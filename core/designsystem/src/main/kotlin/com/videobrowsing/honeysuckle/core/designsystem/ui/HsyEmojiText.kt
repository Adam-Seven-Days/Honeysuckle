package com.videobrowsing.honeysuckle.core.designsystem.ui

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy

/** 表情边长相对字号的倍数:与文字等高的图压在正文里读着像被削了一刀,平台网页也在这一档 */
private const val EMOJI_SIZE_RATIO = 1.35f

/** 调用方没在 [style] 里写字号时的兜底字号;与 [Hsy.typography] 的正文档同量级 */
private val FALLBACK_FONT_SIZE = 14.sp

private const val LIGHT_MENTION_ARGB = 0xFF0075B0
private const val DARK_MENTION_ARGB = 0xFF64B5F6

private sealed interface Segment
private data class TextSegment(val text: String) : Segment
private data class EmoteSegment(val placeholder: String) : Segment
private data class MentionSegment(val name: String, val mid: Long) : Segment

/**
 * 图文混排的一行正文:[emotes] 占位符画成图片,[mentions] 中的 @ 用户画成蓝色链接。
 *
 * 本组件不认识"这是 B 站的表情":给它一张 `{占位符 = 图片地址}` 它就画,其余文字照 [Text] 走。
 * 表里没有的占位符原样留在正文里 —— 用户看得见"这里本来有个表情",而不是被猜一张图,
 * 也不是被吞掉之后读出一句缺字的话。
 *
 * 混排走 Compose 的 inline content,而不是 `FlowRow` 里一段字一个图:后者会在每个表情处留下换行机会,
 * 一条长评论被三个表情切成四段,行尾还参差不齐,读起来像排版坏了。
 */
@Composable
fun EmojiText(
    text: String,
    emotes: Map<String, String>,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    style: TextStyle = Hsy.typography.bodyMedium,
    mentions: Map<String, Long> = emptyMap(),
    onMentionClick: (Long) -> Unit = {},
) {
    val segments = remember(text, emotes, mentions) { splitContent(text, emotes, mentions) }
    val mentionColor = Color(if (Hsy.colors.isLight) LIGHT_MENTION_ARGB else DARK_MENTION_ARGB)
    val annotated = remember(segments, mentionColor, onMentionClick) {
        buildAnnotatedString { segments.forEach { append(it, mentionColor, onMentionClick) } }
    }
    val side = emojiSide(style)
    val sideSize = with(LocalDensity.current) { side.toDp() }
    // 每次重组重建这张表:children 是 composable lambda,不能塞进 remember 的计算块里。
    // 建表只是造几个闭包,真正取图由 Coil 的内存缓存兜住,不会因此多一次请求
    val inlineContent = segments.filterIsInstance<EmoteSegment>().associate { segment ->
        val url = emotes[segment.placeholder].orEmpty()
        segment.placeholder to InlineTextContent(
            placeholder = Placeholder(
                width = side,
                height = side,
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
            ),
            children = {
                AsyncImage(
                    model = url,
                    contentDescription = segment.placeholder.emoteName(),
                    modifier = Modifier.size(sideSize),
                )
            },
        )
    }

    Text(
        text = annotated,
        modifier = modifier,
        color = color,
        maxLines = maxLines,
        style = style,
        inlineContent = inlineContent,
    )
}

private fun AnnotatedString.Builder.append(segment: Segment, mentionColor: Color, onMentionClick: (Long) -> Unit) {
    when (segment) {
        is TextSegment -> append(segment.text)

        is EmoteSegment -> appendInlineContent(segment.placeholder, segment.placeholder.emoteName())

        is MentionSegment -> withLink(
            LinkAnnotation.Clickable(
                tag = "mention:${segment.mid}",
                styles = TextLinkStyles(style = SpanStyle(color = mentionColor)),
                linkInteractionListener = { onMentionClick(segment.mid) },
            ),
        ) { append("@${segment.name}") }
    }
}

// 最长匹配优先,避免较短的用户名截断同前缀的另一个 @ 用户。
private fun splitContent(text: String, emotes: Map<String, String>, mentions: Map<String, Long>): List<Segment> {
    val replacements = buildMap<String, Segment> {
        emotes.keys.filter { it.isNotEmpty() }.forEach { put(it, EmoteSegment(it)) }
        mentions.forEach { (name, mid) ->
            if (name.isNotBlank() && mid > 0L) put("@$name", MentionSegment(name, mid))
        }
    }
    val keys = replacements.keys.sortedByDescending { it.length }
    if (keys.isEmpty()) return listOf(TextSegment(text))
    val segments = mutableListOf<Segment>()
    val plain = StringBuilder()
    var index = 0
    while (index < text.length) {
        val hit = keys.firstOrNull { text.startsWith(it, index) }
        if (hit == null) {
            plain.append(text[index])
            index++
            continue
        }
        if (plain.isNotEmpty()) {
            segments.add(TextSegment(plain.toString()))
            plain.setLength(0)
        }
        segments.add(replacements.getValue(hit))
        index += hit.length
    }
    if (plain.isNotEmpty()) segments.add(TextSegment(plain.toString()))
    return segments
}

/** 读屏与复制用的名字:`[doge]` → `doge`;方括号本来就不是要念出来的东西 */
private fun String.emoteName(): String = removeSurrounding("[", "]")

/**
 * 表情边长跟着字号走:正文档与回复档差着一号字,图也该差一号,否则小字配大图会把行高顶开。
 *
 * 交回 `TextUnit` 而不是 `Dp` —— 平台的 `Placeholder` 按 TextUnit 收尺寸,sp 那一档还会随系统字号
 * 一起放大,与它旁边那行字保持同一个比例。
 */
private fun emojiSide(style: TextStyle): TextUnit {
    val fontSize = style.fontSize.takeIf { it != TextUnit.Unspecified } ?: FALLBACK_FONT_SIZE
    return TextUnit(fontSize.value * EMOJI_SIZE_RATIO, fontSize.type)
}
