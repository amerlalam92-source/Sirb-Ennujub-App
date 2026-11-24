package com.example.presentation.evaluation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.evaluation.CriteriaType
import com.example.domain.evaluation.EvaluationCriteria
import com.example.domain.evaluation.ScoutEvaluation
import com.example.domain.scout.Scout
import com.example.domain.settings.LocalAppTerms
import com.example.domain.settings.replaceCustomWords
import com.example.presentation.common.AppAlertDialog
import com.example.presentation.common.CustomTopAppBar

/**
 * شاشة التقييم الفصلي للأشبال (Quarterly Evaluation Screen)
 * تعرض قائمة الأشبال لكل فصل دراسي (الفصل الأول، الثاني، الثالث) وتتيح رصد المعايير الستة:
 * - 4 معايير خاصة بكل قائد (الأخلاق، المشاركة، الاهتمام، الانضباط) من 20.
 * - معيارين مشتركين (حفظ القرآن، تقييم الكراس) من 20.
 * تدعم صلاحيات القائد الرئيسي (عرض درجات القادة الآخرين للقراءة فقط) وقفل التعديل بعد 7 أيام.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvaluationScreen(
    viewModel: EvaluationViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToMatrix: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val terms = LocalAppTerms.current
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler {
        onNavigateBack()
    }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    // حوار تأكيد الحذف من القائمة المنبثقة لسطر الشبل
    uiState.pendingDeleteEvaluationId?.let { evalId ->
        AppAlertDialog(
            onDismissRequest = { viewModel.setPendingDeleteEvaluation(null) },
            title = {
                Text(
                    text = stringResource(id = R.string.action_delete_evaluation),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(text = replaceCustomWords(stringResource(id = R.string.confirm_delete_evaluation), terms))
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteEvaluation(evalId) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(id = R.string.action_delete_evaluation))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { viewModel.setPendingDeleteEvaluation(null) }) {
                    Text(stringResource(id = R.string.cancel_action))
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CustomTopAppBar(
                title = replaceCustomWords(stringResource(id = R.string.evaluation_screen_title), terms),
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("evaluation_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.cancel_action)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.shareRawPointsPdf(context) },
                        modifier = Modifier.testTag("evaluation_raw_points_pdf_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = stringResource(id = R.string.eval_raw_points_pdf_title),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = onNavigateToMatrix,
                        modifier = Modifier.testTag("evaluation_to_matrix_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = stringResource(id = R.string.eval_matrix_title),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            QuarterlyEvaluationContent(
                selectedTerm = uiState.selectedTerm,
                onTermSelected = { viewModel.onTermSelected(it) },
                scouts = uiState.filteredScouts,
                totalScoutsCount = uiState.totalScoutsCount,
                evaluatedCount = uiState.evaluatedCount,
                uiState = uiState,
                onSearchQueryChanged = { viewModel.onSearchQueryChanged(it) },
                onScoutClick = { viewModel.onScoutSelectedForEvaluation(it) },
                onDeleteMyEvaluation = { evalId -> viewModel.setPendingDeleteEvaluation(evalId) },
                isLoading = uiState.isLoading,
                modifier = Modifier.fillMaxSize()
            )

            // نافذة التقييم الفصلي للشبل (6 معايير من 20)
            uiState.selectedScoutForEvaluation?.let { scout ->
                val sharedCriteriaIds = uiState.criteria.filter { c ->
                    c.type == EvaluationCriteria.TYPE_QURAN ||
                            c.type == CriteriaType.QURAN.name ||
                            c.type == CriteriaType.QURAN.value ||
                            c.type == EvaluationCriteria.TYPE_NOTEBOOK ||
                            c.type == CriteriaType.NOTEBOOK.name ||
                            c.type == CriteriaType.NOTEBOOK.value ||
                            c.name.contains("قرآن") ||
                            c.name.contains("حفظ") ||
                            c.name.contains("كراس")
                }.map { it.id }

                val initialSharedScores = buildMap {
                    sharedCriteriaIds.forEach { criteriaId ->
                        uiState.getLatestSharedScore(scout.id, criteriaId)?.let { score ->
                            put(criteriaId, score)
                        }
                    }
                }

                ScoutEvaluationDialog(
                    scout = scout,
                    criteriaList = uiState.criteria,
                    existingEvaluation = uiState.evaluations[scout.id],
                    isSaving = uiState.isSaving,
                    showPenalty = false,
                    isMainLeader = uiState.isMainLeader,
                    otherLeadersEvaluations = uiState.getOtherLeaderEvaluationsForScout(scout.id),
                    leaderNames = uiState.leaderNames,
                    initialSharedScores = initialSharedScores,
                    initialTestedAmount = uiState.getLatestTestedAmount(scout.id),
                    currentMemorized = uiState.getMemorizedTextForScout(scout.id),
                    onSave = { scoresMap, penaltyAmount, penaltyReason, testedAmount ->
                        viewModel.saveEvaluation(scout.id, scoresMap, penaltyAmount, penaltyReason, testedAmount)
                    },
                    onDelete = { evalId ->
                        viewModel.deleteEvaluation(evalId)
                    },
                    onDismiss = { viewModel.dismissEvaluationDialog() }
                )
            }
        }
    }
}

/**
 * محتوى التقييم الفصلي: اختيار الفصل الدراسي (الفصل الأول، الثاني، الثالث) وقائمة الأشبال
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun QuarterlyEvaluationContent(
    selectedTerm: String,
    onTermSelected: (String) -> Unit,
    scouts: List<Scout>,
    totalScoutsCount: Int,
    evaluatedCount: Int,
    uiState: EvaluationUiState,
    onSearchQueryChanged: (String) -> Unit,
    onScoutClick: (Scout) -> Unit,
    onDeleteMyEvaluation: (String) -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val terms = LocalAppTerms.current
    var dropdownExpanded by remember { mutableStateOf(false) }
    val currentTerm = selectedTerm.ifBlank { "TERM_1" }
    val termLabel = when (currentTerm) {
        "TERM_1" -> stringResource(id = R.string.term_first)
        "TERM_2" -> stringResource(id = R.string.term_second)
        "TERM_3" -> stringResource(id = R.string.term_third)
        else -> currentTerm
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // بطاقة اختيار الفصل الدراسي
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("quarterly_term_selector_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.evaluation_select_term),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.align(Alignment.CenterVertically)
                    ) {
                        Text(
                            text = stringResource(id = R.string.evaluation_tab_quarterly),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = !dropdownExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = termLabel,
                        onValueChange = {},
                        readOnly = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.MenuBook,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                            .testTag("quarterly_term_dropdown")
                    )

                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(id = R.string.term_first)) },
                            onClick = {
                                onTermSelected("TERM_1")
                                dropdownExpanded = false
                            },
                            modifier = Modifier.testTag("term_item_1")
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(id = R.string.term_second)) },
                            onClick = {
                                onTermSelected("TERM_2")
                                dropdownExpanded = false
                            },
                            modifier = Modifier.testTag("term_item_2")
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(id = R.string.term_third)) },
                            onClick = {
                                onTermSelected("TERM_3")
                                dropdownExpanded = false
                            },
                            modifier = Modifier.testTag("term_item_3")
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // شريط إحصائية الإنجاز الفصلي
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "تقييمي: $evaluatedCount من $totalScoutsCount",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = onSearchQueryChanged,
            placeholder = { Text(stringResource(id = R.string.evaluation_search_scouts_hint)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (uiState.searchQuery.isNotBlank()) {
                    IconButton(onClick = { onSearchQueryChanged("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = stringResource(id = R.string.cancel_action)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("evaluation_scout_search_quarterly")
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (isLoading && uiState.evaluations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (scouts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = replaceCustomWords(stringResource(id = R.string.evaluation_no_scouts), terms),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(scouts, key = { it.id }) { scout ->
                    val eval = uiState.evaluations[scout.id]
                    val status = uiState.getEvaluationStatus(scout.id)
                    val isLocked = uiState.isEvaluationLocked(eval)

                    ScoutEvaluationItemCard(
                        scout = scout,
                        status = status,
                        myEvaluation = eval,
                        isLocked = isLocked,
                        onClick = { onScoutClick(scout) },
                        onDeleteMyEvaluation = {
                            eval?.let { onDeleteMyEvaluation(it.id) }
                        }
                    )
                }
            }
        }
    }
}

/**
 * بطاقة الشبل وحالة تقييمه مع قفل التعديل وقائمة ⋮ لحذف التقييم
 */
@Composable
private fun ScoutEvaluationItemCard(
    scout: Scout,
    status: ScoutEvaluationStatus,
    myEvaluation: ScoutEvaluation?,
    isLocked: Boolean = false,
    onClick: () -> Unit,
    onDeleteMyEvaluation: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val isEvaluatedByMe = status == ScoutEvaluationStatus.EVALUATED_BY_ME
    val isEvaluatedByOthers = status == ScoutEvaluationStatus.EVALUATED_BY_OTHERS

    val totalScore = if (isEvaluatedByMe && myEvaluation != null) {
        myEvaluation.criteriaScores.values.sum()
    } else 0

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("scout_evaluation_row_${scout.id}"),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            when {
                isEvaluatedByMe -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                isEvaluatedByOthers -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.outlineVariant
            }
        ),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isEvaluatedByMe -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                isEvaluatedByOthers -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.15f)
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val firstChar = scout.fullName.firstOrNull()?.toString() ?: "ش"
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isEvaluatedByMe -> MaterialTheme.colorScheme.primary
                            isEvaluatedByOthers -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primaryContainer
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = firstChar,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = when {
                        isEvaluatedByMe -> MaterialTheme.colorScheme.onPrimary
                        isEvaluatedByOthers -> MaterialTheme.colorScheme.onTertiary
                        else -> MaterialTheme.colorScheme.onPrimaryContainer
                    }
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = scout.fullName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isEvaluatedByMe && isLocked) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = stringResource(id = R.string.eval_locked_content_desc),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .size(16.dp)
                                .testTag("scout_eval_locked_icon_${scout.id}")
                        )
                    }
                }
                if (scout.school.isNotBlank()) {
                    Text(
                        text = scout.school,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                when (status) {
                    ScoutEvaluationStatus.EVALUATED_BY_ME -> {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(id = R.string.eval_status_evaluated_by_me_format, totalScore),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                    ScoutEvaluationStatus.EVALUATED_BY_OTHERS -> {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Groups,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(id = R.string.eval_status_evaluated_by_others),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                    ScoutEvaluationStatus.NOT_EVALUATED -> {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = stringResource(id = R.string.eval_status_not_evaluated),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // قائمة الخيارات ⋮ لحذف التقييم (تخفى تماماً إذا كان التقييم مقفلاً بمرور 7 أيام)
            if (isEvaluatedByMe && onDeleteMyEvaluation != null && !isLocked) {
                Spacer(modifier = Modifier.width(4.dp))
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("scout_more_options_${scout.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "خيارات التقييم",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(id = R.string.action_delete_evaluation),
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
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
                                onDeleteMyEvaluation()
                            },
                            modifier = Modifier.testTag("menu_delete_eval_${scout.id}")
                        )
                    }
                }
            }
        }
    }
}
