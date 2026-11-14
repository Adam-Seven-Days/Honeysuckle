package com.videobrowsing.honeysuckle.core.model

/**
 * 收藏夹里的一条视频(领域模型)。
 *
 * 只有平台那一份:[folderId] 是它所在的收藏夹,本机不留副本 —— 这一条从平台读来,
 * 移走也直接写回平台。字段只收列表要摆的那几个,详情与播放仍走 bvid。
 */
data class FavoriteItem(
    val bvid: String,
    val aid: Long,
    val title: String,
    val coverUrl: String = "",
    val durationSec: Int = 0,
    val upName: String = "",
    val folderId: Long,
    val folderTitle: String = "",
    val addedAtEpochSec: Long = 0,
    val accessType: VideoAccessType = VideoAccessType.FREE,
)

/** 收藏夹里的一页资源 */
data class FavoritePage(
    val items: List<FavoriteItem> = emptyList(),
    val moreAvailable: Boolean = false,
    val total: Int = 0,
)

/**
 * 夹内列表的排序档。
 *
 * 排序交给平台做:只在本地排当前这一页会把"翻页后的顺序"打乱,而用户看到的是一整夹。
 * [storageId] 是平台 `order` 的取值;空串表示不传,让平台按默认(最近收藏)给。
 */
enum class FavoriteSort(val storageId: String) {
    RECENT("mtime"),
    MOST_PLAYED("view"),
    ;

    companion object {
        val DEFAULT: FavoriteSort = RECENT
    }
}
