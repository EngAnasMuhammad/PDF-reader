package com.anasdev.pdfreader.presentation.pdf

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Slider
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.anasdev.pdfreader.R
import com.anasdev.pdfreader.domain.model.AnnotationTool
import com.anasdev.pdfreader.domain.model.Signature
import com.anasdev.pdfreader.domain.model.TextBox
import com.anasdev.pdfreader.presentation.state.PdfViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TooltipIconButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState()
    ) {
        IconButton(onClick = onClick, modifier = modifier, enabled = enabled) {
            content()
        }
    }
}

@Composable
fun SelectedTextBoxActions(
    selectedTextBox: TextBox?,
    isLandscape: Boolean,
    onEditText: (TextBox) -> Unit,
    onDeleteText: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        if (isLandscape) {
            Column(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TooltipIconButton(
                    label = stringResource(R.string.edit_text),
                    onClick = { selectedTextBox?.let { onEditText(it) } }
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.edit_text),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                TooltipIconButton(
                    label = stringResource(R.string.delete_annotation),
                    onClick = { selectedTextBox?.let { onDeleteText(it.id) } }
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete_annotation),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TooltipIconButton(
                    label = stringResource(R.string.edit_text),
                    onClick = { selectedTextBox?.let { onEditText(it) } }
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.edit_text),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                TooltipIconButton(
                    label = stringResource(R.string.delete_annotation),
                    onClick = { selectedTextBox?.let { onDeleteText(it.id) } }
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete_annotation),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun SelectedSignatureActions(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    isLandscape: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        val deleteButton = @Composable {
            TooltipIconButton(
                label = stringResource(R.string.delete_signature),
                onClick = onDelete
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete_signature),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
        if (isLandscape) {
            Column(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                deleteButton()
            }
        } else {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                deleteButton()
            }
        }
    }
}

@Composable
fun EditToolButtonsContent(
    activeTool: AnnotationTool,
    selectedColorArgb: Int,
    strokeWidthRatio: Float,
    textFontSizeRatio: Float,
    isStylePopoverVisible: Boolean,
    isLandscape: Boolean,
    onSelectPen: () -> Unit,
    onSelectHighlighter: () -> Unit,
    onSelectEraser: () -> Unit,
    onSelectText: () -> Unit,
    onSignatureClick: () -> Unit,
    onToggleStylePopover: () -> Unit,
    onMinimize: () -> Unit
) {
    val buttonSize = if (isLandscape) 38.dp else 42.dp

    // 1. Pen
    val isPenSelected = activeTool == AnnotationTool.PEN
    TooltipIconButton(
        label = stringResource(R.string.pen),
        onClick = onSelectPen,
        modifier = Modifier
            .size(buttonSize)
            .background(
                color = if (isPenSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                shape = CircleShape
            )
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_tool_pen),
            contentDescription = stringResource(R.string.pen),
            tint = if (isPenSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // 2. Highlighter
    val isHighlighterSelected = activeTool == AnnotationTool.HIGHLIGHTER
    TooltipIconButton(
        label = stringResource(R.string.highlighter),
        onClick = onSelectHighlighter,
        modifier = Modifier
            .size(buttonSize)
            .background(
                color = if (isHighlighterSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                shape = CircleShape
            )
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_tool_highlighter),
            contentDescription = stringResource(R.string.highlighter),
            tint = if (isHighlighterSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // 3. Eraser
    val isEraserSelected = activeTool == AnnotationTool.ERASER
    TooltipIconButton(
        label = stringResource(R.string.eraser),
        onClick = onSelectEraser,
        modifier = Modifier
            .size(buttonSize)
            .background(
                color = if (isEraserSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                shape = CircleShape
            )
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_tool_eraser),
            contentDescription = stringResource(R.string.eraser),
            tint = if (isEraserSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // 4. Text Tool
    val isTextSelected = activeTool == AnnotationTool.TEXT
    TooltipIconButton(
        label = stringResource(R.string.text_tool),
        onClick = onSelectText,
        modifier = Modifier
            .size(buttonSize)
            .background(
                color = if (isTextSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                shape = CircleShape
            )
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_tool_text),
            contentDescription = stringResource(R.string.text_tool),
            tint = if (isTextSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // 5. Signature Tool
    val isSignatureSelected = activeTool == AnnotationTool.SIGNATURE
    TooltipIconButton(
        label = stringResource(R.string.signature_tool),
        onClick = onSignatureClick,
        modifier = Modifier
            .size(buttonSize)
            .background(
                color = if (isSignatureSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                shape = CircleShape
            )
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_tool_signature),
            contentDescription = stringResource(R.string.signature_tool),
            tint = if (isSignatureSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // 6. Style Selector Button (visible when Pen, Highlighter, or Text is active)
    if (activeTool == AnnotationTool.PEN || activeTool == AnnotationTool.HIGHLIGHTER || activeTool == AnnotationTool.TEXT) {
        val previewDotSize = if (activeTool == AnnotationTool.TEXT) {
            val fraction = ((textFontSizeRatio - PdfViewModel.MIN_TEXT_FONT_SIZE) /
                    (PdfViewModel.MAX_TEXT_FONT_SIZE - PdfViewModel.MIN_TEXT_FONT_SIZE)).coerceIn(0f, 1f)
            8.dp + (14.dp * fraction)
        } else {
            val (rangeMin, rangeMax) = if (activeTool == AnnotationTool.PEN) {
                0.002f to 0.015f
            } else {
                0.010f to 0.050f
            }
            val fraction = ((strokeWidthRatio - rangeMin) / (rangeMax - rangeMin)).coerceIn(0f, 1f)
            8.dp + (14.dp * fraction)
        }

        TooltipIconButton(
            label = stringResource(if (activeTool == AnnotationTool.TEXT) R.string.text_size else R.string.stroke_style),
            onClick = onToggleStylePopover,
            modifier = Modifier
                .size(buttonSize)
                .background(
                    color = if (isStylePopoverVisible) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    shape = CircleShape
                )
        ) {
            Box(
                modifier = Modifier
                    .size(if (isLandscape) 26.dp else 28.dp)
                    .border(
                        width = if (isStylePopoverVisible) 2.dp else 1.5.dp,
                        color = if (isStylePopoverVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(previewDotSize)
                        .background(Color(selectedColorArgb), CircleShape)
                )
            }
        }
    }

    // 6. Minimize / Hide Toolbar Button
    TooltipIconButton(
        label = stringResource(R.string.hide_toolbar),
        onClick = onMinimize,
        modifier = Modifier.size(buttonSize)
    ) {
        Icon(
            imageVector = if (isLandscape) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.Default.KeyboardArrowDown,
            contentDescription = stringResource(R.string.hide_toolbar),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun StylePopoverCard(
    activeTool: AnnotationTool,
    selectedColorArgb: Int,
    strokeWidthRatio: Float,
    textFontSizeRatio: Float,
    isLandscape: Boolean,
    onSelectColor: (Int) -> Unit,
    onStrokeWidthChange: (Float) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 8.dp,
        shadowElevation = 10.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        modifier = modifier.widthIn(max = if (isLandscape) 280.dp else 400.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val colorPalette = when (activeTool) {
                AnnotationTool.HIGHLIGHTER -> PdfViewModel.HIGHLIGHTER_COLORS
                else -> PdfViewModel.PEN_COLORS
            }

            // Color Swatches
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                colorPalette.forEach { colorArgb ->
                    val isSelected = (selectedColorArgb == colorArgb)
                    val color = Color(colorArgb)
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(color)
                            .clickable { onSelectColor(colorArgb) }
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (activeTool == AnnotationTool.TEXT) {
                // Font Size Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.text_size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Slider(
                        value = textFontSizeRatio.coerceIn(PdfViewModel.MIN_TEXT_FONT_SIZE, PdfViewModel.MAX_TEXT_FONT_SIZE),
                        onValueChange = onFontSizeChange,
                        valueRange = PdfViewModel.MIN_TEXT_FONT_SIZE..PdfViewModel.MAX_TEXT_FONT_SIZE,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                // Stroke Thickness Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.stroke_width),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    val (rangeMin, rangeMax) = if (activeTool == AnnotationTool.PEN) {
                        0.002f to 0.015f
                    } else {
                        0.010f to 0.050f
                    }
                    Slider(
                        value = strokeWidthRatio.coerceIn(rangeMin, rangeMax),
                        onValueChange = onStrokeWidthChange,
                        valueRange = rangeMin..rangeMax,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MinimizedEditToolbarFab(
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(stringResource(R.string.show_toolbar)) } },
        state = rememberTooltipState()
    ) {
        SmallFloatingActionButton(
            onClick = onExpand,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = CircleShape,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
            modifier = modifier
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = stringResource(R.string.show_toolbar)
            )
        }
    }
}

@Composable
fun EditModeFloatingToolbar(
    isLandscape: Boolean,
    activeTool: AnnotationTool,
    selectedColorArgb: Int,
    strokeWidthRatio: Float,
    textFontSizeRatio: Float,
    selectedTextBox: TextBox?,
    selectedSignature: Signature? = null,
    isStylePopoverVisible: Boolean,
    onSelectPen: () -> Unit,
    onSelectHighlighter: () -> Unit,
    onSelectEraser: () -> Unit,
    onSelectText: () -> Unit,
    onSignatureClick: () -> Unit,
    onToggleStylePopover: () -> Unit,
    onSelectColor: (Int) -> Unit,
    onStrokeWidthChange: (Float) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onEditText: (TextBox) -> Unit,
    onDeleteText: (String) -> Unit,
    onDeleteSignature: () -> Unit = {},
    onMinimize: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isLandscape) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
        ) {
            // Selected TextBox Mini Action Pill (Landscape)
            AnimatedVisibility(
                visible = selectedTextBox != null && !isStylePopoverVisible,
                enter = slideInHorizontally(initialOffsetX = { it / 2 }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it / 2 }) + fadeOut()
            ) {
                SelectedTextBoxActions(
                    selectedTextBox = selectedTextBox,
                    isLandscape = true,
                    onEditText = onEditText,
                    onDeleteText = onDeleteText
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            // Selected Signature Mini Action Pill (Landscape)
            AnimatedVisibility(
                visible = selectedSignature != null && !isStylePopoverVisible,
                enter = slideInHorizontally(initialOffsetX = { it / 2 }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it / 2 }) + fadeOut()
            ) {
                SelectedSignatureActions(
                    isLandscape = true,
                    onDelete = onDeleteSignature
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            // Style Popover Card (anchored to the start/left of the side rail)
            AnimatedVisibility(
                visible = isStylePopoverVisible && (activeTool == AnnotationTool.PEN || activeTool == AnnotationTool.HIGHLIGHTER || activeTool == AnnotationTool.TEXT),
                enter = slideInHorizontally(initialOffsetX = { it / 2 }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it / 2 }) + fadeOut()
            ) {
                StylePopoverCard(
                    activeTool = activeTool,
                    selectedColorArgb = selectedColorArgb,
                    strokeWidthRatio = strokeWidthRatio,
                    textFontSizeRatio = textFontSizeRatio,
                    isLandscape = true,
                    onSelectColor = onSelectColor,
                    onStrokeWidthChange = onStrokeWidthChange,
                    onFontSizeChange = onFontSizeChange
                )
                Spacer(modifier = Modifier.width(10.dp))
            }

            // Primary Toolbar Rail
            Surface(
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    EditToolButtonsContent(
                        activeTool = activeTool,
                        selectedColorArgb = selectedColorArgb,
                        strokeWidthRatio = strokeWidthRatio,
                        textFontSizeRatio = textFontSizeRatio,
                        isStylePopoverVisible = isStylePopoverVisible,
                        isLandscape = true,
                        onSelectPen = onSelectPen,
                        onSelectHighlighter = onSelectHighlighter,
                        onSelectEraser = onSelectEraser,
                        onSelectText = onSelectText,
                        onSignatureClick = onSignatureClick,
                        onToggleStylePopover = onToggleStylePopover,
                        onMinimize = onMinimize
                    )
                }
            }
        }
    } else {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier
        ) {
            // Selected TextBox Mini Action Pill (Portrait)
            AnimatedVisibility(
                visible = selectedTextBox != null && !isStylePopoverVisible,
                enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
            ) {
                SelectedTextBoxActions(
                    selectedTextBox = selectedTextBox,
                    isLandscape = false,
                    onEditText = onEditText,
                    onDeleteText = onDeleteText
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Selected Signature Mini Action Pill (Portrait)
            AnimatedVisibility(
                visible = selectedSignature != null && !isStylePopoverVisible,
                enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
            ) {
                SelectedSignatureActions(
                    isLandscape = false,
                    onDelete = onDeleteSignature
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Style Popover Card (anchored above the bottom pill)
            AnimatedVisibility(
                visible = isStylePopoverVisible && (activeTool == AnnotationTool.PEN || activeTool == AnnotationTool.HIGHLIGHTER || activeTool == AnnotationTool.TEXT),
                enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
            ) {
                StylePopoverCard(
                    activeTool = activeTool,
                    selectedColorArgb = selectedColorArgb,
                    strokeWidthRatio = strokeWidthRatio,
                    textFontSizeRatio = textFontSizeRatio,
                    isLandscape = false,
                    onSelectColor = onSelectColor,
                    onStrokeWidthChange = onStrokeWidthChange,
                    onFontSizeChange = onFontSizeChange
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Primary Toolbar Pill
            Surface(
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EditToolButtonsContent(
                        activeTool = activeTool,
                        selectedColorArgb = selectedColorArgb,
                        strokeWidthRatio = strokeWidthRatio,
                        textFontSizeRatio = textFontSizeRatio,
                        isStylePopoverVisible = isStylePopoverVisible,
                        isLandscape = false,
                        onSelectPen = onSelectPen,
                        onSelectHighlighter = onSelectHighlighter,
                        onSelectEraser = onSelectEraser,
                        onSelectText = onSelectText,
                        onSignatureClick = onSignatureClick,
                        onToggleStylePopover = onToggleStylePopover,
                        onMinimize = onMinimize
                    )
                }
            }
        }
    }
}
