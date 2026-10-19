# 白柠视觉辅助

一款面向 Android 的离线实时视觉检测工具。它使用手机摄像头采集画面，在本地通过 ncnn + YOLOv8 完成人物、车辆检测，并将结果叠加到横屏取景画面上。

项目名称：**白柠视觉辅助**  
英文标识：`VisionOverlay`  
当前版本：`1.2.0`  
最低版本：Android 9.0（API 28）  
运行方式：完全离线，无广告、无埋点、无相机画面上传

> 这是一个个人二次开发项目，核心相机、推理和模型能力来自上游开源项目。本仓库重点提供 Android 应用层、绘制控制、启动流程和设置界面。

## 功能概览

- 横屏相机预览，启动动画按 16:9 比例播放
- 本地人物检测和 COCO 车辆类别检测
- YOLOv8 Pose 人体骨骼绘制（当前模型为 COCO 17 点）
- 人物框、车辆框、骨骼、手持标记、类别名称、置信度和 FPS 独立开关
- Vulkan 优先、CPU 兜底的 ncnn 推理路径
- 前后摄像头切换和镜头缩放
- 可隐藏的主页控制栏
- 音量加显示控制栏，音量减隐藏控制栏
- 应用内可拖动操作按钮，无需 Root、无需系统悬浮窗权限
- 启动页视频、离线卡密入口、官方频道和开源地址入口
- 中文设置页，按操作控制、识别性能、绘制显示、Root 增强、关于与链接分类
- 右下角作者标识：`telegram@BNSTR`

## 截图与界面

应用采用横屏布局：取景画面作为主体，控制栏使用半透明深色材料；设置页采用浅色分组列表和侧栏导航。详细的界面结构、字号、交互和动效说明见 [UI_DESIGN.md](UI_DESIGN.md)。

启动页视频会在本地循环播放并静音。首次进入需要输入本地卡密：

```text
jujuenb
```

卡密只在应用进程内比较，不连接服务器，也不提供服务器授权、到期或吊销能力。

## 项目结构

```text
app/src/main/
├── java/com/visionoverlay/app/
│   ├── LaunchActivity.kt       启动视频、卡密弹窗、模型加载
│   ├── MainActivity.kt         横屏相机主页和操作栏
│   ├── SettingsActivity.kt     分类设置页
│   ├── OverlayView.kt          透明绘制层和水印
│   ├── AppRuntime.kt           共享模型初始化
│   ├── AppUi.kt                UI 样式和交互工具
│   └── SettingsStore.kt        设置持久化
├── java/com/tencent/yolov8ncnn/
│   └── YOLOv8Ncnn.java         上游 JNI 门面
├── cpp/upstream/               ncnn、YOLOv8、NDK Camera 代码
├── assets/                     YOLOv8n 检测和 Pose 模型
└── res/
    ├── raw/launch_video.mp4    横屏启动动画
    └── drawable-nodpi/         启动首帧占位图
```

## 上游项目与复用范围

| 上游项目 | 复用内容 |
| --- | --- |
| [nihui/ncnn-android-yolov8](https://github.com/nihui/ncnn-android-yolov8) | ncnn 初始化、Vulkan 后端、YOLOv8 检测与 Pose 解码、NMS、COCO 标签、NDK Camera 和 JNI 调度结构 |
| [OpenCamera](https://github.com/Almalence/OpenCamera) | 相机功能和控制器的参考实现；其应用内部依赖较多，当前版本没有直接复制整套 OpenCamera 应用 |

本项目没有从零重写 ncnn、相机管线或 YOLOv8 后处理。新增代码主要位于 Kotlin 应用层和 UI 层。上游来源、文件映射和许可证说明见：

- [UPSTREAM.md](UPSTREAM.md)
- [SOURCE_ARCHIVES.md](SOURCE_ARCHIVES.md)
- [docs/UPSTREAM_LICENSES.md](docs/UPSTREAM_LICENSES.md)

## 编译运行

### 环境要求

- Android Studio 或 Gradle
- Android SDK 35
- Android NDK `29.0.14206865`
- JDK 21

### 命令行构建

在项目根目录执行：

```powershell
$env:JAVA_HOME='E:\114514\jdk21\jdk-21.0.12'
$env:ANDROID_HOME='E:\114514\android-sdk'
$env:ANDROID_SDK_ROOT='E:\114514\android-sdk'
$env:GRADLE_USER_HOME='E:\114514\.gradle-kernelbn'

.\gradlew.bat :app:assembleDebug :app:lintDebug --no-daemon --console=plain
```

生成文件：

```text
app/build/outputs/apk/debug/app-debug.apk
```

安装到已连接设备：

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r .\app\build\outputs\apk\debug\app-debug.apk
```

应用只申请 `CAMERA` 权限，不申请 `INTERNET` 或 `SYSTEM_ALERT_WINDOW` 权限。首次运行需要用户授予相机权限。

## 性能与兼容性说明

- 默认推理分辨率为 416，可在设置中选择 320、416 或 640。
- Vulkan 是否可用取决于设备 GPU 和驱动；CPU 模式用于兼容性兜底。
- 实际 FPS 会随设备、摄像头分辨率、模型模式和场景复杂度变化。
- 当前 Pose 模型输出 COCO 17 点，不是 33 点全身骨骼。
- 严重失焦、遮挡、极远距离和低光环境会降低检测稳定性。
- Root 增强选项默认关闭，且不属于运行检测的必要条件。

## 隐私

- 推理在设备本地完成。
- 相机帧不上传、不写入网络服务。
- 项目没有广告和埋点。
- 官方频道、GitHub 和上游链接只会在用户主动点击后交给系统浏览器或 Telegram 打开。

## 发布 APK

建议将 APK 作为 GitHub Release 附件发布，而不是提交到源码历史中。Release 文件名可以使用：

```text
白柠视觉辅助-v1.2.0-debug.apk
```

这是调试签名包，适合测试和内部发布。正式公开发布前，请替换为自己的 release signing key，并在 Release 页面说明签名和版本信息。

## 上传到 GitHub

在 GitHub 创建空仓库后，在项目根目录执行：

```powershell
git init
git add .
git commit -m "初始发布：白柠视觉辅助 1.2.0"
git branch -M main
git remote add origin https://github.com/<你的账号>/<你的仓库>.git
git push -u origin main
```

建议把 APK 放到 GitHub Release 的附件中；源码仓库只提交工程、模型说明和许可证文件。发布前请检查 `docs/UPSTREAM_LICENSES.md`，并确认所有上游版权声明仍然保留。

## 许可证与致谢

本项目的上游代码、ncnn、OpenCV、模型和 OpenCamera 相关文件分别受其各自许可证约束。发布时请保留源文件中的版权声明，并阅读 [docs/UPSTREAM_LICENSES.md](docs/UPSTREAM_LICENSES.md)。

感谢以下开源项目：

- [nihui/ncnn-android-yolov8](https://github.com/nihui/ncnn-android-yolov8)
- [OpenCamera](https://github.com/Almalence/OpenCamera)
- [ncnn](https://github.com/Tencent/ncnn)
- [OpenCV](https://opencv.org/)

## 联系方式

- 官方频道：<https://t.me/BNSTR>
- 项目地址：<https://github.com/fsq04116-oss/bn-android-vision-tracker>
- 作者：`telegram@BNSTR`
