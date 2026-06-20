package com.example.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.player.DarkModeManager
import com.example.player.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBackClick: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val darkModeEnabled by DarkModeManager.darkModeEnabled.collectAsStateWithLifecycle()

    Column(modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f).compositeOver(MaterialTheme.colorScheme.background))
    ) {

        Spacer(modifier = Modifier.statusBarsPadding())

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = MaterialTheme.colorScheme.onBackground)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "设置",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {

            SettingsSectionHeader(
                icon = Icons.Default.Settings,
                title = "外观"
            )
            SettingSwitchItem(
                icon = Icons.Default.ModeNight,
                title = "深色模式",
                subtitle = when (darkModeEnabled) {
                    true -> "已开启"
                    false -> "已关闭"
                    null -> "跟随系统"
                },
                checked = darkModeEnabled == true,
                onCheckedChange = { isChecked ->
                    DarkModeManager.setDarkMode(if (isChecked) true else null)
                    coroutineScope.launch {
                        if (isChecked) {
                            com.example.player.ui.theme.DarkModePreference.setDarkModeEnabled(context, true)
                        } else {
                            com.example.player.ui.theme.DarkModePreference.clearDarkModePreference(context)
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            SettingsSectionHeader(
                icon = Icons.Default.Info,
                title = "关于"
            )
            SettingItem(icon = Icons.Default.Info, title = "版本", subtitle = "1.0")
            SettingItem(icon = Icons.Default.MusicNote, title = "应用名称", subtitle = "音乐播放器")

            Spacer(modifier = Modifier.height(24.dp))

            SettingsSectionHeader(
                icon = Icons.Default.VolumeUp,
                title = "音频"
            )
            SettingItem(icon = Icons.Default.VolumeUp, title = "音频输出", subtitle = "媒体流")
            SettingItem(icon = Icons.Default.SkipNext, title = "无缝播放", subtitle = "已启用")

            Spacer(modifier = Modifier.height(24.dp))

            SettingsSectionHeader(
                icon = Icons.Default.Storage,
                title = "存储"
            )
            SettingItem(icon = Icons.Default.Storage, title = "专辑封面缓存", subtitle = "50 MB")

            Spacer(modifier = Modifier.height(24.dp))

            SettingsSectionHeader(
                icon = Icons.Default.Security,
                title = "权限"
            )
            SettingItem(icon = Icons.Default.Album, title = "音频文件访问", subtitle = "已授权")
            SettingItem(icon = Icons.Default.Notifications, title = "通知权限", subtitle = "已授权")
        }
    }
}

@Composable
private fun SettingsSectionHeader(
    icon: ImageVector,
    title: String
) {
    Row(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SettingItem(icon: ImageVector, title: String, subtitle: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f).compositeOver(MaterialTheme.colorScheme.surface)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun SettingSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f).compositeOver(MaterialTheme.colorScheme.surface)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
