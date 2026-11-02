package com.videobrowsing.honeysuckle.core.danmaku.proto

/** 报文畸形:截断、长度越界或线型不支持。错误归一化(→ AppError.Decode)发生在上层 core:network */
class DanmakuProtoException(message: String) : Exception(message)

/**
 * 最小 protobuf wire 读取器 —— 采用的方案 B(手写解码,不引 protoc/生成代码)。
 *
 * 选它的理由:弹幕分片字段极少,而手写可以把 `core:danmaku` 保持在纯 Kotlin、零依赖、零构建插件的状态,
 * 且未知字段按规范 skip —— 平台加字段不会把解码器炸掉。
 * 热路径上不做对象包装:tag 以 Long 返回给调用方自己拆位。
 */
internal class PbReader(private val bytes: ByteArray) {

    private var pos = 0

    fun atEnd(): Boolean = pos >= bytes.size

    /** varint:int32 / int64 / uint32 / bool 与 tag 都用它 */
    fun readVarint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            val b = nextByte()
            result = result or ((b and VARINT_PAYLOAD_MASK).toLong() shl shift)
            if (b and VARINT_CONTINUATION_BIT == 0) return result
            shift += VARINT_GROUP_BITS
            if (shift >= Long.SIZE_BITS) throw DanmakuProtoException("varint 超过 10 字节,非法编码")
        }
    }

    /** 4 字节定长(线型 5) */
    @Suppress("MagicNumber") // 小端字节拼接的位移量就是 protobuf 格式定义,命名它们只会更难读
    fun readFixed32(): Int {
        val b0 = nextByte()
        val b1 = nextByte()
        val b2 = nextByte()
        val b3 = nextByte()
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    /** 8 字节定长(线型 1) */
    @Suppress("MagicNumber") // 同上:两个 32 位半字拼一个 64 位小端字
    fun readFixed64(): Long {
        val lo = readFixed32().toLong() and 0xFFFFFFFFL
        val hi = readFixed32().toLong() and 0xFFFFFFFFL
        return lo or (hi shl 32)
    }

    /** 长度前缀字段(线型 2):string / bytes / 嵌套 message */
    fun readLengthDelimited(): ByteArray {
        val declared = readVarint()
        if (declared < 0 || declared > MAX_PAYLOAD_BYTES) {
            throw DanmakuProtoException("长度前缀非法:$declared(上限 $MAX_PAYLOAD_BYTES 字节)")
        }
        val len = declared.toInt()
        if (len > bytes.size - pos) {
            throw DanmakuProtoException("报文截断:字段声明 $len 字节,实际只剩 ${bytes.size - pos}")
        }
        val out = bytes.copyOfRange(pos, pos + len)
        pos += len
        return out
    }

    fun readString(): String = String(readLengthDelimited(), Charsets.UTF_8)

    /** 跳过不认识或不需要的字段:前向兼容的唯一保障 */
    fun skip(wireType: Int) {
        when (wireType) {
            WIRE_VARINT -> readVarint()
            WIRE_FIXED64 -> readFixed64()
            WIRE_LENGTH_DELIMITED -> readLengthDelimited()
            WIRE_FIXED32 -> readFixed32()
            else -> throw DanmakuProtoException("不支持的线型 $wireType(group 已废弃,本仓不解析)")
        }
    }

    private fun nextByte(): Int {
        if (pos >= bytes.size) throw DanmakuProtoException("报文截断:偏移 $pos 已到末尾")
        return bytes[pos++].toInt() and BYTE_MASK
    }

    companion object {
        const val WIRE_VARINT = 0
        const val WIRE_FIXED64 = 1
        const val WIRE_LENGTH_DELIMITED = 2
        const val WIRE_FIXED32 = 5

        /** tag 的高位段是字段号,低 3 位是线型;两者拆分是解码器共用口径,故在此导出 */
        const val TAG_FIELD_SHIFT = 3

        // tag 是 Long,而 and/or 没有 Long×Int 重载:整型字面量能靠期望类型自适应,const val 不能,故显式写 0x7L
        const val TAG_WIRE_MASK = 0x7L

        private const val VARINT_PAYLOAD_MASK = 0x7F
        private const val VARINT_CONTINUATION_BIT = 0x80
        private const val VARINT_GROUP_BITS = 7
        private const val BYTE_MASK = 0xFF

        /** 单个长度前缀字段的天花板:弹幕分片远小于此,越过即视为畸形输入而不是"再读点试试" */
        private const val MAX_PAYLOAD_BYTES = 8L * 1024 * 1024
    }
}
