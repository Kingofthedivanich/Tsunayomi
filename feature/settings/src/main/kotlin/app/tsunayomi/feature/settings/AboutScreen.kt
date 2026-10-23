package app.tsunayomi.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_settings_about_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.feature_settings_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.feature_settings_about_version)) },
                supportingContent = { Text(version) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.feature_settings_about_license)) },
                supportingContent = { Text(stringResource(R.string.feature_settings_about_license_value)) },
            )
            Text(
                stringResource(R.string.feature_settings_about_credits),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 8.dp),
            )
            // MangaDex's API terms require crediting MangaDex and the scanlation groups.
            ListItem(
                headlineContent = { Text("MangaDex") },
                supportingContent = { Text(stringResource(R.string.feature_settings_about_mangadex)) },
            )
            ListItem(
                headlineContent = { Text("Komga") },
                supportingContent = { Text(stringResource(R.string.feature_settings_about_komga)) },
            )
            ListItem(
                headlineContent = { Text("Google ML Kit") },
                supportingContent = { Text(stringResource(R.string.feature_settings_about_mlkit)) },
            )
            ListItem(
                headlineContent = { Text("Google Gemini API") },
                supportingContent = { Text(stringResource(R.string.feature_settings_about_gemini)) },
            )
            Text(
                stringResource(R.string.feature_settings_about_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
