package com.videobrowsing.honeysuckle.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videobrowsing.honeysuckle.core.common.AppError
import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.common.userMessage
import com.videobrowsing.honeysuckle.core.data.auth.AuthRepository
import com.videobrowsing.honeysuckle.core.data.feed.HomeFeedRepository
import com.videobrowsing.honeysuckle.core.datastore.general.AppPreferenceStore
import com.videobrowsing.honeysuckle.core.model.FeedItem
import com.videobrowsing.honeysuckle.core.model.FeedPage
import com.videobrowsing.honeysuckle.core.model.HomeFeedSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 这一屏正在做的那件事 —— 一个枚举而不是三个布尔:"骨架"和"下拉圈"和"底部转圈"不会同时成立 */
enum class HomeBusy { IDLE, FIRST, REFRESH, MORE }

/** 0 = 全站分区;与 `HomeFeedApi.ALL_REGIONS` 同值,首页不为了一个常量去引数据层 API */
internal const val ALL_REGIONS = 0

/**
 * 首页的全部读数(UI 只读这一份 StateFlow)。
 *
 * 与"我的/历史/设置"那三屏不同,这一屏的读数不是从仓库流出来的,而是**一次一次取回来的**:
 * 远端分页没有"一条 Flow 流到底"的形态(推荐流实测压根没有第二页),所以这里由 Action 驱动、
 * 每成功一次把结果并进状态。那三屏的 `stateIn(combine(...))` 形状套不到这一屏上,不是偷懒。
 */
data class HomeUiState(
    /** 当前档位,默认官方推荐;打开时会被盘上的偏好覆盖 */
    val source: HomeFeedSource = HomeFeedSource.RCMD,
    /** 最新投稿那一档的分区号;0 = 全站,其余档位忽略它 */
    val regionRid: Int = ALL_REGIONS,
    val items: List<FeedItem> = emptyList(),
    val busy: HomeBusy = HomeBusy.IDLE,
    /** 还能不能再往下要 —— 跟着接口给的 [FeedPage.moreAvailable],不由"这批够不够一屏"猜 */
    val moreAvailable: Boolean = false,
    /** 累计被剔出卡片的条目数(非视频路由、缺 bvid):少了几条要在屏上说出来,不能静默少给 */
    val skipped: Int = 0,
    /** 关注档在游客态:不取流,屏上给扫码入口 */
    val loginRequired: Boolean = false,
    val errorMessage: String? = null,
) {
    /** 骨架只在"第一批还没回来"时摆;刷新与翻页都保留已有列表,否则每刷一次屏就闪一回灰块 */
    val showSkeleton: Boolean get() = busy == HomeBusy.FIRST && items.isEmpty()
}

/** 首屏能发生的动作;命名与屏上的动作一一对应 */
sealed interface HomeAction {
    data object Refresh : HomeAction
    data class SelectSource(val source: HomeFeedSource) : HomeAction

    /** 只在 [HomeFeedSource.NEWEST] 下有意义;其余档位屏上不摆分区选择 */
    data class SelectRegion(val rid: Int) : HomeAction
    data object LoadMore : HomeAction
}

/**
 * 首页读数与取数。
 *
 * 偏好盘的接线口径:
 *  - **首屏读盘上的"信息流偏好"**,而不是硬编码推荐档;读完之后的每一拍变化(设置页改档)也跟着走;
 *  - 用户在首页点档位时先乐观切换、同时写盘 —— 写盘回流会再次发出同一个档位,由"相同就跳过"挡掉,
 *    所以一次点击只发一趟请求;
 *  - **分区不落盘**:它是"这次想逛哪个区",不是长期偏好;换回首页时回到全站。
 */
@Suppress("TooManyFunctions") // 取数、登录引导与错误折叠各占几条,拆开只会把一条状态链断成互相看不见的两半
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val homeFeed: HomeFeedRepository,
    private val appPreferences: AppPreferenceStore,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val mutableState = MutableStateFlow(HomeUiState(busy = HomeBusy.FIRST))

    val state: StateFlow<HomeUiState> = mutableState.asStateFlow()

    /** 页码不进 UiState:它是"下一次要哪一页"的游标,不是屏上要读的数 */
    private var loadedPages = 0
    private var fetchJob: Job? = null

    init {
        observePreferredSource()
        observeLogin()
    }

    /**
     * 首拍铺初始档位,之后只处理真正变化的那些拍。
     *
     * 分成 `first()` 与 `drop(1)` 而不是一个 `collect`:首页自己点档位时也会写盘,
     * 若把首拍也交给"变化就换档"那条路,构造时会多走一趟同档位的请求。
     */
    private fun observePreferredSource() {
        viewModelScope.launch {
            val preferred = appPreferences.preferences
                .map { HomeFeedSource.fromStorageId(it.homeFeedSourceId) }
                .distinctUntilChanged()
            applySource(preferred.first())
            preferred.drop(1).collect { source ->
                if (source != mutableState.value.source) applySource(source)
            }
        }
    }

    /**
     * 关注档是唯一要登录的那一档:游客选中它时不发请求,屏上换成扫码引导。
     * 登录成功之后再自动补取这一档 —— 用户不必回到首页重新点一次。
     */
    private fun observeLogin() {
        viewModelScope.launch {
            var wasLoggedIn = false
            authRepository.profile.collect { profile ->
                val loggedIn = profile != null
                val current = mutableState.value
                if (shouldBackfillFollow(current, loggedIn, wasLoggedIn)) {
                    mutableState.update { it.copy(loginRequired = false) }
                    fetch(HomeFeedSource.FOLLOWING, FIRST_PAGE, HomeBusy.FIRST)
                }
                if (!loggedIn && wasLoggedIn && current.source == HomeFeedSource.FOLLOWING) {
                    markLoginRequired(HomeFeedSource.FOLLOWING)
                }
                wasLoggedIn = loggedIn
            }
        }
    }

    private suspend fun applySource(source: HomeFeedSource) {
        if (source == HomeFeedSource.FOLLOWING && authRepository.profile.first() == null) {
            markLoginRequired(source)
        } else {
            fetch(source, FIRST_PAGE, HomeBusy.FIRST)
        }
    }

    private fun markLoginRequired(source: HomeFeedSource) {
        fetchJob?.cancel()
        mutableState.update {
            it.copy(
                source = source,
                busy = HomeBusy.IDLE,
                items = emptyList(),
                skipped = 0,
                moreAvailable = false,
                errorMessage = null,
                loginRequired = true,
            )
        }
    }

    fun onAction(action: HomeAction) {
        val current = mutableState.value
        when (action) {
            HomeAction.Refresh -> refresh(current)
            is HomeAction.SelectSource -> selectSource(current.source, action.source)
            is HomeAction.SelectRegion -> selectRegion(current, action.rid)
            HomeAction.LoadMore -> loadMore(current)
        }
    }

    private fun refresh(current: HomeUiState) {
        if (current.source == HomeFeedSource.FOLLOWING && current.loginRequired) {
            // 游客点了下拉:重新问一次登录态,登录了就补取,没登录就继续摆在引导上
            viewModelScope.launch {
                if (authRepository.profile.first() != null) {
                    fetch(HomeFeedSource.FOLLOWING, FIRST_PAGE, HomeBusy.FIRST)
                }
            }
            return
        }
        val busy = if (current.items.isEmpty()) HomeBusy.FIRST else HomeBusy.REFRESH
        fetch(current.source, FIRST_PAGE, busy)
    }

    private fun selectSource(currentSource: HomeFeedSource, source: HomeFeedSource) {
        // 点当前档位不该重发一次请求,屏上也因此不会闪一下
        if (source == currentSource) return
        viewModelScope.launch { applySource(source) }
        viewModelScope.launch { appPreferences.setHomeFeedSourceId(source.storageId) }
    }

    private fun selectRegion(current: HomeUiState, rid: Int) {
        if (current.source != HomeFeedSource.NEWEST || rid == current.regionRid) return
        fetch(current.source, FIRST_PAGE, HomeBusy.FIRST, rid = rid)
    }

    private fun loadMore(current: HomeUiState) {
        if (current.loginRequired) return
        if (current.busy != HomeBusy.IDLE || !current.moreAvailable) return
        fetch(current.source, loadedPages + 1, HomeBusy.MORE)
    }

    /**
     * 一趟取数。
     *
     * 起手先掐掉上一趟:换档位与"下拉连刷两下"都会让两趟同时在飞,而先出发的那一趟可能**后**回来 ——
     * 不掐,屏上就会出现"切到热门却刷回推荐那批"。
     *
     * 要不要当场清空已有列表,看的是这一趟的**性质**而不是它的成败:
     *  - `FIRST`(首屏、换档位、换分区)当场清空并归零账:骨架位上留着上一个档位的条目,等于把旧内容贴上新标签;
     *  - `REFRESH` 与 `MORE` 不动列表,等 Success 那一刻再整批换掉 / 追加。
     *    于是"失败不抹已有列表"不需要任何补救动作 —— 下拉刷新失败时屏上还是刚才那批,
     *    而不是先清空再报一句错,把一次网络抖动演成"视频全没了"。
     */
    private fun fetch(
        source: HomeFeedSource,
        pageIndex: Int,
        busy: HomeBusy,
        rid: Int = mutableState.value.regionRid,
    ) {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            val clearNow = busy == HomeBusy.FIRST
            mutableState.update { current ->
                current.copy(
                    source = source,
                    regionRid = if (clearNow) rid else current.regionRid,
                    busy = busy,
                    items = if (clearNow) emptyList() else current.items,
                    skipped = if (clearNow) 0 else current.skipped,
                    moreAvailable = if (clearNow) false else current.moreAvailable,
                    errorMessage = null,
                    loginRequired = false,
                )
            }
            homeFeed.page(source, pageIndex, effectiveRid(source, rid)).collect { result ->
                mutableState.update { current -> reduce(current, result, pageIndex) }
            }
        }
    }

    private fun reduce(current: HomeUiState, result: HsyResult<FeedPage>, pageIndex: Int): HomeUiState = when (result) {
        is HsyResult.Success -> {
            val page = result.value
            if (pageIndex >= loadedPages) loadedPages = pageIndex
            // 整批换还是往下接,同样由这一趟的性质决定:`FIRST`/`REFRESH` 换,`MORE` 接
            val append = current.busy == HomeBusy.MORE
            val base = if (append) current.items else emptyList()
            current.copy(
                items = base + page.items.filterDistinctAgainst(base),
                busy = HomeBusy.IDLE,
                moreAvailable = page.moreAvailable,
                skipped = if (append) current.skipped + page.skipped else page.skipped,
                errorMessage = null,
            )
        }

        // 失败不抹已有列表:一屏看得见的内容比一次没拿到的下一页值钱
        is HsyResult.Failure -> {
            // 关注档拿到 -101 = 凭证在取数途中失效:切回登录引导,而不是把平台码原样甩在屏上
            val server = result.error as? AppError.Server
            val needsLogin = current.source == HomeFeedSource.FOLLOWING &&
                server?.code == HTTP_NOT_LOGGED_IN
            current.copy(
                busy = HomeBusy.IDLE,
                errorMessage = result.error.userMessage(),
                loginRequired = needsLogin,
                items = if (needsLogin) emptyList() else current.items,
                moreAvailable = if (needsLogin) false else current.moreAvailable,
            )
        }
    }

    private companion object {
        const val FIRST_PAGE = 1
        const val HTTP_NOT_LOGGED_IN = -101
    }
}

/** 分区只属于最新投稿那一档;其余档位把盘外的 0 送出去(接口自己也忽略它) */
private fun effectiveRid(source: HomeFeedSource, rid: Int): Int =
    if (source == HomeFeedSource.NEWEST) rid else ALL_REGIONS

/** 关注档该不该在这一次登录回流里补取:刚登录、停在关注档、当前还摆在扫码引导上 */
private fun shouldBackfillFollow(current: HomeUiState, loggedIn: Boolean, wasLoggedIn: Boolean): Boolean =
    loggedIn && !wasLoggedIn && current.source == HomeFeedSource.FOLLOWING && current.loginRequired

/**
 * 跨页去重:热门第 2 页与第 1 页实测可能有重叠条目,同一条视频在一列里出现两次比少一条更难读。
 * 换档位与刷新喂进来的是空表,所以同一批内部的重叠也一并去掉 —— 判据只有一条"见过就没有第二遍"。
 */
private fun List<FeedItem>.filterDistinctAgainst(existing: List<FeedItem>): List<FeedItem> {
    val seen = existing.mapTo(mutableSetOf()) { it.brief.bvid }
    return filter { seen.add(it.brief.bvid) }
}
