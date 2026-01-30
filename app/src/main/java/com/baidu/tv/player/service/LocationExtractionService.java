package com.baidu.tv.player.service;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.util.Log;

import com.baidu.tv.player.utils.LocationUtils;

import java.io.File;

/**
 * 在独立进程中执行GPS提取的服务
 * 防止因为内存溢出或native崩溃影响主进程
 */
public class LocationExtractionService extends IntentService {
    
    private static final String TAG = "LocationExtractionService";
    
    public static final String ACTION_EXTRACT_LOCATION = "com.baidu.tv.player.service.action.EXTRACT_LOCATION";
    public static final String EXTRA_URL = "com.baidu.tv.player.service.extra.URL";
    public static final String EXTRA_IS_VIDEO = "com.baidu.tv.player.service.extra.IS_VIDEO";
    public static final String EXTRA_RECEIVER = "com.baidu.tv.player.service.extra.RECEIVER";
    public static final String RESULT_LOCATION = "result_location";
    
    public static final int RESULT_CODE_SUCCESS = 1;
    public static final int RESULT_CODE_FAILURE = 0;

    public LocationExtractionService() {
        super("LocationExtractionService");
    }

    public static void startExtraction(Context context, String url, boolean isVideo, ResultReceiver receiver) {
        Intent intent = new Intent(context, LocationExtractionService.class);
        intent.setAction(ACTION_EXTRACT_LOCATION);
        intent.putExtra(EXTRA_URL, url);
        intent.putExtra(EXTRA_IS_VIDEO, isVideo);
        intent.putExtra(EXTRA_RECEIVER, receiver);
        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_EXTRACT_LOCATION.equals(action)) {
                final String url = intent.getStringExtra(EXTRA_URL);
                final boolean isVideo = intent.getBooleanExtra(EXTRA_IS_VIDEO, false);
                final ResultReceiver receiver = intent.getParcelableExtra(EXTRA_RECEIVER);
                
                handleExtraction(url, isVideo, receiver);
            }
        }
    }

    private void handleExtraction(String url, boolean isVideo, ResultReceiver receiver) {
        Log.d(TAG, "Starting extraction in separate process. PID: " + android.os.Process.myPid());
        String location = null;
        try {
            if (isVideo) {
                location = LocationUtils.getLocationFromVideo(this, url);
            } else {
                location = LocationUtils.getLocationFromImage(this, url);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during extraction: " + e.getMessage(), e);
        }

        if (receiver != null) {
            Bundle bundle = new Bundle();
            bundle.putString(RESULT_LOCATION, location);
            receiver.send(location != null ? RESULT_CODE_SUCCESS : RESULT_CODE_FAILURE, bundle);
        }
    }
}