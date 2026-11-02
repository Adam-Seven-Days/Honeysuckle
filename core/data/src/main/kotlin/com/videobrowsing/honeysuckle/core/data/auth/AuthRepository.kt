package com.videobrowsing.honeysuckle.core.data.auth

import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.model.QrLoginSession
import com.videobrowsing.honeysuckle.core.model.QrPoll
import com.videobrowsing.honeysuckle.core.model.UserProfile
import kotlinx.coroutines.flow.Flow

/**
 * 登录态的门面:扫码、轮询、退出,以及"现在是谁"的订阅。
 *
 * [profile] 是 UI 唯一该读的身份:游客态为 `null`,不是空对象 —— 空对象会让"我的"页
 * 显示一个没有名字的已登录卡片。真正的凭证不从这里流出:凭据归账号盘与 [com.videobrowsing.honeysuckle.core.network.session.HsySession],
 * UI 不需要也不应该拿到它。
 */
interface AuthRepository {

    val profile: Flow<UserProfile?>

    /**
     * 这台设备上留着凭证的账号(按 mid 排序),给账号切换弹窗用。
     *
     * 与 [profile] 一样只出身份三件,不出 Cookie —— 切换动作在仓库内部完成会话同步,
     * UI 拿不到也不需要拿到凭据。
     */
    val accounts: Flow<List<UserProfile>>

    /** 生成一次扫码会话:二维码文本 + 轮询凭据 */
    suspend fun createQrSession(): HsyResult<QrLoginSession>

    /**
     * 轮询一次。
     *
     * 成功那一拍仓库内部会完成三件事:凭证进进程会话、问一次平台"我是谁"、把身份与凭证落盘。
     * 调用方看到 [com.videobrowsing.honeysuckle.core.model.QrPollState.SUCCESS] 时,
     * [profile] 已经可以在下一次收集里给出登录用户。
     */
    suspend fun poll(session: QrLoginSession): HsyResult<QrPoll>

    /**
     * 账号密码登录。
     *
     * 密码在本机用平台公钥加密后直接提交给 B 站,不落盘、不进日志;成功那一拍与扫码共用
     * 同一条链路(凭证进会话 → 问 nav → 落账号盘)。
     */
    suspend fun signInWithPassword(username: String, password: String): HsyResult<Unit>

    /**
     * 切到盘上已有的某个账号。
     *
     * 返回 false 表示这台设备没登录过它(或凭证不完整)。成功时进程会话已换成该账号的 Cookie,
     * [profile] 会在下一次收集里翻成新身份。
     */
    suspend fun switchAccount(mid: Long): Boolean

    /** 从盘上移除一个账号;删的是当前账号时会话一并清空。返回 false 表示原本就没有 */
    suspend fun forgetAccount(mid: Long): Boolean

    /** 退出:清进程会话与账号盘。服务端会话不在基础版里注销(没有 sid 也能正常过期) */
    suspend fun signOut()
}
