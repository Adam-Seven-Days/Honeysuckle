package com.videobrowsing.honeysuckle.core.data.message

import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.model.MessageKind
import com.videobrowsing.honeysuckle.core.model.MessagePage
import com.videobrowsing.honeysuckle.core.model.UnreadCounts

/**
 * 消息中心的读口。
 *
 * 只读不写:这一轮要的是"看得见有人回了我",标记已读 / 回复都不在这一屏做 ——
 * 平台那两条写口各有自己的 csrf 与风控口径,没有真账号核对之前不凭空接。
 */
interface MessageRepository {

    suspend fun unread(): HsyResult<UnreadCounts>

    suspend fun page(kind: MessageKind, pageIndex: Int = 1): HsyResult<MessagePage>
}
