package com.example.fodosmusic

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class RepeatMode { OFF, ALL, ONE }

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    // playWhenReady sengaja dimatiin di awal biar nggak auto-play begitu playlist di-set,
    // sebelum user sendiri yang milih lagu.
    val exoPlayer: ExoPlayer = ExoPlayer.Builder(application).build().apply {
        playWhenReady = false
    }

    // Mapping id -> Song supaya gampang balikin dari mediaId ExoPlayer ke data Song kita.
    private var songById: Map<Long, Song> = emptyMap()
    private var songList: List<Song> = emptyList()
    private var positionUpdateJob: Job? = null
    private var hasStartedPlayback = false

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

    var favoriteIds = mutableStateOf<Set<Long>>(emptySet())
        private set

    var audioTechInfo = mutableStateOf<AudioTechInfo?>(null)
        private set

    // Preview antrian: beberapa lagu sebelum & sesudah lagu yang lagi diputar.
    // Dihitung dari timeline asli ExoPlayer, jadi tetap akurat walau shuffle nyala.
    var upNextSongs = mutableStateOf<List<Song>>(emptyList())
        private set

    var previousSongs = mutableStateOf<List<Song>>(emptyList())
        private set

    init {
        favoriteIds.value = loadFavoriteIds(application)

        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying.value = playing
                if (playing) startPositionUpdates() else stopPositionUpdates()
            }

            override fun onEvents(player: Player, events: Player.Events) {
                duration.value = if (player.duration != C.TIME_UNSET && player.duration > 0) {
                    player.duration
                } else 0L

                if (events.containsAny(
                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_TIMELINE_CHANGED,
                        Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                        Player.EVENT_REPEAT_MODE_CHANGED
                    )
                ) {
                    syncCurrentSong()
                    refreshQueuePreview()
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

    /**
     * Set seluruh playlist ke ExoPlayer sekali di awal. Karena ini playlist "beneran"
     * (bukan cuma satu MediaItem yang di-swap manual kayak sebelumnya), ExoPlayer otomatis
     * lanjut ke lagu berikutnya sendiri begitu lagu yang lagi diputar habis -- ini yang
     * memperbaiki bug autoplay yang sebelumnya nggak jalan.
     */
    fun setSongList(songs: List<Song>) {
        songList = songs
        songById = songs.associateBy { it.id }

        val mediaItems = songs.map { song ->
            MediaItem.Builder()
                .setUri(song.uri)
                .setMediaId(song.id.toString())
                .build()
        }
        exoPlayer.setMediaItems(mediaItems, /* resetPosition = */ false)
        exoPlayer.prepare()
        refreshQueuePreview()
    }

    fun playSong(song: Song) {
        val index = songList.indexOfFirst { it.id == song.id }
        if (index == -1) return
        hasStartedPlayback = true
        exoPlayer.seekTo(index, 0L)
        exoPlayer.play()
        syncCurrentSong()
        refreshQueuePreview()
    }

    /** Dipanggil dari panel antrian ("Up Next" / "Sebelumnya") buat lompat langsung ke lagu itu. */
    fun playFromQueue(song: Song) = playSong(song)

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
    }

    fun toggleShuffle() {
        exoPlayer.shuffleModeEnabled = !exoPlayer.shuffleModeEnabled
        isShuffleEnabled.value = exoPlayer.shuffleModeEnabled
        refreshQueuePreview()
    }

    fun cycleRepeatMode() {
        val next = when (repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        repeatMode.value = next
        exoPlayer.repeatMode = when (next) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
        refreshQueuePreview()
    }

    // Tombol skip manual selalu maju/mundur satu lagu apa pun repeat mode-nya
    // (ini yang biasa dipakai kebanyakan music player); auto-advance saat lagu
    // habis tetap hormat ke repeat mode lewat exoPlayer.repeatMode di atas.
    fun skipNext() {
        if (exoPlayer.mediaItemCount == 0) return
        if (exoPlayer.hasNextMediaItem()) {
            exoPlayer.seekToNext()
        } else {
            exoPlayer.seekTo(0, 0L)
        }
        hasStartedPlayback = true
        exoPlayer.play()
        syncCurrentSong()
        refreshQueuePreview()
    }

    fun skipPrevious() {
        if (exoPlayer.mediaItemCount == 0) return
        if (exoPlayer.hasPreviousMediaItem()) {
            exoPlayer.seekToPrevious()
        } else {
            exoPlayer.seekTo(exoPlayer.mediaItemCount - 1, 0L)
        }
        hasStartedPlayback = true
        exoPlayer.play()
        syncCurrentSong()
        refreshQueuePreview()
    }

    fun seekTo(position: Long) {
        exoPlayer.seekTo(position)
        currentPosition.value = position
    }

    fun toggleFavorite(songId: Long) {
        val updated = favoriteIds.value.toMutableSet().apply {
            if (contains(songId)) remove(songId) else add(songId)
        }
        favoriteIds.value = updated
        saveFavoriteIds(getApplication(), updated)
    }

    fun isFavorite(songId: Long): Boolean = favoriteIds.value.contains(songId)

    private fun syncCurrentSong() {
        if (!hasStartedPlayback) return
        val mediaId = exoPlayer.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        val song = songById[mediaId] ?: return
        val isNewSong = song.id != currentSong.value?.id
        currentSong.value = song
        if (isNewSong) loadAudioTechInfo(song)
    }

    private fun loadAudioTechInfo(song: Song) {
        viewModelScope.launch {
            audioTechInfo.value = getAudioTechInfo(getApplication(), song.uri)
        }
    }

    private fun refreshQueuePreview(maxEach: Int = 15) {
        val timeline = exoPlayer.currentTimeline
        if (timeline.isEmpty || !hasStartedPlayback) {
            upNextSongs.value = emptyList()
            previousSongs.value = emptyList()
            return
        }

        val current = exoPlayer.currentMediaItemIndex
        val shuffle = exoPlayer.shuffleModeEnabled
        val repeat = exoPlayer.repeatMode

        val next = mutableListOf<Song>()
        var cursor = timeline.getNextWindowIndex(current, repeat, shuffle)
        while (cursor != C.INDEX_UNSET && next.size < maxEach && cursor != current) {
            songList.getOrNull(cursor)?.let { next.add(it) }
            cursor = timeline.getNextWindowIndex(cursor, repeat, shuffle)
        }

        val previous = mutableListOf<Song>()
        var backCursor = timeline.getPreviousWindowIndex(current, repeat, shuffle)
        while (backCursor != C.INDEX_UNSET && previous.size < maxEach && backCursor != current) {
            songList.getOrNull(backCursor)?.let { previous.add(it) }
            backCursor = timeline.getPreviousWindowIndex(backCursor, repeat, shuffle)
        }

        upNextSongs.value = next
        previousSongs.value = previous.reversed()
    }

    override fun onCleared() {
        stopPositionUpdates()
        exoPlayer.release()
        super.onCleared()
    }
}