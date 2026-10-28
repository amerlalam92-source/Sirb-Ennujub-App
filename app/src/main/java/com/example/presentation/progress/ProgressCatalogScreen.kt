package com.example.presentation.progress

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.WorkspacePremium
import com.example.presentation.common.AppAlertDialog
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Tab
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.progress.BadgeLevel
import com.example.domain.progress.ProgressRequirement
import com.example.domain.progress.labelRes
import java.lang.String.format

/**
 * شاشة كتالوج بطاقة التقدم (القبول، المبتدئ، الثاني، الأول)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressCatalogScreen(
    viewModel: ProgressCatalogViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingRequirement by remember { mutableStateOf<ProgressRequirement?>(null) }
    var requirementToDelete by remember { mutableStateOf<ProgressRequirement?>(null) }
    var showCannotDeleteAlert by remember { mutableStateOf(false) }

    val levels = remember {
        listOf(
            BadgeLevel.ACCEPTANCE,
            BadgeLevel.BEGINNER,
            BadgeLevel.SECOND,
            BadgeLevel.FIRST
        )
    }

    LaunchedEffect(uiState) {
        if (uiState is ProgressCatalogUiState.Success) {
            val msg = (uiState as ProgressCatalogUiState.Success).userMessage
            if (msg != null) {
                snackbarHostState.showSnackbar(msg)
                viewModel.clearUserMessage()
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CustomTopAppBar(
                title = stringResource(id = R.string.progress_catalog_title),
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("catalog_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.back_action),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_requirement_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(id = R.string.progress_add_requirement)
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (val state = uiState) {
                is ProgressCatalogUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                is ProgressCatalogUiState.Error -> {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp)
                    )
                }

                is ProgressCatalogUiState.Success -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // شريط التبويبات للشارات الأربعة
                        ScrollableTabRow(
                            selectedTabIndex = levels.indexOf(state.selectedLevel).coerceAtLeast(0),
                            edgePadding = 16.dp,
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary
                        ) {
                            levels.forEach { level ->
                                val selected = state.selectedLevel == level
                                Tab(
                                    selected = selected,
                                    onClick = { viewModel.selectLevel(level) },
                                    text = {
                                        Text(
                                            text = stringResource(id = level.labelRes()),
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    modifier = Modifier.testTag("catalog_tab_${level.key}")
                                )
                            }
                        }

                        // ترويسة عدد المتطلبات
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = format(
                                    com.example.presentation.common.AppLocale.AR_DZ,
                                    stringResource(id = R.string.progress_requirements_count_format),
                                    state.filteredRequirements.size
                                ),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )

                            if (state.totalCount > state.activeCount) {
                                Text(
                                    text = "(${state.activeCount} نشط)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // قائمة المتطلبات
                        if (state.filteredRequirements.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.WorkspacePremium,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.outline
                                    )
                                    Text(
                                        text = stringResource(id = R.string.progress_empty_requirements),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                    OutlinedButton(
                                        onClick = { showAddDialog = true },
                                        modifier = Modifier.testTag("add_first_requirement_button")
                                    ) {
                                        Icon(imageVector = Icons.Default.Add, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(stringResource(id = R.string.progress_add_first_requirement))
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("requirements_list"),
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                itemsIndexed(
                                    items = state.filteredRequirements,
                                    key = { _, item -> item.id }
                                ) { index, item ->
                                    val isFirst = index == 0
                                    val isLast = index == state.filteredRequirements.lastIndex

                                    RequirementItemCard(
                                        requirement = item,
                                        hobbyBadges = state.hobbyBadges,
                                        isFirst = isFirst,
                                        isLast = isLast,
                                        onEdit = { editingRequirement = item },
                                        onToggleArchive = { viewModel.toggleArchive(item) },
                                        onMoveUp = { viewModel.moveUp(item.id) },
                                        onMoveDown = { viewModel.moveDown(item.id) },
                                        onDelete = { requirementToDelete = item }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val hobbyBadges = (uiState as? ProgressCatalogUiState.Success)?.hobbyBadges.orEmpty()

    // حوار إضافة متطلب جديد
    if (showAddDialog) {
        ProgressRequirementFormDialog(
            initialRequirement = null,
            onDismiss = { showAddDialog = false },
            onConfirm = { title, description, linkedCount ->
                viewModel.addRequirement(title, description, linkedCount) {
                    showAddDialog = false
                }
            }
        )
    }

    // حوار تعديل متطلب
    editingRequirement?.let { req ->
        ProgressRequirementFormDialog(
            initialRequirement = req,
            onDismiss = { editingRequirement = null },
            onConfirm = { title, description, linkedCount ->
                viewModel.updateRequirement(
                    req.copy(
                        title = title,
                        description = description,
                        linkedHobbyBadgesCount = linkedCount
                    )
                ) {
                    editingRequirement = null
                }
            }
        )
    }

    // حوار تأكيد الحذف
    requirementToDelete?.let { req ->
        AppAlertDialog(
            onDismissRequest = { requirementToDelete = null },
            title = {
                Text(
                    text = stringResource(id = R.string.progress_delete_dialog_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = { Text(stringResource(id = R.string.progress_delete_dialog_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = req.id
                        requirementToDelete = null
                        viewModel.deleteRequirement(
                            id = id,
                            onCannotDelete = { showCannotDeleteAlert = true }
                        )
                    },
                    modifier = Modifier.testTag("confirm_delete_requirement_button")
                ) {
                    Text(
                        text = stringResource(id = R.string.action_delete_permanent),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { requirementToDelete = null }) {
                    Text(stringResource(id = R.string.cancel_action))
                }
            }
        )
    }

    // تنبيه تعذر الحذف لوجود سجلات مرتبطة
    if (showCannotDeleteAlert) {
        AppAlertDialog(
            onDismissRequest = { showCannotDeleteAlert = false },
            title = { Text("تعذر الحذف", fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(id = R.string.progress_cannot_delete_used)) },
            confirmButton = {
                TextButton(onClick = { showCannotDeleteAlert = false }) {
                    Text("حسناً")
                }
            }
        )
    }
}

@Composable
private fun RequirementItemCard(
    requirement: ProgressRequirement,
    hobbyBadges: List<com.example.domain.hobby.HobbyBadge> = emptyList(),
    isFirst: Boolean,
    isLast: Boolean,
    onEdit: () -> Unit,
    onToggleArchive: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val isArchived = requirement.archived

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
            .testTag("requirement_card_${requirement.id}")
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
                        text = requirement.title,
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

                if (requirement.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = requirement.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val linkedCount = requirement.linkedHobbyBadgesCount
                if (linkedCount != null && linkedCount > 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(
                                text = java.lang.String.format(
                                    com.example.presentation.common.AppLocale.AR_DZ,
                                    stringResource(id = R.string.kafaa_link_count_chip_format),
                                    linkedCount
                                ),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.testTag("requirement_menu_button_${requirement.id}")
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
