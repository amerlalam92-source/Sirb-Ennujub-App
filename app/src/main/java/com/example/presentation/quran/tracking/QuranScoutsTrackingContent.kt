package com.example.presentation.quran.tracking

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Warning
import com.example.presentation.common.AppAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.quran.ScoutQuranEnrollment
import com.example.domain.scout.Scout

/**
 * المحتوى التفاعلي لتبويب متابعة الأشبال وحفظهم
 */
@Composable
fun QuranScoutsTrackingContent(
    viewModel: QuranScoutsViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var enrollingScout by remember { mutableStateOf<Scout?>(null) }
    var editingScoutEnrollment by remember { mutableStateOf<Pair<Scout, ScoutQuranEnrollment>?>(null) }
    var enrollmentToDelete by remember { mutableStateOf<Pair<Scout, ScoutQuranEnrollment>?>(null) }

    val userMessage = (uiState as? QuranScoutsUiState.Success)?.userMessage
    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUserMessage()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("quran_scouts_tracking_tab"),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        when (val state = uiState) {
            is QuranScoutsUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.testTag("quran_scouts_loading_indicator")
                    )
                }
            }

            is QuranScoutsUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            is QuranScoutsUiState.Success -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    // شريط الفلاتر والإحصائية العلوية
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = state.selectedFilter == QuranScoutsFilter.ALL,
                            onClick = { viewModel.setFilter(QuranScoutsFilter.ALL) },
                            label = { Text("${stringResource(R.string.quran_scouts_filter_all)} (${state.totalScoutsCount})") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("filter_all_scouts")
                        )
                        FilterChip(
                            selected = state.selectedFilter == QuranScoutsFilter.ENROLLED,
                            onClick = { viewModel.setFilter(QuranScoutsFilter.ENROLLED) },
                            label = { Text("${stringResource(R.string.quran_scouts_filter_enrolled)} (${state.enrolledCount})") },
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.testTag("filter_enrolled_scouts")
                        )
                        FilterChip(
                            selected = state.selectedFilter == QuranScoutsFilter.NOT_ENROLLED,
                            onClick = { viewModel.setFilter(QuranScoutsFilter.NOT_ENROLLED) },
                            label = { Text("${stringResource(R.string.quran_scouts_filter_not_enrolled)} (${state.totalScoutsCount - state.enrolledCount})") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("filter_not_enrolled_scouts")
                        )
                    }

                    if (state.filteredScoutItems.isEmpty()) {
                        // حالات القائمة الفارغة حسب الفلتر المختار
                        val emptyMessage = when {
                            state.allScoutItems.isEmpty() -> stringResource(R.string.quran_scouts_empty_no_scouts)
                            state.selectedFilter == QuranScoutsFilter.ENROLLED -> stringResource(R.string.quran_scouts_empty_none_enrolled)
                            state.selectedFilter == QuranScoutsFilter.NOT_ENROLLED -> stringResource(R.string.quran_scouts_empty_all_enrolled)
                            else -> stringResource(R.string.quran_scouts_empty_no_scouts)
                        }

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
                                    imageVector = Icons.Default.Groups,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                )
                                Text(
                                    text = emptyMessage,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.testTag("quran_scouts_empty_message")
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("quran_scouts_lazy_column"),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                items = state.filteredScoutItems,
                                key = { it.scout.id }
                            ) { scoutItem ->
                                QuranScoutCard(
                                    scoutItem = scoutItem,
                                    onEnrollClicked = { scout -> enrollingScout = scout },
                                    onEditClicked = { scout, enrollment ->
                                        editingScoutEnrollment = scout to enrollment
                                    },
                                    onDeleteClicked = { scout, enrollment ->
                                        enrollmentToDelete = scout to enrollment
                                    }
                                )
                            }
                        }
                    }
                }

                // حوار التسجيل الجديد
                enrollingScout?.let { scout ->
                    EnrollmentFormDialog(
                        scout = scout,
                        existingEnrollment = null,
                        availableSchools = state.activeSchools,
                        onDismiss = { enrollingScout = null },
                        onSave = { school, parts, memoNote, days, periods, notes ->
                            viewModel.saveEnrollment(
                                scoutId = scout.id,
                                school = school,
                                currentMemorizedParts = parts,
                                memorizationNote = memoNote,
                                studyDays = days,
                                studyPeriods = periods,
                                generalNotes = notes
                            ) {
                                enrollingScout = null
                            }
                        }
                    )
                }

                // حوار تعديل التسجيل
                editingScoutEnrollment?.let { (scout, enrollment) ->
                    EnrollmentFormDialog(
                        scout = scout,
                        existingEnrollment = enrollment,
                        availableSchools = state.activeSchools,
                        onDismiss = { editingScoutEnrollment = null },
                        onSave = { school, parts, memoNote, days, periods, notes ->
                            viewModel.saveEnrollment(
                                scoutId = scout.id,
                                school = school,
                                currentMemorizedParts = parts,
                                memorizationNote = memoNote,
                                studyDays = days,
                                studyPeriods = periods,
                                generalNotes = notes
                            ) {
                                editingScoutEnrollment = null
                            }
                        }
                    )
                }

                // حوار تأكيد إلغاء التسجيل
                enrollmentToDelete?.let { (scout, _) ->
                    AppAlertDialog(
                        onDismissRequest = { enrollmentToDelete = null },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        title = {
                            Text(
                                text = stringResource(R.string.quran_scout_delete_enrollment_confirm_title),
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Text(
                                text = "${stringResource(R.string.quran_scout_delete_enrollment_confirm_msg)}\n\n(${scout.fullName})"
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    val id = scout.id
                                    enrollmentToDelete = null
                                    viewModel.deleteEnrollment(id)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier.testTag("confirm_delete_enrollment_button")
                            ) {
                                Text(stringResource(R.string.delete_action))
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { enrollmentToDelete = null },
                                modifier = Modifier.testTag("cancel_delete_enrollment_button")
                            ) {
                                Text(stringResource(R.string.quran_schools_cancel))
                            }
                        },
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }
        }
    }
}
