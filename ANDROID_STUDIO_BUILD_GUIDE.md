# Android Studio 编译 APK 指南

本指南将帮助您在 Android Studio 中编译生成 APK 文件。

## 方法一：使用 Android Studio 菜单（推荐）

### 1. 打开项目
1. 启动 Android Studio
2. 选择 **File → Open**
3. 导航到项目目录 `d:\devspace\tv-baidu-player`
4. 点击 **OK** 打开项目
5. 等待 Gradle 同步完成（底部状态栏会显示进度）

### 2. 编译 Debug APK
1. 在顶部菜单栏，选择 **Build → Build Bundle(s) / APK(s) → Build APK(s)**
2. 等待编译完成（可能需要几分钟，首次编译会更慢）
3. 编译成功后，右下角会弹出提示：**APK(s) generated successfully**
4. 点击提示中的 **locate** 链接，会自动打开 APK 所在目录
5. APK 文件位置：`app\build\outputs\apk\debug\app-debug.apk`

### 3. 编译 Release APK（可选）
如果需要发布版本：
1. 选择 **Build → Build Bundle(s) / APK(s) → Build APK(s)**
2. 但首先需要配置签名（请参考下文"配置签名"部分）

## 方法二：使用 Gradle 面板

### 1. 打开 Gradle 面板
1. 在 Android Studio 右侧边栏，点击 **Gradle** 图标（大象图标）
2. 如果没有看到，选择 **View → Tool Windows → Gradle**

### 2. 执行编译任务
1. 展开 **tv-baidu-player → app → Tasks → build**
2. 双击 **assembleDebug** 任务
3. 在底部的 **Build** 窗口可以看到编译进度
4. 编译完成后，APK 生成在 `app\build\outputs\apk\debug\app-debug.apk`

## 方法三：使用终端命令

### 1. 打开终端
在 Android Studio 底部选择 **Terminal** 标签页

### 2. 执行编译命令
```bash
# Windows 系统
gradlew assembleDebug

# 如果遇到权限问题，使用：
.\gradlew assembleDebug
```

### 3. 查看输出
编译成功后，会显示：
```
BUILD SUCCESSFUL in XXs
```
APK 文件位置：`app\build\outputs\apk\debug\app-debug.apk`

## 方法四：直接运行到设备/模拟器（最简单）

### 1. 连接设备或启动模拟器
- **模拟器**：点击顶部工具栏的 Device Manager，启动您创建的 Android TV 模拟器
- **真机**：通过 USB 或 Wi-Fi 连接电视，确保已开启 ADB 调试

### 2. 选择目标设备
在顶部工具栏的设备下拉菜单中，选择您的模拟器或电视设备

### 3. 点击运行按钮
- 点击绿色的 **Run** 按钮（播放图标）
- 或按快捷键 **Shift + F10**
- 应用会自动编译、安装并启动

### 4. 获取生成的 APK
即使通过 Run 按钮安装，APK 文件也会生成在：
`app\build\outputs\apk\debug\app-debug.apk`

## 检查编译结果

### 验证 APK 文件
1. 打开文件管理器
2. 导航到 `d:\devspace\tv-baidu-player\app\build\outputs\apk\debug\`
3. 应该能看到 `app-debug.apk` 文件
4. 文件大小应该在 **10MB - 30MB** 左右

### 如果没有找到 APK 文件
可能的原因：
1. 编译失败：查看 **Build** 窗口的错误信息
2. 路径不对：确保在项目根目录查找
3. 需要先编译：确保执行了编译步骤

## 常见问题

### Q1: Gradle 同步失败
**解决方案**：
1. 确保网络连接正常（需要下载依赖）
2. File → Invalidate Caches → Invalidate and Restart
3. 删除项目根目录的 `.gradle` 文件夹后重新打开项目

### Q2: 编译错误
**解决方案**：
1. 查看 **Build** 窗口的具体错误信息
2. 参考 [`BUILD_TROUBLESHOOTING.md`](BUILD_TROUBLESHOOTING.md)
3. 执行清理：Build → Clean Project，然后 Build → Rebuild Project

### Q3: 找不到 JDK
**解决方案**：
1. File → Project Structure → SDK Location
2. 确保 JDK 路径正确（需要 JDK 11 或更高版本）
3. 如果没有，点击 **Download JDK** 下载

### Q4: 编译很慢
**解决方案**：
1. 首次编译会下载大量依赖，需要耐心等待
2. 确保网络连接稳定
3. 可以配置 Gradle 离线模式（File → Settings → Build, Execution, Deployment → Gradle）

## 下一步

编译成功后，您可以：
1. 将 APK 文件拷贝到 U 盘，插入电视安装
2. 使用 ADB 命令安装：`adb install app\build\outputs\apk\debug\app-debug.apk`
3. 在模拟器中直接运行测试

如果遇到安装问题，请参考 [`INSTALLATION_TROUBLESHOOTING.md`](INSTALLATION_TROUBLESHOOTING.md)