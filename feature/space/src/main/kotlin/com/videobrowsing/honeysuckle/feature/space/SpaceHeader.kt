package com.videobrowsing.honeysuckle.feature.space

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.videobrowsing.honeysuckle.core.common.formatCount
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.Button
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.designsystem.ui.minimumInteractiveComponentSize
import com.videobrowsing.honeysuckle.core.model.UserSpace

private val AvatarSize = 72.dp
private val HeaderPadding = 16.dp
private val FollowCorner = RoundedCornerShape(20.dp)

/**
 * 空间页头部:名片(头像/昵称/等级/签名)+ 读数三栏 + 关注按钮。
 *
 * 读数与按钮各是一段独立的可组合面:读数拿不到就整行不渲染,按钮的形态随关注态换 ——
 * 空间页的信息密度不在标题栏,全压在这一个头部里。
 */
@Composable
internal fun SpaceHeader(space: UserSpace, state: SpaceUiState, onFollow: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = HeaderPadding, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SpaceAvatar(space)
            Column(
                modifier = Modifier.padding(start = 14.dp).weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = space.name,
                    style = Hsy.typography.titleLarge,
                    color = Hsy.colors.onSurface,
                )
                if (space.level > 0) {
                    LevelChip(space.level)
                }
            }
        }
        if (space.sign.isNotBlank()) {
            Text(
                text = space.sign,
                style = Hsy.typography.bodyMedium,
                color = Hsy.colors.onSurfaceVariant,
            )
        }
        StatsRow(space)
        FollowButton(state = state, onFollow = onFollow)
    }
}

/** 头像只有"有图 / 没图"两态:没图留一块底色,不让行高跟着加载状态跳 */
@Composable
private fun SpaceAvatar(space: UserSpace) {
    Box(
        modifier = Modifier
            .size(AvatarSize)
            .clip(CircleShape)
            .background(Hsy.colors.surfaceVariant),
    ) {
        if (space.avatarUrl.isNotBlank()) {
            AsyncImage(
                model = space.avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(AvatarSize),
            )
        }
    }
}

/** 等级小徽标:浅背底一块,不与昵称抢字重 */
@Composable
private fun LevelChip(level: Int) {
    Text(
        text = "LV$level",
        style = Hsy.typography.labelMedium,
        color = Hsy.colors.accent,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Hsy.colors.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/**
 * 读数三栏:粉丝 / 关注 / 获赞。
 *
 * 只摆拿得到的 —— 读数是可空的,游客被风控抹数、端点挂了都落成 `null`。
 * 一个格子都不齐就整行不渲染,不摆一排 0 让人误会"这 UP 没人看"。
 */
@Composable
private fun StatsRow(space: UserSpace) {
    val cells = listOfNotNull(
        space.fans?.let { StatCellData(formatCount(it), "粉丝") },
        space.following?.let { StatCellData(formatCount(it), "关注") },
        space.likes?.takeIf { it > 0 }?.let { StatCellData(formatCount(it), "获赞") },
    )
    if (cells.isEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        cells.forEachIndexed { index, cell ->
            if (index > 0) {
                VerticalDivider()
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = cell.count,
                    style = Hsy.typography.titleMedium,
                    color = Hsy.colors.onSurface,
                )
                Text(
                    text = cell.label,
                    style = Hsy.typography.labelSmall,
                    color = Hsy.colors.onSurfaceVariant,
                )
            }
        }
    }
}

private data class StatCellData(val count: String, val label: String)

/** 读数之间的细分隔线:给三栏一点各自的宽度,不靠间距硬分 */
@Composable
private fun VerticalDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(24.dp)
            .background(Hsy.colors.surfaceVariant),
    )
}

/**
 * 关注按钮:未关注=实心主按钮,已关注=描边次态。
 *
 * 游客摆的是"关注 · 需登录",点了走登录入口 —— 与详情页 UP 卡同一条口径。
 */
@Composable
private fun FollowButton(state: SpaceUiState, onFollow: () -> Unit) {
    val following = state.following == true
    if (following) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .clip(FollowCorner)
                .border(1.dp, Hsy.colors.onSurfaceVariant.copy(alpha = 0.4f), FollowCorner)
                .clickable(enabled = !state.followBusy, role = Role.Button, onClick = onFollow)
                .padding(horizontal = 18.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "已关注",
                style = Hsy.typography.labelLarge,
                color = Hsy.colors.onSurfaceVariant,
            )
        }
        return
    }
    Button(onClick = onFollow, enabled = !state.followBusy, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = if (state.loggedIn) "关注" else "关注 · 需登录",
            style = Hsy.typography.labelLarge,
        )
    }
}
