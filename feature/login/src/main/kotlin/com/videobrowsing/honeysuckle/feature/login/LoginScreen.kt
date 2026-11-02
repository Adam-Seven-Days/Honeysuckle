package com.videobrowsing.honeysuckle.feature.login

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyErrorState
import com.videobrowsing.honeysuckle.core.designsystem.component.HsySectionCard
import com.videobrowsing.honeysuckle.core.designsystem.component.HsySettingRow
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyTopBar
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.AlertDialog
import com.videobrowsing.honeysuckle.core.designsystem.ui.Button
import com.videobrowsing.honeysuckle.core.designsystem.ui.CircularProgressIndicator
import com.videobrowsing.honeysuckle.core.designsystem.ui.OutlinedTextField
import com.videobrowsing.honeysuckle.core.designsystem.ui.SegmentedTab
import com.videobrowsing.honeysuckle.core.designsystem.ui.SegmentedTabs
import com.videobrowsing.honeysuckle.core.designsystem.ui.Surface
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.designsystem.ui.TextButton
import com.videobrowsing.honeysuckle.core.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val QR_RENDER_DP = 240
private val QrShape = RoundedCornerShape(8.dp)

/**
 * 登录页:扫码与账号密码两种方式同一屏切换。
 *
 * 三条屏上口径:
 *  - **二维码在本机画**:平台只给一段扫码 URL,图不经过任何第三方渲染服务,也不回传平台;
 *  - **密码不出本机**:密码框只做掩码,提交时由数据层取平台公钥加密后直连 B 站;
 *  - **过期与失败各说各的**:过期是"等太久了,换一张",失败是"这一趟没走通,原因如下" ——
 *    压成同一句会让用户对着一个其实还能救的状态反复点。
 *
 * [onDone] 由导航层给:登录成功后用户点"完成"回上一页;本屏不自动跳走 ——
 * 自动返回会让"我到底登录上了没有"这个问题失去一个可核对的答案。
 */
@Composable
fun LoginScreen(viewModel: LoginViewModel, onBack: () -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var askSignOut by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Hsy.colors.surface)
            .verticalScroll(rememberScrollState()),
    ) {
        HsyTopBar(title = "登录", onBack = onBack)

        LoginBody(
            state = state,
            onDone = onDone,
            onAction = viewModel::onAction,
            onAskSignOut = { askSignOut = true },
        )
    }

    if (askSignOut) {
        AlertDialog(
            onDismissRequest = { askSignOut = false },
            title = { Text("退出登录?") },
            text = { Text("这台设备上的凭证会被清掉,下一次请求回到游客身份") },
            confirmButton = {
                TextButton(
                    onClick = {
                        askSignOut = false
                        viewModel.onAction(LoginAction.SignOut)
                    },
                ) { Text("退出") }
            },
            dismissButton = {
                TextButton(onClick = { askSignOut = false }) { Text("留着") }
            },
        )
    }
}

/** 五态中屏上要摆什么:成功 / 要码 / 失败 / 过期 / 扫码中各一段,各自说各自的下一步 */
@Composable
private fun LoginBody(
    state: LoginUiState,
    onDone: () -> Unit,
    onAction: (LoginAction) -> Unit,
    onAskSignOut: () -> Unit,
) {
    if (state.profile != null || state.phase == LoginPhase.SUCCESS) {
        LoggedIn(profile = state.profile, onDone = onDone, onAskSignOut = onAskSignOut)
        return
    }

    MethodTabs(
        selected = state.method,
        onSelect = { onAction(LoginAction.SwitchMethod(it)) },
    )

    when {
        state.method == LoginMethod.PASSWORD -> PasswordSection(state = state, onAction = onAction)

        state.phase == LoginPhase.LOADING -> Centered {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Text(
                    text = "正在向平台要一张二维码",
                    style = Hsy.typography.bodyMedium,
                    color = Hsy.colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }

        state.phase == LoginPhase.FAILED -> HsyErrorState(
            message = state.message ?: "这一趟没走通",
            onRetry = { onAction(LoginAction.Reload) },
            hint = "重新生成一张二维码,不会影响已登录的设备",
        )

        state.phase == LoginPhase.EXPIRED -> Expired(
            message = state.message,
            onReload = { onAction(LoginAction.Reload) },
        )

        else -> QrSection(
            phase = state.phase,
            scanUrl = state.session?.scanUrl,
            onReload = { onAction(LoginAction.Reload) },
        )
    }
}

@Composable
private fun MethodTabs(selected: LoginMethod, onSelect: (LoginMethod) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SegmentedTabs {
            SegmentedTab(
                selected = selected == LoginMethod.QR,
                onClick = { onSelect(LoginMethod.QR) },
                label = { Text("扫码登录") },
            )
            SegmentedTab(
                selected = selected == LoginMethod.PASSWORD,
                onClick = { onSelect(LoginMethod.PASSWORD) },
                label = { Text("账号密码") },
            )
        }
    }
}

/**
 * 账号密码表单。
 *
 * 密码框只做掩码,不做任何本地留存:提交时由 `AuthRepository` 取平台公钥加密后直接发出。
 * 平台要求安全验证时(风控/验证码)这里只给一句"换扫码登录",不假装密码一定能登。
 */
@Composable
private fun PasswordSection(state: LoginUiState, onAction: (LoginAction) -> Unit) {
    val canSubmit = state.username.isNotBlank() && state.password.isNotEmpty() && !state.signingIn
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "用 B 站账号登录",
            style = Hsy.typography.titleMedium,
            color = Hsy.colors.onSurface,
        )
        OutlinedTextField(
            value = state.username,
            onValueChange = { onAction(LoginAction.UsernameChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("账号") },
            placeholder = { Text("手机号 / 邮箱 / 用户名") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = { onAction(LoginAction.PasswordChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("密码") },
            placeholder = { Text("登录密码") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onAction(LoginAction.SignIn) }),
        )
        state.message?.let { message ->
            Text(
                text = message,
                style = Hsy.typography.bodySmall,
                color = Hsy.colors.error,
            )
        }
        Button(
            onClick = { onAction(LoginAction.SignIn) },
            modifier = Modifier.fillMaxWidth(),
            enabled = canSubmit,
        ) {
            if (state.signingIn) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp))
            }
            Text(
                text = if (state.signingIn) "正在登录…" else "登录",
                modifier = if (state.signingIn) Modifier.padding(start = 8.dp) else Modifier,
            )
        }
        Text(
            text = "密码在本机用平台公钥加密后直接提交给 B 站,不保存在这个设备上。" +
                "如果平台要求安全验证,请改用扫码登录。",
            style = Hsy.typography.bodySmall,
            color = Hsy.colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun QrSection(phase: LoginPhase, scanUrl: String?, onReload: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "用 B 站手机客户端扫一扫",
            style = Hsy.typography.titleMedium,
            color = Hsy.colors.onSurface,
        )
        Surface(shape = QrShape, color = Color.White) {
            if (scanUrl == null) {
                QrPlaceholder(text = "二维码还没画出来")
            } else {
                QrImage(text = scanUrl, modifier = Modifier.padding(12.dp))
            }
        }
        Text(
            text = when (phase) {
                LoginPhase.SCANNED -> "已扫码,请在手机上点确认"
                else -> "等待扫码"
            },
            style = Hsy.typography.bodyLarge,
            color = Hsy.colors.onSurface,
        )
        Text(
            text = "登录只换取 Cookie · 不接触密码与验证码",
            style = Hsy.typography.bodySmall,
            color = Hsy.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onReload) { Text("换一张二维码") }
    }
}

@Composable
private fun QrImage(text: String, modifier: Modifier = Modifier) {
    val bitmap by produceState<Bitmap?>(initialValue = null, text) {
        value = withContext(Dispatchers.Default) { renderQr(text) }
    }
    val image = bitmap
    if (image == null) {
        QrPlaceholder(text = "二维码没画出来 · 点下面换一张")
    } else {
        Image(
            bitmap = image.asImageBitmap(),
            contentDescription = "登录二维码",
            filterQuality = FilterQuality.None,
            modifier = modifier.size(QR_RENDER_DP.dp),
        )
    }
}

@Composable
private fun QrPlaceholder(text: String) {
    Box(
        modifier = Modifier.size(QR_RENDER_DP.dp).background(Hsy.colors.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = Hsy.typography.bodySmall,
            color = Hsy.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun Expired(message: String?, onReload: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "二维码过期了",
            style = Hsy.typography.titleMedium,
            color = Hsy.colors.onSurface,
        )
        Text(
            text = message ?: "这张码等得太久,平台已经不认它了",
            style = Hsy.typography.bodyMedium,
            color = Hsy.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onReload) { Text("刷新二维码") }
    }
}

@Composable
private fun LoggedIn(profile: UserProfile?, onDone: () -> Unit, onAskSignOut: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        HsySectionCard(title = "账号") {
            HsySettingRow(
                title = profile?.name?.takeIf { it.isNotBlank() } ?: "已登录",
                supporting = profile?.let { "mid ${it.mid}" } ?: "正在读账号资料",
            )
        }
        Text(
            text = "登录成功 · 弹幕、点赞、收藏与关注时间线现在都能用了",
            style = Hsy.typography.bodyMedium,
            color = Hsy.colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            TextButton(onClick = onDone) { Text("完成") }
            TextButton(onClick = onAskSignOut) { Text("退出登录") }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp), contentAlignment = Alignment.Center) {
        content()
    }
}
