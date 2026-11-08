package com.videobrowsing.honeysuckle.feature.videodetail

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

/**
 * 返回键的三级优先级:分 P 清单在最上面,先收它;其次是全屏;最后才离开详情页。
 *
 * 与导航层的"楼中楼"是同一套读法:压在上面的那一页只退自己,不顺手把详情页也带走。
 */
@Composable
internal fun DetailBackHandler(
    partsOpen: Boolean,
    fullscreen: Boolean,
    onCloseParts: () -> Unit,
    onExitFullscreen: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler {
        when {
            partsOpen -> onCloseParts()
            fullscreen -> onExitFullscreen()
            else -> onBack()
        }
    }
}
