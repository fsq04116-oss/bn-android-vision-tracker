package com.tencent.yolov8ncnn;

import android.content.res.AssetManager;
import android.view.Surface;

/** JNI facade retained from nihui/ncnn-android-yolov8. */
public final class YOLOv8Ncnn {
    public native boolean loadModel(AssetManager mgr, int taskid, int modelid, int cpugpu);
    public native boolean openCamera(int facing);
    public native boolean closeCamera();
    public native boolean setZoom(float zoomRatio);
    public native boolean setInputSize(int size);
    public native float getFps();
    public native boolean setOutputWindow(Surface surface);
    static { System.loadLibrary("visionoverlay"); }
}
