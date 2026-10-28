package com.example.presentation.quran.schools

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Warning
import com.example.presentation.common.AppAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.example.presentation.common.CustomTopAppBar
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
import com.example.domain.quran.QuranSchool

/**
 * شاشة دليل المدارس القرآنية
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuranSchoolsListScreen(
    viewModel: QuranSchoolsViewModel,
    onNavigateBack: () -> Unit = {},
    showTopBar: Boolean = true,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingSchool by remember { mutableStateOf<QuranSchool?>(null) }
    var schoolToDelete by remember { mutableStateOf<QuranSchool?>(null) }
    var cannotDeleteMessage by remember { mutableStateOf<String?>(null) }

    val userMessage = (uiState as? QuranSchoolsUiState.Success)?.userMessage
    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUserMessage()
        }
    }

    Scaffold(
        modifier = modifier.testTag("quran_schools_list_screen"),
        topBar = {
            if (showTopBar) {
                CustomTopAppBar(
                    title = stringResource(R.string.quran_schools_title),
                    navigationIcon = {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.testTag("quran_schools_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back_action)
                            )
                        }
                    }
                )
        }
    },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.testTag("add_quran_school_fab"),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.quran_schools_add_new)
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        when (val state = uiState) {
            is QuranSchoolsUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.testTag("quran_schools_loading_indicator")
                    )
                }
            }

            is QuranSchoolsUiState.Error -> {
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

            is QuranSchoolsUiState.Success -> {
                if (state.schools.isEmpty()) {
                    // شاشة خالية عند عدم وجود أي مدرسة
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.MenuBook,
                                contentDescription = null,
                                modifier = Modifier.size(72.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            )
                            Text(
                                text = stringResource(R.string.quran_schools_empty_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = stringResource(R.string.quran_schools_empty_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { showAddDialog = true },
                                modifier = Modifier.testTag("add_first_quran_school_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(stringResource(R.string.quran_schools_add_first))
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // صف الرأس الصغير مع عدد المدارس النشطة
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.quran_schools_count_format, state.activeCount),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // قائمة بطاقات المدارس مرتبة أبجدياً
                        items(
                            items = state.schools,
                            key = { it.school.id }
                        ) { schoolItem ->
                            QuranSchoolCard(
                                schoolItem = schoolItem,
                                onEdit = { school -> editingSchool = school },
                                onDelete = { school ->
                                    if (schoolItem.enrolledScoutsCount > 0) {
                                        cannotDeleteMessage = "لا يمكن حذف المدرسة القرآنية \"${school.name}\" لوجود ${schoolItem.enrolledScoutsCount} شبل مسجل بها. يمكنك أرشفة المدرسة بدلاً من ذلك."
                                    } else {
                                        schoolToDelete = school
                                    }
                                },
                                onToggleArchive = { school ->
                                    if (school.archived) {
                                        viewModel.unarchiveSchool(school.id)
                                    } else {
                                        viewModel.archiveSchool(school.id)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // حوار إضافة مدرسة جديدة
    if (showAddDialog) {
        QuranSchoolFormDialog(
            initialSchool = null,
            onDismiss = { showAddDialog = false },
            onSave = { name, teacherName, teacherPhone, address, notes ->
                viewModel.addSchool(name, teacherName, teacherPhone, address, notes) {
                    showAddDialog = false
                }
            }
        )
    }

    // حوار تعديل مدرسة
    editingSchool?.let { school ->
        QuranSchoolFormDialog(
            initialSchool = school,
            onDismiss = { editingSchool = null },
            onSave = { name, teacherName, teacherPhone, address, notes ->
                viewModel.updateSchool(
                    school.copy(
                        name = name,
                        teacherName = teacherName,
                        teacherPhone = teacherPhone,
                        address = address,
                        notes = notes
                    )
                ) {
                    editingSchool = null
                }
            }
        )
    }

    // حوار تأكيد الحذف
    schoolToDelete?.let { school ->
        AppAlertDialog(
            onDismissRequest = { schoolToDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.quran_schools_delete_confirm_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "${stringResource(R.string.quran_schools_delete_confirm_msg)}\n\n(${school.name})"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = school.id
                        schoolToDelete = null
                        viewModel.deleteSchool(id) { errorMsg ->
                            cannotDeleteMessage = errorMsg
                        }
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("confirm_delete_school_button")
                ) {
                    Text(stringResource(R.string.delete_action))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { schoolToDelete = null },
                    modifier = Modifier.testTag("cancel_delete_school_button")
                ) {
                    Text(stringResource(R.string.quran_schools_cancel))
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // حوار تنبيه تعذر الحذف لوجود أشبال مسجلين
    cannotDeleteMessage?.let { msg ->
        AppAlertDialog(
            onDismissRequest = { cannotDeleteMessage = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.quran_schools_cannot_delete_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(text = msg)
            },
            confirmButton = {
                Button(
                    onClick = { cannotDeleteMessage = null },
                    modifier = Modifier.testTag("cannot_delete_dialog_ok_button")
                ) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}
