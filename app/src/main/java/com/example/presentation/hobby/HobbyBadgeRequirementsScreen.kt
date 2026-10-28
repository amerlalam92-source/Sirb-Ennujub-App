package com.example.presentation.hobby

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Unarchive
import com.example.presentation.common.AppAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.example.presentation.common.CustomTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.hobby.HobbyBadgeRequirement
import java.lang.String.format

/**
 * شاشة متطلبات شارة هواية معينة
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HobbyBadgeRequirementsScreen(
    viewModel: HobbyBadgeRequirementsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingReq by remember { mutableStateOf<HobbyBadgeRequirement?>(null) }
    var reqToDelete by remember { mutableStateOf<HobbyBadgeRequirement?>(null) }
    var showCannotDeleteDialog by remember { mutableStateOf(false) }

    val userMessage = (uiState as? HobbyBadgeRequirementsUiState.Success)?.userMessage
    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUserMessage()
        }
    }

    val badgeName = (uiState as? HobbyBadgeRequirementsUiState.Success)?.badge?.name ?: "شارة الهواية"

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CustomTopAppBar(
                title = format(
                    com.example.presentation.common.AppLocale.AR_DZ,
                    stringResource(id = R.string.hobby_requirements_title_format),
                    badgeName
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("hobby_requirements_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.back_action)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.testTag("add_hobby_requirement_fab"),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(id = R.string.hobby_add_requirement)
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is HobbyBadgeRequirementsUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is HobbyBadgeRequirementsUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                is HobbyBadgeRequirementsUiState.Success -> {
                    val reqs = state.requirements
                    if (reqs.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Checklist,
                                contentDescription = null,
                                modifier = Modifier.size(72.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "لا توجد متطلبات لهذه الشارة بعد",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { showAddDialog = true },
                                modifier = Modifier.testTag("add_first_hobby_req_button")
                            ) {
                                Text(stringResource(id = R.string.hobby_add_requirement))
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(
                                items = reqs,
                                key = { _, item -> item.id }
                            ) { index, req ->
                                HobbyRequirementItemCard(
                                    req = req,
                                    isFirst = index == 0,
                                    isLast = index == reqs.size - 1,
                                    onEdit = { editingReq = req },
                                    onToggleArchive = {
                                        if (req.archived) {
                                            viewModel.unarchiveRequirement(req.id)
                                        } else {
                                            viewModel.archiveRequirement(req.id)
                                        }
                                    },
                                    onMoveUp = { viewModel.moveUp(req.id) },
                                    onMoveDown = { viewModel.moveDown(req.id) },
                                    onDelete = { reqToDelete = req }
                                )
                            }
                            item {
                                Spacer(modifier = Modifier.height(64.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // Add dialog
    if (showAddDialog) {
        HobbyRequirementSimpleDialog(
            initialTitle = "",
            initialDescription = "",
            titleText = stringResource(id = R.string.hobby_add_requirement),
            onDismiss = { showAddDialog = false },
            onConfirm = { title, desc ->
                viewModel.addRequirement(title, desc) {
                    showAddDialog = false
                }
            }
        )
    }

    // Edit dialog
    editingReq?.let { req ->
        HobbyRequirementSimpleDialog(
            initialTitle = req.title,
            initialDescription = req.description,
            titleText = "تعديل المتطلب",
            onDismiss = { editingReq = null },
            onConfirm = { title, desc ->
                viewModel.updateRequirement(req.copy(title = title, description = desc)) {
                    editingReq = null
                }
            }
        )
    }

    // Delete confirm
    reqToDelete?.let { req ->
        AppAlertDialog(
            onDismissRequest = { reqToDelete = null },
            title = {
                Text(
                    text = stringResource(id = R.string.dialog_delete_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = { Text("سيتم حذف هذا المتطلب نهائياً. هل أنت متأكد؟") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = req.id
                        reqToDelete = null
                        viewModel.deleteRequirement(id, onCannotDelete = {
                            showCannotDeleteDialog = true
                        })
                    },
                    modifier = Modifier.testTag("confirm_delete_hobby_req_button")
                ) {
                    Text(
                        text = stringResource(id = R.string.delete_action),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { reqToDelete = null }) {
                    Text(stringResource(id = R.string.cancel_action))
                }
            }
        )
    }

    if (showCannotDeleteDialog) {
        AppAlertDialog(
            onDismissRequest = { showCannotDeleteDialog = false },
            title = {
                Text(text = "تعذر الحذف", fontWeight = FontWeight.Bold)
            },
            text = { Text("لا يمكن حذف المتطلب لأن بعض الأشبال قد أنجزوه بالفعل. يمكنك أرشفته بدلاً من ذلك.") },
            confirmButton = {
                TextButton(onClick = { showCannotDeleteDialog = false }) {
                    Text(stringResource(id = R.string.action_ok))
                }
            }
        )
    }
}

@Composable
private fun HobbyRequirementItemCard(
    req: HobbyBadgeRequirement,
    isFirst: Boolean,
    isLast: Boolean,
    onEdit: () -> Unit,
    onToggleArchive: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    val isArchived = req.archived
    var menuExpanded by remember { mutableStateOf(false) }

    val cardBg = if (isArchived) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    val contentAlpha = if (isArchived) 0.6f else 1.0f

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isArchived) 0.dp else 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hobby_requirement_card_${req.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
                .alpha(contentAlpha),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = req.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isArchived) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (isArchived) {
                        SuggestionChip(
                            onClick = {},
                            label = {
                                Text(
                                    text = stringResource(id = R.string.progress_archived_chip),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    }
                }

                if (req.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = req.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.testTag("hobby_req_menu_${req.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "خيارات",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(id = R.string.action_edit)) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        }
                    )

                    DropdownMenuItem(
                        text = {
                            Text(
                                if (isArchived) stringResource(id = R.string.action_unarchive)
                                else stringResource(id = R.string.action_archive)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                if (isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onToggleArchive()
                        }
                    )

                    if (!isFirst) {
                        DropdownMenuItem(
                            text = { Text(stringResource(id = R.string.action_move_up)) },
                            leadingIcon = { Icon(Icons.Default.ArrowUpward, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onMoveUp()
                            }
                        )
                    }

                    if (!isLast) {
                        DropdownMenuItem(
                            text = { Text(stringResource(id = R.string.action_move_down)) },
                            leadingIcon = { Icon(Icons.Default.ArrowDownward, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onMoveDown()
                            }
                        )
                    }

                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(id = R.string.action_delete_permanent),
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HobbyRequirementSimpleDialog(
    initialTitle: String,
    initialDescription: String,
    titleText: String,
    onDismiss: () -> Unit,
    onConfirm: (title: String, description: String) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var description by remember { mutableStateOf(initialDescription) }
    var isSubmitted by remember { mutableStateOf(false) }

    val isTitleValid = title.trim().length >= 2

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = titleText, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("نص المتطلب") },
                    isError = isSubmitted && !isTitleValid,
                    supportingText = {
                        if (isSubmitted && !isTitleValid) {
                            Text("يجب أن يحتوي نص المتطلب على حرفين على الأقل")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hobby_req_title_input"),
                    singleLine = false,
                    maxLines = 3
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("الوصف (اختياري)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hobby_req_desc_input"),
                    minLines = 2,
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    isSubmitted = true
                    if (isTitleValid) {
                        onConfirm(title.trim(), description.trim())
                    }
                },
                modifier = Modifier.testTag("save_hobby_req_button")
            ) {
                Text(stringResource(id = R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.cancel_action))
            }
        }
    )
}
