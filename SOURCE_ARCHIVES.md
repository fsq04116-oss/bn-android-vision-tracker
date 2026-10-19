# Attached source archive distinction

This file records what came from the attached documents and what is an
implementation decision for the user's VisionOverlay request.

## Attached archives inspected

- `C:/Users/Administrator/Downloads/OpenCamera-master.zip`
  SHA-256: `948C43AAE267DA42F5BF59CBFE207B1D5BA5F0AD9E4DA7458B0EFF99B276AFA6`
  Role confirmed by the user: camera source. The archive is an application;
  its camera controllers depend on internal OpenCamera classes.
- `C:/Users/Administrator/Downloads/ncnn-android-yolov8-master.zip`
  SHA-256: `F3B2ACAFDB3AEA54583AAF31D9A09432C5FE7058CC26B6659E23687056794D59`
  Role confirmed by the user: capture/detection source. It contains the
  YOLOv8 ncnn detector and pose implementation, native NDK camera sample,
  and `yolov8n`/`yolov8n_pose` model assets.

## User-request decisions

- Landscape-only VisionOverlay UI, independent transparent overlay, settings,
  persistent draw switches, performance display, root status, and watermark
  are new application-layer requirements.
- The active preview now uses the YOLOv8 archive's NDK Camera surface and JNI
  facade. OpenCamera remains a documented reference rather than copied source,
  because its controller depends on its own application/plugin classes.
- The supplied ncnn/OpenCV packages, YOLOv8 native decoder, NDK Camera, and two
  small model pairs are linked into the debug APK. The thin Kotlin
  `InferenceEngine` bitmap bridge remains available for future overlay-result
  exchange; the live native surface performs upstream drawing.
- The supplied pose model exposes COCO-17 keypoints. A 33-point output needs a
  different model/decoder and is not silently attributed to this archive.
