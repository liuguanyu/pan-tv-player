# playback-orchestration Specification

## Purpose
TBD - created by archiving change refactor-playback-architecture-in-increments. Update Purpose after archive.
## Requirements
### Requirement: 不可变播放会话
系统 SHALL 将目录、数据库播放列表和最近播放构建为显式播放会话；会话创建后，其项目顺序 MUST 在当前播放页生命周期内保持不变。

#### Scenario: 最近播放写库不重排当前会话
- **WHEN** 用户从最近播放进入播放页并成功播放另一个会话项目
- **THEN** 系统更新 Room 中该文件的最近时间，但当前会话和快速选播项目顺序保持进入页面时的快照

#### Scenario: 使用同一历史快照定位点击项
- **WHEN** 用户点击某条最近播放记录进入播放页
- **THEN** 系统 MUST 在用于构建会话的同一份最近记录快照中按 historyId 定位该项

#### Scenario: 点击目标已失效
- **WHEN** 被点击的历史记录不存在、被过滤或不在最近 100 条快照中
- **THEN** 系统显示明确错误且 MUST NOT 回退播放另一条记录

### Requirement: 正交播放队列导航
系统 SHALL 以独立、无 Android/Room/网络依赖的队列导航逻辑处理顺序、倒序、单曲和随机播放，且导航 MUST NOT 修改会话项目列表。

#### Scenario: 顺序播放边界循环
- **WHEN** 当前项为顺序会话最后一项且请求下一项
- **THEN** 导航返回第一项索引且会话顺序保持不变

#### Scenario: 随机播放可重复测试
- **WHEN** 测试注入固定随机源并执行随机导航
- **THEN** 导航结果可重复且多项目会话不会立即返回当前索引

### Requirement: 统一可播放 URL 解析
视频、图片和 BGM SHALL 复用同一百度可播放 URL 解析策略，统一处理 access token、文件详情、dlink 校验和 query 参数拼接。

#### Scenario: dlink 已包含 token
- **WHEN** Resolver 收到已包含 `access_token` 的 dlink
- **THEN** 返回 URL 不重复追加 token

#### Scenario: dlink 解析失败
- **WHEN** token 为空、文件详情为空或 dlink 缺失
- **THEN** Resolver 返回可识别失败且不启动播放器

#### Scenario: 解析任务被取消
- **WHEN** 用户切换媒体或页面销毁导致解析协程取消
- **THEN** `CancellationException` 透明传播且 MUST NOT 转换为用户播放错误

#### Scenario: 保持百度大文件网络策略
- **WHEN** Resolver 或播放器创建百度媒体请求
- **THEN** 请求使用 `User-Agent: pan.baidu.com` 并允许跨协议重定向，且 MUST NOT 设置 15 秒连接超时、60 秒读取超时或完整/分片本地缓存

### Requirement: BGM 独立状态机
系统 SHALL 使用独立 BGM 协调器，根据 BGM选择、解析状态、当前媒体类型、用户播放意图和 Activity 生命周期决定背景音频动作。

#### Scenario: 播放中的图片启动 BGM
- **WHEN** 已配置可用 BGM、当前媒体为图片、用户播放状态为播放且 Activity 在前台
- **THEN** BGM 无限循环播放

#### Scenario: 图片暂停时暂停 BGM
- **WHEN** 当前媒体为图片且用户将播放状态切换为暂停
- **THEN** BGM 同步暂停且 Activity 状态刷新不得重新启动它

#### Scenario: 视频期间暂停 BGM
- **WHEN** 当前媒体从图片切换为视频
- **THEN** BGM 暂停；返回播放中的图片时从现有音频位置恢复

#### Scenario: 生命周期不覆盖用户暂停意图
- **WHEN** 图片已由用户暂停，Activity 进入后台后再回到前台
- **THEN** BGM 保持暂停

#### Scenario: BGM选择变化
- **WHEN** 用户从 BGM A 切换到 BGM B
- **THEN** 系统取消 A 的未完成解析、停止 A，并仅允许 B 的解析结果驱动播放器

#### Scenario: BGM解析失败可恢复
- **WHEN** BGM dlink 首次解析失败后重新触发解析或用户重新进入播放页
- **THEN** 系统允许重试且失败状态不会永久阻断该选择

### Requirement: 可替换背景音频播放器
BGM底层播放器 SHALL 通过可注入接口提供播放、暂停、停止和释放能力，领域协调器 MUST NOT 直接构造 Media3 ExoPlayer。

#### Scenario: 使用 Fake 验证播放器调用
- **WHEN** 单元测试向 BGM协调器注入 Fake 背景音频播放器
- **THEN** 测试可确定性验证 play、pause、stop、release 的调用顺序

#### Scenario: 相同音源恢复
- **WHEN** 暂停后恢复同一 BGM URL
- **THEN** 播放器继续播放且不重复创建或 prepare 相同 MediaSource

### Requirement: 最新媒体准备结果唯一生效
系统 SHALL 为每次媒体切换分配单调 generation 或等效标识，仅允许当前请求结果更新 UI 或发出播放器事件。

#### Scenario: 旧请求晚于新请求完成
- **WHEN** A 的准备请求较慢，用户切换到 B 且 B 先完成
- **THEN** 系统只播放 B，A 的晚到结果不得覆盖状态或发出播放事件

#### Scenario: 旧请求晚到失败
- **WHEN** 用户已切换到 B 后 A 的准备请求失败
- **THEN** 系统不得显示 A 的错误或中断 B

#### Scenario: 预加载不改变当前媒体
- **WHEN** 下一项预加载完成
- **THEN** 结果只进入可复用缓存，不改变 currentFile、currentIndex 或当前播放器

### Requirement: 快速选播状态正交
快速选播 SHALL 分别维护会话数据顺序、当前播放项和遥控器焦点项，并为每种状态提供唯一视觉和交互语义。

#### Scenario: 当前项与焦点项不同
- **WHEN** 快速选播打开且用户将焦点移到非当前播放项
- **THEN** 当前播放项仅显示底部蓝条，焦点项仅显示完整焦点边框，页面不得出现两个当前播放蓝条

#### Scenario: 确认焦点项
- **WHEN** 用户在快速选播焦点项上按遥控器确认键
- **THEN** 系统先收起快速选播，再播放该焦点项

#### Scenario: 缩略图异步返回
- **WHEN** 用户滚动或翻页期间缩略图异步加载完成
- **THEN** 项目身份、焦点身份、当前播放标识和会话顺序保持正确

#### Scenario: 最近播放写库期间选择器保持稳定
- **WHEN** 最近播放会话中的媒体成功播放并更新数据库
- **THEN** 已打开的快速选播不重新排序、不丢失焦点且只更新当前播放标识

### Requirement: 非关键历史持久化不得中断播放
系统 SHALL 将播放历史记录视为附属持久化；普通 Room 写入失败不得中断已经成功的媒体播放。

#### Scenario: Room写入失败
- **WHEN** 媒体已准备成功但最近播放写库抛出普通数据库异常
- **THEN** 媒体继续播放、异常被记录且 UI 不显示阻断性错误

#### Scenario: 历史写入协程取消
- **WHEN** 历史写入因所属协程取消而抛出 `CancellationException`
- **THEN** 异常继续传播且不被作为普通数据库失败吞掉

### Requirement: 播放界面职责边界
最终播放界面 SHALL 仅承担 Android 生命周期、View/Surface 渲染、遥控器意图转发和对协调器的委托，不得直接访问百度 API、拼接 dlink 或直接构造具体播放器。

#### Scenario: UI层可替换外部依赖
- **WHEN** 测试启动播放界面或其控制器
- **THEN** 百度 URL解析器、视频播放器、背景音频播放器和数据仓库均可通过接口或 Hilt 测试绑定替换

#### Scenario: 保持视频Surface启动策略
- **WHEN** 视频正在获取 Surface 或缓冲首帧
- **THEN** TextureView 保持 `VISIBLE` 且使用 `alpha=0f` 隐藏，播放成功后设置 `alpha=1f`

