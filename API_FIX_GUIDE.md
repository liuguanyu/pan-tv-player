# API端点修复指南

## 问题诊断

### 原始问题
在运行应用时，登录失败且没有显示二维码。Logcat显示：
```
--> GET https://openapi.baidu.com/device/code?client_id=...
<-- 200 OK https://openapi.baidu.com/device/code...
<-- END HTTP (0-byte body)
```

### 根本原因
Retrofit URL拼接问题：
- **Base URL**: `https://openapi.baidu.com/oauth/2.0/`（以`/`结尾）
- **Endpoint**: `/device/code`（以`/`开头）
- **错误结果**: `https://openapi.baidu.com/device/code`（缺少`oauth/2.0/`部分）
- **正确结果**: `https://openapi.baidu.com/oauth/2.0/device/code`

**Retrofit规则**：
- 当baseUrl以`/`结尾且endpoint以`/`开头时，endpoint会替换baseUrl的路径部分
- 正确做法：baseUrl以`/`结尾，endpoint不以`/`开头

## 已修复的文件

### 1. ApiConstants.java
**位置**: `app/src/main/java/com/baidu/tv/player/network/ApiConstants.java`

**修改内容**:
```java
// 修改前（错误）
public static final String ENDPOINT_DEVICE_CODE = "/device/code";
public static final String ENDPOINT_FILE = "/xpan/file";

// 修改后（正确）
public static final String ENDPOINT_DEVICE_CODE = "device/code";
public static final String ENDPOINT_FILE = "xpan/file";
```

**所有修改的端点**:
- OAuth端点：`authorize`, `token`, `device/code`, `revoke`
- 网盘API端点：`xpan/file`, `xpan/multimedia`, `xpan/nas`

### 2. 其他相关文件
- `BaiduConfig.java`：配置已正确，无需修改
- `BaiduPanService.java`：接口定义正确，无需修改

## 测试步骤

### 1. 重新编译
在Android Studio中：
1. 点击 **Build** > **Clean Project**
2. 点击 **Build** > **Rebuild Project**
3. 等待编译完成

### 2. 运行应用
1. 连接Android TV模拟器或真实设备
2. 点击 **Run** 按钮运行应用
3. 观察Logcat输出

### 3. 验证API请求
在Logcat中搜索关键词 `OkHttp`，应该看到：
```
--> GET https://openapi.baidu.com/oauth/2.0/device/code?client_id=...&scope=basic%2Cnetdisk&response_type=device_code
<-- 200 OK https://openapi.baidu.com/oauth/2.0/device/code
<-- END HTTP (xxx-byte body)  // 注意：应该有实际的body大小，不是0
```

### 4. 验证登录界面
应该能够看到：
- 屏幕中央显示二维码
- 二维码下方显示用户码（user_code）
- 提示文字：使用百度网盘APP扫码登录

### 5. 测试扫码登录
1. 打开手机百度网盘APP
2. 扫描电视上的二维码
3. 在手机上确认授权
4. 电视应自动跳转到主界面

## 预期的API响应

### Device Code响应
```json
{
  "device_code": "xxxxxx",
  "user_code": "ABCD-EFGH",
  "verification_url": "https://openapi.baidu.com/device",
  "qrcode_url": "https://openapi.baidu.com/...",
  "expires_in": 1800,
  "interval": 5
}
```

### Token响应（扫码成功后）
```json
{
  "access_token": "xxxxxx",
  "refresh_token": "xxxxxx",
  "expires_in": 2592000,
  "scope": "basic netdisk",
  "session_key": "xxxxxx",
  "session_secret": "xxxxxx"
}
```

## 可能的其他问题

### 1. 如果仍然返回0字节
可能原因：
- 网络连接问题
- 百度API服务器问题
- App ID/Key配置错误

解决方案：
- 检查网络连接
- 使用浏览器测试API：`https://openapi.baidu.com/oauth/2.0/device/code?client_id=pVB2TAdcOLZiCldLEcG1dABS3OK2owVi&scope=basic,netdisk&response_type=device_code`
- 验证`BaiduConfig.java`中的凭据是否正确

### 2. 如果返回错误响应
检查错误码：
- `error_code: 110`: Invalid access token（需要刷新token）
- `error_code: 111`: Access token过期
- `error_code: 31034`: App不存在或已禁用

解决方案：
- 检查百度开发者平台应用状态
- 确认应用权限配置正确

### 3. 二维码显示但扫码失败
可能原因：
- Token轮询逻辑问题
- 二维码过期（默认30分钟）

检查：
- `BaiduAuthService.java`中的轮询逻辑
- Logcat中是否有轮询请求日志

## 参考文档

- 百度OAuth文档：https://pan.baidu.com/union/doc/
- Retrofit官方文档：https://square.github.io/retrofit/
- 参考实现：`D:\devspace\dupan-player\src\services\auth.service.ts`

## 下一步开发

修复登录后，需要继续完善：
1. 文件列表加载功能
2. 视频播放功能
3. 图片轮播功能
4. 地理位置识别
5. 播放历史记录
6. 设置界面

每个功能都需要类似的测试流程来确保正常工作。