package com.videobrowsing.honeysuckle.core.qrcode

private const val BITS_PER_BYTE = 8

/**
 * 二维码标准表(ISO/IEC 18004 表 9):版本 × 纠错档位决定每块纠错字节数与分块数。
 *
 * 这两组数不是可调参数 —— 差一格,任何扫码器都纠不回来。下标 0 空着,按版本号直接取;
 * 四条数组的顺序与 [QrEcc] 的声明顺序一致(L/M/Q/H)。
 */
@Suppress("MagicNumber") // 整对象就是标准表的逐格转录,给每个数命名只会让对照原表更难
internal object QrTables {
    const val MIN_VERSION = 1
    const val MAX_VERSION = 40

    private val ECC_PER_BLOCK = arrayOf(
        intArrayOf(
            0, 7, 10, 15, 20, 26, 18, 20, 24, 30,
            18, 20, 24, 26, 30, 22, 24, 28, 30, 28,
            28, 28, 28, 30, 30, 26, 28, 30, 30, 30,
            30, 30, 30, 30, 30, 30, 30, 30, 30, 30,
            30,
        ),
        intArrayOf(
            0, 10, 16, 26, 18, 24, 16, 18, 22, 22,
            26, 30, 22, 22, 24, 24, 28, 28, 26, 26,
            26, 26, 28, 28, 28, 28, 28, 28, 28, 28,
            28, 28, 28, 28, 28, 28, 28, 28, 28, 28,
            28,
        ),
        intArrayOf(
            0, 13, 22, 18, 26, 18, 24, 18, 22, 20,
            24, 28, 26, 24, 20, 30, 24, 28, 28, 26,
            30, 28, 30, 30, 30, 30, 28, 30, 30, 30,
            30, 30, 30, 30, 30, 30, 30, 30, 30, 30,
            30,
        ),
        intArrayOf(
            0, 17, 28, 22, 16, 22, 28, 26, 26, 24,
            28, 24, 28, 22, 24, 24, 30, 28, 28, 26,
            28, 30, 24, 30, 30, 30, 30, 30, 30, 30,
            30, 30, 30, 30, 30, 30, 30, 30, 30, 30,
            30,
        ),
    )

    private val BLOCK_COUNT = arrayOf(
        intArrayOf(
            0, 1, 1, 1, 1, 1, 2, 2, 2, 2,
            4, 4, 4, 4, 4, 6, 6, 6, 6, 7,
            8, 8, 9, 9, 10, 12, 12, 12, 13, 14,
            15, 16, 17, 18, 19, 19, 20, 21, 22, 24,
            25,
        ),
        intArrayOf(
            0, 1, 1, 1, 2, 2, 4, 4, 4, 5,
            5, 5, 8, 9, 9, 10, 10, 11, 13, 14,
            16, 17, 17, 18, 20, 21, 23, 25, 26, 28,
            29, 31, 33, 35, 37, 38, 40, 43, 45, 47,
            49,
        ),
        intArrayOf(
            0, 1, 1, 2, 2, 4, 4, 6, 6, 8,
            8, 8, 10, 12, 16, 12, 17, 16, 18, 21,
            20, 23, 23, 25, 27, 29, 34, 34, 35, 38,
            40, 43, 45, 48, 51, 53, 56, 59, 62, 65,
            68,
        ),
        intArrayOf(
            0, 1, 1, 2, 4, 4, 4, 5, 6, 8,
            8, 11, 11, 16, 16, 18, 16, 19, 21, 25,
            25, 25, 34, 30, 32, 35, 37, 40, 42, 45,
            48, 51, 54, 57, 60, 63, 66, 70, 74, 77,
            81,
        ),
    )

    fun eccPerBlock(ecc: QrEcc, version: Int): Int = ECC_PER_BLOCK[ecc.ordinal][version]

    fun blockCount(ecc: QrEcc, version: Int): Int = BLOCK_COUNT[ecc.ordinal][version]

    fun dataCodewords(ecc: QrEcc, version: Int): Int =
        totalCodewords(version) - eccPerBlock(ecc, version) * blockCount(ecc, version)

    fun totalCodewords(version: Int): Int = rawDataModules(version) / BITS_PER_BYTE

    /** 字节模式字符计数字段:版本 1–9 是 8 位,10 以上是 16 位(ISO/IEC 18004 表 3)。 */
    fun charCountBits(version: Int): Int = if (version <= 9) 8 else 16

    /** 校正图形中心坐标:先放 6,再按步长从右下角往回排;版本 32 的步长是特例 26。 */
    fun alignmentCenters(version: Int): IntArray {
        if (version == 1) return IntArray(0)
        val count = version / 7 + 2
        val step = if (version == 32) 26 else (version * 4 + count * 2 + 1) / (count * 2 - 2) * 2
        return IntArray(count).also { centers ->
            centers[0] = 6
            var position = version * 4 + 10
            for (index in count - 1 downTo 1) {
                centers[index] = position
                position -= step
            }
        }
    }

    /** 除功能图形与格式/版本信息之外的模块数,除以 8 就是码字总数(余下的是剩余位)。 */
    private fun rawDataModules(version: Int): Int {
        var result = (16 * version + 128) * version + 64
        if (version >= 2) {
            val align = version / 7 + 2
            result -= (25 * align - 10) * align - 55
        }
        if (version >= 7) result -= 36
        return result
    }
}
