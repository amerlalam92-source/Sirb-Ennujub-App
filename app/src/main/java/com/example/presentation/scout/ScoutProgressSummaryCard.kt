package com.example.presentation.scout

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.progress.BadgeLevel
import com.example.domain.progress.BadgeProgress
import com.example.domain.progress.shortLabelRes
import java.lang.String.format

/**
 * بطاقة ملخص بطاقة التقدم في شاشة تفاصيل الشبل (ScoutDetailScreen).
 * بطاقة واحدة قابلة للنقر تنقل القائد إلى الشاشة التفصيلية لبطاقة التقدم.
 */
@Composable
fun ScoutProgressSummaryCard(
    badgeProgressList: List<BadgeProgress>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // حساب الإحصائيات الإجمالية للشارات الأربعة
    val grantedCount = badgeProgressList.count { it.isGranted }
    val totalActive = badgeProgressList.sumOf { it.totalActiveRequirements }
    val totalCompleted = badgeProgressList.sumOf { it.completedActiveRequirements }
    val overallPercentage = if (totalActive > 0) {
        (totalCompleted * 100) / totalActive
    } else {
        0
    }

    val animatedProgress by animateFloatAsState(
        targetValue = overallPercentage / 100f,
        label = "overall_progress_anim"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("scout_progress_summary_card"),
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
            // سطر 1: العنوان على اليمين وشارة العداد على اليسار
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
                        imageVector = Icons.Default.WorkspacePremium,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = stringResource(id = R.string.progress_section_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = format(
                            com.example.presentation.common.AppLocale.AR_DZ,
                            stringResource(id = R.string.progress_granted_badges_count),
                            grantedCount
                        ),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // سطر 2: مؤشر التقدم الإجمالي ونسبة الإنجاز
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (grantedCount == 4 || overallPercentage == 100) {
                        Color(0xFF2E7D32)
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Text(
                    text = format(
                        com.example.presentation.common.AppLocale.AR_DZ,
                        "%d%%",
                        overallPercentage
                    ),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // سطر 3: صف مدمج من 4 عناصر مصغرة (شارة لكل مستوى: القبول / المبتدئ / الثاني / الأول)
            val orderedLevels = listOf(
                BadgeLevel.ACCEPTANCE,
                BadgeLevel.BEGINNER,
                BadgeLevel.SECOND,
                BadgeLevel.FIRST
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                orderedLevels.forEach { level ->
                    val badgeState = badgeProgressList.firstOrNull { it.level == level }
                    val isGranted = badgeState?.isGranted == true
                    val percentage = badgeState?.percentage ?: 0
                    val isInProgress = !isGranted && percentage > 0

                    BadgeMiniItem(
                        level = level,
                        isGranted = isGranted,
                        isInProgress = isInProgress
                    )
                }
            }

            // سطر 4: نص توجيهي لطيف وأيقونة السهم الخلفي للغة العربية RTL
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(id = R.string.progress_card_tap_for_details),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun BadgeMiniItem(
    level: BadgeLevel,
    isGranted: Boolean,
    isInProgress: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // دائرة ملونة مع أيقونة الشارة أو علامة الصح
        val circleModifier = Modifier
            .size(38.dp)
            .clip(CircleShape)

        val circleDecorated = when {
            isGranted -> {
                circleModifier.background(MaterialTheme.colorScheme.primary)
            }
            isInProgress -> {
                circleModifier
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                    .border(BorderStroke(2.dp, MaterialTheme.colorScheme.secondary), CircleShape)
            }
            else -> {
                circleModifier
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .border(BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)), CircleShape)
            }
        }

        Box(
            modifier = circleDecorated,
            contentAlignment = Alignment.Center
        ) {
            if (isGranted) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.WorkspacePremium,
                    contentDescription = null,
                    tint = if (isInProgress) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // الاسم المختصر للشارة
        Text(
            text = stringResource(id = level.shortLabelRes()),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            fontWeight = if (isGranted) FontWeight.Bold else FontWeight.Normal,
            color = if (isGranted) {
                MaterialTheme.colorScheme.primary
            } else if (isInProgress) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}
