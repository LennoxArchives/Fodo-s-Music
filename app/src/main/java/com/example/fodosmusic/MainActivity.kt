package com.example.fodosmusic

import android.Manifest
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.HazeMaterials

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                MusicPlayerApp()
            }
        }
    }
}

enum class LibraryTab(val label: String) {
    SONGS("Songs"), ARTISTS("Artists"), ALBUMS("Albums"), PLAYLISTS("Playlists")
}

/** Panel yang lagi ditampilin di bawah Now Playing screen. */
enum class PlayerPanel { NONE, INFO, QUEUE }

@Composable
fun MusicPlayerApp() {
    val context = LocalContext.current
    val playerViewModel: PlayerViewModel = viewModel()
    val hazeState = remember { HazeState() }

    var hasPermission by remember { mutableStateOf(false) }
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var isFullScreen by remember { mutableStateOf(false) }
    var backdropArt by remember { mutableStateOf<Bitmap?>(null) }
    var selectedTab by remember { mutableStateOf(LibraryTab.SONGS) }
    val favoriteIds by playerViewModel.favoriteIds

    val permission = if (Build.VERSION.SDK_INT >= 33)
        Manifest.permission.READ_MEDIA_AUDIO
    else
        Manifest.permission.READ_EXTERNAL_STORAGE

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) {
            songs = getAllAudioFiles(context)
            playerViewModel.setSongList(songs)
        }
    }

    LaunchedEffect(Unit) {
        launcher.launch(permission)
    }

    LaunchedEffect(playerViewModel.currentSong.value?.id) {
        val song = playerViewModel.currentSong.value
        backdropArt = if (song != null) getAlbumArt(context, song.id, song.uri) else null
    }

    if (isFullScreen) {
        BackHandler { isFullScreen = false }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            AlbumArtBackdrop(albumArt = backdropArt)

            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(state = hazeState)
                ) {
                    LibraryHeader()

                    LibraryTabRow(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (!hasPermission) {
                        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            Text("Izin akses musik belum diberikan.", color = TextPrimary)
                        }
                    } else {
                        when (selectedTab) {
                            LibraryTab.SONGS -> {
                                val favoriteSongs = remember(songs, favoriteIds) {
                                    songs.filter { favoriteIds.contains(it.id) }
                                }
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(
                                        start = 16.dp,
                                        end = 16.dp,
                                        top = 0.dp,
                                        bottom = 180.dp
                                    )
                                ) {
                                    if (favoriteSongs.isNotEmpty()) {
                                        item { SectionLabel("FAVORITES") }
                                        items(favoriteSongs) { song ->
                                            SongRow(
                                                song = song,
                                                isFavorite = true,
                                                onClick = { playerViewModel.playSong(song) }
                                            )
                                        }
                                        item { Spacer(modifier = Modifier.height(8.dp)) }
                                        item { SectionLabel("SEMUA LAGU") }
                                    }
                                    items(songs) { song ->
                                        SongRow(
                                            song = song,
                                            isFavorite = favoriteIds.contains(song.id),
                                            onClick = { playerViewModel.playSong(song) }
                                        )
                                    }
                                }
                            }
                            else -> {
                                DummyTabContent(tabLabel = selectedTab.label)
                            }
                        }
                    }
                }

                playerViewModel.currentSong.value?.let { song ->
                    if (!isFullScreen) {
                        PlayerBar(
                            playerViewModel = playerViewModel,
                            song = song,
                            hazeState = hazeState,
                            onExpand = { isFullScreen = true },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp)
                        )
                    }
                }
            }
        }

        playerViewModel.currentSong.value?.let { song ->
            AnimatedVisibility(
                visible = isFullScreen,
                modifier = Modifier.fillMaxSize(),
                enter = slideInVertically(
                    initialOffsetY = { fullHeight -> fullHeight },
                    animationSpec = tween(durationMillis = 350)
                ) + fadeIn(animationSpec = tween(durationMillis = 350)),
                exit = slideOutVertically(
                    targetOffsetY = { fullHeight -> fullHeight },
                    animationSpec = tween(durationMillis = 300)
                ) + fadeOut(animationSpec = tween(durationMillis = 300))
            ) {
                FullScreenPlayer(
                    playerViewModel = playerViewModel,
                    song = song,
                    backdropArt = backdropArt,
                    onSwipeDown = { isFullScreen = false }
                )
            }
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text,
        color = TextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun SongRow(song: Song, isFavorite: Boolean, onClick: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickableSong(onClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SongThumbnail(song = song)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(song.title, color = TextPrimary)
                Text(song.artist, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            if (isFavorite) {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = "Favorite",
                    tint = FavoriteColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun LibraryHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                "Fodo's Music",
                color = TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "YOUR SOUNDTRACK",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { /* dummy: search belum diimplementasi */ }) {
                Icon(Icons.Filled.Search, contentDescription = "Search", tint = TextPrimary)
            }
            IconButton(onClick = { /* dummy: menu belum diimplementasi */ }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = TextPrimary)
            }
        }
    }
}

@Composable
fun LibraryTabRow(selectedTab: LibraryTab, onTabSelected: (LibraryTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LibraryTab.entries.forEach { tab ->
            val isSelected = tab == selectedTab
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (isSelected) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.08f)
                    )
                    .clickable { onTabSelected(tab) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    tab.label,
                    color = if (isSelected) Color.Black else TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun DummyTabContent(tabLabel: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "$tabLabel belum tersedia",
            color = TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun AlbumArtBackdrop(albumArt: Bitmap?, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        if (albumArt != null) {
            Image(
                painter = BitmapPainter(albumArt.asImageBitmap()),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(60.dp),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BackgroundColor.copy(alpha = 0.75f))
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(BackgroundColorSecondary, BackgroundColor)
                        )
                    )
            )
        }
    }
}

@Composable
fun SongThumbnail(song: Song, size: androidx.compose.ui.unit.Dp = 48.dp) {
    val context = LocalContext.current
    var art by remember(song.id) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(song.id) {
        art = getAlbumArt(context, song.id, song.uri)
    }

    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center
    ) {
        if (art != null) {
            Image(
                painter = BitmapPainter(art!!.asImageBitmap()),
                contentDescription = "Album Art",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(size / 2)
            )
        }
    }
}

@Composable
fun PlayerBar(
    playerViewModel: PlayerViewModel,
    song: Song,
    hazeState: HazeState,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val position by playerViewModel.currentPosition
    val duration by playerViewModel.duration
    val isPlaying by playerViewModel.isPlaying
    val isShuffleEnabled by playerViewModel.isShuffleEnabled
    val repeatMode by playerViewModel.repeatMode

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .hazeEffect(state = hazeState, style = HazeMaterials.thin())
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.15f),
                shape = RoundedCornerShape(28.dp)
            )
            .pointerInput(Unit) {
                var accumulatedDrag = 0f
                detectVerticalDragGestures(
                    onDragStart = { accumulatedDrag = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        accumulatedDrag += dragAmount
                        if (accumulatedDrag < -80f) {
                            onExpand()
                        }
                    }
                )
            }
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SongThumbnail(song = song, size = 44.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(song.title, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                    Text(song.artist, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { onExpand() }) {
                    Icon(
                        Icons.Filled.KeyboardArrowUp,
                        contentDescription = "Expand",
                        tint = TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Slider(
                value = if (duration > 0) position.toFloat() / duration.toFloat() else 0f,
                onValueChange = { newValue ->
                    val newPosition = (newValue * duration).toLong()
                    playerViewModel.seekTo(newPosition)
                },
                colors = SliderDefaults.colors(
                    thumbColor = TextPrimary,
                    activeTrackColor = TextPrimary,
                    inactiveTrackColor = TextPrimary.copy(alpha = 0.2f)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatTime(position), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                Text(formatTime(duration), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { playerViewModel.toggleShuffle() }) {
                    Icon(
                        Icons.Filled.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffleEnabled) TextPrimary else TextSecondary
                    )
                }
                IconButton(onClick = { playerViewModel.skipPrevious() }) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = TextPrimary)
                }
                IconButton(onClick = { playerViewModel.togglePlayPause() }) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = TextPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }
                IconButton(onClick = { playerViewModel.skipNext() }) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = TextPrimary)
                }
                IconButton(onClick = { playerViewModel.cycleRepeatMode() }) {
                    Icon(
                        imageVector = if (repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = "Repeat",
                        tint = if (repeatMode == RepeatMode.OFF) TextSecondary else TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun FullScreenPlayer(
    playerViewModel: PlayerViewModel,
    song: Song,
    backdropArt: Bitmap?,
    onSwipeDown: () -> Unit
) {
    val position by playerViewModel.currentPosition
    val duration by playerViewModel.duration
    val isPlaying by playerViewModel.isPlaying
    val isShuffleEnabled by playerViewModel.isShuffleEnabled
    val repeatMode by playerViewModel.repeatMode
    val favoriteIds by playerViewModel.favoriteIds
    val techInfo by playerViewModel.audioTechInfo
    val upNextSongs by playerViewModel.upNextSongs
    val previousSongs by playerViewModel.previousSongs

    var activePanel by remember { mutableStateOf(PlayerPanel.NONE) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var accumulatedDrag = 0f
                detectVerticalDragGestures(
                    onDragStart = { accumulatedDrag = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        accumulatedDrag += dragAmount
                        if (accumulatedDrag > 80f) {
                            onSwipeDown()
                        }
                    }
                )
            }
    ) {
        AlbumArtBackdrop(albumArt = backdropArt)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(TextSecondary)
            )

            Spacer(modifier = Modifier.weight(1f))

            VinylDisc(
                albumArt = backdropArt,
                isPlaying = isPlaying,
                progress = if (duration > 0) position.toFloat() / duration.toFloat() else 0f
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(song.title, color = TextPrimary, style = MaterialTheme.typography.headlineSmall)
            Text(song.artist, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)

            Spacer(modifier = Modifier.height(24.dp))

            Slider(
                value = if (duration > 0) position.toFloat() / duration.toFloat() else 0f,
                onValueChange = { newValue ->
                    val newPosition = (newValue * duration).toLong()
                    playerViewModel.seekTo(newPosition)
                },
                colors = SliderDefaults.colors(
                    thumbColor = TextPrimary,
                    activeTrackColor = TextPrimary,
                    inactiveTrackColor = TextPrimary.copy(alpha = 0.2f)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatTime(position), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                Text(formatTime(duration), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { playerViewModel.toggleShuffle() }) {
                    Icon(
                        Icons.Filled.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffleEnabled) TextPrimary else TextSecondary
                    )
                }
                IconButton(onClick = { playerViewModel.skipPrevious() }) {
                    Icon(
                        Icons.Filled.SkipPrevious,
                        contentDescription = "Previous",
                        tint = TextPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }
                IconButton(onClick = { playerViewModel.togglePlayPause() }) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = TextPrimary,
                        modifier = Modifier.size(48.dp)
                    )
                }
                IconButton(onClick = { playerViewModel.skipNext() }) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = "Next",
                        tint = TextPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }
                IconButton(onClick = { playerViewModel.cycleRepeatMode() }) {
                    Icon(
                        imageVector = if (repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = "Repeat",
                        tint = if (repeatMode == RepeatMode.OFF) TextSecondary else TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            NowPlayingActionRow(
                activePanel = activePanel,
                onPanelSelected = { activePanel = it },
                isFavorite = favoriteIds.contains(song.id),
                onToggleFavorite = { playerViewModel.toggleFavorite(song.id) }
            )

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(if (activePanel != PlayerPanel.NONE) 260.dp else 24.dp))
        }

        AnimatedVisibility(
            visible = activePanel != PlayerPanel.NONE,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight },
                animationSpec = tween(durationMillis = 280)
            ) + fadeIn(animationSpec = tween(durationMillis = 280)),
            exit = slideOutVertically(
                targetOffsetY = { fullHeight -> fullHeight },
                animationSpec = tween(durationMillis = 220)
            ) + fadeOut(animationSpec = tween(durationMillis = 220))
        ) {
            GlassSheetContainer {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(TextSecondary.copy(alpha = 0.5f))
                )
                Spacer(modifier = Modifier.height(12.dp))
                when (activePanel) {
                    PlayerPanel.INFO -> InfoPanelContent(
                        song = song,
                        techInfo = techInfo,
                        albumArt = backdropArt
                    )
                    PlayerPanel.QUEUE -> QueuePanelContent(
                        playerViewModel = playerViewModel,
                        currentSong = song,
                        previousSongs = previousSongs,
                        upNextSongs = upNextSongs
                    )
                    PlayerPanel.NONE -> {}
                }
            }
        }
    }
}

/**
 * Baris aksi di bawah tombol transport: switch Info/Antrian di kiri (dua opsi yang saling
 * eksklusif kayak toggle), dan tombol favorite yang berdiri sendiri di kanan. Posisi
 * keduanya digabung dalam satu row supaya tetap senada sama posisi di referensi.
 */
@Composable
fun NowPlayingActionRow(
    activePanel: PlayerPanel,
    onPanelSelected: (PlayerPanel) -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(24.dp))
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SwitchSegment(
                icon = Icons.Filled.Info,
                label = "Info",
                selected = activePanel == PlayerPanel.INFO,
                onClick = {
                    onPanelSelected(if (activePanel == PlayerPanel.INFO) PlayerPanel.NONE else PlayerPanel.INFO)
                }
            )
            SwitchSegment(
                icon = Icons.Filled.QueueMusic,
                label = "Antrian",
                selected = activePanel == PlayerPanel.QUEUE,
                onClick = {
                    onPanelSelected(if (activePanel == PlayerPanel.QUEUE) PlayerPanel.NONE else PlayerPanel.QUEUE)
                }
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        CircleGlassButton(
            onClick = onToggleFavorite,
            active = isFavorite,
            size = 48
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = "Favorite",
                tint = if (isFavorite) FavoriteColor else TextPrimary
            )
        }
    }
}

@Composable
private fun SwitchSegment(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Color.White.copy(alpha = 0.9f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) Color.Black else TextPrimary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            label,
            color = if (selected) Color.Black else TextPrimary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/** Konten panel "Info": durasi, format, sample rate, bit depth -- dibaca langsung dari file. */
@Composable
fun InfoPanelContent(song: Song, techInfo: AudioTechInfo?, albumArt: Bitmap?) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Info",
            color = TextSecondary,
            fontSize = 12.sp,
            letterSpacing = 2.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.08f))
            ) {
                if (albumArt != null) {
                    Image(
                        painter = BitmapPainter(albumArt.asImageBitmap()),
                        contentDescription = null,
                        modifier = Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(song.title, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(song.artist, color = TextSecondary, fontSize = 13.sp)
                val albumLine = buildString {
                    append(song.album)
                    if (song.year > 0) append("  •  ${song.year}")
                }
                Text(albumLine, color = TextSecondary, fontSize = 12.sp)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            InfoStatCard(
                icon = Icons.Filled.Schedule,
                value = formatTime(song.duration),
                label = "Duration",
                modifier = Modifier.weight(1f)
            )
            InfoStatCard(
                icon = Icons.Filled.Album,
                value = techInfo?.formatLabel?.takeIf { it != "Unknown" } ?: "-",
                label = "Audio Quality",
                modifier = Modifier.weight(1f)
            )
            InfoStatCard(
                icon = Icons.Filled.GraphicEq,
                value = if ((techInfo?.sampleRateHz ?: 0) > 0) {
                    "%.1f kHz".format(techInfo!!.sampleRateHz / 1000f)
                } else "-",
                label = "Sample Rate",
                modifier = Modifier.weight(1f)
            )
            InfoStatCard(
                icon = Icons.Filled.Layers,
                value = if ((techInfo?.bitDepth ?: 0) > 0) "${techInfo!!.bitDepth}-bit" else "-",
                label = "Bit Depth",
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun InfoStatCard(icon: ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = TextPrimary, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.height(6.dp))
        Text(value, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = TextSecondary, fontSize = 10.sp, textAlign = TextAlign.Center)
    }
}

/** Konten panel "Antrian": lagu sebelumnya, lagu yang lagi diputar, dan lagu berikutnya. */
@Composable
fun QueuePanelContent(
    playerViewModel: PlayerViewModel,
    currentSong: Song,
    previousSongs: List<Song>,
    upNextSongs: List<Song>
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Antrian",
            color = TextSecondary,
            fontSize = 12.sp,
            letterSpacing = 2.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(10.dp))
        LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
            if (previousSongs.isNotEmpty()) {
                item {
                    Text(
                        "Sebelumnya",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
                items(previousSongs) { song ->
                    QueueRow(song = song, isCurrent = false) {
                        playerViewModel.playFromQueue(song)
                    }
                }
            }
            item {
                QueueRow(song = currentSong, isCurrent = true, onClick = {})
            }
            if (upNextSongs.isNotEmpty()) {
                item {
                    Text(
                        "Berikutnya",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
                items(upNextSongs) { song ->
                    QueueRow(song = song, isCurrent = false) {
                        playerViewModel.playFromQueue(song)
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueRow(song: Song, isCurrent: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isCurrent) Color.White.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(enabled = !isCurrent) { onClick() }
            .padding(vertical = 8.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SongThumbnail(song = song, size = 38.dp)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal
            )
            Text(song.artist, color = TextSecondary, fontSize = 11.sp)
        }
        if (isCurrent) {
            Icon(
                Icons.Filled.GraphicEq,
                contentDescription = "Sedang diputar",
                tint = TextPrimary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}