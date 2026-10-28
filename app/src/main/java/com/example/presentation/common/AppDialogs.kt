package com.example.presentation.common

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DatePickerColors
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * أغلفة الحوارات الخاصة بالتطبيق (App Dialog Wrappers)
 * تقوم بحقن LocalAppDensity لتجاوز مقياس خط النظام بالكامل fontScale=1.0f
 * وتطبيق مقياس uiScale المخصص للتطبيق على نوافذ الحوارات والمحتويات الداخلية.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties()
) {
    val density = LocalAppDensity.current
    CompositionLocalProvider(LocalDensity provides density) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = {
                CompositionLocalProvider(LocalDensity provides density) {
                    confirmButton()
                }
            },
            modifier = modifier,
            dismissButton = dismissButton?.let { btn ->
                {
                    CompositionLocalProvider(LocalDensity provides density) {
                        btn()
                    }
                }
            },
            icon = icon?.let { ic ->
                {
                    CompositionLocalProvider(LocalDensity provides density) {
                        ic()
                    }
                }
            },
            title = title?.let { t ->
                {
                    CompositionLocalProvider(LocalDensity provides density) {
                        t()
                    }
                }
            },
            text = text?.let { txt ->
                {
                    CompositionLocalProvider(LocalDensity provides density) {
                        txt()
                    }
                }
            },
            shape = shape,
            containerColor = containerColor,
            iconContentColor = iconContentColor,
            titleContentColor = titleContentColor,
            textContentColor = textContentColor,
            tonalElevation = tonalElevation,
            properties = properties
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDatePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    shape: Shape = DatePickerDefaults.shape,
    tonalElevation: Dp = DatePickerDefaults.TonalElevation,
    colors: DatePickerColors = DatePickerDefaults.colors(),
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    content: @Composable ColumnScope.() -> Unit
) {
    val density = LocalAppDensity.current
    CompositionLocalProvider(LocalDensity provides density) {
        DatePickerDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = {
                CompositionLocalProvider(LocalDensity provides density) {
                    confirmButton()
                }
            },
            modifier = modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            dismissButton = dismissButton?.let { btn ->
                {
                    CompositionLocalProvider(LocalDensity provides density) {
                        btn()
                    }
                }
            },
            shape = shape,
            tonalElevation = tonalElevation,
            colors = colors,
            properties = properties,
            content = {
                CompositionLocalProvider(LocalDensity provides density) {
                    content()
                }
            }
        )
    }
}

@Composable
fun AppDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit
) {
    val density = LocalAppDensity.current
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties
    ) {
        CompositionLocalProvider(LocalDensity provides density) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = androidx.compose.material3.rememberModalBottomSheetState(),
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = BottomSheetDefaults.Elevation,
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    content: @Composable ColumnScope.() -> Unit
) {
    val density = LocalAppDensity.current
    CompositionLocalProvider(LocalDensity provides density) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            sheetState = sheetState,
            shape = shape,
            containerColor = containerColor,
            contentColor = contentColor,
            tonalElevation = tonalElevation,
            scrimColor = scrimColor,
            dragHandle = dragHandle?.let { handle ->
                {
                    CompositionLocalProvider(LocalDensity provides density) {
                        handle()
                    }
                }
            },
            content = {
                CompositionLocalProvider(LocalDensity provides density) {
                    content()
                }
            }
        )
    }
}

@Composable
fun AppTimePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val density = LocalAppDensity.current
    AppAlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            CompositionLocalProvider(LocalDensity provides density) {
                confirmButton()
            }
        },
        modifier = modifier
            .fillMaxWidth(0.9f)
            .wrapContentHeight(),
        dismissButton = dismissButton?.let { btn ->
            {
                CompositionLocalProvider(LocalDensity provides density) {
                    btn()
                }
            }
        },
        title = title?.let { t ->
            {
                CompositionLocalProvider(LocalDensity provides density) {
                    t()
                }
            }
        },
        text = {
            CompositionLocalProvider(LocalDensity provides density) {
                content()
            }
        }
    )
}
