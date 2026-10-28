package com.example.domain.activity.usecases

import com.example.domain.activity.ActivityRepository

class MarkActivityDoneWithReportUseCase(
    private val repository: ActivityRepository
) {
    suspend operator fun invoke(id: String, report: String): Result<Unit> =
        repository.markAsDoneWithReport(id, report)
}
