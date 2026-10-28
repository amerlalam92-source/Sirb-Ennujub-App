package com.example.presentation.finance.subscriptions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.finance.FinanceSubscription
import com.example.domain.finance.SubscriptionStatus

/**
 * عنصر صف اشتراك الشبل في تفاصيل النشاط (SubscriptionScoutRow)
 * يعرض حالة الحضور والمبالغ المستحقة والمدفوعة، ويوفر أزرار تحصيل سريعة مع إدخال رقمي مخصص.
 */
@Composable
fun SubscriptionScoutRow(
    subscription: FinanceSubscription,
    onSavePayment: (newPaidAmount: Int) -> Unit,
    isSaving: Boolean,
    modifier: Modifier = Modifier
) {
    var textValue by remember(subscription.paidAmount) {
        mutableStateOf(subscription.paidAmount.toString())
    }

    val parsedPaid = textValue.toIntOrNull() ?: 0
    val hasChanged = parsedPaid != subscription.paidAmount

    val isDark = isSystemInDarkTheme()

    // ألوان شارة حالة الحضور
    val (statusBg, statusFg) = when (subscription.attendanceStatus) {
        SubscriptionStatus.PRESENT -> {
            if (isDark) Color(0xFF1B4A2A) to Color(0xFFA5D6A7)
            else Color(0xFFE8F5E9) to Color(0xFF1B5E20)
        }
        SubscriptionStatus.LATE -> {
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        }
        SubscriptionStatus.VERY_LATE -> {
            if (isDark) Color(0xFF4A1F0D) to Color(0xFFFFAB91)
            else Color(0xFFFBE9E7) to Color(0xFFE64A19)
        }
        SubscriptionStatus.ABSENT_EXCUSED -> {
            if (isDark) Color(0xFF4E2C00) to Color(0xFFFFCC80)
            else Color(0xFFFFE0B2) to Color(0xFFE65100)
        }
        SubscriptionStatus.ABSENT_UNEXCUSED -> {
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("subscription_scout_row_${subscription.scoutId}"),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Row 1: Scout name + attendance status chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = subscription.scoutFullName.ifBlank { "شبل كشفي" },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    color = statusBg,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = subscription.attendanceStatus.arabicLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = statusFg,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Row 2: Amounts: المطلوب / المدفوع / الدين
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(id = R.string.subscriptions_required_format, subscription.requiredAmount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(id = R.string.subscriptions_paid_format, subscription.paidAmount),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(id = R.string.subscriptions_debt_format, subscription.debtAmount),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = if (subscription.debtAmount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Row 3: Editing and quick action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { input ->
                        // السماح بالأرقام فقط
                        if (input.all { it.isDigit() } && input.length <= 6) {
                            textValue = input
                        }
                    },
                    modifier = Modifier
                        .width(120.dp)
                        .testTag("subscription_paid_input_${subscription.scoutId}"),
                    label = { Text(stringResource(id = R.string.subscription_detail_paid_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                Button(
                    onClick = {
                        onSavePayment(parsedPaid)
                    },
                    enabled = hasChanged && !isSaving,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(),
                    modifier = Modifier.testTag("subscription_save_button_${subscription.scoutId}")
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = stringResource(id = R.string.subscription_detail_save),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // أزرار التسديد السريع
                if (subscription.paidAmount < subscription.requiredAmount) {
                    TextButton(
                        onClick = {
                            textValue = subscription.requiredAmount.toString()
                            onSavePayment(subscription.requiredAmount)
                        },
                        enabled = !isSaving,
                        modifier = Modifier.testTag("subscription_pay_full_${subscription.scoutId}")
                    ) {
                        Text(
                            text = stringResource(id = R.string.subscription_detail_pay_full),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                } else if (subscription.debtAmount > 0) {
                    TextButton(
                        onClick = {
                            textValue = subscription.requiredAmount.toString()
                            onSavePayment(subscription.requiredAmount)
                        },
                        enabled = !isSaving,
                        modifier = Modifier.testTag("subscription_pay_debt_${subscription.scoutId}")
                    ) {
                        Text(
                            text = stringResource(id = R.string.subscription_detail_pay_debt),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}
