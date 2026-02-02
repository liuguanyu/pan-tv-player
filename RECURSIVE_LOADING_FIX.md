# 递归加载功能修复说明

## 问题描述
用户反馈在创建播放列表时，即使选择了包含子目录的文件夹，也可能出现"未找到任何媒体文件"的提示。

## 根本原因
1. **递归开关状态未正确检查**：在确认选择时，无论递归开关是否开启，都尝试进行递归扫描
2. **用户体验不佳**：当递归开关关闭且选中目录只包含子目录时，提示信息不明确
3. **API错误信息为null**：当API返回失败时，`errmsg`字段可能为null，导致错误信息不明确
4. **访问令牌不一致**：多选确认时使用了过期或错误的访问令牌

## 4. 统一访问令牌获取
在`FileBrowserFragment.java`的`onConfirmSelection()`方法中，将访问令牌的获取方式从`PreferenceUtils.getAccessToken()`改为使用统一的`BaiduAuthService.getInstance().getAccessToken()`：

```java
// 使用统一的认证服务获取访问令牌，确保令牌有效
com.baidu.tv.player.auth.BaiduAuthService authService =
    com.baidu.tv.player.auth.BaiduAuthService.getInstance(requireContext());
String accessToken = authService.getAccessToken();
```

这样可以确保多选确认时使用的访问令牌与快速浏览时一致，避免因令牌过期或不一致导致的`errno=-6`错误。

## 修复内容

### 1. 递归开关状态检查
在`FileBrowserFragment.java`的`onConfirmSelection()`方法中添加了对递归开关状态的检查：

```java
// 获取递归加载开关状态
boolean isRecursiveEnabled = viewModel.getIsRecursive().getValue() != null 
    ? viewModel.getIsRecursive().getValue() : false;
```

根据开关状态选择不同的文件获取方式：
- **开启**：使用`fetchFilesRecursive()`进行深度递归扫描
- **关闭**：使用`fetchFilesNonRecursive()`只扫描当前目录

### 2. 优化提示信息
当扫描结果为空但检测到有子目录时，提供更明确的提示：

```java
if (allMediaFiles.isEmpty()) {
    requireActivity().runOnUiThread(() -> {
        String msg = "未找到任何媒体文件";
        if (!isRecursiveEnabled && hasIgnoredSubDirs[0]) {
            msg = "未找到文件(仅含子目录)，请开启递归加载";
        }
        android.widget.Toast.makeText(requireContext(),
            msg,
            android.widget.Toast.LENGTH_LONG).show();
    });
    return;
}
```

### 3. 添加非递归获取方法
在`FileRepository.java`中添加了`fetchFilesNonRecursive()`方法，用于只获取当前目录的文件而不递归子目录。

## 测试场景
1. 选择只包含子目录的文件夹，递归开关关闭 → 提示"未找到文件(仅含子目录)，请开启递归加载"
2. 选择只包含子目录的文件夹，递归开关开启 → 正常递归扫描并创建播放列表
3. 选择包含直接媒体文件的文件夹，递归开关关闭 → 正常创建播放列表
4. 选择包含直接媒体文件的文件夹，递归开关开启 → 正常递归扫描并创建播放列表

## 3. API错误信息null检查
在`FileRepository.java`的`fetchPagesWithLimit()`方法中，当API返回失败时，添加了对`errmsg`字段的null检查：

```java
} else {
    String errMsg = fileListResponse.getErrmsg();
    if (errMsg == null || errMsg.isEmpty()) {
        errMsg = "API返回错误，errno=" + fileListResponse.getErrno();
    }
    Log.e(TAG, "API返回失败: " + errMsg + ", errno=" + fileListResponse.getErrno());
    callback.onFailure(errMsg);
}
```

这样可以确保即使API返回的`errmsg`为null，也能提供有意义的错误信息（包含errno）。

## 文件变更
- `app/src/main/java/com/baidu/tv/player/ui/filebrowser/FileBrowserFragment.java`
- `app/src/main/java/com/baidu/tv/player/repository/FileRepository.java`
- `app/src/main/java/com/baidu/tv/player/ui/filebrowser/FileBrowserViewModel.java`
