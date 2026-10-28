package com.example.presentation.evaluation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import com.example.domain.activity.Activity
import com.example.domain.evaluation.CriteriaType
import com.example.domain.evaluation.EvaluationCriteria
import com.example.domain.evaluation.ScoutEvaluation
import com.example.domain.scout.Scout
import com.example.presentation.common.AppAlertDialog
import com.example.presentation.common.AppLocale
import com.example.presentation.common.CustomTopAppBar
import java.text.SimpleDateFormat
import java.util.Date

/**
 * شاشة تقييم الأشبال (Evaluation Screen)
 * تنقسم إلى تبويبين:
 * 1. تقييم الأنشطة: اختيار النشاط -> رصد معايير القائد (الانضباط، الأخلاق، المشاركة، الحيوية) + العقوبة.
 * 2. التقييم الفصلي: اختيار الفصل الدراسي (الفصل الأول، الثاني، الثالث) -> تقييم موحد يظهر لجميع القادة مع حفظ القرآن والكراس.
 * تدعم قفل التعديل التلقائي بعد مرور 7 أيام مع إظهار أيقونة القفل وإخفاء خيار الحذف.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvaluationScreen(
    viewModel: EvaluationViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToMatrix: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // التحكم بزر الرجوع: في تبويب الأنشطة إذا اختير نشاط، يعود للخطوة 1 أولاً
    BackHandler {
        if (uiState.selectedTab == 0 && uiState.selectedActivity != null) {
            viewModel.clearSelectedActivity()
        } else {
            onNavigateBack()
        }
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
                Text(text = stringResource(id = R.string.confirm_delete_evaluation))
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
                title = stringResource(id = R.string.evaluation_screen_title),
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (uiState.selectedTab == 0 && uiState.selectedActivity != null) {
                                viewModel.clearSelectedActivity()
                            } else {
                                onNavigateBack()
                            }
                        },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // شريط التبويبات (تقييم الأنشطة / التقييم الفصلي)
            TabRow(
                selectedTabIndex = uiState.selectedTab,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("evaluation_tab_row")
            ) {
                Tab(
                    selected = uiState.selectedTab == 0,
                    onClick = { viewModel.onTabSelected(0) },
                    text = {
                        Text(
                            text = stringResource(id = R.string.evaluation_tab_activities),
                            fontWeight = if (uiState.selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Event,
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.testTag("evaluation_tab_activities")
                )
                Tab(
                    selected = uiState.selectedTab == 1,
                    onClick = { viewModel.onTabSelected(1) },
                    text = {
                        Text(
                            text = stringResource(id = R.string.evaluation_tab_quarterly),
                            fontWeight = if (uiState.selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.testTag("evaluation_tab_quarterly")
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (uiState.selectedTab == 0) {
                    // ==================== التبويب 1: تقييم الأنشطة ====================
                    if (uiState.selectedActivity == null) {
                        Step1SelectActivityContent(
                            activities = uiState.activities,
                            isLoading = uiState.isLoading,
                            onActivitySelected = { viewModel.onActivitySelected(it) },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Step2ScoutsEvaluationContent(
                            selectedActivity = uiState.selectedActivity!!,
                            scouts = uiState.filteredScouts,
                            totalScoutsCount = uiState.totalScoutsCount,
                            evaluatedCount = uiState.evaluatedCount,
                            uiState = uiState,
                            onSearchQueryChanged = { viewModel.onSearchQueryChanged(it) },
                            onChangeActivity = { viewModel.clearSelectedActivity() },
                            onScoutClick = { viewModel.onScoutSelectedForEvaluation(it) },
                            onDeleteMyEvaluation = { evalId -> viewModel.setPendingDeleteEvaluation(evalId) },
                            isLoading = uiState.isLoading,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    // ==================== التبويب 2: التقييم الفصلي ====================
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
                }

                // نافذة تقييم الشبل
                uiState.selectedScoutForEvaluation?.let { scout ->
                    val isActivityTab = uiState.selectedTab == 0
                    val targetCriteria = uiState.criteria.filter { criteria ->
                        if (isActivityTab) {
                            criteria.type == EvaluationCriteria.TYPE_LEADER_CRITERIA ||
                                    criteria.type == CriteriaType.LEADER_CRITERIA.name ||
                                    criteria.type == CriteriaType.LEADER_CRITERIA.value
                        } else {
                            criteria.type == EvaluationCriteria.TYPE_QURAN ||
                                    criteria.type == CriteriaType.QURAN.name ||
                                    criteria.type == CriteriaType.QURAN.value ||
                                    criteria.type == EvaluationCriteria.TYPE_NOTEBOOK ||
                                    criteria.type == CriteriaType.NOTEBOOK.name ||
                                    criteria.type == CriteriaType.NOTEBOOK.value
                        }
                    }

                    ScoutEvaluationDialog(
                        scout = scout,
                        criteriaList = targetCriteria,
                        existingEvaluation = uiState.evaluations[scout.id],
                        isSaving = uiState.isSaving,
                        showPenalty = isActivityTab,
                        currentMemorized = if (!isActivityTab) uiState.getMemorizedTextForScout(scout.id) else null,
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
}

/**
 * محتوى الخطوة 1 (تقييم الأنشطة): قائمة اختيار النشاط
 */
@Composable
private fun Step1SelectActivityContent(
    activities: List<Activity>,
    isLoading: Boolean,
    onActivitySelected: (Activity) -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormatter = remember { SimpleDateFormat("yyyy/MM/dd", AppLocale.AR_DZ) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // بطاقة ترويسة الخطوة الأولى
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
                        imageVector = Icons.Default.Event,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = stringResource(id = R.string.evaluation_step1_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = stringResource(id = R.string.evaluation_step1_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading && activities.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (activities.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(id = R.string.evaluation_no_activities),
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
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(activities, key = { it.id }) { activity ->
                    val dateStr = if (activity.startDate > 0) {
                        dateFormatter.format(Date(activity.startDate))
                    } else "—"

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onActivitySelected(activity) }
                            .testTag("activity_card_${activity.id}"),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = activity.title,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = activity.activityType.arabicLabel,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Text(
                                        text = dateStr,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (activity.location.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.LocationOn,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = activity.location,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * محتوى الخطوة 2 (تقييم الأنشطة): قائمة الأشبال وتقييم كل شبل
 */
@Composable
private fun Step2ScoutsEvaluationContent(
    selectedActivity: Activity,
    scouts: List<Scout>,
    totalScoutsCount: Int,
    evaluatedCount: Int,
    uiState: EvaluationUiState,
    onSearchQueryChanged: (String) -> Unit,
    onChangeActivity: () -> Unit,
    onScoutClick: (Scout) -> Unit,
    onDeleteMyEvaluation: (String) -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val dateFormatter = remember { SimpleDateFormat("yyyy/MM/dd", AppLocale.AR_DZ) }
    val dateStr = if (selectedActivity.startDate > 0) {
        dateFormatter.format(Date(selectedActivity.startDate))
    } else "—"

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // بطاقة تفاصيل النشاط المختار مع زر التبديل
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("selected_activity_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedActivity.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${selectedActivity.activityType.arabicLabel} • $dateStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedButton(
                    onClick = onChangeActivity,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("change_activity_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(id = R.string.evaluation_change_activity),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // شريط إحصائية الإنجاز
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
                    text = "تقييمي الشخصي: $evaluatedCount من $totalScoutsCount",
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
                .testTag("evaluation_scout_search")
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
                    text = stringResource(id = R.string.evaluation_no_scouts),
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
                    val myEval = uiState.evaluations[scout.id]
                    val status = uiState.getEvaluationStatus(scout.id)
                    val isLocked = uiState.isEvaluationLocked(myEval)

                    ScoutEvaluationItemCard(
                        scout = scout,
                        status = status,
                        myEvaluation = myEval,
                        isLocked = isLocked,
                        onClick = { onScoutClick(scout) },
                        onDeleteMyEvaluation = {
                            myEval?.let { onDeleteMyEvaluation(it.id) }
                        }
                    )
                }
            }
        }
    }
}

/**
 * محتوى التقييم الفصلي: اختيار الفصل الدراسي (الفصل الأول، الثاني، الثالث) وقائمة الأشبال
 * تقييم موحد يظهر لجميع القادة
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuarterlyEvaluationContent(
    selectedTerm: String?,
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
    var dropdownExpanded by remember { mutableStateOf(false) }
    val currentTerm = selectedTerm ?: "TERM_1"
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
                Text(
                    text = stringResource(id = R.string.evaluation_select_term),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

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

        // شريط إحصائية الإنجاز الفصلي الموحد
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
                    text = "تم التقييم: $evaluatedCount من $totalScoutsCount",
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
                    text = stringResource(id = R.string.evaluation_no_scouts),
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
        val sum = myEvaluation.criteriaScores.values.sum()
        sum - myEvaluation.penaltyAmount
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
                            isEvaluatedByMe -> MaterialTheme.colorScheme.primaryContainer
                            isEvaluatedByOthers -> MaterialTheme.colorScheme.tertiaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = firstChar,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = when {
                        isEvaluatedByMe -> MaterialTheme.colorScheme.onPrimaryContainer
                        isEvaluatedByOthers -> MaterialTheme.colorScheme.onTertiaryContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = scout.fullName,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
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

            Spacer(modifier = Modifier.width(8.dp))

            // شارة حالة التقييم مع أيقونة القفل إذا كان التقييم مقفلاً
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isLocked) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = stringResource(id = R.string.eval_locked_content_desc),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .size(16.dp)
                            .testTag("scout_locked_icon_${scout.id}")
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }

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
