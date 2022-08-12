package com.uptech.videolist

import androidx.lifecycle.ViewModel
import com.uptech.videolist.MainViewModel.PlayersAction.RELEASE
import com.uptech.videolist.MainViewModel.PlayersAction.RESTART
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update

class MainViewModel(val playersPool: PlayersPool) : ViewModel() {
  val videoUrls: Flow<List<String>> = flowOf(
    listOf(
      "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
      "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
      "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
      "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
      "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WhatCarCanYouGetForAGrand.mp4",
      "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
      "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/VolkswagenGTIReview.mp4"
    )
  )
  private val _playbackPositions: MutableStateFlow<List<Long>> = MutableStateFlow(
    listOf(0L, 0L, 0L, 0L, 0L, 0L, 0L)
  )
  val playbackPositions: Flow<List<Long>> = _playbackPositions

  private val _playerActions: MutableSharedFlow<PlayersAction> = MutableSharedFlow(extraBufferCapacity = 1)
  val playersActions: Flow<PlayersAction> = _playerActions

  fun updatePlaybackPosition(index: Int, playbackPosition: Long) {
    _playbackPositions.update { playbackPositions ->
      playbackPositions.toMutableList().apply {
        removeAt(index)
        add(index, playbackPosition)
      }
    }
  }

  fun releasePlayers() {
    _playerActions.tryEmit(RELEASE)
    playersPool.releaseAll()
  }

  fun restartPlayers() {
    _playerActions.tryEmit(RESTART)
  }

  enum class PlayersAction {
    RELEASE, RESTART;
  }
}