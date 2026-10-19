# Open-source notices and integration record

## Camera source

The supplied archive's root `LICENSE` contains MPL 1.1 text, while
`CameraController.java` and `Camera2Controller.java` carry GPL-3.0-or-later
headers. Its camera controller sources are part of an application and refer to
internal application, plugin, image-conversion, and native support classes.
The current VisionOverlay CameraX code is not a reuse of those camera sources.
Check each imported file's notice and preserve applicable license obligations.

## Detection and pose source

The supplied `ncnn-android-yolov8-master.zip` contains Tencent copyright
notices and a BSD 3-Clause license in its Java/JNI files. Preserve the original
notices alongside imported code. The project depends on ncnn and OpenCV Mobile;
include their respective notices when those libraries are bundled.

## URLs

- https://github.com/Almalence/OpenCamera
- https://github.com/nihui/ncnn-android-yolov8
