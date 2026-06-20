package com.example.player.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.ComponentCallbacks2
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import androidx.compose.ui.graphics.toArgb
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import com.example.player.ThemeColorManager
import com.example.player.data.model.MusicSong
import com.example.player.player.MusicPlayerManager
import com.example.player.ui.theme.getThemeColorOption
import com.example.player.ui.util.LyricsParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class FloatingLyricsService : Service() {

    companion object {
        private const val TAG = "FloatingLyricsService"
        private const val LYRICS_POLL_INTERVAL_MS = 200L

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning

        val COLORS = listOf(
            Color.WHITE to "#FF333333",
            Color.parseColor("#FF3B5C") to null,
            Color.parseColor("#FF9800") to null,
            Color.parseColor("#FFEB3B") to null,
            Color.parseColor("#4CAF50") to null,
            Color.parseColor("#00BCD4") to null,
            Color.parseColor("#2196F3") to null,
            Color.parseColor("#9C27B0") to null,
            Color.parseColor("#E91E63") to null,
        )

        fun start(context: Context) {
            val intent = Intent(context, FloatingLyricsService::class.java)
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingLyricsService::class.java)
            context.stopService(intent)
        }
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var tvCurrentLyric: TextView? = null

    private var menuView: View? = null
    private var menuLayoutParams: WindowManager.LayoutParams? = null
    private var colorRow: LinearLayout? = null
    private var menuBgView: View? = null
    private var menuBgLayoutParams: WindowManager.LayoutParams? = null
    private var currentSlider: CustomSlider? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private var lastClickTime = 0L

    private val handler = Handler(Looper.getMainLooper())
    private var lastLyricText = ""
    private var lyricTextColor = Color.WHITE
    private var lyricTextSize = 22f

    private var lastDarkMode = false

    private var themeColorJob: Job? = null

    private var playerManager: MusicPlayerManager? = null
    private var serviceBound = false
    private var currentLyrics: List<LyricsParser.LyricLine>? = null
    private var lastSongId: Long = -1L
    private var lyricsPollRunnable: Runnable? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? MusicPlaybackService.MusicBinder
            if (binder != null && binder.isReady()) {
                playerManager = binder.getPlayerManager()
                serviceBound = true
                Log.d(TAG, "Bound to MusicPlaybackService, starting lyrics polling")
                startLyricsPolling()
            } else {
                Log.w(TAG, "Service connected but not ready, retrying bind")
                handler.postDelayed({ retryBind() }, 300)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playerManager = null
            serviceBound = false
            stopLyricsPolling()
        }
    }

    private var bindRetryCount = 0

    private fun retryBind() {
        if (serviceBound || bindRetryCount > 20) return
        bindRetryCount++
        try {
            unbindService(serviceConnection)
        } catch (_: Exception) {}
        try {
            val intent = Intent(this, MusicPlaybackService::class.java)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            Log.e(TAG, "Retry bind failed: ${e.message}")
        }
    }

    private fun bindToPlaybackService() {
        try {
            val intent = Intent(this, MusicPlaybackService::class.java)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind to MusicPlaybackService: ${e.message}")
        }
    }

    private fun unbindFromPlaybackService() {
        if (serviceBound) {
            try { unbindService(serviceConnection) } catch (_: Exception) {}
            serviceBound = false
            playerManager = null
        }
    }

    private fun startLyricsPolling() {
        stopLyricsPolling()
        val runnable = object : Runnable {
            override fun run() {
                pollLyrics()
                lyricsPollRunnable = this
                handler.postDelayed(this, LYRICS_POLL_INTERVAL_MS)
            }
        }
        lyricsPollRunnable = runnable
        handler.post(runnable)
    }

    private fun stopLyricsPolling() {
        lyricsPollRunnable?.let { handler.removeCallbacks(it) }
        lyricsPollRunnable = null
    }

    private fun pollLyrics() {
        val pm = playerManager ?: return
        val song = pm.currentSong.value ?: return

        if (song.id != lastSongId) {
            lastSongId = song.id
            lastLyricText = ""
            loadLyricsForSong(song)
        }

        val lyrics = currentLyrics
        if (lyrics.isNullOrEmpty()) {
            if (lastLyricText.isNotEmpty()) {
                lastLyricText = ""
                tvCurrentLyric?.text = "♪"
            }
            return
        }

        try {
            val position = pm.exoPlayer.currentPosition
            val index = LyricsParser.findCurrentLineIndex(lyrics, position)
            val text = if (index >= 0 && index < lyrics.size) lyrics[index].text else ""

            if (text != lastLyricText) {
                lastLyricText = text
                tvCurrentLyric?.text = text.ifEmpty { "♪" }
            }
        } catch (e: Exception) {

            Log.w(TAG, "Error polling lyrics: ${e.message}")
        }
    }

    private fun loadLyricsForSong(song: MusicSong) {
        CoroutineScope(Dispatchers.IO).launch {
            val lyrics = LyricsParser.loadLyricsForSong(song.embeddedLyrics, song.path)
            handler.post {
                currentLyrics = lyrics
                if (lyrics.isNullOrEmpty()) {
                    lastLyricText = ""
                    tvCurrentLyric?.text = "♪"
                }
            }
        }
    }

    private val layoutParams by lazy {
        val dm = resources.displayMetrics
        val screenHeight = dm.heightPixels

        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
            x = 0
            y = screenHeight / 4
        }
    }

    override fun onCreate() {
        super.onCreate()
        loadSettings()
        _isRunning.value = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createFloatingView()

        bindToPlaybackService()

        lastDarkMode = isDarkMode()
        registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) {
                val newDark = isDarkMode()
                if (newDark != lastDarkMode) {
                    lastDarkMode = newDark
                    handler.post { rebuildMenuIfNeeded() }
                }
            }
            override fun onLowMemory() {}
            override fun onTrimMemory(level: Int) {}
        })

        themeColorJob = CoroutineScope(Dispatchers.Main).launch {
            ThemeColorManager.themeColorIndex.collect { index ->
                updateSliderThemeColor()
            }
        }
    }

    private fun updateSliderThemeColor() {
        val color = getThemeColor()
        currentSlider?.accentColor = color
    }

    private fun rebuildMenuIfNeeded() {
        if (menuView != null) {
            dismissMenu()
            showMenu()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatingView() {
        val dm = resources.displayMetrics
        val screenWidth = dm.widthPixels

        tvCurrentLyric = TextView(this).apply {
            setTextColor(lyricTextColor)
            textSize = lyricTextSize
            setSingleLine(false)
            maxLines = 3
            maxWidth = (screenWidth * 0.9).toInt()
            setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            gravity = Gravity.CENTER
        }

        val frameLayout = FrameLayout(this).apply {
            addView(tvCurrentLyric)
        }

        tvCurrentLyric?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (dx * dx + dy * dy > 100) {
                        isDragging = true
                    }
                    layoutParams.x = initialX + dx.toInt()
                    layoutParams.y = initialY + dy.toInt()
                    clampPosition()
                    windowManager?.updateViewLayout(frameLayout, layoutParams)
                    syncMenuPosition()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        val now = System.currentTimeMillis()
                        if (now - lastClickTime < 300) toggleMenu()
                        lastClickTime = now
                    }
                    true
                }
                else -> false
            }
        }

        floatingView = frameLayout
        try {
            windowManager?.addView(frameLayout, layoutParams)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add floating view: ${e.message}")
        }
    }

    private fun clampPosition() {
        val dm = resources.displayMetrics
        val sw = dm.widthPixels
        val sh = dm.heightPixels
        val vw = floatingView?.width ?: 0
        val vh = floatingView?.height ?: 0
        val margin = dp(10)

        val halfScreen = sw / 2
        val maxX = halfScreen - margin
        layoutParams.x = layoutParams.x.coerceIn(-maxX, maxX)

        val minY = margin
        val maxY = sh - vh - margin
        layoutParams.y = layoutParams.y.coerceIn(minY, maxY)
    }

    private fun calcMenuY(): Int {
        val dm = resources.displayMetrics
        val sh = dm.heightPixels
        val lyricBottom = layoutParams.y + (floatingView?.height ?: dp(40))
        val menuHeight = menuView?.height ?: dp(200)
        val margin = dp(10)

        val belowY = lyricBottom + dp(8)
        val aboveY = layoutParams.y - menuHeight - dp(8)

        return if (belowY + menuHeight + margin <= sh) {
            belowY
        } else {
            aboveY.coerceAtLeast(margin)
        }
    }

    private fun syncMenuPosition() {
        menuLayoutParams?.let { p ->
            p.x = layoutParams.x
            p.y = calcMenuY()
            try {
                menuView?.let { windowManager?.updateViewLayout(it, p) }
            } catch (_: Exception) {}
        }
    }

    private inner class CustomSlider @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyle: Int = 0
    ) : View(context, attrs, defStyle) {

        private val density = resources.displayMetrics.density
        private val trackH = 4f * density
        private val thumbR = 7f * density
        private val touchArea = 20f * density

        var min = 12f
        var max = 52f
        var value = 22f
            set(v) {
                field = v.coerceIn(min, max)
                invalidate()
            }

        var accentColor = Color.BLUE
        var trackBgColor = Color.parseColor("#FFE0E0E0")
        var onValueChanged: ((Float) -> Unit)? = null

        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        private var isPressed = false

        init {
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isPressed = true
                        updateValue(event.x)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        updateValue(event.x)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isPressed = false
                        invalidate()
                        true
                    }
                    else -> false
                }
            }
        }

        private fun updateValue(touchX: Float) {
            val pad = thumbR + paddingLeft
            val usable = width - pad - paddingRight - thumbR
            if (usable <= 0) return
            val ratio = ((touchX - pad) / usable).coerceIn(0f, 1f)
            value = min + ratio * (max - min)
            onValueChanged?.invoke(value)
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val h = (touchArea * 2).toInt()
            setMeasuredDimension(getDefaultSize(suggestedMinimumWidth, widthMeasureSpec), h)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cy = height / 2f
            val padLeft = thumbR + paddingLeft
            val padRight = thumbR + paddingRight
            val trackLeft = padLeft
            val trackRight = width - padRight
            val trackW = trackRight - trackLeft

            trackPaint.color = trackBgColor
            val halfTrack = trackH / 2f
            canvas.drawRoundRect(trackLeft, cy - halfTrack, trackRight, cy + halfTrack, halfTrack, halfTrack, trackPaint)

            val ratio = (value - min) / (max - min)
            val fillRight = trackLeft + trackW * ratio
            fillPaint.color = accentColor
            canvas.drawRoundRect(trackLeft, cy - halfTrack, fillRight, cy + halfTrack, halfTrack, halfTrack, fillPaint)

            val thumbX = trackLeft + trackW * ratio
            val currentR = if (isPressed) thumbR * 1.2f else thumbR
            thumbPaint.color = accentColor
            canvas.drawCircle(thumbX, cy, currentR, thumbPaint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun toggleMenu() {
        if (menuView != null) { dismissMenu(); return }
        showMenu()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showMenu() {
        val dm = resources.displayMetrics
        val screenWidth = dm.widthPixels
        val isDark = isDarkMode()
        val themeColor = getThemeColor()
        val m3 = ContextThemeWrapper(this, com.google.android.material.R.style.Theme_Material3_DynamicColors_DayNight)
        val subtextColor = if (isDark) Color.parseColor("#FFB0B0B0") else Color.parseColor("#FF666666")
        val dividerColor = if (isDark) Color.parseColor("#FF444444") else Color.parseColor("#FFE0E0E0")

        val card = MaterialCardView(m3).apply {
            radius = dp(16).toFloat()
            cardElevation = dp(6).toFloat()
            setCardBackgroundColor(if (isDark) Color.parseColor("#FF2B2B2B") else Color.WHITE)
            strokeWidth = 0
            useCompatPadding = false
            setContentPadding(dp(20), dp(18), dp(20), dp(18))
        }

        val root = LinearLayout(m3).apply { orientation = LinearLayout.VERTICAL }

        root.addView(TextView(m3).apply {
            text = "颜色"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(subtextColor)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, dp(8))
            }
        })

        colorRow = LinearLayout(m3).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        COLORS.forEach { (color, borderHex) ->
            val size = dp(32)
            val container = FrameLayout(m3).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    setMargins(0, 0, dp(10), 0)
                }
            }

            val ball = View(m3).apply {
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    borderHex?.let {
                        val widthPx = if (color == Color.WHITE) (0.5f * resources.displayMetrics.density).toInt()
                                       else dp(1)
                        setStroke(widthPx, Color.parseColor(it))
                    }
                }
            }

            val check = TextView(m3).apply {
                text = "✓"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(if (color == Color.WHITE) Color.BLACK else Color.WHITE)
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                visibility = View.GONE
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }

            container.addView(ball)
            container.addView(check)
            container.setOnClickListener {
                lyricTextColor = color
                tvCurrentLyric?.setTextColor(color)
                saveSettings()
                refreshCheckMarks(color)
            }
            colorRow?.addView(container)
        }

        root.addView(HorizontalScrollView(m3).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            setPadding(dp(4), 0, dp(4), 0)
            addView(colorRow)
        })

        root.addView(View(m3).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
                setMargins(0, dp(12), 0, dp(12))
            }
            setBackgroundColor(dividerColor)
        })

        root.addView(TextView(m3).apply {
            text = "字体大小"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(subtextColor)
        })

        currentSlider = CustomSlider(this).apply {
            min = 12f
            max = 52f
            value = lyricTextSize
            accentColor = themeColor
            trackBgColor = if (isDark) Color.parseColor("#FF444444") else Color.parseColor("#FFE0E0E0")
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, dp(4), 0, 0)
            }
            onValueChanged = { v ->
                lyricTextSize = v
                tvCurrentLyric?.textSize = v
                saveSettings()
                handler.post { syncMenuPosition() }
            }
        }
        root.addView(currentSlider)

        card.addView(root)
        menuView = card

        menuBgView = View(this).apply {
            setOnTouchListener { _, _ ->
                dismissMenu()
                true
            }
        }
        menuBgLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        )

        val shortSide = minOf(dm.widthPixels, dm.heightPixels)
        val menuWidth = (shortSide * 0.82).toInt()
        menuLayoutParams = WindowManager.LayoutParams(
            menuWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
            x = layoutParams.x
            y = calcMenuY()
        }

        try {
            windowManager?.addView(menuBgView, menuBgLayoutParams)
            windowManager?.addView(card, menuLayoutParams)
            refreshCheckMarks(lyricTextColor)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add menu: ${e.message}")
        }
    }

    private fun refreshCheckMarks(selectedColor: Int) {
        val row = colorRow ?: return
        for (i in 0 until row.childCount) {
            val container = row.getChildAt(i) as? FrameLayout ?: continue
            val check = container.getChildAt(1) as? TextView ?: continue
            val (color, _) = COLORS[i]
            val isSel = color == selectedColor
            check.visibility = if (isSel) View.VISIBLE else View.GONE
            if (isSel) {
                check.setTextColor(if (color == Color.WHITE) Color.BLACK else Color.WHITE)
            }
        }
    }

    private fun dismissMenu() {
        menuView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        menuBgView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        menuView = null
        menuLayoutParams = null
        menuBgView = null
        menuBgLayoutParams = null
        colorRow = null
        currentSlider = null
    }

    private fun isDarkMode(): Boolean {
        return (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private fun getThemeColor(): Int {
        val index = ThemeColorManager.themeColorIndex.value
        val option = getThemeColorOption(index)
        return option.primary.toArgb()
    }

    private fun saveSettings() {
        getSharedPreferences("floating_lyrics_settings", Context.MODE_PRIVATE).edit().apply {
            putInt("text_color", lyricTextColor)
            putFloat("text_size", lyricTextSize)
            apply()
        }
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("floating_lyrics_settings", Context.MODE_PRIVATE)
        lyricTextColor = prefs.getInt("text_color", Color.WHITE)
        lyricTextSize = prefs.getFloat("text_size", 22f)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        _isRunning.value = false
        stopLyricsPolling()
        unbindFromPlaybackService()
        themeColorJob?.cancel()
        handler.removeCallbacksAndMessages(null)
        dismissMenu()
        floatingView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
            floatingView = null
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
