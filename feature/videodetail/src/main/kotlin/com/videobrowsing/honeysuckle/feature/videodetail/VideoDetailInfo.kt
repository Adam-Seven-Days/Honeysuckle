package com.videobrowsing.honeysuckle.feature.videodetail

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.videobrowsing.honeysuckle.core.common.formatClock
import com.videobrowsing.honeysuckle.core.common.formatCount
import com.videobrowsing.honeysuckle.core.designsystem.component.HsySectionCard
import com.videobrowsing.honeysuckle.core.designsystem.icon.HsyIcons
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.DropdownMenu
import com.videobrowsing.honeysuckle.core.designsystem.ui.DropdownMenuItem
import com.videobrowsing.honeysuckle.core.designsystem.ui.FilterChip
import com.videobrowsing.honeysuckle.core.designsystem.ui.Icon
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.designsystem.ui.TextButton
import com.videobrowsing.honeysuckle.core.designsystem.ui.minimumInteractiveComponentSize
import com.videobrowsing.honeysuckle.core.model.VideoDetail

/**
 * 标题上面那行续播注记,外加取流失败时那一句。
 *
 * 弹幕本机取到多少条、覆盖几片不再往屏上说 —— 那是取数侧的事,观众要的是画面上的字在不在。
 * "从头开始"也不算新闻:只有真的从历史点接着放、或者从后台沿过来,才值得提一句。
 * 两句都没话说时整块不占高度,免得留一条空槽。
 */
@Composable
internal fun PlaybackNotes(state: VideoDetailUiState, continuing: Boolean) {
    val resume = state.resumePositionSec
    // 引擎里坐着的就是这一条流时,画面/声音从后台沿过来,没有"从历史点重开"这回事
    val resumeNote = when {
        continuing -> "接着后台播放,进度连续"
        resume > 0 -> "上次看到 ${formatClock(resume.toLong())},已从那里继续"
        else -> null
    }
    val error = state.playbackError
    if (resumeNote == null && error == null) return
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        resumeNote?.let {
            Text(
                text = it,
                style = Hsy.typography.bodySmall,
                color = Hsy.colors.onSurfaceVariant,
            )
        }
        error?.let {
            Text(
                text = "取流失败:$it",
                style = Hsy.typography.bodySmall,
                color = Hsy.colors.error,
            )
        }
    }
}

@Composable
internal fun VideoMeta(state: VideoDetailUiState, detail: VideoDetail, intents: DetailIntents) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = detail.brief.title,
            style = Hsy.typography.titleLarge,
            color = Hsy.colors.onSurface,
        )
        Text(
            text = statsLineOf(detail.stat),
            style = Hsy.typography.bodySmall,
            color = Hsy.colors.onSurfaceVariant,
        )
        Text(
            text = metaLineOf(detail),
            style = Hsy.typography.bodySmall,
            color = Hsy.colors.onSurfaceVariant,
        )
        // 简介紧跟在发布日期后面:它是这条稿自己的话,与标题同属一段,不该到页面末尾才出现
        Description(detail.description)
        MetaActionRow(state, detail, intents)
        OfflineBar(state, intents)
        state.interactionMessage?.let { message ->
            Text(
                text = message,
                style = Hsy.typography.bodySmall,
                color = Hsy.colors.accent,
                modifier = Modifier.clickable { intents.onAction(VideoDetailAction.DismissMessage) },
            )
        }
        if (state.favoritePickerVisible) FavoriteFolderDialog(state = state, intents = intents)
    }
}

/** 互动区那一排:点赞 / 投币 / 收藏 / 分享,每枚下面挂自己的读数;游客态多一个扫码入口 */
@Composable
private fun MetaActionRow(state: VideoDetailUiState, detail: VideoDetail, intents: DetailIntents) {
    val context = LocalContext.current
    val idle = !state.interactionBusy
    val favorited = state.interaction.favorited == true
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MetaAction(
            icon = HsyIcons.thumbUp,
            label = if (state.interaction.liked) "已点赞" else "点赞",
            count = formatCount(detail.stat.like),
            active = state.interaction.liked,
            enabled = idle,
            onClick = { intents.onAction(VideoDetailAction.ToggleLike) },
        )
        MetaAction(
            icon = HsyIcons.coin,
            label = "投币",
            count = formatCount(detail.stat.coin),
            enabled = idle,
            onClick = { intents.onAction(VideoDetailAction.AddCoin) },
        )
        MetaAction(
            icon = HsyIcons.star,
            label = favoriteLabel(state),
            count = formatCount(detail.stat.favorite),
            active = favorited,
            enabled = idle,
            onClick = {
                // 游客先登录:收藏夹要读账号,面板开了也只会是空的
                if (state.loggedIn) {
                    intents.onAction(VideoDetailAction.OpenFavoritePicker)
                } else {
                    intents.onOpenLogin()
                }
            },
        )
        ShareAction(context = context, detail = detail, enabled = idle)
        if (!state.loggedIn) {
            TextButton(onClick = intents.onOpenLogin) { Text("去登录") }
        }
    }
}

/**
 * 互动区的一枚动作:图标压两行小字,第一行是动作名,第二行是这一格的读数。
 *
 * 图标本身不带语义,读屏念的是下面那两行文字 —— 同一件事不重复念两遍。
 */
@Composable
private fun MetaAction(
    icon: ImageVector,
    label: String,
    count: String,
    onClick: () -> Unit,
    active: Boolean = false,
    enabled: Boolean = true,
) {
    val color = when {
        active -> Hsy.colors.accent
        !enabled -> Hsy.colors.onSurfaceVariant.copy(alpha = DISABLED_ACTION_ALPHA)
        else -> Hsy.colors.onSurfaceVariant
    }
    Column(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(Hsy.shapes.medium))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(22.dp),
        )
        Text(text = label, style = Hsy.typography.labelSmall, color = color)
        Text(text = count, style = Hsy.typography.labelSmall, color = color)
    }
}

/** 分享:下面那行是平台的分享计数;菜单里两条路 —— 复制链接(离线可用)与系统分享面板 */
@Composable
private fun ShareAction(context: Context, detail: VideoDetail, enabled: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        MetaAction(
            icon = HsyIcons.share,
            label = "分享",
            count = formatCount(detail.stat.share),
            enabled = enabled,
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("复制链接") },
                onClick = {
                    expanded = false
                    copyVideoLink(context, detail.brief.bvid)
                },
            )
            DropdownMenuItem(
                text = { Text("系统分享") },
                onClick = {
                    expanded = false
                    shareVideo(context, detail)
                },
            )
        }
    }
}

/** 不可点的动作压到这一档:还读得出是什么,但不会以为是可点的 */
private const val DISABLED_ACTION_ALPHA = 0.45f

@Composable
internal fun UpCard(
    detail: VideoDetail,
    state: VideoDetailUiState,
    onToggleFollow: () -> Unit,
    onOpenSpace: () -> Unit,
    onOpenLogin: () -> Unit,
) {
    val loggedIn = state.loggedIn
    val busy = state.followBusy
    val following = state.following
    val canOpenSpace = detail.ownerMid > 0L
    HsySectionCard(title = "UP 主") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = canOpenSpace, onClickLabel = "打开 UP 主空间") { onOpenSpace() }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UpAvatar(detail)
            Column(
                modifier = Modifier.padding(start = 12.dp).weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = detail.brief.upName.ifBlank { "未知 UP 主" },
                    style = Hsy.typography.bodyLarge,
                    color = Hsy.colors.onSurface,
                )
                if (detail.ownerSign.isNotBlank()) {
                    Text(
                        text = detail.ownerSign,
                        style = Hsy.typography.bodySmall,
                        color = Hsy.colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            TextButton(
                onClick = if (loggedIn) onToggleFollow else onOpenLogin,
                enabled = !busy,
            ) {
                Text(
                    text = when {
                        !loggedIn -> "关注 · 需登录"
                        following == true -> "已关注"
                        else -> "关注"
                    },
                )
            }
        }
    }
}

/** UP 头像:有图才画,没图留一块底色,不让行高随加载状态跳 */
@Composable
private fun UpAvatar(detail: VideoDetail) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Hsy.colors.surfaceVariant),
    ) {
        if (detail.ownerAvatarUrl.isNotBlank()) {
            AsyncImage(
                model = detail.ownerAvatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** 简介那一块,摆在 [VideoMeta] 里;边距由那一段给,这里只管字与展开 */
@Composable
private fun Description(description: String) {
    if (description.isBlank()) return
    var expanded by remember(description) { mutableStateOf(false) }
    Column {
        Text(text = "视频简介", style = Hsy.typography.titleSmall, color = Hsy.colors.onSurface)
        Text(
            text = description,
            style = Hsy.typography.bodyMedium,
            color = Hsy.colors.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else DESCRIPTION_COLLAPSED_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (description.length > DESCRIPTION_PREVIEW_CHARS) {
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起" else "展开") }
        }
    }
}

@Composable
internal fun PartsRow(state: VideoDetailUiState, onSelect: (Int) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(state.parts) { index, part ->
            FilterChip(
                selected = index == state.partIndex,
                onClick = { onSelect(index) },
                label = { Text("P${part.index} ${part.title}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}
