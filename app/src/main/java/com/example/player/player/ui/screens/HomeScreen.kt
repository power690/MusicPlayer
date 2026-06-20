package com.example.player.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.content.Context
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.request.ImageRequest
import coil.request.CachePolicy
import com.example.player.MainViewModel
import com.example.player.data.model.MusicSong
import com.example.player.ui.theme.*
import com.example.player.ui.util.BlurHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onSongClick: (MusicSong) -> Unit,
    onSeeAllClick: (String) -> Unit,
    onNavigateToFavorites: () -> Unit,
    onNavigateToLibrary: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onRequestPermission: () -> Unit = {}
) {
    val suggestions by viewModel.suggestions.collectAsState()
    val featured by viewModel.featured.collectAsState()

    val recentlyAdded by viewModel.recentlyAddedSongs.collectAsState()
    val songCount = viewModel.songCount
    val context = LocalContext.current

    val isDark = isSystemInDarkTheme()
    val searchBarColor = if (isDark) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.surface
    }

    if (!viewModel.hasAudioPermission) {

        PermissionRequestScreen(onRequestPermission = onRequestPermission)
        return
    }

    if (viewModel.isScanning) {
        ScanningScreen(songCount = songCount)
        return
    }

    val blurLoaded = viewModel.homeBlurCache

    val coverUrisLoaded = remember { mutableStateMapOf<String, Boolean>() }
    val imageLoader = coil.Coil.imageLoader(context)

    LaunchedEffect(suggestions) {
        if (suggestions.isEmpty()) return@LaunchedEffect

        for (song in suggestions) {
            val uri = song.albumArtUri
            if (uri != null && !blurLoaded.containsKey(uri)) {
                val cached = BlurHelper.getCachedImageBitmap(uri, targetSize = 200, blurPixels = 16)
                if (cached != null) {
                    blurLoaded[uri] = cached
                }
            }
        }

        val urisToLoad = suggestions.mapNotNull { song ->
            val uri = song.albumArtUri
            if (uri != null && !blurLoaded.containsKey(uri)) uri else null
        }

        if (urisToLoad.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                urisToLoad.forEachIndexed { _, uri ->
                    try {
                        val result = BlurHelper.loadBlurredImageBitmap(context, uri, targetSize = 200, blurPixels = 16)
                        withContext(Dispatchers.Main) {
                            blurLoaded[uri] = result
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            blurLoaded[uri] = null
                        }
                    }
                }
            }
        }

        val coverUrisToLoad = suggestions.mapNotNull { song ->
            val uri = song.albumArtUri
            if (uri != null && !coverUrisLoaded.containsKey(uri)) uri else null
        }

        coverUrisToLoad.forEach { uri ->
            val request = ImageRequest.Builder(context)
                .data(uri)
                .crossfade(false)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .allowHardware(true)
                .listener(
                    onSuccess = { _, _ -> coverUrisLoaded[uri] = true },
                    onError = { _, _ -> coverUrisLoaded[uri] = true }
                )
                .build()
            imageLoader.enqueue(request)
        }
    }

    LaunchedEffect(featured, recentlyAdded) {
        val featuredUris = featured.mapNotNull { it.albumArtUri }
            .filter { !coverUrisLoaded.containsKey(it) }
        val recentUris = recentlyAdded.mapNotNull { it.albumArtUri }
            .filter { !coverUrisLoaded.containsKey(it) }

        (featuredUris + recentUris).forEach { uri ->
            val request = ImageRequest.Builder(context)
                .data(uri)
                .crossfade(false)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .allowHardware(true)
                .listener(
                    onSuccess = { _, _ -> coverUrisLoaded[uri] = true },
                    onError = { _, _ -> coverUrisLoaded[uri] = true }
                )
                .build()
            imageLoader.enqueue(request)
        }
    }

    if (!viewModel.homeContentEverShown) {
        val allBlurLoaded = suggestions.all { song ->
            val uri = song.albumArtUri
            uri == null || blurLoaded.containsKey(uri)
        }

        val allCoversLoaded = suggestions.all { song ->
            val uri = song.albumArtUri
            uri == null || coverUrisLoaded[uri] == true
        }

        val isReadyToDisplay = suggestions.isNotEmpty() && allBlurLoaded && allCoversLoaded

        if (isReadyToDisplay) {
            viewModel.markHomeContentShown()
        } else {

            BlurLoadingScreen(songCount = songCount)
            return
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
    ) {

        Spacer(modifier = Modifier.statusBarsPadding())
        Text(
            text = "音乐播放器",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp, start = 16.dp, end = 16.dp)
        )

        val greeting = getGreeting()
        Text(
            text = greeting,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 20.dp, start = 16.dp, end = 16.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 160.dp)
        ) {

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clickable { onNavigateToSearch() },
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = searchBarColor
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "搜索歌曲、艺术家、专辑...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (suggestions.isNotEmpty()) {
                SectionHeader(
                    title = "猜你喜欢",
                    onSeeAll = { onSeeAllClick("猜你喜欢") }
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    suggestions.forEachIndexed { index, song ->

                        key(song.id) {
                            SongCard(
                                song = song,
                                cachedBlurBitmap = song.albumArtUri?.let { blurLoaded[it] },
                                onClick = {
                                    viewModel.playSongFromList(suggestions, index)
                                    onSongClick(song)
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            if (featured.isNotEmpty()) {
                SectionHeader(
                    title = "精选歌曲",
                    onSeeAll = { onSeeAllClick("精选歌曲") }
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    featured.forEachIndexed { index, song ->

                        key(song.id) {
                            FeaturedSongCard(
                                song = song,
                                onClick = {
                                    viewModel.playSongFromList(featured, index)
                                    onSongClick(song)
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            if (recentlyAdded.isNotEmpty()) {
                SectionHeader(
                    title = "最近添加",
                    onSeeAll = { onSeeAllClick("最近添加") }
                )
                Spacer(modifier = Modifier.height(4.dp))

                recentlyAdded.forEachIndexed { index, song ->

                    key(song.id) {
                        SongListItem(
                            song = song,
                            onClick = {
                                viewModel.playSongFromList(recentlyAdded, index)
                                onSongClick(song)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun getGreeting(): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when {
        hour < 6 -> "夜深了，听听舒缓的音乐吧"
        hour < 12 -> "早上好，开始美好的一天"
        hour < 18 -> "下午好，来点音乐放松一下"
        else -> "晚上好，享受音乐时光"
    }
}

@Composable
private fun SectionHeader(
    title: String,
    onSeeAll: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        TextButton(onClick = onSeeAll) {
            Text(
                text = "查看全部",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun SongCard(
    song: MusicSong,
    cachedBlurBitmap: ImageBitmap? = null,
    onClick: () -> Unit
) {
    val blurBitmap = cachedBlurBitmap

    Card(
        modifier = Modifier
            .width(160.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
            ) {
                MusicCoverImage(
                    model = song.albumArtUri,
                    contentDescription = song.displayAlbum,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
            ) {

                if (blurBitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = blurBitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        contentScale = ContentScale.Crop
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(Color.Black.copy(alpha = 0.08f))
                    )
                } else {

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(MaterialTheme.colorScheme.surface)
                    )
                }

                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = song.displayTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = song.displayArtist,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun FeaturedSongCard(
    song: MusicSong,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(280.dp)
            .height(72.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f).compositeOver(MaterialTheme.colorScheme.surface)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            MusicCoverImage(
                model = song.albumArtUri,
                contentDescription = song.displayAlbum,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.displayTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.displayArtist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun SongListItem(
    song: MusicSong,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp, horizontal = 16.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f).compositeOver(MaterialTheme.colorScheme.surface)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MusicCoverImage(
                model = song.albumArtUri,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.displayTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.displayArtist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun QuickStatsBar(
    songCount: Int,
    onNavigateToFavorites: () -> Unit,
    onNavigateToLibrary: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            icon = Icons.Filled.Headphones,
            title = "${songCount}首",
            subtitle = "本地歌曲",
            accentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
            onClick = onNavigateToLibrary
        )
        StatCard(
            icon = Icons.Filled.Favorite,
            title = "收藏",
            subtitle = "我的收藏",
            accentColor = Color(0xFFE91E63),
            modifier = Modifier.weight(1f),
            onClick = onNavigateToFavorites
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val isDark = isSystemInDarkTheme()

    val gradientColors = if (isDark) {
        listOf(
            accentColor.copy(alpha = 0.22f),
            accentColor.copy(alpha = 0.10f),
        )
    } else {
        listOf(
            accentColor.copy(alpha = 0.13f),
            accentColor.copy(alpha = 0.04f),
        )
    }

    Card(
        onClick = onClick ?: {},
        modifier = modifier.height(72.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box {

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(colors = gradientColors),
                        RoundedCornerShape(16.dp)
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                val iconBgColor = if (isDark) {
                    accentColor.copy(alpha = 0.25f)
                } else {
                    accentColor.copy(alpha = 0.18f)
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(iconBgColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isDark) {
                            Color.White.copy(alpha = 0.6f)
                        } else {
                            Color.Black.copy(alpha = 0.5f)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionRequestScreen(onRequestPermission: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                        MaterialTheme.colorScheme.background,
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {

            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val scale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.15f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "iconScale"
            )
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            }
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = "需要访问您的音乐",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "为了扫描和播放本地音乐文件，请授予音频文件读取权限",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 36.dp)
            )
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(52.dp)
            ) {
                Text(
                    text = "授予权限",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
fun ScanningScreen(songCount: Int) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
                        MaterialTheme.colorScheme.background,
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            val infiniteTransition = rememberInfiniteTransition(label = "scan")
            val progress by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "progress"
            )

            Box(
                modifier = Modifier.size(80.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { progress },
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 4.dp,
                    modifier = Modifier.size(80.dp)
                )
                Icon(
                    Icons.Filled.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "正在扫描音乐文件...",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "已发现 $songCount 首歌曲",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun BlurLoadingScreen(songCount: Int) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f),
                        MaterialTheme.colorScheme.background,
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "blurLoad")
            val progress by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "blurProgress"
            )

            Box(
                modifier = Modifier.size(64.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { progress },
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(64.dp)
                )
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "正在准备音乐...",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "$songCount 首歌曲加载中",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
