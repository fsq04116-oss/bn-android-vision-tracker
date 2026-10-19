// Tencent is pleased to support the open source community by making ncnn available.
//
// Copyright (C) 2021 THL A29 Limited, a Tencent company. All rights reserved.
//
// Licensed under the BSD 3-Clause License (the "License"); you may not use this file except
// in compliance with the License. You may obtain a copy of the License at
//
// https://opensource.org/licenses/BSD-3-Clause
//
// Unless required by applicable law or agreed to in writing, software distributed
// under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
// CONDITIONS OF ANY KIND, either express or implied. See the License for the
// specific language governing permissions and limitations under the License.

#include <android/asset_manager_jni.h>
#include <android/native_window_jni.h>
#include <android/native_window.h>

#include <android/log.h>

#include <jni.h>

#include <string>
#include <vector>
#include <mutex>
#include <atomic>

#include <platform.h>
#include <benchmark.h>

#include "yolov8.h"

#include "ndkcamera.h"

#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>

#if __ARM_NEON
#include <arm_neon.h>
#endif // __ARM_NEON

static int draw_unsupported(cv::Mat& rgb)
{
    const char text[] = "unsupported";

    int baseLine = 0;
    cv::Size label_size = cv::getTextSize(text, cv::FONT_HERSHEY_SIMPLEX, 1.0, 1, &baseLine);

    int y = (rgb.rows - label_size.height) / 2;
    int x = (rgb.cols - label_size.width) / 2;

    cv::rectangle(rgb, cv::Rect(cv::Point(x, y), cv::Size(label_size.width, label_size.height + baseLine)),
                    cv::Scalar(255, 255, 255), -1);

    cv::putText(rgb, text, cv::Point(x, y + label_size.height),
                cv::FONT_HERSHEY_SIMPLEX, 1.0, cv::Scalar(0, 0, 0));

    return 0;
}

static std::atomic<float> g_fps(0.f);

static int update_fps()
{
    // resolve moving average
    float avg_fps = 0.f;
    {
        static double t0 = 0.f;
        static float fps_history[10] = {0.f};

        double t1 = ncnn::get_current_time();
        if (t0 == 0.f)
        {
            t0 = t1;
            return 0;
        }

        if (t1 - t0 > 1000.)
            std::fill(fps_history, fps_history + 10, 0.f);
        float fps = 1000.f / std::max(1., t1 - t0);
        t0 = t1;

        for (int i = 9; i >= 1; i--)
        {
            fps_history[i] = fps_history[i - 1];
        }
        fps_history[0] = fps;

        int samples = 0;
        for (int i = 0; i < 10; i++)
        {
            if (fps_history[i] > 0.f) samples++;
            avg_fps += fps_history[i];
        }
        if (samples > 0) avg_fps /= (float)samples;
        g_fps.store(avg_fps);
    }

    return 0;
}

static YOLOv8* g_yolov8 = 0;
static YOLOv8_det_coco* g_vehicle_detector = 0;
static ncnn::Mutex lock;
// JNI lifecycle calls can arrive from different Android callbacks. Keep
// camera and model transitions serialized while the frame thread uses lock.
static std::mutex lifecycle_lock;

class MyNdkCamera : public NdkCameraWindow
{
public:
    virtual void on_image_render(cv::Mat& rgb) const;
    void set_input_size(int size);
private:
    mutable std::vector<Object> cached_pose_objects;
    mutable std::vector<Object> cached_vehicle_objects;
    mutable int pose_hold_frames = 0;
    mutable int vehicle_hold_frames = 0;
};

void MyNdkCamera::set_input_size(int size)
{
    const int normalized = size <= 320 ? 320 : (size >= 640 ? 640 : 416);
    ncnn::MutexLockGuard guard(lock);
    if (g_yolov8) g_yolov8->set_det_target_size(normalized);
    if (g_vehicle_detector) g_vehicle_detector->set_det_target_size(normalized);
}

static float box_iou(const Object& a, const Object& b)
{
    const cv::Rect_<float> inter = a.rect & b.rect;
    const float union_area = a.rect.area() + b.rect.area() - inter.area();
    return union_area > 0.f ? inter.area() / union_area : 0.f;
}

static void stabilize_objects(std::vector<Object>& current, std::vector<Object>& cached, int& hold_frames)
{
    if (current.empty())
    {
        if (hold_frames > 0 && !cached.empty())
        {
            current = cached;
            hold_frames--;
        }
        return;
    }

    // Keep a recently missed object for a couple of frames. This removes the
    // visible on/off flicker when several targets compete for GPU time.
    if (!cached.empty() && hold_frames > 0)
    {
        for (const Object& old_object : cached)
        {
            bool matched = false;
            for (Object& object : current)
            {
                if (object.label == old_object.label && box_iou(object, old_object) > 0.20f)
                {
                    const float fresh = 0.68f;
                    const float previous = 1.f - fresh;
                    object.rect.x = fresh * object.rect.x + previous * old_object.rect.x;
                    object.rect.y = fresh * object.rect.y + previous * old_object.rect.y;
                    object.rect.width = fresh * object.rect.width + previous * old_object.rect.width;
                    object.rect.height = fresh * object.rect.height + previous * old_object.rect.height;
                    const size_t points = std::min(object.keypoints.size(), old_object.keypoints.size());
                    for (size_t i = 0; i < points; ++i)
                    {
                        object.keypoints[i].p.x = fresh * object.keypoints[i].p.x + previous * old_object.keypoints[i].p.x;
                        object.keypoints[i].p.y = fresh * object.keypoints[i].p.y + previous * old_object.keypoints[i].p.y;
                        object.keypoints[i].prob = fresh * object.keypoints[i].prob + previous * old_object.keypoints[i].prob;
                    }
                    matched = true;
                    break;
                }
            }
            if (!matched)
            {
                Object retained = old_object;
                retained.prob *= 0.92f;
                current.push_back(retained);
            }
        }
        hold_frames--;
    }
    else
    {
        hold_frames = 2;
    }
    cached = current;
}

void MyNdkCamera::on_image_render(cv::Mat& rgb) const
{
    update_fps();
    // yolov8
    {
        ncnn::MutexLockGuard g(lock);

        if (g_yolov8)
        {
            std::vector<Object> objects;
            g_yolov8->detect(rgb, objects);
            stabilize_objects(objects, cached_pose_objects, pose_hold_frames);

            g_yolov8->draw(rgb, objects);

            if (g_vehicle_detector)
            {
                std::vector<Object> vehicles;
                // The vehicle model is the expensive second pass in pose
                // mode. Running it every other frame and holding the last
                // boxes keeps multi-target drawing continuous.
                static int vehicle_frame = 0;
                if ((vehicle_frame++ & 1) == 0)
                    g_vehicle_detector->detect(rgb, vehicles);
                std::vector<Object> filtered;
                for (const Object& object : vehicles)
                {
                    if (object.label == 1 || object.label == 2 || object.label == 3 || object.label == 5 || object.label == 7)
                        filtered.push_back(object);
                }
                stabilize_objects(filtered, cached_vehicle_objects, vehicle_hold_frames);
                g_vehicle_detector->draw(rgb, filtered);
            }
        }
        else
        {
            draw_unsupported(rgb);
        }
    }

}

static MyNdkCamera* g_camera = 0;

extern "C" {

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "JNI_OnLoad");

    g_camera = new MyNdkCamera;

    ncnn::create_gpu_instance();

    return JNI_VERSION_1_4;
}

JNIEXPORT void JNI_OnUnload(JavaVM* vm, void* reserved)
{
    std::lock_guard<std::mutex> lifecycle_guard(lifecycle_lock);
    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "JNI_OnUnload");

    {
        ncnn::MutexLockGuard g(lock);

        delete g_yolov8;
        g_yolov8 = 0;
        delete g_vehicle_detector;
        g_vehicle_detector = 0;
    }

    ncnn::destroy_gpu_instance();

    delete g_camera;
    g_camera = 0;
}

// public native boolean loadModel(AssetManager mgr, int taskid, int modelid, int cpugpu);
JNIEXPORT jboolean JNICALL Java_com_tencent_yolov8ncnn_YOLOv8Ncnn_loadModel(JNIEnv* env, jobject thiz, jobject assetManager, jint taskid, jint modelid, jint cpugpu)
{
    std::lock_guard<std::mutex> lifecycle_guard(lifecycle_lock);
    if (taskid < 0 || taskid > 5 || modelid < 0 || modelid > 8 || cpugpu < 0 || cpugpu > 2)
    {
        return JNI_FALSE;
    }

    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);

    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "loadModel %p", mgr);

    const char* tasknames[6] =
    {
        "",
        "_oiv7",
        "_seg",
        "_pose",
        "_cls",
        "_obb"
    };

    const char* modeltypes[9] =
    {
        "n",
        "s",
        "m",
        "n",
        "s",
        "m",
        "n",
        "s",
        "m"
    };

    std::string parampath = std::string("yolov8") + modeltypes[(int)modelid] + tasknames[(int)taskid] + ".ncnn.param";
    std::string modelpath = std::string("yolov8") + modeltypes[(int)modelid] + tasknames[(int)taskid] + ".ncnn.bin";
    bool use_gpu = (int)cpugpu == 1;
    bool use_turnip = (int)cpugpu == 2;

    // reload
    {
        ncnn::MutexLockGuard g(lock);

        {
            static int old_taskid = 0;
            static int old_modelid = 0;
            static int old_cpugpu = 0;
            if (taskid != old_taskid || (modelid % 3) != old_modelid || cpugpu != old_cpugpu)
            {
                // taskid or model or cpugpu changed
                delete g_yolov8;
                g_yolov8 = 0;
                delete g_vehicle_detector;
                g_vehicle_detector = 0;
            }
            old_taskid = taskid;
            old_modelid = modelid % 3;
            old_cpugpu = cpugpu;

            ncnn::destroy_gpu_instance();

            if (use_turnip)
            {
                ncnn::create_gpu_instance("libvulkan_freedreno.so");
            }
            else if (use_gpu)
            {
                ncnn::create_gpu_instance();
            }

            int target_size = 320;
            if ((int)modelid >= 3)
                target_size = 480;
            if ((int)modelid >= 6)
                target_size = 640;

            if (!g_yolov8)
            {
                if (taskid == 0) g_yolov8 = new YOLOv8_det_coco;
                if (taskid == 1) g_yolov8 = new YOLOv8_det_oiv7;
                if (taskid == 2) g_yolov8 = new YOLOv8_seg;
                if (taskid == 3) g_yolov8 = new YOLOv8_pose;
                if (taskid == 4) g_yolov8 = new YOLOv8_cls;
                if (taskid == 5) g_yolov8 = new YOLOv8_obb;

                g_yolov8->load(mgr, parampath.c_str(), modelpath.c_str(), use_gpu || use_turnip);
                if (taskid == 3)
                {
                    g_vehicle_detector = new YOLOv8_det_coco;
                    g_vehicle_detector->load(mgr, "yolov8n.ncnn.param", "yolov8n.ncnn.bin", use_gpu || use_turnip);
                    g_vehicle_detector->set_det_target_size(target_size);
                }
            }
            g_yolov8->set_det_target_size(target_size);
            if (g_vehicle_detector)
                g_vehicle_detector->set_det_target_size(target_size);
        }
    }

    return JNI_TRUE;
}

// public native boolean openCamera(int facing);
JNIEXPORT jboolean JNICALL Java_com_tencent_yolov8ncnn_YOLOv8Ncnn_openCamera(JNIEnv* env, jobject thiz, jint facing)
{
    std::lock_guard<std::mutex> lifecycle_guard(lifecycle_lock);
    if (facing < 0 || facing > 1)
        return JNI_FALSE;

    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "openCamera %d", facing);

    if (!g_camera || g_camera->open((int)facing) != 0)
        return JNI_FALSE;

    return JNI_TRUE;
}

// public native boolean closeCamera();
JNIEXPORT jboolean JNICALL Java_com_tencent_yolov8ncnn_YOLOv8Ncnn_closeCamera(JNIEnv* env, jobject thiz)
{
    std::lock_guard<std::mutex> lifecycle_guard(lifecycle_lock);
    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "closeCamera");

    g_camera->close();

    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL Java_com_tencent_yolov8ncnn_YOLOv8Ncnn_setZoom(JNIEnv* env, jobject thiz, jfloat zoom_ratio)
{
    std::lock_guard<std::mutex> lifecycle_guard(lifecycle_lock);
    if (!g_camera || zoom_ratio < 1.f || zoom_ratio > 8.f) return JNI_FALSE;
    return g_camera->set_zoom((float)zoom_ratio) == 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jfloat JNICALL Java_com_tencent_yolov8ncnn_YOLOv8Ncnn_getFps(JNIEnv* env, jobject thiz)
{
    return g_fps.load();
}

JNIEXPORT jboolean JNICALL Java_com_tencent_yolov8ncnn_YOLOv8Ncnn_setInputSize(JNIEnv* env, jobject thiz, jint size)
{
    std::lock_guard<std::mutex> lifecycle_guard(lifecycle_lock);
    if (!g_camera || size < 320 || size > 640) return JNI_FALSE;
    g_camera->set_input_size((int)size);
    return JNI_TRUE;
}

// public native boolean setOutputWindow(Surface surface);
JNIEXPORT jboolean JNICALL Java_com_tencent_yolov8ncnn_YOLOv8Ncnn_setOutputWindow(JNIEnv* env, jobject thiz, jobject surface)
{
    std::lock_guard<std::mutex> lifecycle_guard(lifecycle_lock);
    ANativeWindow* win = ANativeWindow_fromSurface(env, surface);

    if (!g_camera || !win)
        return JNI_FALSE;

    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "setOutputWindow %p", win);

    g_camera->set_window(win);
    ANativeWindow_release(win);

    return JNI_TRUE;
}

}
