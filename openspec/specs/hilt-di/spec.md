## Requirements

### Requirement: Hilt 作为 DI 框架
系统 SHALL 使用 Dagger Hilt (`com.google.dagger:hilt-android`) 管理所有依赖注入。

#### Scenario: Application 级单例自动注入
- **WHEN** 需要 `BaiduAuthService`、`FileRepository`、`RetrofitClient` 等全局单例
- **THEN** 通过 `@Singleton` + `@Provides` 在 Hilt Module 中提供，而非手动 `getInstance()` 静态方法

#### Scenario: ViewModel 自动注入 Repository
- **WHEN** 创建 `PlaybackViewModel`
- **THEN** 通过 `@HiltViewModel` + `@Inject constructor(application, fileRepo, historyRepo, playlistRepo)` 自动注入依赖，无需手动 `FileRepository.getInstance()`

### Requirement: Retrofit 实例通过 Module 提供
系统 SHALL 通过 Hilt `@Module` + `@Provides` 提供 Retrofit 和 OkHttp 实例。

#### Scenario: 双 BaseUrl Retrofit 注入
- **WHEN** 需要百度网盘 API 或 OAuth API 的 Retrofit 实例
- **THEN** 通过 `@Qualifier("panApi")` 和 `@Qualifier("oauth")` 区分两个 Retrofit 实例，由 Hilt Module 创建

#### Scenario: OkHttpClient 作为依赖提供
- **WHEN** 需要创建 Retrofit 实例
- **THEN** `OkHttpClient` 通过 `@Provides @Singleton` 提供，含日志拦截器和超时配置

### Requirement: Room 数据库通过 Hilt 提供
系统 SHALL 通过 Hilt `@Module` 提供 Room 数据库实例和 DAO。

#### Scenario: AppDatabase 单例注入
- **WHEN** 需要 `AppDatabase` 实例
- **THEN** 通过 `@Provides @Singleton` 提供 `AppDatabase.getInstance(context)`，DAO 通过 `database.playlistDao()` 等方法提供

### Requirement: 无手动单例残留
系统 SHALL NOT 保留任何 `static synchronized getInstance()` 手动单例模式（外部 SDK 必需的除外）。

#### Scenario: 搜索手动单例残留
- **WHEN** 搜索项目中的 `getInstance` 方法
- **THEN** 仅在 Hilt Module 的 `@Provides` 方法中出现，不在业务类中出现（除 Retrofit/OkHttp Builder 等无状态工厂）
