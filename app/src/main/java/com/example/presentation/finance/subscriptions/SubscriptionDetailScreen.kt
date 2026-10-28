package com.example.presentation.finance.subscriptions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Tune
import com.example.presentation.common.AppAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.finance.FinanceSubscription
import com.example.domain.finance.SubscriptionStatus
import com.example.presentation.common.CustomTopAppBar
import com.example.ui.theme.EnrolledGreenContentDark
import com.example.ui.theme.EnrolledGreenContentLight

/**
 * شاشة تفاصيل اشتراكات نشاط محدد (SubscriptionDetailScreen)
 * تعرض شريط الإحصائيات وفلاتر التصفية وقائمة أشبال النشاط مع تحديث ديناميكي للمبالغ والديون.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SubscriptionDetailScreen(
    viewModel: SubscriptionDetailViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val isDark = isSystemInDarkTheme()
    val collectedColor = if (isDark) EnrolledGreenContentDark else EnrolledGreenContentLight

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

    val displayTitle = uiState.activityTitle.ifBlank {
        stringResource(id = R.string.subscriptions_title)
    }
    var showDeleteConfirmationDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CustomTopAppBar(
                title = displayTitle,
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("subscription_detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.action_back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.onOpenOverrideDialog() },
                        modifier = Modifier.testTag("subscription_detail_override_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = stringResource(id = R.string.subscription_override_action)
                        )
                    }
                    IconButton(
                        onClick = { showDeleteConfirmationDialog = true },
                        modifier = Modifier.testTag("subscription_detail_delete_all_button")
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // 1) شريط الملخص العلوي للنشاط (Stats Bar)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
                    .testTag("subscription_detail_summary_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                )
            ) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SubscriptionStatItem(
                        label = "عدد الأشبال",
                        value = "${uiState.scoutsCount}"
                    )
                    SubscriptionStatItem(
                        label = "المطلوب",
                        value = "${uiState.totalRequired} دج"
                    )
                    SubscriptionStatItem(
                        label = "المُحصَّل",
                        value = "${uiState.totalPaid} دج",
                        valueColor = collectedColor
                    )
                    SubscriptionStatItem(
                        label = "المتبقي",
                        value = "${uiState.totalDebt} دج",
                        valueColor = if (uiState.totalDebt > 0) MaterialTheme.colorScheme.error else collectedColor
                    )
                }
            }

            // 2) شريط الفلاتر (Filter Row)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // فلتر "الكل"
                FilterChip(
                    selected = uiState.selectedFilter == SubscriptionDetailFilter.ALL,
                    onClick = { viewModel.setFilter(SubscriptionDetailFilter.ALL) },
                    label = {
                        Text(
                            text = stringResource(
                                id = R.string.subscription_detail_filter_all_format,
                                uiState.totalCount
                            )
                        )
                    },
                    leadingIcon = if (uiState.selectedFilter == SubscriptionDetailFilter.ALL) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("subscription_detail_filter_all")
                )

                // فلتر "المدينون"
                FilterChip(
                    selected = uiState.selectedFilter == SubscriptionDetailFilter.DEBTORS,
                    onClick = { viewModel.setFilter(SubscriptionDetailFilter.DEBTORS) },
                    label = {
                        Text(
                            text = stringResource(
                                id = R.string.subscription_detail_filter_debtors_format,
                                uiState.debtorsCount
                            )
                        )
                    },
                    leadingIcon = if (uiState.selectedFilter == SubscriptionDetailFilter.DEBTORS) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("subscription_detail_filter_debtors")
                )

                // فلتر "المسددون"
                FilterChip(
                    selected = uiState.selectedFilter == SubscriptionDetailFilter.PAID,
                    onClick = { viewModel.setFilter(SubscriptionDetailFilter.PAID) },
                    label = {
                        Text(
                            text = stringResource(
                                id = R.string.subscription_detail_filter_paid_format,
                                uiState.paidCount
                            )
                        )
                    },
                    leadingIcon = if (uiState.selectedFilter == SubscriptionDetailFilter.PAID) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("subscription_detail_filter_paid")
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 3) & 4) القائمة أو الحالات الفارغة (List Filtering & Empty States)
            if (uiState.isLoading && uiState.subscriptions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (uiState.filteredSubscriptions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when (uiState.selectedFilter) {
                        SubscriptionDetailFilter.DEBTORS -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.testTag("subscription_detail_empty_debtors")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(RoundedCornerShape(36.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = stringResource(id = R.string.subscription_detail_empty_debtors),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        SubscriptionDetailFilter.PAID -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.testTag("subscription_detail_empty_paid")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(RoundedCornerShape(36.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.HourglassEmpty,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = stringResource(id = R.string.subscription_detail_empty_paid),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        SubscriptionDetailFilter.ALL -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.testTag("subscription_detail_empty_all")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(RoundedCornerShape(36.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Assignment,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = stringResource(id = R.string.subscriptions_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = uiState.filteredSubscriptions,
                        key = { it.id.ifBlank { it.scoutId } }
                    ) { sub ->
                        val isThisSaving = uiState.savingScoutId == sub.scoutId
                        SubscriptionScoutItem(
                            subscription = sub,
                            onSavePayment = { newPaid ->
                                viewModel.savePayment(sub, newPaid)
                            },
                            isSaving = isThisSaving
                        )
                    }
                }
            }
        }
    }

    // حوار تخصيص مبالغ الاشتراكات لهذا النشاط
    if (uiState.showOverrideDialog) {
        SubscriptionOverrideDialog(
            isCustomOverride = uiState.isCustomOverride,
            overridePresentText = uiState.overridePresentText,
            overrideLateText = uiState.overrideLateText,
            overrideExcusedText = uiState.overrideExcusedText,
            overrideUnexcusedText = uiState.overrideUnexcusedText,
            defaultPresent = uiState.defaultPresent,
            defaultLate = uiState.defaultLate,
            defaultExcused = uiState.defaultExcused,
            defaultUnexcused = uiState.defaultUnexcused,
            isSaving = uiState.isSavingOverride,
            onToggleCustomOverride = { viewModel.onToggleCustomOverride(it) },
            onPresentChange = { viewModel.onOverridePresentChanged(it) },
            onLateChange = { viewModel.onOverrideLateChanged(it) },
            onExcusedChange = { viewModel.onOverrideExcusedChanged(it) },
            onUnexcusedChange = { viewModel.onOverrideUnexcusedChanged(it) },
            onSave = { viewModel.onSaveOverride() },
            onDismiss = { viewModel.onDismissOverrideDialog() }
        )
    }

    if (showDeleteConfirmationDialog) {
        AppAlertDialog(
            onDismissRequest = { showDeleteConfirmationDialog = false },
            title = { Text(stringResource(id = R.string.dialog_delete_all_collections_title)) },
            text = { Text(stringResource(id = R.string.dialog_delete_all_collections_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmationDialog = false
                        viewModel.deleteAllCollectionsForActivity(onDeleted = onNavigateBack)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("subscription_detail_confirm_delete_button")
                ) {
                    Text(stringResource(id = R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmationDialog = false },
                    modifier = Modifier.testTag("subscription_detail_cancel_delete_button")
                ) {
                    Text(stringResource(id = R.string.action_cancel))
                }
            }
        )
    }
}

/**
 * عنصر صف اشتراك الشبل في تفاصيل النشاط مع مؤشر التسديد الكامل وشارات الحالة والمبالغ.
 */
@Composable
private fun SubscriptionScoutItem(
    subscription: FinanceSubscription,
    onSavePayment: (newPaidAmount: Int) -> Unit,
    isSaving: Boolean,
    modifier: Modifier = Modifier
) {
    var textValue by remember(subscription.paidAmount) {
        mutableStateOf(subscription.paidAmount.toString())
    }

    val parsedPaid = textValue.toIntOrNull() ?: 0
    val hasChanged = parsedPaid != subscription.paidAmount

    val isDark = isSystemInDarkTheme()
    val collectedColor = if (isDark) EnrolledGreenContentDark else EnrolledGreenContentLight

    // ألوان شارة حالة الحضور
    val (statusBg, statusFg) = when (subscription.attendanceStatus) {
        SubscriptionStatus.PRESENT -> {
            if (isDark) Color(0xFF1B4A2A) to Color(0xFFA5D6A7)
            else Color(0xFFE8F5E9) to Color(0xFF1B5E20)
        }
        SubscriptionStatus.LATE -> {
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        }
        SubscriptionStatus.VERY_LATE -> {
            if (isDark) Color(0xFF4A1F0D) to Color(0xFFFFAB91)
            else Color(0xFFFBE9E7) to Color(0xFFE64A19)
        }
        SubscriptionStatus.ABSENT_EXCUSED -> {
            if (isDark) Color(0xFF4E2C00) to Color(0xFFFFCC80)
            else Color(0xFFFFE0B2) to Color(0xFFE65100)
        }
        SubscriptionStatus.ABSENT_UNEXCUSED -> {
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("subscription_scout_row_${subscription.scoutId}"),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Row 1: Scout name + (optional green "مسدَّد" chip) + attendance status chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = subscription.scoutFullName.ifBlank { "شبل كشفي" },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // شارة "مسدَّد" الخضراء عند تسديد كامل الدين (debtAmount == 0)
                    if (subscription.debtAmount == 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = if (isDark) Color(0xFF1B4A2A) else Color(0xFFE8F5E9),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("subscription_settled_chip_${subscription.scoutId}")
                        ) {
                            Text(
                                text = stringResource(id = R.string.subscription_detail_chip_settled),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isDark) Color(0xFFA5D6A7) else Color(0xFF1B5E20),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    color = statusBg,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = subscription.attendanceStatus.arabicLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = statusFg,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Row 2: Amounts: المطلوب / المدفوع / الدين
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(id = R.string.subscriptions_required_format, subscription.requiredAmount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(id = R.string.subscriptions_paid_format, subscription.paidAmount),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = collectedColor
                )
                // المتبقي: بالأخضر إذا كان 0 دج، وبالأحمر إذا كان هناك دين
                Text(
                    text = stringResource(id = R.string.subscriptions_debt_format, subscription.debtAmount),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = if (subscription.debtAmount == 0) {
                        collectedColor
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Row 3: Editing and quick action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() } && input.length <= 6) {
                            textValue = input
                        }
                    },
                    modifier = Modifier
                        .width(120.dp)
                        .testTag("subscription_paid_input_${subscription.scoutId}"),
                    label = { Text(stringResource(id = R.string.subscription_detail_paid_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                Button(
                    onClick = {
                        onSavePayment(parsedPaid)
                    },
                    enabled = hasChanged && !isSaving,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(),
                    modifier = Modifier.testTag("subscription_save_button_${subscription.scoutId}")
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = stringResource(id = R.string.subscription_detail_save),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // أزرار التسديد السريع
                if (subscription.paidAmount < subscription.requiredAmount) {
                    TextButton(
                        onClick = {
                            textValue = subscription.requiredAmount.toString()
                            onSavePayment(subscription.requiredAmount)
                        },
                        enabled = !isSaving,
                        modifier = Modifier.testTag("subscription_pay_full_${subscription.scoutId}")
                    ) {
                        Text(
                            text = stringResource(id = R.string.subscription_detail_pay_full),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                } else if (subscription.debtAmount > 0) {
                    TextButton(
                        onClick = {
                            textValue = subscription.requiredAmount.toString()
                            onSavePayment(subscription.requiredAmount)
                        },
                        enabled = !isSaving,
                        modifier = Modifier.testTag("subscription_pay_debt_${subscription.scoutId}")
                    ) {
                        Text(
                            text = stringResource(id = R.string.subscription_detail_pay_debt),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubscriptionStatItem(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = valueColor,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}
