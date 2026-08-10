package com.baidu.tv.player.kt.network

import com.baidu.tv.player.kt.model.DeviceCodeResponse
import com.baidu.tv.player.kt.model.FileListResponse
import com.baidu.tv.player.kt.model.TokenResponse
import com.baidu.tv.player.kt.model.UserInfoResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 百度网盘 API 服务接口。
 *
 * 使用 Retrofit 的 `suspend` 支持（方法返回响应体类型，而非 `Call`），
 * 调用方在协程中直接 await，由 [com.baidu.tv.player.kt.repository.FileRepository]
 * 等用协程处理分页与异常。
 */
interface BaiduPanService {

    /** 获取设备码。 */
    @GET(ApiConstants.ENDPOINT_DEVICE_CODE)
    suspend fun getDeviceCode(
        @Query("client_id") clientId: String,
        @Query("scope") scope: String,
        @Query("response_type") responseType: String,
    ): DeviceCodeResponse

    /** 轮询设备码状态获取 token。 */
    @GET(ApiConstants.ENDPOINT_TOKEN)
    suspend fun getTokenByDeviceCode(
        @Query("grant_type") grantType: String,
        @Query("code") deviceCode: String,
        @Query("client_id") clientId: String,
        @Query("client_secret") clientSecret: String,
    ): TokenResponse

    /** 刷新 token。 */
    @GET(ApiConstants.ENDPOINT_TOKEN)
    suspend fun refreshToken(
        @Query("grant_type") grantType: String,
        @Query("refresh_token") refreshToken: String,
        @Query("client_id") clientId: String,
        @Query("client_secret") clientSecret: String,
    ): TokenResponse

    /** 撤销 token（登出）。 */
    @GET(ApiConstants.ENDPOINT_REVOKE)
    suspend fun revokeToken(
        @Query("access_token") accessToken: String,
    ): TokenResponse

    /** 获取用户信息。 */
    @GET(ApiConstants.ENDPOINT_NAS)
    suspend fun getUserInfo(
        @Query("method") method: String,
        @Query("access_token") accessToken: String,
    ): UserInfoResponse

    /** 获取文件列表（非递归，分页）。 */
    @GET(ApiConstants.ENDPOINT_FILE)
    suspend fun getFileList(
        @Query("method") method: String,
        @Query("dir") dir: String,
        @Query("order") order: String,
        @Query("desc") desc: Int,
        @Query("start") start: Int,
        @Query("limit") limit: Int,
        @Query("web") web: Int,
        @Query("folder") folder: Int,
        @Query("access_token") accessToken: String,
    ): FileListResponse

    /** 递归获取文件列表（xpan/multimedia?method=listall）。 */
    @GET(ApiConstants.ENDPOINT_MULTIMEDIA)
    suspend fun getFileListRecursive(
        @Query("method") method: String,
        @Query("path") path: String,
        @Query("order") order: String,
        @Query("desc") desc: Int,
        @Query("limit") limit: Int,
        @Query("recursion") recursion: Int,
        @Query("access_token") accessToken: String,
    ): FileListResponse

    /** 获取文件信息（含下载链接 dlink）。fsids 需为 JSON 数组字符串，如 "[12345]"。 */
    @GET(ApiConstants.ENDPOINT_MULTIMEDIA)
    suspend fun getFileInfo(
        @Query("method") method: String,
        @Query("fsids") fsids: String,
        @Query("dlink") dlink: Int,
        @Query("access_token") accessToken: String,
    ): FileListResponse
}
