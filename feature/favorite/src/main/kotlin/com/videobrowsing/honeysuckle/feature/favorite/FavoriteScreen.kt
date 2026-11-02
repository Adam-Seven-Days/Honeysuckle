package com.videobrowsing.honeysuckle.feature.favorite

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.model.FavoriteItem

/**
 * 收藏管理页(收藏夹管理)。
 *
 * 两段并排:**本地收藏**断网与游客态都看得见;平台收藏夹要有登录态才列得出来。
 * 夹内列表是同一屏的第二种形态(不是另一个路由),因为"返回"在这里的含义是"退回夹列表",
 * 与从本页退回上级不是一回事 —— 两种动作各占一个 BackHandler 分支。
 */
@Composable
fun FavoriteScreen(
    viewModel: FavoriteViewModel,
    onBack: () -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<FavoriteDialog?>(null) }
    var moving by remember { mutableStateOf<FavoriteItem?>(null) }
    val goBack: () -> Unit = {
        if (state.selected != null) viewModel.onAction(FavoriteAction.CloseFolder) else onBack()
    }

    BackHandler(onBack = goBack)

    Column(modifier = modifier.fillMaxSize().background(Hsy.colors.surface)) {
        FavoriteTopBar(
            state = state,
            onBack = goBack,
            onOpenDialog = { dialog = it },
        )

        state.message?.let { message ->
            Text(
                text = message,
                style = Hsy.typography.bodySmall,
                color = Hsy.colors.accent,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        if (state.selected == null) {
            FolderList(
                state = state,
                onAction = viewModel::onAction,
                onOpenVideo = onOpenVideo,
                onOpenLogin = onOpenLogin,
                modifier = Modifier.weight(1f),
            )
        } else {
            FolderContent(
                state = state,
                onAction = viewModel::onAction,
                onOpenVideo = onOpenVideo,
                onMove = { moving = it },
                modifier = Modifier.weight(1f),
            )
        }
    }

    FavoriteDialogs(
        dialog = dialog,
        state = state,
        onDismiss = { dialog = null },
        onAction = viewModel::onAction,
    )
    MoveDialog(
        item = moving,
        folders = state.folders,
        onDismiss = { moving = null },
        onPick = { item, target ->
            moving = null
            viewModel.onAction(FavoriteAction.MoveItem(item, target))
        },
    )
}
