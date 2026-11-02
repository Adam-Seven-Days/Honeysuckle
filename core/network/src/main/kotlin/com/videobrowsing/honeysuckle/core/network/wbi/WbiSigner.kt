package com.videobrowsing.honeysuckle.core.network.wbi

import java.net.URLEncoder
import java.security.MessageDigest

/**
 * WBI 签名(w_rid)PoC —— 公开算法。
 *
 * 流程:nav 接口下发的 img_key + sub_key 按公开索引表重排
 * 取前 32 位作为 mixin key;参数注入 wts(秒级时间戳),
 * 按 key 升序拼 query(值过滤 `!'()*` 字符),首尾 + mixinKey 后 MD5 即 w_rid。
 *
 * 注意:该表/算法属于平台公开已考据数据,实现以实测为准;
 * key 过期策略(token 轮询与缓存)由上层的 WbiKeySource 补足。
 */
object WbiSigner {

    /**
     * 混淆索引表(64 项),来源为平台接口考据文档。
     * 表内容即协议本身,逐项命名会把一份置换表变成 64 个无信息量的标识符。
     */
    @Suppress("MagicNumber")
    private val MIXIN_TAB = intArrayOf(
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
        27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
        37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4,
        22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52,
    )

    /** mixin key 定长 32 位:置换后多余的部分按平台实现直接截断 */
    private const val MIXIN_KEY_LENGTH = 32

    /** wts 是秒级时间戳,而 currentTimeMillis 是毫秒 */
    private const val MS_PER_SECOND = 1000L

    /** 由 img_key + sub_key 派生 32 位 mixin key */
    fun mixinKey(imgKey: String, subKey: String): String {
        val raw = imgKey + subKey
        val shuffled = MIXIN_TAB.asIterable().mapNotNull { raw.getOrNull(it) }
        return shuffled.joinToString("").take(MIXIN_KEY_LENGTH)
    }

    /** 生成 w_rid / wts 参数对 */
    fun sign(
        params: Map<String, String>,
        mixinKey: String,
        wtsSec: Long = System.currentTimeMillis() / MS_PER_SECOND,
    ): WbiParams {
        val merged = LinkedHashMap<String, String>(params).apply { put("wts", wtsSec.toString()) }

        val query = merged.keys.sorted()
            .joinToString("&") { key -> "$key=${encodeValue(merged.getValue(key))}" }

        val wRid = md5Hex(query + mixinKey)
        return WbiParams(wts = wtsSec.toString(), wRid = wRid)
    }

    /**
     * RFC3986 式编码:先剔除 `!'()*`,空格由 URLEncoder 的 `+` 归位成 %20。
     *
     * 编码参数用**字符集名**而不是 `Charsets.UTF_8`:后者是 `encode(String, Charset)` 重载,
     * Android 上要 API 33 才有,minSdk 26 会直接 NoSuchMethodError(实测由 lint NewApi 抓出;
     * 桌面上两边重载都存在,只编译不跑设备发现不了)。
     */
    private fun encodeValue(value: String): String =
        URLEncoder.encode(value.replace(Regex("[!'()*]"), ""), Charsets.UTF_8.name())
            .replace("+", "%20")

    fun md5Hex(input: String): String = MessageDigest.getInstance("MD5")
        .digest(input.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

/** 签名结果:wts 与 w_rid 直接作为 query 参数附加到请求 */
data class WbiParams(val wts: String, val wRid: String)
