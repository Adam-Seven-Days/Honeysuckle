package com.videobrowsing.honeysuckle.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.Button
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text

/**
 * 空态。
 *
 * 分成"这里为什么是空的"与"下一步能做什么"两行,是因为空列表有两种成因:
 * 真的一条都没有,还是搜索词把结果筛没了 —— 屏上说同一句话,用户就分不清自己该不该去找内容看。
 */
@Composable
fun HsyEmptyState(title: String, modifier: Modifier = Modifier, hint: String? = null) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = Hsy.typography.titleMedium,
            color = Hsy.colors.onSurface,
            textAlign = TextAlign.Center,
        )
        if (hint != null) {
            Text(
                text = hint,
                style = Hsy.typography.bodyMedium,
                color = Hsy.colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * 错误态:把上游原话摆出来,并给一个"再来一次"的动作。
 *
 * 与 [HsyEmptyState] 分成两个组件而不是给空态加一个 `onRetry` 参数,是因为这两屏要求用户做的事相反:
 * 空态是"这里本来就没有,去别处/等一等",错误态是"这里本该有,刚才没拿到,可以立刻再要一次"。
 * 合成一个组件就会有一半调用点传 null,而"传了 null 所以没按钮"这种分支正是最难读的那种。
 *
 * [message] 由调用方从 `AppError` 折成人话(错误文案归 UI 层),这一层不认识 `AppError`。
 */
@Composable
fun HsyErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier, hint: String? = null) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = message,
            style = Hsy.typography.titleMedium,
            color = Hsy.colors.onSurface,
            textAlign = TextAlign.Center,
        )
        if (hint != null) {
            Text(
                text = hint,
                style = Hsy.typography.bodyMedium,
                color = Hsy.colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Button(onClick = onRetry) { Text("再试一次") }
    }
}
