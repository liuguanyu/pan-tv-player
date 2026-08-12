package com.baidu.tv.player.kt.location

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

class QuickTimeLocationReaderTest {
    private val reader = QuickTimeLocationReader(OkHttpClient())

    @Test
    fun readLocationString_skipsLargeMdatAndReadsTrailingMoovByRange() = runTest {
        val location = "+31.2304+121.4737+012.300/"
        val meta = metaAtom("com.apple.quicktime.location.ISO6709" to location)
        val ftyp = atom("ftyp", "qt  ".toByteArray(StandardCharsets.ISO_8859_1))
        val mdatPayload = ByteArray(3 * 1024 * 1024)
        val file = ftyp + atom("mdat", mdatPayload) + atom("moov", meta)
        val server = MockWebServer()
        server.dispatcher = rangeDispatcher(file)
        server.start()
        try {
            val result = reader.readLocationString(server.url("/iphone.mov").toString())

            assertEquals(location, result)
            val requests = server.requestCount
            // 初始长度、ftyp、mdat、moov、meta、meta 内容；不会请求 3MB mdat payload。
            assertEquals(6, requests)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun parseMdtaLocation_readsIphoneQuickTimeLocationKey() {
        val meta = metaAtom(
            "com.apple.quicktime.make" to "Apple",
            "com.apple.quicktime.location.ISO6709" to "+31.2304+121.4737+012.300/",
            "com.apple.quicktime.model" to "iPhone 13 Pro",
        )

        assertEquals(
            "+31.2304+121.4737+012.300/",
            reader.parseMdtaLocation(meta),
        )
    }

    @Test
    fun parseMdtaLocation_readsAppleQuickTimeMetaWithoutFullBoxHeader() {
        val location = "+39.9485+116.4729+036.863/"
        val meta = quickTimeMetaAtom(
            "com.apple.quicktime.location.accuracy.horizontal" to "14.245955",
            "com.apple.quicktime.location.ISO6709" to location,
            "com.apple.quicktime.model" to "iPhone 16 Pro",
        )

        assertEquals(location, reader.parseMdtaLocation(meta))
    }

    @Test
    fun parseMdtaLocation_missingLocationKeyReturnsNull() {
        val meta = metaAtom(
            "com.apple.quicktime.make" to "Apple",
            "com.apple.quicktime.model" to "iPhone 13 Pro",
        )

        assertNull(reader.parseMdtaLocation(meta))
    }

    @Test
    fun parseIso6709_iphoneValueWithAltitudeUsesLatitudeAndLongitude() {
        assertEquals(
            GpsCoordinate(31.2304, 121.4737),
            LocationExtractor.parseIso6709("+31.2304+121.4737+012.300/"),
        )
    }

    private fun metaAtom(vararg entries: Pair<String, String>): ByteArray =
        fullBox("meta", metadataChildren(entries))

    private fun quickTimeMetaAtom(vararg entries: Pair<String, String>): ByteArray {
        val handler = atom("hdlr", ByteArray(4) + ByteArray(8) + "mdta".toByteArray(StandardCharsets.ISO_8859_1) + ByteArray(12))
        return atom("meta", handler + metadataChildren(entries))
    }

    private fun metadataChildren(entries: Array<out Pair<String, String>>): ByteArray {
        val keyPayload = ByteArrayOutputStream().apply {
            write(intBytes(entries.size))
            entries.forEach { (key, _) ->
                val keyBytes = key.toByteArray(StandardCharsets.UTF_8)
                write(intBytes(8 + keyBytes.size))
                write("mdta".toByteArray(StandardCharsets.ISO_8859_1))
                write(keyBytes)
            }
        }.toByteArray()
        val keys = fullBox("keys", keyPayload)

        val ilstPayload = ByteArrayOutputStream().apply {
            entries.forEachIndexed { index, (_, value) ->
                val valueBytes = value.toByteArray(StandardCharsets.UTF_8)
                val data = atom("data", ByteArray(8) + valueBytes)
                write(indexedAtom(index + 1, data))
            }
        }.toByteArray()
        val ilst = atom("ilst", ilstPayload)
        return keys + ilst
    }

    private fun fullBox(type: String, payload: ByteArray): ByteArray = atom(type, ByteArray(4) + payload)

    private fun atom(type: String, payload: ByteArray): ByteArray =
        intBytes(8 + payload.size) + type.toByteArray(StandardCharsets.ISO_8859_1) + payload

    private fun indexedAtom(index: Int, payload: ByteArray): ByteArray =
        intBytes(8 + payload.size) + intBytes(index) + payload

    private fun intBytes(value: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(value).array()

    private fun rangeDispatcher(file: ByteArray): Dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val range = request.getHeader("Range") ?: return MockResponse().setResponseCode(400)
            val match = Regex("bytes=(\\d+)-(\\d+)").matchEntire(range)
                ?: return MockResponse().setResponseCode(416)
            val start = match.groupValues[1].toInt()
            val requestedEnd = match.groupValues[2].toInt()
            if (start !in file.indices) return MockResponse().setResponseCode(416)
            val end = requestedEnd.coerceAtMost(file.lastIndex)
            val body = file.copyOfRange(start, end + 1)
            return MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes $start-$end/${file.size}")
                .setBody(okio.Buffer().write(body))
        }
    }
}
