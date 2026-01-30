# 百度网盘TV播放器 - 架构设计文档

## 1. 系统架构

### 1.1 整体架构
采用MVVM (Model-View-ViewModel) 架构模式，结合Android TV的Leanback框架。

```
┌─────────────────────────────────────────────────────────┐
│                      Presentation Layer                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │   Activity   │  │   Fragment   │  │   Adapter    │  │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  │
│         │                  │                  │          │
│         └──────────────────┼──────────────────┘          │
│                            │                             │
│                    ┌───────▼────────┐                    │
│                    │   ViewModel    │                    │
│                    └───────┬────────┘                    │
└────────────────────────────┼────────────────────────────┘
                             │
┌────────────────────────────┼────────────────────────────┐
│                      Domain Layer                       │
│                    ┌──────────▼────────┐                │
│                    │    Repository     │                │
│                    └──────────┬────────┘                │
└────────────────────────────┼────────────────────────────┘
                             │
┌────────────────────────────┼────────────────────────────┐
│                       Data Layer                        │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │   Network    │  │   Database   │  │   Storage    │  │
│  │   (Retrofit) │  │  (Room)      │  │ (SharedPreferences)│
│  └──────────────┘  └──────────────┘  └──────────────┘  │
└─────────────────────────────────────────────────────────┘
```

### 1.2 技术栈选择

#### 网络层
- **Retrofit + OkHttp**: 
  - ✅ 成熟的HTTP客户端，支持异步请求
  - ✅ 自动JSON序列化/反序列化
  - ✅ 支持拦截器（用于添加token）
  - ✅ 良好的错误处理机制
  - ✅ 广泛的社区支持

#### 数据库
- **Room Database**:
  - ✅ 官方推荐的SQLite封装
  - ✅ 编译时SQL验证
  - ✅ 支持LiveData观察
  - ✅ 适合存储播放记录和设置

#### UI框架
- **Android TV Leanback**:
  - ✅ 专为TV设计的UI组件
  - ✅ 内置焦点管理
  - ✅ 支持D-pad导航
  - ✅ 提供播放器组件

#### 架构组件
- **ViewModel**: 管理UI相关数据，配置更改时保持数据
- **LiveData**: 响应式数据观察
- **Lifecycle**: 管理组件生命周期

## 2. 模块设计

### 2.1 认证模块 (Auth Module)

**职责**: 处理百度网盘OAuth 2.0认证流程

**核心类**:
- `BaiduAuthService`: 认证服务单例
- `AuthRepository`: 认证数据仓库
- `AuthViewModel`: 认证视图模型
- `LoginActivity`: 登录界面

**流程**:
```
1. 检查本地存储的token
2. 如果token有效，直接进入主界面
3. 如果token无效，显示二维码登录界面
4. 获取设备码 (device_code)
5. 显示二维码 (verification_url + user_code)
6. 轮询设备码状态
7. 授权成功，保存token
8. 进入主界面
```

### 2.2 文件浏览模块 (File Browser Module)

**职责**: 浏览和选择百度网盘文件

**核心类**:
- `BaiduPanService`: 百度网盘API服务
- `FileRepository`: 文件数据仓库
- `FileViewModel`: 文件视图模型
- `FileBrowserFragment`: 文件浏览界面
- `FileAdapter`: 文件列表适配器

**功能**:
- 递归获取文件列表
- 过滤图片/视频文件
- 支持文件夹选择
- 显示文件缩略图

### 2.3 播放器模块 (Player Module)

**职责**: 播放图片和视频

**核心类**:
- `PlayerService`: 播放服务
- `PlayerViewModel`: 播放视图模型
- `PlaybackActivity`: 播放界面
- `VideoPlayerFragment`: 视频播放器
- `ImagePlayerFragment`: 图片播放器

**功能**:
- 支持多种视频格式 (mp4, mov, 3gp, mkv, avi, h.265)
- 支持多种图片格式 (jpeg, jpg, png, avif, webp, heic)
- 横屏/竖屏自适应
- 顺序/随机/单个播放模式
- 预加载下一个文件
- 图片特效 (淡入淡出、缓动、浮现、跳动)

### 2.4 播放记录模块 (Playback History Module)

**职责**: 管理播放历史记录

**核心类**:
- `PlaybackHistoryRepository`: 播放记录仓库
- `PlaybackHistoryDatabase`: 播放记录数据库
- `PlaybackHistoryEntity`: 播放记录实体

**功能**:
- 保存最近10条播放记录
- 显示最近4条记录
- 点击直接播放

### 2.5 设置模块 (Settings Module)

**职责**: 管理应用设置

**核心类**:
- `SettingsRepository`: 设置仓库
- `SettingsFragment`: 设置界面

**功能**:
- 图片特效设置
- 特效时长设置
- 展示时长设置
- 地点显示开关

### 2.6 地点识别模块 (Location Module)

**职责**: 识别媒体文件的拍摄地点

**核心类**:
- `LocationService`: 地点识别服务
- 使用Android原生Geocoder API

**功能**:
- 从EXIF数据提取GPS坐标
- 使用Geocoder转换为地址
- 在播放时显示地点信息

## 3. 数据流设计

### 3.1 认证流程
```
LoginActivity
    ↓
AuthViewModel
    ↓
AuthRepository
    ↓
BaiduAuthService (Retrofit)
    ↓
百度OAuth API
```

### 3.2 文件浏览流程
```
FileBrowserFragment
    ↓
FileViewModel
    ↓
FileRepository
    ↓
BaiduPanService (Retrofit)
    ↓
百度网盘API
```

### 3.3 播放流程
```
PlaybackActivity
    ↓
PlayerViewModel
    ↓
PlayerService
    ↓
ExoPlayer (视频) / Glide (图片)
```

## 4. 关键技术点

### 4.1 随机播放算法
- 预先生成随机播放列表
- 使用Fisher-Yates洗牌算法
- 支持预加载下一个文件

### 4.2 预加载机制
- 视频预加载: 使用ExoPlayer的预加载功能
- 图片预加载: 使用Glide的预加载功能
- 在当前文件播放时，开始加载下一个文件

### 4.3 图片特效实现
- 使用ViewPropertyAnimator实现动画
- 支持多种特效切换
- 可配置动画时长和展示时长

### 4.4 横竖屏适配
- 检测媒体文件宽高比
- 视频横屏: 使用AspectFill模式
- 视频竖屏: 保持比例，占满纵向
- 图片: 保持原比例

## 5. 性能优化

### 5.1 内存优化
- 使用Glide加载图片，自动缓存
- 使用ExoPlayer播放视频，自动缓存
- 及时释放不用的资源

### 5.2 网络优化
- 使用OkHttp连接池
- 实现请求重试机制
- 添加网络状态监听

### 5.3 用户体验优化
- 添加加载进度提示
- 实现平滑的界面切换
- 优化焦点管理

## 6. 安全性

### 6.1 Token管理
- 使用SharedPreferences加密存储token
- 定期刷新token
- Token过期自动重新登录

### 6.2 网络安全
- 使用HTTPS
- 验证SSL证书
- 防止中间人攻击

## 7. 配置管理

### 7.1 敏感信息配置
- 创建`config.properties.example`文件
- 包含app_id, app_key, secret_key等
- 实际配置文件不提交到版本控制

### 7.2 构建配置
- 使用build.gradle管理依赖
- 支持多渠道打包
- 配置签名信息

## 8. 测试策略

### 8.1 单元测试
- 测试ViewModel逻辑
- 测试Repository数据操作
- 测试工具类方法

### 8.2 集成测试
- 测试网络请求
- 测试数据库操作
- 测试播放器功能

### 8.3 UI测试
- 测试界面导航
- 测试焦点管理
- 测试播放流程

## 9. 部署方案

### 9.1 打包
- 生成APK文件
- 签名APK
- 优化APK大小

### 9.2 发布
- 上传到应用商店
- 或通过USB安装到电视

## 10. 后续优化方向

- 支持更多媒体格式
- 添加字幕支持
- 实现播放列表管理
- 添加收藏功能
- 支持多用户
- 添加家长控制