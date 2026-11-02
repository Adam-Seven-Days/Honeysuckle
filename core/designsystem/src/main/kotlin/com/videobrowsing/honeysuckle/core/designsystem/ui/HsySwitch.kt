package com.videobrowsing.honeysuckle.core.designsystem.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy

/** 开关:46×28 的轨道 + 22dp 圆点,开时整条转强调色。 */
@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val track by animateColorAsState(
        targetValue = if (checked) Hsy.colors.accent else Hsy.colors.surfaceVariant,
        label = "HsySwitchTrack",
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 21.dp else 3.dp,
        label = "HsySwitchThumb",
    )
    Box(
        modifier = modifier
            .size(width = 46.dp, height = 28.dp)
            .clip(CircleShape)
            .background(track)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset, y = 3.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(if (checked) Hsy.colors.onAccent else Hsy.colors.onSurfaceVariant),
        )
    }
}
