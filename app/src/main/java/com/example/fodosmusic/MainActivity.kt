package com.example.fodosmusic

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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

enum class SortOption(val label: String) {
    DEFAULT("Default"),
    TITLE_ASC("Title A\u2013Z"),
    TITLE_DESC("Title Z\u2013A"),
    ARTIST_ASC("Artist A\u2013Z"),
    ARTIST_DESC("Artist Z\u2013A")
}

fun sortSongs(songs: List<Song>, option: SortOption): List<Song> = when (option) {
    SortOption.DEFAULT -> songs
    SortOption.TITLE_ASC -> songs.sortedBy { it.title.lowercase() }
    SortOption.TITLE_DESC -> songs.sortedByDescending { it.title.lowercase() }
    SortOption.ARTIST_ASC -> songs.sortedBy { it.artist.lowercase() }
    SortOption.ARTIST_DESC -> songs.sortedByDescending { it.artist.lowercase() }
}

// Persistensi sederhana pakai SharedPreferences, cukup buat nyimpen kumpulan ID lagu
// (dipakai buat lagu yang diarsipkan, sifatnya ringan dan ga butuh database).
private const val PREFS_NAME = "fodos_music_prefs"
private const val KEY_ARCHIVED_IDS = "archived_song_ids"

fun loadArchivedSongIds(context: Context): Set<Long> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getStringSet(KEY_ARCHIVED_IDS, emptySet())
        ?.mapNotNull { it.toLongOrNull() }
        ?.toSet()
        ?: emptySet()
}

fun saveArchivedSongIds(context: Context, ids: Set<Long>) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit()
        .putStringSet(KEY_ARCHIVED_IDS, ids.map { it.toString() }.toSet())
        .apply()
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

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    var likedSongIds by remember { mutableStateOf(setOf<Long>()) }
    var archivedSongIds by remember { mutableStateOf(loadArchivedSongIds(context)) }

    var showOverflowMenu by remember { mutableStateOf(false) }
    var showArchivedDialog by remember { mutableStateOf(false) }

    var sortOption by remember { mutableStateOf(SortOption.DEFAULT) }
    var showSortMenu by remember { mutableStateOf(false) }

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
        }
    }

    LaunchedEffect(Unit) {
        launcher.launch(permission)
    }

    // Autoplay/skip selalu mengacu ke daftar lagu yang belum diarsipkan
    LaunchedEffect(songs, archivedSongIds) {
        playerViewModel.setSongList(songs.filter { it.id !in archivedSongIds })
    }

    // Simpan daftar lagu yang diarsipkan tiap kali berubah, biar inget walau app di-kill
    LaunchedEffect(archivedSongIds) {
        saveArchivedSongIds(context, archivedSongIds)
    }

    LaunchedEffect(playerViewModel.currentSong.value?.id) {
        val song = playerViewModel.currentSong.value
        backdropArt = if (song != null) getAlbumArt(context, song.id, song.uri) else null
    }

    if (isFullScreen) {
        BackHandler { isFullScreen = false }
    }

    val displayedSongs = remember(songs, searchQuery, archivedSongIds, selectedTab, likedSongIds, sortOption) {
        val notArchived = songs.filter { it.id !in archivedSongIds }
        val scoped = if (selectedTab == LibraryTab.LIKED) {
            notArchived.filter { it.id in likedSongIds }
        } else {
            notArchived
        }
        val searched = if (searchQuery.isBlank()) {
            scoped
        } else {
            scoped.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                        it.artist.contains(searchQuery, ignoreCase = true)
            }
        }
        sortSongs(searched, sortOption)
    }

    val archivedSongs = remember(songs, archivedSongIds) {
        songs.filter { it.id in archivedSongIds }
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
                    LibraryHeader(
                        isSearchActive = isSearchActive,
                        onSearchToggle = {
                            isSearchActive = !isSearchActive
                            if (!isSearchActive) searchQuery = ""
                        },
                        showOverflowMenu = showOverflowMenu,
                        onOverflowClick = { showOverflowMenu = true },
                        onOverflowDismiss = { showOverflowMenu = false },
                        onShowArchivedClick = {
                            showOverflowMenu = false
                            showArchivedDialog = true
                        }
                    )

                    AnimatedVisibility(
                        visible = isSearchActive,
                        enter = fadeIn(tween(200)) + expandVertically(tween(220)),
                        exit = fadeOut(tween(150)) + shrinkVertically(tween(200))
                    ) {
                        SearchPillBar(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            onClose = {
                                isSearchActive = false
                                searchQuery = ""
                            }
                        )
                    }

                    LibraryTabRow(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it },
                        sortOption = sortOption,
                        showSortMenu = showSortMenu,
                        onSortClick = { showSortMenu = true },
                        onSortDismiss = { showSortMenu = false },
                        onSortOptionSelected = {
                            sortOption = it
                            showSortMenu = false
                        }
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    if (!hasPermission) {
                        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            Text("Izin akses musik belum diberikan.", color = TextPrimary)
                        }
                    } else {
                        when (selectedTab) {
                            LibraryTab.SONGS, LibraryTab.LIKED -> {
                                if (displayedSongs.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize().padding(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            when {
                                                searchQuery.isNotBlank() -> "Ga ada lagu yang cocok."
                                                selectedTab == LibraryTab.LIKED -> "Belum ada lagu yang di-like."
                                                else -> "Belum ada lagu."
                                            },
                                            color = TextSecondary,
                                            fontSize = 14.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(
                                            start = 16.dp,
                                            end = 16.dp,
                                            top = 0.dp,
                                            bottom = 180.dp
                                        ),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(displayedSongs, key = { it.id }) { song ->
                                            SwipeToArchiveRow(
                                                onArchive = {
                                                    archivedSongIds = archivedSongIds + song.id
                                                }
                                            ) {
                                                SongRow(
                                                    song = song,
                                                    isLiked = song.id in likedSongIds,
                                                    onToggleLike = {
                                                        likedSongIds = if (song.id in likedSongIds) {
                                                            likedSongIds - song.id
                                                        } else {
                                                            likedSongIds + song.id
                                                        }
                                                    },
                                                    onClick = { playerViewModel.playSong(song) }
                                                )
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

        if (showArchivedDialog) {
            ArchivedAudioDialog(
                archivedSongs = archivedSongs,
                onDismiss = { showArchivedDialog = false },
                onRestore = { song ->
                    archivedSongIds = archivedSongIds - song.id
                }
            )
        }
    }
}

@Composable
fun LibraryHeader(
    isSearchActive: Boolean,
    onSearchToggle: () -> Unit,
    showOverflowMenu: Boolean,
    onOverflowClick: () -> Unit,
    onOverflowDismiss: () -> Unit,
    onShowArchivedClick: () -> Unit
) {
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
            IconButton(onClick = onSearchToggle) {
                Icon(
                    imageVector = if (isSearchActive) Icons.Filled.Close else Icons.Filled.Search,
                    contentDescription = if (isSearchActive) "Close search" else "Search",
                    tint = TextPrimary
                )
            }
            Box {
                IconButton(onClick = onOverflowClick) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = TextPrimary)
                }
                DropdownMenu(
                    expanded = showOverflowMenu,
                    onDismissRequest = onOverflowDismiss
                ) {
                    DropdownMenuItem(
                        text = { Text("Show Archived Audio") },
                        onClick = onShowArchivedClick
                    )
                }
            }
        }
    }
}

@Composable
fun SearchPillBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(23.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f)) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 14.sp),
                cursorBrush = SolidColor(TextPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
            if (query.isEmpty()) {
                Text("Cari judul atau artis...", color = TextSecondary, fontSize = 14.sp)
            }
        }
        if (query.isNotEmpty()) {
            IconButton(
                onClick = { onQueryChange("") },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Clear",
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
fun LibraryTabRow(
    selectedTab: LibraryTab,
    onTabSelected: (LibraryTab) -> Unit,
    sortOption: SortOption,
    showSortMenu: Boolean,
    onSortClick: () -> Unit,
    onSortDismiss: () -> Unit,
    onSortOptionSelected: (SortOption) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            val tabCount = LibraryTab.entries.size
            val segmentWidth = maxWidth / tabCount
            val selectedIndex = LibraryTab.entries.indexOf(selectedTab)
            val animatedOffset by animateDpAsState(
                targetValue = segmentWidth * selectedIndex,
                animationSpec = tween(durationMillis = 280),
                label = "tabIndicator"
            )

            Box(
                modifier = Modifier
                    .offset(x = animatedOffset)
                    .width(segmentWidth)
                    .fillMaxHeight()
                    .padding(3.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.92f))
            )

            Row(modifier = Modifier.fillMaxSize()) {
                LibraryTab.entries.forEach { tab ->
                    val isSelected = tab == selectedTab
                    Box(
                        modifier = Modifier
                            .width(segmentWidth)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onTabSelected(tab) },
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

        Spacer(modifier = Modifier.width(8.dp))

        Box {
            IconButton(
                onClick = onSortClick,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.08f))
            ) {
                Icon(
                    Icons.Filled.Sort,
                    contentDescription = "Sort (${sortOption.label})",
                    tint = TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
            DropdownMenu(
                expanded = showSortMenu,
                onDismissRequest = onSortDismiss
            ) {
                SortOption.entries.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                option.label,
                                fontWeight = if (option == sortOption) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        onClick = { onSortOptionSelected(option) }
                    )
                }
            }
        }
    }
}

@Composable
fun SwipeToArchiveRow(
    onArchive: () -> Unit,
    content: @Composable () -> Unit
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val archiveThresholdPx = with(density) { 110.dp.toPx() }
    val dismissPx = with(density) { 500.dp.toPx() }

    Box(modifier = Modifier.fillMaxWidth()) {
        val revealProgress = (-offsetX.value / archiveThresholdPx).coerceIn(0f, 1f)
        if (revealProgress > 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFFC24444).copy(alpha = 0.35f + 0.5f * revealProgress)),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Filled.Archive,
                    contentDescription = "Archive",
                    tint = Color.White.copy(alpha = 0.6f + 0.4f * revealProgress),
                    modifier = Modifier
                        .padding(end = 22.dp)
                        .size(22.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (offsetX.value < -archiveThresholdPx) {
                                    offsetX.animateTo(
                                        targetValue = -dismissPx,
                                        animationSpec = tween(220)
                                    )
                                    onArchive()
                                } else {
                                    offsetX.animateTo(0f, animationSpec = tween(200))
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch { offsetX.animateTo(0f, animationSpec = tween(200)) }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val newVal = (offsetX.value + dragAmount).coerceIn(-dismissPx, 0f)
                            scope.launch { offsetX.snapTo(newVal) }
                        }
                    )
                }
        ) {
            content()
        }
    }
}

@Composable
fun SongRow(
    song: Song,
    isLiked: Boolean,
    onToggleLike: () -> Unit,
    onClick: () -> Unit
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
            Column(modifier = Modifier.weight(1f)) {
                Text(song.title, color = TextPrimary)
                Text(song.artist, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onToggleLike) {
                Icon(
                    imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Like",
                    tint = if (isLiked) Color(0xFFE0507A) else TextSecondary
                )
            }
        }
    }
}

@Composable
fun ArchivedAudioDialog(
    archivedSongs: List<Song>,
    onDismiss: () -> Unit,
    onRestore: (Song) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(BackgroundColorSecondary)
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Arsip Audio",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Tutup", tint = TextPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (archivedSongs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Belum ada lagu yang diarsipkan",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(archivedSongs, key = { it.id }) { song ->
                            GlassCard(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SongThumbnail(song = song, size = 40.dp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(song.title, color = TextPrimary, fontSize = 14.sp)
                                        Text(
                                            song.artist,
                                            color = TextSecondary,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    IconButton(onClick = { onRestore(song) }) {
                                        Icon(
                                            Icons.Filled.Unarchive,
                                            contentDescription = "Kembalikan",
                                            tint = TextPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
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