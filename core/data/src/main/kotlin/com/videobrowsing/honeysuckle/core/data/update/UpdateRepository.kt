package com.videobrowsing.honeysuckle.core.data.update

import com.videobrowsing.honeysuckle.core.common.AppError

/** 主动检查那一次的回执;自动检查不用它(见 [UpdateRepository.checkOnStartup]) */
sealed interface UpdateOutcome {
    /** 已经是最新的了;[current] 是这台自己的版本名,屏上要把它念出来 */
    data class UpToDate(val current: String) : UpdateOutcome

    /** 有更高的一版 */
    data class Available(val release: AppRelease) : UpdateOutcome

    /** 这一次没问出来;错误分类交上去,文案归 UI 层翻 */
    data class Failed(val error: AppError) : UpdateOutcome
}

/**
 * "有没有新版本"这一件事的唯一出口。
 *
 * 两条路的差别只在**要不要替用户做决定**,取数与比较是同一份:
 *  - 自动那一条(冷启动)只问"该不该弹个窗",关着、失败、没更新、这一版已经弹过,都回 null;
 *  - 主动那一条(关于页)把结果原样报回来,不看开关也不看"弹过没有"—— 用户按了就要听到回答。
 */
interface UpdateRepository {

    /** 冷启动那一次自动检查;回 null = 这一轮什么都不做 */
    suspend fun checkOnStartup(): AppRelease?

    /** 用户主动点"检查更新" */
    suspend fun checkNow(): UpdateOutcome

    /** 记下"这一版已经弹过了":弹窗无论被怎么关掉都记,免得下一次启动再拦一道 */
    suspend fun markReminded(release: AppRelease)
}
