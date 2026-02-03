package com.baidu.tv.player.repository;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.baidu.tv.player.model.FileInfo;
import com.baidu.tv.player.model.FileListResponse;
import com.baidu.tv.player.network.ApiConstants;
import com.baidu.tv.player.network.BaiduPanService;
import com.baidu.tv.player.network.RetrofitClient;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 文件数据仓库
 */
public class FileRepository {
    private static final String TAG = "FileRepository";
    private static FileRepository instance;
    private BaiduPanService apiService;

    private FileRepository() {
        apiService = RetrofitClient.getPanApiInstance().create(BaiduPanService.class);
    }

    public static synchronized FileRepository getInstance() {
        if (instance == null) {
            instance = new FileRepository();
        }
        return instance;
    }

    /**
     * 获取文件列表（智能分页，最多加载前5页避免内存溢出）
     */
    public LiveData<List<FileInfo>> getFileList(String accessToken, String dirPath, int mediaType) {
        MutableLiveData<List<FileInfo>> data = new MutableLiveData<>();
        
        Log.d(TAG, "开始获取文件列表: dirPath=" + dirPath + ", mediaType=" + mediaType);
        
        // 智能分页：最多加载前5页（5000个文件）避免内存溢出
        fetchPagesWithLimit(accessToken, dirPath, 0, new ArrayList<>(), 5, new FetchPagesCallback() {
            @Override
            public void onSuccess(List<FileInfo> allFiles, boolean hasMore) {
                Log.d(TAG, "获取到文件数量: " + allFiles.size() + ", 还有更多: " + hasMore);
                List<FileInfo> filteredList = filterFiles(allFiles, mediaType);
                Log.d(TAG, "过滤后文件数量: " + filteredList.size());
                data.setValue(filteredList);
                
                if (hasMore) {
                    Log.w(TAG, "目录包含超过5000个文件，仅显示前5000个以避免内存溢出");
                }
            }

            @Override
            public void onFailure(String error) {
                Log.e(TAG, "获取文件列表失败: " + error);
                data.setValue(new ArrayList<>());
            }
        });
        
        return data;
    }

    /**
     * 分页回调接口
     */
    private interface FetchPagesCallback {
        void onSuccess(List<FileInfo> allFiles, boolean hasMore);
        void onFailure(String error);
    }

    /**
     * 文件列表回调接口
     */
    public interface FileListCallback {
        void onSuccess(List<FileInfo> files);
        void onFailure(String error);
    }

    /**
     * 非递归获取单个目录的文件列表（回调方式）
     * 只获取当前目录的文件，不递归子目录
     */
    public void fetchFilesNonRecursive(String accessToken, String dirPath, final FileListCallback callback) {
        Log.d(TAG, "fetchFilesNonRecursive开始: dirPath=" + dirPath);
        
        // 获取所有文件，不限制页数
        fetchPagesWithLimit(accessToken, dirPath, 0, new ArrayList<>(), Integer.MAX_VALUE, new FetchPagesCallback() {
            @Override
            public void onSuccess(List<FileInfo> allFiles, boolean hasMore) {
                Log.d(TAG, "fetchFilesNonRecursive完成: 文件数=" + allFiles.size());
                
                // 打印前5个文件的详细信息
                for (int i = 0; i < Math.min(5, allFiles.size()); i++) {
                    FileInfo f = allFiles.get(i);
                    Log.d(TAG, "  文件" + i + ": name=" + f.getServerFilename() +
                        ", isDir=" + f.isDirectory() +
                        ", category=" + f.getCategory() +
                        ", isImage=" + f.isImage() +
                        ", isVideo=" + f.isVideo());
                }
                
                callback.onSuccess(allFiles);
            }

            @Override
            public void onFailure(String error) {
                Log.e(TAG, "fetchFilesNonRecursive失败: " + error);
                callback.onFailure(error);
            }
        });
    }
    
    /**
     * 递归获取文件列表（回调方式，用于后台任务组合）
     * 使用手动递归实现，避免使用不稳定的xpan/multimedia?method=listall接口
     */
    public void fetchFilesRecursive(String accessToken, String dirPath, final FileListCallback callback) {
        Log.d(TAG, "fetchFilesRecursive开始: dirPath=" + dirPath);
        
        // 使用手动递归，最多处理100个目录，避免无限递归
        manualRecursiveFetch(accessToken, dirPath, new ArrayList<>(), new ArrayList<>(), 0, 100, new FetchPagesCallback() {
            @Override
            public void onSuccess(List<FileInfo> allFiles, boolean hasMore) {
                // 添加调试日志
                Log.d(TAG, "fetchFilesRecursive完成: 总文件数=" + allFiles.size());
                
                // 打印前5个文件的详细信息
                for (int i = 0; i < Math.min(5, allFiles.size()); i++) {
                    FileInfo f = allFiles.get(i);
                    Log.d(TAG, "  文件" + i + ": name=" + f.getServerFilename() +
                        ", isDir=" + f.isDirectory() +
                        ", category=" + f.getCategory() +
                        ", isImage=" + f.isImage() +
                        ", isVideo=" + f.isVideo());
                }
                
                callback.onSuccess(allFiles);
            }

            @Override
            public void onFailure(String error) {
                Log.e(TAG, "fetchFilesRecursive失败: " + error);
                callback.onFailure(error);
            }
        });
    }
    
    /**
     * 手动递归获取文件列表（深度优先遍历）
     * @param accessToken 访问令牌
     * @param currentPath 当前路径
     * @param allFiles 累积的所有文件
     * @param pendingDirs 待处理的目录队列
     * @param processedDirCount 已处理目录数
     * @param maxDirs 最大处理目录数（防止无限递归）
     * @param callback 回调
     */
    private void manualRecursiveFetch(String accessToken, String currentPath,
                                     List<FileInfo> allFiles, List<String> pendingDirs,
                                     int processedDirCount, int maxDirs,
                                     FetchPagesCallback callback) {
        // 防止无限递归
        if (processedDirCount >= maxDirs) {
            Log.w(TAG, "已达到最大目录处理数限制: " + maxDirs);
            callback.onSuccess(allFiles, true);
            return;
        }
        
        Log.d(TAG, "手动递归处理目录: " + currentPath + " (已处理: " + processedDirCount + "/" + maxDirs + ")");
        
        // 获取当前目录的文件列表（最多5页，5000个文件）
        fetchPagesWithLimit(accessToken, currentPath, 0, new ArrayList<>(), 5, new FetchPagesCallback() {
            @Override
            public void onSuccess(List<FileInfo> files, boolean hasMore) {
                Log.d(TAG, "目录 " + currentPath + " 包含 " + files.size() + " 个项目");
                
                // 分离文件和目录
                List<String> subDirs = new ArrayList<>();
                for (FileInfo file : files) {
                    if (file.isDirectory()) {
                        subDirs.add(file.getPath());
                    } else {
                        allFiles.add(file);
                    }
                }
                
                Log.d(TAG, "目录 " + currentPath + ": 文件数=" + (files.size() - subDirs.size()) + ", 子目录数=" + subDirs.size());
                
                // 将子目录添加到待处理队列
                pendingDirs.addAll(subDirs);
                
                // 处理下一个目录
                processNextDirectory(accessToken, allFiles, pendingDirs, processedDirCount + 1, maxDirs, callback);
            }

            @Override
            public void onFailure(String error) {
                String errorMsg = error != null ? error : "未知错误";
                Log.e(TAG, "获取目录 " + currentPath + " 失败: " + errorMsg);
                // 即使当前目录失败，继续处理其他目录
                processNextDirectory(accessToken, allFiles, pendingDirs, processedDirCount + 1, maxDirs, callback);
            }
        });
    }
    
    /**
     * 处理下一个待处理的目录
     */
    private void processNextDirectory(String accessToken, List<FileInfo> allFiles,
                                     List<String> pendingDirs, int processedDirCount,
                                     int maxDirs, FetchPagesCallback callback) {
        if (pendingDirs.isEmpty()) {
            // 所有目录已处理完成
            Log.d(TAG, "手动递归完成: 总文件数=" + allFiles.size() + ", 已处理目录数=" + processedDirCount);
            callback.onSuccess(allFiles, false);
        } else {
            // 处理下一个目录
            String nextDir = pendingDirs.remove(0);
            manualRecursiveFetch(accessToken, nextDir, allFiles, pendingDirs, processedDirCount, maxDirs, callback);
        }
    }

    /**
     * 智能分页加载（限制最大页数避免内存溢出）
     */
    private void fetchPagesWithLimit(String accessToken, String dirPath, int start,
                                     List<FileInfo> accumulatedFiles, int remainingPages,
                                     FetchPagesCallback callback) {
        final int LIMIT = 1000;
        
        if (remainingPages <= 0) {
            Log.w(TAG, "达到最大页数限制，停止加载");
            callback.onSuccess(accumulatedFiles, true);
            return;
        }
        
        Log.d(TAG, "获取第 " + (start / LIMIT + 1) + " 页，start=" + start + ", 剩余页数=" + remainingPages);
        
        Call<FileListResponse> call = apiService.getFileList(
                "list",
                dirPath,
                "name",  // 按名称排序
                0,       // desc=0 表示升序
                start,   // 动态start参数
                LIMIT,   // limit=1000
                1,       // web=1
                0,       // folder=0
                accessToken
        );
        
        call.enqueue(new Callback<FileListResponse>() {
            @Override
            public void onResponse(Call<FileListResponse> call, Response<FileListResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    FileListResponse fileListResponse = response.body();
                    
                    if (fileListResponse.isSuccess()) {
                        List<FileInfo> fileList = fileListResponse.getList();
                        int currentPageSize = fileList != null ? fileList.size() : 0;
                        Log.d(TAG, "第 " + (start / LIMIT + 1) + " 页获取到 " + currentPageSize + " 个文件");
                        
                        if (fileList != null && !fileList.isEmpty()) {
                            accumulatedFiles.addAll(fileList);
                            
                            // 如果当前页返回的文件数量等于LIMIT，说明可能还有下一页
                            if (currentPageSize == LIMIT) {
                                // 递归获取下一页
                                fetchPagesWithLimit(accessToken, dirPath, start + LIMIT,
                                                  accumulatedFiles, remainingPages - 1, callback);
                            } else {
                                // 没有更多页了，返回所有累积的文件
                                Log.d(TAG, "所有分页获取完成，总共 " + accumulatedFiles.size() + " 个文件");
                                callback.onSuccess(accumulatedFiles, false);
                            }
                        } else {
                            // 当前页没有文件，返回累积的所有文件
                            Log.d(TAG, "当前页无文件，总共 " + accumulatedFiles.size() + " 个文件");
                            callback.onSuccess(accumulatedFiles, false);
                        }
                    } else {
                        String errMsg = fileListResponse.getErrmsg();
                        if (errMsg == null || errMsg.isEmpty()) {
                            errMsg = "API返回错误，errno=" + fileListResponse.getErrno();
                        }
                        Log.e(TAG, "API返回失败: " + errMsg + ", errno=" + fileListResponse.getErrno());
                        callback.onFailure(errMsg);
                    }
                } else {
                    String errorMsg = response.code() + " - " + response.message();
                    try {
                        if (response.errorBody() != null) {
                            errorMsg += ": " + response.errorBody().string();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "读取错误响应体失败", e);
                    }
                    callback.onFailure(errorMsg);
                }
            }

            @Override
            public void onFailure(Call<FileListResponse> call, Throwable t) {
                String errorMsg = t.getMessage();
                if (errorMsg == null) {
                    errorMsg = "网络请求失败: " + t.getClass().getSimpleName();
                }
                Log.e(TAG, "获取文件详情失败: " + errorMsg, t);
                callback.onFailure(errorMsg);
            }
        });
    }

    /**
     * 递归获取文件列表（智能分页，最多加载前10页避免内存溢出）
     */
    public LiveData<List<FileInfo>> getFileListRecursive(String accessToken, String dirPath, int mediaType) {
        MutableLiveData<List<FileInfo>> data = new MutableLiveData<>();
        
        Log.d(TAG, "开始递归获取文件列表: dirPath=" + dirPath + ", mediaType=" + mediaType);
        
        // 获取所有文件，不限制页数
        fetchPagesRecursiveWithLimit(accessToken, dirPath, 0, new ArrayList<>(), Integer.MAX_VALUE, new FetchPagesCallback() {
            @Override
            public void onSuccess(List<FileInfo> allFiles, boolean hasMore) {
                Log.d(TAG, "递归获取到文件数量: " + allFiles.size() + ", 还有更多: " + hasMore);
                List<FileInfo> filteredList = filterFiles(allFiles, mediaType);
                Log.d(TAG, "递归过滤后文件数量: " + filteredList.size());
                data.setValue(filteredList);
            }

            @Override
            public void onFailure(String error) {
                Log.e(TAG, "递归获取文件列表失败: " + error);
                data.setValue(new ArrayList<>());
            }
        });
        
        return data;
    }

    /**
     * 智能分页加载（递归模式，限制最大页数避免内存溢出）
     */
    private void fetchPagesRecursiveWithLimit(String accessToken, String dirPath, int start,
                                             List<FileInfo> accumulatedFiles, int remainingPages,
                                             FetchPagesCallback callback) {
        final int LIMIT = 1000;
        
        if (remainingPages <= 0) {
            Log.w(TAG, "递归达到最大页数限制，停止加载");
            callback.onSuccess(accumulatedFiles, true);
            return;
        }
        
        Log.d(TAG, "递归获取第 " + (start / LIMIT + 1) + " 页，start=" + start + ", 剩余页数=" + remainingPages);
        
        Call<FileListResponse> call = apiService.getFileListRecursive(
                "listall",
                dirPath,
                "name",  // 按名称排序
                0,       // desc=0 表示升序
                LIMIT,   // limit=1000
                1,       // recursion=1
                accessToken
        );
        
        call.enqueue(new Callback<FileListResponse>() {
            @Override
            public void onResponse(Call<FileListResponse> call, Response<FileListResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    FileListResponse fileListResponse = response.body();
                    
                    if (fileListResponse.isSuccess()) {
                        List<FileInfo> fileList = fileListResponse.getList();
                        int currentPageSize = fileList != null ? fileList.size() : 0;
                        Log.d(TAG, "递归第 " + (start / LIMIT + 1) + " 页获取到 " + currentPageSize + " 个文件");
                        
                        if (fileList != null && !fileList.isEmpty()) {
                            accumulatedFiles.addAll(fileList);
                            
                            // 如果当前页返回的文件数量等于LIMIT，说明可能还有下一页
                            if (currentPageSize == LIMIT) {
                                // 递归获取下一页
                                fetchPagesRecursiveWithLimit(accessToken, dirPath, start + LIMIT,
                                                           accumulatedFiles, remainingPages - 1, callback);
                            } else {
                                // 没有更多页了，返回所有累积的文件
                                Log.d(TAG, "所有递归分页获取完成，总共 " + accumulatedFiles.size() + " 个文件");
                                callback.onSuccess(accumulatedFiles, false);
                            }
                        } else {
                            // 当前页没有文件，返回累积的所有文件
                            Log.d(TAG, "递归当前页无文件，总共 " + accumulatedFiles.size() + " 个文件");
                            callback.onSuccess(accumulatedFiles, false);
                        }
                    } else {
                        callback.onFailure(fileListResponse.getErrmsg());
                    }
                } else {
                    String errorMsg = response.code() + " - " + response.message();
                    try {
                        if (response.errorBody() != null) {
                            errorMsg += ": " + response.errorBody().string();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "读取错误响应体失败", e);
                    }
                    callback.onFailure(errorMsg);
                }
            }

            @Override
            public void onFailure(Call<FileListResponse> call, Throwable t) {
                callback.onFailure(t.getMessage());
            }
        });
    }

    /**
     * 文件详情回调接口
     */
    public interface FileDetailCallback {
        void onSuccess(FileInfo fileInfo);
        void onFailure(String error);
    }

    /**
     * 获取单个文件详情（包含dlink）
     */
    public void fetchFileDetail(String accessToken, long fsId, final FileDetailCallback callback) {
        // fsids需要是json数组格式: [12345]
        String fsids = "[" + fsId + "]";
        
        Call<FileListResponse> call = apiService.getFileInfo(
                "filemetas",
                fsids,
                1, // dlink=1
                accessToken
        );
        
        call.enqueue(new Callback<FileListResponse>() {
            @Override
            public void onResponse(Call<FileListResponse> call, Response<FileListResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    FileListResponse fileListResponse = response.body();
                    if (fileListResponse.isSuccess() && fileListResponse.getList() != null && !fileListResponse.getList().isEmpty()) {
                        callback.onSuccess(fileListResponse.getList().get(0));
                    } else {
                        callback.onFailure("获取文件详情失败: " + fileListResponse.getErrmsg());
                    }
                } else {
                    callback.onFailure("获取文件详情失败: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<FileListResponse> call, Throwable t) {
                callback.onFailure("获取文件详情失败: " + t.getMessage());
            }
        });
    }

    /**
     * 根据媒体类型过滤文件
     */
    private List<FileInfo> filterFiles(List<FileInfo> files, int mediaType) {
        if (files == null || files.isEmpty()) {
            return new ArrayList<>();
        }

        List<FileInfo> filteredList = new ArrayList<>();
        for (FileInfo file : files) {
            // 如果是目录，直接添加
            if (file.isDirectory()) {
                filteredList.add(file);
                continue;
            }

            // 根据媒体类型过滤（使用MediaType枚举的值）
            switch (mediaType) {
                case 1: // 图片
                    if (file.isImage()) {
                        filteredList.add(file);
                    }
                    break;
                case 2: // 视频
                    if (file.isVideo()) {
                        filteredList.add(file);
                    }
                    break;
                case 3: // 全部（图片+视频）
                    if (file.isImage() || file.isVideo()) {
                        filteredList.add(file);
                    }
                    break;
                default: // 默认全部
                    if (file.isImage() || file.isVideo()) {
                        filteredList.add(file);
                    }
                    break;
            }
        }
        
        return filteredList;
    }
}