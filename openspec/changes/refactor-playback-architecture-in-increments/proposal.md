## Why

近期连续加入背景音乐、最近播放会话和快速选播后，`PlaybackActivity` 已同时承担 UI、生命周期、百度 API、播放器编排和焦点状态机，已出现图片暂停时 BGM 状态不一致、焦点与当前播放标识混淆等回归风险。需要在保持现有播放性能和用户行为的前提下，以测试先行、逐步可回退的方式完成播放模块职责重构，避免一次性重写造成电视端不可控回归。

## What Changes

- 先以表征测试锁定播放、最近播放、BGM、快速选播和 Room 迁移的现有正确行为，再修复已确认的状态缺陷。
- 建立统一的百度可播放 URL 解析边界，消除视频、图片与 BGM 的 dlink/token 处理重复。
- 将 BGM 底层播放器抽象为可注入接口，并将媒体类型、用户播放意图、生命周期和 BGM 选择的决策移入独立协调器。
- 将目录、数据库播放列表和最近播放统一构建为不可变 `PlaybackSession`；最近播放会话在进入页面时冻结顺序，播放期间只更新 Room。
- 将队列导航、媒体准备与并发控制、快速选播控制、历史记录写入分别提取为可独立测试的组件。
- 最终将 `PlaybackViewModel` 收口为页面状态与用例编排，将 `PlaybackActivity` 收口为 Android 生命周期、View 渲染、Surface 适配和遥控器意图入口。
- 为每个重构增量规定 TDD、定向测试、完整单测、androidTest 编译、release 构建和真机 TV 回归门禁；每个增量必须可独立提交和回退。
- 保持既有视频启动和大文件策略：TextureView 在缓冲时保持 `VISIBLE + alpha=0`，百度请求保持原 User-Agent/重定向策略，不重新加入自定义连接/读取超时，不引入完整文件或分片本地缓存。

## Capabilities

### New Capabilities

- `playback-orchestration`: 定义稳定播放会话、队列导航、媒体准备并发、BGM 状态机、快速选播语义及 UI/领域/数据层职责边界。
- `incremental-refactoring-safety`: 定义播放架构重构的测试先行、分步门禁、真机验收、提交粒度和回退保障。

### Modified Capabilities

_无。既有 `media3-playback` 的用户能力和网络策略保持不变；既有 `testing-strategy` 继续生效，本 change 仅增加针对播放重构的专项安全要求。_

## Impact

- **主要代码**：`ui/playback/PlaybackActivity.kt`、`PlaybackViewModel.kt`、快速选播 Adapter、播放器抽象、播放历史仓库及相关 Hilt Module。
- **新增边界**：`PlayableUrlResolver`、`BackgroundAudioPlayer`、`BackgroundMusicCoordinator`、`PlaybackSessionFactory`、`PlaybackQueueNavigator`、`MediaPreparationCoordinator`、`QuickSelectorController`、`PlaybackHistoryRecorder`，名称可在实现时按现有包结构微调但职责不得合并回 Activity。
- **测试**：扩展 JVM/Robolectric、Room migration、Hilt androidTest、播放页 D-pad instrumentation 与 Sony Android 9 真机回归。
- **数据库**：不新增用户数据语义；为已存在的 v4→v5 迁移补真实升级测试和 schema 导出。
- **兼容性**：不改变 Intent 入口、设置持久化键、最近播放上限、Room 数据保留策略、APK ABI 或百度 API 契约。
