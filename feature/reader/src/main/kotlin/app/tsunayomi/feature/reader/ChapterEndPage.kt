package app.tsunayomi.feature.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.tsunayomi.core.model.Chapter

@Composable
internal fun ChapterEndPage(nextChapter: Chapter?, onNextChapter: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.feature_reader_chapter_finished),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
        )
        if (nextChapter != null) {
            Text(
                stringResource(R.string.feature_reader_next_chapter_name, nextChapter.name),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
            Button(onClick = onNextChapter) { Text(stringResource(R.string.feature_reader_next_chapter)) }
        } else {
            Text(
                stringResource(R.string.feature_reader_last_chapter),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
            )
        }
    }
}
