package com.baidu.tv.player;

import android.app.Application;
import com.baidu.tv.player.database.AppDatabase;

/**
 * 应用程序类
 */
public class BaiduTVApplication extends Application {
    
    private static BaiduTVApplication instance;
    private AppDatabase database;
    
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        
        // 初始化数据库
        database = AppDatabase.getInstance(this);
    }
    
    public static BaiduTVApplication getInstance() {
        return instance;
    }
    
    public AppDatabase getDatabase() {
        return database;
    }
}