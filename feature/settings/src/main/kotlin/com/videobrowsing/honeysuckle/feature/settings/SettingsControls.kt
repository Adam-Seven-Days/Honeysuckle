package com.videobrowsing.honeysuckle.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.videobrowsing.honeysuckle.core.datastore.danmaku.DanmakuPreferences
import com.videobrowsing.honeysuckle.core.designsystem.icon.HsyIcons
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.theme.HsyAccent
import com.videobrowsing.honeysuckle.core.designsystem.ui.FilterChip
import com.videobrowsing.honeysuckle.core.designsystem.ui.Icon
import com.videobrowsing.honeysuckle.core.designsystem.ui.OutlinedTextField
import com.videobrowsing.honeysuckle.core.designsystem.ui.Slider
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.designsystem.ui.TextButton

private val LabelWidth = 84.dp

// 读数栏要装得下"100% 屏高":64dp 时它在屏上折成两行,滑条看起来像坏了一格
private val ReadoutWidth = 92.dp

// 整数输入框:三位数足够(上限 75),再宽就只在桌面上好看
private val NumberFieldWidth = 88.dp

// 超过三位数的输入一律不收,免得用户能敲出一个永远落不了盘的值
private const val MAX_NUMBER_DIGITS = 3

// 50% 圆角 = 两端收成半圆的胶囊:写死成数,detekt 的 MagicNumber 会把它当工程值拦下来
private const val PILL_CORNER_PERCENT = 50
private val ChipShape = RoundedCornerShape(PILL_CORNER_PERCENT)

/** 屏上所有百分比读数与草稿换算共用这一个基数 */
internal const val PERCENT_BASE = 100

/**
 * 数值档:草稿在本地,松手落盘。
 *
 * `remember(value)` 这个键是有意留的:别处(演示页、播放页)改了同一个数,回流到这一屏时草稿要跟着走;
 * 拖动过程中 `value` 不变,所以草稿不会被盘上的旧值拽回去。
 */
@Composable
internal fun PrefSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    readout: (Float) -> String,
    onCommit: (Float) -> Unit,
) {
    var draft by remember(value) { mutableStateOf(value) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Text(
            text = label,
            style = Hsy.typography.bodyLarge,
            color = Hsy.colors.onSurface,
            modifier = Modifier.width(LabelWidth),
        )
        Slider(
            value = draft,
            onValueChange = { draft = it },
            onValueChangeFinished = { onCommit(draft) },
            valueRange = valueRange,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = readout(draft),
            style = Hsy.typography.bodyMedium,
            color = Hsy.colors.onSurfaceVariant,
            modifier = Modifier.width(ReadoutWidth),
        )
    }
}

/**
 * 整数档:屏上直接给一个数字,失焦或按完成时落盘。
 *
 * 字体大小这种量在用户心里就是"25 号字"这样的整数,连续滑条读出的 ×1.01 反而像坏了。
 * 空输入或越界不弹错误:清空就退回原值,超出区间按边界收口 —— 这个控件的语义只是改大改小。
 */
@Composable
internal fun IntPrefField(
    label: String,
    value: Int,
    valueRange: IntRange,
    supporting: String,
    onCommit: (Int) -> Unit,
) {
    var draft by remember(value) { mutableStateOf(value.toString()) }
    val focusManager = LocalFocusManager.current

    fun commit() {
        val next = (draft.trim().toIntOrNull() ?: value).coerceIn(valueRange)
        draft = next.toString()
        if (next != value) onCommit(next)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = Hsy.typography.bodyLarge, color = Hsy.colors.onSurface)
            Text(
                text = supporting,
                style = Hsy.typography.bodyMedium,
                color = Hsy.colors.onSurfaceVariant,
            )
        }
        OutlinedTextField(
            value = draft,
            onValueChange = { input ->
                if (input.length <= MAX_NUMBER_DIGITS && input.all { it.isDigit() }) draft = input
            },
            modifier = Modifier
                .padding(start = 12.dp)
                .width(NumberFieldWidth)
                .onFocusChanged { if (!it.isFocused) commit() },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            // 只收焦点:落盘统一走失焦那一条,免得"按完成"与"焦点丢失"各写一次
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            textStyle = Hsy.typography.bodyLarge.copy(textAlign = TextAlign.Center),
            singleLine = true,
        )
    }
}

@Composable
internal fun <T> TierRow(
    label: String,
    options: List<T>,
    selected: T,
    name: (T) -> String,
    onSelect: (T) -> Unit,
    supporting: String? = null,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(
            text = label,
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
        Row(
            modifier = Modifier.padding(top = 4.dp).horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEach { option ->
                TierChip(text = name(option), selected = option == selected, onClick = { onSelect(option) })
            }
        }
    }
}

@Composable
private fun TierChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = Hsy.colors
    val container = if (selected) colors.accentContainer else colors.surfaceVariant
    val onContainer = if (selected) colors.onAccentContainer else colors.onSurfaceVariant
    Box(
        modifier = Modifier
            .padding(end = 8.dp)
            .clip(ChipShape)
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(text = text, style = Hsy.typography.labelLarge, color = onContainer)
    }
}

/** 主题色:带色点的胶囊;色点用该档在当下亮暗里的强调色,不必先选中才看得见 */
@Composable
internal fun AccentPicker(selected: HsyAccent, onSelect: (HsyAccent) -> Unit) {
    val colors = Hsy.colors
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(
            text = "主题色",
            style = Hsy.typography.bodyLarge,
            color = colors.onSurface,
        )
        Text(
            text = "只改变选中态、进度与点赞等强调色;墨黑主按钮与播放器不受影响",
            style = Hsy.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.padding(top = 4.dp).horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HsyAccent.entries.forEach { option ->
                val swatch = if (colors.isLight) option.light.accent else option.dark.accent
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    modifier = Modifier.padding(end = 8.dp),
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(swatch),
                        )
                    },
                    label = { Text(option.displayName) },
                )
            }
        }
        Text(
            text = "当前:${selected.displayName}",
            style = Hsy.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/**
 * 屏蔽词表。
 *
 * 计数器把上限写在屏上(`N/200`),并且说明满了以后**丢最老的**:这一条规则藏在代码里没人看得出来,
 * 用户敲第 201 个词时若屏上毫无反应,只会以为输入坏了。
 */
@Composable
internal fun BlockedWordsEditor(words: Set<String>, onAction: (SettingsAction) -> Unit) {
    var draft by remember { mutableStateOf("") }
    val full = words.size >= DanmakuPreferences.MAX_BLOCKED_WORDS

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(
            text = "屏蔽词 ${words.size}/${DanmakuPreferences.MAX_BLOCKED_WORDS}",
            style = Hsy.typography.bodyLarge,
            color = Hsy.colors.onSurface,
        )
        Text(
            text = if (full) "已满,再加会挤掉最早的那批" else "命中这些词的那条弹幕不进屏幕",
            style = Hsy.typography.bodyMedium,
            color = Hsy.colors.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("如:剧透") },
                singleLine = true,
            )
            TextButton(
                onClick = {
                    onAction(SettingsAction.AddBlockedWord(draft))
                    draft = ""
                },
                enabled = draft.isNotBlank() && !full,
            ) {
                Text("添加")
            }
        }
        if (words.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                words.forEach { word ->
                    BlockedWordChip(word = word, onRemove = { onAction(SettingsAction.RemoveBlockedWord(word)) })
                }
            }
        }
    }
}

/** 词表里那一颗"点一下就划掉"的胶囊:整颗可点,因为小图标本身不好瞄 */
@Composable
private fun BlockedWordChip(word: String, onRemove: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(end = 8.dp)
            .clip(ChipShape)
            .background(Hsy.colors.surfaceVariant)
            .clickable(onClick = onRemove)
            .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = word,
                style = Hsy.typography.labelLarge,
                color = Hsy.colors.onSurfaceVariant,
            )
            Icon(
                imageVector = HsyIcons.close,
                contentDescription = "移除屏蔽词 $word",
                modifier = Modifier.padding(start = 4.dp).width(16.dp),
                tint = Hsy.colors.onSurfaceVariant,
            )
        }
    }
}
