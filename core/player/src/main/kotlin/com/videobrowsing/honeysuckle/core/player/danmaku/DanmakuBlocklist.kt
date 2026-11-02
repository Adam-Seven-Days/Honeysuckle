package com.videobrowsing.honeysuckle.core.player.danmaku

import com.videobrowsing.honeysuckle.core.danmaku.filter.FilterRule

/**
 * 屏蔽词 → 屏蔽规则("屏蔽词/正则/用户/模式"四类规则里 UI 只开放字符串那一类)。
 *
 * 这一层的存在理由:设置页存的是**一串用户手打的词**,内核吃的是**类型化规则集**,
 * 两边都不该认识对方(存储层不依赖渲染内核,内核不依赖磁盘格式),中间这道翻译归策略层。
 *
 * 三道清洗都有对应的坑:
 *  - 首尾空白:复制粘贴带进来的空格会让 `"前方高能 "` 匹配不上"前方高能";
 *  - 空串:整条作废(它匹配任何文本,等于一键清空全屏弹幕);
 *  - 超长:丢掉而不是截断 —— 一个 300 字的"屏蔽词"是误输入,截断成前 128 字反而挡下意想不到的条。
 *    [FilterRule.StringRule] 的 `require` 会直接抛,这里兜住它,免得设置页加一个词炸掉播放页。
 *
 * 重复词条一并去掉:同一条进两次 automaton 只是白占 trie 节点,但更麻烦的是它会让"已屏蔽 N 个词"
 * 这个读数与用户心里的数不一致。
 */
object DanmakuBlocklist {

    /** 用户词的默认口径:包含匹配 + 忽略大小写(与官方客户端那一档的观感对齐) */
    fun rules(words: Collection<String>): List<FilterRule> = words.asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && it.length <= FilterRule.MAX_WORD_LENGTH }
        .distinct()
        .map { FilterRule.StringRule(word = it, match = FilterRule.MatchMode.CONTAINS, ignoreCase = true) }
        .toList()
}
