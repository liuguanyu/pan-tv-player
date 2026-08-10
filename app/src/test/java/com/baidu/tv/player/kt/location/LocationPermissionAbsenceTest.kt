package com.baidu.tv.player.kt.location

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/**
 * 隐私约束回归测试（对应 tasks.md 7.7 / design.md Decision 11）。
 *
 * 断言 AndroidManifest 中不含任何设备定位权限：
 * - 地点识别仅解析媒体元数据 GPS，绝不申请 ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION。
 *
 * 该测试直接扫描源 AndroidManifest.xml 文本，防止后续误加权限。
 */
class LocationPermissionAbsenceTest {

    private fun manifestText(): String {
        // 单测工作目录为 app 模块根目录。
        val candidates = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
        )
        val manifest = candidates.firstOrNull { it.exists() }
            ?: error("找不到 AndroidManifest.xml，检查路径: ${candidates.map { it.absolutePath }}")
        return manifest.readText()
    }

    @Test
    fun manifest_doesNotRequestFineLocation() {
        assertFalse(
            "不得申请 ACCESS_FINE_LOCATION 权限",
            manifestText().contains("ACCESS_FINE_LOCATION"),
        )
    }

    @Test
    fun manifest_doesNotRequestCoarseLocation() {
        assertFalse(
            "不得申请 ACCESS_COARSE_LOCATION 权限",
            manifestText().contains("ACCESS_COARSE_LOCATION"),
        )
    }

    @Test
    fun manifest_doesNotRequestBackgroundLocation() {
        assertFalse(
            "不得申请 ACCESS_BACKGROUND_LOCATION 权限",
            manifestText().contains("ACCESS_BACKGROUND_LOCATION"),
        )
    }
}
