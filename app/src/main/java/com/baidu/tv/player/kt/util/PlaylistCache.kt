package com.baidu.tv.player.kt.util

import com.baidu.tv.player.kt.model.FileInfo
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistCache @Inject constructor() {
    private val cache = ConcurrentHashMap<String, List<FileInfo>>()

    fun put(key: String, playlist: List<FileInfo>) {
        cache[key] = playlist.toList()
    }

    fun getAndRemove(key: String): List<FileInfo>? = cache.remove(key)?.toList()

    operator fun get(key: String): List<FileInfo>? = cache[key]?.toList()

    fun clear() {
        cache.clear()
    }
}
