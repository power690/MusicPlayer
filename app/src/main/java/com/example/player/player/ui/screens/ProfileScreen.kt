package com.example.player.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Person

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.example.player.MainViewModel
import com.example.player.ThemeColorManager
import com.example.player.ui.theme.THEME_COLOR_OPTIONS
import com.example.player.ui.theme.ThemeColorPreference
import com.example.player.ui.theme.UserProfilePreference
import com.example.player.ui.theme.getThemeColorOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private fun copyAvatarToInternal(context: Context, sourceUri: Uri): String {
    val avatarDir = File(context.filesDir, "avatar")
    if (!avatarDir.exists()) avatarDir.mkdirs()

    avatarDir.listFiles()?.forEach { it.delete() }
    val destFile = File(avatarDir, "avatar.jpg")
    context.contentResolver.openInputStream(sourceUri)?.use { input ->
        destFile.outputStream().use { output ->
            input.copyTo(output)
        }
    }
    return destFile.absolutePath
}

@Composable
fun ProfileScreen(
    viewModel: MainViewModel,
    onNavigateToFavorites: () -> Unit,
    onNavigateToLibrary: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = isSystemInDarkTheme()

    val allSongs by viewModel.allSongs.collectAsState()
    val songCount = allSongs.size

    val userName by UserProfilePreference.userNameFlow(context).collectAsState(initial = "用户")
    val avatarUri by UserProfilePreference.avatarUriFlow(context).collectAsState(initial = null)

    val avatarFile = File(context.filesDir, "avatar/avatar.jpg")
    val avatarModel: Any? = if (avatarFile.exists()) avatarFile else avatarUri?.let { Uri.parse(it) }

    val themeColorIndex by ThemeColorManager.themeColorIndex.collectAsState()
    val themeColor = getThemeColorOption(themeColorIndex)
    val accentColor = MaterialTheme.colorScheme.primary

    var showNameDialog by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf("") }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                withContext(Dispatchers.IO) {

                    val internalPath = copyAvatarToInternal(context, it)
                    UserProfilePreference.setAvatarUri(context, internalPath)
                }
            }
        }
    }

    val launchAvatarPicker: () -> Unit = {
        val getImageIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        val chooserIntent = Intent.createChooser(getImageIntent, "选择头像")
        val resolved = chooserIntent.resolveActivity(context.packageManager)
        if (resolved != null) {
            try {
                imagePickerLauncher.launch("image/*")
            } catch (e: android.content.ActivityNotFoundException) {
                Toast.makeText(context, "暂无法设置头像：未找到图片选择器", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "暂无法设置头像", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(context, "暂无法设置头像：未找到图片选择器", Toast.LENGTH_LONG).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
            .padding(horizontal = 20.dp)
    ) {

        Spacer(modifier = Modifier.statusBarsPadding())

        Text(
            text = "我的",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    nameInput = userName
                    showNameDialog = true
                }
                .padding(start = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        if (isDark) Color.White.copy(alpha = 0.12f)
                        else accentColor.copy(alpha = 0.12f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (avatarModel != null) {
                    MusicCoverImage(
                        model = avatarModel,
                        contentDescription = "头像",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                        showPlaceholder = false
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = "默认头像",
                        tint = accentColor,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(24.dp))

            Column {
                Text(
                    text = userName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "点击查看个人资料",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ProfileStatCard(
                icon = Icons.Filled.Headphones,
                title = "${songCount}首",
                subtitle = "本地歌曲",
                accentColor = accentColor,
                modifier = Modifier.weight(1f),
                onClick = onNavigateToLibrary
            )
            ProfileStatCard(
                icon = Icons.Filled.Favorite,
                title = "收藏",
                subtitle = "我的收藏",
                accentColor = Color(0xFFE91E63),
                modifier = Modifier.weight(1f),
                onClick = onNavigateToFavorites
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        val optionBgColor = if (isDark) Color(0xFF2C2C2C) else accentColor.copy(alpha = 0.05f).compositeOver(Color.White)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(optionBgColor, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                text = "主题色",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                THEME_COLOR_OPTIONS.forEach { option ->
                    val isSelected = themeColorIndex == option.index
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(option.primary)
                            .clickable {
                                scope.launch {
                                    ThemeColorPreference.setThemeColorIndex(context, option.index)
                                    ThemeColorManager.setThemeColorIndex(option.index)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Text(
                                text = "✓",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            ProfileOptionItem(
                title = "版本号",
                value = "1.0"
            )
        }

        Spacer(modifier = Modifier.weight(1f))
    }

    if (showNameDialog) {
        val dialogBg = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF5F5F5)
        val textPrimary = if (isDark) Color.White else Color(0xFF1A1A1A)
        val textSecondary = if (isDark) Color.White.copy(alpha = 0.5f) else Color(0xFF5A5A5A)

        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = {
                Text(
                    text = "个人资料",
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .clip(CircleShape)
                            .background(
                                if (isDark) Color.White.copy(alpha = 0.12f)
                                else accentColor.copy(alpha = 0.12f)
                            )
                            .clickable { launchAvatarPicker() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (avatarModel != null) {
                            MusicCoverImage(
                                model = avatarModel,
                                contentDescription = "头像",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop,
                                showPlaceholder = false
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = "默认头像",
                                tint = accentColor,
                                modifier = Modifier.size(48.dp)
                            )
                        }

                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        singleLine = true,
                        placeholder = { Text("请输入昵称", color = textSecondary) },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary,
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = textSecondary.copy(alpha = 0.3f),
                            cursorColor = accentColor
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (nameInput.isNotBlank()) {
                            scope.launch {
                                UserProfilePreference.setUserName(context, nameInput.trim())
                            }
                        }
                        showNameDialog = false
                    }
                ) {
                    Text("确定", color = accentColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) {
                    Text("取消", color = textSecondary)
                }
            },
            containerColor = dialogBg
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileStatCard(
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
        modifier = modifier.height(84.dp),
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
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val iconBgColor = if (isDark) {
                    accentColor.copy(alpha = 0.25f)
                } else {
                    accentColor.copy(alpha = 0.18f)
                }
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(iconBgColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
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
private fun ProfileOptionItem(
    title: String,
    value: String,
    onClick: (() -> Unit)? = null
) {
    val isDark = isSystemInDarkTheme()
    val accentColor = MaterialTheme.colorScheme.primary
    val bgColor = if (isDark) Color(0xFF2C2C2C) else accentColor.copy(alpha = 0.05f).compositeOver(Color.White)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(12.dp))
            .then(
                if (onClick != null) Modifier.clickable { onClick() }
                else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}
