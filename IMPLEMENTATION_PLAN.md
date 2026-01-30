# 百度网盘TV播放器 - 实施计划

## 项目概述
将现有的Electron版百度网盘播放器迁移到Android TV平台，使用Java语言和Android TV Leanback框架。

## 开发阶段划分

### 阶段1: 项目初始化 (1-2天)
- [ ] 创建Android项目结构
- [ ] 配置build.gradle文件
- [ ] 添加必要的依赖库
- [ ] 创建基础配置文件
- [ ] 设置项目资源文件

### 阶段2: 基础架构搭建 (2-3天)
- [ ] 创建MVVM架构基础类
- [ ] 实现网络层 (Retrofit + OkHttp)
- [ ] 实现数据层 (Room Database)
- [ ] 创建工具类 (加密、日志等)
- [ ] 配置百度网盘API

### 阶段3: 认证模块 (2-3天)
- [ ] 实现百度OAuth 2.0认证流程
- [ ] 创建登录界面 (二维码显示)
- [ ] 实现设备码轮询机制
- [ ] 实现token存储和刷新
- [ ] 创建认证状态管理

### 阶段4: 主界面 (2-3天)
- [ ] 设计主界面布局
- [ ] 实现媒体类型选择 (图片/视频/混合)
- [ ] 实现最近任务列表
- [ ] 实现焦点管理和导航
- [ ] 添加Leanback组件集成

### 阶段5: 文件浏览模块 (3-4天)
- [ ] 实现文件列表API调用
- [ ] 创建文件浏览界面
- [ ] 实现文件夹递归遍历
- [ ] 实现文件过滤 (图片/视频)
- [ ] 实现文件缩略图加载
- [ ] 实现文件选择功能

### 阶段6: 播放器模块 (4-5天)
- [ ] 集成ExoPlayer视频播放器
- [ ] 实现视频播放界面
- [ ] 实现图片播放界面
- [ ] 实现横竖屏适配
- [ ] 实现播放模式切换 (顺序/随机/单个)
- [ ] 实现预加载机制
- [ ] 实现播放控制 (播放/暂停/上一个/下一个)

### 阶段7: 图片特效 (2-3天)
- [ ] 实现淡入淡出特效
- [ ] 实现缓动特效
- [ ] 实现浮现特效
- [ ] 实现跳动特效
- [ ] 实现特效切换功能
- [ ] 实现特效时长配置

### 阶段8: 播放记录管理 (2天)
- [ ] 创建播放记录数据库
- [ ] 实现播放记录保存
- [ ] 实现播放记录查询
- [ ] 实现播放记录显示
- [ ] 实现播放记录点击播放

### 阶段9: 地点识别 (2-3天)
- [ ] 实现EXIF数据提取
- [ ] 集成Android Geocoder API
- [ ] 实现GPS坐标转换
- [ ] 实现地点信息显示
- [ ] 实现地点显示开关

### 阶段10: 设置模块 (2天)
- [ ] 创建设置界面
- [ ] 实现图片特效设置
- [ ] 实现时长配置
- [ ] 实现地点显示开关
- [ ] 实现设置持久化

### 阶段11: 优化和测试 (3-4天)
- [ ] 性能优化
- [ ] 内存优化
- [ ] 网络优化
- [ ] 用户体验优化
- [ ] 单元测试
- [ ] 集成测试
- [ ] 真机测试

### 阶段12: 打包和部署 (1-2天)
- [ ] 配置签名
- [ ] 生成APK
- [ ] 测试APK安装
- [ ] 准备发布材料

## 详细任务列表

### 1. 项目初始化
#### 1.1 创建项目结构
```
app/
├── src/main/
│   ├── java/com/baidu/tv/player/
│   │   ├── auth/
│   │   │   ├── BaiduAuthService.java
│   │   │   ├── AuthRepository.java
│   │   │   ├── AuthViewModel.java
│   │   │   └── LoginActivity.java
│   │   ├── ui/
│   │   │   ├── main/
│   │   │   │   ├── MainActivity.java
│   │   │   │   └── MainFragment.java
│   │   │   ├── filebrowser/
│   │   │   │   ├── FileBrowserFragment.java
│   │   │   │   └── FileAdapter.java
│   │   │   ├── player/
│   │   │   │   ├── PlaybackActivity.java
│   │   │   │   ├── VideoPlayerFragment.java
│   │   │   │   └── ImagePlayerFragment.java
│   │   │   └── settings/
│   │   │       └── SettingsFragment.java
│   │   ├── viewmodel/
│   │   │   ├── FileViewModel.java
│   │   │   ├── PlayerViewModel.java
│   │   │   └── SettingsViewModel.java
│   │   ├── repository/
│   │   │   ├── FileRepository.java
│   │   │   ├── PlaybackHistoryRepository.java
│   │   │   └── SettingsRepository.java
│   │   ├── network/
│   │   │   ├── BaiduPanService.java
│   │   │   ├── RetrofitClient.java
│   │   │   └── ApiConstants.java
│   │   ├── model/
│   │   │   ├── FileInfo.java
│   │   │   ├── AuthInfo.java
│   │   │   ├── PlaybackHistory.java
│   │   │   └── Settings.java
│   │   ├── database/
│   │   │   ├── AppDatabase.java
│   │   │   ├── PlaybackHistoryDao.java
│   │   │   └── PlaybackHistoryEntity.java
│   │   ├── service/
│   │   │   └── PlayerService.java
│   │   └── utils/
│   │       ├── EncryptionUtils.java
│   │       ├── FileUtils.java
│   │       ├── LocationUtils.java
│   │       └── RandomUtils.java
│   └── res/
│       ├── layout/
│       ├── values/
│       ├── drawable/
│       └── xml/
```

#### 1.2 配置build.gradle
- 项目级build.gradle
- 应用级build.gradle
- 添加依赖:
  - AndroidX
  - Leanback
  - Retrofit
  - OkHttp
  - Room
  - ExoPlayer
  - Glide
  - Gson

#### 1.3 创建配置文件
- config.properties.example
- AndroidManifest.xml
- proguard-rules.pro

### 2. 基础架构
#### 2.1 网络层
- 创建RetrofitClient单例
- 配置OkHttp拦截器 (添加token)
- 创建BaiduPanService接口
- 定义API端点

#### 2.2 数据层
- 创建Room数据库
- 创建DAO接口
- 创建Entity类
- 创建Repository类

#### 2.3 工具类
- EncryptionUtils: AES加密/解密
- FileUtils: 文件操作
- LocationUtils: 地点识别
- RandomUtils: 随机算法

### 3. 认证模块
#### 3.1 认证流程
- 实现getDeviceCode()方法
- 实现pollDeviceCodeStatus()方法
- 实现token存储
- 实现token刷新

#### 3.2 登录界面
- 创建LoginActivity
- 集成QRCode生成库
- 显示二维码
- 实现轮询逻辑
- 处理登录成功/失败

### 4. 主界面
#### 4.1 布局设计
- 使用Leanback的BrowseFragment
- 创建媒体类型卡片
- 创建最近任务列表
- 实现焦点管理

#### 4.2 导航逻辑
- 点击图片/视频/混合图标
- 进入文件选择界面
- 点击最近任务
- 直接播放

### 5. 文件浏览
#### 5.1 文件列表
- 调用百度网盘API
- 解析文件列表
- 过滤图片/视频
- 递归遍历文件夹

#### 5.2 文件选择
- 显示文件列表
- 支持多选
- 生成播放列表
- 保存播放记录

### 6. 播放器
#### 6.1 视频播放
- 集成ExoPlayer
- 实现播放控制
- 实现横竖屏适配
- 支持多种格式

#### 6.2 图片播放
- 使用Glide加载图片
- 实现图片特效
- 实现横竖屏适配
- 支持多种格式

#### 6.3 播放模式
- 顺序播放
- 随机播放
- 单个播放
- 模式切换

#### 6.4 预加载
- 预加载下一个文件
- 随机算法预生成列表
- 缓存管理

### 7. 图片特效
#### 7.1 特效实现
- 淡入淡出: Alpha动画
- 缓动: Translate动画
- 浮现: Scale动画
- 跳动: 组合动画

#### 7.2 特效配置
- 特效选择
- 动画时长
- 展示时长

### 8. 播放记录
#### 8.1 数据存储
- 创建数据库表
- 保存播放记录
- 限制10条记录

#### 8.2 记录显示
- 查询最近4条
- 显示在主界面
- 点击播放

### 9. 地点识别
#### 9.1 EXIF提取
- 读取图片EXIF数据
- 提取GPS坐标
- 读取视频元数据

#### 9.2 地点转换
- 使用Geocoder API
- 转换为地址
- 显示在右上角

#### 9.3 开关控制
- 设置中添加开关
- 默认开启
- 动态显示/隐藏

### 10. 设置模块
#### 10.1 设置界面
- 创建SettingsFragment
- 使用PreferenceFragmentCompat
- 添加设置项

#### 10.2 设置项
- 图片特效选择
- 动画时长
- 展示时长
- 地点显示开关

### 11. 优化
#### 11.1 性能优化
- 图片缓存优化
- 视频预加载优化
- 内存泄漏检查

#### 11.2 用户体验优化
- 加载提示
- 错误提示
- 平滑过渡

### 12. 测试
#### 12.1 单元测试
- ViewModel测试
- Repository测试
- 工具类测试

#### 12.2 集成测试
- API测试
- 数据库测试
- 播放器测试

#### 12.3 真机测试
- Sony电视测试
- 不同分辨率测试
- 长时间运行测试

### 13. 打包部署
#### 13.1 打包
- 配置签名
- 生成release APK
- 优化APK大小

#### 13.2 部署
- USB安装测试
- 准备发布材料

## 技术难点和解决方案

### 1. 百度OAuth 2.0认证
**难点**: 设备码轮询机制
**解决方案**: 
- 使用Handler或Timer实现轮询
- 在后台线程执行网络请求
- 使用LiveData通知UI更新

### 2. 随机播放预加载
**难点**: 随机算法和预加载结合
**解决方案**:
- 使用Fisher-Yates算法预生成随机列表
- 在播放当前文件时，开始加载下一个
- 使用ExoPlayer的预加载功能

### 3. 图片特效实现
**难点**: 多种特效切换和配置
**解决方案**:
- 使用ViewPropertyAnimator实现动画
- 创建特效枚举类
- 使用SharedPreferences保存配置

### 4. 地点识别
**难点**: EXIF数据提取和Geocoder使用
**解决方案**:
- 使用ExifInterface读取EXIF
- 使用Android Geocoder API
- 异步执行地点转换

### 5. 横竖屏适配
**难点**: 不同媒体格式的适配
**解决方案**:
- 检测媒体宽高比
- 动态调整播放器布局
- 使用不同的缩放模式

## 风险评估

### 高风险
1. **百度API变更**: 百度网盘API可能随时变更
   - 缓解措施: 定期检查API文档，做好版本兼容

2. **性能问题**: 大量文件加载可能导致卡顿
   - 缓解措施: 实现分页加载，使用缓存

### 中风险
1. **兼容性问题**: 不同Android版本和设备
   - 缓解措施: 测试多个版本，做好兼容处理

2. **网络问题**: 网络不稳定导致播放失败
   - 缓解措施: 实现重试机制，添加错误提示

### 低风险
1. **UI适配**: 不同分辨率适配
   - 缓解措施: 使用约束布局，做好响应式设计

## 时间估算

- 阶段1: 1-2天
- 阶段2: 2-3天
- 阶段3: 2-3天
- 阶段4: 2-3天
- 阶段5: 3-4天
- 阶段6: 4-5天
- 阶段7: 2-3天
- 阶段8: 2天
- 阶段9: 2-3天
- 阶段10: 2天
- 阶段11: 3-4天
- 阶段12: 1-2天

**总计**: 约26-37天 (4-6周)

## 里程碑

1. **M1**: 项目初始化完成 (第1周)
2. **M2**: 认证模块完成 (第2周)
3. **M3**: 主界面和文件浏览完成 (第3周)
4. **M4**: 播放器核心功能完成 (第4周)
5. **M5**: 所有功能完成 (第5周)
6. **M6**: 测试和优化完成 (第6周)

## 交付物

1. 源代码
2. APK文件
3. 技术文档
4. 用户手册
5. 测试报告

## 后续优化方向

1. 支持更多媒体格式
2. 添加字幕支持
3. 实现播放列表管理
4. 添加收藏功能
5. 支持多用户
6. 添加家长控制
7. 优化启动速度
8. 添加离线模式