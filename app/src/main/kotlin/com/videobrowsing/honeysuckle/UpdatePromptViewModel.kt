package com.videobrowsing.honeysuckle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videobrowsing.honeysuckle.core.data.update.AppRelease
import com.videobrowsing.honeysuckle.core.data.update.UpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 冷启动那一次"有没有新版本"的读数与它的出口。
 *
 * 单独一个 ViewModel 而不并进 [HsyAppViewModel]:那份是壳层每一帧都要看的档位(主题、底栏、小窗),
 * 这一份是一次背景问话的结果,一轮进程里最多变一次。合进去等于让每次设置落盘都顺带重算一遍弹窗判定。
 */
@HiltViewModel
class UpdatePromptViewModel @Inject constructor(private val updates: UpdateRepository) : ViewModel() {

    private val mutableRelease = MutableStateFlow<AppRelease?>(null)

    /** 要弹的那一版;null = 什么都不弹 */
    val release: StateFlow<AppRelease?> = mutableRelease.asStateFlow()

    /** 由壳层在首帧画完之后调一次;开关关着、没新版、这一版已经弹过,都停在 null */
    fun checkOnStartup() {
        viewModelScope.launch { mutableRelease.value = updates.checkOnStartup() }
    }

    /**
     * 弹窗被关掉,无论走哪一条出口都记一笔"这一版说过了"。
     *
     * 不记就是每次冷启动把同一版的重申一遍;记了也不会永久沉默 —— 仓库再发新版,版本名对不上
     * 盘上那一行,判定重新成立,于是又弹一次。
     */
    fun acknowledged() {
        val release = mutableRelease.value ?: return
        mutableRelease.value = null
        viewModelScope.launch { updates.markReminded(release) }
    }
}
