package com.videobrowsing.honeysuckle.core.qrcode

internal const val QR_MODULES_PER_VERSION = 4
internal const val QR_BASE_SIZE = 17

/**
 * 纠错档位:L/M/Q/H 的冗余依次升高,可恢复的坏块变多,同样内容占的版本也变大。
 * [formatBits] 是格式信息里这两位的编号(ISO/IEC 18004 表 12)。
 */
@Suppress("MagicNumber") // 两位编号是标准原值:01=L、00=M、11=Q、10=H
enum class QrEcc(internal val formatBits: Int) {
    L(0b01),
    M(0b00),
    Q(0b11),
    H(0b10),
}

/**
 * 编好的模块矩阵:不含静区,左上角是第一个模块。
 * 画图的一方按 [size] 逐格取 [isDark],自己决定留几个模块的白边。
 */
class QrMatrix internal constructor(val version: Int, val ecc: QrEcc, val mask: Int, private val cells: BooleanArray) {
    val size: Int = version * QR_MODULES_PER_VERSION + QR_BASE_SIZE

    fun isDark(x: Int, y: Int): Boolean {
        require(x in 0 until size && y in 0 until size) { "模块坐标越界:($x, $y),边长 $size" }
        return cells[y * size + x]
    }
}

/**
 * 二维码编码入口:字节模式、版本 1–40、四档纠错,按容量自动选最小版本并挑掩码。
 *
 * 内容按 UTF-8 取字节;超过所选档位版本 40 的容量直接拒绝,由调用方决定怎么提示。
 */
object QrCode {
    fun encode(text: String, ecc: QrEcc = QrEcc.L): QrMatrix = QrEncoder.encode(text, ecc)
}
