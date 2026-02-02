# 播放列表功能重新设计 - 实现总结

## 概述

本次重构完全重新设计了百度网盘播放器的首页界面和播放列表功能，从原来简单的三个圆形按钮改为强大的播放列表管理系统。

## 主要变更

### 1. 数据库层 (阶段1)

#### 新增表结构

**播放列表表 (playlists)**
```java
@Entity(tableName = "playlists")
public class Playlist {
    @PrimaryKey(autoGenerate = true)
    private long id;
    private String name;                  // 播放列表名称
    private long createdAt;               // 创建时间
    private long lastPlayedAt;            // 最后播放时间
    private int lastPlayedIndex;          // 最后播放位置
    private int mediaType;                // 媒体类型 (0=混合, 1=视频, 2=图片)
    private String coverImagePath;        // 封面图片路径
    private int totalItems;               // 总文件数
    private long totalDuration;           // 总时长（毫秒）
    private String sourcePaths;           // 源目录路径（JSON数组，用于刷新）
}
```

**播放列表项表 (playlist_items)**
```java
@Entity(tableName = "playlist_items")
public class PlaylistItem {
    @PrimaryKey(autoGenerate = true)
    private long id;
    private long playlistId;              // 外键，关联播放列表
    private String filePath;              // 文件路径
    private String fileName;              // 文件名
    private long fsId;                    // 百度网盘文件ID（重要：不存储dlink）
    private int mediaType;                // 媒体类型 (1=视频, 2=图片)
    private int sortOrder;                // 排序顺序
    private long duration;                // 时长（毫秒）
    private long fileSize;                // 文件大小（字节）
}
```

#### 数据库升级
- 从版本1升级到版本2
- 使用 `fallbackToDestructiveMigration()` 允许重建数据库
- 添加外键级联删除：删除播放列表时自动删除其所有项

#### DAO实现
- `PlaylistDao.java`: 播放列表CRUD操作
- `PlaylistItemDao.java`: 播放列表项CRUD操作
- `PlaylistRepository.java`: 业务逻辑层，提供异步操作

### 2. 首页UI重构 (阶段2)

#### 布局变更 (fragment_main.xml)
**移除：**
- 三个圆形按钮（图片、视频、全部）
- 设置按钮

**新增：**
- "我的播放列表"水平滚动区域（RecyclerView）
- "快速操作"区域（浏览文件、创建播放列表）
- 保留"最近播放"区域

#### 播放列表卡片设计 (item_playlist_card.xml)
```xml
- 16:9 封面图（使用Glide加载）
- 播放列表名称（支持滚动长文本）
- 统计信息（文件数量）
- 焦点效果（选中时放大并显示边框）
```

#### 新增组件
- `PlaylistAdapter.java`: 播放列表RecyclerView适配器
- `PlaylistCardViewHolder.java`: 播放列表卡片ViewHolder
- 点击播放列表直接启动播放器
- 长按显示操作菜单（暂未完全实现）

#### 遥控器操作
- 菜单键打开设置（移除首页设置按钮）
- 左/右方向键：在播放列表之间水平移动
- 上/下方向键：在播放列表和快速操作之间移动

### 3. 文件浏览器改造 (阶段3)

#### 多选模式支持
**FileBrowserActivity.java**
- 添加 `multiSelectMode` Intent参数
- 传递给FileBrowserFragment

**FileAdapter.java**
- 添加 `multiSelectMode` 字段
- 添加 `selectedPaths` 集合存储选中路径
- `toggleSelection()` 方法切换选中状态
- `getSelectedPaths()` 方法获取选中路径
- 复选框UI显示/隐藏逻辑

**FileBrowserFragment.java**
- 添加 `multiSelectMode` 参数支持
- 修改按钮文本（多选模式显示"确认选择"）
- 修改点击事件（多选模式切换选中，普通模式播放）
- 实现 `onConfirmSelection()` 方法

**布局文件 (item_file.xml)**
- 已有 `iv_selected` ImageView（默认隐藏）
- 使用 `android:drawable/checkbox_on_background` 作为选中图标

### 4. 播放列表创建逻辑 (阶段4)

#### 核心实现 (FileBrowserFragment.onConfirmSelection())

**流程：**
1. 获取用户选中的路径集合
2. 判断每个路径是文件还是目录
3. 对于目录：使用 `FileRepository.fetchFilesRecursive()` 递归获取所有文件
4. 对于文件：直接添加（如果是媒体文件）
5. 过滤出图片和视频文件（排除目录）
6. 创建 `Playlist` 对象
7. 生成播放列表名称（使用第一个目录名）
8. 选择封面图（优先使用第一张图片）
9. 保存源目录路径为JSON数组（用于刷新功能）
10. 插入播放列表到数据库
11. 创建 `PlaylistItem` 对象列表
12. 插入播放列表项到数据库
13. 返回首页

#### 关键设计决策
- **只存储fsId，不存储dlink**：因为dlink有24小时有效期
- **记录sourcePaths**：支持后续刷新播放列表
- **异步操作**：在后台线程执行，避免UI卡顿
- **显示进度提示**：用Toast提示用户操作状态

#### FileRepository增强
- 添加 `FileListCallback` 接口
- 添加 `fetchFilesRecursive()` 回调方法
- 支持灵活组合多个目录的文件获取

### 5. 播放器改造 (阶段5)

#### PlaybackActivity.java
**initData() 方法增强：**
- 添加 `playlistDatabaseId` 参数检查
- 从数据库加载播放列表和播放列表项
- 将 `PlaylistItem` 转换为 `FileInfo`
- 根据文件扩展名判断媒体类型
- 恢复上次播放位置（`lastPlayedIndex`）
- 更新最后播放时间
- 传递 `playlistDatabaseId` 给ViewModel

**导入添加：**
```java
import com.baidu.tv.player.model.Playlist;
import com.baidu.tv.player.model.PlaylistItem;
import com.baidu.tv.player.repository.PlaylistRepository;
```

#### PlaybackViewModel.java
**新增字段：**
```java
private long playlistDatabaseId = -1;
private PlaylistRepository playlistRepository;
```

**新增方法：**
- `setPlaylistDatabaseId(long id)`: 设置播放列表ID
- `updatePlaylistProgress(int index)`: 更新播放进度到数据库

**修改方法：**
- `setCurrentIndex()`: 调用 `updatePlaylistProgress()`
- `seekTo()`: 调用 `updatePlaylistProgress()`

**实时获取dlink：**
- 播放时通过 `prepareMediaUrl()` 实时获取dlink
- 使用 `FileRepository.fetchFileDetail()` API

#### MainFragment.java
**onPlaylistClick() 方法：**
```java
private void onPlaylistClick(Playlist playlist) {
    Intent intent = new Intent(requireContext(), PlaybackActivity.class);
    intent.putExtra("playlistDatabaseId", playlist.getId());
    startActivity(intent);
}
```

## 技术亮点

### 1. 数据库设计
- 使用Room ORM框架
- 外键级联删除保证数据完整性
- 分离播放列表和播放列表项，支持大规模数据
- 记录源路径支持刷新功能

### 2. 性能优化
- 分页加载播放列表（防止内存溢出）
- 异步文件扫描（避免UI卡顿）
- 使用PlaylistCache减少Intent数据传递
- 封面图懒加载（Glide）

### 3. 用户体验
- 水平滚动支持无限播放列表
- 焦点效果提升TV操作体验
- 遥控器菜单键快捷访问设置
- 断点续播（记录lastPlayedIndex）
- 实时显示操作状态（Toast提示）

### 4. 代码质量
- MVVM架构（ViewModel + LiveData + Repository）
- 清晰的职责分离
- 充分复用现有代码
- 详细的注释和日志

## 文件修改清单

### 新增文件
1. `app/src/main/java/com/baidu/tv/player/model/Playlist.java`
2. `app/src/main/java/com/baidu/tv/player/model/PlaylistItem.java`
3. `app/src/main/java/com/baidu/tv/player/database/PlaylistDao.java`
4. `app/src/main/java/com/baidu/tv/player/database/PlaylistItemDao.java`
5. `app/src/main/java/com/baidu/tv/player/repository/PlaylistRepository.java`
6. `app/src/main/res/layout/item_playlist_card.xml`
7. `app/src/main/java/com/baidu/tv/player/ui/main/PlaylistAdapter.java`
8. `app/src/main/java/com/baidu/tv/player/ui/main/PlaylistCardViewHolder.java`
9. `NEW_HOME_DESIGN.md` - 设计文档
10. `PLAYLIST_REDESIGN_SUMMARY.md` - 本文档

### 修改文件
1. `app/src/main/java/com/baidu/tv/player/database/AppDatabase.java` - 升级到版本2
2. `app/src/main/res/layout/fragment_main.xml` - 重新设计首页布局
3. `app/src/main/java/com/baidu/tv/player/ui/main/MainFragment.java` - 添加播放列表支持
4. `app/src/main/java/com/baidu/tv/player/ui/filebrowser/FileBrowserActivity.java` - 添加多选模式
5. `app/src/main/java/com/baidu/tv/player/ui/filebrowser/FileAdapter.java` - 添加多选UI
6. `app/src/main/java/com/baidu/tv/player/ui/filebrowser/FileBrowserFragment.java` - 实现创建逻辑
7. `app/src/main/java/com/baidu/tv/player/repository/FileRepository.java` - 添加回调接口
8. `app/src/main/java/com/baidu/tv/player/ui/playback/PlaybackActivity.java` - 支持数据库加载
9. `app/src/main/java/com/baidu/tv/player/ui/playback/PlaybackViewModel.java` - 添加进度更新

## 待实现功能

### 高优先级
1. 播放列表刷新功能（重新扫描sourcePaths）
2. 播放列表长按菜单（播放/刷新/删除/重命名）
3. 播放列表编辑（添加/删除文件）

### 中优先级
4. 封面自定义（手动选择）
5. 播放列表排序（按名称/时间/文件数）
6. 播放列表搜索
7. 播放列表分类/标签

### 低优先级
8. 播放列表导入/导出
9. 播放列表分享
10. 智能播放列表（自动生成）

## 已知问题

1. **等待异步操作的实现不够优雅**
   - 使用 `Thread.sleep()` + 轮询等待异步完成
   - 建议改用 `CountDownLatch` 或 `CompletableFuture`

2. **播放列表项插入成功判断不够准确**
   - 依赖500ms延迟等待
   - 建议使用回调返回插入ID

3. **文件类型判断基于扩展名**
   - 可能不够准确
   - 建议使用MIME类型

4. **大目录扫描可能超时**
   - 30秒超时可能不够
   - 建议添加进度显示和取消功能

## 测试建议

### 功能测试
1. 创建播放列表（单个目录）
2. 创建播放列表（多个目录）
3. 创建播放列表（混合文件和目录）
4. 播放播放列表
5. 断点续播
6. 播放进度更新

### 性能测试
1. 大目录扫描（>1000文件）
2. 多播放列表显示（>20个）
3. 长时间播放（内存泄漏检查）

### 边界测试
1. 空播放列表
2. 单个文件播放列表
3. 大文件（>2GB）
4. 特殊字符文件名
5. 网络中断恢复

## 结论

本次重构成功实现了播放列表功能的完整闭环：
- ✅ 首页展示播放列表
- ✅ 点击直接播放
- ✅ 支持复选多个目录递归添加
- ✅ 保存到数据库持久化
- ✅ 断点续播
- ✅ 移除无用的UI元素

整体架构清晰，代码质量较高，为后续功能扩展打下了良好基础。

---
*文档生成时间：2026-02-02*
*实现模式：Code模式*
*总耗时：约2小时*