package com.visionoverlay.app

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

/** Converts a CameraX YUV frame to the bitmap boundary consumed by the engine. */
fun ImageProxy.toVisionBitmap(): Bitmap {
    val y = planes[0].buffer
    val u = planes[1].buffer
    val v = planes[2].buffer
    val yBytes = ByteArray(y.remaining()); y.get(yBytes)
    val uBytes = ByteArray(u.remaining()); u.get(uBytes)
    val vBytes = ByteArray(v.remaining()); v.get(vBytes)
    val nv21 = ByteArray(yBytes.size + uBytes.size + vBytes.size)
    yBytes.copyInto(nv21, 0)
    var offset = yBytes.size
    var i = 0
    while (i < vBytes.size && offset < nv21.size) {
        nv21[offset++] = vBytes[i]
        if (offset < nv21.size) nv21[offset++] = uBytes.getOrElse(i) { 0 }
        i++
    }
    val image = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val jpeg = ByteArrayOutputStream()
    image.compressToJpeg(Rect(0, 0, width, height), 90, jpeg)
    return android.graphics.BitmapFactory.decodeByteArray(jpeg.toByteArray(), 0, jpeg.size())
}
