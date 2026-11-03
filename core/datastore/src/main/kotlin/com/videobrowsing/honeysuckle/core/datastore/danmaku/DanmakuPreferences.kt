package com.videobrowsing.honeysuckle.core.datastore.danmaku

import com.videobrowsing.honeysuckle.core.datastore.MAX_STORAGE_ID_LENGTH
import com.videobrowsing.honeysuckle.core.datastore.TIER_ID_UNSET

/**
 * 弹幕偏好快照(设置页与播放页共用的那一份)。
 *
 * 可调项全是原始类型:三个数值 + 四个开关(总开关与滚动/顶部/底部三格) + 一份词表 +
 * **三个档位的英文名**(密度、限帧、速度)。
 * 档位不落枚举(本模块也不依赖 `core:player`),落的是 `"standard"` 这类英文 id:
 *  - 盘上写的是用户认得的那个名字,不是"3 条每秒"这种会随重定标改变的工程值;
 *  - id → 档位的翻译只在 `core:player` 一处(`DanmakuDensity.fromStorageId` / `DanmakuFramePace.fromStorageId`),
 *    本模块只负责"用户说过哪一档",不认识档位是什么。
 * [DEFAULT_DENSITY_ID] 取空串 = **"没设过"**,它不是词表里的一个档名,读侧因此一律落回默认档;
 * 这么定是为了让 `"standard"` 这个字面量只存在于 `core:player` 一处。
 *
 * 滚动区域默认 1f;字体大小默认取 15,比平台下发的 25 号基准小一档 ——
 * 换算倍率由宿主按 `用户值 / 平台基准` 算,这里不引用渲染内核的常量,免得存储层反向依赖它。
 */
data class DanmakuPreferences(
    val enabled: Boolean = DEFAULT_ENABLED,
    val opacity: Float = DEFAULT_OPACITY,
    /** 字体大小:直接存用户认得的那个整数,不再经倍率换算 */
    val fontSize: Int = DEFAULT_FONT_SIZE,
    val scrollAreaFraction: Float = DEFAULT_SCROLL_AREA_FRACTION,
    /** 密度档的英文名,口径见类注释;`""` = 没设过 */
    val densityId: String = DEFAULT_DENSITY_ID,
    /** 限帧档的英文名,口径同 [densityId] */
    val framePaceId: String = DEFAULT_FRAME_PACE_ID,
    /** 速度档的英文名(`slow` / `standard` / `fast`),口径同 [densityId] */
    val speedId: String = DEFAULT_SPEED_ID,
    /**
     * 三种弹幕类型各自画不画(设置 → 弹幕 → 类型)。
     *
     * 三把键分开而不是一个"只显示滚动":顶部与底部那两条是定住的,有人只想收掉它们、
     * 保留滚动的热闹;合成一档就只剩"全画"和"全不画"两个选择。
     * mode7 的关键帧弹幕不在这三格管辖里 —— 它是内容本身,不是排版方式。
     */
    val scrollVisible: Boolean = DEFAULT_SCROLL_VISIBLE,
    val topVisible: Boolean = DEFAULT_TOP_VISIBLE,
    val bottomVisible: Boolean = DEFAULT_BOTTOM_VISIBLE,
    /** 屏蔽词原样(清洗后的集合,见仓库侧的 sanitize);词 → 规则的翻译归 `core:player` */
    val blockedWords: Set<String> = DEFAULT_BLOCKED_WORDS,
) {
    /** 关掉弹幕 = 透明度钉成 0:宿主拿到 0f 一帧都不走,是"关闭"的零开销路径 */
    val effectiveOpacity: Float
        get() = if (enabled) opacity else 0f

    companion object {
        const val DEFAULT_ENABLED = true
        const val DEFAULT_OPACITY = 1f
        const val DEFAULT_FONT_SIZE = 15
        const val DEFAULT_SCROLL_AREA_FRACTION = 1f
        val DEFAULT_BLOCKED_WORDS: Set<String> = emptySet()

        /** 盘上"没设过"的取值是空串(定义在 `TIER_ID_UNSET`,两块盘共用一个),它不是词表里的一个档名 */
        const val DEFAULT_DENSITY_ID = TIER_ID_UNSET
        const val DEFAULT_FRAME_PACE_ID = TIER_ID_UNSET
        const val DEFAULT_SPEED_ID = TIER_ID_UNSET

        /** 三种类型默认全画:关掉某一格是用户的决定,不是我们在第一帧替他决定 */
        const val DEFAULT_SCROLL_VISIBLE = true
        const val DEFAULT_TOP_VISIBLE = true
        const val DEFAULT_BOTTOM_VISIBLE = true

        /** 可用区间:写与读都按这组数夹。UI 滑条自己的下限(例如不许拖到全透明)是另一回事,不混在这里 */
        const val OPACITY_MIN = 0f
        const val OPACITY_MAX = 1f
        const val FONT_SIZE_MIN = 12
        const val FONT_SIZE_MAX = 50
        const val AREA_FRACTION_MIN = 0.3f
        const val AREA_FRACTION_MAX = 1f

        /**
         * 屏蔽词条数上限。给个数是因为它直接决定 automaton 的节点数,而这条链跑在播放页:
         * 无上限的输入等于把一份用户手打的清单原样喂进每帧的判定路径。
         * 满了以后**丢最老的那批**,留最新 —— 用户刚敲进去的词不该因为前面挤满了而无声失效。
         */
        const val MAX_BLOCKED_WORDS = 200

        /**
         * 档位名的长度上限。给个数是因为盘上的这一段是用户可写的边界(手改、旧版本残留都可能),
         * 而超长的一串压根对不上任何档名 —— 交给读侧退默认档之前,先别让它占着盘。
         * 数值与"应用偏好"那块盘共用一处口径,见 `StorageLimits`。
         */
        const val MAX_TIER_ID_LENGTH = MAX_STORAGE_ID_LENGTH
    }
}
