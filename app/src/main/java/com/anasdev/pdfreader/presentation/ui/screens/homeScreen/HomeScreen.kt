package com.anasdev.pdfreader.presentation.ui.screens.homeScreen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.rounded.MergeType
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anasdev.pdfreader.R
import com.anasdev.pdfreader.data.local.RecentFile
import com.anasdev.pdfreader.domain.model.AppTheme
import com.anasdev.pdfreader.presentation.state.PdfViewModel
import com.anasdev.pdfreader.presentation.state.ToolResultEvent
import com.anasdev.pdfreader.presentation.ui.screens.homeScreen.component.ImagesToPdfProgressDialog
import com.anasdev.pdfreader.presentation.ui.screens.homeScreen.component.ImagesToPdfSheet
import com.anasdev.pdfreader.presentation.ui.screens.homeScreen.component.MergePdfsProgressDialog
import com.anasdev.pdfreader.presentation.ui.screens.homeScreen.component.MergePdfsSheet
import com.anasdev.pdfreader.presentation.ui.screens.homeScreen.component.RecentFileItem

private enum class ActiveToolType {
    IMAGES_TO_PDF,
    MERGE_PDFS,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: PdfViewModel,
    onNavigateToPdf: () -> Unit,
    onClickSetting: () -> Unit,
    onImagesToPdfClick: (() -> Unit)? = null,
    onMergePdfsClick: (() -> Unit)? = null
) {
    val recentFiles by viewModel.recentFiles.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val selectedCollectionId by viewModel.selectedCollectionId.collectAsStateWithLifecycle()
    val appTheme by viewModel.appTheme.collectAsStateWithLifecycle()

    val isDark = when (appTheme) {
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }

    var showMenu by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newCollectionName by remember { mutableStateOf("") }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showManageFilesDialog by remember { mutableStateOf(false) }
    var showImagesToPdfSheet by remember { mutableStateOf(false) }
    var selectedImagesForPdf by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var showMergePdfsSheet by remember { mutableStateOf(false) }
    var selectedPdfsForMerge by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var activeToolType by remember { mutableStateOf<ActiveToolType?>(null) }

    val isProcessingTool by viewModel.isProcessingTool.collectAsStateWithLifecycle()
    val toolProgress by viewModel.toolProgress.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val pdfSavedMessage = stringResource(R.string.pdf_saved_to_downloads)
    val openActionLabel = stringResource(R.string.open_action)

    val imagesPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            selectedImagesForPdf = (selectedImagesForPdf + uris).distinct()
            showImagesToPdfSheet = true
        }
    }

    val mergePdfsPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            selectedPdfsForMerge = (selectedPdfsForMerge + uris).distinct()
            showMergePdfsSheet = true
        }
    }

    LaunchedEffect(Unit) {
        viewModel.toolResultEvent.collect { event ->
            when (event) {
                is ToolResultEvent.Success -> {
                    showImagesToPdfSheet = false
                    selectedImagesForPdf = emptyList()
                    showMergePdfsSheet = false
                    selectedPdfsForMerge = emptyList()
                    activeToolType = null
                    val result = snackbarHostState.showSnackbar(
                        message = pdfSavedMessage,
                        actionLabel = openActionLabel,
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.loadPdf(event.uri)
                        onNavigateToPdf()
                    }
                }
                is ToolResultEvent.Failure -> {
                    activeToolType = null
                    snackbarHostState.showSnackbar(
                        message = event.errorMessage,
                        duration = SnackbarDuration.Long
                    )
                }
            }
        }
    }

    val handleImagesToPdfClick: () -> Unit = {
        if (onImagesToPdfClick != null) {
            onImagesToPdfClick()
        } else {
            imagesPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }

    val handleMergePdfsClick: () -> Unit = {
        if (onMergePdfsClick != null) {
            onMergePdfsClick()
        } else {
            mergePdfsPickerLauncher.launch(arrayOf("application/pdf"))
        }
    }

    val currentCollection = remember(collections, selectedCollectionId) {
        collections.find { it.id == selectedCollectionId }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.validateRecentFiles()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.loadPdf(it)
            onNavigateToPdf()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.my_documents)) },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.more_options)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.setting)) },
                            onClick = {
                                showMenu = false
                                onClickSetting()
                            }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { launcher.launch(arrayOf("application/pdf")) },
                icon = {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.open_file)
                    )
                },
                text = { Text(stringResource(R.string.open_pdf)) }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)
        ) {
            // 1. Top Offline Tools Row
            item(key = "home_tools_row") {
                HomeToolsRow(
                    onImagesToPdfClick = handleImagesToPdfClick,
                    onMergePdfsClick = handleMergePdfsClick,
                    isDark = isDark,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            // 2. Collections FilterChips Bar
            item(key = "home_collections_row") {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item(key = "col_chip_recent") {
                        FilterChip(
                            selected = selectedCollectionId == null,
                            onClick = { viewModel.selectCollection(null) },
                            label = { Text(stringResource(R.string.recent)) },
                            leadingIcon = if (selectedCollectionId == null) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            } else null
                        )
                    }

                    items(items = collections, key = { it.id }) { collection ->
                        FilterChip(
                            selected = selectedCollectionId == collection.id,
                            onClick = { viewModel.selectCollection(collection.id) },
                            label = { Text("${collection.name} (${collection.files.size})") },
                            leadingIcon = if (selectedCollectionId == collection.id) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            } else null
                        )
                    }

                    item(key = "col_chip_new") {
                        SuggestionChip(
                            onClick = {
                                newCollectionName = ""
                                showCreateDialog = true
                            },
                            label = { Text(stringResource(R.string.new_collection)) },
                            icon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                }
            }

            // 3. Main Content: Recent Files or Selected Collection
            if (selectedCollectionId == null) {
                // Recent Files View
                if (recentFiles.isEmpty()) {
                    item(key = "recent_empty_state") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.no_recent_doc),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                } else {
                    item(key = "recent_files_header") {
                        Text(
                            text = stringResource(R.string.recent_file),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    items(items = recentFiles, key = { it.uri }) { file ->
                        RecentFileItem(
                            file = file,
                            onClick = {
                                viewModel.loadPdf(file.uri.toUri())
                                onNavigateToPdf()
                            },
                            onDelete = {
                                viewModel.deleteRecentFile(file)
                            }
                        )
                    }
                }
            } else if (currentCollection != null) {
                // Selected Collection View
                item(key = "collection_header_${currentCollection.id}") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentCollection.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = stringResource(R.string.files_count, currentCollection.files.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { showManageFilesDialog = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = stringResource(R.string.edit_collection_files)
                                    )
                                }
                                IconButton(onClick = { showDeleteConfirmDialog = true }) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = stringResource(R.string.delete_collection),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    }
                }

                if (currentCollection.files.isEmpty()) {
                    item(key = "collection_empty_${currentCollection.id}") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                )
                                Text(
                                    text = stringResource(R.string.no_files_in_collection),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(onClick = { showManageFilesDialog = true }) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.add_files))
                                }
                            }
                        }
                    }
                } else {
                    items(items = currentCollection.files, key = { it.uri }) { file ->
                        RecentFileItem(
                            file = RecentFile(
                                uri = file.uri,
                                title = file.title,
                                timestamp = file.addedAt
                            ),
                            onClick = {
                                viewModel.loadPdf(file.uri.toUri())
                                onNavigateToPdf()
                            },
                            onDelete = {
                                viewModel.toggleFileInCollection(
                                    currentCollection.id,
                                    file.uri,
                                    file.title,
                                    false
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    // Create Collection Dialog
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(stringResource(R.string.create_collection)) },
            text = {
                OutlinedTextField(
                    value = newCollectionName,
                    onValueChange = { newCollectionName = it },
                    label = { Text(stringResource(R.string.collection_name)) },
                    placeholder = { Text(stringResource(R.string.collection_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = newCollectionName.trim()
                        if (trimmed.isNotBlank()) {
                            viewModel.createCollection(trimmed)
                            showCreateDialog = false
                        }
                    },
                    enabled = newCollectionName.trim().isNotBlank()
                ) {
                    Text(stringResource(R.string.create_collection))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Delete Collection Confirmation Dialog
    if (showDeleteConfirmDialog && currentCollection != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(stringResource(R.string.delete_collection)) },
            text = {
                Text(stringResource(R.string.delete_collection_message, currentCollection.name))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCollection(currentCollection.id)
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.delete_collection))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Manage Files Dialog
    if (showManageFilesDialog && currentCollection != null) {
        val addFilePicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            uri?.let {
                viewModel.addPickedPdfToCollection(currentCollection.id, it)
            }
        }

        val candidateFiles = remember(recentFiles, currentCollection.files) {
            val fromRecents = recentFiles.map { it.uri to it.title }
            val fromCollection = currentCollection.files.map { it.uri to it.title }
            (fromRecents + fromCollection).distinctBy { it.first }
        }
        val currentCollectionUris = remember(currentCollection.files) {
            currentCollection.files.map { it.uri }.toSet()
        }

        AlertDialog(
            onDismissRequest = { showManageFilesDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.manage_files), fontSize = 20.sp)
                    OutlinedButton(
                        onClick = { addFilePicker.launch(arrayOf("application/pdf")) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.add_from_device), style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            text = {
                if (candidateFiles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.no_recent_doc),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                    ) {
                        items(candidateFiles, key = { it.first }) { (uri, title) ->
                            val isChecked = uri in currentCollectionUris
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.toggleFileInCollection(
                                            currentCollection.id,
                                            uri,
                                            title,
                                            !isChecked
                                        )
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        viewModel.toggleFileInCollection(
                                            currentCollection.id,
                                            uri,
                                            title,
                                            checked
                                        )
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showManageFilesDialog = false }) {
                    Text(stringResource(R.string.done))
                }
            }
        )
    }

    // Images to PDF Sheet
    if (showImagesToPdfSheet && selectedImagesForPdf.isNotEmpty()) {
        ImagesToPdfSheet(
            imageUris = selectedImagesForPdf,
            onDismiss = {
                showImagesToPdfSheet = false
                selectedImagesForPdf = emptyList()
            },
            onAddMoreImages = {
                imagesPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onConvert = { uris, fileName ->
                showImagesToPdfSheet = false
                activeToolType = ActiveToolType.IMAGES_TO_PDF
                viewModel.convertImagesToPdf(uris, fileName)
            }
        )
    }

    // Merge PDFs Sheet
    if (showMergePdfsSheet && selectedPdfsForMerge.isNotEmpty()) {
        MergePdfsSheet(
            pdfUris = selectedPdfsForMerge,
            resolveFileName = { uri -> viewModel.resolvePdfDisplayName(uri) },
            onDismiss = {
                showMergePdfsSheet = false
                selectedPdfsForMerge = emptyList()
            },
            onAddMorePdfs = {
                mergePdfsPickerLauncher.launch(arrayOf("application/pdf"))
            },
            onMerge = { uris, fileName ->
                showMergePdfsSheet = false
                activeToolType = ActiveToolType.MERGE_PDFS
                viewModel.mergePdfs(uris, fileName)
            }
        )
    }

    // Tool Progress Dialog
    if (isProcessingTool) {
        val (current, total) = toolProgress ?: Pair(0, 0)
        if (activeToolType == ActiveToolType.MERGE_PDFS) {
            MergePdfsProgressDialog(current = current, total = total)
        } else {
            ImagesToPdfProgressDialog(current = current, total = total)
        }
    }
}

@Composable
fun HomeToolsRow(
    onImagesToPdfClick: () -> Unit,
    onMergePdfsClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDark: Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Images to PDF Card (Green / Emerald)
        HomeToolCard(
            title = stringResource(R.string.tool_images_to_pdf_title),
            subtitle = stringResource(R.string.tool_images_to_pdf_subtitle),
            icon = Icons.Rounded.Collections,
            isGreenTheme = true,
            onClick = onImagesToPdfClick,
            isDark = isDark,
            modifier = Modifier.weight(1f)
        )

        // Merge PDFs Card (Blue / Indigo)
        HomeToolCard(
            title = stringResource(R.string.tool_merge_pdfs_title),
            subtitle = stringResource(R.string.tool_merge_pdfs_subtitle),
            icon = Icons.AutoMirrored.Rounded.MergeType,
            isGreenTheme = false,
            onClick = onMergePdfsClick,
            isDark = isDark,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun HomeToolCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isGreenTheme: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDark: Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f
) {
    val containerColor = if (isGreenTheme) {
        if (isDark) Color(0xFF1B2E23) else Color(0xFFE8F5E9)
    } else {
        if (isDark) Color(0xFF18283E) else Color(0xFFE3F2FD)
    }

    val borderColor = if (isGreenTheme) {
        if (isDark) Color(0xFF2D4B39) else Color(0xFFC8E6C9)
    } else {
        if (isDark) Color(0xFF284263) else Color(0xFFBBDEFB)
    }

    val iconBgColor = if (isGreenTheme) {
        if (isDark) Color(0xFF264433) else Color(0xFFC8E6C9)
    } else {
        if (isDark) Color(0xFF223A5B) else Color(0xFFBBDEFB)
    }

    val iconTint = if (isGreenTheme) {
        if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
    } else {
        if (isDark) Color(0xFF90CAF9) else Color(0xFF1565C0)
    }

    val titleColor = if (isGreenTheme) {
        if (isDark) Color(0xFFA5D6A7) else Color(0xFF1B5E20)
    } else {
        if (isDark) Color(0xFFBBDEFB) else Color(0xFF0D47A1)
    }

    val subtitleColor = if (isGreenTheme) {
        if (isDark) Color(0xFF81C784).copy(alpha = 0.85f) else Color(0xFF388E3C)
    } else {
        if (isDark) Color(0xFF90CAF9).copy(alpha = 0.85f) else Color(0xFF1976D2)
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.heightIn(min = 76.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(iconBgColor, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = subtitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
