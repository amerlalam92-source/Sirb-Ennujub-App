package com.example.presentation.evaluation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.domain.evaluation.EvaluationCriteria
import com.example.domain.evaluation.ScoutEvaluation
import com.example.domain.scout.Scout
import com.example.presentation.common.AppAlertDialog
import com.example.presentation.common.AppDialog

/**
 * نافذة حوار تقييم الشبل الكشفي (Scout Evaluation Dialog)
 * الميزات:
 * 1. درجة افتراضية كاملة (maxScore) عند التقييم لأول مرة.
 * 2. عدادات (+/-) مع شبكة FlowRow من عمودين ودعم .imePadding() و .verticalScroll().
 * 3. في التقييم الفصلي: عرض المحفوظ المسجل من المدرسة القرآنية وإدخال "المقدار المطلوب اختباره" مع إخفاء العقوبة.
 * 4. قفل التعديل التلقائي بعد مرور 7 أيام (أسبوع) من تاريخ الإنشاء، مع إظهار تحذير بارز وتعطيل جميع أزرار التعديل والحفظ والحذف.
 * 5. زر حذف التقييم مع حوار تأكيد (يظهر فقط إذا كان هناك تقييم مسجل وغير مقفل).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScoutEvaluationDialog(
    scout: Scout,
    criteriaList: List<EvaluationCriteria>,
    existingEvaluation: ScoutEvaluation?,
    isSaving: Boolean,
    showPenalty: Boolean = true,
    currentMemorized: String? = null,
    onSave: (scoresMap: Map<String, Int>, penaltyAmount: Int, penaltyReason: String?, testedAmount: String?) -> Unit,
    onDelete: ((String) -> Unit)? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // التحقق من قفل التعديل بعد 7 أيام (التقييمات الجديدة existingEvaluation == null لا تقفل أبداً)
    val isLocked = remember(existingEvaluation) {
        existingEvaluation != null &&
                (System.currentTimeMillis() - existingEvaluation.createdAt) > (7 * 24 * 60 * 60 * 1000L)
    }

    // خريطة درجات المعايير (CriteriaId -> Int score):
    // إذا كان التقييم لأول مرة تكون الدرجة الافتراضية هي criteria.maxScore (مثلاً 20/20)
    // إذا كان تعديلاً يتم تحميل الدرجات السابقة المحفوظة
    val scoresState = remember(criteriaList, existingEvaluation) {
        mutableStateMapOf<String, Int>().apply {
            criteriaList.forEach { criteria ->
                val initialScore = existingEvaluation?.criteriaScores?.get(criteria.id) ?: criteria.maxScore
                put(criteria.id, initialScore.coerceIn(0, criteria.maxScore))
            }
        }
    }

    var penaltyAmountStr by remember(existingEvaluation) {
        mutableStateOf(existingEvaluation?.penaltyAmount?.takeIf { it > 0 }?.toString() ?: "0")
    }
    var penaltyReason by remember(existingEvaluation) {
        mutableStateOf(existingEvaluation?.penaltyReason ?: "")
    }
    var testedAmount by remember(existingEvaluation) {
        mutableStateOf(existingEvaluation?.testedAmount ?: "")
    }

    var showDeleteConfirmation by remember { mutableStateOf(false) }

    // التحقق من صحة العقوبة
    val penaltyAmount = if (showPenalty) {
        penaltyAmountStr.trim().toIntOrNull() ?: 0
    } else 0
    val isPenaltyInvalid = showPenalty && penaltyAmount < 0
    val isPenaltyReasonMissing = showPenalty && penaltyAmount > 0 && penaltyReason.trim().isBlank()

    val hasAnyError by remember(isPenaltyInvalid, isPenaltyReasonMissing) {
        derivedStateOf {
            isPenaltyInvalid || isPenaltyReasonMissing
        }
    }

    // حساب المجموع الإجمالي
    val totalScorePreview by remember {
        derivedStateOf {
            val sumCriteria = criteriaList.sumOf { criteria ->
                scoresState[criteria.id] ?: 0
            }
            (sumCriteria - penaltyAmount)
        }
    }

    // حوار تأكيد الحذف
    if (showDeleteConfirmation && existingEvaluation != null && onDelete != null) {
        AppAlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = {
                Text(
                    text = stringResource(id = R.string.action_delete_evaluation),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(text = stringResource(id = R.string.confirm_delete_evaluation))
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete(existingEvaluation.id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(id = R.string.action_delete_evaluation))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirmation = false }) {
                    Text(stringResource(id = R.string.cancel_action))
                }
            }
        )
    }

    AppDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = !isSaving,
            dismissOnClickOutside = !isSaving,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth(0.94f)
                .wrapContentHeight()
                .padding(vertical = 16.dp)
                .testTag("scout_evaluation_dialog"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(20.dp)
            ) {
                // ترويسة الحوار
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(id = R.string.evaluation_dialog_title, scout.fullName),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (scout.school.isNotBlank()) {
                                Text(
                                    text = scout.school,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // زر حذف التقييم في الترويسة إن وجد تقييم محفوظ ولم يكن مقفلاً
                        if (existingEvaluation != null && onDelete != null && !isLocked) {
                            IconButton(
                                onClick = { showDeleteConfirmation = true },
                                enabled = !isSaving,
                                modifier = Modifier.testTag("dialog_header_delete_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(id = R.string.action_delete_evaluation),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            enabled = !isSaving,
                            modifier = Modifier.testTag("dialog_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(id = R.string.cancel_action)
                            )
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // تحذير قفل التعديل بعد مرور أسبوع (7 أيام)
                if (isLocked) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("evaluation_locked_warning_card")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(id = R.string.eval_edit_locked_warning),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                // في التقييم الفصلي: عرض المحفوظ المسجل وحقل المقدار المطلوب اختباره
                if (!showPenalty && !currentMemorized.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.MenuBook,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(id = R.string.eval_current_memorized_format, currentMemorized),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    OutlinedTextField(
                        value = testedAmount,
                        onValueChange = { testedAmount = it },
                        enabled = !isLocked && !isSaving,
                        readOnly = isLocked,
                        label = { Text(stringResource(id = R.string.eval_tested_amount_label)) },
                        placeholder = { Text(stringResource(id = R.string.eval_tested_amount_hint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp)
                            .testTag("tested_amount_input")
                    )
                }

                // قسم معايير التقييم
                Text(
                    text = stringResource(id = R.string.evaluation_criteria_section_title),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (criteriaList.isEmpty()) {
                    Text(
                        text = stringResource(id = R.string.evaluation_criteria_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    // شبكة العدادات من عمودين باستخدام FlowRow
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        maxItemsInEachRow = 2,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        criteriaList.forEach { criteria ->
                            val currentScore = scoresState[criteria.id] ?: criteria.maxScore
                            CriteriaStepper(
                                criteria = criteria,
                                currentScore = currentScore,
                                enabled = !isLocked && !isSaving,
                                onScoreChange = { newScore ->
                                    scoresState[criteria.id] = newScore
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // قسم الخصم الإضافي (العقوبة) — خاص بتبويب الأنشطة فقط
                if (showPenalty) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Gavel,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(id = R.string.evaluation_penalty_section_title),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    OutlinedTextField(
                        value = penaltyAmountStr,
                        onValueChange = { input ->
                            penaltyAmountStr = input.filter { it.isDigit() }
                        },
                        enabled = !isLocked && !isSaving,
                        readOnly = isLocked,
                        label = { Text(stringResource(id = R.string.evaluation_penalty_amount)) },
                        isError = isPenaltyInvalid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("penalty_amount_input")
                    )

                    OutlinedTextField(
                        value = penaltyReason,
                        onValueChange = { penaltyReason = it },
                        enabled = !isLocked && !isSaving,
                        readOnly = isLocked,
                        label = { Text(stringResource(id = R.string.evaluation_penalty_reason)) },
                        isError = isPenaltyReasonMissing,
                        supportingText = if (isPenaltyReasonMissing) {
                            {
                                Text(
                                    text = stringResource(id = R.string.evaluation_penalty_reason_required),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        } else null,
                        maxLines = 2,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("penalty_reason_input")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // معاينة مجموع النقاط الإجمالي
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "الدرجة النهائية المحسوبة:",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(id = R.string.evaluation_total_preview, totalScorePreview),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (totalScorePreview >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // أزرار الحذف والحفظ والإلغاء
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (existingEvaluation != null && onDelete != null && !isLocked) {
                        OutlinedButton(
                            onClick = { showDeleteConfirmation = true },
                            enabled = !isSaving,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("dialog_delete_evaluation_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(id = R.string.action_delete_evaluation))
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = onDismiss,
                            enabled = !isSaving,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("dialog_cancel_button")
                        ) {
                            Text(stringResource(id = R.string.cancel_action))
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                val mapToSave = mutableMapOf<String, Int>()
                                criteriaList.forEach { c ->
                                    mapToSave[c.id] = scoresState[c.id] ?: c.maxScore
                                }
                                onSave(
                                    mapToSave,
                                    if (showPenalty) penaltyAmount else 0,
                                    if (showPenalty) penaltyReason.trim().ifBlank { null } else null,
                                    testedAmount.trim().ifBlank { null }
                                )
                            },
                            enabled = !isLocked && !hasAnyError && !isSaving,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("dialog_save_button")
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            } else {
                                Icon(
                                    imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.Save,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(stringResource(id = R.string.action_save))
                        }
                    }
                }
            }
        }
    }
}

/**
 * مكون عداد المعيار (+/-) مع مراعاة قفل التعديل
 */
@Composable
private fun CriteriaStepper(
    criteria: EvaluationCriteria,
    currentScore: Int,
    enabled: Boolean = true,
    onScoreChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.testTag("criteria_stepper_${criteria.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = criteria.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                text = "(من ${criteria.maxScore})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                FilledIconButton(
                    onClick = {
                        if (currentScore > 0) onScoreChange(currentScore - 1)
                    },
                    enabled = enabled && currentScore > 0,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("criteria_minus_${criteria.id}"),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Minus",
                        modifier = Modifier.size(18.dp)
                    )
                }

                Text(
                    text = "$currentScore",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = 28.dp)
                )

                FilledIconButton(
                    onClick = {
                        if (currentScore < criteria.maxScore) onScoreChange(currentScore + 1)
                    },
                    enabled = enabled && currentScore < criteria.maxScore,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("criteria_plus_${criteria.id}"),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Plus",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
