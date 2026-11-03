package com.videobrowsing.honeysuckle.feature.message

import com.videobrowsing.honeysuckle.core.model.MessageKind

/** 三栏的名字、列表里那行动作与空态文案:同一套口吻,放一处改 */
internal fun labelOf(kind: MessageKind): String = when (kind) {
    MessageKind.REPLY -> "回复"
    MessageKind.LIKE -> "点赞"
    MessageKind.AT -> "@我"
}

internal fun actionOf(kind: MessageKind): String = when (kind) {
    MessageKind.REPLY -> "回复了你"
    MessageKind.LIKE -> "赞了你"
    MessageKind.AT -> "在动态里 @ 了你"
}

internal fun emptyTitleOf(kind: MessageKind): String = when (kind) {
    MessageKind.REPLY -> "还没有人回复你"
    MessageKind.LIKE -> "还没有人赞你"
    MessageKind.AT -> "还没有人 @ 你"
}
