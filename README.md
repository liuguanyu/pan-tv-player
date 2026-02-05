# 百度网盘TV播放器

这是一个专为Android TV设计的百度网盘媒体播放器应用，支持照片和视频的播放。

## 功能特性

- 百度网盘扫码登录
- 照片和视频播放
- 支持多种媒体格式
- 播放记录管理
- 随机/顺序播放模式
- 地点识别显示
- 遥控器全面支持（D-pad导航和选择）

## 遥控器支持

应用完全支持电视遥控器操作，无需触摸屏即可完成所有功能。

详细的遥控器操作指南请参见：[遥控器操作指南](REMOTE_CONTROL_GUIDE.md)

主要特性：
- ✅ 方向键导航所有界面
- ✅ 明显的焦点视觉反馈
- ✅ 智能默认焦点设置
- ✅ 播放页面直接按键控制

## 配置文件

应用需要配置百度网盘的API凭据才能正常工作。敏感信息已从版本控制中排除，确保安全性。

## 技术栈

- Android Java
- Android TV + Leanback
- MVVM + LiveData
- Retrofit + OkHttp

## 开发环境

- Android Studio
- Android 9.0 (API 28) 或更高版本
- Sony 65寸电视 (目标设备)

## 项目结构

```
app/
├── src/main/java/com/baidu/tv/player/
│   ├── auth/           # 登录认证模块
│   ├── ui/             # UI组件
│   ├── viewmodel/      # ViewModel层
│   ├── repository/     # 数据仓库
│   ├── network/        # 网络请求
│   ├── model/          # 数据模型
│   ├── utils/          # 工具类
│   └── service/        # 后台服务
└── src/main/res/       # 资源文件