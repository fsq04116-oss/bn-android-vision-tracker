package com.visionoverlay.app

import android.content.Context
import android.widget.FrameLayout
import kotlin.math.min
import kotlin.math.roundToInt

/** Fits the whole 16:9 video inside any landscape window without distortion. */
class WideVideoFrame(context: Context) : FrameLayout(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val availableWidth = MeasureSpec.getSize(widthMeasureSpec)
        val availableHeight = MeasureSpec.getSize(heightMeasureSpec)
        val fittedWidth = min(availableWidth, (availableHeight * 16f / 9f).roundToInt())
        val fittedHeight = min(availableHeight, (fittedWidth * 9f / 16f).roundToInt())
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(fittedWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(fittedHeight, MeasureSpec.EXACTLY)
        )
    }
}
