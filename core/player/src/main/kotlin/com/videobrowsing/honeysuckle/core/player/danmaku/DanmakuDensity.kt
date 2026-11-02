package com.videobrowsing.honeysuckle.core.player.danmaku

// 档位数值集中在这里,detekt MagicNumber 不许把裸数字写进枚举构造(本工程口径:能改代码就改代码)
private const val PER_SEC_LOW = 1f
private const val PER_SEC_STANDARD = 3f
private const val PER_SEC_HIGH = 6f

/**
 * 弹幕密度档位:用户能调的那一档 = **每"视频秒"放行几条**,不是同屏条数。
 *
 * 钉在视频时间轴而不是墙上时钟,于是倍速时密度随画面走、seek 回退可复现、
 * 且整道闸门是"合并后时间轴上的纯函数",与播放器状态无关。
 * 同屏条数走另一条线:内核 `DanmakuBudget` 按几何算的性能护栏,不暴露给用户。
 *
 * **数值是工程初值**:官方客户端那一档的实际语义未实测,
 * 校准要在屏上验过手感之后动,不要在 JVM 里猜。改数值不动 [storageId],盘上才读得回老设置。
 *
 * [storageId] 是这一档落到设置盘上的英文名(持久化口径:值可重定标,词汇表是磁盘契约)。
 * [displayName] 是用户在这一档上看到的中文叫法:它和 [storageId] 一起构成"这一档是谁",
 * 谁要摆档位选择器就引这两个数,不许再在自己屏上抄一份对照表(设置页与播放页抄过两次,漂移过一次)。
 */
enum class DanmakuDensity(val storageId: String, val displayName: String, val entriesPerVideoSec: Float) {
    /** 低端机/省电 */
    LOW("low", "少", PER_SEC_LOW),

    /** 适中档 */
    STANDARD("standard", "标准", PER_SEC_STANDARD),

    /** 热闹片段 */
    HIGH("high", "多", PER_SEC_HIGH),

    /**
     * 一条不挡,全交给同屏护栏去兜;也是没设过/旧档名读不回来时落的那一档。
     *
     * [entriesPerVideoSec] 这里取 `+∞`:磁盘上落的是 `"unlimited"` 这个英文名而不是这个数,
     * 所以它不经过任何序列化 —— 早前"用 ∞ 当盘上取值"的那条编码风险,随英文 id 口径一并消掉了。
     */
    UNLIMITED("unlimited", "不限", Float.POSITIVE_INFINITY),
    ;

    companion object {

        /** 默认档:不限。没设过时把弹幕全放行,由同屏护栏兜底 */
        val DEFAULT = UNLIMITED

        /**
         * 盘上的英文名 → 档位。认不出来(旧版本残留、手改、档位改名)一律退 [DEFAULT]。
         *
         * 退默认而不是抛:这一份值直接决定播放页的闸门,而"设置读不出来"不该变成"播放页起不来"
         * (与 `core:datastore` 读盘 IOException 退整份默认值同口径)。
         */
        fun fromStorageId(id: String): DanmakuDensity = entries.firstOrNull { it.storageId == id } ?: DEFAULT
    }
}
