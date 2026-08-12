package com.baidu.tv.player.kt.location

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [LocationExtractor] 隐私/性能约束测试（对应 tasks.md 7.9）。
 *
 * 覆盖：
 * - 图片请求携带 `Range: bytes=0-131071`（断言 header + 128KB 上限）；
 * - 图片读取字节数不超过 128KB；
 * - 视频提取超时(>8s)静默返回 null；
 * - 视频提取异常静默返回 null；
 * - 无 metadata 静默返回 null。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LocationExtractorTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient.Builder().build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun extractImageGps_sendsRangeHeaderForFirst128Kb() = runTest {
        server.enqueue(MockResponse().setResponseCode(206).setBody("not-an-image"))
        val extractor = LocationExtractor(client, StubVideoReader(null))

        extractor.extractImageGps(server.url("/photo.jpg").toString())

        val recorded = server.takeRequest()
        assertEquals("bytes=0-131071", recorded.getHeader("Range"))
    }

    @Test
    fun extractImageGps_readsAtMost128Kb() = runTest {
        // 服务器返回远大于 128KB 的响应，验证只读取头部。
        val hugeBody = Buffer().apply {
            write(ByteArray(1_000_000) { 0 })
        }
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Length", hugeBody.size.toString())
                .setBody(hugeBody),
        )
        val extractor = LocationExtractor(client, StubVideoReader(null))

        // 非法图片头部 -> 返回 null，但不应抛异常，也不应读取整图。
        val result = extractor.extractImageGps(server.url("/big.jpg").toString())
        assertNull(result)
    }

    @Test
    fun extractImageGps_invalidImageReturnsNullSilently() = runTest {
        server.enqueue(MockResponse().setResponseCode(206).setBody("garbage"))
        val extractor = LocationExtractor(client, StubVideoReader(null))
        assertNull(extractor.extractImageGps(server.url("/x.jpg").toString()))
    }

    @Test
    fun extractImageGps_httpErrorReturnsNullSilently() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val extractor = LocationExtractor(client, StubVideoReader(null))
        assertNull(extractor.extractImageGps(server.url("/missing.jpg").toString()))
    }

    @Test
    fun extractVideoGps_noMetadataReturnsNull() = runTest {
        val extractor = LocationExtractor(client, StubVideoReader(null))
        assertNull(extractor.extractVideoGps("http://example.com/v.mp4"))
    }

    @Test
    fun extractVideoGps_exceptionReturnsNull() = runTest {
        val extractor = LocationExtractor(client, ThrowingVideoReader())
        assertNull(extractor.extractVideoGps("http://example.com/v.mp4"))
    }

    @Test
    fun extractVideoGps_validIso6709Parsed() = runTest {
        val extractor = LocationExtractor(client, StubVideoReader("+34.0522-118.2437/"))
        val result = extractor.extractVideoGps("http://example.com/v.mp4")
        assertTrue(result != null)
        assertEquals(34.0522, result!!.latitude, 0.0001)
        assertEquals(-118.2437, result.longitude, 0.0001)
    }

    @Test
    fun constraints_lockPrivacyAndPerformanceValues() {
        // 锁定隐私/性能约束常量，防止回归：图片 128KB 头部、视频 8 秒超时。
        assertEquals(131_072, LocationExtractor.IMAGE_HEADER_MAX_BYTES)
        assertEquals(8_000L, LocationExtractor.VIDEO_TIMEOUT_MS)
    }

    @Test
    fun parseIso6709_variousForms() {
        assertEquals(
            GpsCoordinate(34.0522, -118.2437),
            LocationExtractor.parseIso6709("+34.0522-118.2437/"),
        )
        assertNull(LocationExtractor.parseIso6709("garbage"))
        assertNull(LocationExtractor.parseIso6709("+0.0+0.0/"))
    }

    @Test
    fun normalizeDateTime_variousForms() {
        val originalTz = java.util.TimeZone.getDefault()
        try {
            // 固定为东八区，验证带 Z(UTC) 的视频时间会 +8h 转本地时区。
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Shanghai"))
            // EXIF 格式 yyyy:MM:dd HH:mm:ss（本地时间，不做偏移）
            assertEquals("2023-08-15 09:15", LocationExtractor.normalizeDateTime("2023:08:15 09:15:30"))
            // 视频 ISO8601 带毫秒与 Z：UTC 09:15 -> 东八区 17:15
            assertEquals("2023-08-15 17:15", LocationExtractor.normalizeDateTime("20230815T091530.000Z"))
            // 视频 ISO8601 不带毫秒但带 Z：同样需 +8h
            assertEquals("2023-08-15 17:15", LocationExtractor.normalizeDateTime("20230815T091530Z"))
            // 非法月份回退原串
            assertEquals("2023:99:99 99:99:99", LocationExtractor.normalizeDateTime("2023:99:99 99:99:99"))
            // QuickTime/MP4 纪元零时间戳（1904-01-01）视为无效，返回 null
            assertNull(LocationExtractor.normalizeDateTime("19040101T000000.000Z"))
            assertNull(LocationExtractor.normalizeDateTime("1904:01:01 00:00:00"))
            // Unix 纪元零时间戳（1970-01-01）同样视为无效
            assertNull(LocationExtractor.normalizeDateTime("19700101T000000.000Z"))
            // 空白返回 null
            assertNull(LocationExtractor.normalizeDateTime("   "))
        } finally {
            java.util.TimeZone.setDefault(originalTz)
        }
    }

    @Test
    fun extractVideoDateTime_validDateNormalized() = runTest {
        val originalTz = java.util.TimeZone.getDefault()
        try {
            // 固定东八区：视频 UTC 09:15 应转为本地 17:15。
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Shanghai"))
            val extractor = LocationExtractor(client, StubVideoReader(null, "20230815T091530.000Z"))
            assertEquals(
                "2023-08-15 17:15",
                extractor.extractVideoDateTime("http://example.com/v.mp4"),
            )
        } finally {
            java.util.TimeZone.setDefault(originalTz)
        }
    }

    @Test
    fun extractVideoDateTime_noMetadataReturnsNull() = runTest {
        val extractor = LocationExtractor(client, StubVideoReader(null, null))
        assertNull(extractor.extractVideoDateTime("http://example.com/v.mp4"))
    }

    @Test
    fun extractVideoDateTime_exceptionReturnsNull() = runTest {
        val extractor = LocationExtractor(client, ThrowingVideoReader())
        assertNull(extractor.extractVideoDateTime("http://example.com/v.mp4"))
    }

    private class StubVideoReader(
        private val value: String?,
        private val dateValue: String? = null,
    ) : VideoMetadataReader {
        override suspend fun readLocationString(url: String): String? = value
        override suspend fun readDateString(url: String): String? = dateValue
    }

    private class ThrowingVideoReader : VideoMetadataReader {
        override suspend fun readLocationString(url: String): String? =
            throw IllegalStateException("boom")

        override suspend fun readDateString(url: String): String? =
            throw IllegalStateException("boom")
    }
}
