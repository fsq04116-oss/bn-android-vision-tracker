package com.visionoverlay.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.tencent.yolov8ncnn.YOLOv8Ncnn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class MainActivity : ComponentActivity(), SurfaceHolder.Callback {
    private lateinit var cameraView: SurfaceView
    private lateinit var overlay: OverlayView
    private lateinit var root: FrameLayout
    private lateinit var chrome: FrameLayout
    private lateinit var panel: LinearLayout
    private lateinit var zoomPanel: LinearLayout
    private lateinit var floatingButton: Button
    private lateinit var statusView: TextView
    private lateinit var loadingView: TextView
    private lateinit var pauseButton: Button
    private lateinit var store: SettingsStore
    private lateinit var capture: YOLOv8Ncnn
    private var settings = OverlaySettings()
    private var paused = false
    private var facing = 1
    private var surfaceReady = false
    private var resumed = false
    private var cameraStarted = false
    private var preparing = false
    private var preparedSettings: OverlaySettings? = null
    private var zoomRatio = 1f
    private var controlsVisible = true
    private var rootAvailable = false
    private val fpsHandler = Handler(Looper.getMainLooper())
    private val fpsUpdater = object : Runnable {
        override fun run() {
            if (!resumed || !::statusView.isInitialized) return
            val fps = if (cameraStarted && ::capture.isInitialized) capture.getFps() else 0f
            statusView.text = "${"%.1f".format(fps)} FPS  ·  ${if (paused) "已暂停" else if (rootAvailable) "Root" else "本地识别"}"
            statusView.visibility = if (settings.stats) View.VISIBLE else View.GONE
            fpsHandler.postDelayed(this, 500L)
        }
    }
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) maybeStartCapture() else if (::loadingView.isInitialized) {
            loadingView.text = "需要相机权限。点击此处重试"; loadingView.visibility = View.VISIBLE
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!AppRuntime.validated) { startActivity(Intent(this, LaunchActivity::class.java)); finish(); return }
        store = SettingsStore(this); settings = store.read(); controlsVisible = settings.controlsVisible
        buildUi(); prepareModels()
        lifecycleScope.launch { rootAvailable = withContext(Dispatchers.IO) { RootStatus.refresh() } }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            cameraPermission.launch(Manifest.permission.CAMERA)
    }
    private fun d(value: Int) = AppUi.dp(this, value)
    private fun buildUi() {
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        cameraView = SurfaceView(this).also { it.holder.setFormat(PixelFormat.RGBA_8888); it.holder.addCallback(this) }
        overlay = OverlayView(this)
        root.addView(cameraView, FrameLayout.LayoutParams(-1, -1)); root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        chrome = FrameLayout(this)
        root.addView(chrome, FrameLayout.LayoutParams(-1, -1))
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = AppUi.panel(this@MainActivity, AppUi.cameraMaterial, 24); setPadding(d(6), d(6), d(6), d(6))
        }
        fun addControl(label: String, primary: Boolean = false, action: () -> Unit): Button = AppUi.cameraButton(this, label, primary, action).also {
            panel.addView(it, LinearLayout.LayoutParams(-2, d(48)).apply { marginEnd = d(4) })
        }
        pauseButton = addControl("暂停识别", true) {
            paused = !paused; pauseButton.text = if (paused) "继续识别" else "暂停识别"
            if (paused) stopCapture() else maybeStartCapture()
        }
        addControl("切换镜头") { facing = 1 - facing; stopCapture(); maybeStartCapture() }
        addControl("设置") { startActivity(Intent(this, SettingsActivity::class.java)) }
        addControl("收起") { setControlsVisible(false) }
        chrome.addView(panel, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        val zoomLabel = AppUi.label(this, "1.0×", 15f, Color.WHITE).apply {
            setPadding(d(16), 0, d(4), 0); typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val zoomBar = SeekBar(this).apply {
            max = 70; contentDescription = "镜头缩放，一到八倍"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, user: Boolean) {
                    zoomRatio = 1f + progress / 10f; zoomLabel.text = "%.1f×".format(zoomRatio)
                    if (cameraStarted) capture.setZoom(zoomRatio)
                }
                override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                override fun onStopTrackingTouch(bar: SeekBar?) = Unit
            })
        }
        zoomPanel = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; background = AppUi.panel(this@MainActivity, AppUi.cameraMaterial, 24)
            addView(zoomLabel); addView(zoomBar, LinearLayout.LayoutParams(d(160), d(48)))
        }
        chrome.addView(zoomPanel, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = d(72) })
        statusView = AppUi.label(this, "0.0 FPS · 本地识别", 13f, Color.WHITE).apply {
            setPadding(d(14), d(10), d(14), d(10)); background = AppUi.panel(this@MainActivity, AppUi.cameraMaterial, 24)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        chrome.addView(statusView, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END))
        chrome.addView(AppUi.heading(this, "白柠视觉辅助", 20f).apply {
            setTextColor(Color.WHITE); setShadowLayer(d(3).toFloat(), 0f, 1f, Color.BLACK)
        }, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { topMargin = d(8) })
        loadingView = AppUi.label(this, "正在连接相机…", 16f).apply {
            setPadding(d(24), d(16), d(24), d(16)); background = AppUi.panel(this@MainActivity)
            setOnClickListener {
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
                    cameraPermission.launch(Manifest.permission.CAMERA)
                else prepareModels()
            }
        }
        chrome.addView(loadingView, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        // Activity-local overlay: no su and no SYSTEM_ALERT_WINDOW permission.
        floatingButton = AppUi.cameraButton(this, "操作") { setControlsVisible(!controlsVisible) }.apply {
            contentDescription = "显示或隐藏操作面板，可拖动"
            background = AppUi.panel(this@MainActivity, AppUi.cameraMaterial, 28)
        }
        chrome.addView(floatingButton, FrameLayout.LayoutParams(-2, d(48), Gravity.TOP or Gravity.START).apply { topMargin = d(72) })
        makeDraggable(floatingButton); setContentView(root); AppUi.safeArea(chrome, 20); applyControlVisibility()
    }
    private fun makeDraggable(button: View) {
        var downX = 0f; var downY = 0f; var startX = 0f; var startY = 0f; var dragged = false
        val slop = android.view.ViewConfiguration.get(this).scaledTouchSlop
        button.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY; startX = view.x; startY = view.y; dragged = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX; val dy = event.rawY - downY
                    if (abs(dx) > slop || abs(dy) > slop) dragged = true
                    if (dragged) {
                        view.x = (startX + dx).coerceIn(chrome.paddingLeft.toFloat(), (chrome.width - chrome.paddingRight - view.width).coerceAtLeast(chrome.paddingLeft).toFloat())
                        view.y = (startY + dy).coerceIn(chrome.paddingTop.toFloat(), (chrome.height - chrome.paddingBottom - view.height).coerceAtLeast(chrome.paddingTop).toFloat())
                    }; true
                }
                MotionEvent.ACTION_UP -> { if (!dragged) view.performClick(); true }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
    }
    private fun setControlsVisible(show: Boolean) {
        controlsVisible = show; settings = settings.copy(controlsVisible = show); store.write(settings); applyControlVisibility()
    }
    private fun applyControlVisibility() {
        if (!::panel.isInitialized) return
        AppUi.reveal(panel, controlsVisible); AppUi.reveal(zoomPanel, controlsVisible)
        // Always leave a touch escape if volume-key controls have been disabled.
        floatingButton.visibility = if (settings.floatingControls || !settings.volumeControls) View.VISIBLE else View.GONE
        floatingButton.text = if (controlsVisible) "收起" else "操作"
    }
    private fun handlesVolumeKey(keyCode: Int) = ::store.isInitialized && settings.volumeControls &&
        (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (handlesVolumeKey(keyCode)) {
            if (event.repeatCount == 0) setControlsVisible(keyCode == KeyEvent.KEYCODE_VOLUME_UP)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        if (handlesVolumeKey(keyCode)) true else super.onKeyUp(keyCode, event)
    private fun prepareModels() {
        if (preparing || !::store.isInitialized) return
        preparing = true; val requested = settings; stopCapture(); preparedSettings = null
        loadingView.visibility = View.VISIBLE; loadingView.text = "正在准备相机…"
        lifecycleScope.launch {
            try {
                capture = AppRuntime.prepare(assets, requested); preparedSettings = requested
                if (surfaceReady) capture.setOutputWindow(cameraView.holder.surface)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { loadingView.text = "初始化失败，点击重试" }
            finally { preparing = false }
            if (preparedSettings != null) maybeStartCapture()
        }
    }
    private fun maybeStartCapture() {
        if (paused || preparing || preparedSettings == null || !surfaceReady || !resumed || !::capture.isInitialized) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
        if (!cameraStarted) {
            cameraStarted = capture.openCamera(facing)
            if (cameraStarted) {
                capture.setZoom(zoomRatio); capture.setInputSize(settings.inputSize); loadingView.visibility = View.GONE
            } else { loadingView.text = "相机打开失败，点击重试"; loadingView.visibility = View.VISIBLE }
        }
    }
    private fun stopCapture() { if (cameraStarted && ::capture.isInitialized) { capture.closeCamera(); cameraStarted = false } }
    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true; if (::capture.isInitialized) capture.setOutputWindow(holder.surface); maybeStartCapture()
    }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { if (::capture.isInitialized) capture.setOutputWindow(holder.surface) }
    override fun surfaceDestroyed(holder: SurfaceHolder) { surfaceReady = false; stopCapture() }
    override fun onResume() {
        super.onResume(); resumed = true
        if (::store.isInitialized) {
            val updated = store.read()
            val reload = updated.backend != settings.backend || updated.modelMode != settings.modelMode
            settings = updated; controlsVisible = updated.controlsVisible
            applyControlVisibility(); overlay.update(null, settings)
            if (reload) prepareModels() else maybeStartCapture()
            fpsHandler.removeCallbacks(fpsUpdater); fpsHandler.post(fpsUpdater)
        }
    }
    override fun onPause() { resumed = false; fpsHandler.removeCallbacks(fpsUpdater); stopCapture(); super.onPause() }
    override fun onDestroy() { fpsHandler.removeCallbacks(fpsUpdater); resumed = false; surfaceReady = false; stopCapture(); super.onDestroy() }
}
