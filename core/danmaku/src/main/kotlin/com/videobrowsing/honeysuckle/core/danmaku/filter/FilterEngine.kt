package com.videobrowsing.honeysuckle.core.danmaku.filter

import com.videobrowsing.honeysuckle.core.danmaku.model.DanmakuEntry

/**
 * 弹幕屏蔽引擎:
 *  - 规则匹配前先做归一化(小写 + NFKC 全角→半角),保证"ｊｋ"与"jk"同判;
 *  - CONTAINS 走 [AhoCorasick] 单遍匹配:判定成本与词条数脱钩,百级屏蔽词不再逐条扫;
 *  - 匹配结果按"弹幕全文" LRU 缓存 —— 弹幕文本重复率极高,缓存收益大;
 *  - 正则规则只在命中率进入判定时才启用,错误正则在编译期即被 [FilterRule.RegexRule.compiled]
 *    捕获为无效,非法正则不会炸掉渲染线程。
 *
 * 两条 automaton 而不是"一条通吃",是因为两组规则的文本形态本就不同:忽略大小写的那一组匹配在
 * 归一化 + 压空白的文本上,区分大小写的那一组匹配在原文上(老口径如此 —— 压缩空白是防"前 方 高 能"
 * 式逃逸的,而区分大小写的规则本就是精确匹配,不该替它放宽)。
 */
class FilterEngine(engineRules: List<FilterRule>) {

    private val warmRules: List<WarmRule> = engineRules.filterIsInstance<FilterRule.StringRule>().mapNotNull {
        val word = if (it.ignoreCase) compact(normalizeText(it.word)) else it.word
        // 归一化后见底空的词条必须作废:它匹配任何文本,不该让整屏弹幕给一个手滑的空格陪葬
        if (word.isEmpty()) null else WarmRule(it.match, it.ignoreCase, word)
    }

    private val containsIgnoreCase = AhoCorasick(warmRules.containsWords(onNormalizedText = true))

    private val containsCaseSensitive = AhoCorasick(warmRules.containsWords(onNormalizedText = false))

    /** 锚定型规则(整串相等/前缀/后缀):它们问的不是"这个词出没出现",AC 帮不上,仍是线性 */
    private val anchoredRules: List<WarmRule> = warmRules.filter { it.match != FilterRule.MatchMode.CONTAINS }

    private val regexRules: List<RegexWarm> =
        engineRules.filterIsInstance<FilterRule.RegexRule>()
            .mapNotNull { r ->
                r.compiled.getOrNull()?.let { RegexWarm(it) }
            }

    /** 一条规则都没收下(空规则集,或全被上面的空词条守卫挡掉)时,连归一化都不必做 */
    private val blocksNothing = warmRules.isEmpty() && regexRules.isEmpty()

    private data class RegexWarm(val re: Regex)

    private val cache = object : LinkedHashMap<String, Boolean>(CACHE_INITIAL_CAPACITY, CACHE_LOAD_FACTOR, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>): Boolean = size > CACHE_SIZE
    }

    /** 词条或规则发生变化后调用 */
    fun invalidate() = synchronized(this) { cache.clear() }

    fun isBlocked(entry: DanmakuEntry): Boolean = isBlocked(entry.text)

    fun isBlocked(text: String): Boolean {
        // 按原始全文缓存(弹幕文本重复率极高;同一文本重复出现直接查表,省电)
        synchronized(this) { cache[text]?.let { return it } }

        val blocked = if (blocksNothing) {
            false
        } else {
            val normalized = normalizeText(text)
            evalString(compact(normalized), text) || evalRegex(normalized)
        }
        synchronized(this) { cache[text] = blocked }
        return blocked
    }

    private fun evalString(compactNormalized: String, raw: String): Boolean {
        if (containsIgnoreCase.containsAny(compactNormalized)) return true
        if (containsCaseSensitive.containsAny(raw)) return true
        for (rule in anchoredRules) {
            val target = if (rule.onNormalizedText) compactNormalized else raw
            val hit = when (rule.match) {
                FilterRule.MatchMode.EQUALS -> target == rule.word

                FilterRule.MatchMode.PREFIX -> target.startsWith(rule.word)

                FilterRule.MatchMode.SUFFIX -> target.endsWith(rule.word)

                // CONTAINS 在上面已经交给 automaton,这条支到不了这里
                FilterRule.MatchMode.CONTAINS -> false
            }
            if (hit) return true
        }
        return false
    }

    private fun evalRegex(normalized: String): Boolean = regexRules.any { it.re.containsMatchIn(normalized) }

    private companion object {
        private const val CACHE_SIZE = 512

        /** 一屏重复词条数量级很小,容量给到 64 足够撑过冷启动,避免边填边扩容 */
        private const val CACHE_INITIAL_CAPACITY = 64
        private const val CACHE_LOAD_FACTOR = 0.75f
    }
}

/** 字符串规则预烘焙后的形态:归一化在构造期做一次,查询侧只剩匹配本身 */
private class WarmRule(
    val match: FilterRule.MatchMode,
    /** true = 匹配归一化后的文本(即原规则 ignoreCase);false = 匹配原文 */
    val onNormalizedText: Boolean,
    val word: String,
)

/** 把 CONTAINS 那一组按文本形态分桶,整桶交给一条 automaton */
private fun List<WarmRule>.containsWords(onNormalizedText: Boolean): List<String> =
    filter { it.match == FilterRule.MatchMode.CONTAINS && it.onNormalizedText == onNormalizedText }.map { it.word }

/** 归一化:小写 + NFKC(全角→半角等)+ 压缩空白;正则规则在此形态上匹配 */
fun normalizeText(raw: String): String = java.text.Normalizer.normalize(raw, java.text.Normalizer.Form.NFKC)
    .lowercase(java.util.Locale.ROOT)
    .trim()
    .replace(Regex("\\s+"), " ")

/** 字符串规则的匹配形态:去掉全部空白,防"前 方 高 能"式逃逸;正则不走此形态(\s 语义要保住) */
private fun compact(text: String): String = text.filterNot { it.isWhitespace() }
