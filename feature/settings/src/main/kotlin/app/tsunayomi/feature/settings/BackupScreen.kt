package app.tsunayomi.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/gzip"),
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.createBackup(uri)
        }
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.restoreBackup(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_settings_backup_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            Text(
                text = stringResource(R.string.feature_settings_backup_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.feature_settings_backup_create_title)) },
                supportingContent = { Text(stringResource(R.string.feature_settings_backup_create_summary)) },
                leadingContent = { Icon(Icons.Outlined.Backup, contentDescription = null) },
                modifier = Modifier.clickable {
                    createBackupLauncher.launch(viewModel.defaultBackupFilename())
                },
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.feature_settings_backup_restore_title)) },
                supportingContent = { Text(stringResource(R.string.feature_settings_backup_restore_summary)) },
                leadingContent = { Icon(Icons.Outlined.Restore, contentDescription = null) },
                modifier = Modifier.clickable {
                    restoreBackupLauncher.launch(arrayOf("*/*"))
                },
            )
        }
    }

    when (val currentState = state) {
        is BackupUiState.Creating -> {
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                title = { Text(stringResource(R.string.feature_settings_backup_creating)) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                        Spacer(Modifier.width(16.dp))
                        Text(stringResource(R.string.feature_settings_backup_please_wait))
                    }
                },
            )
        }
        is BackupUiState.Restoring -> {
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                title = { Text(stringResource(R.string.feature_settings_backup_restoring)) },
                text = {
                    Column {
                        val progress = currentState.progress
                        if (progress.total > 0) {
                            Text("${progress.current} / ${progress.total}: ${progress.currentTitle}")
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { progress.current.toFloat() / progress.total },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(36.dp))
                                Spacer(Modifier.width(16.dp))
                                Text(stringResource(R.string.feature_settings_backup_reading_file))
                            }
                        }
                    }
                },
            )
        }
        is BackupUiState.Success -> {
            AlertDialog(
                onDismissRequest = viewModel::resetState,
                confirmButton = {
                    TextButton(onClick = viewModel::resetState) {
                        Text(stringResource(android.R.string.ok))
                    }
                },
                icon = { Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text(stringResource(R.string.feature_settings_backup_success_title)) },
                text = {
                    Text(
                        stringResource(
                            currentState.messageRes,
                            currentState.mangaCount,
                            currentState.categoryCount,
                            currentState.chapterCount,
                        ),
                    )
                },
            )
        }
        is BackupUiState.Error -> {
            AlertDialog(
                onDismissRequest = viewModel::resetState,
                confirmButton = {
                    TextButton(onClick = viewModel::resetState) {
                        Text(stringResource(android.R.string.ok))
                    }
                },
                icon = { Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text(stringResource(R.string.feature_settings_backup_error_title)) },
                text = { Text(currentState.message) },
            )
        }
        BackupUiState.Idle -> Unit
    }
}
