package com.videobrowsing.honeysuckle.feature.videodetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.common.userMessage
import com.videobrowsing.honeysuckle.core.common.writeErrorText
import com.videobrowsing.honeysuckle.core.data.auth.AuthRepository
import com.videobrowsing.honeysuckle.core.data.comment.CommentRepository
import com.videobrowsing.honeysuckle.core.datastore.general.AppPreferenceStore
import com.videobrowsing.honeysuckle.core.model.Comment
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

private const val FIRST_PAGE = 1
private const val SECOND_PAGE = 2
private const val MESSAGE_LINGER_MS = 4_000L
private const val LIKE_READ_ONLY_HINT = "评论点赞已关闭 · 在「设置 → 评论」里可以打开"

/**
 * 评论区的全部读数。
 *
 * [replies] / [expanded] 分开存:楼中楼按需拉取,拉回来的那一层要能在折叠后不丢,
 * 用户再展开时不必重发请求;失败也单独记一笔,不能把整块判成失败。
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
    /** 正在回复谁;null = 顶层评论 */
    val replyTo: Comment? = null,
    val replies: Map<Long, List<Comment>> = emptyMap(),
    val expandedRoots: Set<Long> = emptySet(),
    val repliesLoading: Set<Long> = emptySet(),
    /** 这一楼还有下一页回复:楼中楼一次只给 20 条,展开后按页续 */
    val repliesMore: Set<Long> = emptySet(),
    val repliesLoadingMore: Set<Long> = emptySet(),
    val repliesError: Map<Long, String> = emptyMap(),
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
     * 这不是"没有更多",不能念成"已显示全部";登录后同一条接口会照常翻页,
     * 所以列表底部要把原因说出来并给登录入口。
     */
    val guestLimited: Boolean
        get() = !loggedIn && !moreAvailable && comments.isNotEmpty() && total > comments.size
}

sealed interface CommentAction {
    data object Reload : CommentAction
    data object LoadMore : CommentAction
    data class ChangeSort(val sort: CommentSort) : CommentAction
    data class Send(val text: String) : CommentAction
    data class ReplyTo(val comment: Comment?) : CommentAction
    data class ToggleReplies(val comment: Comment) : CommentAction
    data class RetryReplies(val comment: Comment) : CommentAction

    /** 楼中楼往下翻一页(每页 20 条,不是重取第一页) */
    data class LoadMoreReplies(val comment: Comment) : CommentAction
    data class DismissMessage(val message: String) : CommentAction
    data class ToggleLike(val comment: Comment) : CommentAction
    data object Retry : CommentAction
}

/**
 * 评论区的读数与写入口。
 *
 * 三条口径:
 *  - **发出去之后重读第一页**,不本地拼一条假评论:平台不给稳定 id,拼出来的那条会在真身回来时重复;
 *  - **楼中楼只在展开时取**,并且失败只影响这一层;整块的失败与某一楼的失败是两件事;
 *  - **游客也能看,不能写** —— 写入口换成扫码引导,而不是让用户点一次失败再告诉他。
 *
 * 动作数超过 detekt 的阈值是这一屏的本性:列表/排序/分页/发表/回复/折叠/重试/点赞/提示,
 * 每一条都是一个独立的用户意图,拆成几个类只会让状态在它们之间来回搬。
 */
@Suppress("TooManyFunctions")
@HiltViewModel
class CommentViewModel @Inject constructor(
    private val commentRepository: CommentRepository,
    authRepository: AuthRepository,
    preferences: AppPreferenceStore,
) : ViewModel() {

    private val mutableState = MutableStateFlow(CommentUiState())
    val state: StateFlow<CommentUiState> = mutableState.asStateFlow()

    private var fetchJob: Job? = null
    private var messageJob: Job? = null
    private val replyJobs = mutableMapOf<Long, Job>()

    /** 每一楼下一次该要第几页;第一页收起后仍保留,展开时不重发第一页 */
    private val nextReplyPage = mutableMapOf<Long, Int>()

    /**
     * 用户这一场有没有亲手点过排序。
     *
     * 点过之后设置里那一档就不再回流覆盖 —— 他刚在评论区选了"最新",设置盘上一次回流
     * 把列表拨回"最热",看起来像是应用自己乱了顺序。
     */
    private var sortTouchedByUser = false

    init {
        observeAccount(authRepository, preferences)
    }

    /**
     * 详情页把稿件交给评论区时调用:aid / upMid 来自详情读数。
     * 同一稿件重复调用是空操作;换稿件才清掉上一支的楼层、回复与"我赞过哪些"。
     */
    fun open(aid: Long, upMid: Long) {
        val current = mutableState.value
        if (aid <= 0L || (current.aid == aid && current.upMid == upMid)) return
        fetchJob?.cancel()
        replyJobs.values.forEach { it.cancel() }
        replyJobs.clear()
        nextReplyPage.clear()
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

            is CommentAction.ToggleReplies -> toggleReplies(action.comment)

            is CommentAction.RetryReplies -> {
                mutableState.update {
                    it.copy(
                        expandedRoots = it.expandedRoots + action.comment.id,
                        repliesError = it.repliesError - action.comment.id,
                    )
                }
                loadReplies(action.comment, append = false)
            }

            is CommentAction.LoadMoreReplies -> loadReplies(action.comment, append = true)

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
                // 评论区已经摆过一批才要重取;还没 open() 时把档位留着,第一次读自然用它
                if (adoptSort && mutableState.value.aid > 0L) load(append = false)
            }
        }
    }

    private fun load(append: Boolean) {
        val current = mutableState.value
        if (current.aid <= 0L) {
            mutableState.update { it.copy(errorMessage = "这个视频暂时打不开评论 · 稍后再试") }
            return
        }
        if (current.loading || current.loadingMore) return
        val cursor = if (append) current.cursor else ""
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
            when (val result = commentRepository.page(current.aid, cursor, current.sort, current.upMid)) {
                is HsyResult.Success -> {
                    val pageData = result.value
                    mutableState.update { state ->
                        val base = if (append) state.comments else emptyList()
                        val seen = base.mapTo(mutableSetOf()) { it.id }
                        val merged = base + pageData.comments.filter { seen.add(it.id) }
                        // 追加页若一条新评论都没有,就当这一页重复回来了:再要下去只会原地打转
                        val progressed = !append || merged.size > base.size
                        state.copy(
                            comments = merged,
                            total = pageData.total,
                            cursor = pageData.nextCursor,
                            moreAvailable = pageData.moreAvailable && progressed,
                            loading = false,
                            loadingMore = false,
                            errorMessage = null,
                        )
                    }
                }

                is HsyResult.Failure -> mutableState.update {
                    it.copy(loading = false, loadingMore = false, errorMessage = result.error.userMessage())
                }
            }
        }
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
            !current.loggedIn -> showMessage("发评论需要登录 · 去登录")
            else -> postComment(text, current)
        }
    }

    private fun postComment(text: String, current: CommentUiState) {
        val replyTo = current.replyTo
        viewModelScope.launch {
            mutableState.update { it.copy(sending = true) }
            // 回复"回复"时 root 与 parent 不同:root 是楼主、parent 是被回复的那条
            val rootId = replyTo?.let { if (it.rootId > 0L) it.rootId else it.id } ?: 0L
            val parentId = replyTo?.id ?: 0L
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

    private fun toggleReplies(comment: Comment) {
        val current = mutableState.value
        if (current.expandedRoots.contains(comment.id)) {
            mutableState.update { it.copy(expandedRoots = it.expandedRoots - comment.id) }
            return
        }
        mutableState.update { it.copy(expandedRoots = it.expandedRoots + comment.id) }
        // 已经拉过且没失败就不再重发;已翻到第 N 页的进度还在 nextReplyPage 里
        if (current.replies.containsKey(comment.id) && !current.repliesError.containsKey(comment.id)) return
        loadReplies(comment, append = false)
    }

    /**
     * 楼中楼取数。[append] = false 取第一页并覆盖,true 接着 [nextReplyPage] 往下翻。
     *
     * 同一楼同时只跑一个请求:翻页与重试都会进这里,并发会让两页按不可控的顺序回来。
     * 追加页按 id 去重,平台在边界页重复下发同一条时不至于出现两条一样的回复。
     */
    private fun loadReplies(comment: Comment, append: Boolean) {
        val current = mutableState.value
        if (current.repliesLoading.contains(comment.id) || current.repliesLoadingMore.contains(comment.id)) return
        if (append && !current.repliesMore.contains(comment.id)) return
        val page = if (append) nextReplyPage[comment.id] ?: SECOND_PAGE else FIRST_PAGE
        replyJobs[comment.id]?.cancel()
        replyJobs[comment.id] = viewModelScope.launch {
            mutableState.update {
                it.copy(
                    repliesLoading = if (append) it.repliesLoading else it.repliesLoading + comment.id,
                    repliesLoadingMore = if (append) it.repliesLoadingMore + comment.id else it.repliesLoadingMore,
                    repliesError = it.repliesError - comment.id,
                )
            }
            when (val result = commentRepository.replies(current.aid, comment.id, page, current.upMid)) {
                is HsyResult.Success -> {
                    nextReplyPage[comment.id] = page + 1
                    mutableState.update { state ->
                        val base = if (append) state.replies[comment.id].orEmpty() else emptyList()
                        val merged = (base + result.value.comments).distinctBy { it.id }
                        state.copy(
                            replies = state.replies + (comment.id to merged),
                            repliesMore = if (result.value.moreAvailable) {
                                state.repliesMore + comment.id
                            } else {
                                state.repliesMore - comment.id
                            },
                            repliesLoading = state.repliesLoading - comment.id,
                            repliesLoadingMore = state.repliesLoadingMore - comment.id,
                        )
                    }
                }

                is HsyResult.Failure -> mutableState.update {
                    it.copy(
                        repliesLoading = it.repliesLoading - comment.id,
                        repliesLoadingMore = it.repliesLoadingMore - comment.id,
                        repliesError = it.repliesError + (comment.id to result.error.userMessage()),
                    )
                }
            }
        }
    }

    /**
     * 点赞 / 取消赞。
     *
     * 三道闸在发请求之前:只读模式只回一句提醒、游客只回扫码引导、同一条在飞时不叠第二发。
     * 成功后就地 ±1 并记进 [CommentUiState.likedIds],**不重取列表** —— 重取会把这屏展开的楼中楼冲掉。
     */
    private fun toggleLike(comment: Comment) {
        val current = mutableState.value
        val blocked = when {
            current.likeReadOnly -> LIKE_READ_ONLY_HINT
            !current.loggedIn -> "点赞需要登录 · 去登录"
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
