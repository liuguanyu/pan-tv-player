package com.baidu.tv.player.ui.filebrowser;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.baidu.tv.player.auth.BaiduAuthService;
import com.baidu.tv.player.model.FileInfo;
import com.baidu.tv.player.repository.FileRepository;
import com.baidu.tv.player.utils.PreferenceUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.Collections;
import java.util.Comparator;

/**
 * 文件浏览ViewModel
 */
public class FileBrowserViewModel extends AndroidViewModel {
    public enum SortMode {
        NAME_ASC(0),   // 文件名正序
        NAME_DESC(1),  // 文件名倒序
        DATE_ASC(2),   // 日期正序
        DATE_DESC(3);  // 日期倒序
        
        private final int value;
        
        SortMode(int value) {
            this.value = value;
        }
        
        public int toInt() {
            return value;
        }
        
        public static SortMode fromInt(int value) {
            for (SortMode mode : values()) {
                if (mode.value == value) {
                    return mode;
                }
            }
            return NAME_ASC; // 默认值
        }
    }

    private FileRepository repository;
    private MutableLiveData<List<FileInfo>> fileList;
    private MutableLiveData<Boolean> isLoading;
    private MutableLiveData<String> errorMessage;
    private MutableLiveData<String> currentPath;
    private MutableLiveData<SortMode> sortMode;
    
    // 目录导航栈
    private Stack<String> pathStack;
    
    // 当前媒体类型：0-图片，1-视频，2-全部
    private int mediaType;
    
    // 是否递归
    private boolean isRecursive;

    public FileBrowserViewModel(@NonNull Application application) {
        super(application);
        repository = FileRepository.getInstance();
        fileList = new MutableLiveData<>();
        isLoading = new MutableLiveData<>();
        errorMessage = new MutableLiveData<>();
        currentPath = new MutableLiveData<>();
        // 从SharedPreferences加载排序模式
        int savedSortMode = PreferenceUtils.getFileSortMode(application);
        sortMode = new MutableLiveData<>(SortMode.fromInt(savedSortMode));
        
        pathStack = new Stack<>();
        mediaType = 2; // 默认全部
        isRecursive = false;
    }

    public LiveData<SortMode> getSortMode() {
        return sortMode;
    }

    /**
     * 切换排序模式
     */
    public void toggleSortMode() {
        SortMode current = sortMode.getValue();
        SortMode next;
        
        if (current == SortMode.NAME_ASC) {
            next = SortMode.NAME_DESC;
        } else if (current == SortMode.NAME_DESC) {
            next = SortMode.DATE_ASC;
        } else if (current == SortMode.DATE_ASC) {
            next = SortMode.DATE_DESC;
        } else {
            next = SortMode.NAME_ASC;
        }
        
        sortMode.setValue(next);
        
        // 保存排序模式
        PreferenceUtils.saveFileSortMode(getApplication(), next.toInt());
        
        // 如果当前有文件列表，立即重新排序
        List<FileInfo> files = fileList.getValue();
        if (files != null && !files.isEmpty()) {
            sortFileList(files, next);
            fileList.setValue(files);
        }
    }
    
    /**
     * 对文件列表进行排序
     */
    private void sortFileList(List<FileInfo> files, SortMode mode) {
        Collections.sort(files, (f1, f2) -> {
            // 目录永远排在文件前面
            if (f1.isDirectory() && !f2.isDirectory()) return -1;
            if (!f1.isDirectory() && f2.isDirectory()) return 1;
            
            switch (mode) {
                case NAME_ASC:
                    return f1.getServerFilename().compareToIgnoreCase(f2.getServerFilename());
                case NAME_DESC:
                    return f2.getServerFilename().compareToIgnoreCase(f1.getServerFilename());
                case DATE_ASC:
                    return Long.compare(f1.getServerMtime(), f2.getServerMtime());
                case DATE_DESC:
                    return Long.compare(f2.getServerMtime(), f1.getServerMtime());
                default:
                    return 0;
            }
        });
    }
    
    // 添加获取递归状态的LiveData封装，以便Fragment可以观察
    private MutableLiveData<Boolean> isRecursiveLiveData = new MutableLiveData<>(false);
    
    public LiveData<Boolean> getIsRecursive() {
        return isRecursiveLiveData;
    }

    public LiveData<List<FileInfo>> getFileList() {
        return fileList;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public LiveData<String> getCurrentPath() {
        return currentPath;
    }

    /**
     * 设置媒体类型
     */
    public void setMediaType(int mediaType) {
        this.mediaType = mediaType;
    }

    /**
     * 设置是否递归
     */
    public void setRecursive(boolean recursive) {
        isRecursive = recursive;
        isRecursiveLiveData.setValue(recursive);
    }

    /**
     * 加载文件列表
     */
    public void loadFileList(String path) {
        android.util.Log.d("FileBrowserViewModel", "loadFileList: path=" + path + ", mediaType=" + mediaType + ", isRecursive=" + isRecursive);
        
        isLoading.setValue(true);
        errorMessage.setValue(null);
        
        BaiduAuthService authService = BaiduAuthService.getInstance(getApplication());
        boolean isAuthenticated = authService.isAuthenticated();
        android.util.Log.d("FileBrowserViewModel", "isAuthenticated: " + isAuthenticated);
        
        if (!isAuthenticated) {
            errorMessage.setValue("未登录，请先登录");
            isLoading.setValue(false);
            return;
        }
        
        String accessToken = authService.getAccessToken();
        android.util.Log.d("FileBrowserViewModel", "accessToken: " + (accessToken != null ? accessToken.substring(0, Math.min(20, accessToken.length())) + "..." : "null"));

        LiveData<List<FileInfo>> result;
        if (isRecursive) {
            result = repository.getFileListRecursive(accessToken, path, mediaType);
        } else {
            result = repository.getFileList(accessToken, path, mediaType);
        }

        // 观察结果
        result.observeForever(files -> {
            android.util.Log.d("FileBrowserViewModel", "收到文件列表: " + (files != null ? files.size() : 0) + " 个文件");
            if (files != null) {
                sortFileList(files, sortMode.getValue());
            }
            fileList.setValue(files);
            currentPath.setValue(path);
            isLoading.setValue(false);
        });
    }

    /**
     * 进入目录
     */
    public void enterDirectory(String path) {
        pathStack.push(currentPath.getValue());
        loadFileList(path);
    }

    /**
     * 返回上一级
     */
    public void goBack() {
        if (!pathStack.isEmpty()) {
            String parentPath = pathStack.pop();
            loadFileList(parentPath);
        }
    }

    /**
     * 是否可以返回
     */
    public boolean canGoBack() {
        return !pathStack.isEmpty();
    }

    /**
     * 获取选中的文件列表（用于播放）
     */
    public List<FileInfo> getSelectedFiles(List<FileInfo> selectedItems) {
        List<FileInfo> files = new ArrayList<>();
        if (selectedItems != null) {
            for (FileInfo item : selectedItems) {
                if (!item.isDirectory()) {
                    files.add(item);
                }
            }
        }
        return files;
    }
}