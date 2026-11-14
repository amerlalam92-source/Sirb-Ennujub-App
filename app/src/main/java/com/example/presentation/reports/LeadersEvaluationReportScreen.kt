package com.example.presentation.reports

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.presentation.common.CustomTopAppBar
import kotlinx.coroutines.launch
import java.util.Locale

// أبعاد الأعمدة الثابتة للمصفوفة
private val COL_NAME_WIDTH = 140.dp
private val COL_LEADER_WIDTH = 100.dp
private val COL_OVERALL_WIDTH = 90.dp
private val CELL_HEIGHT = 48.dp

/**
 * شاشة تقرير تقييم القادة
 * مصفوفة تقييم الأشبال من قِبل القادة ومعدلاتهم في الأنشطة
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeadersEvaluationReportScreen(
    viewModel: GeneralReportsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.leadersEvaluationReportData.collectAsStateWithLifecycle()
    val isExportingPdf by viewModel.isExportingPdf.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CustomTopAppBar(
                title = stringResource(id = R.string.report_leaders_evaluation),
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("leaders_evaluation_report_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.back_action),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    if (isExportingPdf) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .size(22.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        IconButton(
                            onClick = {
                                viewModel.exportAndShareLeadersEvaluationReport(context)
                            },
                            modifier = Modifier.testTag("action_share_leaders_pdf")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = stringResource(id = R.string.action_share_pdf),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.rows.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(id = R.string.report_empty_scouts),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            uiState.leaders.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(id = R.string.report_empty_evaluations),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            else -> {
                val horizontalScrollState = rememberScrollState()
                val verticalScrollState = rememberScrollState()

                // حساب إجمالي عرض الجدول بناءً على عدد القادة
                val totalTableWidth = COL_NAME_WIDTH + (COL_LEADER_WIDTH * uiState.leaders.size) + COL_OVERALL_WIDTH

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(verticalScrollState)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. بطاقات الإحصاءات السريعة (شبكة 2×2 بمساحات متكافئة)
                    LeadersSummaryGrid(
                        totalScouts = uiState.totalScouts,
                        leadersCount = uiState.leaders.size,
                        totalEvaluations = uiState.totalEvaluationsCount,
                        groupAverage = uiState.groupOverallAverage
                    )

                    // 2. جدول المصفوفة البيانية لتقييم القادة
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .horizontalScroll(horizontalScrollState),
                            contentAlignment = Alignment.TopStart
                        ) {
                            Column(
                                modifier = Modifier.width(totalTableWidth)
                            ) {
                                // 1. صف الرأس (Header Row)
                                Row(
                                    modifier = Modifier
                                        .width(totalTableWidth)
                                        .height(CELL_HEIGHT)
                                ) {
                                    // عمود اسم الشبل (140.dp)
                                    LeaderTableCell(
                                        text = stringResource(id = R.string.report_column_scout_name),
                                        width = COL_NAME_WIDTH,
                                        isHeader = true,
                                        alignment = Alignment.CenterStart
                                    )

                                    // عمود لكل قائد قام بالتقييم (100.dp لكل قائد)
                                    uiState.leaders.forEach { leader ->
                                        LeaderTableCell(
                                            text = leader.name,
                                            width = COL_LEADER_WIDTH,
                                            isHeader = true
                                        )
                                    }

                                    // المعدل العام للقادة (90.dp)
                                    LeaderTableCell(
                                        text = stringResource(id = R.string.report_leaders_overall_avg),
                                        width = COL_OVERALL_WIDTH,
                                        isHeader = true
                                    )
                                }

                                // 2. صفوف بيانات الأشبال
                                Column(
                                    modifier = Modifier.width(totalTableWidth)
                                ) {
                                    uiState.rows.forEachIndexed { index, row ->
                                        val isEven = index % 2 == 0
                                        Row(
                                            modifier = Modifier
                                                .width(totalTableWidth)
                                                .height(CELL_HEIGHT)
                                        ) {
                                            // اسم الشبل (140.dp)
                                            LeaderTableCell(
                                                text = "${row.scout.name} ${row.scout.surname}".trim(),
                                                width = COL_NAME_WIDTH,
                                                isHeader = false,
                                                isEvenRow = isEven,
                                                alignment = Alignment.CenterStart
                                            )

                                            // أعمدة القادة (100.dp لكل قائد)
                                            uiState.leaders.forEach { leader ->
                                                val score = row.leaderScores[leader.id]
                                                val scoreText = formatScore(score)
                                                LeaderTableCell(
                                                    text = scoreText,
                                                    width = COL_LEADER_WIDTH,
                                                    isHeader = false,
                                                    isEvenRow = isEven,
                                                    textColor = if (score != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                )
                                            }

                                            // المعدل العام للقادة (90.dp)
                                            val overallText = formatScore(row.overallAverage)
                                            LeaderTableCell(
                                                text = overallText,
                                                width = COL_OVERALL_WIDTH,
                                                isHeader = false,
                                                isEvenRow = isEven,
                                                textColor = if (row.overallAverage != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * تنسيق الدرجة كنص بالصيغة e.g., "45/50" أو "45.2/50"، وعند الغياب يظهر "—" موسطاً
 */
private fun formatScore(score: Double?): String {
    if (score == null) return "—"
    return if (score % 1.0 == 0.0) {
        "${score.toInt()}/50"
    } else {
        String.format(Locale.US, "%.1f/50", score)
    }
}

/**
 * خلية لجدول تقييم القادة
 */
@Composable
private fun LeaderTableCell(
    text: String,
    width: Dp,
    isHeader: Boolean,
    modifier: Modifier = Modifier,
    isEvenRow: Boolean = true,
    alignment: Alignment = Alignment.Center,
    textColor: Color = Color.Unspecified
) {
    val backgroundColor = when {
        isHeader -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        isEvenRow -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
    }

    val finalTextColor = if (textColor != Color.Unspecified) {
        textColor
    } else if (isHeader) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val textStyle = if (isHeader) {
        MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.Bold,
            lineHeight = 16.sp
        )
    } else {
        MaterialTheme.typography.bodySmall.copy(
            lineHeight = 16.sp
        )
    }

    Box(
        modifier = modifier
            .width(width)
            .height(CELL_HEIGHT)
            .background(backgroundColor)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = alignment
    ) {
        Text(
            text = text,
            style = textStyle,
            color = finalTextColor,
            maxLines = 2,
            softWrap = true,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (alignment == Alignment.CenterStart) TextAlign.Start else TextAlign.Center
        )
    }
}

/**
 * بطاقات ملخص إحصاءات تقييم القادة بتخطيط شبكي 2×2 متكافئ
 */
@Composable
private fun LeadersSummaryGrid(
    totalScouts: Int,
    leadersCount: Int,
    totalEvaluations: Int,
    groupAverage: Double?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LeaderGridCard(
                title = stringResource(id = R.string.report_total_scouts),
                value = "$totalScouts",
                icon = Icons.Default.People,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )

            LeaderGridCard(
                title = stringResource(id = R.string.report_total_leaders_evaluating),
                value = "$leadersCount",
                icon = Icons.Default.Group,
                color = Color(0xFF00838F),
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LeaderGridCard(
                title = stringResource(id = R.string.report_total_evaluations_count),
                value = "$totalEvaluations",
                icon = Icons.Default.Assessment,
                color = Color(0xFF7B1FA2),
                modifier = Modifier.weight(1f)
            )

            val avgText = if (groupAverage != null) {
                if (groupAverage % 1.0 == 0.0) "${groupAverage.toInt()}/50" else String.format(Locale.US, "%.1f/50", groupAverage)
            } else "—"

            LeaderGridCard(
                title = stringResource(id = R.string.report_overall_leaders_average),
                value = avgText,
                icon = Icons.Default.Star,
                color = Color(0xFFF57C00),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun LeaderGridCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
