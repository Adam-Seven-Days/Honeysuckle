package com.videobrowsing.honeysuckle.core.qrcode

/** 选版 → 数据码字 → 纠错交织 → 画固定图形 → 挑掩码,一条线走完。 */
internal object QrEncoder {
    fun encode(text: String, ecc: QrEcc): QrMatrix {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val version = chooseVersion(bytes.size, ecc)
        val codewords = QrPayload.build(bytes, ecc, version)
        val grid = QrGrid(version * QR_MODULES_PER_VERSION + QR_BASE_SIZE)
        QrPatterns.drawFixed(grid, version)
        val drawn = QrMasks.choose(grid, ecc, codewords)
        return QrMatrix(version, ecc, drawn.mask, drawn.cells)
    }

    private fun chooseVersion(byteCount: Int, ecc: QrEcc): Int {
        for (version in QrTables.MIN_VERSION..QrTables.MAX_VERSION) {
            if (byteCount <= QrPayload.capacityBytes(ecc, version)) return version
        }
        val limit = QrPayload.capacityBytes(ecc, QrTables.MAX_VERSION)
        throw IllegalArgumentException("内容 $byteCount 字节,超过 ${ecc.name} 档版本 40 的 $limit 字节上限")
    }
}
