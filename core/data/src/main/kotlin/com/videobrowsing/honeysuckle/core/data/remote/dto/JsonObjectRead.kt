package com.videobrowsing.honeysuckle.core.data.remote.dto

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 平台字段容错读取("接口失效以降级呈现,不硬吞")。
 *
 * 约定:缺键或类型不符 → 回退默认值,而不是让整页详情因一个畸形字段炸掉;
 * 真正决定成败的字段(code / data / bvid)由调用方显式判空并归一化为 AppError。
 * 注:平台偶有"数字写成字符串"的下发,故数值读取走 content 文本再转数。
 */
internal fun JsonObject.stringOr(key: String, default: String = ""): String =
    ((this[key] as? JsonPrimitive)?.takeIf { it.isString })?.content ?: default

internal fun JsonObject.longOr(key: String, default: Long = 0L): Long =
    (this[key] as? JsonPrimitive)?.content?.toLongOrNull() ?: default

internal fun JsonObject.intOr(key: String, default: Int = 0): Int =
    (this[key] as? JsonPrimitive)?.content?.toIntOrNull() ?: default

/** 缺键与"平台就是没给"要分开时用这一版:null 不会被 0 冒充 */
internal fun JsonObject.intOrNull(key: String): Int? = (this[key] as? JsonPrimitive)?.content?.toIntOrNull()

internal fun JsonObject.objOr(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.arrOr(key: String): JsonArray? = this[key] as? JsonArray

/** 布尔只认字面 true/false;写成人话("1"/"yes")一律按缺失处理,回退由调用方给 */
internal fun JsonObject.boolOr(key: String, default: Boolean = false): Boolean =
    (this[key] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: default

/**
 * 同一字段的两套键名都读(camelCase 与 snake_case)。
 *
 * 实测(2026-10-30 playurl 的 dash 轨):`baseUrl` 与 `base_url`、`SegmentBase` 与 `segment_base`
 * 是并行下发的,内容一致 —— 只认一套等于把可用性押在平台哪天删掉另一套。
 */
internal fun JsonObject.stringEitherOr(camel: String, snake: String, default: String = ""): String =
    stringOr(camel, stringOr(snake, default))

internal fun JsonObject.arrEitherOr(camel: String, snake: String): JsonArray? = arrOr(camel) ?: arrOr(snake)

/** 字符串数组:非字符串元素(含 null)直接丢掉,不让一条脏元素炸掉整批 */
internal fun JsonArray?.stringsOrEmpty(): List<String> =
    this?.mapNotNull { element -> (element as? JsonPrimitive)?.takeIf { it.isString }?.content }.orEmpty()
