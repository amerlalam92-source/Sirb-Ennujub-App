package com.example

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
}
