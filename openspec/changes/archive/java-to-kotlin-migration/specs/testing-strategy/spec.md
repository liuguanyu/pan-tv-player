## ADDED Requirements

### Requirement: 三层测试体系
系统 SHALL 建立严格的三层测试金字塔：单元测试、集成测试、UI 测试。每层有明确的覆盖目标和不纳入覆盖的类型。

#### Scenario: 测试金字塔比例
- **WHEN** 项目全部测试编写完毕
- **THEN** 单元测试占 ≥ 60%、集成测试占 ≥ 25%、UI 测试占 ≤ 15%

### Requirement: 单元测试 — ViewModel
每个 ViewModel SHALL 有对应的单元测试类（`*ViewModelTest.kt`），测试所有公开方法和状态变化。

#### Scenario: PlaybackViewModel 播放模式切换
- **WHEN** 调用 `viewModel.setPlayMode(PlayMode.RANDOM)`
- **THEN** `viewModel.playMode` StateFlow 发出 `PlayMode.RANDOM`，且 `getNextIndex()` 按随机序列返回合法索引

#### Scenario: FileBrowserViewModel 递归加载
- **WHEN** 调用 `viewModel.setRecursive(true)` 后加载目录
- **THEN** `FileRepository.fetchPagesWithLimit` 被调用，且 ViewModel 的 `files` StateFlow 发出包含多层级文件的列表

#### Scenario: ViewModel 协程取消
- **WHEN** `viewModelScope` 被取消（模拟 ViewModel.onCleared()）
- **THEN** 所有 `launch` 的协程被取消，且无资源泄漏（通过 `TestScope` 验证）

### Requirement: 单元测试 — Repository
每个 Repository SHALL 有对应的单元测试类，使用 Fake/Stub 替代真实网络和数据库。

#### Scenario: FileRepository.getFileList 正常响应
- **WHEN** Mock `BaiduPanService` 返回合法 `FileListResponse`
- **THEN** `getFileList()` 返回 LiveData 中包含正确数量过滤后文件的列表

#### Scenario: FileRepository 分页上限
- **WHEN** Mock 目录包含 6000 个文件（超过 5 页上限）
- **THEN** `getFileList()` 仅返回最多 5000 个文件，日志输出 `"目录包含超过5000个文件"` 警告

#### Scenario: PlaylistRepository 刷新播放列表
- **WHEN** 调用 `refreshPlaylist(playlistId, accessToken)` 且源目录文件有变化
- **THEN** 旧 `PlaylistItem` 被事务删除，新项被插入，统计数据更新

### Requirement: 单元测试 — 工具类与策略模式
所有工具类（`LocationUtils`、`PreferenceUtils`、`PlaylistCache`）和策略模式（`ImageEffectFactory`、`ImageBackgroundFactory`、`GeocodingFactory`）SHALL 有 100% 分支覆盖的单测。

#### Scenario: ImageEffectFactory.createActualEffectStrategy(RANDOM)
- **WHEN** 传入 `ImageEffect.RANDOM`
- **THEN** 返回的策略对象不是 RANDOM 本身（已通过 `getActualEffect()` 转换），而是 8 种具体特效之一

#### Scenario: GeocodingFactory 策略回退
- **WHEN** 首选策略 `AmapGeocodingStrategy` 返回 null
- **THEN** 自动尝试 `AndroidGeocoderStrategy`，再尝试 `NominatimGeocodingStrategy`

#### Scenario: LocationUtils 图片 EXIF 有 GPS 数据
- **WHEN** 图片文件头 128KB（`Range: bytes=0-131071` 下载）包含完整 EXIF GPS 标签
- **THEN** `getLocationFromImage()` 返回逆地理编码后的地点名称（如"北京市朝阳区"）

#### Scenario: LocationUtils 图片 EXIF 无 GPS 数据
- **WHEN** 图片文件头 128KB 不包含 EXIF GPS 标签
- **THEN** `getLocationFromImage()` 返回 null，不抛异常

#### Scenario: LocationUtils 视频 MediaMetadataRetriever 返回 GPS
- **WHEN** 视频的 QuickTime `©xyz` atom 包含 ISO-6709 格式 GPS 数据
- **THEN** `MediaMetadataRetriever.extractMetadata(METADATA_KEY_LOCATION)` 返回有效坐标，解析后返回地点名称

#### Scenario: LocationUtils 视频 GPS 提取超时
- **WHEN** `MediaMetadataRetriever.setDataSource()` 对网络流阻塞超过 8 秒
- **THEN** `withTimeout(8000)` 触发 `TimeoutCancellationException`，返回 null，不阻塞主流程

#### Scenario: 不再检查 ACCESS_FINE_LOCATION 权限
- **WHEN** `getLocationForFile()` 被调用
- **THEN** SHALL NOT 检查 `ACCESS_FINE_LOCATION` 或 `ACCESS_COARSE_LOCATION` 权限（ExifInterface / MediaMetadataRetriever / Geocoder 均不需要设备位置权限）

### Requirement: 集成测试 — Room DAO
每个 Room DAO SHALL 有对应的 Android 集成测试（`*DaoTest.kt`），在内存数据库上运行。

#### Scenario: PlaylistDao CRUD
- **WHEN** 创建 Playlist → 插入 → 查询 → 更新 → 删除
- **THEN** 每个操作后查询结果与预期一致，且 `getAllPlaylists()` 的 Flow 正确发出更新

#### Scenario: PlaylistItemDao 批量插入
- **WHEN** 事务插入 50 个 `PlaylistItem`
- **THEN** 重复 `fsId` 的项按 `OnConflictStrategy.REPLACE` 处理，最终 count 等于预期

#### Scenario: Migration 测试
- **WHEN** 提供 v1 schema 的数据库文件
- **THEN** `MigrationTestHelper` 执行 v1→v2 迁移后 schema 正确，数据不丢失

### Requirement: 集成测试 — Retrofit API
关键 API 端点 SHALL 有集成测试，使用 MockWebServer 模拟百度网盘响应。

#### Scenario: OAuth device_code 流程
- **WHEN** MockWebServer 返回 `{ "device_code": "xxx", "user_code": "yyy", "verification_url": "..." }`
- **THEN** `BaiduAuthService.getDeviceCode()` 成功解析 `DeviceCodeResponse`

#### Scenario: API 401 Token 过期
- **WHEN** MockWebServer 返回 HTTP 401
- **THEN** `BaiduAuthService` 触发 token 刷新逻辑，重试原请求，用户无感知

### Requirement: 集成测试 — 播放器降级链
播放器降级逻辑 SHALL 通过集成测试验证，使用 Fake `Media3VideoPlayerEngine` 模拟硬解失败。

#### Scenario: 硬解失败 → FFmpeg 软解接管
- **WHEN** `Media3VideoPlayerEngine.play()` 抛出 `DecoderNotSupported`
- **THEN** 系统自动以 FFmpeg 软解重新播放同一 URL，且不丢当前播放位置

#### Scenario: 硬解 + 软解均失败 → 跳过
- **WHEN** FFmpeg 软解也失败
- **THEN** 返回 `PlaybackResult.Error`，`PlaybackViewModel` 调用 `playNext()`，Toast 提示用户

### Requirement: UI 测试 — 遥控器焦点导航
关键页面 SHALL 有 UI 测试验证 D-pad 焦点链的完整性。

#### Scenario: 主界面焦点链
- **WHEN** 主界面显示且无播放列表
- **THEN** 默认焦点在「浏览文件」按钮上；按 DOWN → 焦点到「创建播放列表」；按 UP → 焦点回到「浏览文件」

#### Scenario: 播放页控制栏焦点
- **WHEN** 播放页控制栏可见
- **THEN** 默认焦点在播放/暂停按钮；按 LEFT → 前一首；按 RIGHT → 下一首

### Requirement: Hilt 测试支持
项目 SHALL 使用 `@HiltAndroidTest` + `HiltTestRunner` 为集成测试提供依赖注入。

#### Scenario: Hilt 集成测试启动
- **WHEN** 运行 `@HiltAndroidTest` 标注的测试
- **THEN** `HiltTestRunner` 加载 `TestDatabaseModule`（内存数据库）替代真实 `DatabaseModule`，测试间数据库隔离

### Requirement: CI 门禁
Pull Request 提交 SHALL 触发 CI 流水线，自动运行全部测试。

#### Scenario: PR 提交触发测试
- **WHEN** 向 `main` 分支发起 Pull Request
- **THEN** GitHub Actions 执行 `./gradlew test` + `./gradlew connectedCheck`（Android TV 模拟器），全部通过后允许合并

#### Scenario: 覆盖率不达标阻断合并
- **WHEN** JaCoCo 报告显示行覆盖率 < 80%
- **THEN** CI 标记为 failure，PR 不可合并

### Requirement: 测试先行（TDD）
每个功能模块 SHALL 先写测试（红），再写实现（绿），最后重构。`tasks.md` 中实现任务的依赖项 SHALL 包含对应测试任务。

#### Scenario: 实现前检查测试是否存在
- **WHEN** 开始实现 `FileRepository.getFileList()`
- **THEN** `FileRepositoryTest.kt` 已存在且至少包含一个测试用例
