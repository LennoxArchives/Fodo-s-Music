package com.example.fodosmusic

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class RepeatMode { OFF, ALL, ONE }

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(application).build()

    private var songList: List<Song> = emptyList()
    private var currentIndex: Int = -1
    private var positionUpdateJob: Job? = null

    var currentSong = mutableStateOf<Song?>(null)
        private set

    var isPlaying = mutableStateOf(false)
        private set

    var currentPosition = mutableStateOf(0L)
        private set

    var duration = mutableStateOf(0L)
        private set

    var isShuffleEnabled = mutableStateOf(false)
        private set

    var repeatMode = mutableStateOf(RepeatMode.OFF)
        private set

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying.value = playing
                if (playing) startPositionUpdates() else stopPositionUpdates()
            }

            override fun onEvents(player: Player, events: Player.Events) {
                duration.value = if (player.duration > 0) player.duration else 0L
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    when (repeatMode.value) {
                        RepeatMode.ONE -> {
                            currentSong.value?.let { playSong(it) }
                        }
                        else -> skipNext()
                    }
                }
            }
        })
    }

    private fun startPositionUpdates() {
        stopPositionUpdates()
        positionUpdateJob = viewModelScope.launch {
            while (true) {
                currentPosition.value = exoPlayer.currentPosition
                delay(200)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    fun setSongList(songs: List<Song>) {
        songList = songs
    }

    fun playSong(song: Song) {
        val index = songList.indexOfFirst { it.id == song.id }
        if (index == -1) return
        currentIndex = index
        currentSong.value = song
        exoPlayer.setMediaItem(MediaItem.fromUri(song.uri))
        exoPlayer.prepare()
        exoPlayer.play()
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
    }

    fun toggleShuffle() {
        isShuffleEnabled.value = !isShuffleEnabled.value
    }

    fun cycleRepeatMode() {
        repeatMode.value = when (repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
    }

    fun skipNext() {
        if (songList.isEmpty() || currentIndex == -1) return
        val nextIndex = if (isShuffleEnabled.value) {
            songList.indices.filter { it != currentIndex }.randomOrNull() ?: currentIndex
        } else {
            val next = currentIndex + 1
            if (next >= songList.size) {
                if (repeatMode.value == RepeatMode.OFF) return
                0
            } else next
        }
        playSong(songList[nextIndex])
    }

    fun skipPrevious() {
        if (songList.isEmpty() || currentIndex == -1) return
        val prevIndex = if (isShuffleEnabled.value) {
            songList.indices.filter { it != currentIndex }.randomOrNull() ?: currentIndex
        } else {
            if (currentIndex - 1 < 0) songList.size - 1 else currentIndex - 1
        }
        playSong(songList[prevIndex])
    }

    fun seekTo(position: Long) {
        exoPlayer.seekTo(position)
        currentPosition.value = position
    }

    override fun onCleared() {
        stopPositionUpdates()
        exoPlayer.release()
        super.onCleared()
    }
}