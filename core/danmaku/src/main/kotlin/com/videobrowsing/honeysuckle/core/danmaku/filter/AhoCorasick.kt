package com.videobrowsing.honeysuckle.core.danmaku.filter

/** 根节点下标:trie 的 0 号位永远是它 */
private const val ROOT = 0

/**
 * 单遍多模式匹配(Aho-Corasick)—— 设计目标。
 *
 * 只需要"命中任一词条"这一个布尔,所以每个节点只记一个 `terminal`(本节点或它的失败链上有完整词条
 * 收尾),构建时按 BFS 把 `terminal[child] |= terminal[fail[child]]` 一次算完 —— 查询侧因此不必再走
 * 一遍输出链,命中判定就是查一个布尔数组。
 *
 * 复杂度:构建 O(词条总长),查询 O(文本长) 且**与词条数无关**。这就是它相对逐条 `contains` 的全部
 * 优势;词条在几十这个量级上两者看不出差别,值回票价的是"百级屏蔽词"那一档(已知限制)。
 *
 * 空串词条在入口就被滤掉:AC 收下空串等于"任何文本都命中",那会把整屏弹幕挡光。
 * 线性扫描那条老路本来也有同一颗雷(`"任意".contains("")` 为真),两边走法一致地滤掉它。
 */
internal class AhoCorasick(patterns: List<String>) {

    private val transitions = ArrayList<HashMap<Char, Int>>()
    private val fail = ArrayList<Int>()
    private val terminal = ArrayList<Boolean>()

    /** 一条词条都没收下(空词表)时不必走 automaton,免得白扫一遍文本 */
    private val empty: Boolean

    init {
        newNode()
        val words = patterns.filter { it.isNotEmpty() }
        words.forEach { insert(it) }
        linkFailures()
        empty = words.isEmpty()
    }

    fun containsAny(text: String): Boolean {
        if (empty) return false
        var node = ROOT
        for (index in text.indices) {
            val char = text[index]
            var next = transitions[node][char]
            // 走不下去就顺着失败链接退:退一次深度就少一层,所以整趟仍是 O(文本长)
            while (node != ROOT && next == null) {
                node = fail[node]
                next = transitions[node][char]
            }
            node = next ?: ROOT
            if (terminal[node]) return true
        }
        return false
    }

    private fun insert(pattern: String) {
        var node = ROOT
        for (char in pattern) {
            node = transitions[node][char] ?: newNode().also { transitions[node][char] = it }
        }
        terminal[node] = true
    }

    /** BFS 逐层接失败链接;根的直系子节点一律指向根 */
    private fun linkFailures() {
        val queue = ArrayDeque<Int>()
        transitions[ROOT].values.forEach { child ->
            fail[child] = ROOT
            queue.addLast(child)
        }
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            for ((char, child) in transitions[node]) {
                var ancestor = fail[node]
                while (ancestor != ROOT && transitions[ancestor][char] == null) ancestor = fail[ancestor]
                val link = transitions[ancestor][char] ?: ROOT
                // 子节点自己不收尾也算命中:它的失败链上可能正压着另一个词条("she" 里的 "he")
                terminal[child] = terminal[child] || terminal[link]
                fail[child] = link
                queue.addLast(child)
            }
        }
    }

    private fun newNode(): Int {
        transitions += HashMap()
        fail += ROOT
        terminal += false
        return transitions.size - 1
    }
}
