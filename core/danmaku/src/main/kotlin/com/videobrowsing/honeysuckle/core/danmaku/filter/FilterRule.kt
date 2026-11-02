package com.videobrowsing.honeysuckle.core.danmaku.filter

/**
 * 屏蔽规则:类型化规则集。
 * UI 只暴露 [StringRule];[RegexRule] 引擎已支持,随"高级规则"开关逐步开放。
 */
sealed interface FilterRule {

    enum class MatchMode { EQUALS, CONTAINS, PREFIX, SUFFIX }

    data class StringRule(val word: String, val match: MatchMode = MatchMode.CONTAINS, val ignoreCase: Boolean = true) :
        FilterRule {
        init {
            require(word.isNotEmpty()) { "屏蔽词不能为空" }
            require(word.length <= MAX_WORD_LENGTH) { "屏蔽词超长(>$MAX_WORD_LENGTH)" }
        }
    }

    /** 高级规则:预编译由 [RegexRule] 自身持有;引擎侧限制长度防 ReDoS */
    data class RegexRule(val pattern: String) : FilterRule {
        init {
            require(pattern.length <= MAX_REGEX_LENGTH) { "正则超长(>$MAX_REGEX_LENGTH)" }
        }

        internal val compiled by lazy { runCatching { Regex(pattern) } }
    }

    companion object {
        const val MAX_WORD_LENGTH = 128
        const val MAX_REGEX_LENGTH = 256
    }
}
