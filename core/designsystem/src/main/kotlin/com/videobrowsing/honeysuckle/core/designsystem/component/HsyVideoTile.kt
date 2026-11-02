package com.videobrowsing.honeysuckle.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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

private val TileShape = RoundedCornerShape(16.dp)
private val BadgeShape = RoundedCornerShape(5.dp)
private val SkeletonCorner = RoundedCornerShape(4.dp)
private const val HERO_ASPECT = 16f / 9f
private const val TILE_ASPECT = 4f / 3f
private const val SKELETON_TITLE_FRACTION = 0.94f
private const val SKELETON_META_FRACTION = 0.6f

/**
 * Bento 磁贴:封面在上、两行标题与一行读数在下。
 *
 * 与横向的 [HsyVideoCard] 是两种用途:横向卡适合"列表里快速扫过",
 * 磁贴把封面放大到接近方形的比例,首页因此读起来像一版拼贴,而不是一条长列表。
 * 封面的圆角只包住封面本身 —— 文字落在纸面上,不跟着图一起压暗。
 */
@Composable
fun HsyVideoTile(
    title: String,
    coverUrl: String,
    durationLabel: String,
    upLine: String,
    statLine: String,
    modifier: Modifier = Modifier,
    featured: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val clickable = if (onClick == null) {
        modifier
    } else {
        modifier.clickable(onClickLabel = "打开视频", role = Role.Button, onClick = onClick)
    }
    Column(modifier = clickable.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(if (featured) HERO_ASPECT else TILE_ASPECT)
                .clip(TileShape)
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
            if (durationLabel.isNotBlank()) {
                Text(
                    text = durationLabel,
                    style = Hsy.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.62f), BadgeShape)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
        Column(
            modifier = Modifier.padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                style = if (featured) Hsy.typography.titleMedium else Hsy.typography.bodyLarge,
                color = Hsy.colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOf(upLine, statLine).filter { it.isNotBlank() }.joinToString(" · "),
                style = Hsy.typography.bodySmall,
                color = Hsy.colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 磁贴骨架:与真磁贴同高,数据到达时网格不跳。 */
@Composable
fun HsyVideoTileSkeleton(modifier: Modifier = Modifier, featured: Boolean = false) {
    val placeholder = Hsy.colors.surfaceVariant
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(if (featured) HERO_ASPECT else TILE_ASPECT)
                .clip(TileShape)
                .background(placeholder),
        )
        Box(
            modifier = Modifier
                .padding(top = 10.dp)
                .fillMaxWidth(SKELETON_TITLE_FRACTION)
                .height(14.dp)
                .clip(SkeletonCorner)
                .background(placeholder),
        )
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth(SKELETON_META_FRACTION)
                .height(10.dp)
                .clip(SkeletonCorner)
                .background(placeholder),
        )
    }
}
