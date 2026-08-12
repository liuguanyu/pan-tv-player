package com.baidu.tv.player.kt.repository

import com.baidu.tv.player.kt.auth.BaiduAuthService
import com.baidu.tv.player.kt.model.FileInfo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 统一的可播放 URL 解析器（Phase 4）。
 *
 * 将百度网盘 dlink + access_token 拼接逻辑从 [com.baidu.tv.player.kt.ui.playback.PlaybackViewModel]
 * 和 [com.baidu.tv.player.kt.ui.playback.PlaybackActivity] BGM 路径中抽出，消除重复代码。
 *
 * 语义：
 * 1. 获取 access_token，为空则抛 [IllegalStateException]。
 * 2. 若 [dlink] 非空则直接使用；否则通过 [FileRepository.fetchFileDetail] 拉取详情获取 dlink。
 * 3. 若最终 dlink 为空则抛 [IllegalStateException]。
 * 4. 若 dlink 已含 `access_token=` 则原样返回，否则拼接。
 *
 * 不做 HTTP 超时设置、不做本地文件缓存、不做分片缓存。
 * 不使用 `runCatching`，[kotlinx.coroutines.CancellationException] 自然传播。
 */
@Singleton
class PlayableUrlResolver @Inject constructor(
    private val authService: BaiduAuthService,
    private val fileRepository: FileRepository,
) {
    /**
     * 解析可播放 URL。
     *
     * @param fsId 文件 fsId（用于拉取详情）。
     * @param dlink 已知 dlink（可为 null/blank，此时会通过 API 拉取）。
     * @param serverFilename 文件名（仅用于错误信息）。
     * @return 拼接好 access_token 的可播放 URL。
     */
    suspend fun resolve(fsId: Long, dlink: String?, serverFilename: String?): String {
        val token = authService.getAccessToken().orEmpty()
        check(token.isNotEmpty()) { "未获取到访问令牌，请先登录" }
        val resolvedDlink = dlink?.takeIf { it.isNotBlank() }
            ?: fileRepository.fetchFileDetail(token, fsId)?.dlink?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("文件缺少 dlink: ${serverFilename.orEmpty()}")
        return appendAccessToken(resolvedDlink, token)
    }

    /** 便捷重载，直接从 [FileInfo] 提取参数。 */
    suspend fun resolve(file: FileInfo): String =
        resolve(file.fsId, file.dlink, file.serverFilename)

    private fun appendAccessToken(dlink: String, token: String): String =
        if (dlink.contains("access_token=")) {
            dlink
        } else {
            dlink + (if (dlink.contains('?')) "&" else "?") + "access_token=" + token
        }
}
