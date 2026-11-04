@file:OptIn(UnstableApi::class)

package com.videobrowsing.honeysuckle.core.player

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import java.io.IOException
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 播放地址的"占位名 → 真实地址"表。
 *
 * 为什么不把签名 URL 直接放进 `MediaItem`:MediaSession 会把当前媒体项暴露给控制器,
 * 而控制器读到的 `MediaItem.uri` 就是播放地址。地址里带有效期与签名,一旦被别的应用拿到,
 * 就能在有效期内绕过本应用直接拉流。这里把 `MediaItem` 的 URI 换成一次性占位名,
 * 真正的地址只在 [resolve] 里、在 DataSource 打开前的一瞬才还原。
 *
 * 占位 URI 保留层级结构(`hsy-media://local/<token>`),是为了 HLS 清单里偶尔出现的
 * 相对分片还能按原语义解析:相对路径会拼在 token 后面,仍然找得到它属于哪条流;
 * 绝对路径的分片会丢掉 token,此时退到最近一次登记的真实地址([fallbackBase])。
 */
internal class MediaUrlRegistry {

    private val urls = ConcurrentHashMap<String, String>()

    /** HLS 绝对路径分片的兜底:它只带路径,已经认不出属于哪条流 */
    @Volatile
    private var fallbackBase: String? = null

    /** 登记一条真实地址,交回可以放进 `MediaItem` 的占位 URI */
    fun syntheticUri(url: String): Uri {
        val token = UUID.randomUUID().toString()
        urls[token] = url
        fallbackBase = url
        return Uri.parse("$SCHEME://$HOST/$token")
    }

    /** DataSource 打开前的还原口;非占位 URI(例如本地 `file://`)原样放行 */
    fun resolve(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri
        if (uri.scheme != SCHEME) return dataSpec
        val real = realUrlOf(uri) ?: throw IOException("本地播放占位地址已失效")
        return dataSpec.withUri(Uri.parse(real))
    }

    /** 换源时清空:表只服务当前这一条媒体,不随播放次数增长 */
    fun clear() {
        urls.clear()
        fallbackBase = null
    }

    private fun realUrlOf(uri: Uri): String? {
        val token = uri.pathSegments.firstOrNull()
        val known = token?.let(urls::get)
        return (known ?: fallbackBase)?.let { base -> resolveAgainst(uri, token, known, base) }
    }

    /** 占位 URI 里的相对路径拼回真实地址;认不出 token 时按绝对路径处理,不拼到目录下 */
    private fun resolveAgainst(uri: Uri, token: String?, known: String?, base: String): String? {
        val encodedPath = uri.encodedPath.orEmpty()
        val tokenPrefix = token?.let { "/$it" }
        val relative = if (known != null && tokenPrefix != null && encodedPath.startsWith(tokenPrefix)) {
            encodedPath.removePrefix(tokenPrefix).removePrefix("/")
        } else {
            "/" + encodedPath.removePrefix("/")
        }
        val query = uri.encodedQuery?.let { "?$it" }.orEmpty()
        return if (relative.isEmpty()) {
            base
        } else {
            runCatching { URI(base).resolve(relative + query).toString() }.getOrNull()
        }
    }

    private companion object {
        const val SCHEME = "hsy-media"
        const val HOST = "local"
    }
}
