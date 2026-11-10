package com.videobrowsing.honeysuckle.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.videobrowsing.honeysuckle.core.designsystem.icon.HsyIcons
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.CircularProgressIndicator
import com.videobrowsing.honeysuckle.core.designsystem.ui.Icon
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text

/** 还没按搜索时的联想层:一行"联想中"、一条失败说明、一串可点的话,空着时告诉用户回车能直接搜 */
@Composable
internal fun Suggestions(state: SearchUiState, onAction: (SearchAction) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        if (state.suggesting) SuggestingIndicator()
        state.suggestionError?.let { error -> SuggestionError(error) }
        state.suggestions.forEach { word ->
            SuggestionRow(word = word, onClick = { onAction(SearchAction.Submit(word)) })
        }
        if (state.suggestions.isEmpty() && !state.suggesting && state.suggestionError == null) {
            Text(
                text = "按回车直接搜「${state.query.trim()}」",
                style = Hsy.typography.bodyMedium,
                color = Hsy.colors.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun SuggestingIndicator() {
    Row(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp))
        Text(
            text = "加载中…",
            style = Hsy.typography.bodySmall,
            color = Hsy.colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun SuggestionError(text: String) {
    Text(
        text = "搜索建议加载失败:$text",
        style = Hsy.typography.bodySmall,
        color = Hsy.colors.error,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun SuggestionRow(word: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = HsyIcons.search,
            contentDescription = null,
            tint = Hsy.colors.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = word,
            style = Hsy.typography.bodyLarge,
            color = Hsy.colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
