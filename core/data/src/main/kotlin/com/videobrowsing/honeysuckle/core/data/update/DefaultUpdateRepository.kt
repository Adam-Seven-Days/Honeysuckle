package com.videobrowsing.honeysuckle.core.data.update

import android.content.Context
import com.videobrowsing.honeysuckle.core.common.AppError
import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.datastore.general.AppPreferenceStore
import com.videobrowsing.honeysuckle.core.datastore.general.UpdateReleaseSnapshot
import com.videobrowsing.honeysuckle.core.datastore.general.UpdateStateStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * 取数([GitHubReleaseApi])→ 记账([UpdateStateStore])→ 判该不该弹。
 *
 * 自动与主动两条路共用同一份比较与同一份缓存:复制成两分迟早会在"什么算更新"上分叉,
 * 而同一台机器上"设置里说是最新、启动弹窗说不是"是最难解释的那种 bug。
 */
@Singleton
class DefaultUpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val releases: GitHubReleaseApi,
    private val appPreferences: AppPreferenceStore,
    private val state: UpdateStateStore,
) : UpdateRepository {

    /**
     * 关着、没问出来、已是最新、这一版已经弹过 —— 四种都回 null。
     *
     * 这里没有"失败也要说一声"那一条:冷启动那一次是背景问话,而 GitHub 在国内直连经常超时,
     * 把它折成一句提示等于让一半用户每次启动都看到一条与自己无关的报错。
     */
    override suspend fun checkOnStartup(): AppRelease? {
        if (!appPreferences.preferences.first().updateCheckEnabled) return null
        val outcome = fetch()
        if (outcome !is UpdateOutcome.Available) return null
        val release = outcome.release
        return release.takeUnless { it.version == state.remindedVersion() }
    }

    override suspend fun checkNow(): UpdateOutcome = fetch()

    override suspend fun markReminded(release: AppRelease) {
        state.markReminded(release.version)
    }

    /**
     * 每次都先读盘上那一对读数,再决定是问"变了没有"还是问"最新是哪版"。
     *
     * 换回 304 时用盘上那一版顶上,既不用重新解析,也不额外花额度;第一次没有 `ETag` 可带,
     * 自然打整条请求,回来把新的一版与 `ETag` 一起落盘 —— 从这一次起才谈得上条件请求。
     */
    private suspend fun fetch(): UpdateOutcome {
        // 连本机版本都读不出来时无从比:报"读不出来"比猜一个版本号去弹更容易解释
        val current = appVersionName(context) ?: return UpdateOutcome.Failed(AppError.Unknown("读不到本机版本号"))
        val cached = state.cache()
        return when (val result = releases.latest(cached?.etag)) {
            is HsyResult.Failure -> UpdateOutcome.Failed(result.error)

            is HsyResult.Success -> when (val fetched = result.value) {
                is ReleaseFetch.Fresh -> {
                    state.writeCache(fetched.etag, fetched.release.toSnapshot())
                    judge(current, fetched.release)
                }

                ReleaseFetch.NotModified -> judge(current, cached?.release?.toAppRelease())
            }
        }
    }

    /** 判定只写一处:远端那一版严格高于本机这一版,才叫"有更新" */
    private fun judge(current: String, release: AppRelease?): UpdateOutcome = when {
        release == null -> UpdateOutcome.Failed(AppError.Decode("盘上没有可比的那一版记录"))
        isNewerVersion(release.version, current) -> UpdateOutcome.Available(release)
        else -> UpdateOutcome.UpToDate(current)
    }
}

/** 落盘形状与内存形状互转:存储层不认识 [AppRelease],这一层不认识 DataStore 的键 */
private fun AppRelease.toSnapshot(): UpdateReleaseSnapshot = UpdateReleaseSnapshot(
    version = version,
    title = title,
    notes = notes,
    pageUrl = pageUrl,
)

private fun UpdateReleaseSnapshot.toAppRelease(): AppRelease = AppRelease(
    version = version,
    title = title,
    notes = notes,
    pageUrl = pageUrl,
)
