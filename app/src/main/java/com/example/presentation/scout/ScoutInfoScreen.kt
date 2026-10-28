package com.example.presentation.scout

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.presentation.common.CustomTopAppBar
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.scout.Scout
import com.example.presentation.common.AppLocale
import java.text.SimpleDateFormat
import java.util.Date

/**
 * شاشة معلومات الشبل التفصيلية (ScoutInfoScreen)
 * تعرض كافة البيانات الشخصية وبيانات ولي الأمر والمعلومات الإضافية والملاحظات في بطاقات منسقة.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoutInfoScreen(
    viewModel: ScoutInfoViewModel,
    onNavigateToEdit: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler {
        onNavigateBack()
    }

    val dateFormatter = remember { SimpleDateFormat("yyyy/MM/dd", AppLocale.AR_DZ) }
    val timeFormatter = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", AppLocale.AR_DZ) }
    var showPhonePicker by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            val title = when (val state = uiState) {
                is ScoutInfoUiState.Success -> stringResource(
                    id = R.string.scout_info_screen_title_format,
                    state.scout.fullName
                )
                else -> stringResource(id = R.string.scout_info_card_title)
            }
            CustomTopAppBar(
                title = title,
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("scout_info_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.cancel_action),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    if (uiState is ScoutInfoUiState.Success) {
                        val scout = (uiState as ScoutInfoUiState.Success).scout
                        IconButton(
                            onClick = { onNavigateToEdit(scout.id) },
                            modifier = Modifier.testTag("scout_info_edit_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(id = R.string.edit_scout_title),
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
                is ScoutInfoUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .testTag("scout_info_loading"),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                is ScoutInfoUiState.Error -> {
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
                        Button(onClick = { viewModel.loadScout() }) {
                            Text("إعادة المحاولة")
                        }
                    }
                }

                is ScoutInfoUiState.Success -> {
                    val scout = state.scout
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // أ) ترويسة مختصرة للشبل
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Face,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(34.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = scout.fullName,
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = stringResource(id = R.string.age_years_format, scout.age),
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }

                                        Surface(
                                            color = if (scout.isActive) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outline,
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = if (scout.isActive) stringResource(id = R.string.status_active)
                                                else stringResource(id = R.string.status_archived),
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // A) المعلومات الشخصية
                        InfoSectionCard(title = stringResource(id = R.string.scout_info_section_personal)) {
                            InfoItemRow(
                                icon = Icons.Default.Person,
                                label = stringResource(id = R.string.scout_name_label),
                                value = scout.name
                            )
                            InfoItemRow(
                                icon = Icons.Default.Badge,
                                label = stringResource(id = R.string.scout_surname_label),
                                value = scout.surname
                            )
                            InfoItemRow(
                                icon = Icons.Default.Cake,
                                label = stringResource(id = R.string.birth_date_label),
                                value = if (scout.birthDate > 0) dateFormatter.format(Date(scout.birthDate)) else "—"
                            )
                            InfoItemRow(
                                icon = Icons.Default.Face,
                                label = stringResource(id = R.string.calculated_age_label),
                                value = stringResource(id = R.string.age_years_format, scout.age)
                            )
                        }

                        // B) معلومات ولي الأمر
                        InfoSectionCard(title = stringResource(id = R.string.scout_info_section_guardian)) {
                            InfoItemRow(
                                icon = Icons.Default.Person,
                                label = stringResource(id = R.string.guardian_name_label),
                                value = scout.guardianName.ifBlank { "غير مسجل" }
                            )
                            InfoItemRow(
                                icon = Icons.Default.Group,
                                label = stringResource(id = R.string.guardian_relation_label),
                                value = scout.guardianRelation
                            )
                            InfoItemRow(
                                icon = Icons.Default.Phone,
                                label = stringResource(id = R.string.phone_picker_primary),
                                value = scout.guardianPhone.ifBlank { "—" }
                            )

                            scout.additionalPhones.forEachIndexed { index, extraPhone ->
                                InfoItemRow(
                                    icon = Icons.Default.Phone,
                                    label = stringResource(id = R.string.phone_picker_additional_format, index + 1),
                                    value = extraPhone
                                )
                            }

                            val phones = scout.allPhones()
                            if (phones.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Button(
                                    onClick = {
                                        if (phones.size == 1) {
                                            val intent = Intent(Intent.ACTION_DIAL).apply {
                                                data = Uri.parse("tel:${phones.first()}")
                                            }
                                            context.startActivity(intent)
                                        } else {
                                            showPhonePicker = true
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("scout_info_call_guardian_button"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Call,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(id = R.string.action_call_guardian),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }

                        // C) معلومات إضافية
                        InfoSectionCard(title = stringResource(id = R.string.scout_info_section_additional)) {
                            InfoItemRow(
                                icon = Icons.Default.LocationOn,
                                label = stringResource(id = R.string.address_label),
                                value = scout.address.ifBlank { "غير مسجل" }
                            )
                            InfoItemRow(
                                icon = Icons.Default.School,
                                label = stringResource(id = R.string.school_label),
                                value = scout.school.ifBlank { "غير مسجلة" }
                            )
                            InfoItemRow(
                                icon = Icons.Default.CalendarToday,
                                label = stringResource(id = R.string.join_year_label),
                                value = scout.joinYear.toString()
                            )
                        }

                        // D) ملاحظات (إن وجدت)
                        if (scout.notes.isNotBlank()) {
                            InfoSectionCard(title = stringResource(id = R.string.scout_info_section_notes)) {
                                InfoItemRow(
                                    icon = Icons.Default.Notes,
                                    label = stringResource(id = R.string.notes_label),
                                    value = scout.notes
                                )
                            }
                        }

                        // E) تذييل السجل وتواريخ الإنشاء والتعديل
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val unknownLeaderFallback = stringResource(id = R.string.placeholder_unknown_leader)

                                if (scout.createdAt > 0) {
                                    val createdTime = timeFormatter.format(Date(scout.createdAt))
                                    val creatorDisplayName = state.createdByName
                                        ?: (if (scout.createdBy.isNotBlank()) unknownLeaderFallback else null)
                                    val text = if (creatorDisplayName != null) {
                                        "أضيف بتاريخ: $createdTime — بواسطة: $creatorDisplayName"
                                    } else {
                                        "أضيف بتاريخ: $createdTime"
                                    }
                                    Text(
                                        text = text,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (scout.updatedAt > 0) {
                                    val updatedTime = timeFormatter.format(Date(scout.updatedAt))
                                    val updaterDisplayName = state.updatedByName
                                        ?: (if (scout.updatedBy.isNotBlank()) unknownLeaderFallback else null)
                                    val text = if (updaterDisplayName != null) {
                                        "آخر تعديل: $updatedTime — بواسطة: $updaterDisplayName"
                                    } else {
                                        "آخر تعديل: $updatedTime"
                                    }
                                    Text(
                                        text = text,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPhonePicker) {
        val currentScout = (uiState as? ScoutInfoUiState.Success)?.scout
        if (currentScout != null) {
            PhonePickerDialog(
                scout = currentScout,
                onDismiss = { showPhonePicker = false }
            )
        }
    }
}

@Composable
private fun InfoSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}

@Composable
private fun InfoItemRow(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End
        )
    }
}
