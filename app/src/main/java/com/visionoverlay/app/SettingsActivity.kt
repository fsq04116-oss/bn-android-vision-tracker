package com.visionoverlay.app

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Native Android settings with a calm split navigation and grouped list rhythm. */
class SettingsActivity : ComponentActivity() {
    private lateinit var store: SettingsStore
    private var current = OverlaySettings()
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var group: LinearLayout
    private val navigation = mutableListOf<Button>()
    private val names = listOf("操作控制", "识别性能", "绘制显示", "Root 增强", "关于与链接")
    private var selected = 0
    private var hasRoot = RootStatus.available()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SettingsStore(this); current = store.read()
        selected = (savedInstanceState?.getInt("category", 0) ?: 0).coerceIn(names.indices)
        buildUi(); showCategory(selected)
        lifecycleScope.launch {
            hasRoot = withContext(Dispatchers.IO) { RootStatus.refresh() }
            if (selected == 3) showCategory(3)
        }
    }
    private fun d(value: Int) = AppUi.dp(this, value)
    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(AppUi.background) }
        val rail = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(d(12), d(16), d(12), d(16)); setBackgroundColor(AppUi.surface) }
        val railScroll = ScrollView(this).apply { addView(rail); isVerticalScrollBarEnabled = false }
        root.addView(railScroll, LinearLayout.LayoutParams(d(188), -1))
        rail.addView(AppUi.button(this, "‹  返回相机") { finish() }.apply { background = AppUi.panel(this@SettingsActivity, Color.TRANSPARENT); gravity = Gravity.START or Gravity.CENTER_VERTICAL }, row())
        rail.addView(AppUi.heading(this, "设置", 30f).apply { setPadding(d(12), d(18), 0, d(18)) })
        names.forEachIndexed { index, name ->
            val button = AppUi.button(this, name) { if (selected != index) showCategory(index, true) }.apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
            }
            navigation.add(button); rail.addView(button, row())
        }
        rail.addView(AppUi.label(this, "白柠视觉辅助\nv${BuildConfig.VERSION_NAME}", 12f, AppUi.muted).apply { setPadding(d(12), d(24), 0, 0) })
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(d(28), d(20), d(28), d(24)) }
        scroll = ScrollView(this).apply { isFillViewport = true; addView(content) }
        root.addView(scroll, LinearLayout.LayoutParams(0, -1, 1f))
        setContentView(root); AppUi.safeArea(root, 0)
    }
    private fun row() = LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(4) }
    private fun save() = store.write(current)
    private fun note(text: String) {
        content.addView(AppUi.label(this, text, 13f, AppUi.muted).apply { setPadding(d(4), d(8), d(4), d(16)) })
    }
    private fun section(title: String) {
        content.addView(AppUi.label(this, title, 13f, AppUi.muted).apply { setPadding(d(16), d(12), 0, d(8)) })
        group = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = AppUi.panel(this@SettingsActivity)
            setPadding(d(8), d(4), d(8), d(4))
        }
        content.addView(group, LinearLayout.LayoutParams(-1, -2))
    }
    private fun switchView(label: String, value: Boolean) = SwitchCompat(this).apply {
        text = label; textSize = 16f; setTextColor(AppUi.text); isChecked = value
        minHeight = d(56); setPadding(d(8), d(8), d(8), d(8)); switchPadding = d(16)
        thumbTintList = ColorStateList.valueOf(Color.WHITE)
        trackTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(0xFF34C759.toInt(), 0xFFD1D1D6.toInt()))
        splitTrack = false
    }
    private fun check(label: String, value: Boolean, change: (Boolean) -> Unit) {
        group.addView(switchView(label, value).apply {
            setOnCheckedChangeListener { _, enabled -> change(enabled); save() }
        }, LinearLayout.LayoutParams(-1, -2))
    }
    private fun choice(label: String, choices: Array<String>, position: Int, change: (Int) -> Unit) {
        val item = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(d(8), d(12), d(8), d(4)) }
        item.addView(AppUi.label(this, label, 15f))
        item.addView(Spinner(this).apply {
            adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_dropdown_item, choices)
            setSelection(position.coerceIn(0, choices.lastIndex)); minimumHeight = d(48)
            contentDescription = label
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) { change(pos); save() }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }, LinearLayout.LayoutParams(-1, d(48)))
        group.addView(item)
    }
    private fun link(label: String, action: () -> Unit) {
        group.addView(AppUi.button(this, "$label  ›", action = action).apply {
            background = AppUi.panel(this@SettingsActivity, Color.TRANSPARENT, 12)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(-1, d(56)))
    }
    private fun showCategory(index: Int, animate: Boolean = false) {
        selected = index; content.removeAllViews(); scroll.scrollTo(0, 0)
        navigation.forEachIndexed { i, button ->
            button.isSelected = i == index
            button.setTextColor(if (i == index) AppUi.accent else AppUi.text)
            button.background = AppUi.panel(this, if (i == index) 0xFFE8F1FF.toInt() else Color.TRANSPARENT, 12)
            button.contentDescription = names[i] + if (i == index) "，当前分类" else ""
        }
        content.addView(AppUi.heading(this, names[index], 28f))
        when (index) {
            0 -> {
                note("按你的习惯，安排相机操作。")
                section("快捷操作")
                check("显示主页控制面板", current.controlsVisible) { current = current.copy(controlsVisible = it) }
                check("音量键控制面板", current.volumeControls) { current = current.copy(volumeControls = it) }
                check("应用内悬浮按钮", current.floatingControls) { current = current.copy(floatingControls = it) }
                note("音量加显示，音量减隐藏；识别会继续运行。悬浮按钮可拖动，无需 Root。关闭音量键控制时会保留触屏入口。")
            }
            1 -> {
                note("平衡识别细节与运行速度。")
                section("模型与计算")
                val backends = arrayOf(BackendChoice.AUTO, BackendChoice.VULKAN, BackendChoice.CPU)
                choice("推理后端", arrayOf("自动", "Vulkan", "CPU（性能较低）"), backends.indexOf(current.backend).coerceAtLeast(0)) { current = current.copy(backend = backends[it]) }
                choice("模型模式", arrayOf("人物骨骼 + 人物 / 车辆", "快速检测 · 人物 / 车辆"), if (current.modelMode == ModelMode.POSE) 0 else 1) { current = current.copy(modelMode = if (it == 0) ModelMode.POSE else ModelMode.DETECTION) }
                val sizes = arrayOf(320, 416, 640)
                choice("推理分辨率", arrayOf("320 · 更快", "416 · 均衡", "640 · 小目标细节"), sizes.indexOf(current.inputSize).coerceAtLeast(0)) { current = current.copy(inputSize = sizes[it]) }
                note("640 可保留更多细节，也会增加耗时。支持人物、自行车、汽车、摩托车、公共汽车和卡车。")
                section("相机")
                check("前置镜头画面镜像", current.mirror) { current = current.copy(mirror = it) }
            }
            2 -> {
                note("保留你需要的信息，让画面更清楚。")
                section("目标与骨骼")
                check("人物检测框", current.personBox) { current = current.copy(personBox = it) }
                check("车辆检测框", current.vehicleBox) { current = current.copy(vehicleBox = it) }
                check("人物骨骼", current.skeleton) { current = current.copy(skeleton = it) }
                check("手持标记", current.heldMarker) { current = current.copy(heldMarker = it) }
                section("画面信息")
                check("类别名称与置信度", current.labels) { current = current.copy(labels = it) }
                check("FPS 帧率", current.stats) { current = current.copy(stats = it) }
                check("标签黑色描边", current.labelStroke) { current = current.copy(labelStroke = it) }
            }
            3 -> renderRoot()
            4 -> {
                note("白柠视觉辅助  ${BuildConfig.VERSION_NAME}\n人物与车辆识别在本地完成，不上传相机画面。")
                section("保持联系")
                link("官方频道 · @BNSTR") { AppUi.openLink(this, AppUi.CHANNEL) }
                link("项目开源地址 · GitHub") { AppUi.openLink(this, AppUi.SOURCE) }
                link("上游项目 · nihui") { AppUi.openLink(this, "https://github.com/nihui/ncnn-android-yolov8") }
                note("保留 ncnn / OpenCV 与原生相机示例的开源声明。\n作者：telegram@BNSTR")
                section("设置管理")
                link("重置所有设置") {
                    AlertDialog.Builder(this).setTitle("恢复默认设置？").setMessage("绘制和操作偏好将恢复默认值。")
                        .setNegativeButton("取消", null).setPositiveButton("重置") { _, _ -> store.reset(); current = OverlaySettings(); showCategory(0) }.show()
                }
            }
        }
        if (animate && android.animation.ValueAnimator.areAnimatorsEnabled()) {
            content.alpha = 0f
            content.animate().alpha(1f).setDuration(160).start()
        }
    }
    private fun renderRoot() {
        note(if (hasRoot) "已检测到 Root。临时调整可能增加发热与功耗。" else "当前为普通模式。悬浮按钮与音量键可照常使用。")
        section("临时调整")
        val controls = listOf("临时放宽 SELinux", "CPU 最低频率选项", "GPU 最低频率选项").map { label ->
            switchView(label, false).apply { isEnabled = hasRoot; alpha = if (hasRoot) 1f else .4f }.also { group.addView(it) }
        }
        val apply = AppUi.button(this, "应用 Root 设置", true) { }
        apply.isEnabled = hasRoot; apply.alpha = if (hasRoot) 1f else .4f
        apply.setOnClickListener {
            val selinux = controls[0].isChecked; val cpu = controls[1].isChecked; val gpu = controls[2].isChecked
            apply.isEnabled = false
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) { RootStatus.applyThermalTuning(selinux, cpu, gpu) }
                apply.isEnabled = true
                Toast.makeText(this@SettingsActivity, if (result) "已应用" else "应用失败", Toast.LENGTH_SHORT).show()
            }
        }
        content.addView(apply, LinearLayout.LayoutParams(-1, d(48)).apply { topMargin = d(20) })
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putInt("category", selected); super.onSaveInstanceState(outState) }
}
