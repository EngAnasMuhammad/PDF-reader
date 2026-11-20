package com.anasdev.pdfreader.presentation.ui.screens.settingsScreen

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.graphics.createBitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anasdev.pdfreader.BuildConfig
import com.anasdev.pdfreader.domain.model.AppLanguage
import com.anasdev.pdfreader.domain.model.AppTheme
import com.anasdev.pdfreader.presentation.state.PdfViewModel
import com.anasdev.pdfreader.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: PdfViewModel,
    onClickBack: () -> Unit,
    onClickLicense: () -> Unit
) {
    val keepScreenAwake by viewModel.keepScreenAwake.collectAsStateWithLifecycle()
    val isHorizontalScroll by viewModel.isHorizontalScroll.collectAsStateWithLifecycle()
    val appTheme by viewModel.appTheme.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val appIconBitmap = remember(context) {
        try {
            val drawable = context.packageManager.getApplicationIcon(context.packageName)
            val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 144
            val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 144
            val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

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
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
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
                leadingContent = { SettingIconContainer(imageVector = Icons.Default.ScreenLockPortrait) },
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
                leadingContent = { SettingIconContainer(imageVector = Icons.Default.Swipe) },
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
                leadingContent = { SettingIconContainer(imageVector = Icons.Default.Palette) }
            )

            ListItem(
                modifier = Modifier.clickable { showLanguageDialog = true },
                headlineContent = { Text(stringResource(R.string.language)) },
                supportingContent = { Text(stringResource(appLanguage.titleResId)) },
                leadingContent = { SettingIconContainer(imageVector = Icons.Default.Language) }
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
                leadingContent = {
                    SettingIconContainer(
                        imageVector = Icons.Default.Delete
                    )
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Section 4: About
            Text(
                text = stringResource(R.string.settings_about_section),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
            )

            // Developer Row
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_designed_by)) },
                supportingContent = {
                    Text(
                        text = stringResource(R.string.settings_developer_name),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                },
                leadingContent = { SettingIconContainer(imageVector = Icons.Rounded.Code) }
            )

            // GitHub Repository Row (Clickable)
            ListItem(
                modifier = Modifier.clickable {
                    try {
                        uriHandler.openUri("https://github.com/EngAnasMuhammad/PDF-reader")
                    } catch (_: Exception) {
                        // Safe fallback if no browser is installed
                    }
                },
                headlineContent = { Text(stringResource(R.string.settings_github_repo)) },
                supportingContent = {
                    Text(
                        text = "github.com/EngAnasMuhammad/PDF-reader",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingContent = { SettingIconContainer(imageVector = Icons.Rounded.Public) }
            )

            // Open Source Licenses
            ListItem(
                modifier = Modifier.clickable { onClickLicense() },
                headlineContent = { Text(stringResource(R.string.open_source_licenses)) },
                leadingContent = { SettingIconContainer(imageVector = Icons.Default.Info) }
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Centered Footer Branding Block
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (appIconBitmap != null) {
                    Image(
                        bitmap = appIconBitmap,
                        contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = stringResource(R.string.app_name),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.settings_app_tagline),
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
                Column (modifier = Modifier.verticalScroll(rememberScrollState())){
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
                                .padding(vertical = 10.dp ), // Fixed touch target padding
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
                Column (modifier = Modifier.verticalScroll(rememberScrollState())){
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
                                .padding(vertical = 10.dp),
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

@Composable
private fun SettingIconContainer(
    imageVector: ImageVector,
    contentDescription: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    iconTint: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Surface(
        shape = CircleShape,
        color = containerColor,
        modifier = Modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}