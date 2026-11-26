package com.example.presentation.attendance

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import com.example.domain.activity.ActivityType
import com.example.presentation.common.AppAlertDialog
import com.example.presentation.common.AppDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.example.presentation.common.CustomTopAppBar
import com.example.presentation.common.LocalAppTerms
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.widget.Toast
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.attendance.AttendanceStatus
import com.example.domain.scout.Scout
import com.example.presentation.activity.ActivityFormatters
import com.example.presentation.activity.AttendanceEditWindow
import kotlinx.coroutines.launch

private fun isDeviceOnline(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val network = cm.activeNetwork ?: return false
    val capabilities = cm.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

/**
 * شاشة معالج تسجيل الحضور الكشفي (Attendance Wizard Entry Screen)
 * تعرض شبلاً واحداً في كل خطوة مع 4 خيارات تفاعلية واضحة ونقاط مخصصة وتأكيدات ذكية.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceEntryScreen(
    activityId: String,
    viewModel: AttendanceViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val terms = LocalAppTerms.current
    val state by viewModel.entryUiState.collectAsStateWithLifecycle()
    val currentScoutTotalPaid by viewModel.currentScoutTotalPaid.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showFinishDialog by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var pendingStatusChange by remember { mutableStateOf<AttendanceStatus?>(null) }
    var showAbsentToPresentWarning by remember { mutableStateOf(false) }

    LaunchedEffect(activityId) {
        viewModel.initAttendanceEntry(activityId)
    }

    val context = LocalContext.current
    var isOnline by remember { mutableStateOf(isDeviceOnline(context)) }

    DisposableEffect(context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isOnline = true
            }

            override fun onLost(network: Network) {
                isOnline = isDeviceOnline(context)
            }
        }
        if (cm != null) {
            try {
                cm.registerDefaultNetworkCallback(callback)
            } catch (_: Exception) {
            }
        }
        onDispose {
            if (cm != null) {
                try {
                    cm.unregisterNetworkCallback(callback)
                } catch (_: Exception) {
                }
            }
        }
    }

    val skippedMessage = "تم تخطي ${terms.scoutSingularDefinite} وسيعاد عرضه في نهاية القائمة"
    val selectStatusFirstMessage = stringResource(id = R.string.error_select_status_first)
    val syncWaitMessage = stringResource(id = R.string.attendance_sync_wait_before_exit)
    val offlineSaveToastMessage = stringResource(id = R.string.attendance_offline_save_toast)

    val handleExitRequest: () -> Unit = {
        if (!isDeviceOnline(context)) {
            Toast.makeText(context, offlineSaveToastMessage, Toast.LENGTH_LONG).show()
            onNavigateBack()
        } else if (!state.isFinishing) {
            if (state.isSaving) {
                scope.launch {
                    snackbarHostState.showSnackbar(syncWaitMessage)
                }
            } else {
                onNavigateBack()
            }
        }
    }

    BackHandler(enabled = state.isSaving || state.isFinishing || !isOnline) {
        handleExitRequest()
    }

    LaunchedEffect(state.snackbarMessage) {
        val msgKey = state.snackbarMessage
        if (msgKey != null) {
            val displayMsg = if (msgKey == "error_select_status_first") {
                selectStatusFirstMessage
            } else {
                msgKey
            }
            snackbarHostState.showSnackbar(displayMsg)
            viewModel.onSnackbarDismissed()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CustomTopAppBar(
                title = state.activity?.title ?: stringResource(id = R.string.action_record_attendance),
                subtitle = stringResource(
                    id = R.string.scout_progress_format,
                    (state.currentIndex + 1).coerceAtMost(state.totalScouts),
                    state.totalScouts
                ),
                navigationIcon = {
                    IconButton(
                        onClick = handleExitRequest,
                        enabled = !state.isFinishing || !isOnline,
                        modifier = Modifier.testTag("attendance_entry_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.cancel_action),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    val editWindow = state.activity?.let { ActivityFormatters.attendanceEditWindow(it.startDate) } ?: AttendanceEditWindow.EDITABLE
                    val isEditable = editWindow == AttendanceEditWindow.EDITABLE
                    if (!state.isLoading && state.scoutsQueue.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                searchQuery = ""
                                showSearchDialog = true
                            },
                            modifier = Modifier.testTag("attendance_search_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "بحث عن ${terms.scoutSingular} للانتقال إليه",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    TextButton(
                        onClick = { if (isEditable && !state.isFinishing) showFinishDialog = true },
                        enabled = isEditable && !state.isFinishing,
                        modifier = Modifier.testTag("finish_save_top_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            tint = if (isEditable && !state.isFinishing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(id = R.string.action_finish_and_save),
                            color = if (isEditable && !state.isFinishing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            fontWeight = FontWeight.Bold
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
                .consumeWindowInsets(paddingValues)
                .imePadding()
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .testTag("entry_loading_indicator"),
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (state.scoutsQueue.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "لا يوجد ${terms.scoutPlural} نشطون لتسجيل الحضور لهم.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                val editWindow = state.activity?.let { ActivityFormatters.attendanceEditWindow(it.startDate) } ?: AttendanceEditWindow.EDITABLE
                val isEditable = editWindow == AttendanceEditWindow.EDITABLE

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    if (!isEditable) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Text(
                                text = if (editWindow == AttendanceEditWindow.EXPIRED) {
                                    stringResource(id = R.string.attendance_expired_caption)
                                } else {
                                    stringResource(id = R.string.attendance_too_early_caption)
                                },
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    // مؤشر التقدم العلوي
                    Column(modifier = Modifier.fillMaxWidth()) {
                        val progress = if (state.totalScouts > 0) {
                            (state.currentIndex.toFloat() + 1f) / state.totalScouts.toFloat()
                        } else 0f

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.secondary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = state.failedSaveCount > 0 || state.failedSaveError != null
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .testTag("attendance_failed_save_banner")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = state.failedSaveError ?: stringResource(
                                        id = R.string.attendance_failed_saves_banner_format,
                                        state.failedSaveCount.coerceAtLeast(1)
                                    ),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    modifier = Modifier.weight(1f)
                                )
                                if (state.failedSaveCount > 0) {
                                    TextButton(
                                        onClick = { viewModel.retryFailedSaves() },
                                        modifier = Modifier.testTag("attendance_retry_save_btn")
                                    ) {
                                        Text(
                                            text = stringResource(id = R.string.attendance_retry_save_action),
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { viewModel.dismissFailedSaveBanner() },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(id = R.string.data_error_dismiss),
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // بطاقة الشبل الحالي بتأثير حركي سلس + مؤشر مزامنة غير معطّل
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        AnimatedContent(
                            targetState = state.currentScout,
                            transitionSpec = {
                                (slideInHorizontally { width -> -width } + fadeIn()) togetherWith
                                        (slideOutHorizontally { width -> width } + fadeOut())
                            },
                            label = "scout_card_transition",
                            modifier = Modifier.fillMaxSize()
                        ) { currentScout ->
                            if (currentScout != null) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState()),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    val isTrainingMeeting = state.activity?.let {
                                        it.activityType == ActivityType.TRAINING_MEETING ||
                                        it.type == ActivityType.TRAINING_MEETING.key ||
                                        it.type == "اجتماع تدريبي"
                                    } ?: false

                                    ScoutWizardCard(
                                        scout = currentScout,
                                        selectedStatus = state.currentStatus,
                                        usesPoints = state.activity?.activityType?.usesPointsSystem() == true,
                                        pointsSettings = state.pointsSettings,
                                        isTrainingMeeting = isTrainingMeeting,
                                        onStatusSelected = { newStatus ->
                                            if (isEditable) {
                                                val existing = state.currentStatus
                                                if (existing != null && existing.isAbsent && newStatus.isPresentOrLate) {
                                                    pendingStatusChange = newStatus
                                                    showAbsentToPresentWarning = true
                                                } else {
                                                    viewModel.onSelectStatus(newStatus)
                                                }
                                            }
                                        }
                                    )

                                    if (state.isFinanceSectionVisible) {
                                        Spacer(modifier = Modifier.height(14.dp))
                                        AttendanceFinanceCard(
                                            uiState = state,
                                            currentScoutTotalPaid = currentScoutTotalPaid,
                                            onAmountInputChanged = viewModel::onAmountInputChanged,
                                            onApplyQuickInput = viewModel::onApplyQuickInput,
                                            onTotalPaidChanged = viewModel::onTotalPaidChanged,
                                            onPaymentFieldClickedWithoutStatus = viewModel::onPaymentFieldClickedWithoutStatus,
                                            isEditable = isEditable
                                        )
                                    }
                                }
                            }
                        }

                        androidx.compose.animation.AnimatedVisibility(
                            visible = state.isSaving && !state.isFinishing,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 4.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.95f),
                                tonalElevation = 3.dp,
                                shadowElevation = 2.dp,
                                modifier = Modifier.testTag("attendance_background_sync_indicator")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        strokeWidth = 2.dp
                                    )
                                    Text(
                                        text = stringResource(
                                            id = R.string.attendance_syncing_background,
                                            state.pendingSyncCount.coerceAtLeast(1)
                                        ),
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // أزرار التنقل السفلية: السابق / التالي / إنهاء
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.onPrevious() },
                            enabled = !state.isFirst && !state.isFinishing,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp)
                                .testTag("wizard_previous_btn"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(id = R.string.action_previous))
                        }

                        if (state.isLast) {
                            Button(
                                onClick = { if (isEditable && !state.isFinishing) showFinishDialog = true },
                                enabled = isEditable && !state.isFinishing,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 52.dp)
                                    .testTag("wizard_finish_btn"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(id = R.string.action_finish_and_save))
                            }
                        } else {
                            Button(
                                onClick = {
                                    viewModel.onNextClicked {
                                        scope.launch {
                                            snackbarHostState.showSnackbar(skippedMessage)
                                        }
                                    }
                                },
                                enabled = isEditable && !state.isFinishing,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 52.dp)
                                    .testTag("wizard_next_btn"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(stringResource(id = R.string.action_next))
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // شاشة التحميل المعتمة فقط عند الضغط على إنهاء وحفظ في انتظار تفريغ مهام المزامنة
            if (state.isFinishing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                        .clickable(enabled = true, onClick = {})
                        .testTag("attendance_saving_overlay"),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp
                            )
                            Text(
                                text = stringResource(id = R.string.saving),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }

    // حوار تأكيد تغيير الحالة من غياب إلى حضور
    if (showAbsentToPresentWarning && pendingStatusChange != null) {
        AppAlertDialog(
            onDismissRequest = {
                showAbsentToPresentWarning = false
                pendingStatusChange = null
            },
            title = {
                Text(
                    text = stringResource(id = R.string.confirm_status_change_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = stringResource(id = R.string.confirm_absent_to_present_message),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingStatusChange?.let { viewModel.onSelectStatus(it) }
                        showAbsentToPresentWarning = false
                        pendingStatusChange = null
                    },
                    modifier = Modifier.testTag("confirm_absent_to_present_btn")
                ) {
                    Text(stringResource(id = R.string.confirm_action))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAbsentToPresentWarning = false
                        pendingStatusChange = null
                    }
                ) {
                    Text(stringResource(id = R.string.cancel_action))
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    // حوار تأكيد إنهاء وحفظ الحضور
    if (showFinishDialog) {
        AppAlertDialog(
            onDismissRequest = { showFinishDialog = false },
            title = {
                Text(
                    text = stringResource(id = R.string.confirm_finish_attendance_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = stringResource(id = R.string.confirm_finish_attendance_message),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFinishDialog = false
                        val online = isDeviceOnline(context)
                        viewModel.onFinishClicked(awaitServerConfirmation = online) {
                            if (!online || !isDeviceOnline(context)) {
                                Toast.makeText(context, offlineSaveToastMessage, Toast.LENGTH_LONG).show()
                            }
                            onNavigateBack()
                        }
                    },
                    enabled = !state.isFinishing,
                    modifier = Modifier.testTag("confirm_finish_attendance_btn")
                ) {
                    if (state.isFinishing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(stringResource(id = R.string.confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showFinishDialog = false }) {
                    Text(stringResource(id = R.string.cancel_action))
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    // حوار البحث عن شبل للانتقال السريع إليه
    if (showSearchDialog) {
        val editWindow = state.activity?.let { ActivityFormatters.attendanceEditWindow(it.startDate) } ?: AttendanceEditWindow.EDITABLE
        val isEditable = editWindow == AttendanceEditWindow.EDITABLE
        ScoutSearchDialog(
            scoutsQueue = state.scoutsQueue,
            recordedStatuses = state.recordedStatuses,
            currentScoutId = state.currentScout?.id,
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            onSelectScout = { scout ->
                showSearchDialog = false
                searchQuery = ""
                viewModel.jumpToScout(scout.id, saveCurrent = isEditable)
            },
            onDismiss = {
                showSearchDialog = false
                searchQuery = ""
            }
        )
    }
}

/**
 * حوار البحث عن شبل للقفز السريع إلى صفحته في معالج الحضور
 */
@Composable
private fun ScoutSearchDialog(
    scoutsQueue: List<Scout>,
    recordedStatuses: Map<String, AttendanceStatus>,
    currentScoutId: String?,
    query: String,
    onQueryChange: (String) -> Unit,
    onSelectScout: (Scout) -> Unit,
    onDismiss: () -> Unit
) {
    val terms = LocalAppTerms.current
    val indexedMatches = remember(query, scoutsQueue) {
        val trimmed = query.trim().lowercase()
        scoutsQueue.mapIndexedNotNull { index, scout ->
            if (trimmed.isEmpty() ||
                scout.fullName.lowercase().contains(trimmed) ||
                scout.name.lowercase().contains(trimmed) ||
                scout.surname.lowercase().contains(trimmed)
            ) {
                index to scout
            } else {
                null
            }
        }
    }

    AppDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "البحث عن ${terms.scoutSingular}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("attendance_search_close_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(id = R.string.attendance_search_close_desc),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text(stringResource(id = R.string.evaluation_search_scouts_hint)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(id = R.string.cancel_action)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("attendance_search_input")
                )

                if (indexedMatches.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = stringResource(id = R.string.empty_search_title),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = indexedMatches,
                            key = { (_, scout) -> scout.id }
                        ) { (index, scout) ->
                            val isCurrent = scout.id == currentScoutId
                            val recordedStatus = recordedStatuses[scout.id]
                            Surface(
                                onClick = { onSelectScout(scout) },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isCurrent) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                },
                                border = if (isCurrent) {
                                    androidx.compose.foundation.BorderStroke(
                                        1.5.dp,
                                        MaterialTheme.colorScheme.primary
                                    )
                                } else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .testTag("attendance_search_item_${scout.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "${index + 1}",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = scout.fullName,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (isCurrent) {
                                            Text(
                                                text = stringResource(id = R.string.attendance_search_current_badge),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    if (recordedStatus != null) {
                                        val statusColor = when (recordedStatus) {
                                            AttendanceStatus.PRESENT -> Color(0xFF2E7D32)
                                            AttendanceStatus.LATE -> Color(0xFFF57F17)
                                            AttendanceStatus.VERY_LATE -> Color(0xFFE64A19)
                                            AttendanceStatus.ABSENT_EXCUSED -> Color(0xFFE65100)
                                            AttendanceStatus.ABSENT_UNEXCUSED -> Color(0xFFC62828)
                                        }
                                        Surface(
                                            color = statusColor.copy(alpha = 0.14f),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = recordedStatus.arabicLabel,
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = statusColor,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
 * بطاقة الشبل الفردية داخل المعالج (Wizard Card)
 */
@Composable
private fun ScoutWizardCard(
    scout: Scout,
    selectedStatus: AttendanceStatus?,
    usesPoints: Boolean,
    pointsSettings: com.example.domain.attendance.AttendancePointsSettings,
    isTrainingMeeting: Boolean,
    onStatusSelected: (AttendanceStatus) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // صورة رمزية للشبل
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Face,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // الاسم واللقب بخط عريض وواضح
            Text(
                text = scout.fullName,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            // العمر وهاتف الولي
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.age_years_format, scout.age),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                if (scout.guardianPhone.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = scout.guardianPhone,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // أزرار اختيار الحالة: 5 أزرار للاجتماع التدريبي، وزران فقط للأنشطة غير التدريبية
            if (isTrainingMeeting) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // الصف الأول: حاضر + متأخر
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AttendanceOptionButton(
                            status = AttendanceStatus.PRESENT,
                            icon = Icons.Default.CheckCircle,
                            isSelected = selectedStatus == AttendanceStatus.PRESENT,
                            pointsText = if (usesPoints) "+${pointsSettings.presentPoints}" else null,
                            baseColor = Color(0xFF2E7D32),
                            containerColor = Color(0xFFE8F5E9),
                            onClick = { onStatusSelected(AttendanceStatus.PRESENT) },
                            testTag = "status_present_btn",
                            modifier = Modifier.weight(1f)
                        )

                        AttendanceOptionButton(
                            status = AttendanceStatus.LATE,
                            icon = Icons.Default.Schedule,
                            isSelected = selectedStatus == AttendanceStatus.LATE,
                            pointsText = if (usesPoints) "+${pointsSettings.latePoints}" else null,
                            baseColor = Color(0xFFF57F17),
                            containerColor = Color(0xFFFFFDE7),
                            onClick = { onStatusSelected(AttendanceStatus.LATE) },
                            testTag = "status_late_btn",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // الصف الثاني: متأخر جداً + غائب بعذر
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AttendanceOptionButton(
                            status = AttendanceStatus.VERY_LATE,
                            icon = Icons.Default.HourglassBottom,
                            isSelected = selectedStatus == AttendanceStatus.VERY_LATE,
                            pointsText = if (usesPoints) "+${pointsSettings.veryLatePoints}" else null,
                            baseColor = Color(0xFFE64A19),
                            containerColor = Color(0xFFFBE9E7),
                            onClick = { onStatusSelected(AttendanceStatus.VERY_LATE) },
                            testTag = "status_very_late_btn",
                            modifier = Modifier.weight(1f)
                        )

                        AttendanceOptionButton(
                            status = AttendanceStatus.ABSENT_EXCUSED,
                            icon = Icons.Default.Warning,
                            isSelected = selectedStatus == AttendanceStatus.ABSENT_EXCUSED,
                            pointsText = if (usesPoints) "+${pointsSettings.absentExcusedPoints}" else null,
                            baseColor = Color(0xFFE65100),
                            containerColor = Color(0xFFFFF3E0),
                            onClick = { onStatusSelected(AttendanceStatus.ABSENT_EXCUSED) },
                            testTag = "status_absent_excused_btn",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // الصف الثالث: غائب بدون عذر (عرض كامل)
                    AttendanceOptionButton(
                        status = AttendanceStatus.ABSENT_UNEXCUSED,
                        icon = Icons.Default.Cancel,
                        isSelected = selectedStatus == AttendanceStatus.ABSENT_UNEXCUSED,
                        pointsText = if (usesPoints) "+${pointsSettings.absentUnexcusedPoints}" else null,
                        baseColor = Color(0xFFC62828),
                        containerColor = Color(0xFFFFEBEE),
                        onClick = { onStatusSelected(AttendanceStatus.ABSENT_UNEXCUSED) },
                        testTag = "status_absent_unexcused_btn",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                // الأنشطة غير التدريبية: زران فقط (حاضر / غائب)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AttendanceOptionButton(
                        status = AttendanceStatus.PRESENT,
                        icon = Icons.Default.CheckCircle,
                        isSelected = selectedStatus == AttendanceStatus.PRESENT,
                        pointsText = if (usesPoints) "+${pointsSettings.presentPoints}" else null,
                        baseColor = Color(0xFF2E7D32),
                        containerColor = Color(0xFFE8F5E9),
                        onClick = { onStatusSelected(AttendanceStatus.PRESENT) },
                        testTag = "status_present_btn",
                        modifier = Modifier.weight(1f)
                    )

                    AttendanceOptionButton(
                        status = AttendanceStatus.ABSENT_UNEXCUSED,
                        icon = Icons.Default.Cancel,
                        isSelected = selectedStatus == AttendanceStatus.ABSENT_UNEXCUSED,
                        pointsText = if (usesPoints) "+${pointsSettings.absentUnexcusedPoints}" else null,
                        baseColor = Color(0xFFC62828),
                        containerColor = Color(0xFFFFEBEE),
                        onClick = { onStatusSelected(AttendanceStatus.ABSENT_UNEXCUSED) },
                        testTag = "status_absent_unexcused_btn",
                        modifier = Modifier.weight(1f),
                        customLabel = stringResource(id = R.string.status_absent_simple)
                    )
                }
            }
        }
    }
}

/**
 * زر خيار الحضور الكبير القابل للنقر بمساحة لمس عريضة (52dp+)
 */
@Composable
private fun AttendanceOptionButton(
    status: AttendanceStatus,
    icon: ImageVector,
    isSelected: Boolean,
    pointsText: String?,
    baseColor: Color,
    containerColor: Color,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    customLabel: String? = null
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 54.dp)
            .testTag(testTag)
            .border(
                width = if (isSelected) 2.5.dp else 1.dp,
                color = if (isSelected) baseColor else Color.LightGray.copy(alpha = 0.5f),
                shape = RoundedCornerShape(14.dp)
            ),
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) baseColor else containerColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) Color.White else baseColor,
                modifier = Modifier.size(22.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = customLabel ?: status.arabicLabel,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = if (isSelected) Color.White else Color.Black.copy(alpha = 0.85f),
                maxLines = 2,
                softWrap = true,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (pointsText != null) {
                Surface(
                    color = if (isSelected) Color.White.copy(alpha = 0.25f) else baseColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = pointsText,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isSelected) Color.White else baseColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

/**
 * بطاقة التحصيل المالي المصاحبة لبطاقة الشبل في معالج تسجيل الحضور
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AttendanceFinanceCard(
    uiState: AttendanceEntryUiState,
    currentScoutTotalPaid: Int,
    onAmountInputChanged: (String) -> Unit,
    onApplyQuickInput: () -> Unit,
    onTotalPaidChanged: (String) -> Unit,
    onPaymentFieldClickedWithoutStatus: () -> Unit,
    isEditable: Boolean,
    modifier: Modifier = Modifier
) {
    val terms = LocalAppTerms.current
    val isStatusSelected = uiState.selectedStatus != null
    val canPay = uiState.canPay
    var isEditingTotal by remember { mutableStateOf(false) }
    var editedTotal by remember { mutableStateOf("") }
    var showConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.currentIndex) {
        isEditingTotal = false
        showConfirmDialog = false
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // العنوان مع إجمالي المدفوع وقابلية التعديل
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Right: Text "التحصيل المالي" + Wallet Icon
                Row(
                    modifier = Modifier.align(Alignment.CenterVertically),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = stringResource(id = R.string.finance_section_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Left: إجمالي المدفوع أو حقل التعديل
                if (!isEditingTotal) {
                    Row(
                        modifier = Modifier.align(Alignment.CenterVertically),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "${stringResource(id = R.string.finance_total_paid_label)} $currentScoutTotalPaid دج",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("attendance_scout_total_paid_text")
                        )
                        IconButton(
                            onClick = {
                                editedTotal = uiState.totalPaidToday.toString()
                                isEditingTotal = true
                            },
                            enabled = isEditable && isStatusSelected && canPay,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("attendance_edit_total_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(id = R.string.action_edit),
                                tint = if (isEditable && isStatusSelected && canPay) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color.Gray
                                },
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.align(Alignment.CenterVertically),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        OutlinedTextField(
                            value = editedTotal,
                            onValueChange = { input ->
                                editedTotal = input.filter { it.isDigit() }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .width(90.dp)
                                .testTag("attendance_edit_total_input"),
                            shape = RoundedCornerShape(8.dp)
                        )
                        IconButton(
                            onClick = { showConfirmDialog = true },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("attendance_confirm_total_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(id = R.string.action_confirm),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = { isEditingTotal = false },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("attendance_cancel_total_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(id = R.string.action_cancel),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                thickness = 1.dp
            )

            // Row 1 (الديون السابقة)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.label_previous_debt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                Text(
                    text = stringResource(id = R.string.label_required_format, uiState.previousDebtRequired),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                val prevPaidColor = if (uiState.previousDebtPaid > 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                }
                Text(
                    text = stringResource(id = R.string.label_paid_format, uiState.previousDebtPaid),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (uiState.previousDebtPaid > 0) FontWeight.Bold else FontWeight.Normal
                    ),
                    color = prevPaidColor,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }

            // Row 2 (اشتراك اليوم)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.label_today_sub),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                Text(
                    text = if (isStatusSelected) {
                        stringResource(id = R.string.label_required_format, uiState.todaySubRequired)
                    } else {
                        "- -"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                val todayPaidColor = if (uiState.todaySubPaid > 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                }
                Text(
                    text = stringResource(id = R.string.label_paid_format, uiState.todaySubPaid),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (uiState.todaySubPaid > 0) FontWeight.Bold else FontWeight.Normal
                    ),
                    color = todayPaidColor,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }

            // Row 3 (الديون المتبقية)
            val remainingDebt = (uiState.previousDebtRequired - uiState.previousDebtPaid).coerceAtLeast(0)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.label_remaining_debt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                val remainingDebtColor = if (remainingDebt > 0) {
                    MaterialTheme.colorScheme.error
                } else {
                    Color(0xFF2E7D32)
                }
                Text(
                    text = "$remainingDebt دج",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = remainingDebtColor,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }

            if (uiState.overpaymentCredit > 0) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.label_advance_payment_credit),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                    Text(
                        text = "+${uiState.overpaymentCredit} دج",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                thickness = 1.dp
            )

            // Row 4 (حقل إدخال مبلغ سريع مع زر التطبيق ✅)
            val isValid = canPay && isStatusSelected && uiState.quickInputAmount.isNotBlank() && uiState.quickInputAmount.toIntOrNull() != null
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isStatusSelected) {
                            onPaymentFieldClickedWithoutStatus()
                        }
                ) {
                    OutlinedTextField(
                        value = uiState.quickInputAmount,
                        onValueChange = { text -> onAmountInputChanged(text) },
                        label = { Text(stringResource(id = R.string.finance_paid_amount_hint)) },
                        placeholder = { Text("0") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        enabled = isEditable && isStatusSelected && canPay,
                        trailingIcon = {
                            IconButton(
                                onClick = onApplyQuickInput,
                                enabled = isEditable && isValid,
                                modifier = Modifier.testTag("attendance_apply_quick_input_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = stringResource(id = R.string.action_confirm),
                                    tint = if (isValid && isEditable) {
                                        Color(0xFF2E7D32)
                                    } else {
                                        Color.Gray
                                    }
                                )
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("attendance_quick_input_field")
                    )

                    // Overlay to capture clicks when status is not selected yet
                    if (!isStatusSelected) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { onPaymentFieldClickedWithoutStatus() }
                        )
                    }
                }

                if (isStatusSelected && !canPay) {
                    Text(
                        text = "${terms.scoutSingularDefinite} غائب، لا يمكن تسجيل الدفع الآن.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                    )
                }
            }
        }
    }

    // Confirmation Dialog for editing total paid
    if (showConfirmDialog) {
        AppAlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = {
                Text(
                    text = stringResource(id = R.string.confirm_update_total_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(text = "هل أنت متأكد من تعديل إجمالي المبلغ المدفوع لهذا ${terms.scoutSingularDefinite}؟")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onTotalPaidChanged(editedTotal)
                        isEditingTotal = false
                        showConfirmDialog = false
                    }
                ) {
                    Text(stringResource(id = R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmDialog = false }
                ) {
                    Text(stringResource(id = R.string.action_cancel))
                }
            }
        )
    }
}
