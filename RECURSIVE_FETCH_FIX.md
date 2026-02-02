# 递归获取文件逻辑修复

## 问题描述

用户在使用多选模式创建播放列表时，后台日志报错：
```
fetchFilesRecursive失败: 400 - Bad Request: {"errmsg":"Invalid Bduss","errno":-6,"request_id":"..."}
```

## 原因分析

1. **接口权限问题**：原本使用的 `xpan/multimedia?method=listall` 接口可能需要特定的Scope权限或Cookie (BDUSS)，仅凭OAuth2 AccessToken可能权限不足或不稳定。
2. **Token有效性**：虽然用户能浏览文件（说明AccessToken有效），但该特定接口报错，表明问题出在接口本身的要求上。

## 修复方案

废弃 `fetchPagesRecursiveWithLimit` 方法（调用 `listall` 接口），改用手动递归实现 `manualRecursiveFetch`。

### 实现逻辑

1. **使用稳定接口**：使用 `fetchPagesWithLimit` (调用 `xpan/file?method=list`)，这是标准的文件列表接口，已验证工作正常。
2. **深度优先遍历**：
   - 获取当前目录内容
   - 将文件加入结果列表
   - 将子目录加入待处理队列
   - 递归处理下一个目录
3. **安全限制**：
   - 限制最大处理目录数为100个，防止无限递归或耗时过长。
   - 每个目录最多获取5页（5000个文件）。

### 代码变更

`FileRepository.java`:

```java
public void fetchFilesRecursive(String accessToken, String dirPath, final FileListCallback callback) {
    // 改为调用 manualRecursiveFetch
    manualRecursiveFetch(accessToken, dirPath, new ArrayList<>(), new ArrayList<>(), 0, 100, new FetchPagesCallback() { ... });
}

private void manualRecursiveFetch(...) {
    // 递归逻辑实现
    // ...
}
```

## 预期效果

1. ✅ 不再报 "Invalid Bduss" 错误
2. ✅ 能够正确递归获取所有子目录下的文件
3. ✅ "未找到任何媒体文件" 的问题应该得到彻底解决

## 测试建议

1. 重新编译应用
2. 在首页点击"创建播放列表"
3. 选择一个包含多层子目录和媒体文件的文件夹
4. 点击"确认选择"
5. 验证播放列表是否成功创建，且包含所有子目录中的文件