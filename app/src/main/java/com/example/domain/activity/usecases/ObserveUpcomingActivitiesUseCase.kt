package com.example.domain.activity.usecases

import com.example.domain.activity.Activity
import com.example.domain.activity.ActivityRepository
import kotlinx.coroutines.flow.Flow

class ObserveUpcomingActivitiesUseCase(
    private val repository: ActivityRepository
) {
    operator fun invoke(limit: Int = 20): Flow<List<Activity>> = repository.observeUpcomingActivities(limit)
}
