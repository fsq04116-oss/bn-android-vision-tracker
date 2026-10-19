package com.visionoverlay.app

import android.graphics.Bitmap

interface InferenceEngine : AutoCloseable {
    val backendName: String
    fun process(bitmap: Bitmap, settings: OverlaySettings): FrameResult
    override fun close()
}

class NcnnYoloPoseEngine(private val assetsPath: String) : InferenceEngine {
    private var nativeHandle: Long = 0
    override var backendName: String = "CPU"
        private set

    init { runCatching { System.loadLibrary("visionoverlay") } }
    override fun process(bitmap: Bitmap, settings: OverlaySettings): FrameResult {
        val start = System.nanoTime()
        backendName = chooseBackend(settings.backend)
        val raw = runCatching { nativeProcessNative(nativeHandle, bitmap, settings.inputSize, settings.confidence, settings.nms, settings.modelMode == ModelMode.POSE) }
            .getOrElse { FrameResult(emptyList(), emptyList(), backendName, 0) }
        return FrameResult(raw.detections, raw.poses, backendName, (System.nanoTime() - start) / 1_000_000)
    }
    // The supplied YOLOv8 sample supports its CPU/GPU (Vulkan) paths. NNAPI
    // remains a future adapter until a native implementation is integrated.
    private fun chooseBackend(choice: BackendChoice): String = when (choice) {
        BackendChoice.VULKAN -> "Vulkan"
        BackendChoice.NNAPI -> "NNAPI"
        BackendChoice.CPU -> "CPU"
        BackendChoice.AUTO -> "CPU" // Conservative until native capability detection exists.
    }
    // Implement this bridge against ncnn-android-yolov8 after importing its
    // model sources and dependencies; no inference is active in this scaffold.
    private external fun nativeProcessNative(handle: Long, bitmap: Bitmap, size: Int, conf: Float, nms: Float, pose: Boolean): FrameResult
    override fun close() { nativeHandle = 0 }
}
