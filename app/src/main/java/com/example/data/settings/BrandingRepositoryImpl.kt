package com.example.data.settings

import com.example.domain.settings.BrandingRepository
import com.example.domain.settings.BrandingSettings
import com.example.presentation.reports.PdfExporter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

class BrandingRepositoryImpl(
    private val dataSource: BrandingFirestoreDataSource
) : BrandingRepository {

    override fun observeBranding(): Flow<BrandingSettings> {
        return dataSource.observeBranding().onEach { settings ->
            PdfExporter.currentLogoBase64 = settings.logoBase64
        }
    }

    override suspend fun updateLogo(base64: String): Result<Unit> {
        PdfExporter.currentLogoBase64 = base64
        return dataSource.updateLogo(base64)
    }
}
