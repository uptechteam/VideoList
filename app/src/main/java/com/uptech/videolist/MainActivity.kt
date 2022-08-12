package com.uptech.videolist

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import com.uptech.videolist.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
  private val viewModel: MainViewModel by viewModels(
    factoryProducer = {
      object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
          MainViewModel(
            PlayersPool(
              applicationContext,
              MediaCodecUtil.getDecoderInfos(MimeTypes.VIDEO_MP4, false, false)
                .minOf { decoder -> decoder.maxSupportedInstances } / 2
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

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)
    binding.videoList.adapter = adapter
    lifecycleScope.launch {
      viewModel.playbackPositions
        .onEach { playbackPositions -> adapter.playbackPositions = playbackPositions }
        .launchIn(this)
      viewModel.videoUrls
        .onEach(adapter::updateVideoUrls)
        .launchIn(this)
    }
  }

  override fun onStart() {
    super.onStart()
    if (Util.SDK_INT > 23) {
      viewModel.restartPlayers()
    }
  }

  override fun onResume() {
    super.onResume()
    if (Util.SDK_INT <= 23) {
      viewModel.restartPlayers()
    }
  }

  override fun onPause() {
    if (Util.SDK_INT <= 23) {
      viewModel.releasePlayers()
    }
    super.onPause()
  }

  override fun onStop() {
    if (Util.SDK_INT > 23) {
      viewModel.releasePlayers()
    }
    super.onStop()
  }
}