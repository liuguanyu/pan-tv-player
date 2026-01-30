package com.baidu.tv.player.network;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Retrofit客户端封装
 */
public class RetrofitClient {
    
    private static volatile Retrofit panApiInstance;
    private static volatile Retrofit oauthInstance;
    
    /**
     * 获取百度网盘API的Retrofit实例
     */
    public static Retrofit getPanApiInstance() {
        if (panApiInstance == null) {
            synchronized (RetrofitClient.class) {
                if (panApiInstance == null) {
                    panApiInstance = createRetrofit(ApiConstants.PAN_API_BASE_URL);
                }
            }
        }
        return panApiInstance;
    }
    
    /**
     * 获取OAuth API的Retrofit实例
     */
    public static Retrofit getOAuthInstance() {
        if (oauthInstance == null) {
            synchronized (RetrofitClient.class) {
                if (oauthInstance == null) {
                    oauthInstance = createRetrofit(ApiConstants.OAUTH_BASE_URL);
                }
            }
        }
        return oauthInstance;
    }
    
    /**
     * 创建Retrofit实例
     */
    private static Retrofit createRetrofit(String baseUrl) {
        OkHttpClient.Builder httpClientBuilder = new OkHttpClient.Builder();
        
        // 添加日志拦截器
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
        httpClientBuilder.addInterceptor(loggingInterceptor);
        
        // 设置超时时间
        httpClientBuilder.connectTimeout(ApiConstants.CONNECT_TIMEOUT, TimeUnit.MILLISECONDS);
        httpClientBuilder.readTimeout(ApiConstants.READ_TIMEOUT, TimeUnit.MILLISECONDS);
        httpClientBuilder.writeTimeout(ApiConstants.WRITE_TIMEOUT, TimeUnit.MILLISECONDS);
        
        // 添加User-Agent
        httpClientBuilder.addInterceptor(chain -> {
            okhttp3.Request original = chain.request();
            okhttp3.Request request = original.newBuilder()
                    .header("User-Agent", "pan.baidu.com")
                    .method(original.method(), original.body())
                    .build();
            return chain.proceed(request);
        });
        
        OkHttpClient client = httpClientBuilder.build();
        
        return new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }
}