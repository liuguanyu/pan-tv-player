# 登录轮询逻辑修复文档

## 问题描述

从 Logcat 日志分析，应用在登录时虽然成功获取了设备码（device_code）和二维码URL，但立即显示"登录失败"，没有等待用户扫码授权。

## 根本原因分析

### 1. Logcat 关键日志

```
<-- 200 OK .../device/code
{"device_code":"da877fc0d23ca16b6d80665032ed313a",...}

<-- 400 Bad Request .../token
{"error":"authorization_pending","error_description":"User has not yet completed the authorization"}
```

### 2. 问题根源

**百度 OAuth API 的行为**：
- 当用户尚未扫码授权时，`/token` 端点返回 **HTTP 400 Bad Request**
- 响应体包含：`{"error":"authorization_pending",...}`
- 这是**正常的轮询状态**，应继续等待，而不是报错

**原代码的错误处理**：
```java
// BaiduAuthService.java (修复前)
public void onResponse(Call<TokenResponse> call, Response<TokenResponse> response) {
    if (response.isSuccessful() && response.body() != null) {
        // 处理成功响应...
    } else {
        // ❌ 错误：将 400 响应直接当作失败处理
        callback.onError("授权失败");
    }
}
```

**Retrofit 的行为**：
- `response.isSuccessful()` 对于 4xx 状态码返回 `false`
- 原代码进入 `else` 分支，调用 `callback.onError("授权失败")`
- 导致 `AuthRepository` 更新状态为 `ERROR`
- UI 显示错误页面，轮询中止

### 3. 与 Electron 实现的对比

Electron 项目 (`auth.service.ts`) 的正确做法：
```typescript
// 循环轮询，正确处理 authorization_pending
for (let i = 0; i < 60; i++) {
    const data = response.data as TokenResponse;
    
    // 授权成功
    if (data.access_token) {
        // 保存 token...
        return true;
    }
    
    // 授权中 - 继续等待
    if (data.error === 'authorization_pending') {
        await sleep(interval * 1000);
        continue; // ✅ 继续轮询
    }
}
```

## 修复方案

### 修改文件：`app/src/main/java/com/baidu/tv/player/auth/BaiduAuthService.java`

**关键改动**：

1. **处理 400 错误响应**：解析 errorBody，检查是否为 `authorization_pending`
2. **继续轮询**：若是 pending 状态，延迟后递归调用继续轮询
3. **保持二维码显示**：不触发 error 回调，UI 保持轮询状态

**修复后的代码逻辑**：

```java
@Override
public void onResponse(Call<TokenResponse> call, Response<TokenResponse> response) {
    if (response.isSuccessful() && response.body() != null) {
        TokenResponse tokenResponse = response.body();
        
        // 授权成功
        if (tokenResponse.getAccessToken() != null && !tokenResponse.getAccessToken().isEmpty()) {
            // 保存 token，调用 callback.onSuccess(true)
        } else {
            handleAuthError(tokenResponse.getError(), ...);
        }
    } else {
        // ✅ 新增：处理 400 错误
        try {
            String errorBody = response.errorBody().string();
            
            // ✅ 检查是否为 pending 状态
            if (errorBody.contains("authorization_pending")) {
                // ✅ 继续轮询，不报错
                handler.postDelayed(() -> {
                    pollDeviceCodeStatus(deviceCode, count + 1, callback);
                }, ApiConstants.POLLING_INTERVAL);
            } else if (errorBody.contains("expired_token")) {
                callback.onError("授权已过期");
            } else {
                callback.onError("授权失败: " + response.code());
            }
        } catch (Exception e) {
            callback.onError("授权失败: " + e.getMessage());
        }
    }
}
```

## 预期行为（修复后）

1. **获取设备码** → 显示二维码
2. **开始轮询**：
   - 每 5 秒请求一次 `/token` 端点
   - 收到 `authorization_pending` → 继续等待（UI 保持二维码显示）
   - 用户扫码授权成功 → 收到 access_token → 跳转主界面
   - 超时（300秒）或过期 → 显示错误

3. **UI 状态变化**：
   ```
   LOADING (正在获取设备码...)
     ↓
   DEVICE_CODE_RECEIVED (设备码获取成功)
     ↓
   POLLING (等待授权中...) ← 保持此状态直到授权或超时
     ↓
   AUTHENTICATED (授权成功) 或 ERROR (授权失败/超时)
   ```

## 测试步骤

### 1. 重新编译
在 Android Studio 中：
- **Build** > **Clean Project**
- **Build** > **Rebuild Project**

### 2. 运行应用
- 启动应用到模拟器或真机
- 观察 Logcat 输出

### 3. 验证正确的 Logcat 日志

**期望看到的日志序列**：
```
// 1. 获取设备码成功
<-- 200 OK .../device/code
{"device_code":"xxx","user_code":"xxx","qrcode_url":"..."}

// 2. 第一次轮询（pending）
--> GET .../token?grant_type=device_token&code=xxx
<-- 400 Bad Request
{"error":"authorization_pending",...}

// 3. 等待 5 秒后，第二次轮询（pending）
--> GET .../token?grant_type=device_token&code=xxx
<-- 400 Bad Request
{"error":"authorization_pending",...}

// 4. 持续轮询...直到用户扫码

// 5. 用户扫码后，授权成功
--> GET .../token?grant_type=device_token&code=xxx
<-- 200 OK
{"access_token":"xxx","refresh_token":"xxx",...}
```

**关键验证点**：
- ✅ 二维码应该**持续显示**，不应消失
- ✅ 状态文字显示："等待授权中..." 或 "请使用手机百度网盘APP扫描二维码登录"
- ✅ **不应该**显示"登录失败"或错误页面
- ✅ Logcat 应该每隔 5 秒看到一次轮询请求
- ✅ 轮询应该使用**同一个 device_code**，而不是每次都重新获取

### 4. 完整测试流程

1. **启动应用** → 自动显示登录界面
2. **观察二维码** → 应在几秒内显示
3. **打开手机百度网盘 APP** → 扫描二维码
4. **在手机上确认授权**
5. **电视端应自动跳转** → 进入主界面
6. **验证登录状态** → 检查是否可以访问网盘文件

## 可能遇到的问题

### 问题 1：仍然显示"登录失败"

**原因**：可能是其他类型的错误
**排查**：
1. 检查 Logcat 中的完整错误消息
2. 确认网络连接正常
3. 验证 BaiduConfig.java 中的 APP_KEY 和 SECRET_KEY

### 问题 2：轮询没有开始

**原因**：device_code 获取失败
**排查**：
1. 检查 Logcat 是否有 200 OK 响应
2. 确认 API URL 正确：`https://openapi.baidu.com/oauth/2.0/device/code`

### 问题 3：扫码后没反应

**原因**：
- 可能 token 解析失败
- 或者回调没有正确处理

**排查**：
1. 检查 Logcat 中 token 响应的完整内容
2. 确认 `TokenResponse` 模型正确解析字段

## 相关文件

- **修改的文件**：
  - `app/src/main/java/com/baidu/tv/player/auth/BaiduAuthService.java` (轮询逻辑)

- **相关文件**：
  - `app/src/main/java/com/baidu/tv/player/auth/AuthRepository.java` (状态管理)
  - `app/src/main/java/com/baidu/tv/player/auth/LoginActivity.java` (UI)
  - `app/src/main/java/com/baidu/tv/player/auth/AuthViewModel.java` (ViewModel)

- **参考实现**：
  - `D:\devspace\dupan-player\src\services\auth.service.ts` (Electron 版本)

## 总结

这次修复的核心是**正确处理 OAuth Device Flow 的轮询机制**：

1. **理解 API 语义**：`authorization_pending` 是正常状态，不是错误
2. **处理 HTTP 状态码**：400 不一定是失败，需要解析响应体
3. **保持轮询状态**：在 pending 时继续轮询，不中断流程
4. **用户体验**：保持二维码显示，让用户有足够时间扫码

修复后，登录流程应该完全符合 OAuth 2.0 Device Authorization Grant 规范，与 Electron 版本行为一致。