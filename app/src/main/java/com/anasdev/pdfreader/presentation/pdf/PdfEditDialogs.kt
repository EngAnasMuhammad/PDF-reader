package com.anasdev.pdfreader.presentation.pdf

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.anasdev.pdfreader.R
import com.anasdev.pdfreader.domain.model.NormalizedPoint
import com.anasdev.pdfreader.domain.model.SavedSignature
import com.anasdev.pdfreader.domain.model.TextBox

@Composable
fun PdfLoadErrorScreen(
    bgColor: Color,
    onClickBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.BrokenImage,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.file_not_available),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.file_not_available_description),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onClickBack,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(stringResource(R.string.back_to_home))
            }
        }
    }
}

@Composable
fun AddOrEditTextDialog(
    dialogState: TextDialogState?,
    onDismiss: () -> Unit,
    onAddText: (pageIndex: Int, xNorm: Float, yNorm: Float, text: String) -> Unit,
    onUpdateText: (TextBox, newText: String) -> Unit,
    onDeleteText: (String) -> Unit
) {
    if (dialogState == null) return

    var textInputValue by remember(dialogState) {
        mutableStateOf(
            when (dialogState) {
                is TextDialogState.Edit -> dialogState.textBox.text
                is TextDialogState.Add -> ""
            }
        )
    }
    val focusRequester = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (dialogState is TextDialogState.Edit) R.string.edit_text else R.string.add_text
                )
            )
        },
        text = {
            OutlinedTextField(
                value = textInputValue,
                onValueChange = { textInputValue = it },
                placeholder = { Text(stringResource(R.string.text_placeholder)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                maxLines = 6
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = textInputValue.trim()
                    when (dialogState) {
                        is TextDialogState.Add -> {
                            if (trimmed.isNotBlank()) {
                                onAddText(dialogState.pageIndex, dialogState.xNorm, dialogState.yNorm, trimmed)
                            }
                        }
                        is TextDialogState.Edit -> {
                            if (trimmed.isNotBlank()) {
                                onUpdateText(dialogState.textBox, trimmed)
                            } else {
                                onDeleteText(dialogState.textBox.id)
                            }
                        }
                    }
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )

    LaunchedEffect(dialogState) {
        focusRequester.requestFocus()
    }
}

@Composable
fun ExportProgressDialog(
    isExporting: Boolean
) {
    if (!isExporting) return

    Dialog(
        onDismissRequest = { /* Modal: cannot dismiss while saving */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Row(
                modifier = Modifier.padding(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.width(20.dp))
                Text(
                    text = stringResource(R.string.saving_pdf),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
fun DiscardConfirmDialog(
    visible: Boolean,
    onConfirmDiscard: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.discard_edits_title)) },
        text = { Text(stringResource(R.string.discard_edits_message)) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onConfirmDiscard()
            }) {
                Text(stringResource(R.string.discard), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.keep_editing))
            }
        }
    )
}

@Composable
fun SavedSignaturePickerDialog(
    savedSignature: SavedSignature,
    onUse: () -> Unit,
    onCreateNew: () -> Unit,
    onDeleteSaved: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.saved_signature_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Rounded preview card with light background for high contrast in light/dark mode
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFAFAFA))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        val cWidth = size.width
                        val cHeight = size.height
                        if (cWidth <= 0f || cHeight <= 0f) return@Canvas

                        val ar = savedSignature.aspectRatio.coerceIn(0.4f, 6.0f)
                        val fittedWidth: Float
                        val fittedHeight: Float
                        if (cWidth / cHeight > ar) {
                            fittedHeight = cHeight
                            fittedWidth = fittedHeight * ar
                        } else {
                            fittedWidth = cWidth
                            fittedHeight = fittedWidth / ar
                        }
                        val offsetX = (cWidth - fittedWidth) / 2f
                        val offsetY = (cHeight - fittedHeight) / 2f

                        val strokeColor = Color(savedSignature.colorArgb)
                        val strokeWidthPx = (fittedWidth * 0.012f).coerceIn(2.5f, 6f)

                        for (stroke in savedSignature.strokes) {
                            if (stroke.isEmpty()) continue
                            if (stroke.size == 1) {
                                val pt = stroke[0]
                                drawCircle(
                                    color = strokeColor,
                                    radius = strokeWidthPx / 2f,
                                    center = Offset(offsetX + pt.x * fittedWidth, offsetY + pt.y * fittedHeight)
                                )
                                continue
                            }

                            val path = Path()
                            val first = stroke[0]
                            var currX = offsetX + first.x * fittedWidth
                            var currY = offsetY + first.y * fittedHeight
                            path.moveTo(currX, currY)

                            for (i in 1 until stroke.size) {
                                val next = stroke[i]
                                val nextX = offsetX + next.x * fittedWidth
                                val nextY = offsetY + next.y * fittedHeight
                                val midX = (currX + nextX) / 2f
                                val midY = (currY + nextY) / 2f
                                path.quadraticTo(currX, currY, midX, midY)
                                currX = nextX
                                currY = nextY
                            }
                            path.lineTo(currX, currY)
                            drawPath(
                                path = path,
                                color = strokeColor,
                                style = Stroke(
                                    width = strokeWidthPx,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }

                    // Trash icon button to delete saved signature
                    IconButton(
                        onClick = onDeleteSaved,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete_signature),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismiss,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(stringResource(R.string.cancel), fontSize = 12.sp)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onCreateNew,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(stringResource(R.string.create_new_signature), fontSize = 12.sp)
                    }
                    Button(
                        onClick = onUse,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(stringResource(R.string.use_signature), fontSize = 12.sp)
                    }
                }
            }
        },
        dismissButton = null
    )
}

@Composable
fun CreateSignatureDialog(
    onSave: (SavedSignature) -> Unit,
    onDismiss: () -> Unit
) {
    val inkColors = remember {
        listOf(
            0xFF111111.toInt(), // Black (default)
            0xFF1A3A6B.toInt(), // Navy Blue
            0xFF8B1E1E.toInt()  // Dark Red
        )
    }
    var selectedColorArgb by remember { mutableStateOf(inkColors[0]) }

    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.create_signature_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Drawing Canvas Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFAFAFA))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    // Placeholder Hint when empty
                    if (strokes.isEmpty() && currentStroke.isEmpty()) {
                        Text(
                            text = stringResource(R.string.sign_here_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            fontStyle = FontStyle.Italic,
                            color = Color(0x66000000)
                        )
                    }

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    down.consume()
                                    val strokePoints = mutableListOf(down.position)
                                    currentStroke = strokePoints.toList()

                                    val pointerId = down.id
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                                        if (!change.pressed) {
                                            change.consume()
                                            break
                                        }
                                        change.consume()
                                        strokePoints.add(change.position)
                                        currentStroke = strokePoints.toList()
                                    }

                                    if (strokePoints.isNotEmpty()) {
                                        strokes.add(strokePoints)
                                        currentStroke = emptyList()
                                    }
                                }
                            }
                    ) {
                        // Faint horizontal baseline guide
                        val baselineY = size.height * 0.75f
                        drawLine(
                            color = Color(0x22000000),
                            start = Offset(24f, baselineY),
                            end = Offset(size.width - 24f, baselineY),
                            strokeWidth = 1.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                        )

                        val strokeColor = Color(selectedColorArgb)
                        val strokeWidthPx = 4.5f

                        // Draw completed strokes
                        val allStrokesToDraw = if (currentStroke.isNotEmpty()) strokes + listOf(currentStroke) else strokes
                        for (stroke in allStrokesToDraw) {
                            if (stroke.isEmpty()) continue
                            if (stroke.size == 1) {
                                drawCircle(
                                    color = strokeColor,
                                    radius = strokeWidthPx / 2f,
                                    center = stroke[0]
                                )
                                continue
                            }

                            val path = Path()
                            val first = stroke[0]
                            var currX = first.x
                            var currY = first.y
                            path.moveTo(currX, currY)

                            for (i in 1 until stroke.size) {
                                val next = stroke[i]
                                val nextX = next.x
                                val nextY = next.y
                                val midX = (currX + nextX) / 2f
                                val midY = (currY + nextY) / 2f
                                path.quadraticTo(currX, currY, midX, midY)
                                currX = nextX
                                currY = nextY
                            }
                            path.lineTo(currX, currY)
                            drawPath(
                                path = path,
                                color = strokeColor,
                                style = Stroke(
                                    width = strokeWidthPx,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Ink Color Selectors (Black, Navy Blue, Dark Red)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (colorArgb in inkColors) {
                        val isSelected = (selectedColorArgb == colorArgb)
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(colorArgb))
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f),
                                    shape = CircleShape
                                )
                                .clickable { selectedColorArgb = colorArgb },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        strokes.clear()
                        currentStroke = emptyList()
                    },
                    enabled = strokes.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(stringResource(R.string.clear_signature))
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(
                        onClick = {
                            if (strokes.isEmpty()) return@Button

                            // 1. Compute tight bounding box across all drawn points
                            var minX = Float.MAX_VALUE
                            var maxX = Float.MIN_VALUE
                            var minY = Float.MAX_VALUE
                            var maxY = Float.MIN_VALUE

                            for (stroke in strokes) {
                                for (pt in stroke) {
                                    if (pt.x < minX) minX = pt.x
                                    if (pt.x > maxX) maxX = pt.x
                                    if (pt.y < minY) minY = pt.y
                                    if (pt.y > maxY) maxY = pt.y
                                }
                            }

                            // 2. Guard against degenerate single-dot / flat-line boxes
                            val rawWidth = maxX - minX
                            val rawHeight = maxY - minY
                            val minDim = 12f
                            val clampedWidth = rawWidth.coerceAtLeast(minDim)
                            val clampedHeight = rawHeight.coerceAtLeast(minDim)

                            val centerX = (minX + maxX) / 2f
                            val centerY = (minY + maxY) / 2f
                            val effMinX = centerX - clampedWidth / 2f
                            val effMaxX = centerX + clampedWidth / 2f
                            val effMinY = centerY - clampedHeight / 2f
                            val effMaxY = centerY + clampedHeight / 2f

                            // Add ~4% padding around the box
                            val padX = clampedWidth * 0.04f
                            val padY = clampedHeight * 0.04f
                            val paddedLeft = effMinX - padX
                            val paddedTop = effMinY - padY
                            val paddedWidth = (effMaxX - effMinX) + (2f * padX)
                            val paddedHeight = (effMaxY - effMinY) + (2f * padY)

                            // 3. Normalize every point into 0f..1f relative to padded box
                            val normalizedStrokes = strokes.map { stroke ->
                                stroke.map { pt ->
                                    val normX = ((pt.x - paddedLeft) / paddedWidth).coerceIn(0f, 1f)
                                    val normY = ((pt.y - paddedTop) / paddedHeight).coerceIn(0f, 1f)
                                    NormalizedPoint(normX, normY)
                                }
                            }

                            // 4. Compute aspectRatio clamped in 0.4f..6.0f
                            val aspectRatio = (paddedWidth / paddedHeight).coerceIn(0.4f, 6.0f)

                            // 5. Pass SavedSignature to onSave
                            onSave(
                                SavedSignature(
                                    strokes = normalizedStrokes,
                                    aspectRatio = aspectRatio,
                                    colorArgb = selectedColorArgb
                                )
                            )
                        },
                        enabled = strokes.isNotEmpty(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }
            }
        },
        dismissButton = null
    )
}
