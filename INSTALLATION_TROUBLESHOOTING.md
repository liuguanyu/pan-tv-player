# 应用安装故障排查指南

当您遇到"应用似乎没有安装上"的问题时，请按照以下步骤逐一排查：

## 1. 检查APK文件

首先确认APK文件已正确生成：
- 路径：`app/build/outputs/apk/debug/app-debug.apk`
- 文件大小：应该在几MB左右，过小可能编译不完整

## 2. 电视端安装问题排查

### 2.1 允许未知来源安装
1. 打开电视设置
2. 找到"应用"或"应用程序"设置
3. 找到"未知来源"或"安装未知应用"选项
4. 允许通过USB或文件管理器安装应用

### 2.2 使用U盘安装
1. 将 `app-debug.apk` 文件拷贝到U盘根目录
2. 将U盘插入电视USB接口
3. 在电视上打开文件管理器
4. 找到U盘中的APK文件并点击安装

### 2.3 使用ADB安装（推荐）
1. 确保电视已开启ADB调试：
   - 设置 → 关于 → 连续点击"版本号"7次启用开发者选项
   - 返回设置 → 开发者选项 → 开启"ADB调试"
2. 确保电脑与电视在同一网络中
3. 在电脑上执行以下命令查找电视IP：
   ```bash
   adb connect [电视IP地址]:5555
   ```
4. 安装应用：
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

## 3. 常见安装失败原因及解决方案

### 3.1 签名问题
- Debug版本可能在某些设备上有兼容性问题
- 解决方案：创建Release版本
  ```bash
  ./gradlew assembleRelease
  ```
  生成路径：`app/build/outputs/apk/release/app-release-unsigned.apk`

### 3.2 架构不兼容
- 检查电视CPU架构（通常为arm64-v8a或armeabi-v7a）
- 在`app/build.gradle`中确认支持的架构：
  ```gradle
  ndk {
      abiFilters 'armeabi-v7a', 'arm64-v8a'
  }
  ```

### 3.3 存储空间不足
- 检查电视存储空间是否充足（至少需要50MB可用空间）

### 3.4 应用权限问题
- 某些电视系统对文件访问权限较严格
- 安装后检查应用权限设置，确保有存储访问权限

## 4. 查看安装日志

### 4.1 ADB安装日志
使用ADB安装时会显示详细错误信息：
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
参数`-r`表示覆盖安装

### 4.2 电视端日志
部分电视支持查看安装日志：
- 设置 → 开发者选项 → 日志查看器

## 5. 验证安装

安装成功后：
1. 在电视应用列表中找到"Baidu TV Player"
2. 点击应用图标启动
3. 首次启动可能需要一些初始化时间

## 6. 如果仍然无法安装

1. 清理项目重新编译：
   ```bash
   ./gradlew clean
   ./gradlew assembleDebug
   ```

2. 检查电视系统版本是否过低（建议Android 8.0以上）

3. 尝试在其他Android设备上安装测试兼容性

4. 检查应用是否已在电视上安装过旧版本，需要先卸载再安装