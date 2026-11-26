package com.example.presentation.settings.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.settings.CustomTerms
import com.example.domain.settings.replaceCustomWords
import com.example.presentation.common.AppAlertDialog
import com.example.presentation.common.AppLogo
import com.example.presentation.common.CustomTopAppBar
import com.example.presentation.common.LocalAppTerms
import com.example.presentation.settings.SettingsEvent
import com.example.presentation.settings.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * شاشة إعدادات القائد الرئيسي (MainLeaderSettingsScreen)
 * تشمل: إدارة قادة السرب، تخصيص شعار التطبيق، وتخصيص الكلمات والمصطلحات.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainLeaderSettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToLeaderManagement: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isMainLeader by viewModel.isMainLeader.collectAsStateWithLifecycle()
    val currentTerms by viewModel.currentTerms.collectAsStateWithLifecycle()
    val terms = LocalAppTerms.current
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isUploadingLogo by remember { mutableStateOf(false) }

    var scoutSingular by remember { mutableStateOf("") }
    var scoutSingularDefinite by remember { mutableStateOf("") }
    var scoutPlural by remember { mutableStateOf("") }
    var scoutPluralDefinite by remember { mutableStateOf("") }
    var groupName by remember { mutableStateOf("") }
    var groupNameDefinite by remember { mutableStateOf("") }
    var unitName by remember { mutableStateOf("") }
    var unitNameDefinite by remember { mutableStateOf("") }
    var patrolName by remember { mutableStateOf("") }
    var showResetTermsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(currentTerms) {
        scoutSingular = currentTerms.scoutSingular
        scoutSingularDefinite = currentTerms.scoutSingularDefinite
        scoutPlural = currentTerms.scoutPlural
        scoutPluralDefinite = currentTerms.scoutPluralDefinite
        groupName = currentTerms.groupName
        groupNameDefinite = currentTerms.groupNameDefinite
        unitName = currentTerms.unitName
        unitNameDefinite = currentTerms.unitNameDefinite
        patrolName = currentTerms.patrolName
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isUploadingLogo = true
            scope.launch(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val originalBitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()

                    if (originalBitmap != null) {
                        val result = viewModel.processAndUploadLogo(originalBitmap)
                        withContext(Dispatchers.Main) {
                            isUploadingLogo = false
                            if (result.isSuccess) {
                                snackbarHostState.showSnackbar(context.getString(R.string.logo_updated_successfully))
                            } else {
                                snackbarHostState.showSnackbar(context.getString(R.string.error_loading_image))
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            isUploadingLogo = false
                            snackbarHostState.showSnackbar(context.getString(R.string.error_loading_image))
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isUploadingLogo = false
                        snackbarHostState.showSnackbar(context.getString(R.string.error_loading_image))
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                else -> {}
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CustomTopAppBar(
                title = "إعدادات القائد الرئيسي",
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("main_leader_settings_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.back_action),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // بطاقة إدارة قادة السرب (Leader Management Hub Card)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("leader_management_hub_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = replaceCustomWords("إدارة قادة ${terms.groupNameDefinite}", terms),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "متابعة طلبات الانضمام المعلقة واعتماد القادة الكشفيين وإدارة الصلاحيات",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        onClick = onNavigateToLeaderManagement,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_leader_management_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("فتح شاشة إدارة القادة")
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // قسم تخصيص شعار التطبيق
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("branding_logo_card"),
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
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(id = R.string.settings_logo_section),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    AppLogo(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentDescription = stringResource(id = R.string.settings_logo_section),
                        contentScale = ContentScale.Fit
                    )

                    Button(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        enabled = !isUploadingLogo,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("change_logo_button")
                    ) {
                        if (isUploadingLogo) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(stringResource(id = R.string.action_change_logo))
                    }
                }
            }

            // قسم تخصيص الكلمات والمصطلحات
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("custom_terms_card"),
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
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(id = R.string.settings_custom_terms_section),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // الحقول الأساسية مرتبة في شبكة من عمودين
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        TermInputField(
                            value = scoutSingular,
                            onValueChange = { scoutSingular = it },
                            label = stringResource(id = R.string.terms_scout_singular),
                            testTagName = "terms_scout_singular_input",
                            modifier = Modifier.weight(1f)
                        )
                        TermInputField(
                            value = scoutSingularDefinite,
                            onValueChange = { scoutSingularDefinite = it },
                            label = stringResource(id = R.string.terms_scout_singular_def),
                            testTagName = "terms_scout_singular_def_input",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        TermInputField(
                            value = scoutPlural,
                            onValueChange = { scoutPlural = it },
                            label = stringResource(id = R.string.terms_scout_plural),
                            testTagName = "terms_scout_plural_input",
                            modifier = Modifier.weight(1f)
                        )
                        TermInputField(
                            value = scoutPluralDefinite,
                            onValueChange = { scoutPluralDefinite = it },
                            label = stringResource(id = R.string.terms_scout_plural_def),
                            testTagName = "terms_scout_plural_def_input",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        TermInputField(
                            value = groupName,
                            onValueChange = { groupName = it },
                            label = stringResource(id = R.string.terms_group_singular),
                            testTagName = "terms_group_singular_input",
                            modifier = Modifier.weight(1f)
                        )
                        TermInputField(
                            value = groupNameDefinite,
                            onValueChange = { groupNameDefinite = it },
                            label = stringResource(id = R.string.terms_group_singular_def),
                            testTagName = "terms_group_singular_def_input",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        TermInputField(
                            value = unitNameDefinite,
                            onValueChange = { unitNameDefinite = it },
                            label = stringResource(id = R.string.terms_unit_name_def),
                            testTagName = "terms_unit_name_def_input",
                            modifier = Modifier.weight(1f)
                        )
                        TermInputField(
                            value = patrolName,
                            onValueChange = { patrolName = it },
                            label = stringResource(id = R.string.terms_patrol_name),
                            placeholder = stringResource(id = R.string.terms_patrol_name_hint),
                            supportingText = stringResource(id = R.string.terms_patrol_name_hint),
                            testTagName = "terms_patrol_name_input",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // زر إعادة الكلمات الافتراضية
                    OutlinedButton(
                        onClick = { showResetTermsDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reset_default_terms_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(id = R.string.action_reset_default_terms))
                    }

                    // زر حفظ الكلمات
                    Button(
                        onClick = {
                            val resolvedUnitDefinite = unitNameDefinite.trim().ifEmpty { "النجب" }
                            val updated = CustomTerms(
                                scoutSingular = scoutSingular.trim().ifEmpty { "شبل" },
                                scoutSingularDefinite = scoutSingularDefinite.trim().ifEmpty { "الشبل" },
                                scoutPlural = scoutPlural.trim().ifEmpty { "أشبال" },
                                scoutPluralDefinite = scoutPluralDefinite.trim().ifEmpty { "الأشبال" },
                                groupName = groupName.trim().ifEmpty { "سرب" },
                                groupNameDefinite = groupNameDefinite.trim().ifEmpty { "السرب" },
                                unitName = unitName.trim().ifEmpty { resolvedUnitDefinite },
                                unitNameDefinite = resolvedUnitDefinite,
                                patrolName = patrolName.trim().ifEmpty { "السداسية" },
                                customWords = currentTerms.customWords
                            )
                            viewModel.updateCustomTerms(updated, context)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_custom_terms_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(id = R.string.action_save_terms))
                    }
                }
            }

            // قسم صيانة بيانات وتواريخ الحضور للأنشطة القديمة (للقائد الرئيسي فقط)
            if (isMainLeader) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("attendance_flags_maintenance_card"),
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
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "صيانة بيانات الحضور",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "فحص كافة الأنشطة من الخادم لتصحيح شارة تسجيل الحضور ومزامنة تواريخ سجلات الحضور المفقودة أو غير المتطابقة",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = { viewModel.recalculateAllAttendanceFlags() },
                            enabled = isMainLeader && !uiState.isRecalculatingAttendance,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("recalculate_attendance_flags_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (uiState.isRecalculatingAttendance) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text("إصلاح حالة الحضور للأنشطة")
                        }

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )

                        Text(
                            text = "فحص كافة الاشتراكات وأرصدة الأشبال من الخادم لتصحيح القيم السالبة ومطابقة الديون المشتقة وعرض أي مبالغ مدفوعة بالزيادة دون تعديلها تلقائياً",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedButton(
                            onClick = { viewModel.reconcileFinancialBalances() },
                            enabled = isMainLeader && !uiState.isReconcilingFinance,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("reconcile_finance_balances_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (uiState.isReconcilingFinance) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AccountBalanceWallet,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text("فحص وتصحيح الأرصدة المالية")
                        }
                    }
                }
            }
        }
    }

    if (showResetTermsDialog) {
        AppAlertDialog(
            onDismissRequest = { showResetTermsDialog = false },
            title = {
                Text(
                    text = stringResource(id = R.string.action_reset_default_terms),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = stringResource(id = R.string.confirm_reset_default_terms),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetTermsDialog = false
                        viewModel.resetToDefault(context)
                    },
                    modifier = Modifier.testTag("confirm_reset_default_terms_button")
                ) {
                    Text(
                        text = stringResource(id = R.string.confirm_action),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetTermsDialog = false },
                    modifier = Modifier.testTag("cancel_reset_default_terms_button")
                ) {
                    Text(stringResource(id = R.string.cancel_action))
                }
            }
        )
    }

    uiState.financeMaintenanceReport?.let { report ->
        AppAlertDialog(
            onDismissRequest = { viewModel.dismissFinanceMaintenanceReport() },
            title = {
                Text(
                    text = "نتائج فحص وتصحيح الأرصدة المالية",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "الاشتراكات المفحوصة: ${report.subscriptionsChecked}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "الأشبال المفحوصون: ${report.scoutsChecked}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "تصحيح المدفوعات السالبة: ${report.negativePaidFixed}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "تصحيح عدم تطابق الديون: ${report.debtMismatchFixed}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "تصحيح الأرصدة المقدمة السالبة: ${report.negativeCreditFixed}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (report.overpayments.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text(
                            text = "تنبيه: مبالغ مدفوعة تتجاوز المطلوب (${report.overpayments.size}) — لم يتم تعديلها تلقائياً:",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.error
                        )
                        val displayedOverpayments = report.overpayments.take(10)
                        displayedOverpayments.forEach { item ->
                            Text(
                                text = "• ${item.scoutName} (${item.activityTitle}): زيادة ${item.excessAmount} دج",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (report.overpayments.size > 10) {
                            Text(
                                text = "و ${report.overpayments.size - 10} أخرى",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text(
                            text = "لا توجد مدفوعات زائدة تتجاوز المبلغ المطلوب.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.dismissFinanceMaintenanceReport() },
                    modifier = Modifier.testTag("dismiss_finance_maintenance_report_button")
                ) {
                    Text(
                        text = "حسناً",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        )
    }
}

@Composable
private fun TermInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    testTagName: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supportingText: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        placeholder = placeholder?.let { hint ->
            {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        supportingText = supportingText?.let { hint ->
            {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        },
        singleLine = true,
        modifier = modifier.testTag(testTagName),
        shape = RoundedCornerShape(12.dp)
    )
}
