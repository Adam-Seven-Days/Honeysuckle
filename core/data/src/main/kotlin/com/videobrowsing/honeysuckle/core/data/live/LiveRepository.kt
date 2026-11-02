package com.videobrowsing.honeysuckle.core.data.live

import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.model.LivePage
import com.videobrowsing.honeysuckle.core.model.LivePlay
import com.videobrowsing.honeysuckle.core.model.LiveRoomInfo

/** 直播的三条读口:首页一屏、某个房间的取流与房间状态 */
interface LiveRepository {

    suspend fun home(): HsyResult<LivePage>

    suspend fun room(roomId: Long): HsyResult<LiveRoomInfo>

    suspend fun play(roomId: Long): HsyResult<LivePlay>
}
