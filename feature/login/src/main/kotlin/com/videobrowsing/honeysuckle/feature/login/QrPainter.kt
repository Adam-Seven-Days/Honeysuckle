package com.videobrowsing.honeysuckle.feature.login

import android.graphics.Bitmap
import com.videobrowsing.honeysuckle.core.qrcode.QrCode
import com.videobrowsing.honeysuckle.core.qrcode.QrMatrix
import java.util.Arrays

internal const val QR_RENDER_PX = 512
internal const val QR_QUIET_MODULES = 1
internal const val QR_WHITE = -1
internal const val QR_BLACK = -0x1000000
private const val QUIET_SIDES = 2

/**
 * 二维码出图:每个模块落成整数倍的方块,四周留 [quietModules] 个模块的白边,
 * 余下的像素居中补齐 —— 非整数缩放会把模块边缘糊掉,扫码时更难对准。
 */
internal fun qrPixels(matrix: QrMatrix, sizePx: Int, quietModules: Int = QR_QUIET_MODULES): IntArray {
    val gridModules = matrix.size + quietModules * QUIET_SIDES
    val scale = (sizePx / gridModules).coerceAtLeast(1)
    val offset = (sizePx - gridModules * scale) / 2
    val pixels = IntArray(sizePx * sizePx) { QR_WHITE }
    val painter = ModulePainter(pixels, sizePx, offset, scale, quietModules)
    for (y in 0 until matrix.size) {
        for (x in 0 until matrix.size) {
            if (matrix.isDark(x, y)) painter.paint(x, y)
        }
    }
    return pixels
}

/** 编码失败不抛给屏:返回 null 由调用方摆一句可恢复的话。 */
internal fun renderQr(text: String): Bitmap? = runCatching {
    val pixels = qrPixels(QrCode.encode(text), QR_RENDER_PX)
    Bitmap.createBitmap(pixels, QR_RENDER_PX, QR_RENDER_PX, Bitmap.Config.ARGB_8888)
}.getOrNull()

private class ModulePainter(
    private val pixels: IntArray,
    private val sizePx: Int,
    private val offset: Int,
    private val scale: Int,
    private val quietModules: Int,
) {
    fun paint(x: Int, y: Int) {
        val left = offset + (x + quietModules) * scale
        val top = offset + (y + quietModules) * scale
        for (pixelY in top until top + scale) {
            val rowStart = pixelY * sizePx
            Arrays.fill(pixels, rowStart + left, rowStart + left + scale, QR_BLACK)
        }
    }
}
