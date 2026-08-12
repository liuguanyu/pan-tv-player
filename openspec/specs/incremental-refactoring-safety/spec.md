# incremental-refactoring-safety Specification

## Purpose
TBD - created by archiving change refactor-playback-architecture-in-increments. Update Purpose after archive.
## Requirements
### Requirement: 每个增量测试先行
每个播放架构重构增量 SHALL 在修改生产行为前建立对应表征测试或失败测试，并在同一增量内完成红、绿、重构循环。

#### Scenario: 开始迁移一个职责
- **WHEN** 开发者准备将某职责从 Activity 或 ViewModel 移入新组件
- **THEN** 对应行为测试已存在并能在旧实现或预期缺陷上给出确定结果

#### Scenario: 缺陷修复测试
- **WHEN** 增量修复图片暂停 BGM、快速选播标识或异步竞态等缺陷
- **THEN** 测试在修复前失败、修复后通过并作为永久回归用例保留

### Requirement: 每个增量独立可交付
每个重构增量 MUST 保持项目可编译、可测试、可构建 release，并且不得夹带无关功能或全文件格式化。

#### Scenario: 增量完成
- **WHEN** 一个任务组准备提交
- **THEN** 该提交仅包含一个明确职责及其测试，且可被单独 revert 而不回退其他已验证功能

#### Scenario: 新旧实现迁移
- **WHEN** 新边界开始替代旧生产路径
- **THEN** 同一时刻只有一条路径驱动实际播放、写库或 UI 状态，验证完成后删除旧路径

### Requirement: 自动化质量门禁
每个增量 SHALL 通过定向测试、完整 JVM 测试、androidTest 编译、v7a release 构建和 diff 格式检查。

#### Scenario: 普通重构增量验收
- **WHEN** 增量实现完成
- **THEN** `testV7aDebugUnitTest`、`compileV7aDebugAndroidTestKotlin`、`assembleV7aRelease` 和 `git diff --check` 全部成功

#### Scenario: 自动化测试失败
- **WHEN** 任一门禁命令失败
- **THEN** 该增量不得提交为完成状态，且必须修复由本增量引入的问题或明确回退

### Requirement: 分层测试覆盖
重构 SHALL 将纯决策放入 JVM 测试，将 Android/Room/Hilt 契约放入 Robolectric 或 androidTest，将 D-pad/Surface/Media3 生命周期保留为 instrumentation 与真机验证。

#### Scenario: 纯状态机或算法
- **WHEN** 被测组件不依赖 Android View、真实 Room 或 Media3
- **THEN** 使用 Fake/Stub 在 JVM 中覆盖成功、边界、失败、取消和并发场景

#### Scenario: Room迁移
- **WHEN** 验证 v4→v5 PlaybackHistory migration
- **THEN** 使用 `MigrationTestHelper` 从真实 v4 schema 升级并验证重复清理、唯一索引、数据保留和 Room schema validation

#### Scenario: 遥控器与Surface行为
- **WHEN** 增量修改快速选播焦点、按键分发、TextureView、播放器或生命周期
- **THEN** 除自动化测试外还必须执行 Android TV 模拟器或真机用例

### Requirement: 真机高风险门禁
涉及播放器、BGM、快速选播、生命周期、Surface 或百度网络策略的增量 MUST 在目标 Android TV 上完成核心回归后才能标记完成。

#### Scenario: Sony Android 9回归
- **WHEN** 高风险增量生成 release APK
- **THEN** 在 Sony/Amlogic Android 9 v7a 设备验证首个视频、图片/BGM、快速选播、设置返回、大文件启动和长时间播放

#### Scenario: 无可用真机
- **WHEN** 当前没有目标设备可执行验证
- **THEN** 任务状态必须明确标记为“自动化通过、真机待验”，不得宣称增量完全完成

### Requirement: 可追溯构建与回退
每个高风险增量 SHALL 记录 commit、release APK 路径和 SHA-256，以支持现场定位和精确回退。

#### Scenario: 真机发现回归
- **WHEN** 某增量 APK 在目标 TV 出现回归
- **THEN** 可根据记录定位对应 commit 并仅 revert 该增量

#### Scenario: Room版本已升级
- **WHEN** 回归发生在已安装 v5 数据库的设备
- **THEN** 系统不得通过降低数据库版本回退，必须以前向 migration 或应用逻辑补偿

### Requirement: 兼容性不变量
整个重构期间 SHALL 持续保护已知视频启动和大文件网络不变量。

#### Scenario: 视频首帧准备
- **WHEN** 视频 Surface 尚未就绪或媒体仍在缓冲
- **THEN** 生产实现不得将 TextureView 设置为 `GONE`

#### Scenario: 百度大文件播放
- **WHEN** 媒体通过百度 dlink 流式播放
- **THEN** 重构不得加入曾造成回归的固定连接/读取超时，也不得引入默认本地分片缓存

### Requirement: 完整重构验收
change 归档前 SHALL 完成职责收口、测试门禁和目标 TV 回归，而不能只以文件行数下降作为完成标准。

#### Scenario: Activity职责验收
- **WHEN** 重构全部任务完成
- **THEN** PlaybackActivity 不直接访问百度 API、不拼接 dlink、不直接构造具体播放器且不维护 BGM或快速选播领域状态机

#### Scenario: ViewModel职责验收
- **WHEN** 重构全部任务完成
- **THEN** PlaybackViewModel 通过可注入会话、导航、准备、历史和元数据边界编排页面状态，不包含 Room实体组装、URL拼接或播放器实现细节

#### Scenario: 完整质量验收
- **WHEN** change 准备归档
- **THEN** 完整自动化门禁通过，Sony Android 9 核心链路通过，视频首播和大文件启动相对基线无明显退化

