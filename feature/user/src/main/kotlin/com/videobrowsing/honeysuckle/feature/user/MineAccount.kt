package com.videobrowsing.honeysuckle.feature.user

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.videobrowsing.honeysuckle.core.designsystem.component.HsySectionCard
import com.videobrowsing.honeysuckle.core.designsystem.icon.HsyIcons
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.AlertDialog
import com.videobrowsing.honeysuckle.core.designsystem.ui.Icon
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.designsystem.ui.TextButton
import com.videobrowsing.honeysuckle.core.model.UserProfile

private val AvatarSize = 48.dp

/** 账号区的四个动作:切换 / 移除 / 添加 / 退出,收成一束免得每层签名都跟着长 */
internal class AccountActions(
    val onSwitch: (Long) -> Unit,
    val onForget: (Long) -> Unit,
    val onAdd: () -> Unit,
    val onSignOut: () -> Unit,
)

/**
 * 账号那一格:游客态点进去是登录页;登录后点开管理弹窗,可切换、添加或移除本机账号。
 *
 * 头像、名字与等级/硬币都来自账号盘里那一份 nav 快照;切换弹窗只列盘上真留着凭证的账号
 * ([MineUiState.accounts]);"添加账号"回登录页,与游客态入口同一条路 —— 不另开一套界面。
 */
@Composable
internal fun AccountSection(
    profile: UserProfile?,
    accounts: List<UserProfile>,
    note: String?,
    actions: AccountActions,
) {
    var managerOpen by remember { mutableStateOf(false) }
    val openManager = { managerOpen = true }
    val onClick = if (profile == null) actions.onAdd else openManager

    HsySectionCard(title = "账号") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AccountAvatar(profile?.avatarUrl)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = profile?.name ?: "游客",
                    style = Hsy.typography.bodyLarge,
                    color = Hsy.colors.onSurface,
                )
                Text(
                    text = if (profile == null) {
                        "点一下登录;不登录也能看推荐、搜视频"
                    } else {
                        profileLine(profile, accounts.size)
                    },
                    style = Hsy.typography.bodyMedium,
                    color = Hsy.colors.onSurfaceVariant,
                )
            }
            TextButton(onClick = onClick) { Text(if (profile == null) "登录" else "管理") }
        }
        if (note != null) {
            Text(
                text = note,
                style = Hsy.typography.bodySmall,
                color = Hsy.colors.accent,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
            )
        }
    }

    if (managerOpen) {
        AccountsDialog(
            current = profile,
            accounts = accounts,
            actions = actions,
            onDismiss = { managerOpen = false },
        )
    }
}

/** 资料里的头像;没有地址(游客 / 旧资料)时画一个人像轮廓,不留空圈 */
@Composable
private fun AccountAvatar(url: String?) {
    Box(
        modifier = Modifier
            .size(AvatarSize)
            .clip(CircleShape)
            .background(Hsy.colors.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrBlank()) {
            Icon(
                imageVector = HsyIcons.person,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = Hsy.colors.primary,
            )
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** 登录后的第二行:等级 / 硬币 / UID / 本机账号数,缺哪一格就不念哪一格 */
private fun profileLine(profile: UserProfile, deviceAccounts: Int): String = listOfNotNull(
    profile.level?.let { "Lv$it" },
    profile.coins?.let { "硬币 $it" },
    "UID ${profile.mid}",
    "本机 $deviceAccounts 个账号".takeIf { deviceAccounts > 1 },
).joinToString(" · ")

@Composable
private fun AccountsDialog(
    current: UserProfile?,
    accounts: List<UserProfile>,
    actions: AccountActions,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("本机账号") },
        text = {
            Column {
                if (accounts.isEmpty()) {
                    Text("还没有保存过其他账号")
                }
                accounts.forEach { account ->
                    AccountRow(
                        account = account,
                        isCurrent = account.mid == current?.mid,
                        onSwitch = {
                            onDismiss()
                            actions.onSwitch(account.mid)
                        },
                        onForget = { actions.onForget(account.mid) },
                    )
                }
                Text(
                    text = "移除只清本机保存的凭证;下一次要用仍需登录一次。",
                    style = Hsy.typography.bodySmall,
                    color = Hsy.colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                actions.onAdd()
            }) { Text("添加账号") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    onDismiss()
                    actions.onSignOut()
                }) { Text("退出当前") }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        },
    )
}

@Composable
private fun AccountRow(account: UserProfile, isCurrent: Boolean, onSwitch: () -> Unit, onForget: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isCurrent, role = Role.Button, onClick = onSwitch)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account.name.ifBlank { "UID ${account.mid}" },
                style = Hsy.typography.bodyLarge,
                color = Hsy.colors.onSurface,
            )
            Text(
                text = if (isCurrent) "当前账号" else "UID ${account.mid}",
                style = Hsy.typography.bodySmall,
                color = Hsy.colors.onSurfaceVariant,
            )
        }
        TextButton(onClick = onForget) { Text("移除") }
    }
}
