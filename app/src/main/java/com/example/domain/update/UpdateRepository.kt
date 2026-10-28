package com.example.domain.update

import kotlinx.coroutines.flow.Flow

/**
 * مستودع للتحقق من وجود تحديثات جديدة للتطبيق
 */
interface UpdateRepository {
    fun observeUpdateInfo(): Flow<AppUpdateInfo?>
    suspend fun getUpdateInfo(): AppUpdateInfo?
}
