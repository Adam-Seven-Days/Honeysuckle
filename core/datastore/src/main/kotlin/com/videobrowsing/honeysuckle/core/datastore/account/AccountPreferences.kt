package com.videobrowsing.honeysuckle.core.datastore.account

/**
 * 登录态快照(第三块盘)。
 *
 * 只收"下一次冷启动要恢复的东西":请求要带的 Cookie、续期用的刷新令牌、以及"我的"页要显示的
 * 三行身份信息。没有密码、没有设备指纹 —— 扫码登录不产生密码,也就不该有地方存它。
 *
 * [isLoggedIn] 同时要求 mid 与 cookie:只有 mid 没有 cookie 是"资料还在、凭证已失效",
 * 这种半截状态必须按游客处理,否则界面会显示登录态而所有需要登录的接口仍旧回 -101。
 */
data class AccountPreferences(
    /** 平台账号 id;0 = 从未登录 */
    val mid: Long = NO_ACCOUNT,
    val name: String = "",
    val avatarUrl: String = "",
    /** 登录那一刻 nav 报的等级与硬币;旧资料没有这两格,所以可空 */
    val level: Int? = null,
    val coins: Int? = null,
    /** 原样保存的 `SESSDATA` 等登录 Cookie 串;空 = 无凭证 */
    val cookie: String = "",
    val refreshToken: String = "",
) {
    val isLoggedIn: Boolean get() = mid != NO_ACCOUNT && cookie.isNotBlank()

    companion object {
        const val NO_ACCOUNT = 0L
    }
}
