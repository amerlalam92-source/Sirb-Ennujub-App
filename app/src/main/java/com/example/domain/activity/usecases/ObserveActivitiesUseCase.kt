package com.example.domain.activity.usecases

import com.example.domain.activity.Activity
import com.example.domain.activity.ActivityRepository
import kotlinx.coroutines.flow.Flow

class ObserveActivitiesUseCase(
    private val repository: ActivityRepository
) {
    operator fun invoke(): Flow<List<Activity>> = repository.observeActivities()
}
