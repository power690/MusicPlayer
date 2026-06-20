package com.example.player

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.example.player.ui.theme.DarkModePreference
import com.example.player.ui.theme.MusicPlayerTheme
import com.example.player.ui.theme.ThemeColorPreference
import com.example.player.ui.theme.DarkBackground
import com.example.player.ui.theme.LightBackground
import com.example.player.ui.theme.DarkBottomBarBackground
import com.example.player.ui.theme.BottomBarBackground
import com.example.player.ui.theme.getThemeColorOption
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val EXTRA_OPEN_PLAYER = "open_player"
    }

    private val viewModel: MainViewModel by viewModels()

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.onPermissionGranted()
        } else {
            Toast.makeText(this, "需要音频权限才能扫描音乐文件", Toast.LENGTH_LONG).show()
        }

        requestNotificationPermission()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val initialDarkMode = DarkModePreference.getDarkModeSync(applicationContext)
        DarkModeManager.setDarkMode(initialDarkMode)

        val initialThemeColorIndex = ThemeColorPreference.getThemeColorIndexSync(applicationContext)
        ThemeColorManager.setThemeColorIndex(initialThemeColorIndex)

        lifecycleScope.launch {
            DarkModePreference.darkModeEnabledFlow(applicationContext)
                .catch { e ->
                    Log.e(TAG, "Error observing dark mode preference", e)
                }
                .collect { enabled ->
                    try {
                        DarkModeManager.setDarkMode(enabled)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error setting dark mode", e)
                    }
                }
        }

        lifecycleScope.launch {
            ThemeColorPreference.themeColorIndexFlow(applicationContext)
                .catch { e ->
                    Log.e(TAG, "Error observing theme color preference", e)
                }
                .collect { index ->
                    try {
                        ThemeColorManager.setThemeColorIndex(index)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error setting theme color", e)
                    }
                }
        }

        requestAllPermissions()

        if (intent?.getBooleanExtra(EXTRA_OPEN_PLAYER, false) == true) {
            window.decorView.post {
                viewModel.showPlayer()
            }
        }

        try {
            setContent {
                val darkModePref by DarkModeManager.darkModeEnabled.collectAsState()
                val themeColorIndex by ThemeColorManager.themeColorIndex.collectAsState()

                MusicPlayerTheme(forceDarkMode = darkModePref, themeColorIndex = themeColorIndex) {
                    val navController = androidx.navigation.compose.rememberNavController()

                    MusicApp(
                        navController = navController,
                        viewModel = viewModel,
                        requestAudioPermission = { requestAudioPermission() }
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error in setContent", e)
            Toast.makeText(this, "界面初始化失败，请重启应用", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestAllPermissions() {
        val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, audioPermission) != PackageManager.PERMISSION_GRANTED) {

            markAudioPermissionRequested()

            audioPermissionLauncher.launch(audioPermission)
        } else {

            viewModel.onPermissionGranted()
            requestNotificationPermission()
        }
    }

    private fun requestAudioPermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {

            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                viewModel.onPermissionGranted()
            }

            hasRequestedAudioPermissionBefore() && !shouldShowRequestPermissionRationale(permission) -> {
                Toast.makeText(
                    this,
                    "权限被永久禁止，需手动授权",
                    Toast.LENGTH_LONG
                ).show()
                openAppPermissionSettings()
            }

            else -> {
                markAudioPermissionRequested()
                audioPermissionLauncher.launch(permission)
            }
        }
    }

    private fun hasRequestedAudioPermissionBefore(): Boolean {
        val prefs = getSharedPreferences("permission_state", MODE_PRIVATE)
        return prefs.getBoolean("audio_permission_requested", false)
    }

    private fun markAudioPermissionRequested() {
        getSharedPreferences("permission_state", MODE_PRIVATE)
            .edit()
            .putBoolean("audio_permission_requested", true)
            .apply()
    }

    private fun openAppPermissionSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "无法打开应用权限设置：${e.message}")
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {

            checkAndOpenNotificationSettingsForLegacyAndroid()
        }
    }

    private fun checkAndOpenNotificationSettingsForLegacyAndroid() {
        try {
            val areEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled()
            if (!areEnabled) {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "打开通知设置失败：${e.message}")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        if (intent.getBooleanExtra(EXTRA_OPEN_PLAYER, false)) {
            viewModel.showPlayer()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            val showPlayerOverlay = viewModel.showPlayerOverlay.value
            val darkModePref = DarkModeManager.darkModeEnabled.value
            val useDarkTheme = darkModePref ?: (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES)
            val lightStatusBar = if (showPlayerOverlay) false else !useDarkTheme
            applyStatusBarIcons(window, lightStatusBar)

            applyStatusBarColor(window, showPlayerOverlay, useDarkTheme)
        }
    }

    override fun onResume() {
        super.onResume()

        val showPlayerOverlay = viewModel.showPlayerOverlay.value
        val darkModePref = DarkModeManager.darkModeEnabled.value
        val useDarkTheme = darkModePref ?: (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES)
        val lightStatusBar = if (showPlayerOverlay) false else !useDarkTheme
        applyStatusBarIcons(window, lightStatusBar)
        applyStatusBarColor(window, showPlayerOverlay, useDarkTheme)

        checkAudioPermissionOnResume()
    }

    private fun checkAudioPermissionOnResume() {
        try {
            val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_AUDIO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            val granted = ContextCompat.checkSelfPermission(this, audioPermission) ==
                    PackageManager.PERMISSION_GRANTED
            if (granted && !viewModel.hasAudioPermission) {
                Log.d(TAG, "onResume: 检测到权限已被授予（可能在系统设置里开启），触发 onPermissionGranted")
                viewModel.onPermissionGranted()
            }
        } catch (e: Exception) {
            Log.e(TAG, "onResume 检测音频权限失败：${e.message}")
        }
    }

    private fun applyStatusBarColor(window: android.view.Window, showPlayerOverlay: Boolean, useDarkTheme: Boolean) {
        try {
            if (showPlayerOverlay) {
                window.statusBarColor = android.graphics.Color.TRANSPARENT
            } else {
                val bgColor = if (useDarkTheme) DarkBackground else LightBackground
                val barColor = if (useDarkTheme) DarkBottomBarBackground else BottomBarBackground
                val themeColorIndex = ThemeColorManager.themeColorIndex.value
                val themeColor = getThemeColorOption(themeColorIndex)
                val tintedBgColor = themeColor.primary.copy(alpha = 0.05f).compositeOver(bgColor)
                window.statusBarColor = tintedBgColor.toArgb()
                window.navigationBarColor = barColor.toArgb()
            }
            if (Build.VERSION.SDK_INT >= 34) {
                window.isStatusBarContrastEnforced = false
                window.isNavigationBarContrastEnforced = false
            }
        } catch (_: Exception) {}
    }
}
