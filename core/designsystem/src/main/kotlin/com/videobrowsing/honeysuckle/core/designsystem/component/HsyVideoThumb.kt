package com.videobrowsing.honeysuckle.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy

private val ThumbWidth = 96.dp
private val ThumbHeight = 54.dp
private val ThumbShape = RoundedCornerShape(8.dp)

/**
 * 列表行左边的那枚 16:9 封面。
 *
 * 与 [HsyVideoCard] 里那张大封面同一套取图口径,只是尺寸收小到一行装得下 —— 稍后再看、收藏夹
 * 这类「已经知道是哪条、只差一眼认出」的清单,封面比一个通用图标多给一层信息。
 *
 * 取不到封面(或 URL 为空)时留一块 `surfaceVariant` 底板:行高不会在图片到达前后跳一下,也不闪白。
 * 尺寸固定,是为了同一份清单里每行的对齐不被封面比例带歪。
 */
@Composable
fun HsyVideoThumb(coverUrl: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = ThumbWidth, height = ThumbHeight)
            .clip(ThumbShape)
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
    }
}
