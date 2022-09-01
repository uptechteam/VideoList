package com.uptech.videolist

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.channels.Channel
import timber.log.Timber
import java.util.LinkedList
import java.util.Queue

class PlayersPool(
  private val context: Context,
  private val maxPoolSize: Int
) {
  private val unlockedPlayers: MutableList<Player> = mutableListOf(ExoPlayer.Builder(context).build())
  private val lockedPlayers: MutableList<Player> = mutableListOf()

  private val waitingQueue: Queue<Channel<Player>> = LinkedList()

  @Synchronized
  fun acquire(): Channel<Player> =
    if(unlockedPlayers.isEmpty()) {
      if(lockedPlayers.size >= maxPoolSize) {
        Channel<Player>(capacity = 1).also { channel -> waitingQueue.offer(channel) }
      } else {
        Channel<Player>(capacity = 1).apply {
          trySend(ExoPlayer.Builder(context).build().also(lockedPlayers::add))
        }
      }
    } else {
      Channel<Player>(capacity = 1).apply {
        trySend(unlockedPlayers.removeLast().also(lockedPlayers::add))
      }
    }.also {
      Timber.tag(VIDEO_LIST).d("pool size = %s", lockedPlayers.size + unlockedPlayers.size)
    }

  @Synchronized
  fun removeFromAwaitingQueue(channel: Channel<Player>) {
    waitingQueue.remove(channel)
  }

  @Synchronized
  fun release(player: Player) {
    lockedPlayers.remove(player)
  }

  @Synchronized
  fun stop(player: Player) {
    if(!reusePlayer(player)) {
      lockedPlayers.remove(player)
      unlockedPlayers.add(player)
    }
  }

  private fun reusePlayer(player: Player): Boolean =
    waitingQueue.poll()?.run {
      trySend(player)
      true
    } ?: false

  @Synchronized
  fun releaseAll() {
    waitingQueue.clear()
    unlockedPlayers.addAll(lockedPlayers)
    lockedPlayers.clear()
  }
}