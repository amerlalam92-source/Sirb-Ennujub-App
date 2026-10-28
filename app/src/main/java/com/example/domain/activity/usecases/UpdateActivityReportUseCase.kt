package com.example.domain.activity.usecases

import com.example.domain.activity.ActivityRepository

class UpdateActivityReportUseCase(
    private val repository: ActivityRepository
) {
    suspend operator fun invoke(id: String, report: String): Result<Unit> =
        repository.updateReport(id, report)
}
