package com.example.domain.evaluation

import kotlinx.coroutines.flow.Flow

/**
 * واجهة مستودع التقييم الديناميكي (Evaluation Repository Interface)
 */
interface EvaluationRepository {
    /**
     * الاستماع لجميع معايير التقييم النشطة (غير المؤرشفة) مرتبة تصاعدياً
     */
    fun observeCriteria(): Flow<List<EvaluationCriteria>>

    /**
     * إضافة معيار تقييم جديد
     */
    suspend fun addCriteria(criteria: EvaluationCriteria): Result<Unit>

    /**
     * تعديل معيار تقييم حالي
     */
    suspend fun updateCriteria(criteria: EvaluationCriteria): Result<Unit>

    /**
     * أرشفة معيار تقييم (إخفائه من الاستخدام الفعال)
     */
    suspend fun archiveCriteria(id: String): Result<Unit>

    /**
     * حذف معيار تقييم نهائياً
     */
    suspend fun deleteCriteria(id: String): Result<Unit>

    /**
     * حفظ أو تحديث تقييم شبل
     */
    suspend fun saveEvaluation(evaluation: ScoutEvaluation): Result<Unit>

    /**
     * حذف تقييم محدد لشبل بواسطة المعرف
     */
    suspend fun deleteEvaluation(id: String): Result<Unit>

    /**
     * الحصول على جميع التقييمات لنشاط محدد أو فصل دراسي (من جميع القادة)
     */
    fun getEvaluationsForActivity(activityId: String): Flow<List<ScoutEvaluation>>

    /**
     * الحصول على جميع التقييمات لشبل محدد عبر الأنشطة
     */
    fun getEvaluationsForScout(scoutId: String): Flow<List<ScoutEvaluation>>

    /**
     * الاستماع لجميع التقييمات المسجلة في النظام
     */
    fun observeAllEvaluations(): Flow<List<ScoutEvaluation>>
}
