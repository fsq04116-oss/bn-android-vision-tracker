package com.visionoverlay.app

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Local landscape video and key gate; model work runs off the UI thread. */
class LaunchActivity : ComponentActivity() {
    private lateinit var video: VideoView
    private lateinit var state: TextView
    private lateinit var enter: Button
    private lateinit var progress: ProgressBar
    private var keyDialog: AlertDialog? = null
    private var authorized = false
    private var ready = false
    private var launching = false
    private var visible = false
    private var loading = false
    private var videoPaused = false
    private var playbackPosition = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppRuntime.validated = false
        authorized = savedInstanceState?.getBoolean("authorized", false) ?: false
        playbackPosition = savedInstanceState?.getInt("playback", 0) ?: 0
        videoPaused = savedInstanceState?.getBoolean("videoPaused", false) ?: false
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val stage = WideVideoFrame(this)
        val poster = ImageView(this).apply {
            setImageResource(R.drawable.launch_poster)
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        video = VideoView(this)
        stage.addView(video, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        stage.addView(poster, FrameLayout.LayoutParams(-1, -1))
        root.addView(stage, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))

        // Controls sit on the safe area, while the movie always keeps its 16:9 ratio.
        val chrome = FrameLayout(this)
        root.addView(chrome, FrameLayout.LayoutParams(-1, -1))
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(18), d(12), d(18), d(12))
            background = AppUi.panel(this@LaunchActivity, AppUi.cameraMaterial)
        }
        header.addView(AppUi.heading(this, "白柠视觉辅助", 23f).apply { setTextColor(Color.WHITE) })
        header.addView(AppUi.label(this, "本地识别，专注眼前。", 13f, 0xFFE5E5EA.toInt()).apply { setPadding(0, d(6), 0, 0) })
        chrome.addView(header, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START))
        val playback = AppUi.cameraButton(this, if (videoPaused) "播放动画" else "暂停动画") { }
        playback.background = AppUi.panel(this, AppUi.cameraMaterial, 24)
        playback.setOnClickListener {
            videoPaused = !videoPaused
            if (videoPaused) video.pause() else video.start()
            playback.text = if (videoPaused) "播放动画" else "暂停动画"
        }
        chrome.addView(playback, FrameLayout.LayoutParams(-2, d(48), Gravity.TOP or Gravity.END))

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(20), d(16), d(20), d(16))
            background = AppUi.panel(this@LaunchActivity, 0xF2FFFFFF.toInt(), 24)
        }
        val title = AppUi.heading(this, "准备就绪，即刻开始", 20f)
        card.addView(title)
        state = AppUi.label(this, "正在准备模型…", 14f, AppUi.muted).apply { setPadding(0, d(8), 0, d(8)) }
        card.addView(state)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { isIndeterminate = true }
        card.addView(progress, LinearLayout.LayoutParams(-1, d(4)).apply { bottomMargin = d(12) })
        val actions = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        enter = AppUi.button(this, "输入卡密", true) { if (!authorized) showKeyDialog() else if (!ready) loadModels() else proceed() }
        actions.addView(enter, LinearLayout.LayoutParams(0, d(48), 1f))
        actions.addView(AppUi.button(this, "官方频道") { AppUi.openLink(this, AppUi.CHANNEL) }, LinearLayout.LayoutParams(0, d(48), 1f).apply { marginStart = d(8) })
        card.addView(actions)
        val cardScroll = ScrollView(this).apply { addView(card); isFillViewport = false }
        // Clamp the card on compact windows and keep it scrollable with large fonts.
        val cardWidth = minOf(384, (resources.displayMetrics.widthPixels / resources.displayMetrics.density - 48).toInt())
        chrome.addView(cardScroll, FrameLayout.LayoutParams(d(cardWidth), -2, Gravity.BOTTOM or Gravity.END))
        chrome.addView(AppUi.label(this, "telegram@BNSTR", 12f, Color.WHITE), FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START))
        setContentView(root)
        AppUi.safeArea(chrome, 20)

        video.setOnPreparedListener { player ->
            player.isLooping = true; player.setVolume(0f, 0f)
            player.setOnInfoListener { _, what, _ ->
                if (what == android.media.MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) poster.visibility = View.GONE
                false
            }
            video.seekTo(playbackPosition)
            if (visible && !videoPaused) video.start()
        }
        video.setOnErrorListener { _, _, _ ->
            poster.visibility = View.VISIBLE
            Toast.makeText(this, "动画暂不可用，可继续验证并进入", Toast.LENGTH_SHORT).show()
            true
        }
        video.setVideoURI(Uri.parse("android.resource://$packageName/${R.raw.launch_video}"))
        loadModels()
        if (!authorized) root.post { if (!isFinishing && !isDestroyed) showKeyDialog() }
    }

    private fun d(value: Int) = AppUi.dp(this, value)
    private fun loadModels() {
        if (loading) return
        loading = true; progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                AppRuntime.prepare(assets, SettingsStore(this@LaunchActivity).read())
                ready = true; progress.visibility = View.GONE
                state.text = if (authorized) "模型已就绪，正在进入…" else "模型已就绪，验证后进入相机"
                proceed()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                progress.visibility = View.GONE
                state.text = "模型加载失败，请重试"
                enter.text = if (authorized) "重试加载" else "输入卡密"
            } finally { loading = false }
        }
    }

    private fun showKeyDialog() {
        if (keyDialog?.isShowing == true) return
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(24), d(20), d(24), d(20))
            background = AppUi.panel(this@LaunchActivity, AppUi.surface, 28)
        }
        form.addView(AppUi.heading(this, "欢迎使用白柠", 24f))
        form.addView(AppUi.label(this, "输入卡密，开启本地视觉识别。", 14f, AppUi.muted).apply { setPadding(0, d(8), 0, d(16)) })
        form.addView(AppUi.label(this, "卡密是：jujuenb", 15f, AppUi.accent).apply { setPadding(0, 0, 0, d(8)) })
        val input = EditText(this).apply {
            hint = "请输入卡密"; isSingleLine = true; minHeight = d(48)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            setTextColor(AppUi.text); setHintTextColor(AppUi.muted); textSize = 17f
            setPadding(d(14), d(8), d(14), d(8)); background = AppUi.panel(this@LaunchActivity, AppUi.background, 12)
            contentDescription = "卡密输入框，卡密为 jujuenb"
        }
        form.addView(input, LinearLayout.LayoutParams(-1, d(50)))
        val confirm = AppUi.button(this, "验证并进入", true) { }
        form.addView(confirm, LinearLayout.LayoutParams(-1, d(48)).apply { topMargin = d(16) })
        val links = LinearLayout(this)
        links.addView(AppUi.button(this, "退出") { finish() }, LinearLayout.LayoutParams(0, d(48), 1f))
        links.addView(AppUi.button(this, "官方频道") { AppUi.openLink(this, AppUi.CHANNEL) }, LinearLayout.LayoutParams(0, d(48), 1f).apply { marginStart = d(8) })
        form.addView(links, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(8) })
        val scroll = ScrollView(this).apply { addView(form) }
        val dialog = AlertDialog.Builder(this).setView(scroll).setCancelable(false).create()
        keyDialog = dialog
        confirm.setOnClickListener {
            if (input.text.toString().trim() == "jujuenb") {
                authorized = true; dialog.dismiss()
                enter.text = if (ready) "进入相机" else if (loading) "正在加载模型…" else "重试加载"
                state.text = if (ready) "验证成功，正在进入…" else if (loading) "验证成功，模型加载中…" else "验证成功，请重试加载模型"
                proceed()
            } else { input.error = "卡密不正确，请输入 jujuenb"; input.requestFocus() }
        }
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val width = minOf(d(380), resources.displayMetrics.widthPixels - d(48))
            setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
            setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            setDimAmount(.24f)
        }
    }

    private fun proceed() {
        if (!authorized || !ready || !visible || launching) return
        launching = true; AppRuntime.validated = true
        startActivity(Intent(this, MainActivity::class.java)); finish()
    }
    override fun onResume() { super.onResume(); visible = true; if (::video.isInitialized && !videoPaused) video.start(); proceed() }
    override fun onPause() { visible = false; if (::video.isInitialized) { playbackPosition = video.currentPosition; video.pause() }; super.onPause() }
    override fun onSaveInstanceState(out: Bundle) {
        out.putBoolean("authorized", authorized); out.putBoolean("videoPaused", videoPaused)
        out.putInt("playback", if (::video.isInitialized) video.currentPosition else playbackPosition)
        super.onSaveInstanceState(out)
    }
    override fun onDestroy() { keyDialog?.dismiss(); if (::video.isInitialized) video.stopPlayback(); super.onDestroy() }
}
