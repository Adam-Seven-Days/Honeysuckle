package com.videobrowsing.honeysuckle.feature.dynamic

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.videobrowsing.honeysuckle.core.common.HsyClock
import com.videobrowsing.honeysuckle.core.common.formatRelativeTime
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyErrorState
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyTopBar
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.CircularProgressIndicator
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.model.Article

/**
 * 专栏阅读页。
 *
 * 本仓没有 WebView,正文在数据层已经去标签、拆成段落,这里只负责"一篇长文怎么读":
 * 封面、标题、作者、段落,段落之间留出比行距更大的一档空白。
 */
@Composable
fun ArticleScreen(viewModel: ArticleViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(modifier = modifier.fillMaxSize().background(Hsy.colors.background)) {
        HsyTopBar(title = "专栏", onBack = onBack)
        when {
            state.article == null && state.loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.article == null -> HsyErrorState(
                message = state.error ?: "专栏加载失败",
                onRetry = viewModel::reload,
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                hint = "可能是风控或原文已删除",
            )

            else -> ArticleContent(article = checkNotNull(state.article))
        }
    }
}

@Composable
private fun ArticleContent(article: Article) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (article.bannerUrl.isNotBlank()) {
            item(key = "banner") {
                AsyncImage(
                    model = article.bannerUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .aspectRatio(BANNER_ASPECT),
                )
            }
        }
        item(key = "title") {
            Text(
                text = article.title,
                style = Hsy.typography.titleLarge,
                color = Hsy.colors.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        item(key = "author") {
            ArticleAuthor(article)
        }
        itemsIndexed(article.paragraphs, key = { index, _ -> "p-${article.id}-$index" }) { _, paragraph ->
            Text(
                text = paragraph,
                style = Hsy.typography.bodyLarge,
                color = Hsy.colors.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        item(key = "footer") {
            Text(
                text = "全文完 · ${article.paragraphs.size} 段",
                style = Hsy.typography.bodySmall,
                color = Hsy.colors.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
            )
        }
    }
}

@Composable
private fun ArticleAuthor(article: Article) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier.size(AuthorAvatarSize).clip(CircleShape).background(Hsy.colors.surfaceVariant),
        ) {
            if (article.authorFaceUrl.isNotBlank()) {
                AsyncImage(
                    model = article.authorFaceUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            text = article.authorName.ifBlank { "佚名" },
            style = Hsy.typography.labelLarge,
            color = Hsy.colors.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (article.publishedEpochSec > 0L) {
            Text(
                text = formatRelativeTime(article.publishedEpochSec, HsyClock.nowEpochSec()),
                style = Hsy.typography.labelSmall,
                color = Hsy.colors.onSurfaceVariant,
            )
        }
    }
}

private val AuthorAvatarSize = 28.dp
private const val BANNER_ASPECT = 1.7778f
