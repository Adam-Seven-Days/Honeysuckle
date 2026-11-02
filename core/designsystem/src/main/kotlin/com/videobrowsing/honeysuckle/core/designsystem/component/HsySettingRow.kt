package com.videobrowsing.honeysuckle.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.Icon
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text

private val RowHeight = 60.dp
private val IconSlot = 40.dp
private val RowPadding = 16.dp
private val ProgressHeight = 3.dp
private val ProgressTopGap = 4.dp

/**
 * 通用条目行(设置页与"我的"页的每一行都是它)。
 *
 * 四条口径值得说明:
 *  - [onClick] 为 null 时**整行不可点**:像"弹幕总开关"那种交互在尾部控件上的行,
 *    如果标题也能点,用户就会在"点标题切换"和"点开关切换"之间猜;
 *  - [trailing] 是一个槽而不是一个布尔:开关、箭头、计数、档位名都要放在同一个位置上,
 *    组件不该替页面猜它长什么样;
 *  - [progress] 只给"进行到哪儿"这一类行(我的页的接着看):一条细线,`null` 就不画,
 *    0 也照样画出空槽 —— "刚开始"与"没有这一栏"在屏上必须分得开;
 *  - 没有 `enabled` 参数:灰化条目通常意味着"这条现在没用",而本仓的口径是把没用的入口干脆不摆出来。
 */
@Composable
fun HsySettingRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    supporting: String? = null,
    progress: Float? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val rowModifier = if (onClick == null) {
        modifier
    } else {
        modifier.clickable(role = Role.Button, onClick = onClick)
    }

    Row(
        modifier = rowModifier
            .defaultMinSize(minHeight = RowHeight)
            .padding(horizontal = RowPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.size(IconSlot), contentAlignment = Alignment.Center) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = Hsy.colors.accent,
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = Hsy.typography.bodyLarge,
                color = Hsy.colors.onSurface,
            )
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = Hsy.typography.bodyMedium,
                    color = Hsy.colors.onSurfaceVariant,
                )
            }
            if (progress != null) {
                RowProgressBar(progress)
            }
        }

        if (trailing != null) {
            trailing()
        }
    }
}

/** 行里那条细进度线:空槽 + 前段实色,`progress` 越界一律夹回 0~1 */
@Composable
private fun RowProgressBar(progress: Float) {
    Box(
        modifier = Modifier
            .padding(top = ProgressTopGap)
            .fillMaxWidth()
            .height(ProgressHeight)
            .clip(RoundedCornerShape(percent = 50))
            .background(Hsy.colors.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(Hsy.colors.accent),
        )
    }
}
