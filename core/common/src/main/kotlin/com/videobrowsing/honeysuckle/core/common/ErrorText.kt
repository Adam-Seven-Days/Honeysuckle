package com.videobrowsing.honeysuckle.core.common

/** 平台风控那一档的 HTTP 码(2026-10-31 设备上真撞到的那一个) */
private const val HTTP_RISK_CONTROL = 412

/** 平台风控那一档的业务码:wbi 签名/指纹不齐时空间与部分读口会回这一枚 */
private const val CODE_RISK_REJECT = -352

/**
 * 错误 → 一句人话(文案映射在 UI 层,数据层只出分类)。
 *
 * 每条都带"下一步该做什么":同一屏上的错误至少有断网、风控、报文变形三种成因,
 * 压成一句"加载失败"就等于让用户拿同一只手去解三件事。
 *
 * **不把底层原话摆上屏**:异常消息里常带主机名、端口、堆栈入口,平台回包也可能带码与英文,
 * 这些字对用户没有下一步价值,却会让人以为应用坏了。所以这里按类别翻译,原话只留给日志。
 */
fun AppError.userMessage(): String = when (this) {
    is AppError.Network -> networkMessage(cause)

    is AppError.Server -> when (code) {
        HTTP_RISK_CONTROL -> "被平台挡回来了 · 歇一会儿再试,连点只会更久"
        CODE_RISK_REJECT -> "平台的身份核查没通过 · 稍后再试,挂着页面里的重试会带齐指纹重来"
        else -> "平台暂时没响应 · 稍后再试"
    }

    is AppError.Decode -> "这条路的数据没读出来 · 稍后再试"

    is AppError.Auth -> "没有权限 · 登录后再试"

    is AppError.Unknown -> "出了点小问题 · 稍后再试"

    AppError.RateLimited -> "请求太密了 · 歇半分钟再来"
}

/**
 * 断网也分几种:域名没解析、连接被掐、等超时,用户的下一步不同。
 *
 * 判据只看异常里那几段英文关键字 —— 这是唯一需要读原话的地方,读到的结果不再往外传。
 */
private fun networkMessage(cause: String): String {
    val raw = cause.lowercase()
    return when {
        "timeout" in raw || "timed out" in raw ->
            "网络有点慢,这一下没等到 · 稍后再试"

        "unknownhost" in raw || "unable to resolve" in raw ||
            "no address associated" in raw || "nodename nor servname" in raw ->
            "连不上网 · 检查一下网络再试"

        "refused" in raw || "reset" in raw || "closed" in raw || "eof" in raw ||
            "unreachable" in raw || "broken pipe" in raw ->
            "连接断了 · 检查一下网络再试"

        else -> "网络不太稳 · 检查一下网络再试"
    }
}
