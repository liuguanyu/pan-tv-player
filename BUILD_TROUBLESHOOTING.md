# 构建问题排查指南

## 常见构建问题及解决方案

### 1. 缺少资源文件

#### 问题：`@drawable/banner` 或 `@mipmap/ic_launcher` 未找到
**解决方案**：已创建占位符资源文件
- `app/src/main/res/drawable/banner.xml`
- `app/src/main/res/mipmap/ic_launcher.xml`
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- `app/src/main/res/drawable/ic_launcher_foreground.xml`

### 2. Gradle同步问题

#### 问题：Gradle无法同步或下载依赖
**解决方案**：
1. 检查网络连接
2. 在Android Studio中：`File` -> `Invalidate Caches / Restart`
3. 删除`.gradle`文件夹并重新同步
4. 检查`gradle.properties`中的代理设置

### 3. SDK版本问题

#### 问题：找不到指定的SDK版本
**解决方案**：
1. 打开SDK Manager（`Tools` -> `SDK Manager`）
2. 安装以下组件：
   - Android 9.0 (API 28)
   - Android SDK Build-Tools
   - Android SDK Platform-Tools
   - Android SDK Tools

### 4. 依赖冲突

#### 问题：依赖版本冲突
**解决方案**：
在`app/build.gradle`中检查并统一以下库的版本：
```gradle
androidx.appcompat:appcompat:1.6.1
androidx.leanback:leanback:1.2.0-alpha02
com.google.android.exoplayer:exoplayer:2.19.1
```

### 5. Java版本问题

#### 问题：不兼容的Java版本
**解决方案**：
1. 确保使用Java 11或更高版本
2. 在`build.gradle`中设置：
```gradle
compileOptions {
    sourceCompatibility JavaVersion.VERSION_11
    targetCompatibility JavaVersion.VERSION_11
}
```

#### 问题：JAVA_HOME未设置或Java命令未找到
**解决方案**：

##### 步骤1: 检查Java版本
打开命令提示符，运行：
```cmd
java -version
```

如果显示"未找到命令"，则需要安装Java。

##### 步骤2: 确认Java 11+已安装
项目需要Java 11或更高版本。如果您使用的是Java 11+，应该看到类似：
```
java version "11.0.x" 或更高版本
```

##### 步骤3: 设置JAVA_HOME环境变量（Windows）

1. **找到Java安装路径**
   - 通常在: `C:\Program Files\Java\jdk-11` 或类似路径
   - 或通过Android Studio: File -> Project Structure -> SDK Location -> JDK location

2. **设置环境变量**
   - 右键"此电脑" -> "属性"
   - 点击"高级系统设置"
   - 点击"环境变量"
   - 在"系统变量"区域点击"新建"
   - 变量名: `JAVA_HOME`
   - 变量值: Java安装路径（例如: `C:\Program Files\Java\jdk-11`）
   - 点击"确定"

3. **添加到PATH**
   - 在"系统变量"中找到"Path"
   - 点击"编辑"
   - 点击"新建"
   - 添加: `%JAVA_HOME%\bin`
   - 点击"确定"

4. **验证设置**
   - 关闭并重新打开命令提示符
   - 运行: `java -version`
   - 运行: `echo %JAVA_HOME%`

#### 问题：Unsupported class file major version 65
**解决方案**：
此错误表示Gradle版本不支持当前Java版本。已将Gradle版本升级到8.5以支持Java 21。

如果仍有问题：
1. 清理Gradle缓存
2. 重启命令提示符
3. 使用Android Studio构建（推荐）

### 6. Kotlin插件问题

#### 问题：项目中包含Kotlin相关错误
**解决方案**：
本项目是纯Java项目，不需要Kotlin插件。如果看到Kotlin相关错误，检查：
1. `build.gradle`中是否错误添加了Kotlin插件
2. 删除任何Kotlin相关的配置

### 7. Gradle版本兼容性

#### 问题：'org.gradle.api.artifacts.Dependency org.gradle.api.artifacts.dsl.DependencyHandler.module(java.lang.Object)'
**解决方案**：
已移除不必要的Kotlin插件依赖。检查项目根目录的`build.gradle`文件，确保只包含：
```gradle
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:7.4.2'
    }
}
```

### 8. 配置文件缺失

#### 问题：`BaiduConfig.java` 未找到
**解决方案**：
已创建包含完整配置的`BaiduConfig.java`文件。该文件在`.gitignore`中被排除，不会提交到版本控制。

### 8. AndroidManifest错误

#### 问题：Activity未声明或权限缺失
**解决方案**：
检查`AndroidManifest.xml`中是否正确声明了所有Activity和权限。当前已声明：
- MainActivity（launcher）
- LoginActivity
- FileBrowserActivity
- PlaybackActivity
- SettingsActivity

### 9. 布局文件缺失

#### 问题：找不到布局文件
**解决方案**：
确保以下布局文件存在：
- `activity_main.xml`
- `activity_login.xml`
- `activity_file_browser.xml`
- `activity_playback.xml`
- `activity_settings.xml`
- `fragment_main.xml`
- `fragment_file_browser.xml`
- `item_recent_task.xml`
- `item_file.xml`

### 10. ProGuard/R8问题

#### 问题：Release构建时崩溃或功能异常
**解决方案**：
如果需要启用代码混淆，需要在`proguard-rules.pro`中添加保留规则：
```proguard
# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }

# ExoPlayer
-keep class com.google.android.exoplayer2.** { *; }

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
```

## 推荐构建步骤（最简单的方法）

### 使用Android Studio构建（强烈推荐）

这是最简单、最可靠的构建方法，无需手动配置环境变量：

1. **安装Android Studio**
   - 下载并安装最新版Android Studio
   - Android Studio会自动包含JDK，无需单独安装Java
   - 安装Android 9.0 (API 28) SDK

2. **导入项目**
   - 打开Android Studio
   - 选择 `Open an Existing Project`
   - 选择项目根目录 `d:\devspace\tv-baidu-player`
   - 等待Gradle自动同步（首次可能需要几分钟下载依赖）

3. **配置百度API**
   - 复制 `app/src/main/java/com/baidu/tv/player/config/BaiduConfig.java.example`
   - 重命名为 `BaiduConfig.java`（保存在同一目录）
   - 填写您的百度API凭证（appId, appKey, secretKey）

4. **构建项目**
   - Build -> Clean Project
   - Build -> Rebuild Project
   - 等待构建完成

5. **运行应用**
   - 连接Android TV设备或启动TV模拟器
   - Run -> Run 'app'

### 使用命令行构建（需要配置环境变量）

如果您想使用命令行，需要先配置Java环境变量（见上文"Java版本问题"部分）：

```cmd
# 清理项目
gradlew clean

# 构建调试版APK
gradlew assembleDebug

# 查看详细错误（如果失败）
gradlew assembleDebug --stacktrace --info
```

### 快速开始总结

**如果您只想快速开始使用：**

1. 安装Android Studio（会自动包含JDK）
2. 用Android Studio打开项目
3. 配置BaiduConfig.java
4. Build -> Rebuild Project

这是最简单、最可靠的方法！无需手动配置Java环境变量。

## 清理构建

如果遇到无法解决的构建问题，尝试清理构建：

```bash
# Windows
gradlew clean
gradlew build

# Linux/Mac
./gradlew clean
./gradlew build
```

## 获取详细错误信息

如果问题仍未解决，请运行以下命令获取详细错误信息：

```cmd
gradlew build --stacktrace --info
```

## 联系支持

如果以上方法都无法解决问题，请：
1. 检查Android Studio的Build输出面板查看具体错误
2. 查看`Event Log`了解详细信息
3. 确保所有文件都已正确创建
4. 提供完整的错误堆栈信息以获得帮助