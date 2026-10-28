package com.example.presentation.finance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.example.presentation.common.CustomTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.presentation.common.AppLocale
import com.example.presentation.finance.common.FinanceFeatureCard

/**
 * الشاشة الرئيسية للإدارة المالية (FinanceHomeScreen)
 * تعرض بطاقة الملخص المالي الإجمالي الشاملة، وبطاقات التوجيه للمميزات المالية.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceHomeScreen(
    viewModel: FinanceHomeViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToCollections: () -> Unit,
    onNavigateToExpenses: () -> Unit,
    onNavigateToDonations: () -> Unit,
    onNavigateToReports: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToSubscriptions: () -> Unit = onNavigateToCollections,
    onNavigateToContributions: () -> Unit = onNavigateToCollections
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isDark = isSystemInDarkTheme()

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CustomTopAppBar(
                title = stringResource(id = R.string.finance_home_title),
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("finance_back_button")
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
            if (uiState.isLoading && uiState.summary == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                val summary = uiState.summary
                val balance = summary?.balance ?: 0
                val totalIncome = summary?.totalIncome ?: 0
                val totalExpenses = summary?.totalExpenses ?: 0
                val totalDebt = summary?.totalOutstandingDebt ?: 0
                val monthlyIncome = summary?.monthlyIncome ?: 0
                val monthlyExpenses = summary?.monthlyExpenses ?: 0

                val positiveBalanceColor = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
                val negativeBalanceColor = MaterialTheme.colorScheme.error
                val balanceColor = if (balance >= 0) positiveBalanceColor else negativeBalanceColor

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1) SUMMARY CARD (الموجز المالي العام)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("finance_summary_card"),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 1) تسمية الرصيد الحالي
                            Text(
                                text = stringResource(id = R.string.finance_balance_label),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )

                            // 2) قيمة الرصيد الحالي (الأبرز بحجم displaySmall عريض)
                            Text(
                                text = String.format(AppLocale.AR_DZ, "%d %s", balance, stringResource(id = R.string.finance_currency)),
                                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                                color = balanceColor,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )

                            // 3) صف الواردات والمصاريف الكلية (عمودان متساويان ومحاذيان للوسط)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.finance_total_income_label),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = String.format(AppLocale.AR_DZ, "%d %s", totalIncome, stringResource(id = R.string.finance_currency)),
                                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }

                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.finance_total_expenses_label),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = String.format(AppLocale.AR_DZ, "%d %s", totalExpenses, stringResource(id = R.string.finance_currency)),
                                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }

                            // 4) سطر الديون المتبقية
                            Text(
                                text = String.format(
                                    AppLocale.AR_DZ,
                                    "%s: %d %s",
                                    stringResource(id = R.string.finance_outstanding_debt_label),
                                    totalDebt,
                                    stringResource(id = R.string.finance_currency)
                                ),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.error,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )

                            // 5) فاصل أفقي رفيع
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                                thickness = 1.dp
                            )

                            // 6) قسم إحصائيات الشهر الحالي
                            Text(
                                text = String.format(
                                    AppLocale.AR_DZ,
                                    "%s: %d واردات • %d مصاريف",
                                    stringResource(id = R.string.finance_month_section),
                                    monthlyIncome,
                                    monthlyExpenses
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }

                    // 2) FEATURE CARDS (بطاقات الأقسام المالية)

                    // Card 1: التحصيلات (دمج الاشتراكات والمساهمات)
                    val totalDebt = uiState.totalOutstandingDebt
                    val collectionsSubtitle = if (totalDebt > 0) {
                        stringResource(id = R.string.finance_feature_collections_subtitle_format, totalDebt)
                    } else {
                        stringResource(id = R.string.finance_feature_collections_no_debt)
                    }
                    val collectionsSubtitleColor = if (totalDebt > 0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    FinanceFeatureCard(
                        title = stringResource(id = R.string.collections_title),
                        subtitle = collectionsSubtitle,
                        icon = Icons.Default.Payments,
                        iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        subtitleColor = collectionsSubtitleColor,
                        onClick = onNavigateToCollections,
                        testTag = "finance_card_collections"
                    )

                    // Card 3: المصاريف
                    val monthlyExpensesFormatted = stringResource(id = R.string.finance_feature_expenses_subtitle_format, monthlyExpenses)
                    FinanceFeatureCard(
                        title = stringResource(id = R.string.finance_feature_expenses),
                        subtitle = monthlyExpensesFormatted,
                        icon = Icons.Default.TrendingDown,
                        iconContainerColor = MaterialTheme.colorScheme.errorContainer,
                        iconContentColor = MaterialTheme.colorScheme.onErrorContainer,
                        onClick = onNavigateToExpenses,
                        testTag = "finance_card_expenses"
                    )

                    // Card 4: التبرعات (تبرعات حرة عامة بدون ربط بأنشطة)
                    val donationsFormatted = stringResource(id = R.string.finance_feature_donations_subtitle_format, uiState.totalDonations)
                    FinanceFeatureCard(
                        title = stringResource(id = R.string.finance_feature_donations),
                        subtitle = donationsFormatted,
                        icon = Icons.Default.VolunteerActivism,
                        iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        iconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        onClick = onNavigateToDonations,
                        testTag = "finance_card_donations"
                    )

                    // Card 5: التقارير المالية (تصدير PDF لكشوفات الحساب وتقارير المصاريف)
                    FinanceFeatureCard(
                        title = stringResource(id = R.string.finance_feature_reports),
                        subtitle = stringResource(id = R.string.finance_feature_reports_subtitle),
                        icon = Icons.Default.Assessment,
                        iconContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        iconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = onNavigateToReports,
                        testTag = "finance_card_reports"
                    )
                }
            }
        }
    }
}
