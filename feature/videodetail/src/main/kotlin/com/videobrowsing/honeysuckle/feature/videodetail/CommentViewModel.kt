package com.videobrowsing.honeysuckle.feature.videodetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.common.userMessage
import com.videobrowsing.honeysuckle.core.common.writeErrorText
import com.videobrowsing.honeysuckle.core.data.auth.AuthRepository
import com.videobrowsing.honeysuckle.core.data.comment.CommentRepository
import com.videobrowsing.honeysuckle.core.datastore.general.AppPreferenceStore
import com.videobrowsing.honeysuckle.core.model.Comment
import com.videobrowsing.honeysuckle.core.model.CommentPage
import com.videobrowsing.honeysuckle.core.model.CommentSort
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 楼中楼页路由上的三个参数名;`:app` 的 `navArgument` 必须用同样的字面量 */
const val THREAD_AID_ARG = "aid"
const val THREAD_ROOT_ARG = "root"
const val THREAD_UP_MID_ARG = "upMid"

private const val FIRST_PAGE = 1
private const val MESSAGE_LINGER_MS = 4_000L
private const val LIKE_READ_ONLY_HINT = "评论点赞已关闭,可在「设置 → 评论」开启"

/**
 * 评论区与楼中楼页的全部读数。
 *
 * 一份读数摆成两种形状,由 [CommentUiState.threadRootId] 分别:0 是详情页那块评论区(顶层评论、
 * 按平台游标翻页),>0 是压在评论区之上的一楼层(主评论钉在头上、这一楼的回复按页码翻)。
 * 登录态、两格开关与发表口两边共用 —— 拆成两个 ViewModel 只会把这三样抄第二遍。
 */
data class CommentUiState(
    val aid: Long = 0L,
    val upMid: Long = 0L,
    val comments: List<Comment> = emptyList(),
    val sort: CommentSort = CommentSort.HOT,
    val total: Int = 0,
    /** 顶层评论的下一页游标;空串 = 第一页或已经到底 */
    val cursor: String = "",
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val moreAvailable: Boolean = false,
    val errorMessage: String? = null,
    val loggedIn: Boolean = false,
    val sending: Boolean = false,
    /** 发出成功一次就 +1;输入框据此清空,失败时保留原文 */
    val sendAcceptedAt: Long = 0L,
    val message: String? = null,
    /** 用户亲手挑的回复对象;null = 没挑。楼中楼页没挑时另有一个默认落点,见 [postTarget] */
    val replyTo: Comment? = null,
    /** 这一屏是一楼层:>0 时 [comments] 装的是这一楼的回复,分页按页码而不是游标 */
    val threadRootId: Long = 0L,
    /** 楼中楼页顶部那条主评论;`null` = 还在读,或平台这一页没带回 */
    val threadRoot: Comment? = null,
    /** 本地记住"我赞过哪些评论/回复":平台列表只给赞数,取消赞要靠这份记忆 */
    val likedIds: Set<Long> = emptySet(),
    val likeBusy: Set<Long> = emptySet(),
    /** 评论点赞只读(设置可选,默认关):按钮照摆,点了只提示,不发请求 */
    val likeReadOnly: Boolean = false,
    /** 楼中楼的回复行是否摆点赞(设置可选,默认关) */
    val replyLikesVisible: Boolean = false,
) {
    /**
     * 平台对未登录用户只给预览:回包说 `is_end=true`,但总数远多于手里的几条。
     *
     * 这不是"没有更多",不能念成"已加载全部";登录后同一条接口会照常翻页,
     * 所以列表底部要把原因说出来并给登录入口。
     */
    val guestLimited: Boolean
        get() = !loggedIn && !moreAvailable && comments.isNotEmpty() && total > comments.size

    /**
     * 这一句发出去落在谁身上。
     *
     * 用户挑的那条优先;楼中楼页里没挑就落回这一楼的主评论 —— 在那一页打字默认就是回这一楼,
     * 不是又起一个新楼。评论区(顶层)两者都是 null,发的便是一条新楼。
     */
    val postTarget: Comment?
        get() = replyTo ?: threadRoot
}

sealed interface CommentAction {
    data object Reload : CommentAction
    data object LoadMore : CommentAction
    data class ChangeSort(val sort: CommentSort) : CommentAction
    data class Send(val text: String) : CommentAction
    data class ReplyTo(val comment: Comment?) : CommentAction
    data class DismissMessage(val message: String) : CommentAction
    data class ToggleLike(val comment: Comment) : CommentAction
    data object Retry : CommentAction
}

/**
 * 评论区的读数与写入口。
 *
 * 三条口径:
 *  - **发出去之后重读第一页**,不本地拼一条假评论:平台不给稳定 id,拼出来的那条会在真身回来时重复;
 *  - **评论区只露平台内嵌带回的那两条楼中楼**,整楼全貌归楼中楼页 —— 「展开 / 收起回复」与它自己那套
 *    按楼分页的读数一起退场,同一楼不再既内联展开又能跳页;
 *  - **游客也能看,不能写** —— 写入口换成扫码引导,而不是让用户点一次失败再告诉他。
 *
 * 动作数超过 detekt 的阈值是这一屏的本性:列表/排序/分页/发表/回复/重试/点赞/提示,
 * 每一条都是一个独立的用户意图,拆成几个类只会让状态在它们之间来回搬。
 */
@Suppress("TooManyFunctions")
@HiltViewModel
class CommentViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val commentRepository: CommentRepository,
    authRepository: AuthRepository,
    preferences: AppPreferenceStore,
) : ViewModel() {

    private val mutableState = MutableStateFlow(
        CommentUiState(
            aid = savedStateHandle.get<Long>(THREAD_AID_ARG) ?: 0L,
            threadRootId = savedStateHandle.get<Long>(THREAD_ROOT_ARG) ?: 0L,
            upMid = savedStateHandle.get<Long>(THREAD_UP_MID_ARG) ?: 0L,
        ),
    )
    val state: StateFlow<CommentUiState> = mutableState.asStateFlow()

    private var fetchJob: Job? = null
    private var messageJob: Job? = null

    /** 楼中楼页下一次该要第几页:重取第一页回到 1,续翻与重试都从这里取 */
    private var nextPage = FIRST_PAGE

    /**
     * 用户这一场有没有亲手点过排序。
     *
     * 点过之后设置里那一档就不再回流覆盖 —— 他刚在评论区选了"最新",设置盘上一次回流
     * 把列表拨回"最热",看起来像是应用自己乱了顺序。
     */
    private var sortTouchedByUser = false

    init {
        observeAccount(authRepository, preferences)
        // 楼中楼页没有"详情页递稿件"那一步:路由带着 aid 与楼层 id,这一屏自己开篇
        if (mutableState.value.threadRootId > 0L) load(append = false)
    }

    /**
     * 详情页把稿件交给评论区时调用:aid / upMid 来自详情读数。
     * 同一稿件重复调用是空操作;换稿件才清掉上一支的楼层与"我赞过哪些"。
     */
    fun open(aid: Long, upMid: Long) {
        val current = mutableState.value
        if (aid <= 0L || (current.aid == aid && current.upMid == upMid)) return
        fetchJob?.cancel()
        nextPage = FIRST_PAGE
        mutableState.update {
            CommentUiState(
                aid = aid,
                upMid = upMid,
                sort = it.sort,
                loggedIn = it.loggedIn,
                likeReadOnly = it.likeReadOnly,
                replyLikesVisible = it.replyLikesVisible,
            )
        }
        load(append = false)
    }

    fun onAction(action: CommentAction) {
        when (action) {
            CommentAction.Reload, CommentAction.Retry -> load(append = false)

            CommentAction.LoadMore -> {
                val current = mutableState.value
                if (current.moreAvailable && !current.loading && !current.loadingMore) load(append = true)
            }

            is CommentAction.ChangeSort -> {
                // 用户在这屏点过哪一档,盘上那一档就不再回来覆盖他:排序是这一场的决定
                sortTouchedByUser = true
                changeSort(action.sort)
            }

            is CommentAction.Send -> send(action.text)

            is CommentAction.ReplyTo -> mutableState.update { it.copy(replyTo = action.comment) }

            is CommentAction.DismissMessage -> {
                messageJob?.cancel()
                mutableState.update { it.copy(message = null) }
            }

            is CommentAction.ToggleLike -> toggleLike(action.comment)
        }
    }

    /**
     * 登录态、评论两格开关,以及"默认排序"那一档。
     *
     * 两个开关都由这一屏自己读:点赞只读是"点下去会发生什么"的判断,不能只靠 UI 藏按钮 ——
     * 藏了就等于用户点了没反应;摆着但在这一层拦下,才有一句能读的提醒。
     *
     * 默认排序只在**用户这一场没亲手换过排序**时才跟着盘走:换过之后屏上那一档就是他的决定,
     * 设置回流不该把他刚点的"最新"又拨回"最热"。
     */
    private fun observeAccount(authRepository: AuthRepository, preferences: AppPreferenceStore) {
        viewModelScope.launch {
            authRepository.profile.collect { profile ->
                mutableState.update { it.copy(loggedIn = profile != null) }
            }
        }
        viewModelScope.launch {
            preferences.preferences.collect { prefs ->
                val preferred = CommentSort.fromStorageId(prefs.commentSortId)
                val adoptSort = !sortTouchedByUser && preferred != mutableState.value.sort
                mutableState.update {
                    it.copy(
                        likeReadOnly = prefs.commentLikeReadOnly,
                        replyLikesVisible = prefs.replyLikesVisible,
                        sort = if (adoptSort) preferred else it.sort,
                    )
                }
                val commentZone = mutableState.value.aid > 0L && mutableState.value.threadRootId == 0L
                // 评论区已经摆过一批才要重取;还没 open() 时把档位留着,第一次读自然用它。
                // 楼中楼页没有排序这一档,设置回流也不去重取它。
                if (adoptSort && commentZone) load(append = false)
            }
        }
    }

    /**
     * 取一页。评论区按平台游标翻,楼中楼页按页码翻 —— 两个端点本就给两套分页,
     * 这里只按这一屏的形状分派,不在 UI 上替平台推算"还有没有下一页"。
     */
    private fun load(append: Boolean) {
        val current = mutableState.value
        if (current.aid <= 0L) {
            mutableState.update { it.copy(errorMessage = "评论暂时无法加载,请稍后再试") }
            return
        }
        if (current.loading || current.loadingMore) return
        val thread = current.threadRootId > 0L
        val cursor = if (append) current.cursor else ""
        val page = if (append) nextPage else FIRST_PAGE
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            mutableState.update {
                it.copy(
                    loading = !append,
                    loadingMore = append,
                    errorMessage = null,
                    comments = if (append) it.comments else emptyList(),
                    cursor = if (append) it.cursor else "",
                    moreAvailable = if (append) it.moreAvailable else false,
                )
            }
            val result = when {
                thread -> commentRepository.replies(current.aid, current.threadRootId, page, current.upMid)
                else -> commentRepository.page(current.aid, cursor, current.sort, current.upMid)
            }
            when (result) {
                is HsyResult.Success -> {
                    val pageData = result.value
                    if (thread) nextPage = page + 1
                    mutableState.update { it.withPage(pageData, append, thread) }
                }

                is HsyResult.Failure -> mutableState.update {
                    it.copy(loading = false, loadingMore = false, errorMessage = result.error.userMessage())
                }
            }
        }
    }

    /**
     * 一页读回来怎么并进屏上。
     *
     * 追加页按 id 去重:平台在页边界重复下发同一条时,屏上不该出现两条一样的回复。
     * 一整页都没有新条目就当它重复回来了,不再往下要 —— 否则会原地打转。
     */
    private fun CommentUiState.withPage(page: CommentPage, append: Boolean, thread: Boolean): CommentUiState {
        val base = if (append) comments else emptyList()
        val seen = base.mapTo(mutableSetOf()) { it.id }
        val merged = base + page.comments.filter { seen.add(it.id) }
        val progressed = !append || merged.size > base.size
        return copy(
            comments = merged,
            total = page.total,
            cursor = page.nextCursor,
            threadRoot = if (thread) page.root ?: threadRoot else null,
            moreAvailable = page.moreAvailable && progressed,
            loading = false,
            loadingMore = false,
            errorMessage = null,
        )
    }

    private fun changeSort(sort: CommentSort) {
        if (mutableState.value.sort == sort) return
        mutableState.update { it.copy(sort = sort) }
        load(append = false)
    }

    private fun send(raw: String) {
        val text = raw.trim()
        val current = mutableState.value
        when {
            text.isEmpty() || current.sending -> Unit
            !current.loggedIn -> showMessage("发评论需要登录")
            else -> postComment(text, current)
        }
    }

    private fun postComment(text: String, current: CommentUiState) {
        val target = current.postTarget
        viewModelScope.launch {
            mutableState.update { it.copy(sending = true) }
            // 回复"回复"时 root 与 parent 不同:root 是楼主、parent 是被回复的那条
            val rootId = target?.let { if (it.rootId > 0L) it.rootId else it.id } ?: 0L
            val parentId = target?.id ?: 0L
            when (val result = commentRepository.send(current.aid, text, rootId, parentId)) {
                is HsyResult.Success -> {
                    mutableState.update {
                        it.copy(sending = false, replyTo = null, sendAcceptedAt = it.sendAcceptedAt + 1)
                    }
                    showMessage("评论已发表")
                    load(append = false)
                }

                is HsyResult.Failure -> {
                    mutableState.update { it.copy(sending = false) }
                    showMessage(writeErrorText("发评论", result.error))
                }
            }
        }
    }

    /**
     * 点赞 / 取消赞。
     *
     * 三道闸在发请求之前:只读模式只回一句提醒、游客只回扫码引导、同一条在飞时不叠第二发。
     * 成功后就地 ±1 并记进 [CommentUiState.likedIds],**不重取列表** —— 重取会把这屏正看着的回复
     * 与滚动位置一起冲回顶部。
     */
    private fun toggleLike(comment: Comment) {
        val current = mutableState.value
        val blocked = when {
            current.likeReadOnly -> LIKE_READ_ONLY_HINT
            !current.loggedIn -> "点赞需要登录"
            else -> null
        }
        if (blocked != null) {
            showMessage(blocked)
            return
        }
        if (comment.id in current.likeBusy) return
        val target = comment.id !in current.likedIds
        viewModelScope.launch {
            mutableState.update { it.copy(likeBusy = it.likeBusy + comment.id) }
            when (val result = commentRepository.setLike(current.aid, comment.id, target)) {
                is HsyResult.Success -> mutableState.update { it.withLikeToggled(comment.id, target) }

                is HsyResult.Failure -> {
                    mutableState.update { it.copy(likeBusy = it.likeBusy - comment.id) }
                    showMessage(writeErrorText("点赞", result.error))
                }
            }
        }
    }

    private fun showMessage(text: String) {
        messageJob?.cancel()
        mutableState.update { it.copy(message = text) }
        messageJob = viewModelScope.launch {
            delay(MESSAGE_LINGER_MS)
            mutableState.update { it.copy(message = null) }
        }
    }
}
