package com.baidu.tv.player.kt.di

import javax.inject.Qualifier

/** 标注百度网盘 API（pan.baidu.com）的 Retrofit / BaiduPanService 实例。 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PanApi

/** 标注百度 OAuth API（openapi.baidu.com）的 Retrofit 实例。 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OAuthApi
