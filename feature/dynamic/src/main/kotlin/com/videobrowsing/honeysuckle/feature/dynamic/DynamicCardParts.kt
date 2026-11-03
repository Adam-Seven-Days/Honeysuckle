package com.videobrowsing.honeysuckle.feature.dynamic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.videobrowsing.honeysuckle.core.common.HsyClock
import com.videobrowsing.honeysuckle.core.common.formatCount
import com.videobrowsing.honeysuckle.core.common.formatRelativeTime
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyVideoCard
import com.videobrowsing.honeysuckle.core.designsystem.icon.HsyIcons
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.Icon
import com.videobrowsing.honeysuckle.core.designsystem.ui.IconButton
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.model.DynamicItem
import com.videobrowsing.honeysuckle.core.model.DynamicKind
import com.videobrowsing.honeysuckle.core.model.DynamicStat

private val AvatarSize = 36.dp
private val CardShape = RoundedCornerShape(10.dp)
private val ThumbShape = RoundedCornerShape(6.dp)
private const val MAX_THUMBS = 3
private const val SINGLE_IMAGE_ASPECT = 1.7778f
private const val SCRIM_ALPHA = 0.6f
private const val SUMMARY_LINES = 2

/** 全屏预览:整组图片 + 从第几张开始 */
internal data class ImagePreview(val urls: List<String>, val index: Int)

/** 头像 + 作者 + 时间;名字可点进空间,头像与名字是同一个入口的两块皮 */
@Composable
internal fun DynamicHeader(item: DynamicItem, onOpenSpace: (Long) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(AvatarSize)
                .clip(CircleShape)
                .background(Hsy.colors.surfaceVariant)
                .clickable(enabled = item.authorMid > 0L, role = Role.Button) { onOpenSpace(item.authorMid) },
        ) {
            if (item.authorFaceUrl.isNotBlank()) {
                AsyncImage(
                    model = item.authorFaceUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
            Text(
                text = item.authorName.ifBlank { "未知 UP" },
                style = Hsy.typography.titleSmall,
                color = Hsy.colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(enabled = item.authorMid > 0L, role = Role.Button) {
                    onOpenSpace(item.authorMid)
                },
            )
            Text(
                text = item.publishedText,
                style = Hsy.typography.bodySmall,
                color = Hsy.colors.onSurfaceVariant,
            )
        }
    }
}

/**
 * 主内容分区。
 *
 * 分流的判据只有 [DynamicItem.kind]:不看"哪块字段恰好非空" ——
 * 一条转发动态的视频号当然为空,按字段猜会把它错画成纯文字。
 */
@Composable
internal fun DynamicBody(item: DynamicItem, actions: OpenActions) {
    if (item.kind == DynamicKind.OPUS && item.articleTitle.isNotBlank()) {
        Text(
            text = item.articleTitle,
            style = Hsy.typography.titleMedium,
            color = Hsy.colors.onSurface,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    when (item.kind) {
        DynamicKind.VIDEO -> if (item.videoBvid.isNotBlank()) {
            HsyVideoCard(
                title = item.videoTitle,
                coverUrl = item.videoCoverUrl,
                durationLabel = item.videoDurationText,
                upLine = item.authorName,
                statLine = "动态视频",
                onClick = { actions.video(item.videoBvid) },
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        DynamicKind.ARTICLE -> if (item.articleId > 0L) {
            ArticleCard(item = item, onClick = { actions.article(item.articleId) })
        }

        DynamicKind.DRAW, DynamicKind.OPUS -> if (item.imageUrls.isNotEmpty()) {
            DynamicImages(urls = item.imageUrls, onOpen = actions.images)
        }

        DynamicKind.FORWARD -> ForwardCard(origin = item.origin, actions = actions)

        DynamicKind.TEXT -> Unit
    }
}

/** 转发的原文:缩进一格、换一层底色,读起来像"引了一段别人的话" */
@Composable
internal fun ForwardCard(origin: DynamicItem?, actions: OpenActions) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(CardShape)
            .background(Hsy.colors.surfaceVariant)
            .padding(10.dp),
    ) {
        if (origin == null) {
            Text(
                text = "原文已不可见",
                style = Hsy.typography.bodySmall,
                color = Hsy.colors.onSurfaceVariant,
            )
            return@Column
        }
        Text(
            text = listOfNotNull(
                origin.authorName.ifBlank { "未知 UP" },
                origin.publishedText.takeIf { it.isNotBlank() },
            ).joinToString(" · "),
            style = Hsy.typography.labelLarge,
            color = Hsy.colors.onSurfaceVariant,
        )
        if (origin.text.isNotBlank()) {
            Text(
                text = origin.text,
                style = Hsy.typography.bodyMedium,
                color = Hsy.colors.onSurface,
                maxLines = SUMMARY_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        DynamicBody(item = origin, actions = actions)
    }
}

@Composable
internal fun ArticleCard(item: DynamicItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(CardShape)
            .background(Hsy.colors.surfaceVariant)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(10.dp),
    ) {
        if (item.articleCoverUrl.isNotBlank()) {
            AsyncImage(
                model = item.articleCoverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = ArticleCoverWidth, height = ArticleCoverHeight).clip(ThumbShape),
            )
        }
        Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
            Text(
                text = item.articleTitle.ifBlank { "专栏" },
                style = Hsy.typography.titleSmall,
                color = Hsy.colors.onSurface,
                maxLines = SUMMARY_LINES,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.articleSummary.isNotBlank()) {
                Text(
                    text = item.articleSummary,
                    style = Hsy.typography.bodySmall,
                    color = Hsy.colors.onSurfaceVariant,
                    maxLines = SUMMARY_LINES,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/**
 * 图片区。
 *
 * 单图按 16:9 摆一张(信息流里最不打断阅读的比例),多图排一排等宽方块 ——
 * 不论几张,点任意一张都从那一张开始全屏翻。超过三张时最后一格压一层 "+N"。
 */
@Composable
internal fun DynamicImages(urls: List<String>, onOpen: (List<String>, Int) -> Unit) {
    if (urls.size == 1) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .clip(CardShape)
                .background(Hsy.colors.surfaceVariant)
                .clickable(role = Role.Button) { onOpen(urls, 0) },
        ) {
            AsyncImage(
                model = urls.first(),
                contentDescription = "动态配图",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(SINGLE_IMAGE_ASPECT),
            )
        }
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        urls.take(MAX_THUMBS).forEachIndexed { index, url ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clip(ThumbShape)
                    .background(Hsy.colors.surfaceVariant)
                    .clickable(role = Role.Button) { onOpen(urls, index) },
            ) {
                AsyncImage(
                    model = url,
                    contentDescription = "动态配图 ${index + 1}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (index == MAX_THUMBS - 1 && urls.size > MAX_THUMBS) {
                    Text(
                        text = "+${urls.size - MAX_THUMBS}",
                        style = Hsy.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = SCRIM_ALPHA), ThumbShape)
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

/** 三个读数:只读不写。这一轮没有点赞/转发的写口,点了不动的按钮比不摆更糟 */
@Composable
internal fun DynamicStats(stat: DynamicStat) {
    if (stat.isEmpty) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatCell(HsyIcons.thumbUp, stat.like)
        StatCell(HsyIcons.comment, stat.comment)
        StatCell(HsyIcons.share, stat.forward)
    }
}

@Composable
private fun StatCell(icon: androidx.compose.ui.graphics.vector.ImageVector, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Hsy.colors.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = formatCount(count.toLong()),
            style = Hsy.typography.bodySmall,
            color = Hsy.colors.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/** 全屏图片预览:黑底、左右翻、右上角关闭;页码在底部,给"还有几张"一个交代 */
@Composable
internal fun ImagePreviewDialog(preview: ImagePreview, onDismiss: () -> Unit) {
    if (preview.urls.isEmpty()) return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val start = preview.index.coerceIn(0, preview.urls.lastIndex)
        val pagerState = rememberPagerState(initialPage = start) { preview.urls.size }
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                AsyncImage(
                    model = preview.urls[page],
                    contentDescription = "第 ${page + 1} 张图",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            ) {
                Icon(HsyIcons.close, contentDescription = "关闭预览", tint = Color.White)
            }
            Text(
                text = "${pagerState.currentPage + 1}/${preview.urls.size}",
                style = Hsy.typography.bodySmall,
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp),
            )
        }
    }
}

private val ArticleCoverWidth = 96.dp
private val ArticleCoverHeight = 64.dp
