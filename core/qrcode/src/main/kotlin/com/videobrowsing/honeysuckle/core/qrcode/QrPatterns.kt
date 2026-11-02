package com.videobrowsing.honeysuckle.core.qrcode

import kotlin.math.abs
import kotlin.math.max

private const val FINDER_MODULES = 7
private const val FINDER_CORE_START = 2
private const val FINDER_CORE_END = 4
private const val ALIGN_RADIUS = 2
private const val TIMING_LINE = 6
private const val TIMING_START = 8
private const val FORMAT_MODULES = 15
private const val FORMAT_LINE = 8
private const val FORMAT_DATA_BITS = 5
private const val FORMAT_MASK_BITS = 3
private const val FORMAT_GENERATOR = 0x537
private const val FORMAT_XOR = 0x5412
private const val VERSION_INFO_MIN = 7
private const val VERSION_MODULES = 18
private const val VERSION_OFFSET = 11
private const val VERSION_GROUP = 3
private const val VERSION_REMAINDER_BITS = 12
private const val VERSION_GENERATOR = 0x1F25

/** 与内容无关的固定图形:定位、分隔、时序、校正、暗模块、格式与版本信息。 */
internal object QrPatterns {
    fun drawFixed(grid: QrGrid, version: Int) {
        drawFinder(grid, 0, 0)
        drawFinder(grid, grid.size - FINDER_MODULES, 0)
        drawFinder(grid, 0, grid.size - FINDER_MODULES)
        drawTiming(grid)
        drawAlignment(grid, version)
        grid.setFixed(FORMAT_LINE, grid.size - FORMAT_LINE, true)
        placeFormat(grid, QrEcc.L, 0) // 先占位;选定掩码后按同一组位置重写
        if (version >= VERSION_INFO_MIN) placeVersion(grid, version)
    }

    /** 格式信息两副本:沿三个定位图形内侧各摆 15 位,自身再异或 0x5412。 */
    fun placeFormat(grid: QrGrid, ecc: QrEcc, mask: Int) {
        val bits = formatBits(ecc, mask)
        val size = grid.size
        for (index in 0 until FORMAT_MODULES) {
            val dark = (bits ushr index) and 1 != 0
            when {
                index < TIMING_LINE -> grid.setFixed(FORMAT_LINE, index, dark)
                index < FORMAT_LINE -> grid.setFixed(FORMAT_LINE, index + 1, dark)
                index == FORMAT_LINE -> grid.setFixed(FORMAT_LINE - 1, FORMAT_LINE, dark)
                else -> grid.setFixed(FORMAT_MODULES - 1 - index, FORMAT_LINE, dark)
            }
            if (index < FORMAT_LINE) {
                grid.setFixed(size - 1 - index, FORMAT_LINE, dark)
            } else {
                grid.setFixed(FORMAT_LINE, size - FORMAT_MODULES + index, dark)
            }
        }
    }

    private fun drawFinder(grid: QrGrid, left: Int, top: Int) {
        for (dy in -1..FINDER_MODULES) {
            for (dx in -1..FINDER_MODULES) {
                val x = left + dx
                val y = top + dy
                if (x !in 0 until grid.size || y !in 0 until grid.size) continue
                val inFinder = dx in 0 until FINDER_MODULES && dy in 0 until FINDER_MODULES
                val border = dx == 0 || dx == FINDER_MODULES - 1 || dy == 0 || dy == FINDER_MODULES - 1
                val core = dx in FINDER_CORE_START..FINDER_CORE_END && dy in FINDER_CORE_START..FINDER_CORE_END
                grid.setFixed(x, y, inFinder && (border || core))
            }
        }
    }

    private fun drawTiming(grid: QrGrid) {
        for (index in TIMING_START until grid.size - TIMING_START) {
            val dark = index % 2 == 0
            grid.setFixed(TIMING_LINE, index, dark)
            grid.setFixed(index, TIMING_LINE, dark)
        }
    }

    private fun drawAlignment(grid: QrGrid, version: Int) {
        val centers = QrTables.alignmentCenters(version)
        val last = centers.size - 1
        for (row in centers.indices) {
            for (column in centers.indices) {
                val overlapsFinder = (row == 0 && column == 0) ||
                    (row == 0 && column == last) ||
                    (row == last && column == 0)
                if (!overlapsFinder) drawAlignmentAt(grid, centers[column], centers[row])
            }
        }
    }

    private fun drawAlignmentAt(grid: QrGrid, centerX: Int, centerY: Int) {
        for (dy in -ALIGN_RADIUS..ALIGN_RADIUS) {
            for (dx in -ALIGN_RADIUS..ALIGN_RADIUS) {
                grid.setFixed(centerX + dx, centerY + dy, max(abs(dx), abs(dy)) != 1)
            }
        }
    }

    /** 版本信息(版本 7 起):18 位 BCH,在右上与左下各摆一份,不参与数据掩码。 */
    private fun placeVersion(grid: QrGrid, version: Int) {
        val bits = versionBits(version)
        val anchor = grid.size - VERSION_OFFSET
        for (index in 0 until VERSION_MODULES) {
            val dark = (bits ushr index) and 1 != 0
            val high = index / VERSION_GROUP
            val low = index % VERSION_GROUP
            grid.setFixed(anchor + low, high, dark)
            grid.setFixed(high, anchor + low, dark)
        }
    }

    private fun versionBits(version: Int): Int {
        var remainder = version
        repeat(VERSION_REMAINDER_BITS) {
            val feedback = (remainder ushr (VERSION_REMAINDER_BITS - 1)) and 1
            remainder = (remainder shl 1) xor (feedback * VERSION_GENERATOR)
        }
        return (version shl VERSION_REMAINDER_BITS) or remainder
    }

    private fun formatBits(ecc: QrEcc, mask: Int): Int {
        val data = (ecc.formatBits shl FORMAT_MASK_BITS) or mask
        val shift = FORMAT_MODULES - FORMAT_DATA_BITS
        var remainder = data shl shift
        for (bit in FORMAT_MODULES - 1 downTo shift) {
            if (((remainder ushr bit) and 1) != 0) {
                remainder = remainder xor (FORMAT_GENERATOR shl (bit - shift))
            }
        }
        return ((data shl shift) or remainder) xor FORMAT_XOR
    }
}
