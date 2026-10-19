package com.visionoverlay.app

data class Detection(val label: String, val score: Float, val left: Float, val top: Float, val right: Float, val bottom: Float)
data class Keypoint(val x: Float, val y: Float, val score: Float)
data class PoseDetection(val detection: Detection, val keypoints: List<Keypoint>)
data class FrameResult(val detections: List<Detection>, val poses: List<PoseDetection>, val backend: String, val inferenceMs: Long)

enum class BackendChoice { AUTO, VULKAN, NNAPI, CPU }
enum class ModelMode { POSE, DETECTION }

data class OverlaySettings(
    val controlsVisible: Boolean = true,
    val volumeControls: Boolean = true,
    val floatingControls: Boolean = true,
    val personBox: Boolean = true,
    val skeleton: Boolean = true,
    val heldMarker: Boolean = true,
    val vehicleBox: Boolean = true,
    val labels: Boolean = true,
    val stats: Boolean = true,
    val mirror: Boolean = true,
    val backend: BackendChoice = BackendChoice.AUTO,
    val modelMode: ModelMode = ModelMode.POSE,
    val confidence: Float = 0.45f,
    val nms: Float = 0.45f,
    val inputSize: Int = 416,
    val boxWidth: Float = 3f,
    val skeletonWidth: Float = 2f,
    val pointRadius: Float = 4f,
    val labelSize: Float = 14f,
    val labelStroke: Boolean = true
)
