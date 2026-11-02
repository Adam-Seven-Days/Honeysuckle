package com.videobrowsing.honeysuckle.core.common

private const val TEN_THOUSAND = 10_000L
private const val HUNDRED_MILLION = 100_000_000L

/** 留一位小数就乘除 10:这个 10 出现在两处,写死两处就是两处口径,一处的常量才是 */
private const val ONE_DECIMAL = 10.0

/**
 * 大数折成中文读数:`1506609 → 150.7 万`、`320000000 → 3.2 亿`,一万以内原样。
 *
 * 单位并进字符串返回而不是返回一个"数值 + 单位"的二元组:卡片上那一行是"150.7 万播放",
 * 拆两段就得在 UI 里再拼一次,而"什么时候进万、什么时候进亿"这条判据只想有一个出处。
 *
 * 小数留一位并**向下取整**(不四舍五入):屏上把 9 999 报成"1.0 万"是虚报,
 * 而读数只用来比多少,宁可少说一成。负数按 0 处理(计数来自接口,风控期间偶发负值不值得为它做分支)。
 */
fun formatCount(count: Long): String = when {
    count < TEN_THOUSAND -> count.coerceAtLeast(0L).toString()
    count < HUNDRED_MILLION -> "${truncateToOne(count.toDouble() / TEN_THOUSAND)} 万"
    else -> "${truncateToOne(count.toDouble() / HUNDRED_MILLION)} 亿"
}

private fun truncateToOne(value: Double): String {
    val truncated = (value * ONE_DECIMAL).toLong() / ONE_DECIMAL
    return if (truncated % 1.0 == 0.0) truncated.toLong().toString() else truncated.toString()
}

private const val BYTES_PER_KB = 1024.0
private const val BYTES_PER_MB = 1024.0 * 1024
private const val BYTES_PER_GB = 1024.0 * 1024 * 1024
private const val BYTES_KEEP_DECIMAL = 10.0

/**
 * 磁盘用量读数:`1.4 MB`、`912 KB`、`0 B`。
 *
 * 与 [formatCount] 同一条口径:小数留一位且向下截(把 0.99 GB 报成 1.0 GB 是虚报)。
 * 单位止于 GB —— 一个客户端的缓存真到 TB 级就已经是异常,那时精确到 0.1 GB 没有意义。
 */
fun formatBytes(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0L)
    val (value, unit) = when {
        safe < BYTES_PER_KB -> safe.toDouble() to "B"
        safe < BYTES_PER_MB -> safe / BYTES_PER_KB to "KB"
        safe < BYTES_PER_GB -> safe / BYTES_PER_MB to "MB"
        else -> safe / BYTES_PER_GB to "GB"
    }
    if (unit == "B") return "${safe}B"
    val truncated = (value * BYTES_KEEP_DECIMAL).toLong() / BYTES_KEEP_DECIMAL
    return if (truncated % 1.0 == 0.0) "${truncated.toLong()} $unit" else "$truncated $unit"
}
