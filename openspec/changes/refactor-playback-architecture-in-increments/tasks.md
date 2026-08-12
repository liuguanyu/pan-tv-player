## 1. 建立播放重构基线

- [x] 1.1 为最近播放补齐同一快照定位、目标失效、100 条边界和会话不重排的表征测试
- [x] 1.2 为快速选播 Adapter 补齐 selectAt、非法位置、stable ID、当前项高亮和缩略图局部更新测试
- [x] 1.3 为 BGM 目标动作建立纯 Kotlin 决策矩阵测试，覆盖媒体类型、用户播放意图、生命周期和配置组合
- [x] 1.4 为快速连续选择媒体补充旧请求晚到、取消和预加载不改当前项的并发测试
- [x] 1.5 运行完整基线门禁并记录测试数量、release APK SHA-256、首个视频和大文件真机基线

## 2. 修复已确认状态缺陷

- [x] 2.1 添加“图片暂停同步暂停 BGM、暂停状态返回前台不恢复”的失败测试
- [x] 2.2 修改 BGM 播放条件，使其同时受图片类型、isPlaying 和前台生命周期约束
- [x] 2.3 添加“焦点项与当前项不同但只有当前项显示底部蓝条”的失败测试
- [x] 2.4 修改快速选播视觉绑定，使完整边框只表示焦点、底部蓝条只表示当前播放
- [x] 2.5 添加“历史目标不存在时不播放其他项”的失败测试并改为同一历史快照定位
- [x] 2.6 添加“BGM 解析失败可重试且 CancellationException 透明传播”的失败测试并修复现有解析状态
- [ ] 2.7 运行自动化门禁并在目标 TV 验证图片暂停/恢复、视频切换、设置返回和快速选播双标识

## 3. 建立 Room Migration 安全网

- [x] 3.1 配置 Room schema export 并将现有 schema JSON 纳入版本控制
- [x] 3.2 使用 MigrationTestHelper 构造包含重复路径、相同时间和非重复记录的 v4 数据库
- [x] 3.3 验证 MIGRATION_4_5 保留最新记录、创建唯一索引并通过 Room schema validation
- [x] 3.4 补充空数据库、中文/空格/引号路径和迁移后重复插入测试
- [x] 3.5 运行 JVM、androidTest 编译、可用设备上的 migration test 和 release 构建门禁

## 4. 统一百度可播放 URL 解析

- [x] 4.1 为 token 为空、已有 query、已有 token、详情为空、dlink 为空、网络失败和协程取消编写 Resolver 单测
- [x] 4.2 定义可注入 PlayableUrlResolver 并实现现有 dlink/token 解析语义
- [x] 4.3 将 PlaybackViewModel 的 URL 解析迁移到 Resolver，保持 dlink 预加载和缓存行为
- [x] 4.4 将 BGM 的 URL 解析迁移到 Resolver并删除 Activity 中重复拼接逻辑
- [x] 4.5 验证 Resolver 和播放器未加入固定连接/读取超时、完整缓存或分片缓存
- [x] 4.6 运行自动化门禁并在目标 TV 比较首个视频和大文件启动基线

## 5. 抽象背景音频播放器

- [x] 5.1 定义 Fake BackgroundAudioPlayer 并编写 play/pause/stop/release 调用序列测试
- [x] 5.2 定义 BackgroundAudioPlayer 接口并将现有 Media3 实现迁移为生产实现
- [x] 5.3 为相同 URL 恢复不重复 prepare、URL 变化替换 source、循环和幂等 release 添加测试
- [x] 5.4 配置合适的 Media3 AudioAttributes 与 audio focus，并增加可验证的配置测试
- [x] 5.5 通过 Hilt或工厂注入接口，移除 PlaybackActivity 对具体 BGM播放器的直接构造
- [x] 5.6 运行自动化门禁并在目标 TV 验证 BGM循环、暂停恢复和外部音频焦点（自动化门禁通过；TV 真机验证待用户确认）

## 6. 提取 BackgroundMusicCoordinator

- [ ] 6.1 为 Disabled、Resolving、Ready、Failed sealed state 编写状态转换测试
- [ ] 6.2 为图片播放/暂停、视频切换、前后台和用户暂停意图编写 Fake Player 测试矩阵
- [ ] 6.3 为 A 切 B、解析期间关闭、旧结果晚到、失败重试和取消传播编写并发测试
- [ ] 6.4 实现 BackgroundMusicCoordinator并接入 Resolver、Settings flow 和 BackgroundAudioPlayer
- [ ] 6.5 将 Activity 的 BGM字段和 syncBackgroundMusic 逻辑迁移到 Coordinator 委托
- [ ] 6.6 删除旧 BGM状态路径并确认生产环境仅由 Coordinator 驱动播放器
- [ ] 6.7 运行自动化门禁并在目标 TV 验证完整 BGM 生命周期矩阵

## 7. 提取 PlaybackSessionFactory

- [ ] 7.1 定义 PlaybackSession 与 PlaybackSource，并为不可变 items、title 和 startIndex 编写模型测试
- [ ] 7.2 为目录缓存、数据库播放列表和最近播放三种来源编写 SessionFactory 单测
- [ ] 7.3 覆盖最近 100 条、目标不在快照、fsId无效、空历史和中文路径场景
- [ ] 7.4 实现 PlaybackSessionFactory，确保最近播放只查询一个一致快照
- [ ] 7.5 将三个 initialize 入口迁移为获取 Session 后统一 applySession
- [ ] 7.6 删除 ViewModel 内 Room实体和 PlaylistItem 到播放会话的组装逻辑
- [ ] 7.7 运行自动化门禁并在目标 TV 验证三个入口及最近播放快速选播内容

## 8. 提取 PlaybackQueueNavigator

- [ ] 8.1 为顺序、倒序、单曲的中间项和首尾循环编写纯 Kotlin 参数化测试
- [ ] 8.2 为随机模式注入固定随机源并测试单元素、不立即重复和一轮不重复
- [ ] 8.3 为列表为空、索引越界、列表变化和不可变 Session 编写边界测试
- [ ] 8.4 实现 PlaybackQueueNavigator并迁移 next、previous、initialIndex 计算
- [ ] 8.5 从 PlaybackViewModel 删除旧导航和随机队列算法
- [ ] 8.6 运行自动化门禁并在目标 TV 验证四种播放模式

## 9. 提取媒体准备与并发控制

- [ ] 9.1 为 A慢B快、A晚到失败、切换取消和 retry 当前项编写 generation 并发测试
- [ ] 9.2 为预加载去重、预加载复用和预加载失败不影响当前媒体编写测试
- [ ] 9.3 实现 MediaPreparationCoordinator并为每次切换分配单调 generation
- [ ] 9.4 将 URL解析、当前准备和下一项预加载迁移到 Coordinator
- [ ] 9.5 确保只有当前 generation 可更新状态、发出播放事件或显示错误
- [ ] 9.6 删除 ViewModel 中旧 prepare/preload job 与 Mutex 编排路径
- [ ] 9.7 运行自动化门禁并在目标 TV 快速连续切换图片/视频，比较首播与大文件性能基线

## 10. 提取快速选播控制器

- [ ] 10.1 为数据变化重绑、当前项局部更新、确认先关闭后切换和非法 position 编写 Controller 测试
- [ ] 10.2 为 ViewHolder 未布局、有限焦点重试、列表隐藏和缩略图晚到编写 Robolectric 测试
- [ ] 10.3 明确并测试“用户主动浏览时自动切换只更新蓝条、不抢焦点；重新打开聚焦当前项”的策略
- [ ] 10.4 实现 QuickSelectorController并迁移 configure、sync、show、hide、focus 和确认逻辑
- [ ] 10.5 将缩略图获取移入可测试数据边界，Activity 不再直接调用 FileRepository
- [ ] 10.6 删除 Activity 中 quickSelectorCurrentKey、quickSelectorDataKey 和旧控制路径
- [ ] 10.7 运行自动化门禁并在目标 TV 验证翻页、异步缩略图、自动切换和遥控器确认

## 11. 提取 PlaybackHistoryRecorder

- [ ] 11.1 为路径回退、媒体类型、来源上下文和封面候选优先级编写映射测试
- [ ] 11.2 为普通 Room异常不阻断播放、CancellationException传播和相同路径去重编写测试
- [ ] 11.3 实现 PlaybackHistoryRecorder并迁移 PlaybackHistory 构建与写入降级策略
- [ ] 11.4 将 ViewModel 历史写入替换为 Recorder 委托并删除旧映射逻辑
- [ ] 11.5 运行自动化门禁并验证最近播放顺序、100 条裁剪和写库失败降级

## 12. 收缩 PlaybackViewModel

- [ ] 12.1 使用 Fake SessionFactory、Navigator、Preparation、History和Metadata边界重写 ViewModel 状态转换测试
- [ ] 12.2 覆盖 Idle→Loading→Ready/Failed、A→B切换、retry、contentReady和清理取消
- [ ] 12.3 提取媒体元数据协调和图片自动切换计时，使其可使用 TestDispatcher 独立测试
- [ ] 12.4 将 PlaybackViewModel 收口为 PlaybackUiState、用户意图和协调器编排
- [ ] 12.5 删除 Room实体组装、URL拼接、队列算法、播放器实现和独立计时细节
- [ ] 12.6 运行完整自动化门禁并比较 ViewModel 公共行为与阶段 1 基线

## 13. 收缩 PlaybackActivity

- [ ] 13.1 为 Activity 按键到用户意图、面板关闭优先级和设置返回状态编写 instrumentation 测试
- [ ] 13.2 为 TextureView `VISIBLE + alpha=0` 到成功后 `alpha=1` 编写可执行 UI/控制器测试
- [ ] 13.3 提取 MediaPresentationController，迁移图片/视频 View、背景和最后一帧呈现
- [ ] 13.4 提取 PlaybackControlsController，迁移控制栏、进度和自动隐藏
- [ ] 13.5 提取 PlaybackKeyDispatcher，迁移遥控器按键状态机并保持系统事件转发语义
- [ ] 13.6 移除 Activity 对百度 API、Repository、具体播放器、BGM状态机和快速选播状态机的直接依赖
- [ ] 13.7 运行完整自动化门禁并在目标 TV 验证 Surface、控制栏、设置、信息面板和返回键链路

## 14. 完整验收与归档准备

- [ ] 14.1 运行 `testV7aDebugUnitTest`、`compileV7aDebugAndroidTestKotlin`、可用设备的 connected tests、`assembleV7aRelease` 和 `git diff --check`
- [ ] 14.2 在 Sony/Amlogic Android 9 v7a TV 验证首个视频、图片+BGM、视频切换、最近播放、快速选播和设置实时生效
- [ ] 14.3 执行大文件启动速度对比、网络中断恢复、BGM循环一轮以上和长时间播放测试
- [ ] 14.4 核对 Activity/ViewModel 职责验收条件，而非仅按文件行数判断完成
- [ ] 14.5 记录最终 commit、release APK 路径、SHA-256、自动化结果和真机测试结果
- [ ] 14.6 清理临时 feature flag、旧实现、无用测试辅助和过期注释
- [ ] 14.7 使用 OpenSpec validate 验证 change，完成评审后再归档并合并 capability specs
