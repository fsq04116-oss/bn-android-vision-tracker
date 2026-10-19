package com.visionoverlay.app

import android.content.Context

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("vision_overlay", Context.MODE_PRIVATE)
    fun read(): OverlaySettings = OverlaySettings(
        controlsVisible = prefs.getBoolean("controlsVisible", true),
        volumeControls = prefs.getBoolean("volumeControls", true),
        floatingControls = prefs.getBoolean("floatingControls", true),
        personBox = prefs.getBoolean("personBox", true), skeleton = prefs.getBoolean("skeleton", true),
        heldMarker = prefs.getBoolean("heldMarker", true), vehicleBox = prefs.getBoolean("vehicleBox", true),
        labels = prefs.getBoolean("labels", true), stats = prefs.getBoolean("stats", true),
        mirror = prefs.getBoolean("mirror", true),
        backend = runCatching { BackendChoice.valueOf(prefs.getString("backend", "AUTO")!!) }.getOrDefault(BackendChoice.AUTO),
        modelMode = runCatching { ModelMode.valueOf(prefs.getString("modelMode", "POSE")!!) }.getOrDefault(ModelMode.POSE),
        confidence = prefs.getFloat("confidence", .45f), nms = prefs.getFloat("nms", .45f),
        inputSize = prefs.getInt("inputSize", 416), boxWidth = prefs.getFloat("boxWidth", 3f),
        skeletonWidth = prefs.getFloat("skeletonWidth", 2f), pointRadius = prefs.getFloat("pointRadius", 4f),
        labelSize = prefs.getFloat("labelSize", 14f), labelStroke = prefs.getBoolean("labelStroke", true)
    )
    fun write(s: OverlaySettings) { prefs.edit().apply {
        putBoolean("controlsVisible", s.controlsVisible); putBoolean("volumeControls", s.volumeControls)
        putBoolean("floatingControls", s.floatingControls)
        putBoolean("personBox", s.personBox); putBoolean("skeleton", s.skeleton); putBoolean("heldMarker", s.heldMarker)
        putBoolean("vehicleBox", s.vehicleBox); putBoolean("labels", s.labels); putBoolean("stats", s.stats)
        putBoolean("mirror", s.mirror); putString("backend", s.backend.name); putString("modelMode", s.modelMode.name)
        putFloat("confidence", s.confidence); putFloat("nms", s.nms); putInt("inputSize", s.inputSize)
        putFloat("boxWidth", s.boxWidth); putFloat("skeletonWidth", s.skeletonWidth); putFloat("pointRadius", s.pointRadius)
        putFloat("labelSize", s.labelSize); putBoolean("labelStroke", s.labelStroke); apply()
    } }
    fun reset() { prefs.edit().clear().apply() }
}
