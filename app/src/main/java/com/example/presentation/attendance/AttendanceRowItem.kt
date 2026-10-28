package com.example.presentation.attendance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.attendance.AttendanceRow
import com.example.domain.attendance.AttendanceStatus

/**
 * سطر عرض حالة حضور شبل محدد في نشاط كشفي
 */
@Composable
fun AttendanceRowItem(
    row: AttendanceRow,
    usesPointsSystem: Boolean,
    modifier: Modifier = Modifier
) {
    val attendance = row.attendance
    val status = attendance?.attendanceStatus

    val (statusLabel, statusColor, statusBgColor) = when (status) {
        AttendanceStatus.PRESENT -> Triple(
            stringResource(id = R.string.status_present),
            Color(0xFF2E7D32), // green
            Color(0xFFE8F5E9)
        )
        AttendanceStatus.LATE -> Triple(
            stringResource(id = R.string.status_late),
            Color(0xFFF57C00), // amber
            Color(0xFFFFF3E0)
        )
        AttendanceStatus.VERY_LATE -> Triple(
            stringResource(id = R.string.attendance_status_very_late),
            Color(0xFFE64A19), // deep orange
            Color(0xFFFBE9E7)
        )
        AttendanceStatus.ABSENT_EXCUSED -> Triple(
            stringResource(id = R.string.status_absent_excused),
            Color(0xFFE65100), // orange
            Color(0xFFFFE0B2)
        )
        AttendanceStatus.ABSENT_UNEXCUSED -> Triple(
            stringResource(id = R.string.status_absent_unexcused),
            MaterialTheme.colorScheme.error, // red
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        )
        null -> Triple(
            stringResource(id = R.string.attendance_unrecorded_status),
            Color(0xFF757575), // gray
            Color(0xFFEEEEEE)
        )
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("attendance_row_${row.scout.id}"),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // اسم ولقب الشبل
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.scout.fullName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // شريحة حالة الحضور
            Surface(
                color = statusBgColor,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = statusColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            // قيمة النقاط (إذا كان النشاط يطبق سلم التنقيط)
            if (usesPointsSystem) {
                Spacer(modifier = Modifier.width(10.dp))
                val pointsText = if (attendance != null) {
                    if (attendance.points > 0) "+${attendance.points}" else "${attendance.points}"
                } else {
                    "—"
                }
                Surface(
                    color = if (attendance != null && attendance.points > 0)
                        MaterialTheme.colorScheme.secondaryContainer
                    else
                        MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = pointsText,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (attendance != null && attendance.points > 0)
                            MaterialTheme.colorScheme.secondary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
