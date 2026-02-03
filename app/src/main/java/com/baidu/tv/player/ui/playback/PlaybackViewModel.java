package com.baidu.tv.player.ui.playback;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import android.util.Log;

import com.baidu.tv.player.model.FileInfo;
import com.baidu.tv.player.model.ImageEffect;
import com.baidu.tv.player.model.PlayMode;
import com.baidu.tv.player.model.Playlist;
import com.baidu.tv.player.repository.FileRepository;
import com.baidu.tv.player.repository.PlaylistRepository;
import com.baidu.tv.player.utils.PreferenceUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 播放器ViewModel
 */
public class PlaybackViewModel extends AndroidViewModel {
    private MutableLiveData<List<FileInfo>> playList;
    private MutableLiveData<Integer> currentIndex;
    private MutableLiveData<PlayMode> playMode;
    private MutableLiveData<Boolean> isPlaying;
    private MutableLiveData<String> currentLocation;
    
    // 随机播放的索引列表
    private List<Integer> randomIndices;
    private Random random;
    
    // 播放列表数据库ID（用于更新播放进度）
    private long playlistDatabaseId = -1;
    private PlaylistRepository playlistRepository;
    
    // 图片特效
    private MutableLiveData<ImageEffect> imageEffect;
    private MutableLiveData<Integer> imageDisplayDuration; // 图片展示时长（毫秒）
    private MutableLiveData<Integer> imageTransitionDuration; // 图片过渡时长（毫秒）
    
    // 是否显示地点信息
    private MutableLiveData<Boolean> showLocation;
    
    // 准备好的媒体URL (用于播放)
    private MutableLiveData<String> preparedMediaUrl;
    private FileRepository fileRepository;
    
    // 预加载相关
    private String preloadedDlink = null;
    private int preloadedIndex = -1;

    public PlaybackViewModel(@NonNull Application application) {
        super(application);
        playList = new MutableLiveData<>();
        currentIndex = new MutableLiveData<>(); // 不初始化值，等待setPlayList设置
        playMode = new MutableLiveData<>(PlayMode.fromValue(
                PreferenceUtils.getPlayMode(application)));
        isPlaying = new MutableLiveData<>(false);
        currentLocation = new MutableLiveData<>();
        randomIndices = new ArrayList<>();
        random = new Random();
        
        // 从设置中读取图片特效配置
        imageEffect = new MutableLiveData<>(ImageEffect.fromValue(
                PreferenceUtils.getImageEffect(application)));
        imageDisplayDuration = new MutableLiveData<>(
                PreferenceUtils.getImageDisplayDuration(application));
        imageTransitionDuration = new MutableLiveData<>(
                PreferenceUtils.getImageTransitionDuration(application));
        
        // 从设置中读取是否显示地点
        showLocation = new MutableLiveData<>(
                PreferenceUtils.getShowLocation(application));
                
        preparedMediaUrl = new MutableLiveData<>();
        fileRepository = FileRepository.getInstance();
        playlistRepository = new PlaylistRepository(application);
    }

    public LiveData<List<FileInfo>> getPlayList() {
        return playList;
    }

    public LiveData<Integer> getCurrentIndex() {
        return currentIndex;
    }

    public LiveData<PlayMode> getPlayMode() {
        return playMode;
    }

    public LiveData<Boolean> getIsPlaying() {
        return isPlaying;
    }

    public LiveData<String> getCurrentLocation() {
        return currentLocation;
    }

    public LiveData<ImageEffect> getImageEffect() {
        return imageEffect;
    }

    public LiveData<Integer> getImageDisplayDuration() {
        return imageDisplayDuration;
    }

    public LiveData<Integer> getImageTransitionDuration() {
        return imageTransitionDuration;
    }

    public LiveData<Boolean> getShowLocation() {
        return showLocation;
    }

    public LiveData<String> getPreparedMediaUrl() {
        return preparedMediaUrl;
    }

    /**
     * 准备媒体URL (获取dlink并附加access_token)
     * @param accessToken 用户访问令牌
     * @param file 文件信息
     */
    public void prepareMediaUrl(String accessToken, FileInfo file) {
        // 如果文件已经是目录，则不处理
        if (file.isDirectory()) {
            Log.w("PlaybackViewModel", "尝试准备目录的媒体URL: " + file.getPath());
            return;
        }

        // 1. 检查是否有预加载的dlink
        Integer currentIndexVal = currentIndex.getValue();
        if (currentIndexVal != null && currentIndexVal == preloadedIndex && preloadedDlink != null) {
            Log.d("PlaybackViewModel", "命中预加载缓存，索引: " + preloadedIndex);
            
            String finalUrl = preloadedDlink;
            // 确保token是最新的
            if (finalUrl.contains("access_token=")) {
                // 替换现有token
                finalUrl = finalUrl.replaceAll("access_token=[^&]*", "access_token=" + accessToken);
            } else {
                finalUrl += (finalUrl.contains("?") ? "&" : "?") + "access_token=" + accessToken;
            }
            
            preparedMediaUrl.setValue(finalUrl);
            
            // 清除使用的缓存
            preloadedDlink = null;
            preloadedIndex = -1;
            
            // 触发下一次预加载
            preloadNextFile(accessToken);
            return;
        }

        // 2. 如果dlink已经存在且有效（以http开头），直接使用
        String currentDlink = file.getDlink();
        if (currentDlink != null && !currentDlink.isEmpty() && currentDlink.startsWith("http")) {
            Log.d("PlaybackViewModel", "使用现有的dlink: " + currentDlink);
            String finalUrl = currentDlink;
            if (!finalUrl.contains("access_token=")) {
                finalUrl += (finalUrl.contains("?") ? "&" : "?") + "access_token=" + accessToken;
            }
            preparedMediaUrl.setValue(finalUrl);
            
            // 触发预加载
            preloadNextFile(accessToken);
            return;
        } else {
            if (currentDlink != null) {
                Log.w("PlaybackViewModel", "现有的dlink无效(不是http开头): " + currentDlink);
            }
        }

        // 3. 否则，通过API获取文件详情
        Log.d("PlaybackViewModel", "正在获取文件详情以获取dlink, fsId=" + file.getFsId());
        fileRepository.fetchFileDetail(accessToken, file.getFsId(), new FileRepository.FileDetailCallback() {
            @Override
            public void onSuccess(FileInfo fileInfo) {
                String dlink = fileInfo.getDlink();
                Log.d("PlaybackViewModel", "获取文件详情成功, dlink=" + dlink);
                
                if (dlink != null && !dlink.isEmpty()) {
                    if (!dlink.startsWith("http")) {
                        Log.e("PlaybackViewModel", "API返回的dlink无效(不是http开头): " + dlink);
                        preparedMediaUrl.setValue(null);
                        return;
                    }
                    
                    // 附加access_token到dlink
                    String finalUrl = dlink;
                    if (!finalUrl.contains("access_token=")) {
                        finalUrl += (finalUrl.contains("?") ? "&" : "?") + "access_token=" + accessToken;
                    }
                    Log.d("PlaybackViewModel", "准备播放URL: " + finalUrl);
                    preparedMediaUrl.setValue(finalUrl);
                    
                    // 触发预加载
                    preloadNextFile(accessToken);
                } else {
                    Log.e("PlaybackViewModel", "获取到的dlink为空: " + fileInfo.getPath());
                    preparedMediaUrl.setValue(null);
                }
            }

            @Override
            public void onFailure(String error) {
                Log.e("PlaybackViewModel", "获取文件详情失败: " + error);
                preparedMediaUrl.setValue(null);
            }
        });
    }

    /**
     * 预加载下一个文件
     */
    private void preloadNextFile(String accessToken) {
        List<FileInfo> files = playList.getValue();
        if (files == null || files.isEmpty()) return;
        
        Integer current = currentIndex.getValue();
        if (current == null) return;
        
        // 计算下一个索引
        int nextIndex;
        PlayMode mode = playMode.getValue();
        if (mode == null) mode = PlayMode.SEQUENTIAL;
        
        switch (mode) {
            case SEQUENTIAL:
                nextIndex = (current + 1) % files.size();
                break;
            case REVERSE:
                nextIndex = (current - 1 + files.size()) % files.size();
                break;
            case RANDOM:
                nextIndex = getNextRandomIndex(current);
                break;
            case SINGLE:
                nextIndex = current; // 单曲循环预加载自己
                break;
            default:
                nextIndex = (current + 1) % files.size();
        }
        
        // 如果只有一个文件且不是单曲循环，不需要预加载
        if (files.size() <= 1 && mode != PlayMode.SINGLE) return;
        
        // 如果已经是预加载的索引，跳过
        if (nextIndex == preloadedIndex && preloadedDlink != null) return;
        
        FileInfo nextFile = files.get(nextIndex);
        
        // 如果已经有dlink，不需要请求API，但可以缓存索引
        if (nextFile.getDlink() != null && nextFile.getDlink().startsWith("http")) {
            preloadedDlink = nextFile.getDlink();
            preloadedIndex = nextIndex;
            Log.d("PlaybackViewModel", "预加载完成(使用现有dlink)，索引: " + nextIndex);
            return;
        }
        
        Log.d("PlaybackViewModel", "开始预加载下一个文件，索引: " + nextIndex + ", 文件: " + nextFile.getServerFilename());
        
        // 异步获取详情
        final int targetIndex = nextIndex;
        fileRepository.fetchFileDetail(accessToken, nextFile.getFsId(), new FileRepository.FileDetailCallback() {
            @Override
            public void onSuccess(FileInfo fileInfo) {
                String dlink = fileInfo.getDlink();
                if (dlink != null && !dlink.isEmpty() && dlink.startsWith("http")) {
                    preloadedDlink = dlink;
                    preloadedIndex = targetIndex;
                    Log.d("PlaybackViewModel", "预加载成功，索引: " + targetIndex);
                }
            }

            @Override
            public void onFailure(String error) {
                Log.w("PlaybackViewModel", "预加载失败: " + error);
            }
        });
    }

    /**
     * 设置播放列表
     */
    public void setPlayList(List<FileInfo> files) {
        setPlayList(files, false);
    }

    /**
     * 设置播放列表
     * @param files 文件列表
     * @param resetIndex 是否根据播放模式重置索引
     */
    public void setPlayList(List<FileInfo> files, boolean resetIndex) {
        playList.setValue(files);
        
        // 如果是随机模式，需要生成随机索引列表
        if (playMode.getValue() == PlayMode.RANDOM) {
            generateRandomIndices();
        }
        
        // 根据播放模式设置初始索引
        // 条件：明确要求重置，或者currentIndex从未设置过
        Integer currentIndexValue = currentIndex.getValue();
        if ((resetIndex || currentIndexValue == null) && files != null && !files.isEmpty()) {
            PlayMode mode = playMode.getValue();
            int initialIndex;
            
            Log.d("PlaybackViewModel", "重置播放索引，模式: " + (mode != null ? mode.getName() : "null"));
            
            if (mode == PlayMode.REVERSE) {
                // 倒序播放：从最后一个开始
                initialIndex = files.size() - 1;
            } else if (mode == PlayMode.RANDOM) {
                // 随机播放：从随机列表的第一个索引开始（该索引对应的文件是随机的）
                // randomIndices列表已经被打乱，取第一个元素作为初始播放索引
                initialIndex = randomIndices.isEmpty() ? 0 : randomIndices.get(0);
                Log.d("PlaybackViewModel", "随机模式：初始索引 = " + initialIndex);
            } else {
                // 其他模式：从第一个开始
                initialIndex = 0;
            }
            
            currentIndex.setValue(initialIndex);
        }
    }

    /**
     * 切换播放模式
     */
    public void togglePlayMode() {
        PlayMode currentMode = playMode.getValue();
        PlayMode newMode;
        switch (currentMode) {
            case SEQUENTIAL:
                newMode = PlayMode.RANDOM;
                break;
            case RANDOM:
                newMode = PlayMode.SINGLE;
                break;
            case SINGLE:
                newMode = PlayMode.REVERSE;
                break;
            case REVERSE:
                newMode = PlayMode.SEQUENTIAL;
                break;
            default:
                newMode = PlayMode.SEQUENTIAL;
        }
        playMode.setValue(newMode);
        PreferenceUtils.savePlayMode(getApplication(), newMode.getValue());
        
        // 如果切换到随机模式，重新生成随机索引
        if (newMode == PlayMode.RANDOM) {
            generateRandomIndices();
        }
    }

    /**
     * 播放/暂停
     */
    public void togglePlayPause() {
        Boolean playing = isPlaying.getValue();
        isPlaying.setValue(playing == null ? true : !playing);
    }
    
    /**
     * 设置播放状态
     */
    public void setPlaying(boolean playing) {
        isPlaying.setValue(playing);
    }

    /**
     * 播放下一个
     */
    public void playNext() {
        List<FileInfo> files = playList.getValue();
        if (files == null || files.isEmpty()) {
            return;
        }

        PlayMode mode = playMode.getValue();
        Integer current = currentIndex.getValue();
        if (current == null) {
            current = 0;
        }

        int nextIndex;
        switch (mode) {
            case SEQUENTIAL:
                nextIndex = (current + 1) % files.size();
                break;
            case REVERSE:
                nextIndex = (current - 1 + files.size()) % files.size();
                break;
            case RANDOM:
                nextIndex = getNextRandomIndex(current);
                break;
            case SINGLE:
                nextIndex = current;
                break;
            default:
                nextIndex = (current + 1) % files.size();
        }

        currentIndex.setValue(nextIndex);
        
        // 清除预加载缓存（如果需要）
        if (preloadedIndex != nextIndex) {
            preloadedDlink = null;
            preloadedIndex = -1;
        }
    }

    /**
     * 播放上一个
     */
    public void playPrevious() {
        List<FileInfo> files = playList.getValue();
        if (files == null || files.isEmpty()) {
            return;
        }

        PlayMode mode = playMode.getValue();
        Integer current = currentIndex.getValue();
        if (current == null) {
            current = 0;
        }

        int prevIndex;
        switch (mode) {
            case SEQUENTIAL:
                prevIndex = (current - 1 + files.size()) % files.size();
                break;
            case REVERSE:
                prevIndex = (current + 1) % files.size();
                break;
            case RANDOM:
                prevIndex = getPreviousRandomIndex(current);
                break;
            case SINGLE:
                prevIndex = current;
                break;
            default:
                prevIndex = (current - 1 + files.size()) % files.size();
        }

        currentIndex.setValue(prevIndex);
        
        // 清除预加载缓存（如果需要）
        if (preloadedIndex != prevIndex) {
            preloadedDlink = null;
            preloadedIndex = -1;
        }
    }

    /**
     * 跳转到指定索引
     */
    public void seekTo(int index) {
        List<FileInfo> files = playList.getValue();
        if (files != null && index >= 0 && index < files.size()) {
            currentIndex.setValue(index);
            updatePlaylistProgress(index);
        }
    }
    
    /**
     * 设置当前索引（用于初始化播放列表时指定起始位置）
     */
    public void setCurrentIndex(int index) {
        List<FileInfo> files = playList.getValue();
        if (files != null && index >= 0 && index < files.size()) {
            currentIndex.setValue(index);
            updatePlaylistProgress(index);
        }
    }

    /**
     * 设置当前地点
     */
    public void setCurrentLocation(String location) {
        currentLocation.setValue(location);
    }

    /**
     * 设置图片特效
     */
    public void setImageEffect(ImageEffect effect) {
        imageEffect.setValue(effect);
        PreferenceUtils.saveImageEffect(getApplication(), effect.getValue());
    }

    /**
     * 设置图片展示时长
     */
    public void setImageDisplayDuration(int duration) {
        imageDisplayDuration.setValue(duration);
        PreferenceUtils.saveImageDisplayDuration(getApplication(), duration);
    }

    /**
     * 设置图片过渡时长
     */
    public void setImageTransitionDuration(int duration) {
        imageTransitionDuration.setValue(duration);
        PreferenceUtils.saveImageTransitionDuration(getApplication(), duration);
    }

    /**
     * 设置是否显示地点
     */
    public void setShowLocation(boolean show) {
        showLocation.setValue(show);
        PreferenceUtils.saveShowLocation(getApplication(), show);
    }

    /**
     * 设置播放模式
     */
    public void setPlayMode(PlayMode mode) {
        playMode.setValue(mode);
        PreferenceUtils.savePlayMode(getApplication(), mode.getValue());
        
        // 如果切换到随机模式，重新生成随机索引
        if (mode == PlayMode.RANDOM) {
            generateRandomIndices();
        }
    }

    /**
     * 生成随机索引列表
     */
    private void generateRandomIndices() {
        List<FileInfo> files = playList.getValue();
        if (files == null || files.isEmpty()) {
            randomIndices.clear();
            return;
        }

        randomIndices = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            randomIndices.add(i);
        }
        Collections.shuffle(randomIndices, random);
    }

    /**
     * 获取下一个随机索引
     */
    private int getNextRandomIndex(int currentIndex) {
        if (randomIndices.isEmpty()) {
            generateRandomIndices();
        }
        
        // 找到当前索引在随机列表中的位置
        int currentPos = randomIndices.indexOf(currentIndex);
        if (currentPos == -1) {
            return randomIndices.get(0);
        }
        
        // 返回下一个
        int nextPos = (currentPos + 1) % randomIndices.size();
        return randomIndices.get(nextPos);
    }

    /**
     * 获取上一个随机索引
     */
    private int getPreviousRandomIndex(int currentIndex) {
        if (randomIndices.isEmpty()) {
            generateRandomIndices();
        }
        
        // 找到当前索引在随机列表中的位置
        int currentPos = randomIndices.indexOf(currentIndex);
        if (currentPos == -1) {
            return randomIndices.get(0);
        }
        
        // 返回上一个
        int prevPos = (currentPos - 1 + randomIndices.size()) % randomIndices.size();
        return randomIndices.get(prevPos);
    }

    /**
     * 获取当前播放的文件
     */
    public FileInfo getCurrentFile() {
        List<FileInfo> files = playList.getValue();
        Integer index = currentIndex.getValue();
        
        if (files != null && index != null && index >= 0 && index < files.size()) {
            return files.get(index);
        }
        return null;
    }
    
    /**
     * 设置播放列表数据库ID
     */
    public void setPlaylistDatabaseId(long id) {
        this.playlistDatabaseId = id;
    }
    
    /**
     * 更新播放列表的播放进度
     */
    private void updatePlaylistProgress(int index) {
        if (playlistDatabaseId == -1) {
            return;
        }
        
        new Thread(() -> {
            try {
                Playlist playlist = playlistRepository.getPlaylistByIdSync(playlistDatabaseId);
                if (playlist != null) {
                    playlist.setLastPlayedIndex(index);
                    playlist.setLastPlayedAt(System.currentTimeMillis());
                    playlistRepository.updatePlaylist(playlist, null, null);
                    Log.d("PlaybackViewModel", "更新播放列表进度: playlistId=" + playlistDatabaseId + ", index=" + index);
                }
            } catch (Exception e) {
                Log.e("PlaybackViewModel", "更新播放列表进度失败", e);
            }
        }).start();
    }
}