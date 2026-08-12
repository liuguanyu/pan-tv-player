## Context

播放模块当前以 `PlaybackActivity`（约 960 行）和 `PlaybackViewModel`（约 619 行）承载大部分功能。近期 BGM、最近播放会话和快速选播共享 `PlaybackUiState` 与 Activity 生命周期，但各自又有独立网络、焦点和播放器状态，导致实现层耦合：Activity 直接解析 BGM dlink、请求快速选播缩略图、操作视频与音频播放器；ViewModel 同时负责会话来源、URL 解析、队列算法、预加载、历史写入和元数据提取。

本项目运行在 Android TV，尤其需要兼容 Sony/Amlogic Android 9 低端设备。视频 Surface、遥控器焦点、Media3 生命周期和大文件网络行为不能仅靠 JVM 测试证明，因此重构必须以小批次、自动化门禁加真机门禁推进。

当前不可回归约束：

- 视频 Surface 在准备阶段保持 `VISIBLE`，仅使用 `alpha=0f` 隐藏，成功后恢复 `alpha=1f`。
- 百度 HTTP 使用 `User-Agent: pan.baidu.com` 并允许跨协议重定向。
- 不设置曾导致视频回归的 15 秒连接超时和 60 秒读取超时。
- 不增加完整文件或分片本地缓存。
- 最近播放最多 100 条；播放页使用进入时的不可变会话顺序，播放成功只更新 Room。

## Goals / Non-Goals

**Goals:**

- 让 Activity 只承担 Android 生命周期、View/Surface 渲染和按键意图转发。
- 让 ViewModel 只承担页面状态和领域用例编排。
- 将 URL 解析、BGM、会话构建、队列导航、媒体准备、快速选播、历史记录拆成正交且可注入测试的边界。
- 消除旧媒体准备请求覆盖新选择、BGM 状态组合不一致、快速选播焦点与当前项混淆等风险。
- 每一步先有失败测试或表征测试，再迁移实现；每个提交可独立构建、安装、回退。
- 为 Room v4→v5 建立真实 Migration 测试，并将 schema 纳入版本控制。

**Non-Goals:**

- 不增加新的用户可见播放功能。
- 不改变百度 OAuth/API 契约、dlink Range 能力或大文件策略。
- 不替换 XML/ViewBinding 为 Compose。
- 不在本 change 中接入新的 FFmpeg 产物或替换 Media3。
- 不为了缩短文件而机械拆分无业务边界的工具类。
- 不追求一次提交完成全部重构。

## Decisions

### 1. 采用绞杀式增量重构，不做播放模块重写

每次先建立新边界及测试，只迁移一个调用链；验证后删除该链路的旧实现。生产路径不得长期同时启用两套实现。

**替代方案：**新建 `PlaybackActivityV2` 并一次性切换。拒绝原因是 Surface、遥控器和低端 TV 行为难以完整自动化，双实现长期漂移且回退粒度过大。

### 2. 先修正确性，再锁定目标行为

阶段 1 先修图片暂停时 BGM 不暂停、快速选播底部蓝条同时表示焦点和当前项、历史目标失效时回退播放其他项、BGM 解析失败不可恢复等已确认问题。测试锁定的是修正后的产品语义，而不是保留错误行为。

### 3. 用不可变 PlaybackSession 隔离数据来源与播放会话

目录、数据库播放列表和最近播放统一转换成 `PlaybackSession`。Session 包含来源、不可变 items、起始索引和展示名称。最近播放从同一 Room 快照定位点击项；创建 Session 后，数据库热流变化不得重排当前会话。

**替代方案：**继续直接把 `PlaybackHistory` 转成 `PlaybackUiState.files`。拒绝原因是持久化排序/容量策略会隐式改变队列和快速选播语义。

### 4. 使用统一 PlayableUrlResolver

Resolver 统一获取 token、复用/查询 dlink、校验链接、追加 access token。视频、图片和 BGM 共用同一策略；HTTP DataSource 的请求头与播放器仍由各播放器实现配置。

Resolver 必须透明传播 `CancellationException`，不得引入自定义网络超时或本地分片缓存。

### 5. BGM 分为底层播放器与领域协调器

`BackgroundAudioPlayer` 只封装 Media3 的 source、play、pause、stop、release、循环和音频焦点；`BackgroundMusicCoordinator` 根据选择、解析状态、媒体类型、用户播放意图和生命周期决定目标动作。

Coordinator 使用 sealed state 表达 Disabled/Resolving/Ready/Failed，避免多个布尔字段形成非法组合。Activity 只转发生命周期与 `PlaybackUiState`。

### 6. 媒体准备使用 generation 防止旧请求覆盖新请求

每次切换媒体生成单调递增 generation。URL 解析、预加载和元数据提取完成后，只有仍匹配当前 generation 的结果可更新状态或发出播放事件。切换、重试和 ViewModel 清理会取消旧任务；取消不转为用户错误。

### 7. 队列导航使用纯 Kotlin 组件

顺序、倒序、单曲和随机模式的 index 计算移入无 Android/Room/网络依赖的组件。随机算法注入 `Random` 或随机源，使测试可重复；Session items 不由导航器修改。

### 8. 快速选播分离三种状态

快速选播明确区分：

- 数据顺序：来自当前 Session，打开期间不因最近播放写库而改变；
- 当前播放项：唯一的底部蓝条；
- 遥控器焦点项：完整 `card_selector` 边框，可与当前项不同。

Controller 负责配置、显示隐藏、滚动、焦点恢复和确认；缩略图加载由可测试的数据边界提供。确认必须先关闭列表，再发出切换意图。

### 9. 历史写入使用独立 Recorder

Recorder 负责路径规范化、媒体类型、来源、封面候选、Room upsert 和非关键失败降级。普通数据库异常只记录日志且不影响播放；`CancellationException` 必须传播。

数据库唯一身份的长期目标是 `fsId > 0` 优先、路径回退；本 change 在没有单独 migration 设计前不改变已发布 v5 的 `folderPath` 唯一约束。

### 10. 测试金字塔与门禁

- 纯 Kotlin 决策、状态机、映射和并发使用 JVM 测试。
- SharedPreferences/View/Adapter 使用 Robolectric。
- Room schema/migration/Hilt 使用 androidTest。
- D-pad、Surface、Media3 生命周期和低端 TV 性能使用 instrumentation 加真机测试。

每个增量必须依次通过定向测试、完整 `testV7aDebugUnitTest`、`compileV7aDebugAndroidTestKotlin`、`assembleV7aRelease` 和 `git diff --check`。涉及播放器/焦点/生命周期/网络策略的增量还必须真机验收。

### 11. 提交与回退粒度

每个任务组原则上形成一个独立提交；较复杂步骤可拆为 `test`、`refactor introduce`、`refactor migrate`、`cleanup`，但每个提交都必须可构建。每个高风险步骤保留对应 release APK、commit 与 SHA-256；失败时直接 revert 该增量，不回退已经验证的其他功能。

## Risks / Trade-offs

- **边界数量短期增加、代码总量上升** → 只提取具有独立状态或外部依赖的职责；完成迁移后立即删除旧路径。
- **新旧路径迁移期间行为漂移** → 表征测试先行，单次只切换一个调用点，不长期双写或双播。
- **Activity instrumentation 在 TV 模拟器不稳定** → 纯决策下沉到 JVM；UI 测试只覆盖焦点/生命周期契约，并以 Sony Android 9 真机作为高风险门禁。
- **媒体准备 generation 改动可能影响首播速度** → 建立 A/B 基线，保留 dlink 预加载，不改变 HTTP timeout、Surface 可见性和缓存策略。
- **Room schema export 改变构建产物** → schema JSON 纳入版本控制，MigrationTestHelper 验证 v4→v5，禁止 destructive migration。
- **重构周期中继续加入新功能导致边界反复变化** → 除 P0 缺陷外，播放页新功能先落 OpenSpec，再决定进入现有边界或排在重构之后。

## Migration Plan

1. 建立表征测试与 fake 基础设施，不改生产行为。
2. 修复 BGM暂停、快速选播视觉语义、历史目标失效和 BGM失败恢复。
3. 补 Room v4→v5 schema 与 Migration 测试。
4. 引入并迁移统一 URL Resolver。
5. 注入 BackgroundAudioPlayer，再提取 BackgroundMusicCoordinator。
6. 提取 PlaybackSessionFactory 与 PlaybackQueueNavigator。
7. 提取 MediaPreparationCoordinator，并用 generation 解决竞态。
8. 提取 QuickSelectorController 与 PlaybackHistoryRecorder。
9. 收缩 PlaybackViewModel，再收缩 PlaybackActivity/Presentation/Controls。
10. 执行完整自动化、release、Sony Android 9 真机长稳与大文件回归；完成后归档 change。

每一步部署前记录 commit、APK 路径和 SHA-256。若真机门禁失败，仅回退该步；Room migration 步骤不得降级数据库版本，修复必须以前向 migration 或应用逻辑补偿完成。

## Open Questions

- 自动播放切换时，快速选播已打开且用户主动移动过焦点，焦点应跟随当前播放还是保留浏览位置？设计默认“蓝条更新、用户焦点不被抢；重新打开时聚焦当前项”，需真机验收确认。
- BGM audio focus 采用完全暂停还是 duck，需要根据 TV 与外部音源产品体验确认；默认使用 Media3 标准媒体音频焦点并在失去焦点时暂停。
- `PlaybackActivity` 最终行数不设硬门禁；以不直接访问百度 API、不直接构造播放器、不承载领域状态机作为验收条件。
