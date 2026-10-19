cmake_minimum_required(VERSION 3.22.1)
project(visionoverlay)

set(OpenCV_DIR ${CMAKE_SOURCE_DIR}/../jni/opencv-mobile-4.13.0-android/sdk/native/jni)
set(ncnn_DIR ${CMAKE_SOURCE_DIR}/../jni/ncnn-20260526-android-vulkan/${ANDROID_ABI}/lib/cmake/ncnn)
find_package(OpenCV REQUIRED core imgproc)
find_package(ncnn REQUIRED)

add_library(visionoverlay SHARED
        visionoverlay_jni.cpp
        upstream/yolov8.cpp
        upstream/yolov8_det.cpp
        upstream/yolov8_pose.cpp
        upstream/yolov8_seg.cpp
        upstream/yolov8_cls.cpp
        upstream/yolov8_obb.cpp
        upstream/yolov8ncnn.cpp
        upstream/ndkcamera.cpp)

find_library(log-lib log)
target_link_libraries(visionoverlay ncnn ${OpenCV_LIBS} camera2ndk mediandk ${log-lib})
