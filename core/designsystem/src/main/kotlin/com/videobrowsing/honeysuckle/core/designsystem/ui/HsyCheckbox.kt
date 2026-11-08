package com.videobrowsing.honeysuckle.core.designsystem.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.videobrowsing.honeysuckle.core.designsystem.icon.HsyIcons
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy

private val BoxSize = 24.dp
private val BoxShape = RoundedCornerShape(6.dp)

/**
 * 勾选框:选中转强调色底 + 对勾,未选中留描边空框。
 *
 * [onCheckedChange] 为 null 时**只画不点**:批量管理里整行已经是勾选的点击区,
 * 框自己再吃一次点击就会"点一下 toggles 两回"。这时它只是行选中态的一面镜子。
 */
@Composable
fun Checkbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactive = if (onCheckedChange != null) {
        Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Checkbox,
            onValueChange = onCheckedChange,
        )
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .size(BoxSize)
            .clip(BoxShape)
            .background(if (checked) Hsy.colors.accent else Hsy.colors.surface)
            .border(1.5.dp, if (checked) Hsy.colors.accent else Hsy.colors.outline, BoxShape)
            .then(interactive),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                imageVector = HsyIcons.check,
                contentDescription = null,
                tint = Hsy.colors.onAccent,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
