package com.uptech.videolist

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.channels.Channel
import timber.log.Timber
import java.util.LinkedList
import java.util.Queue

class ReusablePlayersPool(
  private val context: Context,
  private val maxPoolSize: Int
) : PlayersPool {
  private val unlockedPlayers: MutableList<ReusablePlayer> = mutableListOf()
  private val lockedPlayers: MutableList<ReusablePlayer> = mutableListOf()
  private var playerIndex: Int = 0

  private val waitingQueue: Queue<Channel<ReusablePlayer>> = LinkedList()

  @Synchronized
  override fun acquire(url: String): Channel<ReusablePlayer> =
    if(unlockedPlayers.isEmpty()) {
      if(lockedPlayers.size >= maxPoolSize) {
        Channel<ReusablePlayer>(capacity = 1).also { channel -> waitingQueue.offer(channel) }
      } else {
        Channel<ReusablePlayer>(capacity = 1).apply {
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
              .also { player -> lockedPlayers.add(player) }
          )
        }
      }
    } else {
      Channel<ReusablePlayer>(capacity = 1).apply {
        trySend(unlockedPlayers.removeLast().also(lockedPlayers::add))
      }
    }.also {
      Timber.tag(VIDEO_LIST).d("pool size = %s", lockedPlayers.size + unlockedPlayers.size)
    }

  @Synchronized
  override fun removeFromAwaitingQueue(channel: Channel<ReusablePlayer>) {
    waitingQueue.remove(channel)
  }

  @Synchronized
  override fun release(player: Player) {
    lockedPlayers.removeAll { reusablePlayer -> reusablePlayer.player == player }
  }

  @Synchronized
  override fun stop(player: Player) {
    val reusablePlayer = lockedPlayers.first { reusablePlayer -> reusablePlayer.player == player }
    if (!reusePlayer(reusablePlayer)) {
      lockedPlayers.remove(reusablePlayer)
      unlockedPlayers.add(reusablePlayer)
    }
  }

  private fun reusePlayer(player: ReusablePlayer): Boolean =
    waitingQueue.poll()?.run {
      trySend(player)
      true
    } ?: false

  @Synchronized
  override fun releaseAll() {
    waitingQueue.clear()
    unlockedPlayers.addAll(lockedPlayers)
    lockedPlayers.clear()
  }
}