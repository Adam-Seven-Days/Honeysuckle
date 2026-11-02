package com.videobrowsing.honeysuckle.feature.videodetail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.videobrowsing.honeysuckle.core.model.VideoDetail

/**
 * 分享出去的是网页版地址:不经过平台接口,游客也能复制、分享。
 *
 * 平台另有 `share/add` 的计数写入,本版不调 —— 分享发生在本机,为一次点击去写平台计数器
 * 没有对用户可见的收益,失败还得多养一条错误文案。
 */
internal fun videoWebUrl(bvid: String): String = "https://www.bilibili.com/video/$bvid"

internal fun copyVideoLink(context: Context, bvid: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("视频链接", videoWebUrl(bvid)))
    Toast.makeText(context, "链接已复制", Toast.LENGTH_SHORT).show()
}

internal fun shareVideo(context: Context, detail: VideoDetail) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, detail.brief.title)
        putExtra(Intent.EXTRA_TEXT, "${detail.brief.title}\n${videoWebUrl(detail.brief.bvid)}")
    }
    context.startActivity(Intent.createChooser(send, "分享视频"))
}
