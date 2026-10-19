package com.visionoverlay.app

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class OverlayView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private var result: FrameResult? = null
    private var lastFrameAt = 0L
    private var fps = 0f
    private var settings = OverlaySettings()
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val posePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val skeleton = arrayOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 0 to 5, 5 to 6, 6 to 7, 7 to 8, 0 to 9, 9 to 10, 10 to 11, 11 to 12, 12 to 13, 13 to 14, 14 to 15, 15 to 16)
    fun update(frame: FrameResult?, next: OverlaySettings) { val now = System.nanoTime(); if (result != null && lastFrameAt != 0L) fps = 1_000_000_000f / (now - lastFrameAt).coerceAtLeast(1L); lastFrameAt = now; result = frame; settings = next; invalidate() }
    override fun onDraw(canvas: Canvas) { super.onDraw(canvas); val frame = result
        boxPaint.style = Paint.Style.STROKE; boxPaint.strokeWidth = settings.boxWidth; boxPaint.color = Color.rgb(50, 220, 150)
        posePaint.style = Paint.Style.STROKE; posePaint.strokeWidth = settings.skeletonWidth; posePaint.color = Color.rgb(255, 214, 70)
        textPaint.textSize = settings.labelSize; textPaint.style = Paint.Style.FILL; textPaint.color = Color.WHITE
        frame?.detections?.forEach { d -> if (d.label == "person" && settings.personBox || d.label != "person" && settings.vehicleBox) canvas.drawRect(d.left * width, d.top * height, d.right * width, d.bottom * height, boxPaint); if (settings.labels) drawLabel(canvas, d) }
        if (settings.skeleton) frame?.poses?.forEach { p -> val pts = p.keypoints; skeleton.forEach { (a, b) -> if (a < pts.size && b < pts.size) canvas.drawLine(pts[a].x * width, pts[a].y * height, pts[b].x * width, pts[b].y * height, posePaint) }; pts.forEach { if (it.score >= settings.confidence) canvas.drawCircle(it.x * width, it.y * height, settings.pointRadius, posePaint) }; if (settings.heldMarker && pts.size > 10) canvas.drawCircle(pts[9].x * width, pts[9].y * height, settings.pointRadius * 1.8f, boxPaint) }
        if (settings.stats && frame != null) { textPaint.textSize = 16f; val inference = frame.inferenceMs; val load = (inference / 33f * 100f).toInt().coerceAtMost(999); val metrics = "${"%.1f".format(fps)} FPS  ${inference} ms  LOAD ${load}%"; canvas.drawText(metrics, (width - textPaint.measureText(metrics)) / 2f, height - 22f, textPaint) }
        textPaint.textSize = 14f; canvas.drawText("telegram@BNSTR", width - 150f, height - 22f, textPaint)
    }
    private fun drawLabel(canvas: Canvas, d: Detection) { val x = d.left * width; val y = d.top * height - 8; if (settings.labelStroke) { textPaint.style = Paint.Style.STROKE; textPaint.strokeWidth = 4f; textPaint.color = Color.BLACK; canvas.drawText("${d.label} ${(d.score * 100).toInt()}%", x, y, textPaint); textPaint.style = Paint.Style.FILL; textPaint.color = Color.WHITE }; canvas.drawText("${d.label} ${(d.score * 100).toInt()}%", x, y, textPaint) }
}
