package com.videobrowsing.honeysuckle.core.qrcode

private const val BITS_PER_BYTE = 8
private const val BYTE_MASK = 0xFF
private const val HIGH_BIT = 0x80
private const val MODE_BITS = 4
private const val MODE_BYTE = 0b0100
private const val TERMINATOR_BITS = 4
private const val PAD_FIRST = 0xEC
private const val PAD_SECOND = 0x11
private const val GF_SIZE = 0x100
private const val GF_POLY = 0x11D

/**
 * 数据码字这一段:字节模式拼位 → 补终止符与填充字节 → 按块算 RS 纠错 → 交织。
 * 只处理"装得下"的输入,选版在 [QrEncoder] 里先做完。
 */
internal object QrPayload {
    fun capacityBytes(ecc: QrEcc, version: Int): Int {
        val dataBits = QrTables.dataCodewords(ecc, version) * BITS_PER_BYTE
        return (dataBits - MODE_BITS - QrTables.charCountBits(version)) / BITS_PER_BYTE
    }

    fun build(bytes: ByteArray, ecc: QrEcc, version: Int): IntArray {
        val data = encodeData(bytes, ecc, version)
        return interleave(data, ecc, version)
    }

    private fun encodeData(bytes: ByteArray, ecc: QrEcc, version: Int): IntArray {
        val dataCount = QrTables.dataCodewords(ecc, version)
        val out = IntArray(dataCount)
        val writer = BitWriter(out, dataCount * BITS_PER_BYTE)
        writer.put(MODE_BYTE, MODE_BITS)
        writer.put(bytes.size, QrTables.charCountBits(version))
        for (byte in bytes) writer.put(byte.toInt() and BYTE_MASK, BITS_PER_BYTE)
        writer.finish()
        return out
    }

    private fun interleave(data: IntArray, ecc: QrEcc, version: Int): IntArray {
        val blockCount = QrTables.blockCount(ecc, version)
        val eccLength = QrTables.eccPerBlock(ecc, version)
        val rawCodewords = data.size + blockCount * eccLength
        val shortDataLength = rawCodewords / blockCount - eccLength
        val shortBlocks = blockCount - rawCodewords % blockCount
        val dataBlocks = ArrayList<IntArray>(blockCount)
        var offset = 0
        for (index in 0 until blockCount) {
            val length = shortDataLength + if (index < shortBlocks) 0 else 1
            dataBlocks += data.copyOfRange(offset, offset + length)
            offset += length
        }
        val eccBlocks = dataBlocks.map { ecc(it, eccLength) }
        val result = IntArray(rawCodewords)
        var cursor = 0
        for (column in 0..shortDataLength) {
            for (block in dataBlocks) {
                if (column < block.size) result[cursor++] = block[column]
            }
        }
        for (column in 0 until eccLength) {
            for (block in eccBlocks) result[cursor++] = block[column]
        }
        return result
    }

    private fun ecc(data: IntArray, eccLength: Int): IntArray {
        val divisor = divisor(eccLength)
        val remainder = IntArray(eccLength)
        for (value in data) {
            val factor = value xor remainder[0]
            remainder.copyInto(remainder, 0, 1)
            remainder[eccLength - 1] = 0
            for (index in 0 until eccLength) {
                remainder[index] = remainder[index] xor gfMultiply(divisor[index], factor)
            }
        }
        return remainder
    }

    /** 生成多项式 ∏(x − α^i),系数从高到低存放、去掉首项 1。 */
    private fun divisor(degree: Int): IntArray {
        val result = IntArray(degree)
        result[degree - 1] = 1
        var root = 1
        repeat(degree) {
            for (index in 0 until degree) {
                val next = if (index + 1 < degree) result[index + 1] else 0
                result[index] = gfMultiply(result[index], root) xor next
            }
            root = gfMultiply(root, 2)
        }
        return result
    }

    private fun gfMultiply(x: Int, y: Int): Int {
        var left = x
        var right = y
        var product = 0
        repeat(BITS_PER_BYTE) {
            if (right and 1 != 0) product = product xor left
            right = right ushr 1
            left = left shl 1
            if (left and GF_SIZE != 0) left = left xor GF_POLY
        }
        return product and BYTE_MASK
    }
}

/** 逐位写入:高位在前,写满容量就停(选版保证正常内容不会写满)。 */
private class BitWriter(private val bytes: IntArray, private val capacityBits: Int) {
    private var position = 0

    fun put(value: Int, width: Int) {
        for (shift in width - 1 downTo 0) {
            if (position >= capacityBits) return
            if ((value ushr shift) and 1 != 0) {
                val index = position / BITS_PER_BYTE
                bytes[index] = bytes[index] or (HIGH_BIT ushr (position and (BITS_PER_BYTE - 1)))
            }
            position++
        }
    }

    fun finish() {
        position = minOf(position + TERMINATOR_BITS, capacityBits)
        position = (position + BITS_PER_BYTE - 1) / BITS_PER_BYTE * BITS_PER_BYTE
        var useSecond = false
        while (position < capacityBits) {
            put(if (useSecond) PAD_SECOND else PAD_FIRST, BITS_PER_BYTE)
            useSecond = !useSecond
        }
    }
}
