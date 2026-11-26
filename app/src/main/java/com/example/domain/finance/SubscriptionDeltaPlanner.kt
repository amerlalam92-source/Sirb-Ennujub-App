package com.example.domain.finance

/**
 * فرق المبلغ المدفوع لاشتراك محدد لتطبيق تحديث تزايدي آمن دون الكتابة فوق التعديلات المتزامنة
 */
data class SubscriptionPaidDelta(
    val docId: String,
    val paidDelta: Int,
    val updatedAt: Long,
    val recordedAt: Long?,
    val unitId: String?,
    val recordedBy: String?,
    val recordedByName: String?
)

/**
 * مخطط تحويل نتائج توزيع الديون أو التراجع عنها إلى فروقات تزايدية (Deltas) بحتة دون تبعيات Firebase
 */
object SubscriptionDeltaPlanner {

    fun planFromAllocation(
        originalSubscriptions: List<FinanceSubscription>,
        allocation: DebtAllocationResult,
        fallbackUnitId: String? = null,
        fallbackRecordedBy: String? = null,
        fallbackRecordedByName: String? = null
    ): List<SubscriptionPaidDelta> {
        if (allocation.updatedSubscriptions.isEmpty()) return emptyList()
        val originalsById = originalSubscriptions.associateBy { sub ->
            if (sub.id.isNotBlank()) sub.id else "${sub.activityId}_${sub.scoutId}"
        }

        return allocation.updatedSubscriptions.mapNotNull { updated ->
            val docId = if (updated.id.isNotBlank()) updated.id else "${updated.activityId}_${updated.scoutId}"
            val original = originalsById[docId]
            val oldPaid = original?.paidAmount ?: 0
            val delta = updated.paidAmount - oldPaid
            if (delta == 0) return@mapNotNull null

            val resolvedUnitId = if (original?.unitId.isNullOrBlank()) {
                updated.unitId?.takeIf { it.isNotBlank() } ?: fallbackUnitId?.takeIf { it.isNotBlank() }
            } else {
                null
            }
            val resolvedRecordedBy = updated.recordedBy.ifBlank { fallbackRecordedBy.orEmpty() }
                .takeIf { it.isNotBlank() }
            val resolvedRecordedByName = updated.recordedByName.ifBlank { fallbackRecordedByName.orEmpty() }
                .takeIf { it.isNotBlank() }

            SubscriptionPaidDelta(
                docId = docId,
                paidDelta = delta,
                updatedAt = updated.updatedAt,
                recordedAt = updated.recordedAt.takeIf { it > 0L },
                unitId = resolvedUnitId,
                recordedBy = resolvedRecordedBy,
                recordedByName = resolvedRecordedByName
            )
        }
    }
}

/**
 * عملية فردية ضمن دفعة تسديد الديون دون اتصال (بحد أقصى 500 عملية لكل WriteBatch)
 */
sealed interface OfflineDebtBatchOp {
    data class SubscriptionIncrement(val delta: SubscriptionPaidDelta) : OfflineDebtBatchOp
    data class AuditTransactionSet(val transaction: FinanceTransaction) : OfflineDebtBatchOp
    data class ScoutCreditIncrement(val scoutId: String, val delta: Int) : OfflineDebtBatchOp
}

/**
 * تقسيم عمليات تسديد الديون ومعاملة التدقيق وتعديل الرصيد المقدم إلى دفعات تحترم حد الـ 500 عملية،
 * مع ضمان بقاء معاملة التدقيق وتعديل الرصيد المقدم في الدفعة الأولى.
 */
object OfflineDebtPaymentBatchPlanner {
    const val MAX_BATCH_WRITES = 500

    fun planBatches(
        subscriptionDeltas: List<SubscriptionPaidDelta>,
        auditTransaction: FinanceTransaction?,
        scoutId: String,
        creditDelta: Int,
        maxWritesPerBatch: Int = MAX_BATCH_WRITES
    ): List<List<OfflineDebtBatchOp>> {
        val safeLimit = maxWritesPerBatch.coerceAtLeast(1)
        val fixedOps = buildList {
            if (auditTransaction != null) {
                add(OfflineDebtBatchOp.AuditTransactionSet(auditTransaction))
            }
            if (creditDelta != 0 && scoutId.isNotBlank()) {
                add(OfflineDebtBatchOp.ScoutCreditIncrement(scoutId, creditDelta))
            }
        }
        val subOps = subscriptionDeltas.map { OfflineDebtBatchOp.SubscriptionIncrement(it) }
        if (fixedOps.isEmpty() && subOps.isEmpty()) return emptyList()
        if (fixedOps.isEmpty()) return subOps.chunked(safeLimit)
        if (subOps.isEmpty()) return fixedOps.chunked(safeLimit)

        val firstBatchSubCapacity = (safeLimit - fixedOps.size).coerceAtLeast(0)
        val firstBatchSubs = subOps.take(firstBatchSubCapacity)
        val remainingSubs = subOps.drop(firstBatchSubCapacity)

        val batches = mutableListOf<List<OfflineDebtBatchOp>>()
        if (firstBatchSubs.size + fixedOps.size <= safeLimit) {
            batches.add(firstBatchSubs + fixedOps)
        } else {
            batches.addAll(fixedOps.chunked(safeLimit))
        }
        if (remainingSubs.isNotEmpty()) {
            batches.addAll(remainingSubs.chunked(safeLimit))
        }
        return batches
    }
}
