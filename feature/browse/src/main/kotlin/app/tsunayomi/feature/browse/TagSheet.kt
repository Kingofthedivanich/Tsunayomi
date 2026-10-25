package app.tsunayomi.feature.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** Lets the user tick several of the source's tags and lists titles that have all of them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TagSheet(
    choices: TagChoices,
    initiallySelected: Set<String>,
    onSearch: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(initiallySelected) }
    var filter by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxHeight(0.85f)
                .navigationBarsPadding(),
        ) {
            Text(
                stringResource(R.string.feature_browse_tags_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            when (choices) {
                TagChoices.NotLoaded, TagChoices.Loading -> Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                    CircularProgressIndicator()
                }
                is TagChoices.Unavailable -> Text(
                    choices.error ?: stringResource(R.string.feature_browse_tags_unsupported),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp).weight(1f),
                )
                is TagChoices.Loaded -> {
                    OutlinedTextField(
                        value = filter,
                        onValueChange = { filter = it },
                        placeholder = { Text(stringResource(R.string.feature_browse_tags_filter)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    // Ticked tags stay on top, so they're easy to untick after typing a filter.
                    val shown = choices.tags
                        .filter { filter.isBlank() || it.contains(filter.trim(), ignoreCase = true) }
                        .sortedByDescending { it in selected }
                    LazyColumn(Modifier.weight(1f)) {
                        items(shown, key = { it }) { tag ->
                            val checked = tag in selected
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { selected = if (checked) selected - tag else selected + tag }
                                    .padding(horizontal = 8.dp)
                                    .height(48.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(horizontal = 8.dp))
                                Text(tag)
                            }
                        }
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                TextButton(onClick = { selected = emptySet() }, enabled = selected.isNotEmpty()) {
                    Text(stringResource(R.string.feature_browse_filter_reset))
                }
                Button(onClick = { onSearch(selected) }, enabled = choices is TagChoices.Loaded) {
                    Text(
                        if (selected.isEmpty()) {
                            stringResource(R.string.feature_browse_tags_show_all)
                        } else {
                            stringResource(R.string.feature_browse_tags_search, selected.size)
                        },
                    )
                }
            }
        }
    }
}
