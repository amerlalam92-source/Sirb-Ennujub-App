package com.example.presentation.finance.collections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.presentation.common.AppLocale
import com.example.presentation.common.CustomTopAppBar
import com.example.presentation.common.ResponsiveFilterRow
import com.example.ui.theme.EnrolledGreenContentDark
import com.example.ui.theme.EnrolledGreenContentLight
import java.text.SimpleDateFormat
import java.util.Date

/**
 * شاشة التحصيلات المالية الموحدة (CollectionsScreen)
 * تدمج متابعة اشتراكات الاجتماعات التدريبية ومساهمات الأنشطة الكبرى في واجهة موحدة.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CollectionsScreen(
    viewModel: CollectionsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToSubscriptionDetail: (String) -> Unit,
    onNavigateToContributionDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isDark = isSystemInDarkTheme()
    val collectedColor = if (isDark) EnrolledGreenContentDark else EnrolledGreenContentLight
    val positiveGreen = collectedColor

    val dateFormat = remember {
        SimpleDateFormat("d MMMM yyyy", AppLocale.AR_DZ)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CustomTopAppBar(
                title = stringResource(id = R.string.collections_title),
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("collections_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.action_back)
                        )
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
            if (uiState.isLoading && uiState.items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1) SUMMARY CARD (بطاقة الملخص المالي العلوي)
                    item(key = "collections_summary_card") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("collections_summary_card"),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1) إجمالي المطلوب
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = String.format(AppLocale.AR_DZ, "%d دج", uiState.totalRequired),
                                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "المطلوب",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }

                                // 2) المُحصَّل
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = String.format(AppLocale.AR_DZ, "%d دج", uiState.totalPaid),
                                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                        color = collectedColor,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "المُحصَّل",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }

                                // 3) المتبقي
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = String.format(AppLocale.AR_DZ, "%d دج", uiState.totalDebt),
                                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (uiState.totalDebt > 0) MaterialTheme.colorScheme.error else collectedColor,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "المتبقي",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    // 2) FILTER CHIPS (شرائح التصفية)
                    item(key = "collections_filter_chips") {
                        ResponsiveFilterRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("collections_filter_row")
                        ) {
                            FilterChip(
                                selected = uiState.selectedFilter == CollectionsFilter.ALL,
                                onClick = { viewModel.onFilterSelected(CollectionsFilter.ALL) },
                                label = {
                                    Text(
                                        text = String.format(AppLocale.AR_DZ, "الكل (%d)", uiState.allCount),
                                        fontWeight = if (uiState.selectedFilter == CollectionsFilter.ALL) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                shape = RoundedCornerShape(20.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier.testTag("filter_chip_all")
                            )

                            FilterChip(
                                selected = uiState.selectedFilter == CollectionsFilter.SUBSCRIPTIONS,
                                onClick = { viewModel.onFilterSelected(CollectionsFilter.SUBSCRIPTIONS) },
                                label = {
                                    Text(
                                        text = String.format(AppLocale.AR_DZ, "اشتراكات (%d)", uiState.subsCount),
                                        fontWeight = if (uiState.selectedFilter == CollectionsFilter.SUBSCRIPTIONS) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                shape = RoundedCornerShape(20.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier.testTag("filter_chip_subscriptions")
                            )

                            FilterChip(
                                selected = uiState.selectedFilter == CollectionsFilter.CONTRIBUTIONS,
                                onClick = { viewModel.onFilterSelected(CollectionsFilter.CONTRIBUTIONS) },
                                label = {
                                    Text(
                                        text = String.format(AppLocale.AR_DZ, "مساهمات (%d)", uiState.contribsCount),
                                        fontWeight = if (uiState.selectedFilter == CollectionsFilter.CONTRIBUTIONS) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                shape = RoundedCornerShape(20.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier.testTag("filter_chip_contributions")
                            )
                        }
                    }

                    // 3) LIST OF COLLECTION ACTIVITIES / EMPTY STATE
                    if (uiState.filteredItems.isEmpty()) {
                        item(key = "collections_empty_state") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Payments,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(56.dp)
                                    )
                                    Text(
                                        text = stringResource(id = R.string.collections_empty),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = stringResource(id = R.string.collections_empty_hint),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        items(
                            items = uiState.filteredItems,
                            key = { "${it.type}_${it.activityId}" }
                        ) { item ->
                            CollectionActivityCard(
                                item = item,
                                dateFormat = dateFormat,
                                positiveColor = positiveGreen,
                                onClick = {
                                    if (item.type == "subscriptions") {
                                        onNavigateToSubscriptionDetail(item.activityId)
                                    } else {
                                        onNavigateToContributionDetail(item.activityId)
                                    }
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
 * بطاقة عرض نشاط التحصيل (CollectionActivityCard)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CollectionActivityCard(
    item: CollectionActivityItem,
    dateFormat: SimpleDateFormat,
    positiveColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = if (item.totalRequired > 0) {
        (item.totalPaid.toFloat() / item.totalRequired).coerceIn(0f, 1f)
    } else {
        1f
    }

    val isSubscriptions = item.type == "subscriptions"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("collection_card_${item.type}_${item.activityId}"),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Row 1: العنوان وسهم الانتقال
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.activityTitle.ifBlank { "نشاط كشفي" },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Row 2: شريحة المصدر (اشتراكات / مساهمات) + التاريخ
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSubscriptions) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    }
                ) {
                    Text(
                        text = if (isSubscriptions) {
                            stringResource(id = R.string.collections_chip_subscriptions)
                        } else {
                            stringResource(id = R.string.collections_chip_contributions)
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isSubscriptions) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                val dateStr = if (item.activityDate > 0) {
                    dateFormat.format(Date(item.activityDate))
                } else {
                    ""
                }
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // السطر 1: عدد المشاركين + شريحة حالة التحصيل
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = String.format(AppLocale.AR_DZ, "%d مشارك", item.participantsCount),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (item.totalDebt == 0) positiveColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = if (item.totalDebt == 0) "مكتمل التحصيل" else "متبقٍ ديون",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (item.totalDebt == 0) positiveColor else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            // السطر 2: إحصائيات المبالغ في FlowRow تسمح بالالتفاف السلس
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = String.format(AppLocale.AR_DZ, "المطلوب: %d دج", item.totalRequired),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = positiveColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = String.format(AppLocale.AR_DZ, "المُحصَّل: %d دج", item.totalPaid),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = positiveColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (item.totalDebt > 0) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f) else positiveColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = String.format(AppLocale.AR_DZ, "المتبقي: %d دج", item.totalDebt),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = if (item.totalDebt > 0) MaterialTheme.colorScheme.error else positiveColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // شريط التقدم للنسبة المحصلة
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = if (item.totalDebt == 0) positiveColor else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outlineVariant,
                strokeCap = StrokeCap.Round
            )
        }
    }
}
