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
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
    SONGS("Songs"), ARTISTS("Artists"), ALBUMS("Albums"), LIKED("Liked")
}

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
                        .statusBarsPadding()
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
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(
                                        start = 16.dp,
                                        end = 16.dp,
                                        top = 0.dp,
                                        bottom = 180.dp
                                    )
                                ) {
                                    items(songs) { song ->
                                        GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickableSong { playerViewModel.playSong(song) },
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                SongThumbnail(song = song)
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column {
                                                    Text(song.title, color = TextPrimary)
                                                    Text(song.artist, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                                                }
                                            }
                                        }
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
fun LibraryHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
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
    val density = LocalDensity.current
    val tabCount = LibraryTab.entries.size

    var tabWidths by remember { mutableStateOf(List(tabCount) { 0.dp }) }
    var tabOffsets by remember { mutableStateOf(List(tabCount) { 0.dp }) }

    val selectedIndex = LibraryTab.entries.indexOf(selectedTab)

    val animatedOffset by animateDpAsState(
        targetValue = tabOffsets.getOrElse(selectedIndex) { 0.dp },
        animationSpec = tween(durationMillis = 280),
        label = "pillOffset"
    )
    val animatedWidth by animateDpAsState(
        targetValue = tabWidths.getOrElse(selectedIndex) { 0.dp },
        animationSpec = tween(durationMillis = 280),
        label = "pillWidth"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Pill highlight yang geser
        if (animatedWidth > 0.dp) {
            Box(
                modifier = Modifier
                    .offset(x = animatedOffset)
                    .height(36.dp)
                    .width(animatedWidth)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.9f))
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LibraryTab.entries.forEachIndexed { index, tab ->
                val isSelected = tab == selectedTab
                Box(
                    modifier = Modifier
                        .onGloballyPositioned { coordinates ->
                            val widthDp = with(density) { coordinates.size.width.toDp() }
                            val offsetXDp = with(density) { coordinates.positionInParent().x.toDp() }
                            if (tabWidths.getOrNull(index) != widthDp) {
                                tabWidths = tabWidths.toMutableList().also { it[index] = widthDp }
                            }
                            if (tabOffsets.getOrNull(index) != offsetXDp) {
                                tabOffsets = tabOffsets.toMutableList().also { it[index] = offsetXDp }
                            }
                        }
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onTabSelected(tab) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
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
    val likedSongs by playerViewModel.likedSongs
    val isLiked = likedSongs.contains(song.id)

    var showInfoSheet by remember { mutableStateOf(false) }
    var showPlaylistSheet by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        AlbumArtBackdrop(albumArt = backdropArt)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Ruang kosong di atas buat handle drag yang sekarang dipisah (lihat di bawah),
            // biar konten nggak ketiban dia.
            Spacer(modifier = Modifier.height(24.dp))

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

            Spacer(modifier = Modifier.height(20.dp))

            // Row bawah: pill Info + Playlist di kiri, tombol Like terpisah di kanan
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(24.dp)
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .clickable { showInfoSheet = true }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = "Info",
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Info", color = TextPrimary, fontSize = 13.sp)
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(20.dp)
                            .background(Color.White.copy(alpha = 0.15f))
                    )

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .clickable { showPlaylistSheet = true }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.QueueMusic,
                            contentDescription = "Playlist",
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Playlist", color = TextPrimary, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                        .clickable { playerViewModel.toggleLike(song.id) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (isLiked) Color(0xFFFF5C7A) else TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Handle drag buat nutup layar ini, sengaja dipisah dari Column di atas (yang sekarang
        // bisa di-scroll) supaya gesture-nya nggak ke-"makan" duluan sama scroll. Area sentuhnya
        // dilebarin biar gampang di-drag, bukan cuma pas di garis kecilnya doang.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .fillMaxWidth()
                .height(48.dp)
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
                },
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(TextSecondary)
            )
        }
    }

    if (showInfoSheet) {
        InfoBottomSheet(
            song = song,
            albumArt = backdropArt,
            onDismiss = { showInfoSheet = false }
        )
    }

    if (showPlaylistSheet) {
        PlaylistBottomSheet(
            playerViewModel = playerViewModel,
            onDismiss = { showPlaylistSheet = false },
            onSongSelected = { selected ->
                playerViewModel.playSong(selected)
                showPlaylistSheet = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoBottomSheet(
    song: Song,
    albumArt: Bitmap?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var audioInfo by remember(song.id) { mutableStateOf<AudioInfo?>(null) }

    LaunchedEffect(song.id, song.uri) {
        audioInfo = getAudioInfo(context, song.uri)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BackgroundColorSecondary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                "Info",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(18.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (albumArt != null) {
                        Image(
                            painter = BitmapPainter(albumArt.asImageBitmap()),
                            contentDescription = "Album Art",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(song.title, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                    Text(song.artist, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                InfoStatBox(
                    label = "Duration",
                    value = formatTime(song.duration),
                    modifier = Modifier.weight(1f)
                )
                InfoStatBox(
                    label = "Audio Quality",
                    value = audioInfo?.format ?: "...",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                InfoStatBox(
                    label = "Sample Rate",
                    value = audioInfo?.sampleRateLabel ?: "...",
                    modifier = Modifier.weight(1f)
                )
                InfoStatBox(
                    label = "Bit Depth",
                    value = audioInfo?.bitDepthLabel ?: "...",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun InfoStatBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(vertical = 14.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, color = TextSecondary, fontSize = 11.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistBottomSheet(
    playerViewModel: PlayerViewModel,
    onDismiss: () -> Unit,
    onSongSelected: (Song) -> Unit
) {
    val queue = playerViewModel.getSongList()
    val currentIndex = playerViewModel.getCurrentIndex()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BackgroundColorSecondary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                "Playlist",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (queue.isEmpty()) {
                Text(
                    "Belum ada lagu di antrian.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    itemsIndexed(queue) { index, queuedSong ->
                        val isCurrent = index == currentIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isCurrent) Color.White.copy(alpha = 0.10f) else Color.Transparent
                                )
                                .clickable { onSongSelected(queuedSong) }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SongThumbnail(song = queuedSong, size = 40.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    queuedSong.title,
                                    color = if (isCurrent) TextPrimary else TextSecondary,
                                    fontSize = 14.sp,
                                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal
                                )
                                Text(queuedSong.artist, color = TextSecondary, fontSize = 12.sp)
                            }
                            if (isCurrent) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = "Now playing",
                                    tint = TextPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}