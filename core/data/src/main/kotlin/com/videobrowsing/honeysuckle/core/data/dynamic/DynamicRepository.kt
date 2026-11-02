package com.videobrowsing.honeysuckle.core.data.dynamic

import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.model.DynamicPage

/** 动态时间线的读口;这一端要登录,游客态由调用方先拦(平台回 -101) */
interface DynamicRepository {

    suspend fun page(offset: String = "", pageIndex: Int = 1): HsyResult<DynamicPage>
}
