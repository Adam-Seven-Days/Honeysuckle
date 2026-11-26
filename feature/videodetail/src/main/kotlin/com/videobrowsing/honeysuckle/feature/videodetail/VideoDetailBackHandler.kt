package com.videobrowsing.honeysuckle.feature.videodetail

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

/**
 * 返回键优先级:合集与分 P 清单先各自收起,其次是全屏,最后才离开详情页。
 */
@Suppress("LongParameterList")
@Composable
internal fun DetailBackHandler(
    partsOpen: Boolean,
    seasonOpen: Boolean,
    fullscreen: Boolean,
    onCloseParts: () -> Unit,
    onCloseSeason: () -> Unit,
    onExitFullscreen: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler {
        when {
            partsOpen -> onCloseParts()
            seasonOpen -> onCloseSeason()
            fullscreen -> onExitFullscreen()
            else -> onBack()
        }
    }
}
