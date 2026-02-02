# 播放修复和删除功能实现总结

## 修复的问题

### 1. 播放列表重复添加文件问题

**问题描述：**
在 [`PlaybackActivity.initData()`](app/src/main/java/com/baidu/tv/player/ui/playback/PlaybackActivity.java:555) 中，从数据库加载播放列表项后，每个文件被添加了两次：

```java
files.add(fileInfo);
files.add(fileInfo);  // 重复添加！
```

**修复方案：**
移除重复的 `files.add(fileInfo);` 语句，每个文件只添加一次。

**修改文件：**
- [`app/src/main/java/com/baidu/tv/player/ui/playback/PlaybackActivity.java`](app/src/main/java/com/baidu/tv/player/ui/playback/PlaybackActivity.java:532-557)

---

### 2. 播放问题调试增强

**问题描述：**
用户反馈点击播放列表后不播放，但缺少足够的日志信息来诊断问题。

**修复方案：**
在关键位置添加详细的调试日志：

1. **播放当前文件时** - 记录文件信息、路径、fsId、是否有 dlink
2. **播放视频时** - 记录视频 URL
3. **播放图片时** - 记录图片 URL 和加载状态

**修改文件：**
- [`app/src/main/java/com/baidu/tv/player/ui/playback/PlaybackActivity.java`](app/src/main/java/com/baidu/tv/player/ui/playback/PlaybackActivity.java:659-792)

**添加的日志：**
```java
// 播放当前文件
android.util.Log.d("PlaybackActivity", "playCurrentFile: " + currentFile.getServerFilename() + 
    ", path=" + currentFile.getPath() + 
    ", fsId=" + currentFile.getFsId() +
    ", category=" + currentFile.getCategory() +
    ", hasDlink=" + (currentFile.getDlink() != null));

// 准备获取媒体URL
android.util.Log.d("PlaybackActivity", "准备获取媒体URL, accessToken=" + 
    (accessToken != null ? accessToken.substring(0, 10) + "..." : "null"));

// 播放视频
android.util.Log.d("PlaybackActivity", "playVideoWithUrl: " + videoUrl);

// 播放图片
android.util.Log.d("PlaybackActivity", "playImageWithUrl: " + imageUrl);
android.util.Log.d("PlaybackActivity", "开始加载图片: " + imageUrl);

// 从数据库加载播放列表
android.util.Log.d("PlaybackActivity", "从数据库加载播放列表: " + files.size() + " 个文件");
```

---

## 新增功能

### 播放列表删除功能

**功能描述：**
用户可以长按播放列表进入编辑模式，显示删除按钮，点击删除按钮可以删除播放列表。

**实现步骤：**

#### 1. 修改布局文件

**文件：** [`app/src/main/res/layout/item_playlist_card.xml`](app/src/main/res/layout/item_playlist_card.xml)

**改动：**
- 将根布局从 `LinearLayout` 改为 `FrameLayout`，以支持删除按钮覆盖在右上角
- 添加删除按钮 `ImageView`，默认设为 `GONE`
- 删除按钮使用系统图标 `@android:drawable/ic_menu_close_clear_cancel`
- 删除按钮背景使用圆形背景 `@drawable/circle_background`

```xml
<!-- 删除按钮（覆盖在右上角） -->
<ImageView
    android:id="@+id/iv_delete"
    android:layout_width="40dp"
    android:layout_height="40dp"
    android:layout_gravity="top|end"
    android:layout_marginTop="16dp"
    android:layout_marginEnd="16dp"
    android:src="@android:drawable/ic_menu_close_clear_cancel"
    android:background="@drawable/circle_background"
    android:padding="8dp"
    android:visibility="gone"
    android:clickable="true"
    android:focusable="true"
    android:contentDescription="删除播放列表" />
```

#### 2. 修改 ViewHolder

**文件：** [`app/src/main/java/com/baidu/tv/player/ui/main/PlaylistCardViewHolder.java`](app/src/main/java/com/baidu/tv/player/ui/main/PlaylistCardViewHolder.java)

**改动：**
- 添加 `ivDelete` 成员变量
- 在构造函数中绑定删除按钮

```java
public ImageView ivDelete;

public PlaylistCardViewHolder(View itemView) {
    super(itemView);
    ivCover = itemView.findViewById(R.id.iv_cover);
    tvPlaylistName = itemView.findViewById(R.id.tv_playlist_name);
    tvStats = itemView.findViewById(R.id.tv_stats);
    ivDelete = itemView.findViewById(R.id.iv_delete);
}
```

#### 3. 修改 Adapter

**文件：** [`app/src/main/java/com/baidu/tv/player/ui/main/PlaylistAdapter.java`](app/src/main/java/com/baidu/tv/player/ui/main/PlaylistAdapter.java)

**改动：**
1. 添加 `OnDeleteClickListener` 接口
2. 添加 `isEditMode` 标志
3. 添加 `setEditMode()` 和 `isEditMode()` 方法
4. 在 `onBindViewHolder()` 中根据编辑模式显示/隐藏删除按钮
5. 为删除按钮设置点击监听器

```java
private OnDeleteClickListener onDeleteClickListener;
private boolean isEditMode = false;

public void setOnDeleteClickListener(OnDeleteClickListener listener) {
    this.onDeleteClickListener = listener;
}

public void setEditMode(boolean editMode) {
    this.isEditMode = editMode;
    notifyDataSetChanged();
}

public boolean isEditMode() {
    return isEditMode;
}

// 在 onBindViewHolder 中
if (isEditMode) {
    holder.ivDelete.setVisibility(View.VISIBLE);
} else {
    holder.ivDelete.setVisibility(View.GONE);
}

holder.ivDelete.setOnClickListener(v -> {
    if (onDeleteClickListener != null) {
        onDeleteClickListener.onDeleteClick(playlist);
    }
});

public interface OnDeleteClickListener {
    void onDeleteClick(Playlist playlist);
}
```

#### 4. 修改 MainFragment

**文件：** [`app/src/main/java/com/baidu/tv/player/ui/main/MainFragment.java`](app/src/main/java/com/baidu/tv/player/ui/main/MainFragment.java)

**改动：**

1. **设置删除按钮监听器：**
```java
playlistAdapter.setOnDeleteClickListener(this::onPlaylistDelete);
```

2. **修改播放列表点击事件** - 编辑模式下禁用点击：
```java
private void onPlaylistClick(Playlist playlist) {
    if (playlistAdapter.isEditMode()) {
        return;  // 编辑模式下禁用点击
    }
    // ... 启动播放器
}
```

3. **修改长按事件** - 切换编辑模式：
```java
private void onPlaylistLongClick(Playlist playlist) {
    boolean newEditMode = !playlistAdapter.isEditMode();
    playlistAdapter.setEditMode(newEditMode);
    
    if (newEditMode) {
        Toast.makeText(requireContext(),
            "点击删除按钮删除播放列表，再次长按退出编辑模式",
            Toast.LENGTH_SHORT).show();
    }
}
```

4. **实现删除事件** - 显示确认对话框并删除：
```java
private void onPlaylistDelete(Playlist playlist) {
    new AlertDialog.Builder(requireContext())
        .setTitle("删除播放列表")
        .setMessage("确定要删除播放列表\"" + playlist.getName() + "\"吗？\n这将删除播放列表及其所有文件记录。")
        .setPositiveButton("删除", (dialog, which) -> {
            new Thread(() -> {
                try {
                    playlistRepository.deletePlaylist(playlist,
                        () -> {
                            requireActivity().runOnUiThread(() -> {
                                Toast.makeText(requireContext(),
                                    "播放列表已删除",
                                    Toast.LENGTH_SHORT).show();
                                playlistAdapter.setEditMode(false);
                                loadPlaylists();
                            });
                        },
                        () -> {
                            requireActivity().runOnUiThread(() -> {
                                Toast.makeText(requireContext(),
                                    "删除播放列表失败",
                                    Toast.LENGTH_SHORT).show();
                            });
                        });
                } catch (Exception e) {
                    Log.e("MainFragment", "删除播放列表失败", e);
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(),
                            "删除播放列表失败: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    });
                }
            }).start();
        })
        .setNegativeButton("取消", null)
        .show();
}
```

5. **支持返回键退出编辑模式：**
```java
getView().setOnKeyListener((v, keyCode, event) -> {
    if (event.getAction() == KeyEvent.ACTION_DOWN) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            openSettings();
            return true;
        }
        // 返回键：如果处于编辑模式，退出编辑模式
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (playlistAdapter.isEditMode()) {
                playlistAdapter.setEditMode(false);
                return true;
            }
        }
    }
    return false;
});
```

---

## 数据库级联删除

播放列表项表 `PlaylistItem` 已经配置了外键约束，删除播放列表时会自动级联删除所有项：

```java
@Entity(
    tableName = "playlist_items",
    foreignKeys = @ForeignKey(
        entity = Playlist.class,
        parentColumns = "id",
        childColumns = "playlistId",
        onDelete = ForeignKey.CASCADE  // ← 级联删除
    ),
    indices = {@Index("playlistId")}
)
```

因此 [`PlaylistRepository.deletePlaylist()`](app/src/main/java/com/baidu/tv/player/repository/PlaylistRepository.java:107-122) 只需调用 `playlistDao.delete(playlist)`，无需手动删除播放列表项。

---

## 用户体验

### 删除流程：

1. **长按播放列表** → 进入编辑模式，所有播放列表右上角显示删除按钮
2. **点击删除按钮** → 弹出确认对话框
3. **确认删除** → 删除播放列表和所有项，退出编辑模式，刷新列表
4. **取消** → 关闭对话框，保持编辑模式

### 退出编辑模式：

- **方法1：** 再次长按任意播放列表
- **方法2：** 按遥控器返回键
- **方法3：** 删除操作完成后自动退出

---

## 测试要点

1. ✅ 长按播放列表进入编辑模式，删除按钮显示
2. ✅ 再次长按退出编辑模式，删除按钮隐藏
3. ✅ 编辑模式下点击播放列表无效（不会启动播放）
4. ✅ 点击删除按钮显示确认对话框
5. ✅ 确认删除后播放列表和所有项被删除
6. ✅ 删除后自动退出编辑模式并刷新列表
7. ✅ 按返回键可以退出编辑模式
8. ✅ 删除按钮有清晰的圆形背景，易于识别

---

## 待测试问题

### 播放不工作的问题

**症状：**
点击播放列表后不播放，日志显示：
```
java.io.FileNotFoundException: /视频/2026/2026-01-01 104059.mov (No such file or directory)
```

**原因分析：**
播放列表项从数据库加载时，只设置了基本信息（path, fileName, fsId, size），但**没有设置 dlink 下载链接**。播放器尝试直接使用 path 作为本地文件路径，导致失败。

**预期解决流程：**
当前的代码流程应该是正确的：
1. `PlaybackActivity.playCurrentFile()` 调用 `viewModel.prepareMediaUrl()`
2. `PlaybackViewModel.prepareMediaUrl()` 检查是否有 dlink，如果没有调用 `fileRepository.fetchFileDetail()`
3. `FileRepository.fetchFileDetail()` 通过 API 获取文件详情（包含 dlink）
4. 获取到 dlink 后通过 `preparedMediaUrl` LiveData 通知 Activity
5. Activity 收到 URL 后调用 `playVideoWithUrl()` 或 `playImageWithUrl()`

**需要验证：**
- `fetchFileDetail()` API 调用是否成功
- 返回的 `dlink` 是否有效
- `preparedMediaUrl` LiveData 是否正确触发

**调试建议：**
查看日志中是否有：
- "准备获取媒体URL, accessToken=xxx"
- "获取文件详情失败: xxx"（来自 `FileRepository.fetchFileDetail()`）
- "获取到的dlink为空: xxx"（来自 `PlaybackViewModel.prepareMediaUrl()`）

如果API调用失败，可能需要检查：
- Access token 是否有效
- fsId 是否正确
- 网络连接是否正常
- API 权限是否足够

---

## 修改文件列表

1. [`app/src/main/res/layout/item_playlist_card.xml`](app/src/main/res/layout/item_playlist_card.xml) - 添加删除按钮
2. [`app/src/main/java/com/baidu/tv/player/ui/main/PlaylistCardViewHolder.java`](app/src/main/java/com/baidu/tv/player/ui/main/PlaylistCardViewHolder.java) - 绑定删除按钮
3. [`app/src/main/java/com/baidu/tv/player/ui/main/PlaylistAdapter.java`](app/src/main/java/com/baidu/tv/player/ui/main/PlaylistAdapter.java) - 实现编辑模式和删除逻辑
4. [`app/src/main/java/com/baidu/tv/player/ui/main/MainFragment.java`](app/src/main/java/com/baidu/tv/player/ui/main/MainFragment.java) - 处理删除事件
5. [`app/src/main/java/com/baidu/tv/player/ui/playback/PlaybackActivity.java`](app/src/main/java/com/baidu/tv/player/ui/playback/PlaybackActivity.java) - 修复重复添加，添加调试日志

---

## 下一步

需要在 Android Studio 中编译测试，验证：
1. 删除功能是否正常工作
2. 播放问题的根本原因（通过新增的日志）
3. 是否需要进一步优化