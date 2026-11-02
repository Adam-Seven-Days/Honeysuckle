package com.videobrowsing.honeysuckle.core.data.auth

import com.videobrowsing.honeysuckle.core.common.AppError
import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.common.flatMap
import com.videobrowsing.honeysuckle.core.common.map
import com.videobrowsing.honeysuckle.core.data.di.IoDispatcher
import com.videobrowsing.honeysuckle.core.data.remote.AccountNavApi
import com.videobrowsing.honeysuckle.core.data.remote.PasswordLoginApi
import com.videobrowsing.honeysuckle.core.data.remote.QrLoginApi
import com.videobrowsing.honeysuckle.core.datastore.account.AccountPreferenceRepository
import com.videobrowsing.honeysuckle.core.model.QrLoginSession
import com.videobrowsing.honeysuckle.core.model.QrPoll
import com.videobrowsing.honeysuckle.core.model.QrPollState
import com.videobrowsing.honeysuckle.core.model.UserProfile
import com.videobrowsing.honeysuckle.core.network.session.HsySession
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * [AuthRepository] 的实现。
 *
 * 成功那一拍的顺序是有讲究的:**先写进程会话,再问 nav,最后落盘**。
 *  - 先会话:nav 需带上刚拿到的 Cookie 才会回答"已登录";
 *  - 再 nav:名字与头像是平台唯一权威来源,拿不到也不阻断登录(退空名);
 *  - 最后落盘:盘上一次写完五件,不会出现"盘上有 cookie 没身份"的半截状态。
 * 落盘之后由 `:app` 的启动收集器继续把盘上的凭证同步回会话,冷启动不需要再走一遍扫码逻辑。
 *
 * 为什么退出不调平台注销接口:当前的链路只要求本地退出;服务端会话可以等过期,
 * 而注销接口需要 `sid` 与 csrf 双条件,把一件"本地清干净"的事绑到一次可能失败的网络写
 * 会让"退出"按钮看起来时灵时不灵。这一条留待账号管理成型时再补。
 */
@Singleton
class DefaultAuthRepository @Inject constructor(
    private val qrLoginApi: QrLoginApi,
    private val passwordLoginApi: PasswordLoginApi,
    private val navApi: AccountNavApi,
    private val accountStore: AccountPreferenceRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AuthRepository {

    override val profile: Flow<UserProfile?> = accountStore.preferences.map { prefs ->
        if (!prefs.isLoggedIn) {
            null
        } else {
            UserProfile(
                mid = prefs.mid,
                name = prefs.name,
                avatarUrl = prefs.avatarUrl,
                level = prefs.level,
                coins = prefs.coins,
            )
        }
    }

    override val accounts: Flow<List<UserProfile>> = accountStore.accounts.map { stored ->
        stored.filter { it.isLoggedIn }.map {
            UserProfile(
                mid = it.mid,
                name = it.name,
                avatarUrl = it.avatarUrl,
                level = it.level,
                coins = it.coins,
            )
        }
    }

    override suspend fun switchAccount(mid: Long): Boolean = withContext(ioDispatcher) {
        val target = accountStore.accounts.first().firstOrNull { it.mid == mid && it.isLoggedIn }
            ?: return@withContext false
        if (!accountStore.switchTo(mid)) return@withContext false
        // 会话与盘上的指针必须同一拍落下:只换指针不带 Cookie,下一个请求仍是上一个身份
        HsySession.updateLoginCookies(target.cookie)
        true
    }

    override suspend fun forgetAccount(mid: Long): Boolean = withContext(ioDispatcher) {
        val wasCurrent = accountStore.preferences.first().mid == mid
        val removed = accountStore.forget(mid)
        if (removed && wasCurrent) HsySession.clear()
        removed
    }

    override suspend fun createQrSession(): HsyResult<QrLoginSession> =
        withContext(ioDispatcher) { qrLoginApi.generate() }

    override suspend fun poll(session: QrLoginSession): HsyResult<QrPoll> = withContext(ioDispatcher) {
        qrLoginApi.poll(session.qrcodeKey).flatMap { result ->
            if (result.poll.state != QrPollState.SUCCESS) {
                return@flatMap HsyResult.Success(result.poll)
            }
            completeSignIn(
                cookieHeader = result.cookieHeader,
                refreshToken = result.refreshToken,
                fallbackMid = result.mid,
            ).map { result.poll }
        }
    }

    override suspend fun signInWithPassword(username: String, password: String): HsyResult<Unit> =
        withContext(ioDispatcher) {
            passwordLoginApi.login(username, password).flatMap { result ->
                completeSignIn(
                    cookieHeader = result.cookieHeader,
                    refreshToken = result.refreshToken,
                    fallbackMid = result.mid,
                )
            }
        }

    /**
     * 扫码与密码登录共用的收尾:先会话、再 nav、最后落盘。
     *
     * 拿不到 mid 就撤掉会话 —— 留着 Cookie 而"我的"页仍是游客,是比登录失败更难查的状态。
     */
    private suspend fun completeSignIn(cookieHeader: String, refreshToken: String, fallbackMid: Long): HsyResult<Unit> {
        // 后续 nav 请求必须带上这一串,否则它回的是游客身份
        HsySession.updateLoginCookies(cookieHeader)

        val navProfile = (navApi.fetch() as? HsyResult.Success)?.value
        val mid = navProfile?.mid ?: fallbackMid
        if (mid <= 0L) {
            HsySession.clear()
            return HsyResult.Failure(AppError.Decode("登录成功但拿不到账号 mid(nav 失败且凭证里没有 DedeUserID)"))
        }

        accountStore.signIn(
            mid = mid,
            name = navProfile?.name.orEmpty(),
            avatarUrl = navProfile?.avatarUrl.orEmpty(),
            cookie = cookieHeader,
            refreshToken = refreshToken,
            level = navProfile?.level,
            coins = navProfile?.coins,
        )
        return HsyResult.Success(Unit)
    }

    override suspend fun signOut() {
        // 先断进程会话:下一个请求立刻回到游客态,不必等盘写完
        HsySession.clear()
        withContext(ioDispatcher) { accountStore.signOut() }
    }
}
