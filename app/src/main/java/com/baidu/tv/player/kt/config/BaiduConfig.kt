package com.baidu.tv.player.kt.config

import com.baidu.tv.player.kt.BuildConfig

/**
 * 百度网盘 / 高德地图应用配置。
 *
 * 真实凭据从 gitignore 的 local.properties 读取，经 app/build.gradle.kts 的
 * buildConfigField 注入 BuildConfig，本对象仅做转发。密钥永不进入版本控制系统。
 *
 * local.properties 需配置的键（缺省为占位符，保持“未配置”语义）：
 *   baidu.app.id      → [APP_ID]
 *   baidu.app.key     → [APP_KEY]        （百度网盘 OAuth client_id）
 *   baidu.secret.key  → [SECRET_KEY]     （百度网盘 OAuth client_secret）
 *   baidu.sign.key    → [SIGN_KEY]
 *   amap.api.key      → [AMAP_API_KEY]   （高德逆地理编码 REST key）
 */
object BaiduConfig {
    // 百度网盘应用配置（来自 BuildConfig，值见 app/build.gradle.kts buildConfigField）
    val APP_ID: String = BuildConfig.BAIDU_APP_ID
    val APP_KEY: String = BuildConfig.BAIDU_APP_KEY
    val SECRET_KEY: String = BuildConfig.BAIDU_SECRET_KEY
    val SIGN_KEY: String = BuildConfig.BAIDU_SIGN_KEY

    // 高德地图 API 配置
    val AMAP_API_KEY: String = BuildConfig.AMAP_API_KEY

    // OAuth 配置
    const val REDIRECT_URI = "oob"
    const val SCOPE = "basic,netdisk"
    const val DEVICE_NAME = "度盘读天下"
}
