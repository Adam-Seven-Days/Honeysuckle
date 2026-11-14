package com.videobrowsing.honeysuckle.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.videobrowsing.honeysuckle.core.common.HsyClock
import com.videobrowsing.honeysuckle.core.common.formatClock
import com.videobrowsing.honeysuckle.core.common.formatCount
import com.videobrowsing.honeysuckle.core.common.formatRelativeTime
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyEmptyState
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyErrorState
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyTopBar
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyVideoCard
import com.videobrowsing.honeysuckle.core.designsystem.icon.HsyIcons
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.AlertDialog
import com.videobrowsing.honeysuckle.core.designsystem.ui.AssistChip
import com.videobrowsing.honeysuckle.core.designsystem.ui.CircularProgressIndicator
import com.videobrowsing.honeysuckle.core.designsystem.ui.FilterChip
import com.videobrowsing.honeysuckle.core.designsystem.ui.Icon
import com.videobrowsing.honeysuckle.core.designsystem.ui.OutlinedTextField
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.designsystem.ui.TextButton
import com.videobrowsing.honeysuckle.core.model.FeedItem

/**
 * 搜索页(联想 + 结果 + 历史词)。
 *
 * 一屏三态,判据只有一条 —— **输入框里的词还是不是"搜过的那个"**:
 *  - 还没搜过、输入框也空 → 摆本地搜过的词;
 *  - 正在打字 → 摆联想词(防抖在 ViewModel);
 *  - 结果属于当前这个词 → 摆结果,滚到底再要一页。
 *
 * 空结果与"还没搜"在屏上是两句话:前者念"没有找到「词」",后者什么都不念。
 * 拿 `results.isEmpty()` 当判据就会把一次真的空结果演成用户还没动手。
 */
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onBack: () -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenSpace: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var askBeforeClear by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Hsy.colors.surface),
    ) {
        HsyTopBar(title = "搜索", onBack = onBack)
        SearchBar(
            query = state.query,
            onQueryChange = { viewModel.onAction(SearchAction.QueryChanged(it)) },
            onSearch = { viewModel.onAction(SearchAction.Submit(state.query)) },
        )
        TabRow(tab = state.tab, onSelect = { viewModel.onAction(SearchAction.SelectTab(it)) })

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.showingResults -> when (state.tab) {
                    SearchTab.VIDEO -> Results(
                        state = state,
                        onOpenVideo = onOpenVideo,
                        onAction = viewModel::onAction,
                    )

                    SearchTab.USER -> UserResults(
                        state = state,
                        onOpenSpace = onOpenSpace,
                        onAction = viewModel::onAction,
                    )
                }

                state.showingHistory -> History(
                    state = state,
                    onAction = viewModel::onAction,
                    onAskClear = { askBeforeClear = true },
                )

                else -> Suggestions(state = state, onAction = viewModel::onAction)
            }
        }
    }

    if (askBeforeClear) {
        AlertDialog(
            onDismissRequest = { askBeforeClear = false },
            title = { Text("清空搜索记录?") },
            text = { Text("将删除本机保存的 ${state.history.size} 个搜索词") },
            confirmButton = {
                TextButton(
                    onClick = {
                        askBeforeClear = false
                        viewModel.onAction(SearchAction.ClearHistory)
                    },
                ) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { askBeforeClear = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit, onSearch: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("搜视频、UP 主、番剧") },
            singleLine = true,
            leadingIcon = { Icon(imageVector = HsyIcons.search, contentDescription = null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        )
        TextButton(onClick = onSearch, enabled = query.isNotBlank()) { Text("搜索") }
    }
}

@Composable
private fun TabRow(tab: SearchTab, onSelect: (SearchTab) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = tab == SearchTab.VIDEO,
            onClick = { onSelect(SearchTab.VIDEO) },
            label = { Text("视频") },
        )
        FilterChip(
            selected = tab == SearchTab.USER,
            onClick = { onSelect(SearchTab.USER) },
            label = { Text("用户") },
        )
    }
}

@Composable
private fun Results(state: SearchUiState, onOpenVideo: (String) -> Unit, onAction: (SearchAction) -> Unit) {
    when {
        state.loading -> Centered { CircularProgressIndicator() }

        state.errorMessage != null && state.results.isEmpty() -> HsyErrorState(
            message = state.errorMessage,
            onRetry = { onAction(SearchAction.Retry) },
            hint = "搜索失败,请重试",
        )

        state.results.isEmpty() -> HsyEmptyState(
            title = "没有找到「${state.submittedKeyword}」",
            hint = "换个关键词试试",
        )

        else -> ResultList(state = state, onOpenVideo = onOpenVideo, onAction = onAction)
    }
}

@Composable
private fun ResultList(state: SearchUiState, onOpenVideo: (String) -> Unit, onAction: (SearchAction) -> Unit) {
    val listState = rememberLazyListState()
    // 与首页同一条判据:最后一行真的露头才要下一页,不拿滚动百分比猜
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            info.totalItemsCount > 0 &&
                info.visibleItemsInfo.lastOrNull()?.index == info.totalItemsCount - 1
        }
    }
    LaunchedEffect(atBottom) { if (atBottom && state.moreAvailable) onAction(SearchAction.LoadMore) }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(state.results, key = { it.brief.bvid }) { item ->
            ResultCard(item = item, onClick = { onOpenVideo(item.brief.bvid) })
        }
        item(key = "footer") { ResultFooter(state = state, onAction = onAction) }
    }
}

@Composable
private fun ResultCard(item: FeedItem, onClick: () -> Unit) {
    HsyVideoCard(
        title = item.brief.title,
        coverUrl = item.brief.coverUrl,
        durationLabel = formatClock(item.brief.durationSec.toLong()),
        upLine = item.brief.upName,
        statLine = statLineOf(item),
        badgeLabel = item.brief.accessType.label,
        onClick = onClick,
    )
}

/** 与首页卡片同一行读数的口径(两边各自一份:这行的字段来自同一个模型,别为了省几行去抽接口) */
private fun statLineOf(item: FeedItem): String = listOfNotNull(
    if (item.viewCount > 0) "${formatCount(item.viewCount)} 播放" else null,
    if (item.danmakuCount > 0) "${formatCount(item.danmakuCount)} 弹幕" else null,
    item.publishEpochSec.takeIf { it > 0 }?.let { formatRelativeTime(it, HsyClock.nowEpochSec()) },
).joinToString(" · ")

@Composable
private fun ResultFooter(state: SearchUiState, onAction: (SearchAction) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            state.loadingMore -> CircularProgressIndicator()

            // 列表已有内容时失败的是"重试",不把整屏替换成错误页
            state.errorMessage != null -> {
                Text(
                    text = state.errorMessage,
                    style = Hsy.typography.bodySmall,
                    color = Hsy.colors.error,
                )
                TextButton(onClick = { onAction(SearchAction.Retry) }) { Text("重试") }
            }

            !state.moreAvailable -> Text(
                text = "已加载全部 ${state.results.size} 条",
                style = Hsy.typography.bodySmall,
                color = Hsy.colors.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun History(state: SearchUiState, onAction: (SearchAction) -> Unit, onAskClear: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "最近搜索",
                style = Hsy.typography.titleSmall,
                color = Hsy.colors.onSurface,
            )
            if (state.history.isNotEmpty()) {
                TextButton(onClick = onAskClear) { Text("清空") }
            }
        }
        if (state.history.isEmpty()) {
            HsyEmptyState(
                title = "暂无搜索记录",
                hint = "输入关键词开始搜索",
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                state.history.forEach { entry ->
                    AssistChip(
                        onClick = { onAction(SearchAction.Submit(entry.keyword)) },
                        label = {
                            Text(
                                text = entry.keyword,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = HsyIcons.close,
                                contentDescription = "删除搜索词 ${entry.keyword}",
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { onAction(SearchAction.RemoveHistory(entry.keyword)) },
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
