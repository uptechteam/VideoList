package com.uptech.videolist

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import timber.log.Timber

class ReusablePlayer(
  val player: ExoPlayer
) {
  var playerId: String = ""
  private var playbackHistory: MutableList<String> = mutableListOf()
  private var prepareTime: Long = 0L

  init {
    player.addListener(
      object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
          when (playbackState) {
            ExoPlayer.STATE_IDLE -> {
              Timber.tag(VIDEO_TAG).d(
                "Idle player number: %s",
                playerId
              )
            }
            ExoPlayer.STATE_BUFFERING -> {
              Timber.tag(VIDEO_TAG).d(
                "Buffering player number: %s",
                playerId
              )
            }
            ExoPlayer.STATE_READY -> {
              Timber.tag(VIDEO_TAG).d(
                "Ready player number: %s, preparation %d ms",
                playerId,
                System.currentTimeMillis() - prepareTime
              )
            }
            ExoPlayer.STATE_ENDED ->
              Timber.tag(VIDEO_TAG).d(
                "Ended player number: %s",
                playerId
              )
            else -> {}
          }
        }
      }
    )
  }

  fun setMediaSource(mediaSource: MediaSource) {
    val mediaId: String = mediaSource.mediaItem.playbackProperties?.uri.toString()
      .substringAfterLast('/')
    if (playbackHistory.size > 0) {
      if (mediaId != playbackHistory.last()) {
        Timber.tag(VIDEO_TAG).d(
          "Player %s rebind from %s to %s media source",
          playerId,
          playbackHistory.last(),
          mediaId
        )
      }
    } else {
      Timber.tag(VIDEO_TAG).d(
        "Player %s first bind to %s media source",
        playerId,
        mediaId
      )
    }
    playbackHistory += mediaId
    player.setMediaSource(mediaSource)
  }

  fun prepare() {
    prepareTime = System.currentTimeMillis()
    player.prepare()
  }

  companion object {
    const val VIDEO_TAG = "videoTag"
  }
}