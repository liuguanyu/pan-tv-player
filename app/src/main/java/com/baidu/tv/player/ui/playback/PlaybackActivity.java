
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
import com.baidu.tv.player.ui.view.BlindsImageView;
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
 * 默认使用 ExoPlayer (主力播放器)，失败时自动切换到 VLC (备用播放器)
 *
 * 播放器策略：
 * 1. 主力使用 ExoPlayer - Google 官方推荐，性能更好，适合 Android TV
 * 2. ExoPlayer 失败时自动切换到 VLC - 支持更多格式（HEVC/H.265 等）
 * 3. 两者都失败则跳过当前文件
 */
public class PlaybackActivity extends FragmentActivity {
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    
    private PlaybackViewModel viewModel;
    private PlaybackHistoryRepository historyRepository;
    private com.baidu.tv.player.auth.AuthRepository authRepository;
    
    // UI组件
    private SurfaceView surfaceView; // VLC Surface
    private PlayerView playerView;   // ExoPlayer View
    private BlindsImageView ivImageDisplay;
    private View layoutControls;
    private TextView tvFileName;
    private TextView tvLocation;
    private TextView tvPlayerIndicator; // 播放器指示器
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
    
    // ExoPlayer 播放器 (主力播放器)
    private ExoPlayer exoPlayer;
    
    // 播放模式：true使用VLC，false使用ExoPlayer
    // 默认使用 ExoPlayer (主力播放器)，失败时切换到 VLC
    private boolean useVlc = false;
    
    // 错误重试计数
    private int vlcErrorCount = 0;
    private static final int MAX_VLC_RETRIES = 2;
    
    // ExoPlayer 错误重试计数
    private int exoErrorCount = 0;
    private static final int MAX_EXO_RETRIES = 1;
    
    // 当前播放的URL
    private String currentMediaUrl = null;
    
    // 记录最后一次prepare的时间，用于性能分析
    private long lastPrepareTime = 0;
    
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
    
    @Override
    protected void onResume() {
        super.onResume();
        android.util.Log.d("PlaybackActivity", "onResume called");
        
        // 从设置页返回时重新加载配置
        viewModel.reloadSettings();
        
        FileInfo currentFile = viewModel.getCurrentFile();
        if (currentFile == null) {
            android.util.Log.d("PlaybackActivity", "onResume: currentFile is null");
            return;
        }
        
        Boolean isPlaying = viewModel.getIsPlaying().getValue();
        if (isPlaying != null && isPlaying) {
            if (currentFile.isVideo()) {
                // 视频：恢复播放
                android.util.Log.d("PlaybackActivity", "onResume: 恢复视频播放");
                if (useVlc && vlcMediaPlayer != null && !vlcMediaPlayer.isPlaying()) {
                    vlcMediaPlayer.play();
                } else if (exoPlayer != null && !exoPlayer.isPlaying()) {
                    exoPlayer.setPlayWhenReady(true);
                }
            } else if (currentFile.isImage()) {
                // 图片：需要重新显示图片（因为可能已经被清空或隐藏）
                android.util.Log.d("PlaybackActivity", "onResume: 重新显示图片");
                
                // 确保图片显示View可见
                ivImageDisplay.setVisibility(View.VISIBLE);
                surfaceView.setVisibility(View.GONE);
                playerView.setVisibility(View.GONE);
                
                // 如果图片当前不可见或为空，重新加载
                String mediaUrl = viewModel.getPreparedMediaUrl().getValue();
                if (mediaUrl != null && !mediaUrl.isEmpty()) {
                    // 如果ImageView中没有图片，或者我们想确保它被刷新
                    if (ivImageDisplay.getDrawable() == null) {
                        android.util.Log.d("PlaybackActivity", "onResume: 重新加载图片 URL");
                        playImageWithUrl(mediaUrl);
                    }
                }
                
                // 重新启动定时器以应用新的显示时长
                startImageDisplayTimer();
            }
        }
    }

    private void initViews() {
        surfaceView = findViewById(R.id.surface_view);
        playerView = findViewById(R.id.player_view);
        ivImageDisplay = findViewById(R.id.iv_image_display);
        layoutControls = findViewById(R.id.layout_controls);
        tvFileName = findViewById(R.id.tv_file_name);
        tvLocation = findViewById(R.id.tv_location);
        tvPlayerIndicator = findViewById(R.id.tv_player_indicator);
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
            // 使用硬件加速，默认设置（让VLC自动选择最佳解码器）
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
                        
                        // 判断视频是横屏还是竖屏 (使用最终显示比例判断)
                        boolean isLandscape = finalVideoRatio >= 1.0;
                        
                        android.widget.FrameLayout.LayoutParams lp =
                            (android.widget.FrameLayout.LayoutParams) surfaceView.getLayoutParams();
                        
                        if (isLandscape) {
                            // 横屏视频：尽量充满全屏 (CenterCrop 模式)
                            // 计算能够填满屏幕的 SurfaceView 尺寸，同时保持视频比例
                            int surfaceWidth, surfaceHeight;
                            
                            if (finalVideoRatio > screenRatio) {
                                // 视频比屏幕更宽 (例如 21:9 在 16:9 屏幕)
                                // 以高度为基准填满屏幕，宽度超出屏幕 (裁剪左右)
                                surfaceHeight = screenHeight;
                                surfaceWidth = (int) (screenHeight * finalVideoRatio);
                            } else {
                                // 视频比屏幕更窄 (例如 4:3 在 16:9 屏幕)
                                // 以宽度为基准填满屏幕，高度超出屏幕 (裁剪上下)
                                surfaceWidth = screenWidth;
                                surfaceHeight = (int) (screenWidth / finalVideoRatio);
                            }
                            
                            lp.width = surfaceWidth;
                            lp.height = surfaceHeight;
                            lp.gravity = android.view.Gravity.CENTER;
                            surfaceView.setLayoutParams(lp);
                            
                            // 不设置宽高比，让VLC自动适应SurfaceView
                            // 由于SurfaceView的比例已调整为和视频一致，VLC会自然充满SurfaceView
                            vlcMediaPlayer.setAspectRatio(null);
                            vlcMediaPlayer.setScale(0);
                            
                            android.util.Log.d("PlaybackActivity", String.format(
                                "横屏视频 - CenterCrop模式: SurfaceView=%dx%d (Screen=%dx%d)",
                                surfaceWidth, surfaceHeight, screenWidth, screenHeight));
                        } else {
                            // 竖屏视频：等比占满纵轴 (FitCenter 模式)
                            // SurfaceView保持全屏
                            lp.width = screenWidth;
                            lp.height = screenHeight;
                            lp.gravity = android.view.Gravity.CENTER;
                            surfaceView.setLayoutParams(lp);
                            
                            // 计算目标宽高比字符串，保持视频比例
                            String targetAspectRatio;
                            if (finalVideoRatio > screenRatio) {
                                // 视频比屏幕宽（罕见情况）：宽度占满
                                targetAspectRatio = screenWidth + ":" + (int)(screenWidth / finalVideoRatio);
                            } else {
                                // 视频比屏幕窄：高度占满
                                targetAspectRatio = (int)(screenHeight * finalVideoRatio) + ":" + screenHeight;
                            }
                            
                            vlcMediaPlayer.setAspectRatio(targetAspectRatio);
                            vlcMediaPlayer.setScale(0);
                            
                            android.util.Log.d("PlaybackActivity", String.format(
                                "竖屏视频 - FitCenter模式: AspectRatio=%s, SurfaceView=%dx%d",
                                targetAspectRatio, screenWidth, screenHeight));
                        }
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
                        handleVlcError();
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
        if (exoPlayer == null) {
            // 检测是否为模拟器（提前检测，用于后续配置）
            boolean isEmulator = android.os.Build.FINGERPRINT.contains("generic") ||
                                android.os.Build.FINGERPRINT.contains("vbox") ||
                                android.os.Build.PRODUCT.contains("sdk") ||
                                android.os.Build.MODEL.contains("Emulator");
            
            android.util.Log.d("PlaybackActivity", "设备类型: " + (isEmulator ? "模拟器" : "真机"));
            
            // 初始化ExoPlayer，配置更好的解码器和渲染策略
            // 针对模拟器和真机使用不同的缓冲策略
            com.google.android.exoplayer2.DefaultLoadControl loadControl;
            
            if (isEmulator) {
                // 模拟器：极致激进的启动策略
                loadControl = new com.google.android.exoplayer2.DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        5000,   // minBufferMs: 最小缓冲5秒
                        15000,  // maxBufferMs: 最大缓冲15秒
                        200,    // bufferForPlaybackMs: 仅缓冲0.2秒即开始播放
                        800     // bufferForPlaybackAfterRebufferMs: 重新缓冲0.8秒
                    )
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .setBackBuffer(0, false) // 禁用后向缓冲
                    .build();
                android.util.Log.d("PlaybackActivity", "使用模拟器优化配置：超低延迟启动");
            } else {
                // 真机：平衡的策略
                loadControl = new com.google.android.exoplayer2.DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        10000,  // minBufferMs: 最小缓冲10秒
                        30000,  // maxBufferMs: 最大缓冲30秒
                        500,    // bufferForPlaybackMs: 开始播放前缓冲0.5秒
                        1500    // bufferForPlaybackAfterRebufferMs: 重新缓冲后需要1.5秒
                    )
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build();
            }
            
            // 优化渲染器工厂：优先使用硬件解码器
            // 渲染器工厂配置
            com.google.android.exoplayer2.DefaultRenderersFactory renderersFactory =
                new com.google.android.exoplayer2.DefaultRenderersFactory(this)
                    // 使用ON模式，允许扩展解码器但不优先
                    .setExtensionRendererMode(
                        com.google.android.exoplayer2.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                    )
                    .setEnableDecoderFallback(true)
                    .setAllowedVideoJoiningTimeMs(2000); // 进一步减少视频连接时间

            // 在真机上启用异步队列可以提高性能，但在模拟器上可能导致问题
            if (!isEmulator) {
                renderersFactory.forceEnableMediaCodecAsynchronousQueueing();
            }
            
            // 配置自定义 DataSource.Factory 以添加百度网盘所需的请求头
            // 使用标准的User-Agent，模拟Android设备，避免被服务器识别为异常客户端
            String userAgent = com.google.android.exoplayer2.util.Util.getUserAgent(this, "BaiduTVPlayer");
            
            com.google.android.exoplayer2.upstream.DefaultHttpDataSource.Factory httpDataSourceFactory =
                new com.google.android.exoplayer2.upstream.DefaultHttpDataSource.Factory()
                    .setUserAgent(userAgent)
                    .setConnectTimeoutMs(15000) // 减少连接超时到15秒
                    .setReadTimeoutMs(15000)    // 减少读取超时到15秒
                    .setAllowCrossProtocolRedirects(true)
                    .setKeepPostFor302Redirects(true);
            
            // 使用带带宽测量的DataSource，有助于ExoPlayer调整缓冲策略
            com.google.android.exoplayer2.upstream.DefaultBandwidthMeter bandwidthMeter =
                new com.google.android.exoplayer2.upstream.DefaultBandwidthMeter.Builder(this).build();
                
            com.google.android.exoplayer2.upstream.DefaultDataSource.Factory dataSourceFactory =
                new com.google.android.exoplayer2.upstream.DefaultDataSource.Factory(this, httpDataSourceFactory)
                    .setTransferListener(bandwidthMeter);
            
            exoPlayer = new ExoPlayer.Builder(this)
                .setRenderersFactory(renderersFactory)
                .setLoadControl(loadControl)
                .setMediaSourceFactory(
                    new com.google.android.exoplayer2.source.DefaultMediaSourceFactory(this)
                        .setDataSourceFactory(dataSourceFactory)
                )
                .build();
            
            playerView.setPlayer(exoPlayer);
            
            // 设置视频缩放模式为自适应
            playerView.setResizeMode(com.google.android.exoplayer2.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT);
            
            // 不保持内容在播放器重置时不变，确保切换视频时清除上一帧
            playerView.setKeepContentOnPlayerReset(false);
            
            // 监听播放器状态变化
            exoPlayer.addListener(new Player.Listener() {
                @Override
                public void onPlayerError(com.google.android.exoplayer2.PlaybackException error) {
                    android.util.Log.e("PlaybackActivity", "ExoPlayer error: " + error.getMessage(), error);
                    android.util.Log.e("PlaybackActivity", "Error type: " + error.errorCode);
                    
                    // 特别处理解码器错误
                    if (error.errorCode == com.google.android.exoplayer2.PlaybackException.ERROR_CODE_DECODING_FAILED ||
                        error.errorCode == com.google.android.exoplayer2.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED) {
                        android.util.Log.e("PlaybackActivity", "⚠️ 解码器错误，可能是不支持的视频格式，准备切换到VLC");
                    }
                    
                    handleExoPlayerError();
                }
                
                @Override
                public void onVideoSizeChanged(com.google.android.exoplayer2.video.VideoSize videoSize) {
                    int width = videoSize.width;
                    int height = videoSize.height;
                    android.util.Log.d("PlaybackActivity", "ExoPlayer 视频尺寸: " + width + "x" + height);
                    
                    // 根据视频是横屏还是竖屏设置不同的缩放模式
                    if (width >= height) {
                        // 横屏视频：使用 ZOOM 模式填满屏幕 (CenterCrop)
                        playerView.setResizeMode(com.google.android.exoplayer2.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
                        android.util.Log.d("PlaybackActivity", "横屏视频，使用 RESIZE_MODE_ZOOM (CenterCrop)");
                    } else {
                        // 竖屏视频：使用 FIT 模式保持比例 (FitCenter，高度占满)
                        playerView.setResizeMode(com.google.android.exoplayer2.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT);
                        android.util.Log.d("PlaybackActivity", "竖屏视频，使用 RESIZE_MODE_FIT (FitCenter)");
                    }
                }

                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    String stateName;
                    switch (playbackState) {
                        case Player.STATE_IDLE:
                            stateName = "IDLE";
                            break;
                        case Player.STATE_BUFFERING:
                            stateName = "BUFFERING";
                            break;
                        case Player.STATE_READY:
                            stateName = "READY";
                            break;
                        case Player.STATE_ENDED:
                            stateName = "ENDED";
                            break;
                        default:
                            stateName = "UNKNOWN";
                    }
                    android.util.Log.d("PlaybackActivity", "ExoPlayer state changed: " + stateName);
                    
                    if (playbackState == Player.STATE_READY) {
                        long readyTime = System.currentTimeMillis();
                        android.util.Log.d("PlaybackActivity", "ExoPlayer is ready, hiding loading indicator");
                        android.util.Log.d("PlaybackActivity", "从prepare到READY的总耗时: " +
                            (readyTime - lastPrepareTime) + "ms");
                        
                        // 检查视频轨道信息
                        if (exoPlayer != null) {
                            com.google.android.exoplayer2.Tracks tracks = exoPlayer.getCurrentTracks();
                            boolean hasVideo = false;
                            boolean hasAudio = false;
                            
                            for (com.google.android.exoplayer2.Tracks.Group trackGroup : tracks.getGroups()) {
                                if (trackGroup.getType() == com.google.android.exoplayer2.C.TRACK_TYPE_VIDEO && trackGroup.isSelected()) {
                                    hasVideo = true;
                                    android.util.Log.d("PlaybackActivity", "✓ 检测到视频轨道");
                                }
                                if (trackGroup.getType() == com.google.android.exoplayer2.C.TRACK_TYPE_AUDIO && trackGroup.isSelected()) {
                                    hasAudio = true;
                                    android.util.Log.d("PlaybackActivity", "✓ 检测到音频轨道");
                                }
                            }
                            
                            android.util.Log.d("PlaybackActivity", "视频轨道: " + hasVideo + ", 音频轨道: " + hasAudio);
                            
                            // 如果有音频但没有视频，可能是渲染问题
                            if (hasAudio && !hasVideo) {
                                android.util.Log.w("PlaybackActivity", "⚠️ 警告：检测到音频但没有视频轨道");
                            }
                        }
                        
                        loadingIndicator.setVisibility(View.GONE);
                        updatePlayPauseButton(true);
                        updateProgress();
                        // ExoPlayer 成功播放，重置错误计数
                        exoErrorCount = 0;
                    } else if (playbackState == Player.STATE_BUFFERING) {
                        android.util.Log.d("PlaybackActivity", "ExoPlayer is buffering, showing loading indicator");
                        loadingIndicator.setVisibility(View.VISIBLE);
                    } else if (playbackState == Player.STATE_ENDED) {
                        android.util.Log.d("PlaybackActivity", "ExoPlayer playback ended, playing next");
                        viewModel.playNext();
                    }
                }
                
                @Override
                public void onRenderedFirstFrame() {
                    android.util.Log.d("PlaybackActivity", "✓ ExoPlayer 渲染了第一帧视频");
                }
            });
        }
    }

    /**
     * 处理 ExoPlayer 播放错误，尝试切换到 VLC
     */
    private void handleExoPlayerError() {
        exoErrorCount++;
        loadingIndicator.setVisibility(View.GONE);
        
        // 对于解码器错误，直接切换到 VLC，不重试
        // 解码器错误通常意味着设备不支持该视频格式，重试没有意义
        if (exoErrorCount > 1) {
            // ExoPlayer 彻底失败，切换到 VLC
            android.util.Log.d("PlaybackActivity", "ExoPlayer失败次数过多，切换到VLC");
            Toast.makeText(this, "ExoPlayer播放失败，切换到VLC播放器", Toast.LENGTH_SHORT).show();
            
            // 释放 ExoPlayer
            if (exoPlayer != null) {
                exoPlayer.stop();
            }
            
            // 切换到 VLC
            useVlc = true;
            exoErrorCount = 0;
            updatePlayerIndicator();
            
            // 重新尝试播放
            if (currentMediaUrl != null) {
                playVideoWithUrl(currentMediaUrl);
            } else {
                viewModel.playNext();
            }
        } else {
            // 只重试一次
            android.util.Log.d("PlaybackActivity", "ExoPlayer错误，尝试重试 (1/1)");
            if (exoPlayer != null) {
                exoPlayer.prepare();
                exoPlayer.play();
            }
        }
    }

    private void initViewModel() {
        viewModel = new ViewModelProvider(this).get(PlaybackViewModel.class);
        
        // 初始化播放器指示器（在 viewModel 创建后）
        updatePlayerIndicator();
        
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
        
        // 初始化Handler
        imageHandler = new Handler();
        controlsHandler = new Handler();
        progressHandler = new Handler();
        
        locationHandler = new Handler();
        locationRunnable = () -> {
            // 10秒后淡出地点信息
            tvLocation.animate()
                    .alpha(0f)
                    .setDuration(1000)
                    .withEndAction(() -> tvLocation.setVisibility(View.GONE))
                    .start();
        };
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
                                    // 保存播放列表ID到ViewModel，用于更新播放进度
                                    viewModel.setPlaylistDatabaseId(playlistDatabaseId);
                                    
                                    // 设置播放列表，并强制根据当前播放模式重置初始索引
                                    viewModel.setPlayList(files, true);
                                    
                                    // 注意：不再手动设置startIndex，让setPlayList()根据播放模式自动处理
                                    // 倒序模式会自动从最后一个开始，顺序/随机模式会从第一个开始
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
        // 每次播放新文件时，重置为使用ExoPlayer（优先）
        // 除非是因为ExoPlayer播放失败导致的重试（这种情况下由handleExoPlayerError处理，不会重新调playCurrentFile）
        useVlc = false;
        exoErrorCount = 0;
        vlcErrorCount = 0;
        
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
        
        // 立即隐藏之前的地点信息
        tvLocation.setVisibility(View.GONE);
        tvLocation.setAlpha(1.0f); // 重置透明度
        if (locationHandler != null && locationRunnable != null) {
            locationHandler.removeCallbacks(locationRunnable);
        }
        
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
        updatePlayerIndicator();
        
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
                // 启用硬件解码以提高性能，同时保留软件解码作为备选
                media.setHWDecoderEnabled(true, true);
                // 添加媒体选项
                media.addOption(":network-caching=1500"); // 减少网络缓存到1.5秒
                
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

                        // 启动进度更新
                        startProgressUpdate();
                    }
                }, 100);
            }
        } else {
            // 使用 ExoPlayer 播放
            surfaceView.setVisibility(View.GONE);
            playerView.setVisibility(View.VISIBLE);
            
            if (videoUrl != null && !videoUrl.isEmpty()) {
                long startTime = System.currentTimeMillis();
                android.util.Log.d("PlaybackActivity", "开始ExoPlayer准备: " + videoUrl);
                
                // 清除之前的媒体项，防止上一个视频的帧残留
                exoPlayer.clearMediaItems();
                
                MediaItem mediaItem = MediaItem.fromUri(videoUrl);
                exoPlayer.setMediaItem(mediaItem);
                
                // 记录prepare开始时间
                lastPrepareTime = System.currentTimeMillis();
                long prepareStartTime = lastPrepareTime;
                exoPlayer.prepare();
                android.util.Log.d("PlaybackActivity", "ExoPlayer.prepare() 调用完成，耗时: " +
                    (System.currentTimeMillis() - prepareStartTime) + "ms");
                
                // 设置播放状态为true，确保自动播放
                viewModel.setPlaying(true);
                exoPlayer.setPlayWhenReady(true);
                
                android.util.Log.d("PlaybackActivity", "ExoPlayer准备完成，总耗时: " +
                    (System.currentTimeMillis() - startTime) + "ms");
                
                // 启动进度更新
                startProgressUpdate();
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
        updatePlayerIndicator();
        
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

            // 取消当前正在进行的动画，避免与新动画冲突
            ivImageDisplay.animate().cancel();
            
            // 对于FADE效果使用Glide的CrossFade，其他效果不使用CrossFade避免冲突
            DrawableTransitionOptions transitionOptions;
            if (actualEffect == ImageEffect.FADE) {
                transitionOptions = DrawableTransitionOptions.withCrossFade(800);
            } else {
                // 其他效果使用更快的CrossFade或不使用
                transitionOptions = DrawableTransitionOptions.withCrossFade(300);
            }
            
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
                            // 对于非FADE效果，需要先设置初始状态再开始动画
                            new Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                applyImageEffect(actualEffect);
                            }, actualEffect == ImageEffect.FADE ? 0 : 150);
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
        
        // 先重置到中性状态，避免残留的变换影响新动画
        ivImageDisplay.setScaleX(1.0f);
        ivImageDisplay.setScaleY(1.0f);
        ivImageDisplay.setTranslationX(0);
        ivImageDisplay.setTranslationY(0);
        ivImageDisplay.setRotation(0);
        ivImageDisplay.setAlpha(1.0f);
        
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
                // 跳动效果：先缩小再弹回
                ivImageDisplay.setScaleX(0.8f);
                ivImageDisplay.setScaleY(0.8f);
                ivImageDisplay.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(800)
                        .setInterpolator(new android.view.animation.BounceInterpolator())
                        .start();
                break;
                
            case EASE:
                // 缓动效果：从右侧滑入
                ivImageDisplay.setTranslationX(80f);
                ivImageDisplay.animate()
                        .translationX(0f)
                        .setDuration(800)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator())
                        .start();
                break;
                
            case BLINDS:
                // 百叶窗效果：启动自定义动画
                ivImageDisplay.resetBlinds();
                ivImageDisplay.post(() -> ivImageDisplay.startBlindsAnimation());
                break;
                
            case ZOOM:
                // 放大效果：从中心放大
                ivImageDisplay.setScaleX(0.7f);
                ivImageDisplay.setScaleY(0.7f);
                ivImageDisplay.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(800)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator())
                        .start();
                break;
                
            case ROTATE:
                // 旋转效果：从旋转状态恢复
                ivImageDisplay.setRotation(180f);
                ivImageDisplay.animate()
                        .rotation(0f)
                        .setDuration(800)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator())
                        .start();
                break;
                
            case SLIDE:
                // 滑入效果：从左侧滑入并放大
                ivImageDisplay.setTranslationX(-100f);
                ivImageDisplay.setScaleX(0.9f);
                ivImageDisplay.setScaleY(0.9f);
                ivImageDisplay.animate()
                        .translationX(0f)
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(800)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator())
                        .start();
                break;
                
            case FADE:
            default:
                // FADE效果完全由Glide的CrossFade处理，不需要额外动画
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
            exoPlayer.clearMediaItems(); // 确保完全清除
        }
        
        // 确保PlayerView不显示旧内容
        if (playerView != null) {
            // 这会触发快门显示，遮挡旧视频帧
            playerView.setPlayer(null);
            playerView.setPlayer(exoPlayer);
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
    
    private void updatePlayerIndicator() {
        if (tvPlayerIndicator == null) return;
        
        // 始终隐藏 UI 指示器，只在日志中记录当前使用的播放器
        tvPlayerIndicator.setVisibility(View.GONE);
        
        // 如果 viewModel 还未初始化，直接返回
        if (viewModel == null) return;
        
        if (!isCurrentFileVideo()) return;
        
        String playerName = useVlc ? "VLC Player" : "ExoPlayer";
        android.util.Log.d("PlaybackActivity", "当前使用的播放器: " + playerName);
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
        // 记录请求时的文件ID，用于验证结果是否匹配当前文件
        final long requestFsId = file.getFsId();
        
        android.os.ResultReceiver receiver = new android.os.ResultReceiver(new android.os.Handler(android.os.Looper.getMainLooper())) {
            @Override
            protected void onReceiveResult(int resultCode, android.os.Bundle resultData) {
                // 检查当前文件是否仍然是请求时的文件
                FileInfo currentFile = viewModel.getCurrentFile();
                if (currentFile == null || currentFile.getFsId() != requestFsId) {
                    android.util.Log.d("PlaybackActivity", "忽略过期的地点信息回调 (文件已切换)");
                    return;
                }
                
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
    
    /**
     * 处理VLC播放错误，实现智能降级策略
     * 针对H.265/HEVC（特别是苹果设备拍摄）和高帧率视频的兼容性问题
     */
    private void handleVlcError() {
        vlcErrorCount++;
        android.util.Log.w("PlaybackActivity", "VLC播放错误 (错误次数: " + vlcErrorCount + "/" + MAX_VLC_RETRIES + ")");
        
        FileInfo currentFile = viewModel.getCurrentFile();
        if (currentFile == null) {
            Toast.makeText(this, "播放错误", Toast.LENGTH_SHORT).show();
            viewModel.playNext();
            return;
        }
        
        String fileName = currentFile.getServerFilename();
        boolean isHevc = fileName.toLowerCase().contains("hevc") ||
                        fileName.toLowerCase().contains("h265") ||
                        fileName.toLowerCase().contains("h.265");
        
        if (vlcErrorCount >= MAX_VLC_RETRIES) {
            // 达到最大重试次数，切换到ExoPlayer或跳过
            if (!useVlc) {
                // 已经在使用ExoPlayer，跳过此文件
                Toast.makeText(this,
                    "无法播放此文件 (" + (isHevc ? "H.265编码可能不受支持" : "格式不兼容") + ")，跳过",
                    Toast.LENGTH_LONG).show();
                vlcErrorCount = 0; // 重置计数器
                viewModel.playNext();
            } else {
                // 尝试切换到ExoPlayer
                Toast.makeText(this,
                    "VLC播放失败，尝试使用ExoPlayer" + (isHevc ? " (H.265)" : ""),
                    Toast.LENGTH_SHORT).show();
                vlcErrorCount = 0; // 重置计数器
                useVlc = false;
                updatePlayerIndicator();
                
                // 重新播放当前文件
                if (currentMediaUrl != null) {
                    playVideoWithUrl(currentMediaUrl);
                } else {
                    viewModel.playNext();
                }
            }
        } else {
            // 还未达到最大重试次数，尝试软解码
            Toast.makeText(this,
                "播放错误，尝试软解码" + (isHevc ? " (H.265视频)" : ""),
                Toast.LENGTH_SHORT).show();
            
            // 重新初始化VLC，使用软解码
            try {
                if (vlcMediaPlayer != null) {
                    vlcMediaPlayer.stop();
                    vlcMediaPlayer.release();
                }
                if (libVLC != null) {
                    libVLC.release();
                }
                
                // 使用软解码选项重新初始化
                ArrayList<String> options = new ArrayList<>();
                options.add("-vvv");
                options.add("--avcodec-hw=none"); // 强制软解码
                options.add("--network-caching=2000");
                
                libVLC = new LibVLC(this, options);
                vlcMediaPlayer = new MediaPlayer(libVLC);
                
                IVLCVout vout = vlcMediaPlayer.getVLCVout();
                vout.setVideoView(surfaceView);
                vout.attachViews();
                
                // 重新设置事件监听
                vlcMediaPlayer.setEventListener(event -> {
                    switch (event.type) {
                        case MediaPlayer.Event.Playing:
                            loadingIndicator.setVisibility(View.GONE);
                            updatePlayPauseButton(true);
                            startProgressUpdate();
                            break;
                        case MediaPlayer.Event.EncounteredError:
                            loadingIndicator.setVisibility(View.GONE);
                            handleVlcError();
                            break;
                    }
                });
                
                // 重新播放
                if (currentMediaUrl != null) {
                    playVideoWithUrl(currentMediaUrl);
                }
            } catch (Exception e) {
                android.util.Log.e("PlaybackActivity", "重新初始化VLC失败", e);
                Toast.makeText(this, "播放器初始化失败，跳过此文件", Toast.LENGTH_SHORT).show();
                vlcErrorCount = 0;
                viewModel.playNext();
            }
        }
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // 如果控制栏显示，处理方向键导航和确认键
        if (layoutControls.getVisibility() == View.VISIBLE) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    // 如果有焦点的控件，触发点击
                    View focusedView = getCurrentFocus();
                    if (focusedView != null) {
                        focusedView.performClick();
                        return true;
                    }
                    // 否则隐藏控制栏
                    hideControls();
                    return true;
                    
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    // 重置隐藏计时器
                    showControls();
                    // 让系统处理焦点导航
                    return super.onKeyDown(keyCode, event);
                    
                case KeyEvent.KEYCODE_BACK:
                    hideControls();
                    return true;
            }
        } else {
            // 控制栏隐藏时的快捷键处理
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    // 显示控制栏并让播放/暂停按钮获取焦点
                    showControls();
                    ivPlayPause.requestFocus();
                    return true;
                    
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    // 快退 10秒 或 上一张图片
                    if (isCurrentFileVideo()) {
                        seekBy(-10000);
                        showControls();
                    } else {
                        viewModel.playPrevious();
                    }
                    return true;
                    
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    // 快进 10秒 或 下一张图片
                    if (isCurrentFileVideo()) {
                        seekBy(10000);
                        showControls();
                    } else {
                        viewModel.playNext();
                    }
                    return true;
            }
        }
        
        // 全局快捷键
        switch (keyCode) {
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                viewModel.togglePlayPause();
                showControls();
                return true;
                
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                viewModel.playNext();
                showControls();
                return true;
                
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                viewModel.playPrevious();
                showControls();
                return true;
                
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_M:
                Intent intent = new Intent(this, com.baidu.tv.player.ui.settings.SettingsActivity.class);
                startActivity(intent);
                return true;
                
            case KeyEvent.KEYCODE_BACK:
                if (layoutControls.getVisibility() == View.VISIBLE) {
                    hideControls();
                    return true;
                }
                break;
        }

        return super.onKeyDown(keyCode, event);
    }
    
    /**
     * 快进/快退
     * @param offsetMs 偏移量（毫秒）
     */
    private void seekBy(long offsetMs) {
        long currentTime = 0;
        long totalTime = 0;
        
        if (useVlc && vlcMediaPlayer != null) {
            currentTime = vlcMediaPlayer.getTime();
            totalTime = vlcMediaPlayer.getLength();
            
            long newTime = Math.max(0, Math.min(totalTime, currentTime + offsetMs));
            vlcMediaPlayer.setTime(newTime);
            
            // 更新UI
            tvCurrentTime.setText(DateUtils.formatElapsedTime(newTime / 1000));
            seekbarProgress.setProgress((int) newTime);
            
        } else if (exoPlayer != null) {
            currentTime = exoPlayer.getCurrentPosition();totalTime = exoPlayer.getDuration();
            
            if (totalTime != com.google.android.exoplayer2.C.TIME_UNSET) {
                long newTime = Math.max(0, Math.min(totalTime, currentTime + offsetMs));
                exoPlayer.seekTo(newTime);
                
                // 更新UI
                tvCurrentTime.setText(DateUtils.formatElapsedTime(newTime / 1000));
                seekbarProgress.setProgress((int) newTime);
            }
        }
        
        // 显示快进/快退提示
        String text = offsetMs > 0 ? "+" + (offsetMs/1000) + "s" : (offsetMs/1000) + "s";
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
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
