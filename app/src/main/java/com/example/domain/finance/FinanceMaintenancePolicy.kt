package com.example.domain.finance

data class StoredSubscriptionState(
    val id: String,
    val scoutFullName: String,
    val activityTitle: String,
    val requiredAmount: Int,
    val storedPaidAmount: Int,
    val storedDebtAmount: Int?
)

data class StoredScoutCreditState(
    val id: String,
    val fullName: String,
    val storedCreditBalance: Int
)

data class SubscriptionBalanceFix(
    val subscriptionId: String,
    val newPaidAmount: Int?,
    val newDebtAmount: Int?
)

data class ScoutCreditFix(
    val scoutId: String,
    val newCreditBalance: Int
)

data class OverpaymentReportItem(
    val subscriptionId: String,
    val scoutName: String,
    val activityTitle: String,
    val excessAmount: Int
)

data class FinanceReconciliationPlan(
    val subscriptionFixes: List<SubscriptionBalanceFix>,
    val creditFixes: List<ScoutCreditFix>,
    val overpayments: List<OverpaymentReportItem>
)

data class FinanceMaintenanceReport(
    val subscriptionsChecked: Int,
    val scoutsChecked: Int,
    val negativePaidFixed: Int,
    val debtMismatchFixed: Int,
    val negativeCreditFixed: Int,
    val overpayments: List<OverpaymentReportItem>
)

/**
 * سياسة فحص ومواءمة الأرصدة المالية البحتة (دون أي تبعيات Firebase)
 */
object FinanceMaintenancePolicy {

    fun planReconciliation(
        subscriptions: List<StoredSubscriptionState>,
        scouts: List<StoredScoutCreditState>
    ): FinanceReconciliationPlan {
        val subscriptionFixes = mutableListOf<SubscriptionBalanceFix>()
        val overpayments = mutableListOf<OverpaymentReportItem>()

        for (sub in subscriptions) {
            val sanitizedPaid = sub.storedPaidAmount.coerceAtLeast(0)
            val derivedDebt = (sub.requiredAmount - sanitizedPaid).coerceAtLeast(0)
            val newPaid = if (sub.storedPaidAmount < 0) 0 else null
            val newDebt = if (sub.storedDebtAmount != derivedDebt) derivedDebt else null

            if (newPaid != null || newDebt != null) {
                subscriptionFixes.add(
                    SubscriptionBalanceFix(
                        subscriptionId = sub.id,
                        newPaidAmount = newPaid,
                        newDebtAmount = newDebt
                    )
                )
            }

            if (sanitizedPaid > sub.requiredAmount) {
                overpayments.add(
                    OverpaymentReportItem(
                        subscriptionId = sub.id,
                        scoutName = sub.scoutFullName.ifBlank { sub.id },
                        activityTitle = sub.activityTitle.ifBlank { "نشاط كشفي" },
                        excessAmount = sanitizedPaid - sub.requiredAmount
                    )
                )
            }
        }

        val creditFixes = scouts.mapNotNull { scout ->
            if (scout.storedCreditBalance < 0) {
                ScoutCreditFix(
                    scoutId = scout.id,
                    newCreditBalance = 0
                )
            } else {
                null
            }
        }

        return FinanceReconciliationPlan(
            subscriptionFixes = subscriptionFixes,
            creditFixes = creditFixes,
            overpayments = overpayments
        )
    }
}
