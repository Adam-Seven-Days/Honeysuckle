package com.videobrowsing.honeysuckle.core.data.relation

import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.model.RelationState

/** 关注关系的读写口;读在游客态直接回默认值,写必须登录 */
interface RelationRepository {

    suspend fun state(mid: Long): HsyResult<RelationState>

    suspend fun setFollowing(mid: Long, following: Boolean): HsyResult<Unit>
}
