package com.example.presentation.finance.expenses

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ReceiptLong
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.finance.ExpenseCategory
import com.example.domain.finance.FinanceTransaction
import com.example.presentation.common.AppLocale
import java.text.SimpleDateFormat
import java.util.Date

/**
 * شاشة إدارة المصاريف الكشفية (ExpensesScreen)
 * توفر عمليات الإضافة والتعديل والحذف وتصفية المصاريف حسب التصنيف مع ملخص مالي علوي.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(
    viewModel: ExpensesViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val isDark = isSystemInDarkTheme()

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { msg ->
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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CustomTopAppBar(
                title = stringResource(id = R.string.expenses_title),
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("expenses_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.action_back)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.onAddClick() },
                modifier = Modifier.testTag("expenses_fab_add"),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(id = R.string.expenses_add)
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
            if (uiState.isLoading && uiState.expenses.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // 1) SUMMARY CARD (الملخص المالي العلوي للمصاريف)
                    ExpenseSummaryCard(
                        totalExpenses = uiState.totalExpenses,
                        monthlyTotal = uiState.monthlyTotal,
                        totalCount = uiState.totalCount,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    // 2) FILTER ROW (أشرطة تصفية التصنيفات)
                    ExpenseFilterRow(
                        selectedCategory = uiState.selectedCategory,
                        onCategorySelected = { viewModel.onCategoryFilterChanged(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )

                    // 3) LIST (قائمة المصاريف المسجلة)
                    if (uiState.filteredExpenses.isEmpty()) {
                        ExpenseEmptyState(
                            hasFilter = uiState.selectedCategory != null,
                            onAddFirstClick = { viewModel.onAddClick() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .testTag("expenses_list"),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(
                                items = uiState.filteredExpenses,
                                key = { it.id }
                            ) { expense ->
                                ExpenseItemCard(
                                    expense = expense,
                                    onEdit = { viewModel.onEditClick(expense) },
                                    onDelete = { id -> viewModel.onDeleteClick(expense) },
                                    isDark = isDark
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog الإضافة / التعديل
    uiState.formState?.let { formState ->
        ExpenseFormDialog(
            formState = formState,
            onAmountChange = { viewModel.onAmountChanged(it) },
            onCategoryChange = { viewModel.onCategoryChanged(it) },
            onDateChange = { viewModel.onDateChanged(it) },
            onDescriptionChange = { viewModel.onDescriptionChanged(it) },
            onSave = { viewModel.onSaveExpense() },
            onDismiss = { viewModel.onDismissForm() }
        )
    }

    // Dialog تأكيد الحذف
    uiState.deleteConfirmExpense?.let { targetExpense ->
        AppAlertDialog(
            onDismissRequest = { viewModel.onDismissDelete() },
            modifier = Modifier.testTag("expense_delete_dialog"),
            title = {
                Text(
                    text = stringResource(id = R.string.confirm_delete_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(text = stringResource(id = R.string.expenses_delete_confirm))
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.onDeleteTransaction(targetExpense.id) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("expense_delete_confirm_button")
                ) {
                    Text(stringResource(id = R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.onDismissDelete() },
                    modifier = Modifier.testTag("expense_delete_cancel_button")
                ) {
                    Text(stringResource(id = R.string.cancel_action))
                }
            }
        )
    }
}

/**
 * بطاقة ملخص المصاريف العلوية
 */
@Composable
private fun ExpenseSummaryCard(
    totalExpenses: Int,
    monthlyTotal: Int,
    totalCount: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.testTag("expenses_summary_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(id = R.string.expenses_total_label),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = String.format(AppLocale.AR_DZ, "%d دج", totalExpenses),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.error
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(id = R.string.expenses_this_month_format, monthlyTotal),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )

                Text(
                    text = stringResource(id = R.string.expenses_count_format, totalCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * شريط تصفية التصنيفات
 */
@Composable
private fun ExpenseFilterRow(
    selectedCategory: ExpenseCategory?,
    onCategorySelected: (ExpenseCategory?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // شريحة الكل
        FilterChip(
            selected = selectedCategory == null,
            onClick = { onCategorySelected(null) },
            label = { Text("الكل") },
            modifier = Modifier.testTag("expense_filter_all"),
            shape = RoundedCornerShape(12.dp),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
            )
        )

        ExpenseCategory.values().forEach { category ->
            FilterChip(
                selected = selectedCategory == category,
                onClick = { onCategorySelected(category) },
                label = { Text(category.arabicLabel) },
                modifier = Modifier.testTag("expense_filter_${category.key}"),
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

/**
 * بطاقة عرض عنصر المصروف
 */
@Composable
private fun ExpenseItemCard(
    expense: FinanceTransaction,
    onEdit: () -> Unit,
    onDelete: (String) -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("d MMMM yyyy", AppLocale.AR_DZ) }
    val formattedDate = remember(expense.date) { dateFormat.format(Date(expense.date)) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onEdit() }
            .testTag("expense_card_${expense.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // RIGHT side (Start in RTL): Category / Description + Date + Category Badge
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val category = expense.category ?: ExpenseCategory.OTHER
                val categoryColor = getCategoryColor(category, isDark)
                val title = if (expense.description.isNotBlank()) expense.description else category.arabicLabel

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = categoryColor.copy(alpha = if (isDark) 0.35f else 0.15f),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = category.arabicLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = categoryColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // LEFT side (End in RTL): Amount + Menu
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${expense.amount} دج",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.testTag("expense_menu_button_${expense.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "خيارات المصروف",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(id = R.string.action_edit)) },
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
                            },
                            modifier = Modifier.testTag("expense_menu_edit_${expense.id}")
                        )

                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(id = R.string.action_delete),
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDelete(expense.id)
                            },
                            modifier = Modifier.testTag("expense_menu_delete_${expense.id}")
                        )
                    }
                }
            }
        }
    }
}

/**
 * حالة القائمة الفارغة للمصاريف
 */
@Composable
private fun ExpenseEmptyState(
    hasFilter: Boolean,
    onAddFirstClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.ReceiptLong,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (hasFilter) "لا توجد مصاريف في هذا التصنيف" else stringResource(id = R.string.expenses_empty),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (!hasFilter) {
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onAddFirstClick,
                modifier = Modifier.testTag("expenses_add_first_button")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(id = R.string.expenses_add_first))
            }
        }
    }
}

/**
 * لون مخصص لكل تصنيف مصروف
 */
private fun getCategoryColor(category: ExpenseCategory, isDark: Boolean): Color {
    return when (category) {
        ExpenseCategory.TRANSPORT -> if (isDark) Color(0xFF64B5F6) else Color(0xFF1976D2)
        ExpenseCategory.FOOD -> if (isDark) Color(0xFFFFB74D) else Color(0xFFF57C00)
        ExpenseCategory.SUPPLIES -> if (isDark) Color(0xFF4DB6AC) else Color(0xFF00796B)
        ExpenseCategory.ACTIVITIES -> if (isDark) Color(0xFFBA68C8) else Color(0xFF7B1FA2)
        ExpenseCategory.ADMIN -> if (isDark) Color(0xFF90A4AE) else Color(0xFF455A64)
        ExpenseCategory.CAMP -> if (isDark) Color(0xFF81C784) else Color(0xFF388E3C)
        ExpenseCategory.CHARITY -> if (isDark) Color(0xFFE57373) else Color(0xFFD32F2F)
        ExpenseCategory.OTHER -> if (isDark) Color(0xFFA1887F) else Color(0xFF5D4037)
    }
}
