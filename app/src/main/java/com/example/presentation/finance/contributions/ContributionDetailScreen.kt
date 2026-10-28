package com.example.presentation.finance.contributions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Tune
import com.example.presentation.common.AppAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.finance.FinanceContribution
import com.example.presentation.common.AppLocale
import com.example.presentation.common.CustomTopAppBar
import com.example.ui.theme.EnrolledGreenContentDark
import com.example.ui.theme.EnrolledGreenContentLight

/**
 * شاشة تفاصيل مساهمات النشاط الكشفي (ContributionDetailScreen)
 * تعرض تفاصيل مبالغ المساهمات للمشاركين مع إمكانية التسديد، والتصفية والإضافة والتعديل.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ContributionDetailScreen(
    viewModel: ContributionDetailViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val isDark = isSystemInDarkTheme()

    BackHandler(onBack = onNavigateBack)

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    val collectedColor = if (isDark) EnrolledGreenContentDark else EnrolledGreenContentLight
    val positiveGreen = collectedColor
    var showDeleteAllConfirmationDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CustomTopAppBar(
                title = if (uiState.activityTitle.isNotBlank()) "مساهمات: ${uiState.activityTitle}" else stringResource(id = R.string.contributions_title),
                subtitle = stringResource(id = R.string.contributions_subtitle),
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("contribution_detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.action_back)
                        )
                    }
                },
                actions = {
                    // زر إضافة مشارك
                    IconButton(
                        onClick = viewModel::onOpenAddScoutDialog,
                        modifier = Modifier.testTag("action_add_scout_contribution")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = stringResource(id = R.string.contributions_add_participant),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // زر ضبط إعدادات المساهمة للنشاط
                    IconButton(
                        onClick = viewModel::onOpenDefaultSettingsDialog,
                        modifier = Modifier.testTag("action_contribution_settings")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = stringResource(id = R.string.contributions_set_activity_default),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // زر حذف كل التحصيلات لهذا النشاط
                    IconButton(
                        onClick = { showDeleteAllConfirmationDialog = true },
                        modifier = Modifier.testTag("action_delete_all_collections")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(id = R.string.action_delete_all_collections),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (uiState.isLoading && uiState.contributions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1) STATS BAR (شريط الإحصائيات العلوي)
                    item(key = "contribution_stats_bar") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("contribution_detail_stats_card"),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp, horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceAround,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // عدد المشاركين
                                Column(
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "${uiState.participantsCount} مشارك",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "الكل",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        maxLines = 1
                                    )
                                }

                                // المطلوب
                                Column(
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "${uiState.totalRequired} دج",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "المطلوب",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        maxLines = 1
                                    )
                                }

                                // المُحصَّل
                                Column(
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "${uiState.totalPaid} دج",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = positiveGreen,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "المُحصَّل",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        maxLines = 1
                                    )
                                }

                                // المتبقي
                                Column(
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "${uiState.totalDebt} دج",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (uiState.totalDebt > 0) MaterialTheme.colorScheme.error else positiveGreen,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "المتبقي",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    // 2) FILTER ROW (أزرار التصفية)
                    item(key = "contribution_filter_chips_row") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = uiState.selectedFilter == ContributionDetailFilter.ALL,
                                onClick = { viewModel.onFilterSelected(ContributionDetailFilter.ALL) },
                                label = {
                                    Text(
                                        text = String.format(AppLocale.AR_DZ, "الكل (%d)", uiState.participantsCount),
                                        fontWeight = if (uiState.selectedFilter == ContributionDetailFilter.ALL) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                shape = RoundedCornerShape(20.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier.testTag("filter_chip_all")
                            )

                            FilterChip(
                                selected = uiState.selectedFilter == ContributionDetailFilter.DEBTORS,
                                onClick = { viewModel.onFilterSelected(ContributionDetailFilter.DEBTORS) },
                                label = {
                                    Text(
                                        text = String.format(AppLocale.AR_DZ, "المدينون (%d)", uiState.debtorsCount),
                                        fontWeight = if (uiState.selectedFilter == ContributionDetailFilter.DEBTORS) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                shape = RoundedCornerShape(20.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier.testTag("filter_chip_debtors")
                            )

                            FilterChip(
                                selected = uiState.selectedFilter == ContributionDetailFilter.PAID,
                                onClick = { viewModel.onFilterSelected(ContributionDetailFilter.PAID) },
                                label = {
                                    Text(
                                        text = String.format(AppLocale.AR_DZ, "المسددون (%d)", uiState.paidCount),
                                        fontWeight = if (uiState.selectedFilter == ContributionDetailFilter.PAID) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                shape = RoundedCornerShape(20.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier.testTag("filter_chip_paid")
                            )
                        }
                    }

                    // 3) LIST OF SCOUTS / EMPTY STATES
                    if (uiState.filteredContributions.isEmpty()) {
                        item(key = "empty_filtered_state") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                when (uiState.selectedFilter) {
                                    ContributionDetailFilter.DEBTORS -> {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(48.dp)
                                            )
                                            Text(
                                                text = stringResource(id = R.string.contributions_all_paid_empty),
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                    ContributionDetailFilter.PAID -> {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.HourglassEmpty,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(48.dp)
                                            )
                                            Text(
                                                text = stringResource(id = R.string.contributions_none_paid_empty),
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                    ContributionDetailFilter.ALL -> {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Text(
                                                text = stringResource(id = R.string.contributions_no_scouts_empty),
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center
                                            )
                                            Button(
                                                onClick = viewModel::onOpenAddScoutDialog,
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.testTag("add_first_scout_btn")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PersonAdd,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.size(8.dp))
                                                Text(stringResource(id = R.string.contributions_add_participant))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        items(
                            items = uiState.filteredContributions,
                            key = { it.id }
                        ) { contribution ->
                            ScoutContributionRow(
                                contribution = contribution,
                                positiveColor = positiveGreen,
                                isSaving = uiState.savingScoutId == contribution.scoutId,
                                onPayFull = { viewModel.onPayFullClick(contribution) },
                                onPayPartial = { viewModel.onOpenPaymentDialog(contribution) },
                                onEdit = { viewModel.onOpenEditDialog(contribution) },
                                onDelete = { viewModel.onDeleteClick(contribution) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialog التسديد الجزئي
    uiState.paymentDialogContribution?.let { contrib ->
        ContributionPaymentDialog(
            contribution = contrib,
            onConfirm = viewModel::onConfirmPartialPayment,
            onDismiss = viewModel::onDismissPaymentDialog
        )
    }

    // Dialog التعديل
    uiState.editDialogContribution?.let { contrib ->
        ContributionEditDialog(
            contribution = contrib,
            onSave = viewModel::onSaveEditedContribution,
            onDismiss = viewModel::onDismissEditDialog
        )
    }

    // Dialog إضافة مشاركين للمساهمة (Multi-Select)
    if (uiState.showAddScoutDialog) {
        ContributionAddScoutDialog(
            amount = uiState.newParticipantAmount,
            selectedScoutIds = uiState.selectedNewScoutIds,
            availableScouts = uiState.availableScoutsForAddition,
            onAmountChange = viewModel::onNewParticipantAmountChanged,
            onToggleScout = viewModel::onNewScoutToggled,
            onSelectAll = viewModel::onSelectAllAvailable,
            onClearAll = viewModel::onClearSelection,
            onConfirm = viewModel::onAddParticipantsConfirmed,
            onDismiss = viewModel::onDismissAddScoutDialog
        )
    }

    // Dialog إعدادات مساهمة النشاط
    if (uiState.showDefaultSettingsDialog) {
        ContributionActivitySettingsDialog(
            currentRequired = uiState.isContributionRequired,
            currentDefaultAmount = uiState.defaultAmount,
            onSave = viewModel::onSaveActivityContributionSettings,
            onDismiss = viewModel::onDismissDefaultSettingsDialog
        )
    }

    // Dialog تأكيد الحذف
    uiState.deleteConfirmContribution?.let { target ->
        AppAlertDialog(
            onDismissRequest = viewModel::onDismissDelete,
            modifier = Modifier.testTag("delete_contribution_confirm_dialog"),
            title = {
                Text(
                    text = stringResource(id = R.string.contributions_delete_participant),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = "هل أنت متأكد من حذف الشبل ${target.scoutFullName} من قائمة المساهمات لهذا النشاط؟",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = viewModel::onConfirmDelete,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("confirm_delete_scout_btn")
                ) {
                    Text(stringResource(id = R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = viewModel::onDismissDelete,
                    modifier = Modifier.testTag("cancel_delete_scout_btn")
                ) {
                    Text(stringResource(id = R.string.cancel_action))
                }
            }
        )
    }

    if (showDeleteAllConfirmationDialog) {
        AppAlertDialog(
            onDismissRequest = { showDeleteAllConfirmationDialog = false },
            title = { Text(stringResource(id = R.string.dialog_delete_all_collections_title)) },
            text = { Text(stringResource(id = R.string.dialog_delete_all_collections_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteAllConfirmationDialog = false
                        viewModel.deleteAllCollectionsForActivity(onDeleted = onNavigateBack)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("contribution_detail_confirm_delete_all_button")
                ) {
                    Text(stringResource(id = R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteAllConfirmationDialog = false },
                    modifier = Modifier.testTag("contribution_detail_cancel_delete_all_button")
                ) {
                    Text(stringResource(id = R.string.action_cancel))
                }
            }
        )
    }
}

/**
 * بطاقة سطر الشبل في قائمة المساهمات
 */
@Composable
private fun ScoutContributionRow(
    contribution: FinanceContribution,
    positiveColor: Color,
    isSaving: Boolean,
    onPayFull: () -> Unit,
    onPayPartial: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("scout_contribution_card_${contribution.scoutId}"),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // الصف 1: اسم الشبل وشارة الحالة وقائمة الخيارات
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = contribution.scoutFullName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!contribution.notes.isNullOrBlank()) {
                        Text(
                            text = contribution.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (contribution.isFullyPaid()) {
                        Box(
                            modifier = Modifier
                                .background(positiveColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = stringResource(id = R.string.contributions_paid_full_badge),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = positiveColor
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = String.format(AppLocale.AR_DZ, "متبقي %d دج", contribution.debtAmount),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }

                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.testTag("scout_menu_${contribution.scoutId}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(id = R.string.action_more_options),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(id = R.string.contributions_edit_contribution)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onEdit()
                                }
                            )

                            DropdownMenuItem(
                                text = { Text(stringResource(id = R.string.contributions_delete_participant)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
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

            // الصف 2: المبالغ (المطلوب، المسدد، المتبقي)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = String.format(AppLocale.AR_DZ, "المطلوب: %d دج", contribution.requiredAmount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = String.format(AppLocale.AR_DZ, "المسدد: %d دج", contribution.paidAmount),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = positiveColor
                )
            }

            // الصف 3: أزرار التسديد (فقط إذا كان هناك دين متبقي)
            if (contribution.debtAmount > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onPayFull,
                        enabled = !isSaving,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pay_full_scout_${contribution.scoutId}")
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = stringResource(id = R.string.contributions_pay_full),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    OutlinedButton(
                        onClick = onPayPartial,
                        enabled = !isSaving,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pay_partial_scout_${contribution.scoutId}")
                    ) {
                        Text(
                            text = stringResource(id = R.string.contributions_pay_partial),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}
