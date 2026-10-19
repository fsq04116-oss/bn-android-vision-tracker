#include <jni.h>

// Integration boundary for nihui/ncnn-android-yolov8. Keep preprocessing,
// Vulkan initialization, YOLO decode/NMS, and the COCO pose decoder in the
// upstream native module. This typed fallback keeps the camera shell stable
// until the upstream ncnn/OpenCV libraries are linked into this CMake target.
extern "C" JNIEXPORT jobject JNICALL
Java_com_visionoverlay_app_NcnnYoloPoseEngine_nativeProcessNative(
        JNIEnv* env, jobject, jlong, jobject, jint, jfloat, jfloat, jboolean) {
    jclass arrayListClass = env->FindClass("java/util/ArrayList");
    jmethodID arrayListCtor = env->GetMethodID(arrayListClass, "<init>", "()V");
    jobject detections = env->NewObject(arrayListClass, arrayListCtor);
    jobject poses = env->NewObject(arrayListClass, arrayListCtor);
    jclass resultClass = env->FindClass("com/visionoverlay/app/FrameResult");
    jmethodID resultCtor = env->GetMethodID(resultClass, "<init>", "(Ljava/util/List;Ljava/util/List;Ljava/lang/String;J)V");
    jstring backend = env->NewStringUTF("CPU");
    return env->NewObject(resultClass, resultCtor, detections, poses, backend, (jlong)0);
}
