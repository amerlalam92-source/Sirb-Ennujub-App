package com.example.domain.settings

import kotlinx.coroutines.flow.Flow

interface BrandingRepository {
    fun observeBranding(): Flow<BrandingSettings>
    suspend fun updateLogo(base64: String): Result<Unit>
}
