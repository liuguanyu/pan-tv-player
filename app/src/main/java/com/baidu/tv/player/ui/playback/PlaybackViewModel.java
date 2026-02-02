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

    public PlaybackViewModel(@NonNull Application application) {
        super(application);
        playList = new MutableLiveData<>();
        currentIndex = new MutableLiveData<>(0);
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

        // 如果dlink已经存在且有效（以http开头），直接使用
        String currentDlink = file.getDlink();
        if (currentDlink != null && !currentDlink.isEmpty() && currentDlink.startsWith("http")) {
            Log.d("PlaybackViewModel", "使用现有的dlink: " + currentDlink);
            String finalUrl = currentDlink;
            if (!finalUrl.contains("access_token=")) {
                finalUrl += (finalUrl.contains("?") ? "&" : "?") + "access_token=" + accessToken;
            }
            preparedMediaUrl.setValue(finalUrl);
            return;
        } else {
            if (currentDlink != null) {
                Log.w("PlaybackViewModel", "现有的dlink无效(不是http开头): " + currentDlink);
            }
        }

        // 否则，通过API获取文件详情
        Log.d("PlaybackViewModel", "正在获取文件详情以获取dlink, fsId=" + file.getFsId());
        fileRepository.fetchFileDetail(accessToken, file.getFsId(), new FileRepository.FileDetailCallback() {
            @Override
            public void onSuccess(FileInfo fileInfo) {
                String dlink = fileInfo.getDlink();
                Log.d("PlaybackViewModel", "获取文件详情成功, dlink=" + dlink);
                
                if (dlink != null && !dlink.isEmpty()) {
                    if (!dlink.startsWith("http")) {
                        Log.e("PlaybackViewModel", "API返回的dlink无效(不是http开头): " + dlink);
                        // 尝试构建一个临时的dlink（虽然可能无法工作，但比路径好）
                        // 或者直接报错
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
                } else {
                    Log.e("PlaybackViewModel", "获取到的dlink为空: " + fileInfo.getPath());
                    preparedMediaUrl.setValue(null); // 或者设置一个错误状态
                }
            }

            @Override
            public void onFailure(String error) {
                Log.e("PlaybackViewModel", "获取文件详情失败: " + error);
                preparedMediaUrl.setValue(null); // 或者设置一个错误状态
            }
        });
    }

    /**
     * 设置播放列表
     */
    public void setPlayList(List<FileInfo> files) {
        playList.setValue(files);
        currentIndex.setValue(0);
        generateRandomIndices();
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
                newMode = PlayMode.SEQUENTIAL;
                break;
            default:
                newMode = PlayMode.SEQUENTIAL;
        }
        playMode.setValue(newMode);
        
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