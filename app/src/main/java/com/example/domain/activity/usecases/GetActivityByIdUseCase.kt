package com.example.domain.activity.usecases

import com.example.domain.activity.Activity
import com.example.domain.activity.ActivityRepository

class GetActivityByIdUseCase(
    private val repository: ActivityRepository
) {
    suspend operator fun invoke(id: String): Activity? = repository.getActivityById(id)
}
