package com.videobrowsing.honeysuckle.core.data.user

import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.data.remote.UserSpaceApi
import com.videobrowsing.honeysuckle.core.model.UserSpace
import com.videobrowsing.honeysuckle.core.model.UserVideoPage

/**
 * UP 主空间的读口。
 *
 * 名片与投稿分开:头部先到就先摆,投稿失败只让"稿件"那一段降级,
 * 不把整页判成"空间打不开"。两者都不需要登录态。
 */
interface UserSpaceRepository {

    suspend fun space(mid: Long): HsyResult<UserSpace>

    suspend fun videos(mid: Long, pageIndex: Int = UserSpaceApi.FIRST_PAGE): HsyResult<UserVideoPage>
}
