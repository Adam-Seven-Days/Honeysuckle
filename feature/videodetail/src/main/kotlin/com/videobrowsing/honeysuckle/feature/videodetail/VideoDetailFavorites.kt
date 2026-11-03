package com.videobrowsing.honeysuckle.feature.videodetail

import com.videobrowsing.honeysuckle.core.common.HsyClock
import com.videobrowsing.honeysuckle.core.data.favorite.FavoriteRepository
import com.videobrowsing.honeysuckle.core.model.FavoriteFolder
import com.videobrowsing.honeysuckle.core.model.FavoriteItem
import com.videobrowsing.honeysuckle.core.model.VideoDetail

// 收藏这一条稿在本机留下的两笔:面板上的读数,与「我的 → 收藏」那条本地镜像。
// 与 `FavoriteFolderDialog`(那张面板的长相)分文件:那边只画夹列表与勾选,这一头管的是
// "点完之后盘上多哪一行、少哪一行"。

/** 收藏面板的读数:夹列表、这一条稿在哪些夹里,以及归属那一路单独失败时那句话 */
internal fun VideoDetailUiState.withFavoritePanel(
    folders: List<FavoriteFolder>,
    ids: Set<Long>,
    ownershipError: String?,
): VideoDetailUiState = copy(
    favoriteFolders = folders,
    favoriteFolderIds = ids,
    favoriteFoldersLoading = false,
    favoriteFoldersError = ownershipError,
)

/** 收进某个夹时在本机写下这一条:没有网络也能在「我的 → 收藏」里看见刚收藏的稿 */
internal suspend fun FavoriteRepository.recordLocalMirror(detail: VideoDetail, folderTitle: String) {
    recordLocal(
        FavoriteItem(
            bvid = detail.brief.bvid,
            aid = detail.brief.aid,
            title = detail.brief.title,
            coverUrl = detail.brief.coverUrl,
            durationSec = detail.brief.durationSec,
            upName = detail.brief.upName,
            folderId = FavoriteItem.LOCAL_FOLDER_ID,
            folderTitle = folderTitle,
            addedAtEpochSec = HsyClock.nowEpochSec(),
        ),
    )
}

/** 移走之后删掉本机那一行;本地镜像没有平台归属,走 removeItem 的本地分支即可,不用网络 */
internal suspend fun FavoriteRepository.clearLocalMirror(detail: VideoDetail) {
    removeItem(
        FavoriteItem(
            bvid = detail.brief.bvid,
            aid = detail.brief.aid,
            title = detail.brief.title,
            folderId = FavoriteItem.LOCAL_FOLDER_ID,
        ),
    )
}
