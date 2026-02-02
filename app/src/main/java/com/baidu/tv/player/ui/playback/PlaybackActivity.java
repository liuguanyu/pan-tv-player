
package com.baidu.tv.player.ui.playback;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Geocoder;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.format.DateUtils;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.baidu.tv.player.R;
import com.baidu.tv.player.model.FileInfo;
import com.baidu.tv.player.model.ImageEffect;
import com.baidu.tv.player.model.PlayMode;
import com.baidu.tv.player.model.PlaybackHistory;
import com.baidu.tv.player.model.Playlist;
import com.baidu.tv.player.model.PlaylistItem;
import com.baidu.tv.player.repository.PlaybackHistoryRepository;
import com.baidu.tv.player.repository.PlaylistRepository;
import com.baidu.tv.player.utils.LocationUtils;
import com.baidu.tv.player.utils.PlaylistCache;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ui.PlayerView;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.interfaces.IVLCVout;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 播放器Activity
 * 支持 VLC (软解码) 和 ExoPlayer (硬解码)
 */
public class PlaybackActivity extends FragmentActivity {
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    
    private PlaybackViewModel viewModel;
    private PlaybackHistoryRepository historyRepository;
    private com.baidu.tv.player.auth.AuthRepository authRepository;
    
    // UI组件
    private SurfaceView surfaceView; // VLC Surface
    private PlayerView playerView;   // ExoPlayer View
    private ImageView ivImageDisplay;
    private View layoutControls;
    private TextView tvFileName;
    private TextView tvLocation;
    private TextView tvCurrentTime;
    private TextView tvTotalTime;
    private SeekBar seekbarProgress;
    private ImageView ivPlayMode;
    private ImageView ivPrev;
    private ImageView ivPlayPause;
    private ImageView ivNext;
    private ProgressBar loadingIndicator;
    
    // VLC 播放器
    private LibVLC libVLC;
    private MediaPlayer vlcMediaPlayer;
    
    // ExoPlayer 播放器 (备用)
    private ExoPlayer exoPlayer;
    
    // 播放模式：true使用VLC，false使用ExoPlayer
    private boolean useVlc = true; 
    
    // 图片播放相关
    private Handler imageHandler;
    private Runnable imageRunnable;
    
    // 控制栏显示相关
    private Handler controlsHandler;
    private Runnable controlsRunnable;
    private static final int CONTROLS_HIDE_DELAY = 3000; // 3秒后隐藏控制栏
    
    // 进度更新相关
    private Handler progressHandler;
    private Runnable progressRunnable;
    
    // 地点显示相关
    private Handler locationHandler;
    private Runnable locationRunnable;
    private static final int LOCATION_HIDE_DELAY = 10000; // 10秒后隐藏地点

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 设置全屏和沉浸式模式
        getWindow().setFlags(
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        
        setContentView(R.layout.activity_playback);
        
        initViews();
        initVLC();
        initExoPlayer();
        initViewModel();
        initData();
        setupClickListeners();
    }

    private void initViews() {
        surfaceView = findViewById(R.id.surface_view);
        playerView = findViewById(R.id.player_view);
        ivImageDisplay = findViewById(R.id.iv_image_display);
        layoutControls = findViewById(R.id.layout_controls);
        tvFileName = findViewById(R.id.tv_file_name);
        tvLocation = findViewById(R.id.tv_location);
        tvCurrentTime = findViewById(R.id.tv_current_time);
        tvTotalTime = findViewById(R.id.tv_total_time);
        seekbarProgress = findViewById(R.id.seekbar_progress);
        ivPlayMode = findViewById(R.id.iv_play_mode);
        ivPrev = findViewById(R.id.iv_prev);
        ivPlayPause = findViewById(R.id.iv_play_pause);
        ivNext = findViewById(R.id.iv_next);
        loadingIndicator = findViewById(R.id.loading_indicator);
        
        // 强制设置SurfaceView布局参数
        android.view.ViewGroup.LayoutParams params = surfaceView.getLayoutParams();
        params.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
        params.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
        surfaceView.setLayoutParams(params);
        
        // 隐藏控制栏
        hideControls();
    }
    
    private void initVLC() {
        try {
            ArrayList<String> options = new ArrayList<>();
            // 启用详细日志
            options.add("-vvv");
            // 尝试硬件加速，如果失败则回退到软解码
            options.add("--avcodec-hw=any");
            // 增加网络缓存以提高稳定性
            options.add("--network-caching=2000");
            
            libVLC = new LibVLC(this, options);
            vlcMediaPlayer = new MediaPlayer(libVLC);
            
            IVLCVout vout = vlcMediaPlayer.getVLCVout();
            vout.setVideoView(surfaceView);
            vout.setWindowSize(surfaceView.getWidth(), surfaceView.getHeight());
            
            // 添加布局监听器
            vout.addCallback(new IVLCVout.Callback() {
                @Override
                public void onSurfacesCreated(IVLCVout vout) {
                    android.util.Log.d("PlaybackActivity", "VLC Surface created");
                }

                @Override
                public void onSurfacesDestroyed(IVLCVout vout) {
                    android.util.Log.d("PlaybackActivity", "VLC Surface destroyed");
                }

                public void onNewLayout(IVLCVout vout, int width, int height, int visibleWidth, int visibleHeight, int sarNum, int sarDen) {
                    if (width * height == 0) return;
                    
                    // 计算视频实际宽高比（考虑SAR）
                    double videoRatio = (double) width / height;
                    if (sarNum > 0 && sarDen > 0) {
                        videoRatio = videoRatio * sarNum / sarDen;
                    }
                    final double finalVideoRatio = videoRatio;

                    android.util.Log.d("PlaybackActivity", String.format(
                        "视频源尺寸: video=%dx%d, sar=%d/%d, ratio=%.4f",
                        width, height, sarNum, sarDen, finalVideoRatio));
                    
                    runOnUiThread(() -> {
                        // 获取屏幕尺寸
                        android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
                        getWindowManager().getDefaultDisplay().getRealMetrics(dm);
                        int screenWidth = dm.widthPixels;
                        int screenHeight = dm.heightPixels;
                        
                        double screenRatio = (double) screenWidth / screenHeight;
                        
                        // SurfaceView始终保持全屏
                        android.widget.FrameLayout.LayoutParams lp =
                            (android.widget.FrameLayout.LayoutParams) surfaceView.getLayoutParams();
                        lp.width = screenWidth;
                        lp.height = screenHeight;
                        lp.gravity = android.view.Gravity.CENTER;
                        surfaceView.setLayoutParams(lp);
                        
                        // 使用VLC的API控制视频显示
                        // 计算目标宽高比字符串
                        String targetAspectRatio;
                        if (finalVideoRatio > screenRatio) {
                            // 横版视频：宽度占满，高度按比例
                            targetAspectRatio = screenWidth + ":" + (int)(screenWidth / finalVideoRatio);
                        } else {
                            // 竖版视频：高度占满，宽度按比例
                            targetAspectRatio = (int)(screenHeight * finalVideoRatio) + ":" + screenHeight;
                        }
                        
                        // 设置VLC的宽高比
                        vlcMediaPlayer.setAspectRatio(targetAspectRatio);
                        // 设置缩放为0（自动）
                        vlcMediaPlayer.setScale(0);
                        
                        android.util.Log.d("PlaybackActivity", String.format(
                            "VLC设置: AspectRatio=%s, SurfaceView=%dx%d",
                            targetAspectRatio, screenWidth, screenHeight));
                    });
                }
            });
            
            // 确保SurfaceView已准备好再附加
            surfaceView.getHolder().addCallback(new android.view.SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(@NonNull android.view.SurfaceHolder holder) {
                    android.util.Log.d("PlaybackActivity", "SurfaceHolder created");
                    if (!vout.areViewsAttached()) {
                        vout.attachViews();
                    }
                }

                @Override
                public void surfaceChanged(@NonNull android.view.SurfaceHolder holder, int format, int width, int height) {
                    android.util.Log.d("PlaybackActivity", "SurfaceHolder changed: " + width + "x" + height);
                    if (vout.areViewsAttached()) {
                        vout.setWindowSize(width, height);
                    }
                }

                @Override
                public void surfaceDestroyed(@NonNull android.view.SurfaceHolder holder) {
                    android.util.Log.d("PlaybackActivity", "SurfaceHolder destroyed");
                }
            });
            
            vlcMediaPlayer.setEventListener(event -> {
                switch (event.type) {
                    case MediaPlayer.Event.Buffering:
                        if (event.getBuffering() == 100.0f) {
                            loadingIndicator.setVisibility(View.GONE);
                        } else {
                            if (loadingIndicator.getVisibility() != View.VISIBLE) {
                                loadingIndicator.setVisibility(View.VISIBLE);
                            }
                        }
                        break;
                    case MediaPlayer.Event.Playing:
                        loadingIndicator.setVisibility(View.GONE);
                        updatePlayPauseButton(true);
                        startProgressUpdate();
                        break;
                    case MediaPlayer.Event.Paused:
                        updatePlayPauseButton(false);
                        stopProgressUpdate();
                        break;
                    case MediaPlayer.Event.Stopped:
                        updatePlayPauseButton(false);
                        stopProgressUpdate();
                        break;
                    case MediaPlayer.Event.EndReached:
                        stopProgressUpdate();
                        viewModel.playNext();
                        break;
                    case MediaPlayer.Event.EncounteredError:
                        loadingIndicator.setVisibility(View.GONE);
                        Toast.makeText(this, "播放错误，尝试下一个", Toast.LENGTH_SHORT).show();
                        viewModel.playNext();
                        break;
                }
            });
            
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "VLC初始化失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            useVlc = false; // 回退到ExoPlayer
        }
    }

    private void initExoPlayer() {
        // 初始化ExoPlayer
        exoPlayer = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(exoPlayer);
        
        // 监听播放器状态变化
        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY) {
                    loadingIndicator.setVisibility(View.GONE);
                    updateProgress();
                } else if (playbackState == Player.STATE_BUFFERING) {
                    loadingIndicator.setVisibility(View.VISIBLE);
                } else if (playbackState == Player.STATE_ENDED) {
                    viewModel.playNext();
                }
            }
            
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                updatePlayPauseButton(isPlaying);
                if (isPlaying) {
                    startProgressUpdate();
                } else {
                    stopProgressUpdate();
                }
            }

            @Override
            public void onPlayerError(@NonNull com.google.android.exoplayer2.PlaybackException error) {
                loadingIndicator.setVisibility(View.GONE);
                android.util.Log.e("PlaybackActivity", "播放错误", error);
                
                String errorMessage = "播放出错";
                boolean shouldSkip = false;
                
                // 检查是否是解码器问题
                if (error.getCause() instanceof com.google.android.exoplayer2.mediacodec.MediaCodecRenderer.DecoderInitializationException) {
                    errorMessage = "无法解码此视频格式 (HEVC/Dolby Vision)，跳过到下一个";
                    shouldSkip = true;
                } else if (error.errorCode == com.google.android.exoplayer2.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED) {
                    errorMessage = "解码器初始化失败，跳过到下一个";
                    shouldSkip = true;
                }
                
                android.widget.Toast.makeText(PlaybackActivity.this, errorMessage, android.widget.Toast.LENGTH_SHORT).show();
                
                // 自动播放下一个
                if (shouldSkip) {
                    new Handler().postDelayed(() -> {
                        List<FileInfo> files = viewModel.getPlayList().getValue();
                        if (files != null && files.size() > 1) {
                            viewModel.playNext();
                        }
                    }, 2000);
                }
            }
        });
        
        // 初始化图片播放Handler
        imageHandler = new Handler();
        controlsHandler = new Handler();
        progressHandler = new Handler();
        // 初始化地点显示Handler
        locationHandler = new Handler();
        locationRunnable = () -> {
            // 淡出动画
            tvLocation.animate()
                    .alpha(0f)
                    .setDuration(500)
                    .withEndAction(() -> tvLocation.setVisibility(View.GONE))
                    .start();
        };
    }

    private void initViewModel() {
        viewModel = new ViewModelProvider(this).get(PlaybackViewModel.class);
        
        // 观察播放列表
        viewModel.getPlayList().observe(this, files -> {
            if (files != null && !files.isEmpty()) {
                playCurrentFile();
            }
        });
        
        // 观察当前索引
        viewModel.getCurrentIndex().observe(this, index -> {
            if (index != null) {
                playCurrentFile();
            }
        });
        
        // 观察播放模式
        viewModel.getPlayMode().observe(this, mode -> {
            if (mode != null) {
                updatePlayModeIcon();
            }
        });
        
        // 观察播放状态
        viewModel.getIsPlaying().observe(this, isPlaying -> {
            if (isPlaying != null) {
                handlePlayPause(isPlaying);
            }
        });
        
        // 观察地点信息
        viewModel.getCurrentLocation().observe(this, location -> {
            android.util.Log.d("PlaybackActivity", "CurrentLocation观察者触发: " + location);
            Boolean showLocation = viewModel.getShowLocation().getValue();
            android.util.Log.d("PlaybackActivity", "ShowLocation值: " + showLocation);
            
            if (showLocation != null && showLocation && location != null && !location.isEmpty()) {
                android.util.Log.d("PlaybackActivity", "显示位置信息: " + location);
                showLocationToast(location);
            } else {
                android.util.Log.d("PlaybackActivity", "隐藏位置信息");
                tvLocation.setVisibility(View.GONE);
            }
        });
        
        // 观察是否显示地点
        viewModel.getShowLocation().observe(this, showLocation -> {
            android.util.Log.d("PlaybackActivity", "ShowLocation观察者触发: " + showLocation);
            if (showLocation != null && !showLocation) {
                android.util.Log.d("PlaybackActivity", "设置关闭，隐藏位置");
                tvLocation.setVisibility(View.GONE);
            } else if (showLocation != null && showLocation) {
                // 当设置打开时，检查是否有位置信息
                String location = viewModel.getCurrentLocation().getValue();
                if (location != null && !location.isEmpty()) {
                    android.util.Log.d("PlaybackActivity", "设置打开，显示位置: " + location);
                    showLocationToast(location);
                }
            }
        });

        // 观察准备好的媒体URL
        viewModel.getPreparedMediaUrl().observe(this, url -> {
            if (url != null) {
                // 停止之前的加载指示器
                loadingIndicator.setVisibility(View.GONE);
                
                FileInfo currentFile = viewModel.getCurrentFile();
                if (currentFile == null) return;
                
                if (currentFile.isVideo()) {
                    playVideoWithUrl(url);
                } else if (currentFile.isImage()) {
                    playImageWithUrl(url);
                }
                
                // 获取地点信息（使用实际的媒体URL）
                getLocationForFile(currentFile, url);
                
                // 临时添加：测试反向地理编码功能
                // LocationUtils.testReverseGeocode(this);
                
                // 保存到播放历史
                new Thread(() -> {
                    PlaybackHistory history = new PlaybackHistory();
                    history.setFolderPath(currentFile.getPath());
                    history.setFolderName(currentFile.getServerFilename());
                    
                    // 设置媒体类型
                    int mediaType = 3; // 默认混合
                    if (currentFile.isVideo()) {
                        mediaType = 2; // 视频
                    } else if (currentFile.isImage()) {
                        mediaType = 1; // 图片
                    }
                    history.setMediaType(mediaType);
                    
                    history.setFileCount(1);
                    history.setLastPlayTime(System.currentTimeMillis());
                    history.setCreateTime(System.currentTimeMillis());
                    historyRepository.insert(history);
                }).start();
            } else {
                // 加载失败
                loadingIndicator.setVisibility(View.GONE);
                // 可以显示错误提示
            }
        });
    }

    private void initData() {
        historyRepository = new PlaybackHistoryRepository(getApplication());
        authRepository = new com.baidu.tv.player.auth.AuthRepository(this);
        
        // 检查是否有传入的历史记录ID
        long historyId = getIntent().getLongExtra("historyId", -1);
        if (historyId != -1) {
            // 从历史记录加载
            historyRepository.getHistoryById(historyId).observe(this, history -> {
                if (history != null) {
                    // TODO: 从历史记录加载播放列表
                }
            });
        } else {
            // 检查是否有传入的播放列表ID
            long playlistDatabaseId = getIntent().getLongExtra("playlistDatabaseId", -1);
            if (playlistDatabaseId != -1) {
                // 从数据库加载播放列表（异步）
                PlaylistRepository playlistRepository = new PlaylistRepository(getApplication());
                
                // 在后台线程中执行数据库操作
                new Thread(() -> {
                    try {
                        Playlist playlist = playlistRepository.getPlaylistByIdSync(playlistDatabaseId);
                        
                        if (playlist != null) {
                            // 更新最后播放时间
                            playlist.setLastPlayedAt(System.currentTimeMillis());
                            playlistRepository.updatePlaylist(playlist, null, null);
                            
                            // 从数据库获取播放列表项
                            List<PlaylistItem> playlistItems = playlistRepository.getPlaylistItemsSync(playlistDatabaseId);
                            
                            // 转换为FileInfo列表（需要实时获取dlink）
                            List<FileInfo> files = new ArrayList<>();
                            for (PlaylistItem item : playlistItems) {
                                FileInfo fileInfo = new FileInfo();
                                fileInfo.setPath(item.getFilePath());
                                fileInfo.setServerFilename(item.getFileName());
                                fileInfo.setFsId(item.getFsId());
                                fileInfo.setSize(item.getFileSize());
                                fileInfo.setDlink(null); // 显式置空，强制使用prepareMediaUrl获取
                                
                                // 根据文件扩展名判断媒体类型
                                String fileName = item.getFileName().toLowerCase();
                                if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") ||
                                    fileName.endsWith(".png") || fileName.endsWith(".gif") ||
                                    fileName.endsWith(".bmp") || fileName.endsWith(".webp")) {
                                    fileInfo.setCategory(3); // 图片
                                } else if (fileName.endsWith(".mp4") || fileName.endsWith(".avi") ||
                                           fileName.endsWith(".mkv") || fileName.endsWith(".mov") ||
                                           fileName.endsWith(".wmv") || fileName.endsWith(".flv") ||
                                           fileName.endsWith(".webm") || fileName.endsWith(".m4v")) {
                                    fileInfo.setCategory(1); // 视频
                                }
                                
                                files.add(fileInfo);
                            }
                            
                            android.util.Log.d("PlaybackActivity", "从数据库加载播放列表: " + files.size() + " 个文件");
                            
                            if (!files.isEmpty()) {
                                // 切换回主线程更新UI
                                runOnUiThread(() -> {
                                    viewModel.setPlayList(files);
                                    
                                    // 设置起始播放位置（从播放列表中获取）
                                    int startIndex = playlist.getLastPlayedIndex();
                                    if (startIndex >= 0 && startIndex < files.size()) {
                                        viewModel.setCurrentIndex(startIndex);
                                    }
                                    
                                    // 保存播放列表ID到ViewModel，用于更新播放进度
                                    viewModel.setPlaylistDatabaseId(playlistDatabaseId);
                                });
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        runOnUiThread(() -> {
                            android.widget.Toast.makeText(PlaybackActivity.this,
                                "加载播放列表失败", android.widget.Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    }
                }).start();
            } else {
                // 优先从缓存加载播放列表
                String playlistId = getIntent().getStringExtra("playlistId");
                List<FileInfo> files = null;
                
                if (playlistId != null) {
                    // 从缓存中获取并移除播放列表（一次性使用）
                    files = PlaylistCache.getInstance().getAndRemove(playlistId);
                }
                
                // 如果缓存中没有，则尝试从Intent中获取（兼容旧版本）
                if (files == null) {
                    files = getIntent().getParcelableArrayListExtra("files");
                }
                
                if (files != null && !files.isEmpty()) {
                    viewModel.setPlayList(files);
                    
                    // 设置起始播放位置
                    int startIndex = getIntent().getIntExtra("startIndex", 0);
                    if (startIndex >= 0 && startIndex < files.size()) {
                        viewModel.setCurrentIndex(startIndex);
                    }
                }
            }
        }
    }

    private void setupClickListeners() {
        // 播放模式切换
        ivPlayMode.setOnClickListener(v -> viewModel.togglePlayMode());
        
        // 上一个
        ivPrev.setOnClickListener(v -> viewModel.playPrevious());
        
        // 播放/暂停
        ivPlayPause.setOnClickListener(v -> viewModel.togglePlayPause());
        
        // 下一个
        ivNext.setOnClickListener(v -> viewModel.playNext());
        
        // 控制栏显示/隐藏
        View.OnClickListener controlsClickListener = v -> showControls();
        playerView.setOnClickListener(controlsClickListener);
        surfaceView.setOnClickListener(controlsClickListener);
        ivImageDisplay.setOnClickListener(controlsClickListener);
        
        // 进度条拖动
        seekbarProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    if (isCurrentFileVideo()) {
                        long newTime = (long) progress;
                        if (useVlc && vlcMediaPlayer != null) {
                            vlcMediaPlayer.setTime(newTime);
                        } else if (exoPlayer != null) {
                            exoPlayer.seekTo(newTime);
                        }
                        tvCurrentTime.setText(DateUtils.formatElapsedTime(newTime / 1000));
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                stopProgressUpdate();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                startProgressUpdate();
            }
        });
    }

    /**
     * 播放当前文件
     */
    private void playCurrentFile() {
        FileInfo currentFile = viewModel.getCurrentFile();
        if (currentFile == null) {
            android.util.Log.e("PlaybackActivity", "playCurrentFile: currentFile is null");
            return;
        }

        android.util.Log.d("PlaybackActivity", "playCurrentFile: " + currentFile.getServerFilename() +
            ", path=" + currentFile.getPath() +
            ", fsId=" + currentFile.getFsId() +
            ", category=" + currentFile.getCategory() +
            ", hasDlink=" + (currentFile.getDlink() != null));

        // 更新文件名
        tvFileName.setText(currentFile.getServerFilename());
        
        // 停止之前的播放
        stopCurrentPlayback();
        
        // 显示加载指示器
        loadingIndicator.setVisibility(View.VISIBLE);
        
        // 准备媒体URL
        String accessToken = authRepository.getAccessToken();
        android.util.Log.d("PlaybackActivity", "准备获取媒体URL, accessToken=" +
            (accessToken != null ? accessToken.substring(0, 10) + "..." : "null"));
        viewModel.prepareMediaUrl(accessToken, currentFile);
    }

    /**
     * 使用URL播放视频
     */
    private void playVideoWithUrl(String videoUrl) {
        android.util.Log.d("PlaybackActivity", "playVideoWithUrl: " + videoUrl);
        
        // 隐藏图片显示
        ivImageDisplay.setVisibility(View.GONE);
        
        if (useVlc) {
            // 使用 VLC 播放
            surfaceView.setVisibility(View.VISIBLE);
            playerView.setVisibility(View.GONE);
            
            if (vlcMediaPlayer != null && videoUrl != null && !videoUrl.isEmpty()) {
                android.util.Log.d("PlaybackActivity", "开始VLC播放: " + videoUrl);
                
                // 确保VLCVout已附加
                IVLCVout vout = vlcMediaPlayer.getVLCVout();
                if (!vout.areViewsAttached()) {
                    android.util.Log.d("PlaybackActivity", "VLCVout未附加，重新附加");
                    vout.setVideoView(surfaceView);
                    vout.setWindowSize(surfaceView.getWidth(), surfaceView.getHeight());
                    vout.attachViews();
                }
                
                Media media = new Media(libVLC, Uri.parse(videoUrl));
                // 禁用硬件解码以避免兼容性问题
                media.setHWDecoderEnabled(false, false);
                // 添加媒体选项
                media.addOption(":network-caching=2000");
                
                vlcMediaPlayer.setMedia(media);
                
                // 设置缩放为0，让VLC自动适应SurfaceView
                vlcMediaPlayer.setScale(0);
                // 不设置宽高比，让onNewLayout回调处理
                vlcMediaPlayer.setAspectRatio(null);
                
                android.util.Log.d("PlaybackActivity", "VLC Scale=0 (自动适应), AspectRatio=null");
                
                media.release();
                
                // 延迟播放，确保Surface准备完成
                new Handler().postDelayed(() -> {
                    if (vlcMediaPlayer != null) {
                        vlcMediaPlayer.play();
                        viewModel.setPlaying(true);
                        android.util.Log.d("PlaybackActivity", "VLC开始播放");
                    }
                }, 100);
            }
        } else {
            // 使用 ExoPlayer 播放
            surfaceView.setVisibility(View.GONE);
            playerView.setVisibility(View.VISIBLE);
            
            if (videoUrl != null && !videoUrl.isEmpty()) {
                MediaItem mediaItem = MediaItem.fromUri(videoUrl);
                exoPlayer.setMediaItem(mediaItem);
                exoPlayer.prepare();
                
                // 设置播放状态为true，确保自动播放
                viewModel.setPlaying(true);
                exoPlayer.setPlayWhenReady(true);
            }
        }
    }

    /**
     * 使用URL播放图片
     */
    private void playImageWithUrl(String imageUrl) {
        android.util.Log.d("PlaybackActivity", "playImageWithUrl: " + imageUrl);
        
        // 显示图片显示，隐藏视频播放器
        surfaceView.setVisibility(View.GONE);
        playerView.setVisibility(View.GONE);
        ivImageDisplay.setVisibility(View.VISIBLE);
        
        // 加载图片
        if (imageUrl != null && !imageUrl.isEmpty()) {
            android.util.Log.d("PlaybackActivity", "开始加载图片: " + imageUrl);
            // 根据设置的特效加载图片
            ImageEffect effect = viewModel.getImageEffect().getValue();
            if (effect == null) {
                effect = ImageEffect.FADE;
            }
            
            // 如果是随机特效，每次显示图片时随机选择一种特效
            ImageEffect actualEffect = effect.getActualEffect();
            android.util.Log.d("PlaybackActivity", "图片特效: " + effect.getName() +
                (effect == ImageEffect.RANDOM ? " -> 实际特效: " + actualEffect.getName() : ""));

            // 重置ImageView的变换状态
            ivImageDisplay.setScaleX(1.0f);
            ivImageDisplay.setScaleY(1.0f);
            ivImageDisplay.setTranslationX(0);
            ivImageDisplay.setTranslationY(0);
            ivImageDisplay.setAlpha(1.0f);
            
            DrawableTransitionOptions transitionOptions = DrawableTransitionOptions.withCrossFade(1000);
            
            Glide.with(this)
                    .load(imageUrl)
                    .transition(transitionOptions)
                    .listener(new RequestListener<android.graphics.drawable.Drawable>() {
                        @Override
                        public boolean onLoadFailed(@androidx.annotation.Nullable com.bumptech.glide.load.engine.GlideException e, Object model, Target<android.graphics.drawable.Drawable> target, boolean isFirstResource) {
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model, Target<android.graphics.drawable.Drawable> target, com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                            // 图片加载完成后应用动画
                            // 使用Handler延迟一小段时间，确保图片已经完全显示
                            new Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                applyImageEffect(actualEffect);
                            }, 100);
                            return false;
                        }
                    })
                    .into(ivImageDisplay);
        }
        
        // 启动图片定时播放
        startImageDisplayTimer();
    }

    /**
     * 应用图片特效
     */
    private void applyImageEffect(ImageEffect effect) {
        // 确保视图可见
        ivImageDisplay.setVisibility(View.VISIBLE);
        
        switch (effect) {
            case FLOAT:
                // 浮动效果：缓慢放大
                ivImageDisplay.animate()
                        .scaleX(1.1f)
                        .scaleY(1.1f)
                        .setDuration(5000)
                        .setInterpolator(new android.view.animation.LinearInterpolator())
                        .start();
                break;
                
            case BOUNCE:
                // 跳动效果
                ivImageDisplay.setScaleX(0.8f);
                ivImageDisplay.setScaleY(0.8f);
                ivImageDisplay.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(1000)
                        .setInterpolator(new android.view.animation.BounceInterpolator())
                        .start();
                break;
                
            case EASE:
                // 缓动效果：从一侧滑入
                ivImageDisplay.setTranslationX(100f);
                ivImageDisplay.animate()
                        .translationX(0f)
                        .setDuration(1000)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator())
                        .start();
                break;
                
            case FADE:
            default:
                // 默认淡入淡出由Glide处理
                break;
        }
    }

    /**
     * 启动图片显示定时器
     */
    private void startImageDisplayTimer() {
        // 移除之前的回调
        if (imageRunnable != null) {
            imageHandler.removeCallbacks(imageRunnable);
        }
        
        // 创建新的回调
        imageRunnable = () -> {
            // 播放下一张图片
            viewModel.playNext();
        };
        
        // 获取显示时长
        Integer duration = viewModel.getImageDisplayDuration().getValue();
        if (duration == null) {
            duration = 5000; // 默认5秒
        }
        
        // 延迟执行
        imageHandler.postDelayed(imageRunnable, duration);
    }
    
    private void stopCurrentPlayback() {
        // 停止VLC
        if (vlcMediaPlayer != null) {
            vlcMediaPlayer.stop();
        }
        
        // 停止ExoPlayer
        if (exoPlayer != null) {
            exoPlayer.stop();
        }
        
        // 停止图片定时器
        if (imageRunnable != null) {
            imageHandler.removeCallbacks(imageRunnable);
        }
        
        stopProgressUpdate();
    }
    
    private void handlePlayPause(boolean isPlaying) {
        if (isCurrentFileVideo()) {
            if (useVlc) {
                if (vlcMediaPlayer != null) {
                    if (isPlaying) {
                        if (!vlcMediaPlayer.isPlaying()) vlcMediaPlayer.play();
                    } else {
                        if (vlcMediaPlayer.isPlaying()) vlcMediaPlayer.pause();
                    }
                }
            } else {
                if (exoPlayer != null) {
                    exoPlayer.setPlayWhenReady(isPlaying);
                }
            }
        }
    }
    
    private void updatePlayPauseButton(boolean isPlaying) {
        if (isPlaying) {
            ivPlayPause.setImageResource(android.R.drawable.ic_media_pause);
        } else {
            ivPlayPause.setImageResource(android.R.drawable.ic_media_play);
        }
    }
    
    private void updatePlayPauseButton() {
        boolean isPlaying = false;
        if (useVlc && vlcMediaPlayer != null) {
            isPlaying = vlcMediaPlayer.isPlaying();
        } else if (exoPlayer != null) {
            isPlaying = exoPlayer.isPlaying();
        }
        updatePlayPauseButton(isPlaying);
    }
    
    private void updatePlayModeIcon() {
        PlayMode mode = viewModel.getPlayMode().getValue();
        if (mode == null) mode = PlayMode.SEQUENTIAL;
        
        switch (mode) {
            case SINGLE:
                ivPlayMode.setImageResource(android.R.drawable.ic_menu_revert);
                break;
            case RANDOM:
                ivPlayMode.setImageResource(android.R.drawable.ic_menu_sort_alphabetically);
                break;
            case SEQUENTIAL:
            default:
                ivPlayMode.setImageResource(android.R.drawable.ic_menu_sort_by_size);
                break;
        }
    }
    
    private void showControls() {
        layoutControls.setVisibility(View.VISIBLE);
        
        // 移除之前的隐藏回调
        if (controlsRunnable != null) {
            controlsHandler.removeCallbacks(controlsRunnable);
        }
        
        // 创建新的隐藏回调
        controlsRunnable = this::hideControls;
        
        // 延迟隐藏
        controlsHandler.postDelayed(controlsRunnable, CONTROLS_HIDE_DELAY);
    }
    
    private void hideControls() {
        layoutControls.setVisibility(View.GONE);
    }

    /**
     * 显示浮动地点信息，并在几秒后自动隐藏
     */
    private void showLocationToast(String location) {
        // 移除之前的隐藏任务
        locationHandler.removeCallbacks(locationRunnable);
        
        // 设置内容并显示
        tvLocation.setText(location);
        tvLocation.setAlpha(1f);
        tvLocation.setVisibility(View.VISIBLE);
        
        // 延迟隐藏
        locationHandler.postDelayed(locationRunnable, LOCATION_HIDE_DELAY);
    }
    
    private void startProgressUpdate() {
        if (progressRunnable != null) {
            progressHandler.removeCallbacks(progressRunnable);
        }
        
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                updateProgress();
                progressHandler.postDelayed(this, 1000);
            }
        };
        
        progressHandler.post(progressRunnable);
    }
    
    private void stopProgressUpdate() {
        if (progressRunnable != null) {
            progressHandler.removeCallbacks(progressRunnable);
        }
    }
    
    private void updateProgress() {
        if (!isCurrentFileVideo()) return;
        
        long currentTime = 0;
        long totalTime = 0;
        
        if (useVlc && vlcMediaPlayer != null) {
            currentTime = vlcMediaPlayer.getTime();
            totalTime = vlcMediaPlayer.getLength();
        } else if (exoPlayer != null) {
            currentTime = exoPlayer.getCurrentPosition();
            totalTime = exoPlayer.getDuration();
            if (totalTime == com.google.android.exoplayer2.C.TIME_UNSET) {
                totalTime = 0;
            }
        }
        
        if (totalTime > 0) {
            seekbarProgress.setMax((int) totalTime);
            seekbarProgress.setProgress((int) currentTime);
            
            tvCurrentTime.setText(DateUtils.formatElapsedTime(currentTime / 1000));
            tvTotalTime.setText(DateUtils.formatElapsedTime(totalTime / 1000));
        }
    }
    
    private boolean isCurrentFileVideo() {
        FileInfo currentFile = viewModel.getCurrentFile();
        return currentFile != null && currentFile.isVideo();
    }
    
    private void getLocationForFile(FileInfo file, String mediaUrl) {
        if (file == null || mediaUrl == null) {
            android.util.Log.d("PlaybackActivity", "getLocationForFile: file or mediaUrl is null");
            return;
        }
        
        Boolean showLocation = viewModel.getShowLocation().getValue();
        if (showLocation == null || !showLocation) {
            android.util.Log.d("PlaybackActivity", "getLocationForFile: showLocation is false or null");
            return;
        }
        
        // 检查位置权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            android.util.Log.d("PlaybackActivity", "getLocationForFile: 位置权限未授予，请求权限");
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }
        
        // 使用独立进程的服务获取地点信息（避免GPS提取崩溃影响主进程）
        android.util.Log.d("PlaybackActivity", "开始获取地点信息（使用独立进程服务）: " + mediaUrl);
        
        android.os.ResultReceiver receiver = new android.os.ResultReceiver(new android.os.Handler(android.os.Looper.getMainLooper())) {
            @Override
            protected void onReceiveResult(int resultCode, android.os.Bundle resultData) {
                if (resultCode == com.baidu.tv.player.service.LocationExtractionService.RESULT_CODE_SUCCESS) {
                    String location = resultData.getString(com.baidu.tv.player.service.LocationExtractionService.RESULT_LOCATION);
                    android.util.Log.d("PlaybackActivity", "地点信息获取成功: " + location);
                    if (location != null && !location.isEmpty()) {
                        viewModel.setCurrentLocation(location);
                    } else {
                        viewModel.setCurrentLocation(null);
                    }
                } else {
                    android.util.Log.d("PlaybackActivity", "地点信息获取失败");
                    viewModel.setCurrentLocation(null);
                }
            }
        };
        
        boolean isVideo = file.isVideo();
        com.baidu.tv.player.service.LocationExtractionService.startExtraction(this, mediaUrl, isVideo, receiver);
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (layoutControls.getVisibility() == View.VISIBLE) {
                    hideControls();
                } else {
                    showControls();
                }
                return true;
                
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                viewModel.togglePlayPause();
                return true;
                
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                viewModel.playNext();
                return true;
                
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                viewModel.playPrevious();
                return true;
                
            case KeyEvent.KEYCODE_BACK:
                if (layoutControls.getVisibility() == View.VISIBLE) {
                    hideControls();
                    return true;
                }
                break;
        }
        
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_M) {
            Intent intent = new Intent(this, com.baidu.tv.player.ui.settings.SettingsActivity.class);
            startActivity(intent);
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        // 从SharedPreferences中读取播放模式并更新ViewModel
        int savedPlayMode = com.baidu.tv.player.utils.PreferenceUtils.getPlayMode(this);
        PlayMode newPlayMode = PlayMode.fromValue(savedPlayMode);
        viewModel.setPlayMode(newPlayMode);
        
        Boolean isPlaying = viewModel.getIsPlaying().getValue();
        if (isPlaying != null && isPlaying) {
            if (useVlc && vlcMediaPlayer != null && !vlcMediaPlayer.isPlaying()) {
                vlcMediaPlayer.play();
            } else if (exoPlayer != null) {
                exoPlayer.setPlayWhenReady(true);
            }
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        if (useVlc && vlcMediaPlayer != null && vlcMediaPlayer.isPlaying()) {
            vlcMediaPlayer.pause();
        } else if (exoPlayer != null && exoPlayer.isPlaying()) {
            exoPlayer.pause();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // 释放VLC资源
        if (vlcMediaPlayer != null) {
            vlcMediaPlayer.stop();
            vlcMediaPlayer.getVLCVout().detachViews();
            vlcMediaPlayer.release();
        }
        if (libVLC != null) {
            libVLC.release();
        }
        
        // 释放ExoPlayer资源
        if (exoPlayer != null) {
            exoPlayer.release();
        }
        
        // 清理Handler
        if (imageHandler != null) {
            imageHandler.removeCallbacksAndMessages(null);
        }
        if (controlsHandler != null) {
            controlsHandler.removeCallbacksAndMessages(null);
        }
        if (progressHandler != null) {
            progressHandler.removeCallbacksAndMessages(null);
        }
    }
}
