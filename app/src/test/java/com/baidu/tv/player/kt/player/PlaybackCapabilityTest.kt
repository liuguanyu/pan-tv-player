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
    fun evaluate_dolbyVision_unsupported() {
        val capability = PlaybackCapability { true }

        val result = capability.evaluate(VideoCodecInfo(mimeType = "video/dolby-vision", width = 1920, height = 1080, bitDepth = 10))

        assertEquals(PlaybackCapability.Capability.Unsupported(UnsupportedReason.DOLBY_VISION), result)
    }

    @Test
    fun evaluate_hevc10Bit_warnAndPlay() {
        val capability = PlaybackCapability { true }

        val result = capability.evaluate(VideoCodecInfo(mimeType = "video/hevc", width = 1920, height = 1080, bitDepth = 10))

        assertEquals(PlaybackCapability.Capability.WarnAndPlay(UnsupportedReason.HEVC_10BIT), result)
    }

    @Test
    fun evaluate_hevc4k_warnAndPlay() {
        val capability = PlaybackCapability { true }

        val result = capability.evaluate(VideoCodecInfo(mimeType = "video/hevc", width = 3840, height = 2160))

        assertEquals(PlaybackCapability.Capability.WarnAndPlay(UnsupportedReason.HEVC_4K), result)
    }

    @Test
    fun evaluate_hevcWithoutHardware_unsupported() {
        val capability = PlaybackCapability { false }

        val result = capability.evaluate(VideoCodecInfo(mimeType = "video/hevc", width = 1920, height = 1080))

        assertEquals(PlaybackCapability.Capability.Unsupported(UnsupportedReason.NO_HARDWARE_DECODER), result)
    }
}
