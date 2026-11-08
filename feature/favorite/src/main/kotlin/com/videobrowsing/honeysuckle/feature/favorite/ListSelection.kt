package com.videobrowsing.honeysuckle.feature.favorite

/**
 * 列表勾选模式的一束读数:选没选、选了谁、点一下怎么翻。
 *
 * 收成一束而不是三个散参数,与本仓"递回调束"的做法同口径:收藏与稍后再看的列表分支都要这三件,
 * 散着递会把签名撑过 detekt 的必填参数阈值。行里只问它两件事 —— 这一条勾没勾([checked])、
 * 点一下是勾选还是打开([click])。
 */
internal class ListSelection(val selecting: Boolean, val selected: Set<String>, val onToggle: (String) -> Unit) {
    fun checked(bvid: String): Boolean = bvid in selected

    fun click(bvid: String, onOpen: () -> Unit) {
        if (selecting) onToggle(bvid) else onOpen()
    }
}
