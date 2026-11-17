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
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import com.example.presentation.common.CustomTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onNavigateToSubscriptions: () -> Unit = onNavigateToCollections
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isDark = isSystemInDarkTheme()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

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
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
            } else if (uiState.errorMessage != null && uiState.summary == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = uiState.errorMessage ?: "فشل تحميل البيانات",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = { viewModel.refresh() },
                            modifier = Modifier.testTag("finance_retry_button")
                        ) {
                            Text("إعادة المحاولة")
                        }
                    }
                }
            } else {
                val summary = uiState.summary
                val balance = summary?.balance ?: 0
                val totalIncome = summary?.totalIncome ?: 0
                val totalExpenses = summary?.totalExpenses ?: 0
                val summaryDebt = summary?.totalOutstandingDebt ?: 0
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
                                .padding(horizontal = 20.dp, vertical = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // 1) الرصيد الحالي
                            Text(
                                text = stringResource(id = R.string.finance_balance_label),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = String.format(
                                    AppLocale.AR_DZ,
                                    "%d %s",
                                    balance,
                                    stringResource(id = R.string.finance_currency)
                                ),
                                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                                color = balanceColor,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            // 2) صف الواردات والمصاريف الكلية (عمودان متساويان مع فاصل رأسي)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.finance_total_income_label),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = String.format(
                                            AppLocale.AR_DZ,
                                            "%d %s",
                                            totalIncome,
                                            stringResource(id = R.string.finance_currency)
                                        ),
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                VerticalDivider(
                                    modifier = Modifier.height(44.dp),
                                    thickness = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.finance_total_expenses_label),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = String.format(
                                            AppLocale.AR_DZ,
                                            "%d %s",
                                            totalExpenses,
                                            stringResource(id = R.string.finance_currency)
                                        ),
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // 3) سطر الديون المتبقية
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = String.format(
                                        AppLocale.AR_DZ,
                                        "%s: %d %s",
                                        stringResource(id = R.string.finance_outstanding_debt_label),
                                        summaryDebt,
                                        stringResource(id = R.string.finance_currency)
                                    ),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }

                            // 4) فاصل أفقي رفيع
                            HorizontalDivider(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                                thickness = 1.dp
                            )

                            // 5) قسم إحصائيات الشهر الحالي (واردات الشهر يميناً • مصاريف الشهر يساراً)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.finance_monthly_income_label),
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f),
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = String.format(
                                            AppLocale.AR_DZ,
                                            "%d %s",
                                            monthlyIncome,
                                            stringResource(id = R.string.finance_currency)
                                        ),
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = positiveBalanceColor,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                VerticalDivider(
                                    modifier = Modifier.height(36.dp),
                                    thickness = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.finance_monthly_expenses_label),
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f),
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = String.format(
                                            AppLocale.AR_DZ,
                                            "%d %s",
                                            monthlyExpenses,
                                            stringResource(id = R.string.finance_currency)
                                        ),
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = negativeBalanceColor,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // 2) FEATURE CARDS (بطاقات الأقسام المالية)

                    // Card 1: التحصيلات (دمج الاشتراكات والمساهمات)
                    val collectionsPendingDebt = uiState.totalOutstandingDebt
                    val collectionsSubtitle = if (collectionsPendingDebt > 0) {
                        stringResource(id = R.string.finance_feature_collections_subtitle_format, collectionsPendingDebt)
                    } else {
                        stringResource(id = R.string.finance_feature_collections_no_debt)
                    }
                    val collectionsSubtitleColor = if (collectionsPendingDebt > 0) {
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
