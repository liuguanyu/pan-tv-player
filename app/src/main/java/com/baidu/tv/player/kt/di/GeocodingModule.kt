package com.baidu.tv.player.kt.di

import com.baidu.tv.player.kt.location.geocoding.AmapGeocodingStrategy
import com.baidu.tv.player.kt.location.geocoding.AndroidGeocoderStrategy
import com.baidu.tv.player.kt.location.geocoding.GeocodingStrategy
import com.baidu.tv.player.kt.location.geocoding.NominatimGeocodingStrategy
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * 逆地理编码策略 Multibinding 模块（对应 tasks.md 7.8）。
 *
 * 将三种策略（Amap / AndroidGeocoder / Nominatim）绑定到同一个 Set，
 * 供 [com.baidu.tv.player.kt.location.geocoding.GeocodingFactory] 按优先级排序与回退，
 * 完全避免手动 getInstance 单例。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class GeocodingModule {

    @Binds
    @IntoSet
    abstract fun bindAmap(strategy: AmapGeocodingStrategy): GeocodingStrategy

    @Binds
    @IntoSet
    abstract fun bindAndroidGeocoder(strategy: AndroidGeocoderStrategy): GeocodingStrategy

    @Binds
    @IntoSet
    abstract fun bindNominatim(strategy: NominatimGeocodingStrategy): GeocodingStrategy
}
