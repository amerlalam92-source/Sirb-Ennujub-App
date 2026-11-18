package com.example

import com.example.domain.finance.DebtAllocationCalculator
import com.example.domain.finance.ExpenseCategory
import com.example.domain.finance.FinanceSettings
import com.example.domain.finance.FinanceSubscription
import com.example.domain.finance.FinanceSummary
import com.example.domain.finance.FinanceTransaction
import com.example.domain.finance.FinanceTransactionType
import com.example.domain.finance.SubscriptionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceDomainTest {

    @Test
    fun financeSettings_requiredAmounts_matchSpecifications() {
        val settings = FinanceSettings(
            presentAmount = 20,
            lateAmount = 30,
            absentExcusedAmount = 40,
            absentUnexcusedAmount = 50
        )

        assertEquals(20, settings.requiredAmountFor(SubscriptionStatus.PRESENT))
        assertEquals(30, settings.requiredAmountFor(SubscriptionStatus.LATE))
        assertEquals(40, settings.requiredAmountFor(SubscriptionStatus.ABSENT_EXCUSED))
        assertEquals(50, settings.requiredAmountFor(SubscriptionStatus.ABSENT_UNEXCUSED))
    }

    @Test
    fun financeSubscription_debtAmountCalculation() {
        val sub1 = FinanceSubscription(
            activityId = "act1",
            scoutId = "scout1",
            requiredAmount = 50,
            paidAmount = 20
        )
        assertEquals(30, sub1.debtAmount)

        val sub2 = FinanceSubscription(
            activityId = "act1",
            scoutId = "scout2",
            requiredAmount = 20,
            paidAmount = 20
        )
        assertEquals(0, sub2.debtAmount)

        // When overpaid, debt should not be negative
        val sub3 = FinanceSubscription(
            activityId = "act1",
            scoutId = "scout3",
            requiredAmount = 20,
            paidAmount = 30
        )
        assertEquals(0, sub3.debtAmount)
    }

    @Test
    fun financeTransaction_typeIncomeClassification() {
        val expense = FinanceTransaction(
            type = FinanceTransactionType.EXPENSE,
            amount = 1500,
            category = ExpenseCategory.TRANSPORT
        )
        assertFalse(expense.isIncome())

        val donation = FinanceTransaction(
            type = FinanceTransactionType.DONATION,
            amount = 5000,
            source = "فاعل خير"
        )
        assertTrue(donation.isIncome())

        val contribution = FinanceTransaction(
            type = FinanceTransactionType.CONTRIBUTION,
            amount = 2000
        )
        assertTrue(contribution.isIncome())

        val opening = FinanceTransaction(
            type = FinanceTransactionType.OPENING_BALANCE,
            amount = 10000
        )
        assertTrue(opening.isIncome())
    }

    @Test
    fun expenseCategory_fromKeyResolution() {
        assertEquals(ExpenseCategory.TRANSPORT, ExpenseCategory.fromKey("transport"))
        assertEquals(ExpenseCategory.FOOD, ExpenseCategory.fromKey("food"))
        assertEquals(ExpenseCategory.SUPPLIES, ExpenseCategory.fromKey("supplies"))
        assertEquals(ExpenseCategory.ACTIVITIES, ExpenseCategory.fromKey("activities"))
        assertEquals(ExpenseCategory.ADMIN, ExpenseCategory.fromKey("admin"))
        assertEquals(ExpenseCategory.CAMP, ExpenseCategory.fromKey("camp"))
        assertEquals(ExpenseCategory.CHARITY, ExpenseCategory.fromKey("charity"))
        assertEquals(ExpenseCategory.OTHER, ExpenseCategory.fromKey("other"))
        assertEquals(null, ExpenseCategory.fromKey("unknown_category"))
    }

    @Test
    fun subscriptionStatus_fromKeyResolution() {
        assertEquals(SubscriptionStatus.PRESENT, SubscriptionStatus.fromKey("present"))
        assertEquals(SubscriptionStatus.LATE, SubscriptionStatus.fromKey("late"))
        assertEquals(SubscriptionStatus.ABSENT_EXCUSED, SubscriptionStatus.fromKey("absent_excused"))
        assertEquals(SubscriptionStatus.ABSENT_UNEXCUSED, SubscriptionStatus.fromKey("absent_unexcused"))
    }

    @Test
    fun financeContribution_debtAmountAndFullyPaid() {
        val contributionUnpaid = com.example.domain.finance.FinanceContribution(
            activityId = "camp_1",
            scoutId = "scout_1",
            requiredAmount = 1500,
            paidAmount = 500
        )
        assertEquals(1000, contributionUnpaid.debtAmount)
        assertFalse(contributionUnpaid.isFullyPaid())

        val contributionPaid = com.example.domain.finance.FinanceContribution(
            activityId = "camp_1",
            scoutId = "scout_2",
            requiredAmount = 1500,
            paidAmount = 1500
        )
        assertEquals(0, contributionPaid.debtAmount)
        assertTrue(contributionPaid.isFullyPaid())

        // Overpayment should clamp debt to 0
        val contributionOverpaid = com.example.domain.finance.FinanceContribution(
            activityId = "camp_1",
            scoutId = "scout_3",
            requiredAmount = 1500,
            paidAmount = 2000
        )
        assertEquals(0, contributionOverpaid.debtAmount)
        assertTrue(contributionOverpaid.isFullyPaid())
    }

    @Test
    fun collectionActivityItem_fullyCollectedValidation() {
        val pendingItem = com.example.presentation.finance.collections.CollectionActivityItem(
            activityId = "act_1",
            activityTitle = "مخيم ربيعي",
            activityDate = 1700000000L,
            type = "contributions",
            participantsCount = 10,
            totalRequired = 10000,
            totalPaid = 7000,
            totalDebt = 3000,
            isFullyCollected = false
        )
        assertFalse(pendingItem.isFullyCollected)
        assertEquals(3000, pendingItem.totalDebt)

        val completedItem = com.example.presentation.finance.collections.CollectionActivityItem(
            activityId = "act_2",
            activityTitle = "اجتماع تدريبي",
            activityDate = 1700050000L,
            type = "subscriptions",
            participantsCount = 8,
            totalRequired = 160,
            totalPaid = 160,
            totalDebt = 0,
            isFullyCollected = true
        )
        assertTrue(completedItem.isFullyCollected)
        assertEquals(0, completedItem.totalDebt)
    }

    @Test
    fun debtAllocation_partialPayment_allocatesOldestFirst() {
        val subs = listOf(
            FinanceSubscription(
                id = "act2_scout1",
                activityId = "act2",
                scoutId = "scout1",
                activityDate = 2000L,
                requiredAmount = 50,
                paidAmount = 0,
                debtAmount = 50
            ),
            FinanceSubscription(
                id = "act1_scout1",
                activityId = "act1",
                scoutId = "scout1",
                activityDate = 1000L,
                requiredAmount = 40,
                paidAmount = 10,
                debtAmount = 30
            ),
            FinanceSubscription(
                id = "act3_scout1",
                activityId = "act3",
                scoutId = "scout1",
                activityDate = 3000L,
                requiredAmount = 30,
                paidAmount = 0,
                debtAmount = 30
            )
        )

        val result = DebtAllocationCalculator.allocatePayment(
            subscriptions = subs,
            amountToPay = 45,
            now = 9999L
        )

        assertEquals(45, result.totalApplied)
        assertEquals(0, result.unallocatedAmount)
        assertEquals(2, result.updatedSubscriptions.size)

        // Oldest (act1, date 1000): had 30 debt, gets 30 -> paid 40, debt 0
        val first = result.updatedSubscriptions[0]
        assertEquals("act1_scout1", first.id)
        assertEquals(40, first.paidAmount)
        assertEquals(0, first.debtAmount)
        assertEquals(9999L, first.recordedAt)
        assertEquals(9999L, first.updatedAt)

        // Second oldest (act2, date 2000): gets remaining 15 -> paid 15, debt 35
        val second = result.updatedSubscriptions[1]
        assertEquals("act2_scout1", second.id)
        assertEquals(15, second.paidAmount)
        assertEquals(35, second.debtAmount)
    }

    @Test
    fun debtAllocation_exactPayment_clearsAllMultipleSubscriptions() {
        val subs = listOf(
            FinanceSubscription(
                id = "act1_scout1",
                activityId = "act1",
                scoutId = "scout1",
                activityDate = 1000L,
                requiredAmount = 20,
                paidAmount = 0,
                debtAmount = 20
            ),
            FinanceSubscription(
                id = "act2_scout1",
                activityId = "act2",
                scoutId = "scout1",
                activityDate = 2000L,
                requiredAmount = 30,
                paidAmount = 10,
                debtAmount = 20
            ),
            FinanceSubscription(
                id = "act3_scout1",
                activityId = "act3",
                scoutId = "scout1",
                activityDate = 3000L,
                requiredAmount = 50,
                paidAmount = 0,
                debtAmount = 50
            )
        )

        val result = DebtAllocationCalculator.allocatePayment(
            subscriptions = subs,
            amountToPay = 90,
            now = 5000L
        )

        assertEquals(90, result.totalApplied)
        assertEquals(0, result.unallocatedAmount)
        assertEquals(3, result.updatedSubscriptions.size)
        assertTrue(result.updatedSubscriptions.all { it.debtAmount == 0 && it.paidAmount == it.requiredAmount })
    }

    @Test
    fun debtAllocation_overpayment_capsAtRequiredAmountAndReturnsSurplus() {
        val subs = listOf(
            FinanceSubscription(
                id = "act1_scout1",
                activityId = "act1",
                scoutId = "scout1",
                activityDate = 1000L,
                requiredAmount = 20,
                paidAmount = 0,
                debtAmount = 20
            ),
            FinanceSubscription(
                id = "act2_scout1",
                activityId = "act2",
                scoutId = "scout1",
                activityDate = 2000L,
                requiredAmount = 30,
                paidAmount = 5,
                debtAmount = 25
            )
        )

        val result = DebtAllocationCalculator.allocatePayment(
            subscriptions = subs,
            amountToPay = 100,
            now = 7000L
        )

        assertEquals(45, result.totalApplied)
        assertEquals(55, result.unallocatedAmount)
        assertEquals(2, result.updatedSubscriptions.size)
        assertEquals(20, result.updatedSubscriptions[0].paidAmount)
        assertEquals(0, result.updatedSubscriptions[0].debtAmount)
        assertEquals(30, result.updatedSubscriptions[1].paidAmount)
        assertEquals(0, result.updatedSubscriptions[1].debtAmount)
    }

    @Test
    fun debtAllocation_skipsAlreadyPaidSubscriptionsSafely() {
        val subs = listOf(
            FinanceSubscription(
                id = "act1_scout1",
                activityId = "act1",
                scoutId = "scout1",
                activityDate = 1000L,
                requiredAmount = 50,
                paidAmount = 50,
                debtAmount = 0
            ),
            FinanceSubscription(
                id = "act2_scout1",
                activityId = "act2",
                scoutId = "scout1",
                activityDate = 2000L,
                requiredAmount = 40,
                paidAmount = 0,
                debtAmount = 40
            )
        )

        val result = DebtAllocationCalculator.allocatePayment(
            subscriptions = subs,
            amountToPay = 50,
            now = 8000L
        )

        assertEquals(40, result.totalApplied)
        assertEquals(10, result.unallocatedAmount)
        assertEquals(1, result.updatedSubscriptions.size)
        assertEquals("act2_scout1", result.updatedSubscriptions[0].id)
        assertEquals(40, result.updatedSubscriptions[0].paidAmount)
        assertEquals(0, result.updatedSubscriptions[0].debtAmount)
    }

    @Test
    fun debtRollback_acrossMultipleSubscriptions_oldestFirstAndRespectsExcludeActivityId() {
        val subs = listOf(
            FinanceSubscription(
                id = "act2_scout1",
                activityId = "act2",
                scoutId = "scout1",
                activityDate = 2000L,
                requiredAmount = 50,
                paidAmount = 50,
                debtAmount = 0,
                recordedAt = 1234L
            ),
            FinanceSubscription(
                id = "act_excluded_scout1",
                activityId = "act_excluded",
                scoutId = "scout1",
                activityDate = 500L,
                requiredAmount = 40,
                paidAmount = 40,
                debtAmount = 0,
                recordedAt = 1111L
            ),
            FinanceSubscription(
                id = "act1_scout1",
                activityId = "act1",
                scoutId = "scout1",
                activityDate = 1000L,
                requiredAmount = 30,
                paidAmount = 30,
                debtAmount = 0,
                recordedAt = 1200L
            )
        )

        val result = DebtAllocationCalculator.allocateRollback(
            subscriptions = subs,
            amountToRollback = 55,
            excludeActivityId = "act_excluded",
            now = 9000L
        )

        assertEquals(55, result.totalApplied)
        assertEquals(0, result.unallocatedAmount)
        assertEquals(2, result.updatedSubscriptions.size)

        // Oldest non-excluded (act1, date 1000): paid 30 -> rolled back 30 -> paid 0, debt 30
        val first = result.updatedSubscriptions[0]
        assertEquals("act1_scout1", first.id)
        assertEquals(0, first.paidAmount)
        assertEquals(30, first.debtAmount)
        assertEquals(1200L, first.recordedAt)
        assertEquals(9000L, first.updatedAt)

        // Next oldest (act2, date 2000): paid 50 -> rolled back remaining 25 -> paid 25, debt 25
        val second = result.updatedSubscriptions[1]
        assertEquals("act2_scout1", second.id)
        assertEquals(25, second.paidAmount)
        assertEquals(25, second.debtAmount)
        assertEquals(1234L, second.recordedAt)
        assertEquals(9000L, second.updatedAt)
    }
}
