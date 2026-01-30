# Android TV 模拟器调试指南

本指南将帮助您在 Android Studio 中创建和配置 Android TV 模拟器，以便在没有实体电视的情况下调试应用。

## 1. 打开设备管理器 (Device Manager)

1. 打开 Android Studio。
2. 在顶部工具栏中，点击手机图标的 **Device Manager** 按钮（或通过菜单：Tools -> Device Manager）。

## 2. 创建新的虚拟设备

1. 在 Device Manager 窗口中，点击 **Create Device**（或是 "+" 号）。
2. 在左侧类别列表（Category）中，选择 **TV**。
3. 在中间列表中，选择一个设备配置，例如 **Android TV (1080p)** 或 **Android TV (720p)**。
   * *建议选择 1080p 以获得更好的预览效果。*
4. 点击 **Next**。

## 3. 选择系统镜像 (System Image)

1. 您需要选择一个 Android 系统版本。根据本项目需求（Android 9），建议下载 **Pie (API 28)** 或更高版本的镜像。
2. 点击 **x86 Images** 标签页。
3. 找到 **Pie** (API 28) 或 **R** (API 30)，目标架构 (Target) 应为 **Android TV**。
4. 如果该行旁边有下载图标（向下箭头），点击下载并等待安装完成。
5. 选中下载好的镜像，点击 **Next**。

## 4. 配置虚拟设备

1. **AVD Name**: 给模拟器起个名字，例如 "Sony TV Emulator"。
2. **Graphics**: 建议设置为 **Hardware - GLES 2.0** 以获得更流畅的图形性能。
3. **Memory**: 点击 "Show Advanced Settings"，向下滚动找到 Memory。建议将 RAM 设置为 **2048 MB (2GB)** 或更多，以确保流畅运行。
4. 点击 **Finish**。

## 5. 启动模拟器

1. 在 Device Manager 列表中找到刚创建的设备。
2. 点击 **播放 (Play)** 按钮启动模拟器。
3. 等待模拟器启动完成，出现 Android TV 主界面。
   * *注意：第一次启动可能需要几分钟时间。*

## 6. 运行应用

### 方法 A：通过 Android Studio (推荐)
1. 确保模拟器已运行。
2. 在 Android Studio 顶部工具栏的设备下拉菜单中，选择您的电视模拟器。
3. 点击绿色的 **Run** (播放) 按钮 (Shift+F10)。
4. 应用将自动编译、安装并打开。

### 方法 B：通过命令行
如果模拟器已经启动，您可以在终端运行：
```bash
./gradlew installDebug
```
安装成功后，在模拟器中的应用列表里找到 "Baidu TV Player" 并打开。

## 7. 模拟器操作指南

由于电脑没有遥控器，操作 Android TV 模拟器需要使用键盘或模拟器侧边栏的控制器：

*   **方向键 (Up/Down/Left/Right)**: 对应遥控器方向键。
*   **回车 (Enter)**: 确认/点击 (OK)。
*   **ESC**: 返回 (Back)。
*   **F1 / Home**: 回到主屏幕。

## 8. 常见问题排查

*   **模拟器启动黑屏/卡死**:
    *   检查电脑是否开启了虚拟化技术 (Intel VT-x 或 AMD-V)，需在 BIOS 中开启。
    *   尝试在 AVD 设置中将 Graphics 改为 "Software"，虽然慢一点但兼容性更好。
*   **安装失败 "INSTALL_FAILED_NO_MATCHING_ABIS"**:
    *   这通常是因为您的电脑架构（如 M1/M2 Mac）与选的系统镜像不匹配。请确保下载了对应架构（arm64-v8a 或 x86_64）的镜像。
*   **应用崩溃**:
    *   查看 Android Studio底部的 **Logcat** 面板，过滤 "com.baidu.tv.player"，查看错误日志。