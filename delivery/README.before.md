# 白柠视觉辅助 1.1.0

Android 9.0+ 原生横屏相机工具，本地运行人物/车辆检测。包名保持 com.visionoverlay.app，保留 telegram@BNSTR 水印。

## 本次更新

- 使用用户提供的 JPG 生成五种密度的桌面图标，名称改为“白柠视觉辅助”。
- 启动页播放用户提供的视频。视频已从 HEVC 转为 H.264，静音循环播放，可暂停；系统启动背景改为深色。
- 模型在后台加载，视频与卡密弹窗无需等待模型加载。卡密为 jujuenb，弹窗直接提示；输入错误可重试。验证和模型加载均完成后才打开相机。
- 卡密在本地比较，不联网验证；重新进入启动页需验证，Activity 重建保留本次验证状态。此机制不提供服务器授权、到期或吊销功能。
- 主页按钮和缩放条可以收起。音量加显示、音量减隐藏，隐藏面板不暂停识别；可在设置中禁用音量键控制。
- 默认提供可拖动的应用内“操作”悬浮按钮，普通用户可用，无需 Root 和系统悬浮窗权限。关闭音量键入口时保留触屏入口，避免无法再次展开。
- 设置改为左侧分类、右侧内容：操作控制、识别性能、绘制显示、Root 增强、关于与链接；设置即时保存。
- 启动页、卡密弹窗及设置提供官方频道入口；设置提供本项目与上游源码入口。
- FPS 在原生帧回调内采样，由界面定时读取。Root 检测移到后台并限制等待时间。

官方频道：https://t.me/BNSTR

项目源码：https://github.com/fsq04116-oss/bn-android-vision-tracker

链接通过系统浏览器或 Telegram 打开。本应用无 INTERNET 权限，相机画面和卡密均不上传。

## 工程结构与复用关系

- LaunchActivity.kt：新增视频加载页、卡密弹窗、频道入口。
- AppRuntime.kt：新增后台模型准备、共享 JNI 实例与访问状态。
- AppUi.kt：新增原生 View 主题、按钮、链接工具。
- MainActivity.kt：改造收起面板、音量键、应用内悬浮按钮。
- SettingsActivity.kt：改造分类设置与链接。
- SettingsStore.kt / Models.kt：扩展操作偏好保存。
- RootStatus.kt：改造后台检测、缓存与超时。
- OverlayView.kt：沿用透明绘制层与作者水印。
- java/com/tencent/yolov8ncnn/YOLOv8Ncnn.java：沿用 JNI 门面。
- cpp/upstream：沿用上游 ncnn 推理、后处理、NDK 相机；yolov8ncnn.cpp 小幅修改 FPS 采样。
- assets：沿用 yolov8n、yolov8n_pose 本地模型。
- res/raw/launch_video.mp4：新增 H.264 启动视频。
- res/mipmap-*/ic_launcher.png：新增附件图标。
- res/values*/styles.xml：深色窗口与 Android 12+ 系统启动背景。
- delivery：校验脚本、回滚数据与构建日志。

核心相机、ncnn/Vulkan、检测/骨骼后处理继续复用 nihui/ncnn-android-yolov8，没有重写推理底层。OpenCamera 仍为参考源，实际相机管线使用上游 NDK Camera。详见 UPSTREAM.md、SOURCE_ARCHIVES.md、docs/UPSTREAM_LICENSES.md。

当前内置 YOLOv8n / YOLOv8n_pose，骨骼为 COCO 17 点；可用推理后端为 Vulkan 和 CPU。既有部分绘制样式参数仍属于旧 Overlay 配置，原生模型绘制尚未全部接入这些参数。本次重点为启动、品牌、设置组织与操作入口。

## 编译与安装

在本目录运行 PowerShell：

```powershell
$env:JAVA_HOME='E:\114514\jdk21\jdk-21.0.12'
$env:ANDROID_HOME='E:\114514\android-sdk'
$env:ANDROID_SDK_ROOT='E:\114514\android-sdk'
$env:GRADLE_USER_HOME='E:\114514\.gradle-kernelbn'
.\gradlew.bat :app:assembleDebug :app:lintDebug --no-daemon --console=plain --offline
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r .\app\build\outputs\apk\debug\app-debug.apk
```

SDK 35、Build Tools 36.0.0、NDK 29.0.14206865，支持 arm64-v8a、armeabi-v7a、x86、x86_64。Android Studio 打开本目录也可编译。首次在其他环境构建需下载对应 Gradle 依赖、SDK/NDK。

分发 APK 为上一级目录的 白柠视觉辅助-v1.1.0-debug.apk；旧文件名 VisionOverlay-debug.apk 也更新为相同内容。保留调试签名，与本机上一版证书一致。

## 验证与回滚

VERIFICATION.txt 记录真实构建、Lint、签名、包内容、视频解码与回滚结果。当前未连接实机，需安装后验证视频播放、卡密输入、音量键与拖动交互。

ROLLBACK.sh <目标工程副本> 使用 Python 3 恢复本次修改前的源文件，并移除本次新增的应用源文件/资源；先检查当前哈希，避免覆盖目标后续修改。Windows 用 Git Bash 执行，并设置 PYTHON 指向 Python 可执行文件。仅恢复源码，不自动安装 APK；已在独立副本上验证。更新前 APK 另存为上一级目录的 白柠视觉辅助-更新前备份.apk。
