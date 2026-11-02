package com.videobrowsing.honeysuckle.core.model

/**
 * 收藏夹里的一条视频(领域模型)。
 *
 * 平台夹与本地夹共用这一个类型:[folderId] 为 [LOCAL_FOLDER_ID] 时来自本地表,
 * 否则来自平台资源列表。两边都只收列表要摆的字段,详情与播放仍走 bvid。
 */
data class FavoriteItem(
    val bvid: String,
    val aid: Long,
    val title: String,
    val coverUrl: String = "",
    val durationSec: Int = 0,
    val upName: String = "",
    val folderId: Long = LOCAL_FOLDER_ID,
    val folderTitle: String = "",
    val addedAtEpochSec: Long = 0,
) {
    val isLocal: Boolean get() = folderId == LOCAL_FOLDER_ID

    companion object {
        /** 本地收藏不是平台夹:id 取 0,与平台夹(>0)永远不撞 */
        const val LOCAL_FOLDER_ID = 0L
    }
}

/** 平台收藏夹的一页资源;本地收藏不分页,由表直接流出 */
data class FavoritePage(
    val items: List<FavoriteItem> = emptyList(),
    val moreAvailable: Boolean = false,
    val total: Int = 0,
)
