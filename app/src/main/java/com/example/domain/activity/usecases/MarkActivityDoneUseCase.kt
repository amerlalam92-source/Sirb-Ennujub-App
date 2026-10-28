package com.example.domain.activity.usecases

import com.example.domain.activity.ActivityRepository

class MarkActivityDoneUseCase(
    private val repository: ActivityRepository
) {
    suspend operator fun invoke(id: String): Result<Unit> = repository.markAsDone(id)
}
