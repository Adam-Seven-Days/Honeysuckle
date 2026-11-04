package com.videobrowsing.honeysuckle.core.data.crash

/**
 * 崩溃文本的脱敏口。
 *
 * 崩溃 message 里最常见的三类东西恰好都是凭证或可定位信息:
 *  - 带签名的 URL(播放地址、直播地址、搜索词、bvid 都在 query 里);
 *  - 请求头里的整串 `Cookie`(含 `SESSDATA` / `bili_jct`);
 *  - 单独出现的 `token` / `mid` / `sid` 键值。
 *
 * 这里只做"抹掉值、留下键"的替换:保留 `?` 与键名,是为了还能看出"崩在带 query 的请求上",
 * 而不是把整段文本替换成一句无信息量的 `<redacted>`。
 *
 * 写入与读取两端都过这一道:旧版本已经落盘的栈也要在展示/复制前脱敏,
 * 不能只保护新写的那一份。
 */
object CrashRedactor {

    /** `scheme://host/path?query` → 只留到 `?`;query 里通常就是签名与搜索词 */
    private val URL_QUERY = Regex("""(https?://[^\s"'<>]+?)\?[^\s"'<>]*""", RegexOption.IGNORE_CASE)

    /** 整行 Cookie / Set-Cookie:值可能很长,整行抹掉最稳 */
    private val COOKIE_HEADER = Regex("""(?im)\b(?:cookie|set-cookie)\s*[:=]\s*[^\r\n]*""")

    /** 键值对形式的敏感项;值允许被引号包住 */
    private val SENSITIVE_PAIR = Regex(
        """(?i)\b(sessdata|bili_jct|refresh_token|access_token|token|sid|csrf|bili_ticket|dedeuserid|mid)""" +
            """\s*[=:]\s*("[^"]*"|[^\s&;"',]+)""",
    )

    fun redact(text: String): String = text
        .replace(URL_QUERY) { match -> "${match.groupValues[1]}?<redacted>" }
        .replace(COOKIE_HEADER) { match -> "${headerNameOf(match.value)}: <redacted>" }
        .replace(SENSITIVE_PAIR) { match -> "${match.groupValues[1]}=<redacted>" }

    private fun headerNameOf(line: String): String =
        line.substringBefore('=').substringBefore(':').trim().ifBlank { "cookie" }
}
