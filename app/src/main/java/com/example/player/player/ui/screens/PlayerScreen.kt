package com.example.player.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.interaction.MutableInteractionSource
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.player.MainViewModel
import com.example.player.data.model.MusicSong
import com.example.player.ui.theme.*
import android.content.Intent
import android.os.Build
import com.example.player.service.FloatingLyricsService
import com.example.player.ui.util.LyricsParser

@Composable
fun isLightTheme(): Boolean {
    val darkModePref by com.example.player.DarkModeManager.darkModeEnabled.collectAsState()
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val useDarkTheme = darkModePref ?: systemDark
    return !useDarkTheme
}

enum class PlayMode(val label: String) {
    SEQUENTIAL("顺序播放"),
    SHUFFLE("随机播放"),
    SINGLE_LOOP("单曲循环");

    fun next(): PlayMode = when (this) {
        SEQUENTIAL -> SHUFFLE
        SHUFFLE -> SINGLE_LOOP
        SINGLE_LOOP -> SEQUENTIAL
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit
) {
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val speed by viewModel.speed.collectAsState()
    val playQueue by viewModel.playQueue.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()

    var playMode by remember { mutableStateOf(PlayMode.SEQUENTIAL) }

    val context = LocalContext.current

    var showPlaylist by remember { mutableStateOf(false) }

    var showSpeedDialog by remember { mutableStateOf(false) }

    var showLyrics by remember { mutableStateOf(false) }

    var isFloatingLyricsEnabled by remember { mutableStateOf(FloatingLyricsService.isRunning.value) }
    val floatingLyricsRunning by FloatingLyricsService.isRunning.collectAsState()

    val configuration = LocalConfiguration.current
    val screenHeightDp = configuration.screenHeightDp

    val repeatMode by viewModel.repeatMode.collectAsState()
    val shuffleMode by viewModel.shuffleMode.collectAsState()

    LaunchedEffect(repeatMode, shuffleMode) {
        playMode = when {
            shuffleMode.name == "ON" -> PlayMode.SHUFFLE
            repeatMode.name == "ONE" -> PlayMode.SINGLE_LOOP
            else -> PlayMode.SEQUENTIAL
        }
    }

    val blurredBackground by viewModel.playerBlurredBackground.collectAsState()
    val lyricLines by viewModel.playerLyrics.collectAsState()

    val currentLyricIndex = remember(currentPosition, lyricLines) {
        lyricLines?.let { LyricsParser.findCurrentLineIndex(it, currentPosition) } ?: -1
    }

    val lyricsListState = rememberLazyListState()

    LaunchedEffect(currentLyricIndex, showLyrics) {
        if (currentLyricIndex >= 0 && showLyrics) {
            try {
                lyricsListState.animateScrollToItem(
                    index = currentLyricIndex,
                    scrollOffset = -200
                )
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(floatingLyricsRunning) {
        if (!floatingLyricsRunning && isFloatingLyricsEnabled) {
            isFloatingLyricsEnabled = false
        }
    }

    DisposableEffect(Unit) {
        val activity = context as? android.app.Activity
        activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        }
    }

    val playerPageBgColor = Color(0xFF1A1A1A)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(playerPageBgColor)

            .clickable(
                onClick = {},
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
    ) {

        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.05f),
                                Color.Black.copy(alpha = 0.15f),
                                Color.Black.copy(alpha = 0.30f)
                            )
                        )
                    )
                }
        ) {
            if (blurredBackground != null) {
                Image(
                    bitmap = blurredBackground!!,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp)
        ) {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 8.dp)
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "返回",
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                val screenWidthDp = configuration.screenWidthDp - 56
                val artSize = minOf(screenWidthDp.toFloat(), (screenHeightDp * 0.46f), 400f)

                val coverAlpha by animateFloatAsState(
                    targetValue = if (showLyrics) 0f else 1f,
                    animationSpec = tween(300),
                    label = "coverAlpha"
                )

                val currentUri = currentSong?.albumArtUri
                var displayedUri by remember { mutableStateOf<String?>(currentUri) }

                LaunchedEffect(currentUri) {
                    val uri = currentUri
                    if (uri != null && uri != displayedUri) {
                        try {
                            val request = ImageRequest.Builder(context)
                                .data(uri)
                                .crossfade(false)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .allowHardware(true)
                                .build()
                            coil.Coil.imageLoader(context).execute(request)
                            displayedUri = uri
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            displayedUri = uri
                        }
                    } else if (uri == null) {
                        displayedUri = null
                    }
                }

                Card(
                    modifier = Modifier
                        .size(artSize.dp)
                        .graphicsLayer { alpha = coverAlpha }
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { if (!lyricLines.isNullOrEmpty()) showLyrics = true },
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
                ) {
                    if (displayedUri != null) {
                        AsyncImage(
                            model = displayedUri,
                            contentDescription = currentSong?.displayAlbum ?: "",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(24.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(24.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = "默认封面",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size((artSize * 0.45f).dp)
                            )
                        }
                    }
                }

                val lyricsAlpha by animateFloatAsState(
                    targetValue = if (showLyrics) 1f else 0f,
                    animationSpec = tween(300),
                    label = "lyricsAlpha"
                )

                if (lyricsAlpha > 0.01f) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LyricsDisplay(
                            lyricLines = lyricLines,
                            currentLyricIndex = currentLyricIndex,
                            listState = lyricsListState,
                            onClick = { showLyrics = false },
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = lyricsAlpha }
                        )

                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp)
            ) {
                Text(
                    text = currentSong?.displayTitle ?: "未在播放",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = currentSong?.displaySubtitle ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Normal,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            var isDragging by remember { mutableStateOf(false) }
            var dragPosition by remember { mutableFloatStateOf(0f) }

            var isWaitingSeek by remember { mutableStateOf(false) }
            var seekTarget by remember { mutableFloatStateOf(0f) }

            val playbackProgress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f

            if (isWaitingSeek && kotlin.math.abs(playbackProgress - seekTarget) < 0.02f) {
                isWaitingSeek = false
            }

            val sliderValue = when {
                isDragging -> dragPosition
                isWaitingSeek -> seekTarget
                else -> playbackProgress.coerceIn(0f, 1f)
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = sliderValue,
                    onValueChange = { newValue ->
                        isDragging = true
                        isWaitingSeek = false
                        dragPosition = newValue
                    },
                    onValueChangeFinished = {

                        viewModel.seekToPercent(dragPosition)
                        seekTarget = dragPosition
                        isDragging = false
                        isWaitingSeek = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    thumb = {
                        Box(
                            modifier = Modifier.size(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .background(Color.White, CircleShape)
                            )
                        }
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                    )
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(if (isDragging || isWaitingSeek) (sliderValue * duration).toLong() else currentPosition),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )
                    Text(
                        text = formatTime(duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {

                IconButton(onClick = {
                    playMode = playMode.next()
                    viewModel.applyPlayMode(playMode)
                    android.widget.Toast.makeText(context, playMode.label, android.widget.Toast.LENGTH_SHORT).show()
                }) {
                    Icon(
                        when (playMode) {
                            PlayMode.SEQUENTIAL -> Icons.Outlined.Repeat
                            PlayMode.SHUFFLE -> Icons.Default.Shuffle
                            PlayMode.SINGLE_LOOP -> Icons.Default.RepeatOne
                        },
                        contentDescription = playMode.label,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.previous() },
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = "上一首",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        modifier = Modifier.size(38.dp),
                        tint = Color.White
                    )
                }

                IconButton(
                    onClick = { viewModel.next() },
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = "下一首",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                TextButton(onClick = { showSpeedDialog = true }) {
                    Text(
                        text = "${speed}x",
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                IconButton(
                    onClick = {
                        currentSong?.let { song ->
                            viewModel.toggleFavoriteAndUpdateUI(song)
                        }
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        if (currentSong?.isFavorite == true) Icons.Filled.Favorite
                        else Icons.Outlined.FavoriteBorder,
                        contentDescription = "收藏",
                        tint = if (currentSong?.isFavorite == true) Color(0xFFFF3B5C)
                        else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                if (!lyricLines.isNullOrEmpty()) {
                    IconButton(
                        onClick = {
                            handleFloatingLyricsToggle(
                                context = context,
                                isEnabled = isFloatingLyricsEnabled,
                                onToggle = { enabled ->
                                    isFloatingLyricsEnabled = enabled
                                }
                            )
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Lyrics,
                            contentDescription = "悬浮歌词",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))
                }

                IconButton(
                    onClick = { showPlaylist = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "播放列表",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

    }
    if (showPlaylist) {
        PlaylistBottomSheet(
            playQueue = playQueue,
            currentIndex = currentIndex,
            onSongSelect = { index ->
                viewModel.playFromQueue(index)
                showPlaylist = false
            },
            onRemoveSong = { index ->
                viewModel.removeFromQueue(index)
            },
            onClearQueue = {
                viewModel.clearQueue()
                showPlaylist = false
            },
            onDismiss = { showPlaylist = false }
        )
    }

    if (showSpeedDialog) {
        SpeedSelectionBottomSheet(
            currentSpeed = speed,
            onSelectSpeed = { viewModel.setSpeed(it) },
            onDismiss = { showSpeedDialog = false }
        )
    }
}

private fun handleFloatingLyricsToggle(
    context: android.content.Context,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    if (isEnabled) {

        FloatingLyricsService.stop(context)
        onToggle(false)
        return
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        if (!android.provider.Settings.canDrawOverlays(context)) {

            try {
                val intent = Intent(
                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:${context.packageName}")
                )
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (_: Exception) {
            }
            return
        }
    }

    FloatingLyricsService.start(context)
    onToggle(true)
}

@Composable
private fun LyricsDisplay(
    lyricLines: List<LyricsParser.LyricLine>?,
    currentLyricIndex: Int,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (lyricLines.isNullOrEmpty()) {

        Box(
            modifier = modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Lyrics,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "暂无歌词",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.height(4.dp))

            }
        }
    } else {

        val isPlainText = lyricLines.size == 1 && lyricLines[0].timeMs == 0L && lyricLines[0].text.contains("\n")

        if (isPlainText) {

            Box(
                modifier = modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onClick() }
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = lyricLines[0].text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        } else {

            LazyColumn(
                state = listState,
                modifier = modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onClick() },
                contentPadding = PaddingValues(vertical = 180.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                itemsIndexed(lyricLines, key = { index, _ -> index }) { index, line ->
                    if (line.text.isEmpty()) {

                    } else {
                        val isCurrent = index == currentLyricIndex
                        val distance = kotlin.math.abs(index - currentLyricIndex)

                        val alpha = when {
                            isCurrent -> 1.0f
                            distance <= 1 -> 0.6f
                            distance <= 2 -> 0.42f
                            distance <= 4 -> 0.28f
                            else -> 0.2f
                        }
                        val scale = when {
                            isCurrent -> 1.05f
                            distance <= 1 -> 1.0f
                            else -> 0.95f
                        }
                        val fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal

                        Text(
                            text = line.text,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = if (isCurrent) 18.sp else 16.sp
                            ),
                            color = Color.White.copy(alpha = alpha),
                            textAlign = TextAlign.Center,
                            fontWeight = fontWeight,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = if (isCurrent) 27.sp else 26.sp,
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .padding(vertical = 4.dp)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .animateContentSize(
                                    animationSpec = tween(200)
                                )
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistBottomSheet(
    playQueue: List<MusicSong>,
    currentIndex: Int,
    onSongSelect: (Int) -> Unit,
    onRemoveSong: (Int) -> Unit,
    onClearQueue: () -> Unit,
    onDismiss: () -> Unit
) {

    val isDark = !isLightTheme()
    val sheetBg = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF5F5F5)
    val textPrimary = if (isDark) Color.White else Color(0xFF1A1A1A)
    val textSecondary = if (isDark) Color.White.copy(alpha = 0.5f) else Color(0xFF5A5A5A)
    val textTertiary = if (isDark) Color.White.copy(alpha = 0.35f) else Color(0xFF8A8A8A)
    val dividerColor = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.08f)
    val currentSongBg = if (isDark) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    val dragHandleColor = if (isDark) Color.White.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.2f)

    var showClearConfirm by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        if (playQueue.isNotEmpty() && currentIndex >= 0 && currentIndex < playQueue.size) {
            listState.scrollToItem(currentIndex)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = sheetBg,
        dragHandle = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(3.dp)
                        .background(dragHandleColor, RoundedCornerShape(2.dp))
                )
            }
        },
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        scrimColor = Color.Black.copy(alpha = 0.6f),
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.45f)
                .navigationBarsPadding()
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "播放列表",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
                Spacer(modifier = Modifier.weight(1f))
                if (playQueue.isNotEmpty()) {
                    IconButton(
                        onClick = { showClearConfirm = true },
                        modifier = Modifier.offset(x = 8.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "清空列表",
                            tint = textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            HorizontalDivider(
                color = dividerColor,
                thickness = 0.3.dp
            )

            if (playQueue.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = null,
                            tint = textTertiary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "播放列表为空",
                            style = MaterialTheme.typography.bodyLarge,
                            color = textTertiary
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    itemsIndexed(playQueue, key = { _, song -> song.id }) { index, song ->
                        val isCurrentSong = index == currentIndex

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isCurrentSong) currentSongBg
                                    else Color.Transparent
                                )
                                .clickable { onSongSelect(index) }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MusicCoverImage(
                                model = song.albumArtUri,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.displayTitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isCurrentSong) MaterialTheme.colorScheme.primary else textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = song.displayArtist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            IconButton(
                                onClick = { onRemoveSong(index) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "删除",
                                    tint = textTertiary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = {
                Text(
                    text = "清空播放列表",
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
            },
            text = {
                Text(
                    text = "确定要清空所有歌曲吗？",
                    color = textSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        onClearQueue()
                    }
                ) {
                    Text("确定", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("取消", color = textSecondary)
                }
            },
            containerColor = sheetBg
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeedSelectionBottomSheet(
    currentSpeed: Float,
    onSelectSpeed: (Float) -> Unit,
    onDismiss: () -> Unit
) {

    val isDark = !isLightTheme()
    val sheetBg = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF5F5F5)
    val textPrimary = if (isDark) Color.White else Color(0xFF1A1A1A)
    val textSecondary = if (isDark) Color.White.copy(alpha = 0.5f) else Color(0xFF5A5A5A)
    val textTertiary = if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF5A5A5A).copy(alpha = 0.7f)
    val dividerColor = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.08f)
    val dragHandleColor = if (isDark) Color.White.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.2f)
    val optionBg = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)

    val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = sheetBg,
        dragHandle = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .background(dragHandleColor, RoundedCornerShape(2.dp))
                )
            }
        },
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        scrimColor = Color.Black.copy(alpha = 0.6f),
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "播放速度",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "当前: ${currentSpeed}x",
                    style = MaterialTheme.typography.bodySmall,
                    color = textSecondary
                )
            }

            HorizontalDivider(
                color = dividerColor,
                thickness = 0.5.dp
            )

            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                speeds.chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { speed ->
                            val isSelected = kotlin.math.abs(speed - currentSpeed) < 0.01f
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        onSelectSpeed(speed)
                                        onDismiss()
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else optionBg
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${speed}x",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else textTertiary
                                    )
                                }
                            }
                        }

                        repeat(4 - row.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingCard(
    song: MusicSong,
    isPlaying: Boolean,
    viewModel: MainViewModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {

    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val progress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        color = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        shape = RoundedCornerShape(0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                MusicCoverImage(
                    model = song.albumArtUri,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onClick() },
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onClick() },
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = song.displayTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.displaySubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.previous() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            contentDescription = "上一首",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.togglePlayPause() },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "暂停" else "播放",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.next() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.SkipNext,
                            contentDescription = "下一首",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            var miniSliderPos by remember { mutableStateOf<Float?>(null) }
            val miniDisplayPos = miniSliderPos ?: (if (duration > 0) progress else 0f)
            Slider(
                value = miniDisplayPos.coerceIn(0f, 1f),
                onValueChange = { miniSliderPos = it },
                onValueChangeFinished = {
                    miniSliderPos?.let { viewModel.seekToPercent(it) }
                    miniSliderPos = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                thumb = {
                    Spacer(modifier = Modifier.size(0.dp))
                },
                colors = SliderDefaults.colors(
                    thumbColor = Color.Transparent,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                )
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                thickness = 0.5.dp
            )
        }
    }
}

fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

@Composable
fun MusicCoverImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    showPlaceholder: Boolean = true,
    placeholderColor: Color? = null
) {

    var imageState by remember(model) { mutableStateOf<ImageLoadState>(ImageLoadState.LOADING) }

    Box(modifier = modifier) {

        if (showPlaceholder && (model == null || imageState != ImageLoadState.SUCCESS)) {
            val isDark = !isLightTheme()
            val primaryColor = MaterialTheme.colorScheme.primary
            val bgColor = placeholderColor ?: if (isDark) {
                primaryColor.copy(alpha = 0.12f).compositeOver(Color(0xFF1A1A1A))
            } else {
                primaryColor.copy(alpha = 0.08f).compositeOver(Color(0xFFF5F5F5))
            }
            val iconTint = primaryColor
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor),
                contentAlignment = Alignment.Center
            ) {

                if (model == null || imageState == ImageLoadState.ERROR) {
                    BoxWithConstraints {
                        val iconSize = minOf(maxWidth, maxHeight) * 0.5f
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = "默认封面",
                            tint = iconTint,
                            modifier = Modifier.size(iconSize)
                        )
                    }
                }
            }
        }

        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = if (model != null && imageState == ImageLoadState.SUCCESS) 1f else 0f
                },
            contentScale = contentScale,
            onSuccess = { imageState = ImageLoadState.SUCCESS },
            onError = { imageState = ImageLoadState.ERROR }
        )
    }
}

private enum class ImageLoadState {
    LOADING, SUCCESS, ERROR
}
