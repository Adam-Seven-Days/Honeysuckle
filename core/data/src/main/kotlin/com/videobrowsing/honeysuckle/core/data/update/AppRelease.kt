package com.videobrowsing.honeysuckle.core.data.update

/**
 * 仓库上一个 Release 里本应用真正要用的那几项。
 *
 * 只到"有哪一版、说了什么、去哪儿下":下载与安装都交给浏览器,所以这里不装 APK 直链、
 * 大小、ABI 与摘要 —— 那些只有应用内安装才用得上,而这一版不做应用内安装。
 */
data class AppRelease(
    /** 归一化后的版本名(不带前导 `v`),与本机 `versionName` 同一个写法,可直接比 */
    val version: String,
    /** Release 标题;作者没填时退版本名 */
    val title: String,
    /** 更新说明正文,原样文本;可能为空 */
    val notes: String,
    /** Release 网页地址,弹窗里"去下载"打的就是它 */
    val pageUrl: String,
)

/**
 * candidate 严格高于 current 才算"有新版本"。
 *
 * 比的是版本名而不是 versionCode:Release 里只有 tag,没有 versionCode,而 tag 是人手写的,
 * 只剩"逐段比数字"这一条路。缺的段按 0 补,认不出的那一段(如 `1.3.0-rc1` 的 `0-rc1`)
 * 只取开头的数字 —— 剩下的后缀不参与比较。
 *
 * 判不准时一律不判成"有新版本":催用户去下一个装不上的东西,比这一晚没提醒难看得多。
 */
internal fun isNewerVersion(candidate: String, current: String): Boolean = compareVersions(candidate, current) > 0

/** 平台 tag 习惯写成 `v1.2.0`,`versionName` 写成 `1.2.0`;比之前先削成同一个写法 */
internal fun normalizeVersion(raw: String): String {
    val trimmed = raw.trim()
    return if (trimmed.startsWith("v", ignoreCase = true)) trimmed.drop(1) else trimmed
}

private fun compareVersions(left: String, right: String): Int {
    val ours = left.toVersionParts()
    val theirs = right.toVersionParts()
    for (index in 0 until maxOf(ours.size, theirs.size)) {
        val diff = ours.getOrElse(index) { ZERO_SEGMENT }.compareTo(theirs.getOrElse(index) { ZERO_SEGMENT })
        if (diff != 0) return diff
    }
    return 0
}

/** 按 `.` 切段,每段只取开头的连续数字;`1.2` 与 `1.2.0` 因此可比 */
private fun String.toVersionParts(): List<Int> = split(SEGMENT_SEPARATOR).map { part ->
    part.takeWhile { it.isDigit() }.toIntOrNull() ?: ZERO_SEGMENT
}

private const val ZERO_SEGMENT = 0
private const val SEGMENT_SEPARATOR = "."
