package com.videobrowsing.honeysuckle.core.data.update

import com.videobrowsing.honeysuckle.core.common.AppError
import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.common.map
import com.videobrowsing.honeysuckle.core.data.remote.dto.stringOr
import com.videobrowsing.honeysuckle.core.network.client.HsyJsonWithHeadersSource
import javax.inject.Inject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/** 服务端对这一次问话的回话:给了新的一版,或者说"还是上次那一版" */
sealed interface ReleaseFetch {
    data class Fresh(val release: AppRelease, val etag: String?) : ReleaseFetch

    /** `If-None-Match` 命中;内容在本地缓存里,这一条不计入匿名额度 */
    data object NotModified : ReleaseFetch
}

/**
 * 最新发布版:`GET https://api.github.com/repos/{owner}/{repo}/releases/latest`。
 *
 * 为什么是 GitHub API 而不是自建接口:仓库本来就在 GitHub 上发 Release,这一条请求一次给齐
 * "最新是哪版 + 更新说明 + 网页地址",零服务端、零密钥、零运维。另一条省事的路子是发一个
 * 跟版本走的 raw JSON,但那是一份会漏改的清单 —— 改版号的人忘了改它,提醒就从此停在旧版本上。
 *
 * 匿名调用的额度是 60 次/小时/IP,而这一条链每次都带 `If-None-Match`:服务端答"没变"时回 304,
 * GitHub 明确不计额度。**共享的那块 OkHttp 响应缓存挡不住这一档** —— 实测它只兜住 `max-age=60`
 * 之内的重复问话,过点之后仍会打一条整请求并计入额度,所以条件请求在这里自己写。
 *
 * 这次问话打的是 github.com,不在指纹头拦截器的通道表里,于是原样放行:
 * 不带 Cookie、不带站内 UA,登录身份不会顺着"检查更新"漏给第三方。
 */
class GitHubReleaseApi @Inject constructor(private val jsonSource: HsyJsonWithHeadersSource) {

    private val latestUrl: HttpUrl = LATEST_RELEASE_URL.toHttpUrl()

    /**
     * @param ifNoneMatch 上次问到的 `ETag`;null = 本地没有可比的那一版,打整条请求
     *
     * `releases/latest` 本身就不含草稿与预发布,不必再筛。
     */
    suspend fun latest(ifNoneMatch: String?): HsyResult<ReleaseFetch> {
        val headers = ifNoneMatch?.takeIf { it.isNotBlank() }?.let { mapOf(KEY_IF_NONE_MATCH to it) }.orEmpty()
        return when (val result = jsonSource.getJsonWithHeaders(latestUrl, headers)) {
            is HsyResult.Failure -> if (result.error.isNotModified()) {
                HsyResult.Success(ReleaseFetch.NotModified)
            } else {
                HsyResult.Failure(result.error)
            }

            is HsyResult.Success -> result.value.let { response ->
                releaseOf(response.body).map { release ->
                    ReleaseFetch.Fresh(release, response.headers[KEY_ETAG])
                }
            }
        }
    }

    private fun releaseOf(element: JsonElement): HsyResult<AppRelease> {
        val node = element as? JsonObject
            ?: return HsyResult.Failure(AppError.Decode("最新发布版的报文不是预期形状"))
        val rawTag = node.stringOr(KEY_TAG_NAME)
        val version = normalizeVersion(rawTag)
        val pageUrl = node.stringOr(KEY_PAGE_URL)
        if (version.isBlank() || pageUrl.isBlank()) {
            return HsyResult.Failure(AppError.Decode("最新发布版缺 $KEY_TAG_NAME / $KEY_PAGE_URL"))
        }
        return HsyResult.Success(
            AppRelease(
                version = version,
                title = node.stringOr(KEY_TITLE).ifBlank { rawTag },
                notes = node.stringOr(KEY_NOTES).trim(),
                pageUrl = pageUrl,
            ),
        )
    }

    /** 交换层把非 2xx 一律归成 [AppError.Server];304 在它眼里是"没实体",在这条链上是"还是上次那一版" */
    private fun AppError.isNotModified(): Boolean = this is AppError.Server && code == HTTP_NOT_MODIFIED

    companion object {
        /** 仓库地址改了要回来动这一行;owner/repo 在全仓只出现在这一处 */
        private const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/Adam-Seven-Days/Honeysuckle/releases/latest"

        private const val HTTP_NOT_MODIFIED = 304
        private const val KEY_IF_NONE_MATCH = "If-None-Match"
        private const val KEY_ETAG = "ETag"
        private const val KEY_TAG_NAME = "tag_name"
        private const val KEY_TITLE = "name"
        private const val KEY_NOTES = "body"
        private const val KEY_PAGE_URL = "html_url"
    }
}
