package com.example.presentation.evaluation.matrix

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

/**
 * شاشة المصفوفة الشاملة لتقييم الأشبال (Comprehensive Evaluation Matrix)
 * تجمع نسب الحضور، تقييم القادة، تقييم حفظ القرآن، وتقييم الكراس مع حساب المعدل العام.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvaluationMatrixScreen(
    viewModel: EvaluationMatrixViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val horizontalScrollState = rememberScrollState()
    val verticalScrollState = rememberScrollState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CustomTopAppBar(
                title = stringResource(id = R.string.eval_matrix_title),
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("eval_matrix_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.cancel_action)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (uiState.rows.isEmpty()) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.eval_matrix_empty),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                val file = EvaluationMatrixPdfHelper.exportMatrixPdf(
                                    rows = uiState.rows,
                                    totalMeetingsHeld = uiState.totalMeetingsHeld,
                                    context = context
                                )
                                EvaluationMatrixPdfHelper.shareMatrixPdf(file, context)
                            }
                        },
                        modifier = Modifier.testTag("eval_matrix_export_pdf_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(id = R.string.eval_matrix_export_pdf),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.testTag("eval_matrix_loading"))
                }
            } else {
                // شريط البحث وملخص الإحصائيات
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = viewModel::onSearchQueryChanged,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("eval_matrix_search_field"),
                        placeholder = {
                            Text(
                                text = stringResource(id = R.string.eval_matrix_search_placeholder),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = stringResource(id = R.string.cancel_action)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    // بطاقة الإحصائيات المختصرة
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = stringResource(
                                id = R.string.eval_matrix_summary_format,
                                uiState.totalScoutsCount,
                                uiState.totalMeetingsHeld
                            ),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            textAlign = TextAlign.Center
                        )
                    }

                    // دليل الألوان (Legend)
                    MatrixLegendRow()
                }

                if (uiState.filteredRows.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(id = R.string.eval_matrix_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    // جدول المصفوفة ذو التمرير الرأسي والأفقي
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(verticalScrollState)
                            .padding(bottom = 24.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .horizontalScroll(horizontalScrollState)
                                .padding(horizontal = 16.dp)
                        ) {
                            Column {
                                // 1. ترويسة الجدول (Header Row)
                                Row(
                                    modifier = Modifier
                                        .height(IntrinsicSize.Min)
                                        .testTag("eval_matrix_header_row")
                                ) {
                                    // 1. الترتيب (50.dp)
                                    MatrixTableCell(
                                        text = stringResource(id = R.string.eval_col_rank),
                                        width = 50.dp,
                                        isHeader = true,
                                        isBold = true
                                    )
                                    // 2. اسم الشبل (140.dp)
                                    MatrixTableCell(
                                        text = stringResource(id = R.string.eval_col_scout),
                                        width = 140.dp,
                                        isHeader = true
                                    )
                                    // 3. تقييم الحضور (85.dp)
                                    MatrixTableCell(
                                        text = stringResource(id = R.string.eval_col_attendance_score),
                                        width = 85.dp,
                                        isHeader = true
                                    )
                                    // 4. تقييم القادة (85.dp)
                                    MatrixTableCell(
                                        text = stringResource(id = R.string.eval_col_leaders),
                                        width = 85.dp,
                                        isHeader = true
                                    )
                                    // 5. تقييم الحفظ (85.dp)
                                    MatrixTableCell(
                                        text = stringResource(id = R.string.eval_col_quran),
                                        width = 85.dp,
                                        isHeader = true
                                    )
                                    // 6. تقييم الكراس (85.dp)
                                    MatrixTableCell(
                                        text = stringResource(id = R.string.eval_col_notebook),
                                        width = 85.dp,
                                        isHeader = true
                                    )
                                    // 7. شارات الكفاية (85.dp)
                                    MatrixTableCell(
                                        text = stringResource(id = R.string.eval_col_kafaa_points),
                                        width = 85.dp,
                                        isHeader = true
                                    )
                                    // 8. شارات الهواية (85.dp)
                                    MatrixTableCell(
                                        text = stringResource(id = R.string.eval_col_hobby_points),
                                        width = 85.dp,
                                        isHeader = true
                                    )
                                    // 9. المجموع الكلي (90.dp)
                                    MatrixTableCell(
                                        text = stringResource(id = R.string.eval_col_total_score),
                                        width = 90.dp,
                                        isHeader = true,
                                        isBold = true
                                    )
                                    // 10. المعدل العام (90.dp - MUST BE LAST)
                                    MatrixTableCell(
                                        text = stringResource(id = R.string.eval_col_overall),
                                        width = 90.dp,
                                        isHeader = true,
                                        isBold = true
                                    )
                                }

                                // 2. صفوف بيانات الأشبال (Data Rows)
                                uiState.filteredRows.forEachIndexed { index, row ->
                                    val isEven = index % 2 == 0
                                    Row(
                                        modifier = Modifier
                                            .height(IntrinsicSize.Min)
                                            .testTag("eval_matrix_row_${row.scoutId}")
                                    ) {
                                        // 1. الترتيب
                                        MatrixTableCell(
                                            text = "${row.rank}",
                                            width = 50.dp,
                                            isHeader = false,
                                            isBold = true,
                                            isAlternate = !isEven,
                                            alignment = Alignment.Center
                                        )

                                        // 2. اسم الشبل
                                        MatrixTableCell(
                                            text = row.scoutName,
                                            width = 140.dp,
                                            isHeader = false,
                                            isAlternate = !isEven,
                                            alignment = Alignment.CenterEnd
                                        )

                                        // 3. تقييم الحضور (من 20)
                                        MatrixScoreCell(
                                            score = row.attendanceScore,
                                            width = 85.dp,
                                            isAlternate = !isEven
                                        )

                                        // 4. تقييم القادة (من 20)
                                        MatrixScoreCell(
                                            score = row.leaderScore,
                                            width = 85.dp,
                                            isAlternate = !isEven
                                        )

                                        // 5. تقييم الحفظ (من 20)
                                        MatrixScoreCell(
                                            score = row.quranScore,
                                            width = 85.dp,
                                            isAlternate = !isEven
                                        )

                                        // 6. تقييم الكراس (من 20)
                                        MatrixScoreCell(
                                            score = row.notebookScore,
                                            width = 85.dp,
                                            isAlternate = !isEven
                                        )

                                        // 7. شارات الكفاية (رقم خام)
                                        MatrixTableCell(
                                            width = 85.dp,
                                            isHeader = false,
                                            isAlternate = !isEven,
                                            alignment = Alignment.Center
                                        ) {
                                            val kafaaText = if (row.kafaaPoints != null) row.kafaaPoints.toString() else "—"
                                            Text(
                                                text = kafaaText,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                color = if (row.kafaaPoints != null && row.kafaaPoints > 0) Color(0xFF1565C0) else Color(0xFF9E9E9E),
                                                textAlign = TextAlign.Center
                                            )
                                        }

                                        // 8. شارات الهواية (رقم خام)
                                        MatrixTableCell(
                                            width = 85.dp,
                                            isHeader = false,
                                            isAlternate = !isEven,
                                            alignment = Alignment.Center
                                        ) {
                                            val hobbyText = if (row.hobbyPoints != null) row.hobbyPoints.toString() else "—"
                                            Text(
                                                text = hobbyText,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                color = if (row.hobbyPoints != null && row.hobbyPoints > 0) Color(0xFF1565C0) else Color(0xFF9E9E9E),
                                                textAlign = TextAlign.Center
                                            )
                                        }

                                        // 9. المجموع الكلي (أخضر >=80، عنبري 50-79، أحمر <50)
                                        MatrixTableCell(
                                            width = 90.dp,
                                            isHeader = false,
                                            isBold = true,
                                            isAlternate = !isEven,
                                            alignment = Alignment.Center
                                        ) {
                                            val totalColor = getTotalScoreColor(row.totalScore)
                                            val totalText = formatTotalScore(row.totalScore)
                                            Surface(
                                                color = totalColor.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.padding(2.dp)
                                            ) {
                                                Text(
                                                    text = totalText,
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = totalColor,
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        // 10. المعدل العام (ناتج قسمة المجموع الكلي على 4 - MUST BE LAST)
                                        MatrixScoreCell(
                                            score = row.overallScore,
                                            width = 90.dp,
                                            isBold = true,
                                            isAlternate = !isEven
                                        )
                                    }
                                }
                            }
                        }

                        // بطاقة شرح طريقة الحساب
                        Spacer(modifier = Modifier.height(24.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = stringResource(id = R.string.eval_legend_title),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                                Text(
                                    text = "• " + stringResource(id = R.string.eval_legend_overall),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "• " + stringResource(id = R.string.eval_legend_total),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "• " + stringResource(id = R.string.eval_legend_kafaa),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "• " + stringResource(id = R.string.eval_legend_hobby),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * دالة مساعدة لتنسيق الدرجة من 20 (مثال: "18/20" أو "18.5/20") والتعامل مع القيم الفارغة كـ "—"
 */
fun formatScore(score: Double?): String {
    if (score == null) return "—"
    val positive = score.coerceAtLeast(0.0)
    return if (positive % 1.0 == 0.0) {
        "${positive.toInt()}/20"
    } else {
        String.format(java.util.Locale.US, "%.1f/20", positive)
    }
}

/**
 * دالة مساعدة لتحديد لون الدرجة (مقياس من 20):
 * >= 16 (80%): أخضر
 * 10 - 15.9 (50% - 79%): برتقالي/عنبري
 * < 10 (< 50%): أحمر
 * null أو لا توجد بيانات: رمادي
 */
fun getMatrixScoreColor(score: Double?): Color {
    if (score == null) return Color(0xFF9E9E9E)
    return when {
        score < 10.0 -> Color(0xFFD32F2F)
        score < 16.0 -> Color(0xFFF57C00)
        else -> Color(0xFF2E7D32)
    }
}

/**
 * دالة مساعدة لتنسيق المجموع الكلي (إظهار فاصلة عشرية واحدة إن وجدت)
 */
fun formatTotalScore(score: Double?): String {
    if (score == null) return "—"
    return if (score % 1.0 == 0.0) {
        score.toInt().toString()
    } else {
        String.format(java.util.Locale.US, "%.1f", score)
    }
}

/**
 * دالة مساعدة لتحديد لون المجموع الكلي:
 * >= 80: أخضر
 * 50 - 79: برتقالي/عنبري
 * < 50: أحمر
 * null: رمادي
 */
fun getTotalScoreColor(score: Double?): Color {
    if (score == null) return Color(0xFF9E9E9E)
    return when {
        score < 50.0 -> Color(0xFFD32F2F)
        score < 80.0 -> Color(0xFFF57C00)
        else -> Color(0xFF2E7D32)
    }
}

/**
 * خلية نصية ثابتة العرض للجدول
 */
@Composable
fun MatrixTableCell(
    width: Dp,
    modifier: Modifier = Modifier,
    text: String? = null,
    isHeader: Boolean = false,
    isBold: Boolean = false,
    isAlternate: Boolean = false,
    alignment: Alignment = Alignment.Center,
    content: @Composable (() -> Unit)? = null
) {
    val bgColor = when {
        isHeader -> MaterialTheme.colorScheme.surfaceVariant
        isAlternate -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        else -> MaterialTheme.colorScheme.surface
    }

    Box(
        modifier = modifier
            .width(width)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
            .background(bgColor)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        contentAlignment = alignment
    ) {
        if (text != null) {
            Text(
                text = text,
                style = if (isHeader) {
                    MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                } else if (isBold) {
                    MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                } else {
                    MaterialTheme.typography.bodySmall
                },
                maxLines = 2,
                textAlign = if (alignment == Alignment.CenterEnd) TextAlign.End else TextAlign.Center,
                overflow = TextOverflow.Ellipsis
            )
        } else if (content != null) {
            content()
        }
    }
}

/**
 * خلية الدرجة الملونة (بصيغة X/20)
 */
@Composable
fun MatrixScoreCell(
    score: Double?,
    width: Dp,
    modifier: Modifier = Modifier,
    isBold: Boolean = false,
    isAlternate: Boolean = false
) {
    val color = getMatrixScoreColor(score)
    val displayText = formatScore(score)

    MatrixTableCell(
        width = width,
        modifier = modifier,
        isHeader = false,
        isAlternate = isAlternate,
        alignment = Alignment.Center
    ) {
        Surface(
            color = color.copy(alpha = if (score != null) 0.12f else 0.08f),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                text = displayText,
                style = if (isBold) {
                    MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                } else {
                    MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                },
                color = color,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

/**
 * شريط دليل الألوان التوضيحي للنسب
 */
@Composable
fun MatrixLegendRow(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendItem(color = Color(0xFF2E7D32), label = stringResource(id = R.string.eval_matrix_legend_green))
            LegendItem(color = Color(0xFFF57C00), label = stringResource(id = R.string.eval_matrix_legend_amber))
            LegendItem(color = Color(0xFFD32F2F), label = stringResource(id = R.string.eval_matrix_legend_red))
            LegendItem(color = Color(0xFF9E9E9E), label = stringResource(id = R.string.eval_matrix_legend_gray))
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
