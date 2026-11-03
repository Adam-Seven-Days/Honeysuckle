package com.videobrowsing.honeysuckle.core.model

/** 低于这个高度就画不出"多少 P",与屏上画质清单同一口径([PlaybackTrackSelector.qualities]) */
private const val MIN_VALID_HEIGHT = 1

// 档位高度集中在这里,detekt MagicNumber 不许把裸数字写进枚举构造(与 DanmakuDensity 同一条口径)
private const val HEIGHT_FULL_HD = 1080
private const val HEIGHT_HD = 720
private const val HEIGHT_SD = 480

/**
 * 清晰度档位:用户在设置里能挑的那一档(播放默认档与下载默认档共用这一张表)。
 *
 * **这里不维护 qn 码表。** 平台同一档会并排下发几条码率、番剧与实拍给的档号也不完全一样,
 * 而"这一档是多少 P"只有轨自己标的高度靠得住 —— 所以每一档只钉一个高度,
 * 落到具体档号由 [resolve] 在那支视频**实际下发的候选轨**里问出来。
 *
 * 与其余档位同一持久化口径:[storageId] 是盘上的英文名,[displayName] 是屏上的中文,
 * 对照表只在这一处;设置页、播放页与下载都引这两个数。改数值不动 [storageId],盘上才读得回老设置。
 */
enum class QualityTier(val storageId: String, val displayName: String, private val heightPx: Int) {
    /** 不指定:按平台默认编码收口后取候选里最高的一档 */
    AUTO("auto", "自动", 0),

    FULL_HD("1080p", "1080P", HEIGHT_FULL_HD),
    HD("720p", "720P", HEIGHT_HD),
    SD("480p", "480P", HEIGHT_SD),
    ;

    /**
     * 在那支视频实际下发的候选轨里取"不高于本档的最高一档",问不出来就交回自动档。
     *
     * 用户要 1080P 而这片只有 720P,给他 720P 才是本意;反过来这片最低就是 1080P 而用户要 480P,
     * 这里退回 [PlaybackTrackSelector.AUTO_QUALITY] 而不是硬挑一条 —— 宁可按自动播,
     * 也不悄悄给他一档比所要的还高的画质。
     */
    fun resolve(playback: Playback): Int {
        if (this == AUTO) return PlaybackTrackSelector.AUTO_QUALITY
        val best = playback.videoTracks
            .filter {
                it.qualityId != PlaybackTrackSelector.AUTO_QUALITY && it.heightPx in MIN_VALID_HEIGHT..heightPx
            }
            .maxByOrNull { it.heightPx }
        return best?.qualityId ?: PlaybackTrackSelector.AUTO_QUALITY
    }

    companion object {

        /** 默认档:自动。没设过与旧档名读不回来都落这里 */
        val DEFAULT = AUTO

        /** 认不出的存档退默认,和弹幕、画中画那几档同一条口径 */
        fun fromStorageId(id: String): QualityTier = entries.firstOrNull { it.storageId == id } ?: DEFAULT
    }
}

/**
 * 下载档:比 [QualityTier] 多一格"跟随当前播放档"。
 *
 * 用户按下"下载"时心里想的多半是"就照我现在看的这档存下来",所以这一格是默认;
 * 想要固定档位(省流量或存高画质)才挑具体那一档。[tier] 为 null 就是"跟随",
 * 换算只在这一处说清,调用方不再各自判一次"follow 是什么意思"。
 */
enum class DownloadQuality(val storageId: String, val displayName: String, internal val tier: QualityTier?) {
    FOLLOW_PLAYBACK("follow", "跟随当前播放档", null),
    AUTO(QualityTier.AUTO.storageId, QualityTier.AUTO.displayName, QualityTier.AUTO),
    FULL_HD(QualityTier.FULL_HD.storageId, QualityTier.FULL_HD.displayName, QualityTier.FULL_HD),
    HD(QualityTier.HD.storageId, QualityTier.HD.displayName, QualityTier.HD),
    SD(QualityTier.SD.storageId, QualityTier.SD.displayName, QualityTier.SD),
    ;

    /**
     * 这一档在本次下载里该挑哪个平台档号。
     *
     * [currentQualityId] 是播放页此刻选中的那一档(自动档就是 [PlaybackTrackSelector.AUTO_QUALITY]);
     * 指定档位则在那支视频的实际候选里换算,挑不出来交回自动 —— 下载比播放更不能失败,
     * 差一档画质远比"这一 P 没下成"好受。
     */
    fun resolve(currentQualityId: Int, playback: Playback): Int = tier?.resolve(playback) ?: currentQualityId

    companion object {

        /** 默认档:跟随播放页当下那一档 */
        val DEFAULT = FOLLOW_PLAYBACK

        fun fromStorageId(id: String): DownloadQuality = entries.firstOrNull { it.storageId == id } ?: DEFAULT
    }
}

/**
 * 首页排布:双列瀑布格与单列长条。
 *
 * [TWO_COLUMN] 是 Bento 那一版(首条通栏、其余两列);[SINGLE_COLUMN] 给只想一条一条看的人。
 * 这一档只管"一行摆几张卡",卡片本身的长相两档共用。
 */
enum class HomeLayout(val storageId: String, val displayName: String) {
    TWO_COLUMN("two_column", "双列"),
    SINGLE_COLUMN("single_column", "单列"),
    ;

    companion object {
        val DEFAULT = TWO_COLUMN

        fun fromStorageId(id: String): HomeLayout = entries.firstOrNull { it.storageId == id } ?: DEFAULT
    }
}
