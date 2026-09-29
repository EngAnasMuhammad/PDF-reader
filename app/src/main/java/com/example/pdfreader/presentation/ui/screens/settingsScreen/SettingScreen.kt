package com.example.pdfreader.presentation.ui.screens.settingsScreen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pdfreader.R
import com.example.pdfreader.domain.model.AppLanguage
import com.example.pdfreader.domain.model.AppTheme
import com.example.pdfreader.presentation.state.PdfViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: PdfViewModel,
    onClickBack: () -> Unit
) {
    val keepScreenAwake by viewModel.keepScreenAwake.collectAsStateWithLifecycle()
    val isHorizontalScroll by viewModel.isHorizontalScroll.collectAsStateWithLifecycle()
    val appTheme by viewModel.appTheme.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()

    var showThemeDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.setting)) },
                navigationIcon = {
                    IconButton(onClick = { onClickBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Section 1: Reading Preferences
            Text(
                text = stringResource(R.string.reading_preferences),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.keep_screen_awake)) },
                supportingContent = { Text(stringResource(R.string.keep_screen_awake_description)) },
                leadingContent = { Icon(Icons.Default.ScreenLockPortrait, null) },
                trailingContent = {
                    Switch(
                        checked = keepScreenAwake,
                        onCheckedChange = { viewModel.toggleKeepScreenAwake(it) }
                    )
                }
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.horizontal_scrolling)) },
                supportingContent = { Text(stringResource(R.string.horizontal_scrolling_description)) },
                leadingContent = { Icon(Icons.Default.Swipe, null) },
                trailingContent = {
                    Switch(
                        checked = isHorizontalScroll,
                        onCheckedChange = { viewModel.toggleHorizontalScroll(it) }
                    )
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Section 2: Display & Language
            ListItem(
                modifier = Modifier.clickable { showThemeDialog = true },
                headlineContent = { Text(stringResource(R.string.app_theme)) },
                supportingContent = { Text((stringResource(appTheme.displayName))) },
                leadingContent = { Icon(Icons.Default.Palette, contentDescription = null) }
            )

            ListItem(
                modifier = Modifier.clickable { showLanguageDialog = true },
                headlineContent = { Text(stringResource(R.string.language)) },
                supportingContent = { Text(stringResource(appLanguage.titleResId)) },
                leadingContent = { Icon(Icons.Default.Language, contentDescription = null) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Section 3: Privacy
            Text(
                text = stringResource(R.string.privacy),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
            )

            ListItem(
                modifier = Modifier.clickable { showClearDialog = true },
                headlineContent = { Text(stringResource(R.string.clear_history), color = Color.Red) },
                supportingContent = { Text(stringResource(R.string.clear_history_description)) },
                leadingContent = { Icon(Icons.Default.Delete, null, tint = Color.Red) }
            )

            Spacer(modifier = Modifier.weight(1f))

            // Section 4: About (Footer)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row {
                    Text(
                        text = stringResource(R.string.app_name),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Text(
                        text = "V1",
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = stringResource(R.string.credit_to_marain87),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // --- ALL DIALOGS EXTRACTED TO THE BOTTOM ---

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text(stringResource(R.string.choose_theme)) },
            text = {
                Column {
                    AppTheme.entries.forEach { theme ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = (theme == appTheme),
                                    onClick = {
                                        viewModel.setAppTheme(theme)
                                        showThemeDialog = false
                                    }
                                )
                                .padding(vertical = 12.dp), // Fixed touch target padding
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (theme == appTheme),
                                onClick = {
                                    viewModel.setAppTheme(theme)
                                    showThemeDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = stringResource(theme.displayName),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.language)) },
            text = {
                Column {
                    AppLanguage.entries.forEach { lang ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = (lang == appLanguage),
                                    onClick = {
                                        viewModel.setAppLanguage(lang)
                                        showLanguageDialog = false
                                    }
                                )
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (lang == appLanguage),
                                onClick = {
                                    viewModel.setAppLanguage(lang)
                                    showLanguageDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = stringResource(lang.titleResId),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            // Make sure to add this specific string to strings.xml to avoid RTL bugs
            title = { Text(stringResource(R.string.clear_history_title)) },
            text = { Text(stringResource(R.string.clear_history_dialog_description)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearRecentHistory()
                    showClearDialog = false
                }) {
                    Text(stringResource(R.string.clear), color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}