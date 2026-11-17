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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
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
import com.example.presentation.common.LocalAppTerms
import kotlinx.coroutines.launch

// إعدادات أبعاد الأعمدة الثابتة للمصفوفة
private val COL_NAME_WIDTH = 140.dp
private val COL_BADGE_WIDTH = 90.dp
private val COL_HOBBY_WIDTH = 110.dp
// إجمالي عرض الجدول = 140 + (90 * 4) + 110 = 610.dp
private val TOTAL_TABLE_WIDTH = COL_NAME_WIDTH + (COL_BADGE_WIDTH * 4) + COL_HOBBY_WIDTH
private val CELL_HEIGHT = 48.dp

/**
 * شاشة تقرير التقدم الشامل
 * مصفوفة شارات الكفاءة وشارات الهواية لجميع الأشبال النشطين
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressReportScreen(
    viewModel: GeneralReportsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val terms = LocalAppTerms.current
    val uiState by viewModel.progressReportData.collectAsStateWithLifecycle()
    val isExportingPdf by viewModel.isExportingPdf.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CustomTopAppBar(
                title = stringResource(id = R.string.report_comprehensive_progress),
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("progress_report_back_button")
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
                                viewModel.exportAndShareProgressReport(context)
                            },
                            modifier = Modifier.testTag("action_share_progress_pdf")
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
                        text = "لا يوجد ${terms.scoutPlural} نشطون لعرض التقرير",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            else -> {
                val horizontalScrollState = rememberScrollState()
                val verticalScrollState = rememberScrollState()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(verticalScrollState)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. بطاقات الإحصاءات السريعة (شبكة 2×3 بمساحات متكافئة)
                    ProgressSummaryGrid(
                        totalScouts = uiState.totalScouts,
                        acceptanceCount = uiState.acceptanceCount,
                        beginnerCount = uiState.beginnerCount,
                        secondCount = uiState.secondCount,
                        firstCount = uiState.firstCount,
                        totalHobbies = uiState.totalHobbiesAwarded
                    )

                    // 2. جدول المصفوفة البيانية للتقدم والشارات
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(horizontalScrollState)
                        ) {
                            Column(
                                modifier = Modifier.width(TOTAL_TABLE_WIDTH)
                            ) {
                                // 1. صف الرأس (Header Row)
                                Row(
                                    modifier = Modifier
                                        .width(TOTAL_TABLE_WIDTH)
                                        .height(CELL_HEIGHT)
                                ) {
                                    // عمود اسم الشبل (140.dp)
                                    ProgressTableCell(
                                        text = "اسم ${terms.scoutSingularDefinite}",
                                        width = COL_NAME_WIDTH,
                                        isHeader = true,
                                        alignment = Alignment.CenterStart
                                    )

                                    // شارة القبول (90.dp)
                                    ProgressTableCell(
                                        text = stringResource(id = R.string.report_badge_acceptance_col),
                                        width = COL_BADGE_WIDTH,
                                        isHeader = true
                                    )

                                    // شارة المبتدئ (90.dp)
                                    ProgressTableCell(
                                        text = stringResource(id = R.string.report_badge_beginner_col),
                                        width = COL_BADGE_WIDTH,
                                        isHeader = true
                                    )

                                    // شارة الثاني (90.dp)
                                    ProgressTableCell(
                                        text = stringResource(id = R.string.report_badge_second_col),
                                        width = COL_BADGE_WIDTH,
                                        isHeader = true
                                    )

                                    // شارة الأول (90.dp)
                                    ProgressTableCell(
                                        text = stringResource(id = R.string.report_badge_first_col),
                                        width = COL_BADGE_WIDTH,
                                        isHeader = true
                                    )

                                    // شارات الهواية (عدد) (110.dp)
                                    ProgressTableCell(
                                        text = stringResource(id = R.string.report_badge_hobbies_count_col),
                                        width = COL_HOBBY_WIDTH,
                                        isHeader = true
                                    )
                                }

                                // 2. صفوف بيانات الأشبال
                                Column(
                                    modifier = Modifier.width(TOTAL_TABLE_WIDTH)
                                ) {
                                    uiState.rows.forEachIndexed { index, row ->
                                        val isEven = index % 2 == 0
                                        Row(
                                            modifier = Modifier
                                                .width(TOTAL_TABLE_WIDTH)
                                                .height(CELL_HEIGHT)
                                        ) {
                                            // اسم الشبل (140.dp)
                                            ProgressTableCell(
                                                text = "${row.scout.name} ${row.scout.surname}".trim(),
                                                width = COL_NAME_WIDTH,
                                                isHeader = false,
                                                isEvenRow = isEven,
                                                alignment = Alignment.CenterStart
                                            )

                                            // شارة القبول (90.dp)
                                            ProgressBadgeCell(
                                                isGranted = row.hasAcceptance,
                                                width = COL_BADGE_WIDTH,
                                                isEvenRow = isEven
                                            )

                                            // شارة المبتدئ (90.dp)
                                            ProgressBadgeCell(
                                                isGranted = row.hasBeginner,
                                                width = COL_BADGE_WIDTH,
                                                isEvenRow = isEven
                                            )

                                            // شارة الثاني (90.dp)
                                            ProgressBadgeCell(
                                                isGranted = row.hasSecond,
                                                width = COL_BADGE_WIDTH,
                                                isEvenRow = isEven
                                            )

                                            // شارة الأول (90.dp)
                                            ProgressBadgeCell(
                                                isGranted = row.hasFirst,
                                                width = COL_BADGE_WIDTH,
                                                isEvenRow = isEven
                                            )

                                            // شارات الهواية (عدد) (110.dp)
                                            ProgressTableCell(
                                                text = if (row.hobbyBadgesCount > 0) "${row.hobbyBadgesCount}" else "—",
                                                width = COL_HOBBY_WIDTH,
                                                isHeader = false,
                                                isEvenRow = isEven,
                                                textColor = if (row.hobbyBadgesCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
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
 * خلية شارة كفاءة تعرض علامة صح خضراء عند المنح، أو خطاً "—" موسطاً عند عدم المنح
 */
@Composable
private fun ProgressBadgeCell(
    isGranted: Boolean,
    width: Dp,
    isEvenRow: Boolean,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isEvenRow) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
    }

    Box(
        modifier = modifier
            .width(width)
            .height(CELL_HEIGHT)
            .background(backgroundColor)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isGranted) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2E7D32).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "ممنوحة",
                    tint = Color(0xFF2E7D32),
                    modifier = Modifier.size(16.dp)
                )
            }
        } else {
            Text(
                text = "—",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * خلية نصية عادية لجدول التقرير
 */
@Composable
private fun ProgressTableCell(
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
 * بطاقات ملخص إحصاءات التقدم الشامل بتخطيط شبكي 2×3 مع دعم التفاف النص وتناسق الأبعاد
 */
@Composable
private fun ProgressSummaryGrid(
    totalScouts: Int,
    acceptanceCount: Int,
    beginnerCount: Int,
    secondCount: Int,
    firstCount: Int,
    totalHobbies: Int,
    modifier: Modifier = Modifier
) {
    val terms = LocalAppTerms.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // الصف الأول
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SummaryGridCard(
                title = "إجمالي ${terms.scoutPluralDefinite}",
                value = "$totalScouts",
                icon = Icons.Default.People,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )

            SummaryGridCard(
                title = stringResource(id = R.string.badge_acceptance),
                value = "$acceptanceCount",
                icon = Icons.Default.WorkspacePremium,
                color = Color(0xFF2E7D32),
                modifier = Modifier.weight(1f)
            )
        }

        // الصف الثاني
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SummaryGridCard(
                title = stringResource(id = R.string.badge_beginner),
                value = "$beginnerCount",
                icon = Icons.Default.MilitaryTech,
                color = Color(0xFF1976D2),
                modifier = Modifier.weight(1f)
            )

            SummaryGridCard(
                title = stringResource(id = R.string.badge_second),
                value = "$secondCount",
                icon = Icons.Default.MilitaryTech,
                color = Color(0xFFF57C00),
                modifier = Modifier.weight(1f)
            )
        }

        // الصف الثالث
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SummaryGridCard(
                title = stringResource(id = R.string.badge_first),
                value = "$firstCount",
                icon = Icons.Default.WorkspacePremium,
                color = Color(0xFF7B1FA2),
                modifier = Modifier.weight(1f)
            )

            SummaryGridCard(
                title = stringResource(id = R.string.report_total_hobbies_granted),
                value = "$totalHobbies",
                icon = Icons.Default.Star,
                color = Color(0xFF00838F),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SummaryGridCard(
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
