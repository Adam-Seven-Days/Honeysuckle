package com.videobrowsing.honeysuckle.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.videobrowsing.honeysuckle.core.data.crash.CrashRedactor
import com.videobrowsing.honeysuckle.core.designsystem.component.HsySectionCard
import com.videobrowsing.honeysuckle.core.designsystem.component.HsySettingRow
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyTopBar
import com.videobrowsing.honeysuckle.core.designsystem.icon.HsyIcons
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.AlertDialog
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.designsystem.ui.TextButton

/**
 * 关于页(那条"README 明示免责与仅供学习"在 app 内的同一份口径)。
 *
 * 这一页只放**已经是事实**的话:隐私与免责、常见问题各一张卡,内容与 `docs/` 同源;
 * 崩溃记录读的是本机 `CrashStore`,可复制、可清除 —— Beta 里没有服务端,导出只能靠用户自己带走。
 * 「检查更新」在这一页是有据可依的:更新源就是仓库的 Release 页,点了真能问出结果,
 * 结果要么是一句实话,要么是一张能跳去下载的弹窗,不是一个打不开下一屏的空入口。
 */
@Composable
fun AboutScreen(viewModel: AboutViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Hsy.colors.surface)
            .verticalScroll(rememberScrollState()),
    ) {
        HsyTopBar(title = "关于", onBack = onBack)

        Text(
            text = "金银花 Honeysuckle",
            style = Hsy.typography.headlineSmall,
            color = Hsy.colors.onSurface,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 2.dp),
        )
        Text(
            text = "版本 ${state.versionName}",
            style = Hsy.typography.bodyMedium,
            color = Hsy.colors.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, bottom = 12.dp),
        )
        state.message?.let { message ->
            Text(
                text = message,
                style = Hsy.typography.bodyMedium,
                color = Hsy.colors.accent,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        AboutSections(state = state, onAction = viewModel::onAction)

        AboutDialogs(state = state, onAction = viewModel::onAction)

        // 手动查出来的新版本:与冷启动那一张是同一个弹窗、同一份文案口径
        state.updateRelease?.let { release ->
            AppUpdateDialog(release = release, onAcknowledged = { viewModel.onAction(AboutAction.DismissUpdate) })
        }
    }
}

@Composable
private fun AboutSections(state: AboutUiState, onAction: (AboutAction) -> Unit) {
    HsySectionCard(title = "这是什么") {
        HsySettingRow(
            title = "非官方客户端",
            icon = HsyIcons.info,
            supporting = "与 bilibili 无隶属关系,登录后可使用评论、弹幕与收藏",
        )
        HsySettingRow(
            title = "只做浏览与本地过滤",
            supporting = "弹幕与显示设置仅保存在本机,不会上传",
        )
        HsySettingRow(
            title = "仅供学习与自用",
            supporting = "不上架、不商用。第三方客户端与平台用户协议可能冲突,风险由使用者自担",
        )
    }

    UpdateCard(state, onAction)

    HsySectionCard(title = "隐私与数据") {
        HsySettingRow(
            title = "隐私与免责说明",
            icon = HsyIcons.info,
            supporting = "查看隐私政策与数据管理入口",
            onClick = { onAction(AboutAction.Open(AboutDialog.PRIVACY)) },
        )
        HsySettingRow(
            title = "常见问题",
            icon = HsyIcons.refresh,
            supporting = "登录、清晰度、发布失败、缓存清理",
            onClick = { onAction(AboutAction.Open(AboutDialog.FAQ)) },
        )
        HsySettingRow(
            title = "上次崩溃记录",
            icon = HsyIcons.warning,
            supporting = if (state.crashLog == null) "本机没有记录" else "查看、复制或清除",
            onClick = if (state.crashLog == null) {
                null
            } else {
                { onAction(AboutAction.Open(AboutDialog.CRASH)) }
            },
        )
    }

    HsySectionCard(title = "许可") {
        HsySettingRow(
            title = "本项目 MIT",
            supporting = "仓库内 LICENSE 全文可查",
        )
        HsySettingRow(
            title = "第三方依赖与许可",
            supporting = "Media3 / OkHttp / Room / DataStore / Coil 等,查看完整清单",
            onClick = { onAction(AboutAction.Open(AboutDialog.LICENSES)) },
        )
    }
}

/**
 * 「更新」那一格:这一页里唯一一条会自己改字的行 —— 问出去的那一段时间它得看着像在进行,
 * 而且不能再点(连点三下等于发三条同样的请求)。
 */
@Composable
private fun UpdateCard(state: AboutUiState, onAction: (AboutAction) -> Unit) {
    HsySectionCard(title = "更新") {
        HsySettingRow(
            title = if (state.updateChecking) "正在检查更新" else "检查更新",
            icon = HsyIcons.refresh,
            supporting = "检查最新版本,下载将在浏览器中打开",
            onClick = if (state.updateChecking) {
                null
            } else {
                { onAction(AboutAction.CheckUpdate) }
            },
        )
    }
}

@Composable
private fun AboutDialogs(state: AboutUiState, onAction: (AboutAction) -> Unit) {
    val context = LocalContext.current
    when (state.dialog) {
        AboutDialog.PRIVACY -> TextDialog(
            title = "隐私与免责说明",
            text = PRIVACY_TEXT,
            onDismiss = { onAction(AboutAction.CloseDialog) },
        )

        AboutDialog.FAQ -> TextDialog(
            title = "常见问题",
            text = FAQ_TEXT,
            onDismiss = { onAction(AboutAction.CloseDialog) },
        )

        AboutDialog.LICENSES -> TextDialog(
            title = "第三方许可",
            text = LICENSES_TEXT,
            onDismiss = { onAction(AboutAction.CloseDialog) },
        )

        AboutDialog.CRASH -> CrashDialog(
            log = state.crashLog,
            onCopy = {
                val log = state.crashLog ?: return@CrashDialog
                // 读取端已脱敏,这里再兜一次:剪贴板是文本离开本机最直接的一条路
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard?.setPrimaryClip(ClipData.newPlainText("金银花崩溃记录", CrashRedactor.redact(log)))
                onAction(AboutAction.CrashCopied)
            },
            onClear = { onAction(AboutAction.ClearCrash) },
            onDismiss = { onAction(AboutAction.CloseDialog) },
        )

        null -> Unit
    }
}

@Composable
private fun TextDialog(title: String, text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(text = text, style = Hsy.typography.bodyMedium)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } },
    )
}

@Composable
private fun CrashDialog(log: String?, onCopy: () -> Unit, onClear: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("上次崩溃记录") },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(
                    text = log ?: "记录已清除",
                    style = Hsy.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            if (log != null) {
                TextButton(onClick = onCopy) { Text("复制") }
            }
        },
        dismissButton = {
            TextButton(onClick = onClear) { Text("清除") }
        },
    )
}
