package com.visionoverlay.app

import android.content.res.AssetManager
import com.tencent.yolov8ncnn.YOLOv8Ncnn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Process-local access gate. A cold launch always asks for the local demo key. */
object AppRuntime {
    @Volatile var validated = false
    private val capture by lazy { YOLOv8Ncnn() }
    private var loadedKey: String? = null

    // The splash and camera screen share the same JNI facade and native models.
    // Disk/GPU initialization must never block drawing the video or key dialog.
    suspend fun prepare(assets: AssetManager, settings: OverlaySettings): YOLOv8Ncnn =
        withContext(Dispatchers.IO) {
            synchronized(this@AppRuntime) {
                val task = if (settings.modelMode == ModelMode.POSE) 3 else 0
                val backend = if (settings.backend == BackendChoice.CPU) 0 else 1
                val key = "$task:$backend"
                if (loadedKey != key) {
                    check(capture.loadModel(assets, task, 0, backend)) { "模型加载失败，请重试" }
                    loadedKey = key
                }
                capture.setInputSize(settings.inputSize)
                capture
            }
        }
}
