package com.example.presentation.progress

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.example.presentation.common.CustomTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.progress.BadgeLevel
import com.example.domain.progress.BadgeProgress
import com.example.domain.progress.labelRes
import com.example.presentation.reports.PdfExporter
import kotlinx.coroutines.launch
import java.lang.String.format
import java.text.SimpleDateFormat
import java.util.Date

/**
 * شاشة تفاصيل بطاقة التقدم لشبل معين (ScoutProgressDetailScreen)
 * تعرض:
 * 1. بطاقة ملخص التقدم الكلي للشبل
 * 2. 4 بطاقات ملخص قابلة للنقر (شارة لكل مستوى: القبول -> المبتدئ -> الثاني -> الأول)
 *    النقر على أي بطاقة ينقل إلى شاشة الشارة المفردة scout_badge_progress/{scoutId}/{badgeKey}
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoutProgressDetailScreen(
    viewModel: ScoutProgressDetailViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToBadge: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val errorMessage by viewModel.message.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    val dateFormatter = remember {
        SimpleDateFormat("yyyy/MM/dd", com.example.presentation.common.AppLocale.AR_DZ)
    }

    BackHandler { onNavigateBack() }

    LaunchedEffect(errorMessage) {
        errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            val scoutName = (uiState as? ScoutProgressDetailUiState.Success)?.scout?.fullName
            CustomTopAppBar(
                title = stringResource(id = R.string.progress_card_screen_title),
                subtitle = scoutName,
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("progress_detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.cancel_action),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    if (uiState is ScoutProgressDetailUiState.Success) {
                        val success = uiState as ScoutProgressDetailUiState.Success
                        IconButton(
                            onClick = {
                                try {
                                    val file = PdfExporter.exportProgressCardPdf(
                                        scout = success.scout,
                                        badgeProgressList = success.badgeProgressList,
                                        context = context
                                    )
                                    PdfExporter.shareProgressCardPdf(
                                        file = file,
                                        scoutName = success.scout.fullName,
                                        context = context
                                    )
                                } catch (e: Exception) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("تعذر تصدير ملف PDF: ${e.localizedMessage}")
                                    }
                                }
                            },
                            modifier = Modifier.testTag("export_progress_card_pdf_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = stringResource(id = R.string.progress_export_pdf),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (val state = uiState) {
                is ScoutProgressDetailUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .testTag("progress_detail_loading"),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                is ScoutProgressDetailUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onNavigateBack) {
                            Text(stringResource(id = R.string.cancel_action))
                        }
                    }
                }

                is ScoutProgressDetailUiState.Success -> {
                    val scout = state.scout
                    val badgeProgressList = state.badgeProgressList

                    val grantedCount = badgeProgressList.count { it.isGranted }
                    val totalActive = badgeProgressList.sumOf { it.totalActiveRequirements }
                    val totalCompleted = badgeProgressList.sumOf { it.completedActiveRequirements }
                    val overallPercentage = if (totalActive > 0) (totalCompleted * 100) / totalActive else 0

                    val orderedLevels = listOf(
                        BadgeLevel.ACCEPTANCE,
                        BadgeLevel.BEGINNER,
                        BadgeLevel.SECOND,
                        BadgeLevel.FIRST
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("progress_detail_content"),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 1. بطاقة ملخص الشبل والتقدم الإجمالي
                        item {
                            ScoutOverallHeaderCard(
                                scoutName = scout.fullName,
                                scoutAge = scout.age,
                                grantedCount = grantedCount,
                                overallPercentage = overallPercentage
                            )
                        }

                        // عنوان قسم الشارات الأربعة
                        item {
                            Text(
                                text = "شارات الكفاءة والتقدم",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }

                        // 2. بطاقات ملخص الشارات الأربعة (قابلة للنقر وتفتح صفحة الشارة المفردة)
                        items(
                            items = orderedLevels,
                            key = { it.key }
                        ) { level ->
                            val badge = badgeProgressList.firstOrNull { it.level == level }
                                ?: BadgeProgress(
                                    level = level,
                                    totalActiveRequirements = 0,
                                    completedActiveRequirements = 0,
                                    percentage = 0,
                                    isGranted = false,
                                    grantedAt = null,
                                    grantedByName = null,
                                    requirements = emptyList()
                                )

                            BadgeClickableSummaryCard(
                                badge = badge,
                                dateFormatter = dateFormatter,
                                onClick = {
                                    onNavigateToBadge(scout.id, level.key)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * بطاقة ملخص الشارة الواحدة في قائمة الشارات (قابلة للنقر):
 * - اليمين: اسم الشارة (bold, colorScheme.primary)
 * - اليسار: رقاقة "{completed}/{total} ({percent}%)"
 * - LinearProgressIndicator بعرض كامل في الأسفل
 * - إذا كانت ممنوحة: رقاقة "ممنوحة" صغيرة + تاريخ المنح
 * - أيقونة السهم المتجه لليسار KeyboardArrowLeft
 */
@Composable
private fun BadgeClickableSummaryCard(
    badge: BadgeProgress,
    dateFormatter: SimpleDateFormat,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val badgeTitle = stringResource(id = badge.level.labelRes())
    val progressAnimated by animateFloatAsState(
        targetValue = badge.percentage / 100f,
        label = "summary_progress_${badge.level.key}"
    )

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("badge_summary_card_${badge.level.key}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // سطر 1: اليمين: اسم الشارة (bold, colorScheme.primary). اليسار: رقاقة "{completed}/{total} ({percent}%)"
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (badge.isGranted) Icons.Default.Check else Icons.Default.WorkspacePremium,
                        contentDescription = null,
                        tint = if (badge.isGranted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = badgeTitle,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "${badge.completedActiveRequirements}/${badge.totalActiveRequirements} (${badge.percentage}%)",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // سطر 2: LinearProgressIndicator below (full width)
            LinearProgressIndicator(
                progress = { progressAnimated },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = if (badge.isGranted || badge.percentage == 100) Color(0xFF2E7D32)
                else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            // سطر 3: أسفل: إذا كانت ممنوحة: رقاقة "ممنوحة" صغيرة + تاريخ المنح. أيقونة السهم المتجه لليسار KeyboardArrowLeft
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (badge.isGranted) {
                    val dateStr = badge.grantedAt?.let { dateFormatter.format(Date(it)) } ?: ""
                    Surface(
                        color = Color(0xFF2E7D32).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = stringResource(id = R.string.progress_granted_chip),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF2E7D32)
                            )
                            if (dateStr.isNotBlank()) {
                                Text(
                                    text = dateStr,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = stringResource(id = R.string.progress_card_tap_for_details),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * بطاقة الترويسة العلوية لملخص تقدم الشبل الإجمالي
 */
@Composable
private fun ScoutOverallHeaderCard(
    scoutName: String,
    scoutAge: Int,
    grantedCount: Int,
    overallPercentage: Int
) {
    val progressAnimated by animateFloatAsState(
        targetValue = overallPercentage / 100f,
        label = "overall_progress_anim"
    )

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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Text(
                            text = scoutName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = format(
                                com.example.presentation.common.AppLocale.AR_DZ,
                                "%d سنة",
                                scoutAge
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = format(
                            com.example.presentation.common.AppLocale.AR_DZ,
                            stringResource(id = R.string.progress_granted_badges_count),
                            grantedCount
                        ),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LinearProgressIndicator(
                    progress = { progressAnimated },
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (grantedCount == 4 || overallPercentage == 100) Color(0xFF2E7D32)
                    else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Text(
                    text = format(
                        com.example.presentation.common.AppLocale.AR_DZ,
                        "%d%%",
                        overallPercentage
                    ),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
