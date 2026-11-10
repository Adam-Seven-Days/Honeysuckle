package com.videobrowsing.honeysuckle.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.videobrowsing.honeysuckle.core.data.update.AppRelease
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.AlertDialog
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.designsystem.ui.TextButton

/**
 * 新版本弹窗:标题报哪一版,正文放作者写的更新说明,右下「去下载」/「以后再说」。
 *
 * 「去下载」只把 Release 网页交给浏览器,应用不经手安装包:不经手就不用申请"安装未知应用"的权限,
 * 也不会出现"这包是谁装的"那种说不清的事。
 *
 * 两条按钮、点遮罩与返回键都走 [onAcknowledged] —— 调用方要记的是"这一版已经跟用户说过了",
 * 与他从哪一条路离开无关。
 */
@Composable
fun AppUpdateDialog(release: AppRelease, onAcknowledged: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onAcknowledged,
        title = { Text("发现新版本 ${release.title}") },
        text = {
            Column(modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                Text(
                    text = release.notes.ifBlank { "暂无更新说明,可到发布页查看" },
                    style = Hsy.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    context.openReleasePage(release.pageUrl)
                    onAcknowledged()
                },
            ) { Text("去下载") }
        },
        dismissButton = {
            TextButton(onClick = onAcknowledged) { Text("以后再说") }
        },
    )
}

/**
 * 把 Release 网页交给浏览器;这台设备上没有能接 https 的应用时,这一次点击就只是关掉弹窗。
 *
 * `FLAG_ACTIVITY_NEW_TASK` 是从 Compose 拿到的上下文起 Activity 的常规写法:它可能是 Activity,
 * 也可能是应用级上下文,带上这一位两种都起得来。
 */
private fun Context.openReleasePage(url: String) {
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
