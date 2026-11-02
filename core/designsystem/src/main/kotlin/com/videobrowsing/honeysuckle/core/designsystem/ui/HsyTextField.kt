package com.videobrowsing.honeysuckle.core.designsystem.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.theme.LocalHsyContentColor

/**
 * 输入框:1dp 描边 + 10dp 圆角,聚焦时描边转强调色。
 *
 * 这是"面板"而不是"下划线":在深色底上,下划线输入框很容易和列表分隔线混在一起。
 * label 摆在上方而不是做浮动动画 —— 这一仓的输入框都用在单行检索或短文本上,
 * 浮动标签省下的那点高度换不来可读性。
 */
@Composable
fun OutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = Hsy.typography.bodyMedium,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    isError: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val borderColor = when {
        !enabled -> Hsy.colors.outlineVariant
        isError -> Hsy.colors.error
        focused -> Hsy.colors.accent
        else -> Hsy.colors.outline
    }
    val textColor = if (enabled) Hsy.colors.onSurface else Hsy.colors.onSurfaceVariant

    Column(modifier = modifier) {
        if (label != null) {
            Box(modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)) {
                CompositionLocalProvider(LocalHsyContentColor provides Hsy.colors.onSurfaceVariant) {
                    label()
                }
            }
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(Hsy.shapes.small))
                .background(Hsy.colors.surface)
                .border(1.dp, borderColor, RoundedCornerShape(Hsy.shapes.small))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            enabled = enabled,
            readOnly = readOnly,
            textStyle = textStyle.copy(color = textColor),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            visualTransformation = visualTransformation,
            interactionSource = interactionSource,
            cursorBrush = SolidColor(Hsy.colors.accent),
            decorationBox = { innerTextField ->
                FieldDecoration(
                    value = value,
                    placeholder = placeholder,
                    leadingIcon = leadingIcon,
                    trailingIcon = trailingIcon,
                    innerTextField = innerTextField,
                )
            },
        )
    }
}

/** 输入区的内衬:左右图标槽 + 空值时显示占位文案 */
@Composable
private fun FieldDecoration(
    value: String,
    placeholder: (@Composable () -> Unit)?,
    leadingIcon: (@Composable () -> Unit)?,
    trailingIcon: (@Composable () -> Unit)?,
    innerTextField: @Composable () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (leadingIcon != null) {
            Box(
                modifier = Modifier.padding(end = 8.dp).size(18.dp),
                contentAlignment = Alignment.Center,
            ) { leadingIcon() }
        }
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty() && placeholder != null) {
                CompositionLocalProvider(
                    LocalHsyContentColor provides Hsy.colors.onSurfaceVariant,
                ) { placeholder() }
            }
            innerTextField()
        }
        if (trailingIcon != null) {
            Box(modifier = Modifier.padding(start = 6.dp)) { trailingIcon() }
        }
    }
}
