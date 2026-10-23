package app.tsunayomi.feature.library

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.tsunayomi.core.designsystem.component.EmptyState
import app.tsunayomi.core.model.Category
import app.tsunayomi.data.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class CategoriesViewModel @Inject constructor(private val repository: LibraryRepository) : ViewModel() {
    val categories: StateFlow<List<Category>?> = repository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = null)

    fun create(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.createCategory(name) }
    }

    fun rename(category: Category, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.renameCategory(category.id, name) }
    }

    fun delete(category: Category) {
        viewModelScope.launch { repository.deleteCategory(category.id) }
    }

    fun move(index: Int, by: Int) {
        val list = categories.value ?: return
        val target = index + by
        if (target !in list.indices) return
        val ids = list.map { it.id }.toMutableList().apply { add(target, removeAt(index)) }
        viewModelScope.launch { repository.reorderCategories(ids) }
    }
}

/** Dialog state: null when closed; [editing] null means "create". */
private data class NameDialog(val editing: Category?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategoriesScreen(onBack: () -> Unit, viewModel: CategoriesViewModel = hiltViewModel()) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    var nameDialog by remember {
        mutableStateOf<NameDialog?>(null)
    }
    var deleting by remember {
        mutableStateOf<Category?>(null)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_library_categories)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.feature_library_back))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nameDialog = NameDialog(editing = null) },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.feature_library_category_add)) },
            )
        },
    ) { padding ->
        val list = categories ?: return@Scaffold
        if (list.isEmpty()) {
            EmptyState(
                icon = Icons.AutoMirrored.Outlined.Label,
                title = stringResource(R.string.feature_library_no_categories),
                message = stringResource(R.string.feature_library_no_categories_message),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(contentPadding = padding) {
            itemsIndexed(list, key = { _, category -> category.id }) { index, category ->
                ListItem(
                    headlineContent = { Text(category.name) },
                    leadingContent = { Icon(Icons.AutoMirrored.Outlined.Label, contentDescription = null) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { viewModel.move(index, -1) }, enabled = index > 0) {
                                Icon(Icons.Outlined.KeyboardArrowUp, stringResource(R.string.feature_library_move_up))
                            }
                            IconButton(onClick = { viewModel.move(index, 1) }, enabled = index < list.lastIndex) {
                                Icon(Icons.Outlined.KeyboardArrowDown, stringResource(R.string.feature_library_move_down))
                            }
                            IconButton(onClick = { nameDialog = NameDialog(editing = category) }) {
                                Icon(Icons.Outlined.Edit, stringResource(R.string.feature_library_rename))
                            }
                            IconButton(onClick = { deleting = category }) {
                                Icon(Icons.Outlined.Delete, stringResource(R.string.feature_library_delete))
                            }
                        }
                    },
                )
            }
        }
    }

    nameDialog?.let { dialog ->
        var name by rememberSaveable { mutableStateOf(dialog.editing?.name.orEmpty()) }
        AlertDialog(
            onDismissRequest = { nameDialog = null },
            title = {
                Text(
                    stringResource(
                        if (dialog.editing == null) R.string.feature_library_category_add else R.string.feature_library_rename,
                    ),
                )
            },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.feature_library_category_name)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        if (dialog.editing == null) viewModel.create(name) else viewModel.rename(dialog.editing, name)
                        nameDialog = null
                    },
                ) { Text(stringResource(R.string.feature_library_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { nameDialog = null }) { Text(stringResource(R.string.feature_library_cancel)) }
            },
        )
    }

    deleting?.let { category ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.feature_library_delete_category_title, category.name)) },
            text = { Text(stringResource(R.string.feature_library_delete_category_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(category)
                    deleting = null
                }) { Text(stringResource(R.string.feature_library_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.feature_library_cancel)) }
            },
        )
    }
}
