package com.videobrowsing.honeysuckle.feature.videodetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.EmojiText
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.designsystem.ui.TextButton
import com.videobrowsing.honeysuckle.core.model.Comment
import com.videobrowsing.honeysuckle.core.model.CommentPicture

@Composable
internal fun CommentContent(
    comment: Comment,
    onOpenSpace: (Long) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = Hsy.typography.bodyMedium,
) {
    Column(modifier = modifier) {
        if (comment.content.isNotBlank()) {
            EmojiText(
                text = comment.content,
                emotes = comment.emotes,
                mentions = comment.mentions,
                onMentionClick = onOpenSpace,
                color = Hsy.colors.onSurface,
                style = style,
            )
        }
        if (comment.pictures.isNotEmpty()) {
            CommentPictures(comment.pictures)
        }
    }
}

@Composable
private fun CommentPictures(pictures: List<CommentPicture>) {
    var selectedIndex by remember(pictures) { mutableStateOf<Int?>(null) }
    val single = pictures.size == 1
    val columns = if (single) 1 else PICTURE_COLUMNS
    val width = if (single) Modifier.widthIn(max = 240.dp) else Modifier
    Column(
        modifier = width.fillMaxWidth().padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        pictures.chunked(columns).forEachIndexed { rowIndex, row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEachIndexed { columnIndex, picture ->
                    val index = rowIndex * columns + columnIndex
                    val aspect = if (single) picture.thumbnailAspectRatio() else 1f
                    AsyncImage(
                        model = picture.url,
                        contentDescription = "评论图片 ${index + 1}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(aspect)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Hsy.colors.surfaceVariant)
                            .clickable(role = Role.Button, onClickLabel = "查看大图") { selectedIndex = index },
                    )
                }
                repeat(columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
    selectedIndex?.let { index ->
        CommentPicturePreview(pictures, index, onDismiss = { selectedIndex = null })
    }
}

@Composable
private fun CommentPicturePreview(pictures: List<CommentPicture>, initialIndex: Int, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val pagerState = rememberPagerState(initialPage = initialIndex) { pictures.size }
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                AsyncImage(
                    model = pictures[page].url,
                    contentDescription = "评论图片 ${page + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                )
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            ) { Text("关闭", color = Color.White) }
            Text(
                text = "${pagerState.currentPage + 1}/${pictures.size}",
                color = Color.White,
                style = Hsy.typography.bodySmall,
                modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp),
            )
        }
    }
}

private fun CommentPicture.thumbnailAspectRatio(): Float = if (width > 0 && height > 0) {
    (width.toFloat() / height).coerceIn(MIN_PICTURE_ASPECT, MAX_PICTURE_ASPECT)
} else {
    1f
}

private const val PICTURE_COLUMNS = 3
private const val MIN_PICTURE_ASPECT = 0.5f
private const val MAX_PICTURE_ASPECT = 2f
