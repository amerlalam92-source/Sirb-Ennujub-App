package com.example.presentation.quran.tracking

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.R
import com.example.domain.quran.ScoutQuranEnrollment
import com.example.domain.scout.Scout
import com.example.ui.theme.EnrolledGreenContainerDark
import com.example.ui.theme.EnrolledGreenContainerLight
import com.example.ui.theme.EnrolledGreenContentDark
import com.example.ui.theme.EnrolledGreenContentLight
import com.example.ui.theme.NotEnrolledRedContainerDark
import com.example.ui.theme.NotEnrolledRedContainerLight
import com.example.ui.theme.NotEnrolledRedContentDark
import com.example.ui.theme.NotEnrolledRedContentLight

/**
 * بطاقة عرض الشبل مع تفاصيل تسجيله في المدرسة القرآنية ومقدار الحفظ
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuranScoutCard(
    scoutItem: QuranScoutItem,
    onEnrollClicked: (Scout) -> Unit,
    onEditClicked: (Scout, ScoutQuranEnrollment) -> Unit,
    onDeleteClicked: (Scout, ScoutQuranEnrollment) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scout = scoutItem.scout
    val enrollment = scoutItem.enrollment
    var expandedDetails by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { expandedDetails = !expandedDetails }
            .testTag("quran_scout_card_${scout.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // الصف العلوي: الاسم + شارة التسجيل + زر القائمة/التوسيع
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // الأيقونة الدائرية
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (enrollment != null) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (enrollment != null) Icons.Default.MenuBook else Icons.Default.Person,
                        contentDescription = null,
                        tint = if (enrollment != null) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = scout.fullName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("scout_name_${scout.id}")
                    )

                    // شارة التسجيل (ألوان دلالية خضراء للمسجلين وحمراء لغير المسجلين)
                    val isDark = isSystemInDarkTheme()
                    if (enrollment != null) {
                        Surface(
                            color = if (isDark) EnrolledGreenContainerDark else EnrolledGreenContainerLight,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.quran_scout_badge_enrolled),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) EnrolledGreenContentDark else EnrolledGreenContentLight,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    } else {
                        Surface(
                            color = if (isDark) NotEnrolledRedContainerDark else NotEnrolledRedContainerLight,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.quran_scout_badge_not_enrolled),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) NotEnrolledRedContentDark else NotEnrolledRedContentLight,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                if (enrollment != null) {
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.testTag("scout_menu_button_${scout.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "خيارات الشبل"
                            )
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.quran_scout_edit_enrollment_action)) },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onEditClicked(scout, enrollment)
                                },
                                modifier = Modifier.testTag("scout_menu_edit_${scout.id}")
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.quran_scout_delete_enrollment_action),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onDeleteClicked(scout, enrollment)
                                },
                                modifier = Modifier.testTag("scout_menu_delete_${scout.id}")
                            )
                        }
                    }
                }

                IconButton(
                    onClick = { expandedDetails = !expandedDetails },
                    modifier = Modifier.testTag("scout_expand_button_${scout.id}")
                ) {
                    Icon(
                        imageVector = if (expandedDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "تفاصيل"
                    )
                }
            }

            // إذا كان الشبل غير مسجل
            if (enrollment == null) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { onEnrollClicked(scout) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("enroll_scout_button_${scout.id}"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.HowToReg,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.quran_scout_enroll_action),
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                // إذا كان الشبل مسجل: عرض مختصر للمدرسة وعدد الأجزاء
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = enrollment.schoolName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // شريحة عدد الأجزاء
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = if (enrollment.currentMemorizedParts > 0)
                                stringResource(R.string.quran_scout_memorized_parts_chip, enrollment.currentMemorizedParts)
                            else stringResource(R.string.quran_scout_memorized_parts_zero),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                // تفاصيل الحفظ المفتوحة القابلة للتوسيع
                AnimatedVisibility(visible = expandedDetails) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        // المعلم ورقم الهاتف مع زر الاتصال
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "الشيخ: ${enrollment.teacherName}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = enrollment.teacherPhone,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (enrollment.teacherPhone.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        try {
                                            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                                                data = Uri.parse("tel:${enrollment.teacherPhone}")
                                            }
                                            context.startActivity(dialIntent)
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "تعذر فتح تطبيق الهاتف", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.testTag("call_scout_teacher_${scout.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Call,
                                        contentDescription = "اتصال بالمعلم",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        // ملاحظة الحفظ الحالية
                        enrollment.memorizationNote?.takeIf { it.isNotBlank() }?.let { note ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "ملاحظة الحفظ: $note",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // أيام الدوام
                        if (enrollment.studyDays.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.quran_scout_study_days_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                enrollment.studyDays.forEach { dayKey ->
                                    val dayObj = QuranStudyDay.fromKey(dayKey)
                                    val label = if (dayObj != null) stringResource(dayObj.labelRes) else dayKey
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                        )
                                    )
                                }
                            }
                        }

                        // فترات الدوام
                        if (enrollment.studyPeriods.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.quran_scout_study_periods_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                enrollment.studyPeriods.forEach { sp ->
                                    val periodLabel = stringResource(sp.period.labelRes)
                                    val displayLabel = if (sp.startTime != null) {
                                        "$periodLabel (${sp.startTime})"
                                    } else {
                                        periodLabel
                                    }
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text(displayLabel, style = MaterialTheme.typography.labelSmall) },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                        )
                                    )
                                }
                            }
                        }

                        // ملاحظات عامة
                        enrollment.generalNotes?.takeIf { it.isNotBlank() }?.let { notes ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${stringResource(R.string.quran_scout_notes_label)} $notes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // أزرار سريعة
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = { onEditClicked(scout, enrollment) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("quick_edit_enrollment_${scout.id}")
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.quran_scout_edit_enrollment_action))
                            }
                        }
                    }
                }
            }
        }
    }
}
