package com.example.presentation.leaders

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.auth.Leader
import com.example.presentation.common.AppAlertDialog
import com.example.presentation.common.CustomTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderManagementScreen(
    viewModel: LeaderManagementViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is LeaderManagementEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("leader_management_screen"),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CustomTopAppBar(
                title = stringResource(id = R.string.leader_management_title),
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("leader_management_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.cancel_action)
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
        ) {
            // شريط التبويبات
            TabRow(
                selectedTabIndex = uiState.selectedTab,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = uiState.selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(id = R.string.tab_pending_leaders))
                            if (uiState.pendingLeaders.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ) {
                                    Text(uiState.pendingLeaders.size.toString())
                                }
                            }
                        }
                    },
                    modifier = Modifier.testTag("tab_pending_leaders")
                )
                Tab(
                    selected = uiState.selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(id = R.string.tab_approved_leaders))
                            if (uiState.approvedLeaders.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ) {
                                    Text(uiState.approvedLeaders.size.toString())
                                }
                            }
                        }
                    },
                    modifier = Modifier.testTag("tab_approved_leaders")
                )
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                when (uiState.selectedTab) {
                    0 -> PendingLeadersList(
                        leaders = uiState.pendingLeaders,
                        onApprove = { viewModel.approveLeader(it) },
                        onReject = { viewModel.rejectLeader(it) },
                        isActionInProgress = uiState.isActionInProgress,
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                    )
                    1 -> ApprovedLeadersList(
                        leaders = uiState.approvedLeaders,
                        mainLeadersCount = uiState.mainLeadersCount,
                        onDelete = { viewModel.requestDeleteLeader(it) },
                        onToggleMainLeader = { viewModel.onToggleMainLeaderClicked(it) },
                        isActionInProgress = uiState.isActionInProgress,
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                    )
                }
            }
        }

        // حوار تأكيد حذف قائد معتمد
        val pendingDelete = uiState.leaderPendingDelete
        if (pendingDelete != null) {
            AppAlertDialog(
                onDismissRequest = { viewModel.dismissDeleteDialog() },
                title = {
                    Text(
                        text = stringResource(id = R.string.leader_delete),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.error
                    )
                },
                text = {
                    Text(
                        text = "${stringResource(id = R.string.confirm_delete_leader)} (${pendingDelete.fullName})",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.confirmDeleteLeader() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.testTag("confirm_delete_leader_button")
                    ) {
                        Text(stringResource(id = R.string.leader_delete))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { viewModel.dismissDeleteDialog() },
                        modifier = Modifier.testTag("cancel_delete_leader_button")
                    ) {
                        Text(stringResource(id = R.string.cancel_action))
                    }
                }
            )
        }

        // حوار تأكيد تعيين أو إلغاء القائد الرئيسي
        val leaderToToggle = viewModel.leaderToToggle
        if (leaderToToggle != null) {
            val isMakingMain = viewModel.isActionMakingMainLeader
            AppAlertDialog(
                onDismissRequest = { viewModel.dismissToggleMainLeaderDialog() },
                title = {
                    Text(
                        text = if (isMakingMain) {
                            stringResource(id = R.string.confirm_make_main_leader_title)
                        } else {
                            stringResource(id = R.string.confirm_revoke_main_leader_title)
                        },
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = if (isMakingMain) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                },
                text = {
                    Text(
                        text = if (isMakingMain) {
                            stringResource(id = R.string.confirm_make_main_leader_body_format, leaderToToggle.fullName)
                        } else {
                            stringResource(id = R.string.confirm_revoke_main_leader_body_format, leaderToToggle.fullName)
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.confirmToggleMainLeader() },
                        colors = if (isMakingMain) {
                            ButtonDefaults.buttonColors()
                        } else {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        },
                        modifier = Modifier.testTag("confirm_toggle_main_leader_button")
                    ) {
                        Text(stringResource(id = R.string.action_confirm))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { viewModel.dismissToggleMainLeaderDialog() },
                        modifier = Modifier.testTag("cancel_toggle_main_leader_button")
                    ) {
                        Text(stringResource(id = R.string.action_cancel))
                    }
                }
            )
        }
    }
}

@Composable
private fun PendingLeadersList(
    leaders: List<Leader>,
    onApprove: (Leader) -> Unit,
    onReject: (Leader) -> Unit,
    isActionInProgress: Boolean,
    modifier: Modifier = Modifier
) {
    if (leaders.isEmpty()) {
        Box(
            modifier = modifier.padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(id = R.string.no_pending_leaders),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    } else {
        LazyColumn(
            modifier = modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(leaders, key = { it.uid }) { leader ->
                PendingLeaderCard(
                    leader = leader,
                    onApprove = { onApprove(leader) },
                    onReject = { onReject(leader) },
                    isActionInProgress = isActionInProgress
                )
            }
        }
    }
}

@Composable
private fun PendingLeaderCard(
    leader: Leader,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    isActionInProgress: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pending_leader_card_${leader.uid}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
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
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = leader.fullName.ifBlank { "قائد كشفي" },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (leader.email.isNotBlank()) {
                        Text(
                            text = leader.email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onApprove,
                    enabled = !isActionInProgress,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("approve_button_${leader.uid}")
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(id = R.string.leader_approve))
                }

                OutlinedButton(
                    onClick = onReject,
                    enabled = !isActionInProgress,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("reject_button_${leader.uid}")
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(id = R.string.leader_reject))
                }
            }
        }
    }
}

@Composable
private fun ApprovedLeadersList(
    leaders: List<Leader>,
    mainLeadersCount: Int,
    onDelete: (Leader) -> Unit,
    onToggleMainLeader: (Leader) -> Unit,
    isActionInProgress: Boolean,
    modifier: Modifier = Modifier
) {
    if (leaders.isEmpty()) {
        Box(
            modifier = modifier.padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(id = R.string.no_approved_leaders),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    } else {
        LazyColumn(
            modifier = modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(leaders, key = { it.uid }) { leader ->
                val isOnlyMainLeader = leader.isMainLeader && mainLeadersCount <= 1
                ApprovedLeaderCard(
                    leader = leader,
                    isOnlyMainLeader = isOnlyMainLeader,
                    onDelete = { onDelete(leader) },
                    onToggleMainLeader = { onToggleMainLeader(leader) },
                    isActionInProgress = isActionInProgress
                )
            }
        }
    }
}

@Composable
private fun ApprovedLeaderCard(
    leader: Leader,
    isOnlyMainLeader: Boolean,
    onDelete: () -> Unit,
    onToggleMainLeader: () -> Unit,
    isActionInProgress: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("approved_leader_card_${leader.uid}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (leader.isMainLeader) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (leader.isMainLeader) Icons.Default.Star else Icons.Default.Person,
                        contentDescription = null,
                        tint = if (leader.isMainLeader) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = leader.fullName.ifBlank { "قائد كشفي" },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (leader.isMainLeader) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = stringResource(id = R.string.main_leader_badge),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    if (leader.email.isNotBlank()) {
                        Text(
                            text = leader.email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = onToggleMainLeader,
                    enabled = !isActionInProgress && !isOnlyMainLeader,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("toggle_main_leader_${leader.uid}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (leader.isMainLeader) stringResource(id = R.string.action_remove_main_leader)
                        else stringResource(id = R.string.action_make_main_leader),
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                OutlinedButton(
                    onClick = onDelete,
                    enabled = !isActionInProgress,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("delete_leader_button_${leader.uid}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(id = R.string.leader_delete),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(id = R.string.leader_delete))
                }
            }
        }
    }
}
