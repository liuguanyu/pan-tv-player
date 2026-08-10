package com.baidu.tv.player.kt.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackCapabilityTest {

    @Test
    fun playbackResult_keepsUnsupportedCodecAndReason() {
        val result: PlaybackResult = PlaybackResult.Unsupported("hevc", UnsupportedReason.HEVC_4K)

        assertTrue(result is PlaybackResult.Unsupported)
        val unsupported = result as PlaybackResult.Unsupported
        assertEquals("hevc", unsupported.codec)
        assertEquals(UnsupportedReason.HEVC_4K, unsupported.reason)
    }

    @Test
    fun evaluate_nonHevc_directPlay() {
        val capability = PlaybackCapability { false }

        val result = capability.evaluate(VideoCodecInfo(mimeType = "video/avc", width = 1920, height = 1080))

        assertEquals(PlaybackCapability.Capability.DirectPlay, result)
    }

    @Test
    fun evaluate_dolbyVisionWithHardware_directPlay() {
        val capability = PlaybackCapability { true }

        val result = capability.evaluate(VideoCodecInfo(mimeType = "video/dolby-vision", width = 3840, height = 2160, bitDepth = 10))

        assertEquals(PlaybackCapability.Capability.DirectPlay, result)
    }

    @Test
    fun evaluate_dolbyVisionWithoutHardware_unsupported() {
        val capability = PlaybackCapability { false }

        val result = capability.evaluate(VideoCodecInfo(mimeType = "video/dolby-vision", width = 3840, height = 2160, bitDepth = 10))

        assertEquals(PlaybackCapability.Capability.Unsupported(UnsupportedReason.DOLBY_VISION), result)
    }

    @Test
    fun evaluate_hevc10BitWithHardware_directPlay() {
        val capability = PlaybackCapability { true }

        val result = capability.evaluate(VideoCodecInfo(mimeType = "video/hevc", width = 1920, height = 1080, bitDepth = 10))

        assertEquals(PlaybackCapability.Capability.DirectPlay, result)
    }

    @Test
    fun evaluate_hevc4kWithHardware_directPlay() {
        val capability = PlaybackCapability { true }

        val result = capability.evaluate(VideoCodecInfo(mimeType = "video/hevc", width = 3840, height = 2160))

        assertEquals(PlaybackCapability.Capability.DirectPlay, result)
    }

    @Test
    fun evaluate_hevcWithoutHardware_unsupported() {
        val capability = PlaybackCapability { false }

        val result = capability.evaluate(VideoCodecInfo(mimeType = "video/hevc", width = 1920, height = 1080))

        assertEquals(PlaybackCapability.Capability.Unsupported(UnsupportedReason.NO_HARDWARE_DECODER), result)
    }
}
