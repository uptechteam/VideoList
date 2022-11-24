package com.uptech.videolist

import androidx.media3.common.Player
import kotlinx.coroutines.channels.Channel

interface PlayersPool {

  fun acquire(url: String = ""): Channel<ReusablePlayer>
  fun removeFromAwaitingQueue(channel: Channel<ReusablePlayer>)
  fun release(player: Player)
  fun stop(player: Player)
  fun releaseAll()
}