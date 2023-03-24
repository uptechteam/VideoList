package com.uptech.videolist

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.channels.Channel

class NoOpPlayersPool(
  private val context: Context
) : PlayersPool {
  private val playerMap: MutableMap<String, ReusablePlayer> = mutableMapOf()
  private var playerIndex: Int = 0

  override fun acquire(url: String): Channel<ReusablePlayer> =
    playerMap[url]?.let { reusablePlayer ->
      Channel<ReusablePlayer>(capacity = 1).apply {
        trySend(reusablePlayer)
      }
    } ?: Channel<ReusablePlayer>(capacity = 1).apply {
      trySend(
        ExoPlayer.Builder(context)
          .setLoadControl(
            DefaultLoadControl.Builder()
              .setBufferDurationsMs(
                10_000,
                10_000,
                100,
                2000
              ).build()
          )
          .build()
          .let { exoPlayer -> ReusablePlayer(exoPlayer) }
          .apply { playerId = "player${playerIndex++}" }
          .also { reusablePlayer -> playerMap[url] = reusablePlayer }
      )
    }

  override fun removeFromAwaitingQueue(channel: Channel<ReusablePlayer>) {}

  override fun release(player: Player) {}

  override fun stop(player: Player) {}

  override fun releaseAll() {}
}