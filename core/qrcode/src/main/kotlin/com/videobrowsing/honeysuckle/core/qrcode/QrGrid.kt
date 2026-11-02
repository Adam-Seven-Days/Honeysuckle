package com.videobrowsing.honeysuckle.core.qrcode

/** 模块画布:颜色与"是不是功能图形"分开记,数据落位时只往非功能格子里写。 */
internal class QrGrid(val size: Int) {
    val cells = BooleanArray(size * size)
    private val fixed = BooleanArray(size * size)

    fun isDark(x: Int, y: Int): Boolean = cells[y * size + x]

    fun isFixed(x: Int, y: Int): Boolean = fixed[y * size + x]

    fun setDark(x: Int, y: Int, dark: Boolean) {
        cells[y * size + x] = dark
    }

    fun setFixed(x: Int, y: Int, dark: Boolean) {
        cells[y * size + x] = dark
        fixed[y * size + x] = true
    }

    fun copy(): QrGrid = QrGrid(size).also {
        cells.copyInto(it.cells)
        fixed.copyInto(it.fixed)
    }
}
