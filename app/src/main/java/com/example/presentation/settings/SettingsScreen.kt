package com.example.presentation.settings

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Score
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import com.example.domain.evaluation.CriteriaType
import com.example.domain.evaluation.EvaluationCriteria
import com.example.presentation.common.AppAlertDialog
import com.example.presentation.common.CustomTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R

/**
 * شاشة إعدادات التطبيق وسلّم التنقيط الكشفي (Settings Screen)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CustomTopAppBar(
                title = stringResource(id = R.string.settings_title),
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.cancel_action),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
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
                // بطاقة توضيحية حول نظام النقاط
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
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
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Score,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(id = R.string.settings_points_section),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "يُطبَّق سلّم النقاط هذا حصرياً على أنشطة 'اجتماع تدريبي' لتحفيز الأشبال على المواظبة والانضباط.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }

                // عناصر تعديل النقاط (قاعدة الأساس 10 + المؤثرات)
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
                        PointSettingRow(
                            label = stringResource(id = R.string.settings_base_points),
                            points = uiState.basePoints,
                            badgeColor = MaterialTheme.colorScheme.primary,
                            onDecrement = { viewModel.onBasePointsChanged(uiState.basePoints - 1) },
                            onIncrement = { viewModel.onBasePointsChanged(uiState.basePoints + 1) },
                            tag = "base_points"
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        PointSettingRow(
                            label = stringResource(id = R.string.settings_early_bonus),
                            points = uiState.earlyBonus,
                            badgeColor = Color(0xFF2E7D32),
                            prefix = "+",
                            onDecrement = { viewModel.onEarlyBonusChanged(uiState.earlyBonus - 1) },
                            onIncrement = { viewModel.onEarlyBonusChanged(uiState.earlyBonus + 1) },
                            tag = "early_bonus"
                        )

                        PointSettingRow(
                            label = stringResource(id = R.string.settings_late_deduction),
                            points = uiState.lateDeduction,
                            badgeColor = Color(0xFFF57C00),
                            prefix = "-",
                            onDecrement = { viewModel.onLateDeductionChanged(uiState.lateDeduction - 1) },
                            onIncrement = { viewModel.onLateDeductionChanged(uiState.lateDeduction + 1) },
                            tag = "late_deduction"
                        )

                        PointSettingRow(
                            label = stringResource(id = R.string.settings_very_late_deduction),
                            points = uiState.veryLateDeduction,
                            badgeColor = Color(0xFFE64A19),
                            prefix = "-",
                            onDecrement = { viewModel.onVeryLateDeductionChanged(uiState.veryLateDeduction - 1) },
                            onIncrement = { viewModel.onVeryLateDeductionChanged(uiState.veryLateDeduction + 1) },
                            tag = "very_late_deduction"
                        )

                        PointSettingRow(
                            label = stringResource(id = R.string.settings_excused_deduction),
                            points = uiState.excusedDeduction,
                            badgeColor = Color(0xFF0288D1),
                            prefix = "-",
                            onDecrement = { viewModel.onExcusedDeductionChanged(uiState.excusedDeduction - 1) },
                            onIncrement = { viewModel.onExcusedDeductionChanged(uiState.excusedDeduction + 1) },
                            tag = "excused_deduction"
                        )

                        PointSettingRow(
                            label = stringResource(id = R.string.settings_unexcused_deduction),
                            points = uiState.unexcusedDeduction,
                            badgeColor = MaterialTheme.colorScheme.error,
                            prefix = "-",
                            onDecrement = { viewModel.onUnexcusedDeductionChanged(uiState.unexcusedDeduction - 1) },
                            onIncrement = { viewModel.onUnexcusedDeductionChanged(uiState.unexcusedDeduction + 1) },
                            tag = "unexcused_deduction"
                        )

                        // ملخص الحصيلة النهائية للنقاط
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "الصافي المحتسب لكل حالة حضور:",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "حاضر: ${uiState.presentPoints} • متأخر: ${uiState.latePoints} • متأخر جداً: ${uiState.veryLatePoints} • بعذر: ${uiState.absentExcusedPoints} • بدون عذر: ${uiState.absentUnexcusedPoints}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // =============================================================
                // قسم إعدادات التقييم التربوي (نقاط شارات الكفاية والهواية الإضافية)
                // =============================================================
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
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(id = R.string.settings_educational_eval_section),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(id = R.string.settings_educational_eval_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }

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
                        PointSettingRow(
                            label = stringResource(id = R.string.settings_kafaa_badge_points),
                            points = uiState.kafaaBadgePoints,
                            badgeColor = Color(0xFF1B5E20),
                            prefix = "+",
                            onDecrement = { viewModel.onKafaaBadgePointsChanged(uiState.kafaaBadgePoints - 1) },
                            onIncrement = { viewModel.onKafaaBadgePointsChanged(uiState.kafaaBadgePoints + 1) },
                            tag = "kafaa_badge_points"
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        PointSettingRow(
                            label = stringResource(id = R.string.settings_extra_hobby_badge_points),
                            points = uiState.extraHobbyBadgePoints,
                            badgeColor = Color(0xFF0D47A1),
                            prefix = "+",
                            onDecrement = { viewModel.onExtraHobbyBadgePointsChanged(uiState.extraHobbyBadgePoints - 1) },
                            onIncrement = { viewModel.onExtraHobbyBadgePointsChanged(uiState.extraHobbyBadgePoints + 1) },
                            tag = "extra_hobby_badge_points"
                        )
                    }
                }

                // أزرار الحفظ واستعادة الافتراضي لسلم التنقيط والتقييم التربوي
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.resetToDefaults() },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("reset_defaults_button"),
                        shape = RoundedCornerShape(12.dp)
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
                        onClick = { viewModel.saveSettings() },
                        enabled = !uiState.isSaving && !uiState.isSavingPoints,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_settings_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (uiState.isSavingPoints) {
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
                            Text(stringResource(id = R.string.action_save_settings))
                        }
                    }
                }

                // فاصل بصري
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // =============================================================
                // قسم معايير التقييم الديناميكية (Dynamic Evaluation Criteria)
                // =============================================================

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
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
                                .background(MaterialTheme.colorScheme.tertiary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Score,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(id = R.string.settings_eval_section),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(id = R.string.settings_eval_section_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }

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
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "المعايير المعتمدة (${uiState.criteriaList.size})",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Button(
                                onClick = { viewModel.openAddCriteriaDialog() },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("add_criteria_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(id = R.string.settings_eval_add))
                            }
                        }

                        if (uiState.isLoadingCriteria) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        } else if (uiState.criteriaList.isEmpty()) {
                            Text(
                                text = stringResource(id = R.string.settings_eval_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        } else {
                            uiState.criteriaList.forEach { criterion ->
                                EvaluationCriterionItem(
                                    criterion = criterion,
                                    onDelete = { viewModel.onDeleteCriteriaClicked(criterion) }
                                )
                            }
                        }
                    }
                }

                // فاصل بصري بين معايير التقييم وإعدادات المالية
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

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

                // A) Section: مبالغ الاشتراكات الافتراضية
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
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
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

                        // مبلغ الغائب بعذر (default 40)
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

                // B) Section: الرصيد الافتتاحي
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

                // C) Buttons at the bottom of these finance settings
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

                Spacer(modifier = Modifier.height(8.dp))

                // زر تنظيف السجلات المالية المعلقة
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

        // حوار إضافة معيار تقييم جديد
        AddCriteriaDialog(
            isOpen = uiState.isAddCriteriaDialogOpen,
            onDismiss = { viewModel.closeAddCriteriaDialog() },
            onConfirm = { name, maxScore, type ->
                viewModel.addCriteria(name, maxScore, type)
            },
            isSaving = uiState.isSavingCriteria
        )

        // حوار تأكيد حذف معيار تقييم
        val pendingDelete = uiState.pendingDeleteCriteria
        if (pendingDelete != null) {
            AppAlertDialog(
                onDismissRequest = { viewModel.onDeleteCriteriaDismissed() },
                title = {
                    Text(
                        text = stringResource(id = R.string.settings_delete_criteria_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                text = {
                    Text(
                        text = stringResource(id = R.string.settings_delete_criteria_body_format, pendingDelete.name),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.onDeleteCriteriaConfirmed() },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("confirm_delete_criteria_button")
                    ) {
                        Text(stringResource(id = R.string.action_delete))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { viewModel.onDeleteCriteriaDismissed() },
                        modifier = Modifier.testTag("cancel_delete_criteria_button")
                    ) {
                        Text(stringResource(id = R.string.action_cancel))
                    }
                }
            )
        }

        // حوار تأكيد تنظيف السجلات المالية المعلقة
        if (uiState.isCleanOrphanedDialogOpen) {
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

@Composable
private fun PointSettingRow(
    label: String,
    points: Int,
    badgeColor: Color,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    tag: String,
    prefix: String = "",
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(badgeColor)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalIconButton(
                onClick = onDecrement,
                enabled = points > 0,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("decrement_$tag")
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "إنقاص",
                    modifier = Modifier.size(18.dp)
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "$prefix$points",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .testTag("value_$tag"),
                    textAlign = TextAlign.Center
                )
            }

            FilledTonalIconButton(
                onClick = onIncrement,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("increment_$tag")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "زيادة",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun EvaluationCriterionItem(
    criterion: EvaluationCriteria,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = criterion.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val (typeLabel, typeColor) = when (criterion.type) {
                        EvaluationCriteria.TYPE_QURAN, CriteriaType.QURAN.name ->
                            stringResource(id = R.string.settings_eval_type_quran) to Color(0xFF2E7D32)
                        EvaluationCriteria.TYPE_NOTEBOOK, CriteriaType.NOTEBOOK.name ->
                            stringResource(id = R.string.settings_eval_type_notebook) to Color(0xFFE64A19)
                        else ->
                            stringResource(id = R.string.settings_eval_type_leader) to MaterialTheme.colorScheme.primary
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = typeColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = typeLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = typeColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "العلامة: ${criterion.maxScore}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete_criterion_${criterion.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(id = R.string.settings_eval_delete),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun AddCriteriaDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (name: String, maxScore: Int, type: CriteriaType) -> Unit,
    isSaving: Boolean
) {
    if (!isOpen) return

    var name by remember { mutableStateOf("") }
    var maxScore by remember { mutableIntStateOf(10) }
    var selectedType by remember { mutableStateOf(CriteriaType.LEADER_CRITERIA) }

    AppAlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = {
            Text(
                text = stringResource(id = R.string.settings_eval_add),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(id = R.string.settings_eval_name)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_criteria_name_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                // اختيار الدرجة القصوى (10 أو 20)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(id = R.string.settings_eval_max_score),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        listOf(10, 20).forEach { score ->
                            FilterChip(
                                selected = maxScore == score,
                                onClick = { maxScore = score },
                                label = { Text("$score نقاط") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // اختيار نوع المعيار
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(id = R.string.settings_eval_type),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        CriteriaType.values().forEach { type ->
                            val label = when (type) {
                                CriteriaType.LEADER_CRITERIA -> stringResource(id = R.string.settings_eval_type_leader)
                                CriteriaType.QURAN -> stringResource(id = R.string.settings_eval_type_quran)
                                CriteriaType.NOTEBOOK -> stringResource(id = R.string.settings_eval_type_notebook)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedType = type }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedType == type,
                                    onClick = { selectedType = type }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, maxScore, selectedType) },
                enabled = name.isNotBlank() && !isSaving,
                modifier = Modifier.testTag("confirm_add_criteria_button")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(stringResource(id = R.string.action_save))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSaving
            ) {
                Text(stringResource(id = R.string.cancel_action))
            }
        }
    )
}
