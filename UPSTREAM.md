# Upstream source mapping

The supplied archives define these roles:

| Source | VisionOverlay role | Integration status |
| --- | --- | --- |
| `OpenCamera-master.zip` | Camera capture and camera UI reference (`src/com/almalence/opencam/cameracontroller/CameraController.java`, `Camera2Controller.java`) | Inspected. It is a complete legacy application, not a standalone camera SDK; its controller depends on OpenCamera application classes. The current UI still uses CameraX and needs a dedicated camera adapter before claiming OpenCamera reuse. |
| `ncnn-android-yolov8-master.zip` | Detection/pose model and ncnn inference reference | Inspected. Its Java JNI facade loads a task/model and selects CPU/GPU. Its native model code implements YOLOv8 decode/NMS and COCO labels. It can supply `yolov8n.ncnn.*` and `yolov8n_pose.ncnn.*`. |

## Model and output constraints

- The pose asset in this archive is `yolov8n_pose.ncnn.param/.bin`; it produces
  COCO-17 keypoints, not 33-point whole-body pose.
- The standard detection model is COCO detection. Filtering to person and
  selected vehicle labels belongs in the adapter/UI policy.
- The sample native project depends on ncnn, OpenCV Mobile, and Android NDK
  camera sources. Its sample Java facade controls its own native camera and
  renders through a `Surface`; it does not expose a frame-to-result `Bitmap`
  API. A frame adapter is required to combine it with another camera source.
- The sample documents CPU/GPU (Vulkan) selection. NNAPI fallback is not
  provided by this upstream and must not be shown as implemented until added
  and tested in the native adapter.

## License notes

- The OpenCamera archive's `CameraController.java` and `Camera2Controller.java`
  carry GPL-3.0-or-later headers, while the archive's root `LICENSE` contains
  MPL 1.1 text. Check file-level notices and the exact files imported before
  distribution; do not assume a single license covers the whole archive.
- The supplied ncnn-android-yolov8 Java/native source uses the BSD 3-Clause
  License; preserve its copyright/license notices when importing it.
- Keep the full upstream notices with any imported sources and review the
  transitive ncnn/OpenCV licenses before publishing.

Source URLs:

- https://github.com/Almalence/OpenCamera
- https://github.com/nihui/ncnn-android-yolov8
