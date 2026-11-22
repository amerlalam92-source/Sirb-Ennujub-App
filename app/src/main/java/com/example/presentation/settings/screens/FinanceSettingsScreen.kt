package com.example.presentation.settings.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.presentation.common.AppAlertDialog
import com.example.presentation.common.CustomTopAppBar
import com.example.presentation.common.LocalAppTerms
import com.example.presentation.settings.SettingsEvent
import com.example.presentation.settings.SettingsViewModel

/**
 * شاشة الإعدادات المالية (FinanceSettingsScreen)
 * تشمل: مبالغ الاشتراكات الافتراضية، الرصيد الافتتاحي، وتنظيف السجلات المعلقة.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceSettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isMainLeader by viewModel.isMainLeader.collectAsStateWithLifecycle()
    val terms = LocalAppTerms.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                else -> {}
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CustomTopAppBar(
                title = "الإعدادات المالية",
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("finance_settings_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.back_action),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading || uiState.isLoadingFinance) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // بطاقة توضيحية حول إعدادات المالية
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(id = R.string.settings_finance_section),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(id = R.string.settings_finance_section_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }

                // قسم مبالغ الاشتراكات الافتراضية
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "مبالغ الاشتراكات الافتراضية للأنشطة",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // مبلغ الحاضر (default 20)
                        OutlinedTextField(
                            value = uiState.presentAmount,
                            onValueChange = { viewModel.onFinanceFieldChanged("present_amount", it) },
                            label = { Text(stringResource(id = R.string.settings_present_amount)) },
                            suffix = { Text(stringResource(id = R.string.settings_currency_suffix)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("present_amount_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // مبلغ المتأخر (default 30)
                        OutlinedTextField(
                            value = uiState.lateAmount,
                            onValueChange = { viewModel.onFinanceFieldChanged("late_amount", it) },
                            label = { Text(stringResource(id = R.string.settings_late_amount)) },
                            suffix = { Text(stringResource(id = R.string.settings_currency_suffix)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("late_amount_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // مبلغ المتأخر جداً (default 40)
                        OutlinedTextField(
                            value = uiState.veryLateAmount,
                            onValueChange = { viewModel.onFinanceFieldChanged("very_late_amount", it) },
                            label = { Text(stringResource(id = R.string.settings_very_late_amount)) },
                            suffix = { Text(stringResource(id = R.string.settings_currency_suffix)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("very_late_amount_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // مبلغ الغائب بعذر (default 20/40)
                        OutlinedTextField(
                            value = uiState.absentExcusedAmount,
                            onValueChange = { viewModel.onFinanceFieldChanged("absent_excused_amount", it) },
                            label = { Text(stringResource(id = R.string.settings_excused_amount)) },
                            suffix = { Text(stringResource(id = R.string.settings_currency_suffix)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("excused_amount_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // مبلغ الغائب بدون عذر (default 50)
                        OutlinedTextField(
                            value = uiState.absentUnexcusedAmount,
                            onValueChange = { viewModel.onFinanceFieldChanged("absent_unexcused_amount", it) },
                            label = { Text(stringResource(id = R.string.settings_unexcused_amount)) },
                            suffix = { Text(stringResource(id = R.string.settings_currency_suffix)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("unexcused_amount_input"),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                // قسم الرصيد الافتتاحي
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.settings_opening_balance_section),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(id = R.string.settings_opening_balance_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = uiState.openingBalance,
                            onValueChange = { viewModel.onFinanceFieldChanged("opening_balance", it) },
                            label = { Text(stringResource(id = R.string.settings_opening_balance_label)) },
                            suffix = { Text(stringResource(id = R.string.settings_currency_suffix)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("opening_balance_input"),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                // أزرار الحفظ واستعادة الافتراضي للإعدادات المالية
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { viewModel.resetFinanceToDefaults() },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("reset_finance_defaults_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(id = R.string.action_reset_defaults))
                    }

                    Button(
                        onClick = { viewModel.saveFinanceSettings() },
                        enabled = !uiState.isSavingFinance,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_finance_settings_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (uiState.isSavingFinance) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(id = R.string.settings_save_finance))
                        }
                    }
                }

                if (isMainLeader) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    // زر تنظيف السجلات المالية المعلقة (للقائد الرئيسي فقط)
                    OutlinedButton(
                        onClick = { viewModel.onOpenCleanOrphanedDialog() },
                        enabled = !uiState.isCleaningOrphanedRecords,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("clean_orphaned_finance_button"),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (uiState.isCleaningOrphanedRecords) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(id = R.string.settings_cleaning_orphaned))
                        } else {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(id = R.string.settings_clean_orphaned_records))
                        }
                    }
                }
            }
        }

        // حوار تأكيد تنظيف السجلات المالية المعلقة
        if (isMainLeader && uiState.isCleanOrphanedDialogOpen) {
            AppAlertDialog(
                onDismissRequest = { viewModel.onDismissCleanOrphanedDialog() },
                title = {
                    Text(
                        text = stringResource(id = R.string.dialog_clean_orphaned_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                text = {
                    Text(
                        text = stringResource(id = R.string.dialog_clean_orphaned_message),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.onConfirmCleanOrphanedRecords() },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("confirm_clean_orphaned_button")
                    ) {
                        Text(stringResource(id = R.string.action_clean))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { viewModel.onDismissCleanOrphanedDialog() },
                        modifier = Modifier.testTag("cancel_clean_orphaned_button")
                    ) {
                        Text(stringResource(id = R.string.action_cancel))
                    }
                }
            )
        }
    }
}
