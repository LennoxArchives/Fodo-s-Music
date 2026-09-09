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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Unarchive
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
import kotlinx.coroutines.launch

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
    SONGS("Songs"), ARTISTS("Artists"), ALBUMS("Albums"), FAVORITES("Favorites")
}

enum class SortOption(val label: String) {
    DEFAULT("Default"),
    TITLE_ASC("Title (A-Z)"),
    TITLE_DESC("Title (Z-A)"),
    ARTIST_ASC("Artist (A-Z)"),
    ARTIST_DESC("Artist (Z-A)")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPlayerApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playerViewModel: PlayerViewModel = viewModel()
    val hazeState = remember { HazeState() }

    var hasPermission by remember { mutableStateOf(false) }
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var isFullScreen by remember { mutableStateOf(false) }
    var backdropArt by remember { mutableStateOf<Bitmap?>(null) }
    var selectedTab by remember { mutableStateOf(LibraryTab.SONGS) }

    // Loading state buat preload album art sebelum list ditampilin
    var isPreloading by remember { mutableStateOf(false) }
    var preloadProgress by remember { mutableStateOf(0 to 0) }

    // Archive state, dipersist lewat SharedPreferences
    var archivedIds by remember { mutableStateOf(getArchivedSongIds(context)) }
    var showArchived by remember { mutableStateOf(false) }

    // Search state
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Sort state
    var sortOption by remember { mutableStateOf(SortOption.DEFAULT) }

    val permission = if (Build.VERSION.SDK_INT >= 33)
        Manifest.permission.READ_MEDIA_AUDIO
    else
        Manifest.permission.READ_EXTERNAL_STORAGE

    fun startPreload(list: List<Song>) {
        scope.launch {
            isPreloading = true
            preloadProgress = 0 to list.size
            preloadAlbumArt(context, list) { done, total ->
                preloadProgress = done to total
            }
            isPreloading = false
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) {
            val loaded = getAllAudioFiles(context)
            songs = loaded
            playerViewModel.setSongList(loaded)
            startPreload(loaded)
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

    fun archiveSong(id: Long) {
        val updated = archivedIds + id
        archivedIds = updated
        saveArchivedSongIds(context, updated)
    }

    fun unarchiveSong(id: Long) {
        val updated = archivedIds - id
        archivedIds = updated
        saveArchivedSongIds(context, updated)
    }

    val displayedSongs = remember(songs, archivedIds, showArchived, searchQuery, sortOption) {
        var list = songs.filter { song ->
            if (showArchived) archivedIds.contains(song.id) else !archivedIds.contains(song.id)
        }
        if (searchQuery.isNotBlank()) {
            list = list.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                        it.artist.contains(searchQuery, ignoreCase = true)
            }
        }
        when (sortOption) {
            SortOption.DEFAULT -> list
            SortOption.TITLE_ASC -> list.sortedBy { it.title.lowercase() }
            SortOption.TITLE_DESC -> list.sortedByDescending { it.title.lowercase() }
            SortOption.ARTIST_ASC -> list.sortedBy { it.artist.lowercase() }
            SortOption.ARTIST_DESC -> list.sortedByDescending { it.artist.lowercase() }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            AlbumArtBackdrop(albumArt = backdropArt)

            Box(modifier = Modifier.fillMaxSize()) {
                if (hasPermission && isPreloading) {
                    LoadingScreen(progress = preloadProgress)
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .hazeSource(state = hazeState)
                    ) {
                        LibraryHeader(
                            isSearchActive = isSearchActive,
                            searchQuery = searchQuery,
                            onSearchQueryChange = { searchQuery = it },
                            onSearchToggle = {
                                isSearchActive = !isSearchActive
                                if (!isSearchActive) searchQuery = ""
                            },
                            showArchived = showArchived,
                            onToggleShowArchived = { showArchived = !showArchived }
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LibraryTabRow(
                                selectedTab = selectedTab,
                                onTabSelected = { selectedTab = it },
                                modifier = Modifier.weight(1f)
                            )
                            SortMenuButton(
                                currentSort = sortOption,
                                onSortSelected = { sortOption = it }
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        if (!hasPermission) {
                            Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                                Text("Izin akses musik belum diberikan.", color = TextPrimary)
                            }
                        } else {
                            when (selectedTab) {
                                LibraryTab.SONGS -> {
                                    if (showArchived) {
                                        Text(
                                            "Archived Audio",
                                            color = TextSecondary,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                        )
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
                                        items(displayedSongs, key = { it.id }) { song ->
                                            SongRow(
                                                song = song,
                                                isArchived = showArchived,
                                                onClick = { playerViewModel.playSong(song) },
                                                onArchive = { archiveSong(song.id) },
                                                onUnarchive = { unarchiveSong(song.id) },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(bottom = 8.dp)
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
fun LoadingScreen(progress: Pair<Int, Int>) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = TextPrimary)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Loading your library...", color = TextPrimary)
            if (progress.second > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "${progress.first}/${progress.second}",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun LibraryHeader(
    isSearchActive: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchToggle: () -> Unit,
    showArchived: Boolean,
    onToggleShowArchived: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSearchActive) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search title or artist", color = TextSecondary) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = Color.White.copy(alpha = 0.4f),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                    cursorColor = TextPrimary
                ),
                trailingIcon = {
                    IconButton(onClick = onSearchToggle) {
                        Icon(Icons.Filled.Close, contentDescription = "Close search", tint = TextPrimary)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp)
            )
        } else {
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
                IconButton(onClick = onSearchToggle) {
                    Icon(Icons.Filled.Search, contentDescription = "Search", tint = TextPrimary)
                }
                Box {
                    var menuExpanded by remember { mutableStateOf(false) }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = TextPrimary)
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(if (showArchived) "Show All Audio" else "Show Archived Audio") },
                            onClick = {
                                onToggleShowArchived()
                                menuExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LibraryTabRow(
    selectedTab: LibraryTab,
    onTabSelected: (LibraryTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = LibraryTab.entries
    BoxWithConstraints(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.08f))
    ) {
        val segmentWidth = maxWidth / tabs.size
        val selectedIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)
        val offset by animateDpAsState(targetValue = segmentWidth * selectedIndex, label = "tabIndicator")

        Box(
            modifier = Modifier
                .offset(x = offset)
                .width(segmentWidth)
                .fillMaxHeight()
                .padding(3.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(Color.White.copy(alpha = 0.9f))
        )

        Row(modifier = Modifier.fillMaxSize()) {
            tabs.forEach { tab ->
                val isSelected = tab == selectedTab
                Box(
                    modifier = Modifier
                        .width(segmentWidth)
                        .fillMaxHeight()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onTabSelected(tab) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        tab.label,
                        color = if (isSelected) Color.Black else TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun SortMenuButton(currentSort: SortOption, onSortSelected: (SortOption) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            Icon(Icons.Filled.Sort, contentDescription = "Sort", tint = TextPrimary)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortOption.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    leadingIcon = {
                        if (option == currentSort) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                        }
                    },
                    onClick = {
                        onSortSelected(option)
                        expanded = false
                    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongRow(
    song: Song,
    isArchived: Boolean,
    onClick: () -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                if (isArchived) onUnarchive() else onArchive()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            // Cuma tampilin background merah kalau lagi bener-bener di-swipe.
            // Tanpa ini, background-nya nembus keliatan terus karena GlassCard
            // di atasnya translucent (bukan solid), jadi merahnya kelihatan
            // walaupun lagi diem/ga di-swipe sama sekali.
            if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFB33A3A))
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Icon(
                        imageVector = if (isArchived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                        contentDescription = if (isArchived) "Unarchive" else "Archive",
                        tint = Color.White
                    )
                }
            }
        }
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickableSong(onClick),
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))
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

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}