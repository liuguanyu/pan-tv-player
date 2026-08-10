package com.baidu.tv.player.kt.network

import com.baidu.tv.player.kt.model.DeviceCodeResponse
import com.baidu.tv.player.kt.model.FileListResponse
import com.baidu.tv.player.kt.model.TokenResponse
import com.baidu.tv.player.kt.model.UserInfoResponse

/**
 * 可注入的 Fake [BaiduPanService]，用于 ViewModel / Repository 单测隔离网络。
 * 通过预设返回值/异常模拟百度网盘响应。
 *
 * 放在 `src/test`，供纯 JVM 单测使用。Retrofit 集成测试改用 MockWebServer。
 */
class FakeBaiduPanService : BaiduPanService {

    var fileListResponses: ArrayDeque<FileListResponse> = ArrayDeque()
    var fileListRecursiveResponses: ArrayDeque<FileListResponse> = ArrayDeque()
    var fileInfoResponse: FileListResponse? = null
    var deviceCodeResponse: DeviceCodeResponse = DeviceCodeResponse()
    var tokenResponse: TokenResponse = TokenResponse()
    var userInfoResponse: UserInfoResponse = UserInfoResponse()
    var revokeResponse: TokenResponse = TokenResponse()

    var fileListError: Throwable? = null
    var recursiveError: Throwable? = null
    var fileInfoError: Throwable? = null

    /** 记录 getFileList 调用参数（start 等），便于断言分页。 */
    val fileListCalls = mutableListOf<FileListCall>()
    val recursiveCalls = mutableListOf<FileListCall>()

    data class FileListCall(val dir: String, val start: Int, val limit: Int, val accessToken: String)

    fun enqueueFileList(vararg responses: FileListResponse) {
        fileListResponses.addAll(responses.toList())
    }

    fun enqueueRecursive(vararg responses: FileListResponse) {
        fileListRecursiveResponses.addAll(responses.toList())
    }

    override suspend fun getDeviceCode(
        clientId: String, scope: String, responseType: String,
    ): DeviceCodeResponse = deviceCodeResponse

    override suspend fun getTokenByDeviceCode(
        grantType: String, deviceCode: String, clientId: String, clientSecret: String,
    ): TokenResponse = tokenResponse

    override suspend fun refreshToken(
        grantType: String, refreshToken: String, clientId: String, clientSecret: String,
    ): TokenResponse = tokenResponse

    override suspend fun revokeToken(accessToken: String): TokenResponse = revokeResponse

    override suspend fun getUserInfo(method: String, accessToken: String): UserInfoResponse =
        userInfoResponse

    override suspend fun getFileList(
        method: String, dir: String, order: String, desc: Int,
        start: Int, limit: Int, web: Int, folder: Int, accessToken: String,
    ): FileListResponse {
        fileListCalls += FileListCall(dir, start, limit, accessToken)
        fileListError?.let { throw it }
        return fileListResponses.removeFirstOrNull() ?: FileListResponse()
    }

    override suspend fun getFileListRecursive(
        method: String, path: String, order: String, desc: Int,
        limit: Int, recursion: Int, accessToken: String,
    ): FileListResponse {
        recursiveCalls += FileListCall(path, 0, limit, accessToken)
        recursiveError?.let { throw it }
        return fileListRecursiveResponses.removeFirstOrNull() ?: FileListResponse()
    }

    override suspend fun getFileInfo(
        method: String, fsids: String, dlink: Int, accessToken: String,
    ): FileListResponse {
        fileInfoError?.let { throw it }
        return fileInfoResponse ?: FileListResponse()
    }
}
