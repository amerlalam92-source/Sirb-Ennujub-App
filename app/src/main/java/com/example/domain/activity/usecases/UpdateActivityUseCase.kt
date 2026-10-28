package com.example.domain.activity.usecases

import com.example.domain.activity.Activity
import com.example.domain.activity.ActivityRepository

class UpdateActivityUseCase(
    private val repository: ActivityRepository
) {
    suspend operator fun invoke(activity: Activity): Result<Unit> = repository.updateActivity(activity)
}
