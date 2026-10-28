package com.example.presentation.hobby

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.MilitaryTech
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.hobby.HobbyBadge
import com.example.domain.hobby.HobbyBadgeIcon
import java.lang.String.format

/**
 * شاشة كتالوج شارات الهواية
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HobbyBadgesCatalogScreen(
    viewModel: HobbyBadgesCatalogViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToRequirements: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingBadge by remember { mutableStateOf<HobbyBadge?>(null) }
    var badgeToDelete by remember { mutableStateOf<HobbyBadge?>(null) }
    var showCannotDeleteDialog by remember { mutableStateOf(false) }

    val userMessage = (uiState as? HobbyBadgesCatalogUiState.Success)?.userMessage
    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUserMessage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CustomTopAppBar(
                title = stringResource(id = R.string.hobby_catalog_title),
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("hobby_catalog_back_button")
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
                modifier = Modifier.testTag("add_hobby_badge_fab"),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(id = R.string.hobby_add_badge)
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
                is HobbyBadgesCatalogUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is HobbyBadgesCatalogUiState.Error -> {
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
                is HobbyBadgesCatalogUiState.Success -> {
                    val badges = state.badgesWithReqCount
                    if (badges.isEmpty()) {
                        // Empty State
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MilitaryTech,
                                contentDescription = null,
                                modifier = Modifier.size(72.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(id = R.string.hobby_empty_catalog),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { showAddDialog = true },
                                modifier = Modifier.testTag("add_first_hobby_badge_button")
                            ) {
                                Text(stringResource(id = R.string.hobby_add_first_badge))
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(
                                items = badges,
                                key = { _, item -> item.badge.id }
                            ) { index, item ->
                                HobbyBadgeCatalogCard(
                                    item = item,
                                    isFirst = index == 0,
                                    isLast = index == badges.size - 1,
                                    onClick = { onNavigateToRequirements(item.badge.id) },
                                    onEdit = { editingBadge = item.badge },
                                    onManageRequirements = { onNavigateToRequirements(item.badge.id) },
                                    onToggleArchive = {
                                        if (item.badge.archived) {
                                            viewModel.unarchiveBadge(item.badge.id)
                                        } else {
                                            viewModel.archiveBadge(item.badge.id)
                                        }
                                    },
                                    onMoveUp = { viewModel.moveUp(item.badge.id) },
                                    onMoveDown = { viewModel.moveDown(item.badge.id) },
                                    onDelete = { badgeToDelete = item.badge }
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
        HobbyBadgeFormDialog(
            initialBadge = null,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, desc, iconKey ->
                viewModel.addBadge(name, desc, iconKey) {
                    showAddDialog = false
                }
            }
        )
    }

    // Edit dialog
    editingBadge?.let { badge ->
        HobbyBadgeFormDialog(
            initialBadge = badge,
            onDismiss = { editingBadge = null },
            onConfirm = { name, desc, iconKey ->
                viewModel.updateBadge(badge.copy(name = name, description = desc, iconKey = iconKey)) {
                    editingBadge = null
                }
            }
        )
    }

    // Delete confirmation dialog
    badgeToDelete?.let { badge ->
        AppAlertDialog(
            onDismissRequest = { badgeToDelete = null },
            title = {
                Text(
                    text = stringResource(id = R.string.dialog_delete_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = { Text(stringResource(id = R.string.hobby_delete_badge_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = badge.id
                        badgeToDelete = null
                        viewModel.deleteBadge(id, onCannotDelete = {
                            showCannotDeleteDialog = true
                        })
                    },
                    modifier = Modifier.testTag("confirm_delete_hobby_badge_button")
                ) {
                    Text(
                        text = stringResource(id = R.string.delete_action),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { badgeToDelete = null }) {
                    Text(stringResource(id = R.string.cancel_action))
                }
            }
        )
    }

    // Cannot delete because granted dialog
    if (showCannotDeleteDialog) {
        AppAlertDialog(
            onDismissRequest = { showCannotDeleteDialog = false },
            title = {
                Text(
                    text = "تعذر الحذف",
                    fontWeight = FontWeight.Bold
                )
            },
            text = { Text(stringResource(id = R.string.hobby_cannot_delete_granted)) },
            confirmButton = {
                TextButton(onClick = { showCannotDeleteDialog = false }) {
                    Text(stringResource(id = R.string.action_ok))
                }
            }
        )
    }
}

@Composable
private fun HobbyBadgeCatalogCard(
    item: HobbyBadgeWithCount,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onManageRequirements: () -> Unit,
    onToggleArchive: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    val badge = item.badge
    val isArchived = badge.archived
    var menuExpanded by remember { mutableStateOf(false) }

    val cardBg = if (isArchived) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    val contentAlpha = if (isArchived) 0.6f else 1.0f

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isArchived) 0.dp else 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("hobby_badge_card_${badge.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
                .alpha(contentAlpha),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // أيقونة الشارة
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(
                        if (isArchived) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.primaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = HobbyBadgeIcon.resolveIcon(badge.iconKey),
                    contentDescription = null,
                    tint = if (isArchived) MaterialTheme.colorScheme.outline
                    else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // الاسم والوصف وعدد المتطلبات
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = badge.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isArchived) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface
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

                if (badge.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = badge.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // رقاقة عدد المتطلبات
                Text(
                    text = format(
                        com.example.presentation.common.AppLocale.AR_DZ,
                        stringResource(id = R.string.hobby_requirements_count_format),
                        item.activeRequirementsCount
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // قائمة الخيارات ⋮
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.testTag("hobby_badge_menu_${badge.id}")
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
                        text = { Text(stringResource(id = R.string.hobby_edit_badge)) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("إدارة المتطلبات") },
                        leadingIcon = { Icon(Icons.Default.FormatListNumbered, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onManageRequirements()
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
