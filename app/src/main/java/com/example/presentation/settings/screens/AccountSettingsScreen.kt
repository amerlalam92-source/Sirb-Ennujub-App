package com.example.presentation.settings.screens

import android.widget.Toast
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.settings.replaceCustomWords
import com.example.presentation.common.AppAlertDialog
import com.example.presentation.common.CustomTopAppBar
import com.example.presentation.common.LocalAppTerms
import com.example.presentation.settings.SettingsEvent
import com.example.presentation.settings.SettingsViewModel

/**
 * شاشة إعدادات الحساب (AccountSettingsScreen)
 * تشمل: معلومات الحساب، ومنطقة الخطر لحذف الحساب ونقل الرئاسة.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToLogin: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isMainLeader by viewModel.isMainLeader.collectAsStateWithLifecycle()
    val terms = LocalAppTerms.current
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is SettingsEvent.AccountDeleted -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                    onNavigateToLogin()
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CustomTopAppBar(
                title = "إعدادات الحساب",
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("account_settings_back_btn")
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
            // بطاقة معلومات الحساب
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("account_info_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(
                                if (isMainLeader) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.secondaryContainer
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isMainLeader) Icons.Default.Security else Icons.Default.ManageAccounts,
                            contentDescription = null,
                            tint = if (isMainLeader) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isMainLeader) "حساب قائد رئيسي" else "حساب قائد كشفي",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isMainLeader) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = if (isMainLeader) replaceCustomWords("قائد ${terms.groupNameDefinite}", terms) else "قائد معتمد",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isMainLeader) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // قسم منطقة الخطر: حذف الحساب نهائياً
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("danger_zone_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(id = R.string.settings_danger_zone),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Text(
                        text = "عند حذف حسابك، سيتم إلغاء وصولك بشكل نهائي. في حال كنت القائد الرئيسي، سيتطلب ذلك نقل القيادة لقائد آخر مسجل أولاً.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedButton(
                        onClick = { viewModel.onDeleteAccountClicked() },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("delete_account_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(id = R.string.settings_delete_account),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }

        // 1) حوار حذف الحساب (Normal Leader)
        if (uiState.showDeleteAccountDialog) {
            AppAlertDialog(
                onDismissRequest = { viewModel.onDismissDeleteAccountDialog() },
                title = {
                    Text(
                        text = stringResource(id = R.string.delete_account_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.error
                    )
                },
                text = {
                    Text(
                        text = stringResource(id = R.string.delete_account_body),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.onConfirmDelete(null) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("confirm_delete_account_button")
                    ) {
                        Text(
                            text = stringResource(id = R.string.delete_account_title),
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { viewModel.onDismissDeleteAccountDialog() },
                        modifier = Modifier.testTag("cancel_delete_account_button")
                    ) {
                        Text(stringResource(id = R.string.action_cancel))
                    }
                }
            )
        }

        // 2) حوار نقل الرئاسة لحذف الحساب (Main Leader)
        if (uiState.showTransferLeadershipDialog) {
            var transferDropdownExpanded by remember { mutableStateOf(false) }
            var selectedLeader by remember(uiState.availableLeadersForTransfer) {
                mutableStateOf(uiState.availableLeadersForTransfer.firstOrNull())
            }

            AppAlertDialog(
                onDismissRequest = { viewModel.onDismissTransferLeadershipDialog() },
                title = {
                    Text(
                        text = "نقل الرئاسة لحذف الحساب",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.error
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.transfer_leadership_body),
                            style = MaterialTheme.typography.bodyMedium
                        )

                        ExposedDropdownMenuBox(
                            expanded = transferDropdownExpanded,
                            onExpandedChange = { transferDropdownExpanded = !transferDropdownExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = selectedLeader?.fullName?.ifBlank { selectedLeader?.email } ?: "",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(id = R.string.select_new_main_leader)) },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = transferDropdownExpanded)
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                                    .testTag("transfer_leader_dropdown"),
                                colors = OutlinedTextFieldDefaults.colors()
                            )

                            ExposedDropdownMenu(
                                expanded = transferDropdownExpanded,
                                onDismissRequest = { transferDropdownExpanded = false }
                            ) {
                                uiState.availableLeadersForTransfer.forEach { leader ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = leader.fullName.ifBlank { "قائد كشفي" },
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                                )
                                                if (leader.email.isNotBlank()) {
                                                    Text(
                                                        text = leader.email,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            selectedLeader = leader
                                            transferDropdownExpanded = false
                                        },
                                        modifier = Modifier.testTag("leader_item_${leader.uid}")
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            selectedLeader?.uid?.let { viewModel.onConfirmDelete(it) }
                        },
                        enabled = selectedLeader != null,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("confirm_transfer_delete_button")
                    ) {
                        Text(
                            text = stringResource(id = R.string.confirm_transfer_and_delete),
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { viewModel.onDismissTransferLeadershipDialog() },
                        modifier = Modifier.testTag("cancel_transfer_dialog_button")
                    ) {
                        Text(stringResource(id = R.string.action_cancel))
                    }
                }
            )
        }

        // 3) مؤشر جارٍ حذف الحساب
        if (uiState.isDeletingAccount) {
            Dialog(onDismissRequest = {}) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.error,
                            strokeWidth = 2.5.dp
                        )
                        Text(
                            text = stringResource(id = R.string.deleting_account_progress),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}
