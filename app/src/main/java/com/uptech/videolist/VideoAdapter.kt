package com.uptech.videolist

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.recyclerview.widget.RecyclerView
import com.uptech.videolist.MainViewModel.PlayersAction
import com.uptech.videolist.MainViewModel.PlayersAction.RELEASE
import com.uptech.videolist.MainViewModel.PlayersAction.RESTART
import com.uptech.videolist.VideoAdapter.VideoViewHolder
import com.uptech.videolist.databinding.VideoItemViewBinding
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class VideoAdapter(
  private val playersPool: PlayersPool,
  private val lifecycleScope: CoroutineScope,
  private val playersActions: Flow<PlayersAction>,
  private val dispatcher: CoroutineDispatcher,
  private val updatePlaybackPosition: (Int, Long) -> Unit
) : RecyclerView.Adapter<VideoViewHolder>() {
  private var videoUrls: List<String> = listOf()
  var playbackPositions: List<Long> = listOf()

  fun updateVideoUrls(videoUrls: List<String>) {
    this.videoUrls = videoUrls
    notifyDataSetChanged()
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder =
    VideoViewHolder(
      VideoItemViewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

  override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
    holder.bind(videoUrls[position], playbackPositions[position])
  }

  override fun getItemCount(): Int = videoUrls.size

  override fun onViewAttachedToWindow(holder: VideoViewHolder) {
    super.onViewAttachedToWindow(holder)
    holder.attach()
  }

  override fun onViewDetachedFromWindow(holder: VideoViewHolder) {
    super.onViewDetachedFromWindow(holder)
    holder.detach()
  }

  inner class VideoViewHolder(
    private val binding: VideoItemViewBinding
  ) : RecyclerView.ViewHolder(binding.root) {
    private lateinit var videoScope: CoroutineScope
    private lateinit var playerChannel: Channel<Player>
    private var playJob: Job? = null
    private var restartJob: Job? = null

    fun bind(url: String, playbackPosition: Long) {
      videoScope = CoroutineScope(Job() + dispatcher)
      bindPlayer(url, playbackPosition)
    }

    fun attach() {
      if(!(this::videoScope.isInitialized || videoScope.isActive)) {
        videoScope = CoroutineScope(Job() + dispatcher)
        bindPlayer(
          videoUrls[absoluteAdapterPosition],
          playbackPositions[absoluteAdapterPosition]
        )
      }
    }

    private fun bindPlayer(url: String, playbackPosition: Long) {
      playJob?.cancel()
      playJob = videoScope.launch {
        playersPool.acquire()
          .also { playerChannel = it }
          .receive()
          .run {
            binding.playerView.player = this
            setMediaItem(MediaItem.fromUri(url))
            playWhenReady = true
            seekTo(0, playbackPosition)
            prepare()
          }
      }
      if(restartJob === null) {
        restartJob = playersActions
          .onEach { action ->
            when(action) {
              RELEASE -> with(binding.playerView) {
                updatePlaybackPosition(absoluteAdapterPosition, player?.currentPosition ?: 0)
                player?.run {
                  release()
                  playersPool.release(this)
                }
                player = null
              }
              RESTART -> {
                bindPlayer(
                  videoUrls[absoluteAdapterPosition],
                  playbackPositions[absoluteAdapterPosition]
                )
              }
            }
          }.launchIn(lifecycleScope)
      }
    }

    fun detach() {
      with(binding.playerView) {
        playersPool.removeFromAwaitingQueue(playerChannel)
        player?.run {
          playWhenReady = false
          stop()
          updatePlaybackPosition(absoluteAdapterPosition, currentPosition)
          playersPool.release(this)
        }
        videoScope.cancel()
        player = null
      }
    }
  }
}