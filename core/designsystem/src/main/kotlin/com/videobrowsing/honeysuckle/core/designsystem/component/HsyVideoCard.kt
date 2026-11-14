package com.videobrowsing.honeysuckle.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import coil3.compose.AsyncImage
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text

private val CoverWidth = 128.dp
private val CoverHeight = 72.dp
private val CoverShape = RoundedCornerShape(10.dp)
private val BadgeShape = RoundedCornerShape(4.dp)
private val RowPaddingHorizontal = 12.dp
private val RowPaddingVertical = 8.dp
private val CoverToTextGap = 12.dp
private val TextGap = 3.dp
private val BadgePaddingHorizontal = 5.dp
private val BadgePaddingVertical = 1.dp
private val BadgeInset = 4.dp
private val SkeletonLineHeight = 12.dp

/** 骨架那三条杠的相对长度:满行 / 大半行 / 短行,一眼读得出"这是两行标题加一行读数" */
private const val FULL_LINE = 1f
private const val UPPER_LINE = 0.72f
private const val SHORT_LINE = 0.45f

/** 时长角底的那层压暗:0.7 是"白字压得住、又还没把画面糊成一团"的那一档,再高就成不透明黑了 */
private const val SCRIM_ALPHA = 0.7f

/**
 * 信息流的一行:封面 + 标题 + UP + 两个读数。
 *
 * 参数收字符串而不是收 `FeedItem`,是那条"`designsystem` 不依赖业务"的直接后果:
 * 同一张卡片将来要同时服务首页信息流与搜索结果,而两边递过来的领域模型是两个类型 ——
 * 卡片认识其中之一,就得为另一个再加一条构造。刻度("×.x 万 / 3 小时前")由调用方折好递进来。
 *
 * [onClick] 可空是有意的:目的点还没出生的那一版里,行要渲染成"看得到、点不动",
 * 而不是摆一个吞掉一次 tap 的可点区域(四屏上那两处 `onOpenVideo = null` 是同一条口径)。
 * 封面 `contentDescription` 留 null 也是同一条判断的镜像:这一行的语义全由标题承载,
 * 给装饰图念一句"视频封面"只会让读屏软件每行多说一句废话。
 */
@Composable
fun HsyVideoCard(
    title: String,
    coverUrl: String,
    durationLabel: String,
    upLine: String,
    statLine: String,
    modifier: Modifier = Modifier,
    badgeLabel: String = "",
    onClick: (() -> Unit)? = null,
) {
    val rowModifier = if (onClick == null) {
        modifier.fillMaxWidth()
    } else {
        modifier.fillMaxWidth().clickable(onClickLabel = "打开视频", role = Role.Button) { onClick() }
    }

    Row(
        modifier = rowModifier.padding(horizontal = RowPaddingHorizontal, vertical = RowPaddingVertical),
        horizontalArrangement = Arrangement.spacedBy(CoverToTextGap),
    ) {
        Cover(coverUrl = coverUrl, durationLabel = durationLabel, badgeLabel = badgeLabel)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(TextGap),
        ) {
            Text(
                text = title,
                style = Hsy.typography.bodyLarge,
                color = Hsy.colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = upLine,
                style = Hsy.typography.bodySmall,
                color = Hsy.colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = statLine,
                style = Hsy.typography.bodySmall,
                color = Hsy.colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 取数还没回来的那一屏:同一份占位摆五行,让列表的高度在数据到达前后不跳(内容块上下移动比灰块更难读) */
@Composable
fun HsyVideoCardSkeleton(modifier: Modifier = Modifier) {
    val placeholder = Hsy.colors.surfaceVariant
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = RowPaddingHorizontal, vertical = RowPaddingVertical),
        horizontalArrangement = Arrangement.spacedBy(CoverToTextGap),
    ) {
        Box(
            modifier = Modifier
                .size(width = CoverWidth, height = CoverHeight)
                .clip(CoverShape)
                .background(placeholder),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(TextGap),
        ) {
            // 两行标题 + 一行读数,和真卡片同数量;宽度给成不等长,免得屏上出现三条一模一样的杠
            Box(modifier = Modifier.skeletonLine(FULL_LINE, placeholder))
            Box(modifier = Modifier.skeletonLine(UPPER_LINE, placeholder))
            Box(modifier = Modifier.skeletonLine(SHORT_LINE, placeholder))
        }
    }
}

private fun Modifier.skeletonLine(fraction: Float, color: Color): Modifier = this
    .fillMaxWidth(fraction)
    .height(SkeletonLineHeight)
    .clip(BadgeShape)
    .background(color)

@Composable
private fun Cover(coverUrl: String, durationLabel: String, badgeLabel: String) {
    Box(
        modifier = Modifier
            .size(width = CoverWidth, height = CoverHeight)
            .clip(CoverShape)
            .background(Hsy.colors.surfaceVariant),
    ) {
        if (coverUrl.isNotBlank()) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (badgeLabel.isNotBlank()) {
            Text(
                text = badgeLabel,
                style = Hsy.typography.labelSmall,
                color = Hsy.colors.onPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(BadgeInset)
                    .background(Hsy.colors.primary, BadgeShape)
                    .padding(horizontal = BadgePaddingHorizontal, vertical = BadgePaddingVertical),
            )
        }
        if (durationLabel.isNotBlank()) {
            Text(
                text = durationLabel,
                style = Hsy.typography.labelSmall,
                color = Hsy.colors.inverseOnSurface,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(BadgeInset)
                    .background(Hsy.colors.inverseSurface.copy(alpha = SCRIM_ALPHA), BadgeShape)
                    .padding(horizontal = BadgePaddingHorizontal, vertical = BadgePaddingVertical),
            )
        }
    }
}
