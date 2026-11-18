package com.example.domain.finance

/**
 * نتيجة توزيع مبلغ السداد أو التراجع على اشتراكات الشبل
 */
data class DebtAllocationResult(
    val updatedSubscriptions: List<FinanceSubscription>,
    val totalApplied: Int,
    val unallocatedAmount: Int
)

/**
 * حاسبة توزيع سداد الديون والتراجع عنها بترتيب الأقدم فالأحدث (Oldest First)
 */
object DebtAllocationCalculator {

    fun allocatePayment(
        subscriptions: List<FinanceSubscription>,
        amountToPay: Int,
        now: Long = System.currentTimeMillis()
    ): DebtAllocationResult {
        if (amountToPay <= 0 || subscriptions.isEmpty()) {
            return DebtAllocationResult(
                updatedSubscriptions = emptyList(),
                totalApplied = 0,
                unallocatedAmount = amountToPay.coerceAtLeast(0)
            )
        }

        val unpaid = subscriptions
            .filter { it.paidAmount < it.requiredAmount && it.debtAmount > 0 }
            .sortedBy { it.activityDate }

        val updated = mutableListOf<FinanceSubscription>()
        var remaining = amountToPay
        var totalApplied = 0

        for (sub in unpaid) {
            if (remaining <= 0) break
            val currentDebt = (sub.requiredAmount - sub.paidAmount).coerceAtLeast(0)
            if (currentDebt <= 0) continue

            val toPay = minOf(remaining, currentDebt)
            val newPaid = sub.paidAmount + toPay
            val newDebt = (sub.requiredAmount - newPaid).coerceAtLeast(0)

            updated.add(
                sub.copy(
                    paidAmount = newPaid,
                    debtAmount = newDebt,
                    recordedAt = now,
                    updatedAt = now
                )
            )
            remaining -= toPay
            totalApplied += toPay
        }

        return DebtAllocationResult(
            updatedSubscriptions = updated,
            totalApplied = totalApplied,
            unallocatedAmount = remaining
        )
    }

    fun allocateRollback(
        subscriptions: List<FinanceSubscription>,
        amountToRollback: Int,
        excludeActivityId: String? = null,
        now: Long = System.currentTimeMillis()
    ): DebtAllocationResult {
        if (amountToRollback <= 0 || subscriptions.isEmpty()) {
            return DebtAllocationResult(
                updatedSubscriptions = emptyList(),
                totalApplied = 0,
                unallocatedAmount = amountToRollback.coerceAtLeast(0)
            )
        }

        val paidSubs = subscriptions
            .filter {
                it.paidAmount > 0 &&
                    (excludeActivityId.isNullOrBlank() || it.activityId != excludeActivityId)
            }
            .sortedBy { it.activityDate }

        val updated = mutableListOf<FinanceSubscription>()
        var remaining = amountToRollback
        var totalApplied = 0

        for (sub in paidSubs) {
            if (remaining <= 0) break
            val toRollback = minOf(sub.paidAmount, remaining)
            if (toRollback <= 0) continue

            val newPaid = (sub.paidAmount - toRollback).coerceAtLeast(0)
            val newDebt = (sub.requiredAmount - newPaid).coerceAtLeast(0)

            updated.add(
                sub.copy(
                    paidAmount = newPaid,
                    debtAmount = newDebt,
                    recordedAt = if (sub.recordedAt > 0L) sub.recordedAt else now,
                    updatedAt = now
                )
            )
            remaining -= toRollback
            totalApplied += toRollback
        }

        return DebtAllocationResult(
            updatedSubscriptions = updated,
            totalApplied = totalApplied,
            unallocatedAmount = remaining
        )
    }
}
