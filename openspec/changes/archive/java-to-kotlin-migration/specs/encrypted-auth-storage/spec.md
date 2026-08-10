## ADDED Requirements

### Requirement: EncryptedSharedPreferences 存储 Token
系统 SHALL 使用 `androidx.security:security-crypto` 的 `EncryptedSharedPreferences` 存储 OAuth access_token、refresh_token 等认证凭据。

#### Scenario: Token 写入加密存储
- **WHEN** `BaiduAuthService.saveAuthInfo()` 保存认证信息
- **THEN** 所有 key 为 `access_token`、`refresh_token` 的值通过 AES-256 GCM 加密后写入 SharedPreferences 文件

#### Scenario: Token 读取自动解密
- **WHEN** `BaiduAuthService.loadAuthInfo()` 读取认证信息
- **THEN** `EncryptedSharedPreferences` 自动解密，返回明文 token 到内存

#### Scenario: 明文 SharedPreferences 不再被密钥使用
- **WHEN** 旧 Java 版曾用明文 `baidu_auth` SharedPreferences 存储 token
- **THEN** Kotlin 版 SHALL NOT 创建或写入名为 `baidu_auth` 的非加密 SharedPreferences；如果检测到旧版数据，执行一次性迁移后删除旧文件

### Requirement: 一次性迁移旧明文 Token
系统 SHALL 在首次启动时检测旧 Java 版 `baidu_auth` SharedPreferences 中的明文 token，并将其迁移到 EncryptedSharedPreferences。

#### Scenario: 迁移已存在旧 Token
- **WHEN** 应用首次启动且检测到旧 `baidu_auth` SharedPreferences 中存在 `access_token`
- **THEN** 读取旧 token → 写入 EncryptedSharedPreferences → 删除旧 `baidu_auth` SharedPreferences 文件 → 打印迁移日志

#### Scenario: 无旧 Token 时静默跳过
- **WHEN** 应用首次启动且不存在旧 `baidu_auth` SharedPreferences
- **THEN** 不抛出异常，正常进入登录流程

### Requirement: Master Key 自动生成
系统 SHALL 依赖 `MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)` 自动生成设备绑定的主密钥。

#### Scenario: 首次启动生成 Master Key
- **WHEN** 应用首次创建 `EncryptedSharedPreferences`
- **THEN** `MasterKeys.getOrCreate` 在 Android Keystore 内生成 AES-256 密钥，对用户透明
