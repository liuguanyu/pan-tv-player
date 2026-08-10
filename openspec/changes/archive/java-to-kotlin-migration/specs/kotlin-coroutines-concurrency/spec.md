## ADDED Requirements

### Requirement: 协程管理异步操作
系统 SHALL 使用 Kotlin Coroutines (`kotlinx.coroutines`) 管理所有异步操作，替代 `Thread` + `Handler` + `Thread.sleep` 轮询模式。

#### Scenario: 回调转挂起函数
- **WHEN** 调用百度网盘 API 获取文件列表
- **THEN** `FileRepository` 方法为 `suspend` 函数，使用 `suspendCancellableCoroutine` 包装 Retrofit `Callback`，调用方在协程作用域内直接 `await` 结果

#### Scenario: 非递归文件扫描不用 Thread.sleep 轮询
- **WHEN** `FileRepository.fetchFilesNonRecursive` 需要等待多个分页回调
- **THEN** SHALL NOT 使用 `while(!completed) { Thread.sleep(100) }` 模式；改用 `suspend` + `Callback` 包装或 `CompletableDeferred`

#### Scenario: ViewModel 生命周期绑定
- **WHEN** `PlaybackViewModel` 执行异步操作
- **THEN** 使用 `viewModelScope` 启动协程，ViewModel 销毁时自动取消所有协程

### Requirement: Flow 替代 LiveData 作为数据流
系统 SHALL 使用 `kotlinx.coroutines.flow.Flow` 作为 Repository → ViewModel 之间的数据流通道，`LiveData` 仅保留 ViewModel → View 层的数据观察。

#### Scenario: 播放列表 Flow 采集
- **WHEN** `PlaylistRepository.getAllPlaylists()` 返回数据
- **THEN** 返回类型为 `Flow<List<Playlist>>`，ViewModel 通过 `stateIn(scope, SharingStarted.WhileSubscribed(5000), initial)` 转换为 `StateFlow`

#### Scenario: Flow 错误处理
- **WHEN** `Flow` 中出现网络异常
- **THEN** ViewModel 捕获异常并通过 `MutableStateFlow<UiState>` 传递 `Error` 状态到 View 层，不崩溃

### Requirement: 结构化并发
系统 SHALL 遵循结构化并发原则，不创建独立不受控的协程。

#### Scenario: 无全局 CoroutineScope
- **WHEN** 搜索 "GlobalScope"
- **THEN** 项目源码中 SHALL NOT 出现 `GlobalScope.launch` 或 `GlobalScope.async`

#### Scenario: 并发异常传播
- **WHEN** 一个 `coroutineScope { }` 内的子协程失败
- **THEN** 同作用域内的兄弟协程自动取消，异常向上传播到 ViewModel

### Requirement: 线程安全的状态管理
系统 SHALL 通过 `Mutex` 或 `@Volatile` 保护多协程访问的共享状态。

#### Scenario: dlink 预加载无竞态
- **WHEN** `PlaybackViewModel` 的 `preloadNextFile()` 和 `playCurrentFile()` 并发执行
- **THEN** `preloadedDlink` 的读写通过 `Mutex` 保护，不出现脏读取或错误命中
