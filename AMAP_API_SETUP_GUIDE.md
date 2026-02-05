# 高德地图API配置指南

## 应用信息

### 包名
```
com.baidu.tv.player
```

### SHA1获取方法

由于开发环境需要配置JDK，这里提供几种获取SHA1的方法：

## 方法1A：通过Android Studio Terminal获取（推荐）

1. 打开 Android Studio
2. 打开本项目
3. 在底部找到 **Terminal** (终端) 选项卡
   - 如果看不到，点击菜单栏 **View** -> **Tool Windows** -> **Terminal**
   - 或使用快捷键 **Alt + F12**
4. 在终端中输入以下命令并回车：
   ```bash
   gradlew signingReport
   ```
5. 等待命令执行完成
6. 在输出中找到 **Variant: debug** 和 **Variant: release** 部分
7. 复制对应版本的 **SHA1** 值（格式：XX:XX:XX:XX:...）

示例输出：
```
Variant: debug
Config: debug
Store: C:\Users\用户名\.android\debug.keystore
Alias: AndroidDebugKey
MD5: ...
SHA1: AB:CD:EF:12:34:56:78:90:AB:CD:EF:12:34:56:78:90:AB:CD:EF:12
SHA-256: ...

Variant: release
Config: release
Store: /path/to/your/release.keystore
Alias: your-alias
MD5: ...
SHA1: 12:34:56:78:90:AB:CD:EF:12:34:56:78:90:AB:CD:EF:12:34:56:78
SHA-256: ...
```

**开发测试时使用debug版SHA1，正式发布时使用release版SHA1**

## 方法1B：通过Gradle面板获取（如果可见）

1. 打开 Android Studio
2. 打开本项目
3. 点击右侧的 **Gradle** 面板（大象图标）
4. 如果看不到Tasks，点击面板顶部的刷新按钮（圆形箭头）
5. 尝试以下路径之一：
   - **app** -> **Tasks** -> **android** -> **signingReport**
   - 或者在Gradle面板顶部的搜索框中输入 `signingReport`
6. 双击 **signingReport**
7. 在底部的 **Run** 或 **Build** 窗口查看输出
8. 找到对应版本的 **SHA1** 值并复制

**故障排除**：
- 如果看不到"android"分组，尝试点击Gradle面板顶部的"刷新"按钮
- 如果整个Tasks列表为空，确保项目已完全加载（等待右下角的进度条完成）
- 如果仍然没有，使用方法1A（Terminal方式）更可靠

## 方法2：使用keytool命令

### 前提条件
- 已安装JDK
- 已配置JAVA_HOME环境变量
- 已有release keystore文件

### 如果没有配置JAVA_HOME环境变量

如果您在运行命令时遇到以下错误：
```
ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
```

这表示系统没有找到JDK。有以下几种解决方案：

#### 方案1：使用Android Studio内置Terminal（推荐）

Android Studio自带JDK，其内置的Terminal会自动配置好环境：

1. 打开Android Studio
2. 打开本项目
3. 点击底部的 **Terminal** 选项卡
4. 直接运行命令（无需配置）：
   ```bash
   keytool -list -v -keystore %USERPROFILE%\.android\debug.keystore -alias androiddebugkey -storepass android -keypass android
   ```

#### 方案2：手动查找并使用JDK

1. 查找Android Studio自带的JDK位置，通常在：
   ```
   C:\Program Files\Android\Android Studio\jbr\bin\java.exe
   ```
   或
   ```
   C:\Program Files\Android\Android Studio\jre\bin\java.exe
   ```

2. 使用完整路径运行命令：
   ```bash
   "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -list -v -keystore %USERPROFILE%\.android\debug.keystore -alias androiddebugkey -storepass android -keypass android
   ```

#### 方案3：配置JAVA_HOME环境变量

1. 找到Android Studio的JDK目录（如上所示）
2. 右键"此电脑" -> "属性" -> "高级系统设置" -> "环境变量"
3. 在"系统变量"中点击"新建"
4. 变量名：`JAVA_HOME`
5. 变量值：JDK路径（例如：`C:\Program Files\Android\Android Studio\jbr`）
6. 确定并重启命令行窗口

### 获取Debug版SHA1（开发测试用）
```bash
keytool -list -v -keystore %USERPROFILE%\.android\debug.keystore -alias androiddebugkey -storepass android -keypass android
```

### 获取Release版SHA1（正式发布用）
```bash
keytool -list -v -keystore /path/to/your/release.keystore -alias your-alias
```

输入密码后，找到SHA1值并复制。

## 方法3：如果还没有release keystore

### 创建release keystore
```bash
keytool -genkey -v -keystore release.keystore -alias my-key-alias -keyalg RSA -keysize 2048 -validity 10000
```

按提示输入：
- 密码（记住这个密码，后续需要用）
- 姓名、组织等信息

### 然后用方法2获取SHA1

## 高德地图API申请步骤

### 1. 注册高德开发者账号
访问：https://lbs.amap.com/

### 2. 创建应用
1. 登录后进入控制台
2. 点击"应用管理" -> "我的应用"
3. 点击"创建新应用"
4. 填写应用名称：`百度网盘电视播放器`
5. 选择应用类型：`其他`

### 3. 添加Key
1. 在创建的应用下，点击"添加Key"或"添加新Key"
2. 填写以下信息：
   - **Key名称**：`Android端`（或任意名称）
   - **服务平台**：选择 `Android平台` 或 `Android SDK`
   - **发布版SHA1**：填入上面获取的SHA1值
   - **PackageName**：`com.baidu.tv.player`

**注意**：
- 如果没有看到"Android平台"选项，可能需要选择"Web服务"或"Web服务API"
- 高德地图的界面可能更新，但核心信息（包名和SHA1）是必需的
- 如果找不到Android平台选项，可以选择"Web服务"，同样可以使用逆地理编码功能

### 4. 获取API Key
创建成功后，会显示一个API Key，格式如：
```
1234567890abcdef1234567890abcdef
```

复制这个Key。

## 配置到应用中

### 方法1：修改LocationUtils.java（简单）

找到文件：`app/src/main/java/com/baidu/tv/player/utils/LocationUtils.java`

找到第42行左右的这一行：
```java
private static final String AMAP_API_KEY = ""; // 留空，用户可在设置中配置
```

修改为：
```java
private static final String AMAP_API_KEY = "你的API_KEY"; // 替换为实际的Key
```

### 方法2：通过配置文件（推荐）

将来可以考虑支持在设置界面中配置API Key，这样用户可以使用自己的Key。

## 高德地图API费用说明

### 免费额度
- **Web服务API**：每天30万次调用
- **Android SDK**：每天30万次调用
- 对于个人开发者完全够用

### 计费方式
- 超出免费额度后才开始计费
- 个人应用通常不会超出免费额度
- 无需绑定信用卡即可使用免费额度

### 使用限制
- 单个Key每秒最多6000次请求（QPS）
- 单个IP每秒最多50次请求

## 验证配置

配置完成后，运行应用并播放包含GPS信息的视频，查看logcat日志：

```
GPS_DEBUG:🌐 尝试高德地图API反向地理编码
GPS_DEBUG:✅ 高德地图地址: 北京市朝阳区xxx街道xxx号
```

如果看到类似输出，说明配置成功。

## 故障排除

### 问题1：返回错误状态码
- 检查API Key是否正确
- 检查包名和SHA1是否匹配
- 确认API Key已启用Web服务API

### 问题2：请求超时
- 检查网络连接
- 可能是请求频率过高，稍后重试

### 问题3：无法解析地址
- 确认GPS坐标有效
- 可能该位置在偏远地区，数据不完整

## 注意事项

1. **不要将API Key提交到公开仓库**
   - 如果使用Git，将包含Key的文件加入`.gitignore`
   - 考虑使用环境变量或本地配置文件

2. **Key的安全性**
   - 限制Key的使用范围（只允许特定包名和SHA1）
   - 定期检查使用量，防止被盗用

3. **备用方案**
   - 即使不配置高德地图API，应用仍然可以使用免费的Nominatim API
   - 只是在中国境内速度可能较慢

## 快速开始（开发测试）

如果只是想快速测试功能，可以暂时使用Debug版本的SHA1：

1. 获取Debug SHA1：
```bash
keytool -list -v -keystore %USERPROFILE%\.android\debug.keystore -alias androiddebugkey -storepass android -keypass android
```

2. 使用Debug SHA1创建高德地图Key

3. 在开发测试阶段使用这个Key

4. 正式发布前，使用Release SHA1重新创建Key

## 相关文档

- 高德地图开放平台：https://lbs.amap.com/
- 高德Web服务API文档：https://lbs.amap.com/api/webservice/summary/
- 逆地理编码API：https://lbs.amap.com/api/webservice/guide/api/georegeo

## 联系方式

如有问题，可以：
1. 查看高德地图开发文档
2. 访问高德地图开发者社区
3. 联系高德技术支持