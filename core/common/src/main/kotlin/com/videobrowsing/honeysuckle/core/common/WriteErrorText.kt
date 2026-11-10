package com.videobrowsing.honeysuckle.core.common

/** 平台"账号未登录"的业务码:写接口的游客门禁与读接口的游客分支都认它 */
const val CODE_NOT_LOGGED_IN = -101

/** csrf 失效:凭证还在,但 `bili_jct` 对不上,再点一次也不会成 */
private const val CODE_CSRF_INVALID = -111

/** 平台风控(HTTP 412 与业务码 -352 是同一类:歇一会儿,不是连点) */
private const val CODE_RISK_CONTROL = -352
private const val HTTP_RISK_CONTROL = 412
private const val CODE_REQUEST_TOO_FREQUENT = -509

/** 投币域的业务码:硬币不够与今日到上限都是"今天就这样",重试不会好 */
private const val CODE_COIN_INSUFFICIENT = -104
private const val CODE_COIN_DAILY_LIMIT = 34005

/** 评论域的业务码 */
private const val CODE_COMMENT_CLOSED = 12002
private const val CODE_COMMENT_TOO_FAST = 12009
private const val CODE_COMMENT_REJECTED = 12015

/**
 * 写操作失败 → 一句人话(风控错误页要能说清下一步)。
 *
 * 与 [userMessage] 分开,是因为写有它自己的那几张脸:同一个 412 出现在列表刷新上叫"歇一会儿再拉",
 * 出现在一次点赞上叫"这一下被挡了";`-101` 在写链里意味着"该去登录",而不是"这一页坏了"。
 * 文案仍留在 `:core:common`,数据层只出分类。
 */
fun writeErrorText(action: String, error: AppError): String {
    if (error.isLoginRequired()) return "${action}需要登录"
    // 码表另起一段:写接口那张脸主要是平台的业务码,一条条摊在同一个 when 里会把复杂度堆到读不动
    if (error is AppError.Server) return serverWriteText(action, error)
    return "${action}失败:${error.userMessage()}"
}

private fun serverWriteText(action: String, error: AppError.Server): String = when (error.code) {
    CODE_CSRF_INVALID -> "${action}失败:登录凭证过期,请重新登录"
    CODE_RISK_CONTROL -> "${action}触发平台风控,请稍后再试"
    HTTP_RISK_CONTROL -> "${action}被平台限制(412),请稍后再试"
    CODE_REQUEST_TOO_FREQUENT -> "${action}过于频繁,请稍后再试"
    CODE_COMMENT_CLOSED -> "这个视频关闭了评论"
    CODE_COMMENT_TOO_FAST -> "评论发送太快,请稍后再发"
    CODE_COMMENT_REJECTED -> "内容未通过审核,请修改后再试"
    CODE_COIN_INSUFFICIENT -> "硬币不足"
    CODE_COIN_DAILY_LIMIT -> "今日投币已达上限"
    else -> "${action}失败:${humanPlatformMessage(error) ?: error.userMessage()}"
}

/**
 * 码表没收录的那一枚,借平台自己那句中文给用户看。
 *
 * "稿件不支持投币""今日投币已达上限"这类话正是这一笔唯一可执行的下一步,而本项目不写日志,
 * 屏上再不说就只剩一句"稍后再试"。形状不对(英文、超长、HTTP 那句 "Not Found")一律不上屏,
 * 退回 [userMessage] 的通用话 —— 原话里可能夹着用户读不懂也做不了什么的东西。
 */
private fun humanPlatformMessage(error: AppError.Server): String? = error.message.trim().removeSuffix("。")
    .takeIf { it.isNotEmpty() && it.length <= PLATFORM_MESSAGE_MAX && it.any { ch -> ch in PLATFORM_CJK } }

private const val PLATFORM_MESSAGE_MAX = 24
private val PLATFORM_CJK = '一'..'鿿'

/** 这一发失败是不是"该去登录":界面据此把入口换成扫码页,而不是反复重试 */
fun AppError.isLoginRequired(): Boolean = this is AppError.Server && code == CODE_NOT_LOGGED_IN
