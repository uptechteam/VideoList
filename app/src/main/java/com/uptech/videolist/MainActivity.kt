package com.uptech.videolist

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.util.Util
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.uptech.videolist.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {
  private val viewModel: MainViewModel by viewModels(
    factoryProducer = {
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
          MainViewModel(
            PlayersPool(
              applicationContext,
              //use predefined number of codecs if there are not enough hardware codecs available on
              //device. P.S. as tests show app doesn't use more than 4 codecs instances
              4//minOf(4, availableCodecsNum())
            )
          ) as T
      }
    }
  )
  private val adapter: VideoAdapter by lazy(LazyThreadSafetyMode.NONE) {
    VideoAdapter(
      viewModel.playersPool,
      lifecycleScope,
      viewModel.playersActions,
      Dispatchers.Main,
      viewModel::updatePlaybackPosition
    )
  }

  lateinit var binding: ActivityMainBinding

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)
    binding.videoList.adapter = adapter
    val videoCacheDir = File(applicationContext.externalCacheDir, VIDEO_CACHE_DIR)
    val videoCache = SimpleCache(
      videoCacheDir,
      NoOpCacheEvictor(),
      StandaloneDatabaseProvider(this)
    )
    val cacheSink = CacheDataSink.Factory()
      .setCache(videoCache)
    val upstreamFactory = DefaultDataSource.Factory(
      this,
      DefaultHttpDataSource.Factory()
    )
    val downStreamFactory = FileDataSource.Factory()
    val cacheDataSourceFactory = CacheDataSource.Factory()
      .setCache(videoCache)
      .setCacheWriteDataSinkFactory(cacheSink)
      .setCacheReadDataSourceFactory(downStreamFactory)
      .setUpstreamDataSourceFactory(upstreamFactory)
      .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    lifecycleScope.launch {
      viewModel.playbackPositions
        .onEach { playbackPositions -> adapter.playbackPositions = playbackPositions }
        .launchIn(this)
      viewModel.videoUrls
        .onEach { videoUrls ->
          ProgressiveMediaSource.Factory(cacheDataSourceFactory).run {
            videoUrls.map { url -> createMediaSource(MediaItem.fromUri(url)) }
          }.let { mediaSources -> adapter.updateVideoUrls(mediaSources) }
        }.launchIn(this)
    }
  }

  override fun onStart() {
    super.onStart()
    if (Util.SDK_INT > 23)
      viewModel.restartPlayers()
  }

  override fun onResume() {
    super.onResume()
    if (Util.SDK_INT <= 23)
      viewModel.restartPlayers()
  }

  override fun onPause() {
    if (Util.SDK_INT <= 23)
      viewModel.releasePlayers()
    super.onPause()
  }

  override fun onStop() {
    if (Util.SDK_INT > 23)
      viewModel.releasePlayers()
    super.onStop()
  }

  companion object {
    const val VIDEO_CACHE_DIR = "videoCache"
  }
}