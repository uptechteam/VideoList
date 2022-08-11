package com.uptech.videolist

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.channels.Channel
import java.util.LinkedList
import java.util.Queue

class PlayersPool(
  private val context: Context,
  private val maxPoolSize: Int
) {
  private val unlockedExoPlayers: MutableList<Player> = mutableListOf(ExoPlayer.Builder(context).build())
  private val lockedExoPlayers: MutableList<Player> = mutableListOf()

  private val awaitingQueue: Queue<Channel<Player>> = LinkedList()

  @Synchronized
  fun acquire(): Channel<Player> =
    if(unlockedExoPlayers.isEmpty()) {
      if(lockedExoPlayers.size >= maxPoolSize) {
        Channel<Player>().also { channel -> awaitingQueue.offer(channel) }
      } else {
        Channel<Player>(capacity = 1).apply {
          trySend(ExoPlayer.Builder(context).build().also(lockedExoPlayers::add))
        }
      }
    } else {
      Channel<Player>(capacity = 1).apply {
        trySend(unlockedExoPlayers.removeLast().also(lockedExoPlayers::add))
      }
    }

  @Synchronized
  fun removeFromAwaitingQueue(channel: Channel<Player>) {
    awaitingQueue.remove(channel)
  }

  @Synchronized
  fun release(player: Player) {
    if(!reusePlayer(player)) {
      lockedExoPlayers.remove(player)
      unlockedExoPlayers.add(player)
    }
  }

  private fun reusePlayer(player: Player): Boolean =
    awaitingQueue.poll()?.run {
      trySend(player)
      true
    } ?: false

  @Synchronized
  fun releaseAll() {
    awaitingQueue.clear()
    unlockedExoPlayers.addAll(lockedExoPlayers)
    lockedExoPlayers.clear()
  }
}