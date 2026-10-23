package app.tsunayomi.feature.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import app.tsunayomi.source.api.Filter
import app.tsunayomi.source.api.FilterList

/**
 * Renders any source's [FilterList] generically. Filters are mutable objects owned by the view model;
 * [version] changes after each edit so the sheet recomposes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FilterSheet(
    filters: FilterList,
    version: Int,
    onEdit: (() -> Unit) -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onReset) { Text(stringResource(R.string.feature_browse_filter_reset)) }
                Row(Modifier.weight(1f)) {}
                Button(onClick = onApply) { Text(stringResource(R.string.feature_browse_filter_apply)) }
            }
            // The filter objects mutate in place, so Compose would skip them as unchanged; rebuild on every edit.
            key(version) { filters.forEach { FilterItem(it, onEdit) } }
        }
    }
}

@Composable
private fun FilterItem(filter: Filter<*>, onEdit: (() -> Unit) -> Unit) {
    when (filter) {
        is Filter.Header -> Text(
            filter.name,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        )
        is Filter.Text -> OutlinedTextField(
            value = filter.state,
            onValueChange = { value -> onEdit { filter.state = value } },
            label = { Text(filter.name) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        )
        is Filter.CheckBox -> Row(
            Modifier
                .fillMaxWidth()
                .clickable { onEdit { filter.state = !filter.state } },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = filter.state, onCheckedChange = null, modifier = Modifier.padding(12.dp))
            Text(filter.name)
        }
        is Filter.TriState -> Row(
            Modifier
                .fillMaxWidth()
                .clickable {
                    onEdit {
                        filter.state = when (filter.state) {
                            Filter.TriState.Value.Ignore -> Filter.TriState.Value.Include
                            Filter.TriState.Value.Include -> Filter.TriState.Value.Exclude
                            Filter.TriState.Value.Exclude -> Filter.TriState.Value.Ignore
                        }
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TriStateCheckbox(
                state = when (filter.state) {
                    Filter.TriState.Value.Ignore -> ToggleableState.Off
                    Filter.TriState.Value.Include -> ToggleableState.On
                    Filter.TriState.Value.Exclude -> ToggleableState.Indeterminate
                },
                onClick = null,
                modifier = Modifier.padding(12.dp),
            )
            Text(filter.name)
        }
        is Filter.Select -> ChipSection(filter.name) {
            filter.values.forEachIndexed { index, value ->
                FilterChip(
                    selected = filter.state == index,
                    onClick = { onEdit { filter.state = index } },
                    label = { Text(value) },
                )
            }
        }
        is Filter.Sort -> ChipSection(filter.name) {
            filter.values.forEachIndexed { index, value ->
                val selection = filter.state
                val selected = selection?.index == index
                FilterChip(
                    selected = selected,
                    onClick = {
                        onEdit {
                            // Tapping the active option flips the direction.
                            filter.state = Filter.Sort.Selection(index, ascending = if (selected) !selection!!.ascending else false)
                        }
                    },
                    label = { Text(value) },
                    leadingIcon = if (selected) {
                        {
                            Icon(
                                if (selection!!.ascending) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward,
                                contentDescription = null,
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
        is Filter.Group<*> -> Column {
            Text(
                filter.name,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            )
            filter.state.forEach { FilterItem(it, onEdit) }
        }
    }
}

@Composable
private fun ChipSection(title: String, chips: @Composable () -> Unit) {
    Column(Modifier.padding(top = 12.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
    }
}

