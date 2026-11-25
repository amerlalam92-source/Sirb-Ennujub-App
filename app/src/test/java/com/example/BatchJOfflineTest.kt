package com.example

import com.example.data.activity.ActivityFirestoreDataSource
import com.example.domain.activity.Activity
import com.example.domain.activity.ActivityStatus
import com.example.domain.activity.ActivityType
import com.example.domain.attendance.AttendancePointsSettings
import com.example.domain.attendance.AttendanceRepository
import com.example.domain.evaluation.EvaluationRepository
import com.example.domain.hobby.HobbyBadgeRepository
import com.example.domain.hobby.ScoutHobbyBadge
import com.example.domain.progress.ProgressRepository
import com.example.domain.progress.ProgressRequirement
import com.example.domain.progress.ScoutBadge
import com.example.domain.scout.Scout
import com.example.domain.scout.ScoutRepository
import com.example.domain.settings.SettingsRepository
import com.example.presentation.evaluation.matrix.EvaluationMatrixViewModel
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.any
import org.mockito.Mockito.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class BatchJOfflineTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `defaultScoutingYearStart returns September 1st 00-00-00-000 of current season`() {
        val algiersTz = java.util.TimeZone.getTimeZone("Africa/Algiers")
        val now = Calendar.getInstance(algiersTz)
        val expectedYear = if (now.get(Calendar.MONTH) >= Calendar.SEPTEMBER) {
            now.get(Calendar.YEAR)
        } else {
            now.get(Calendar.YEAR) - 1
        }

        val startMillis = AttendanceRepository.defaultScoutingYearStart()
        val startCal = Calendar.getInstance(algiersTz).apply { timeInMillis = startMillis }

        assertEquals(expectedYear, startCal.get(Calendar.YEAR))
        assertEquals(Calendar.SEPTEMBER, startCal.get(Calendar.MONTH))
        assertEquals(1, startCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, startCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, startCal.get(Calendar.MINUTE))
        assertEquals(0, startCal.get(Calendar.SECOND))
        assertEquals(0, startCal.get(Calendar.MILLISECOND))
    }

    @Test
    fun `updateActivity excludes hasAttendanceRecorded and uses SetOptions merge`() = runTest {
        val mockFirestore = mock(FirebaseFirestore::class.java)
        val mockCollection = mock(CollectionReference::class.java)
        val mockDocRef = mock(DocumentReference::class.java)

        `when`(mockFirestore.collection(ActivityFirestoreDataSource.COLLECTION_ACTIVITIES)).thenReturn(mockCollection)
        `when`(mockCollection.document("act_1")).thenReturn(mockDocRef)
        `when`(mockDocRef.set(any(), eq(SetOptions.merge()))).thenReturn(Tasks.forResult(null))

        val dataSource = ActivityFirestoreDataSource(mockFirestore)
        val activity = Activity(
            id = "act_1",
            title = "اجتماع تدريبي معدل",
            type = ActivityType.TRAINING_MEETING.key,
            status = ActivityStatus.PLANNED.key,
            startDate = 1_700_000_000_000L,
            endDate = 1_700_003_600_000L,
            hasAttendanceRecorded = false
        )

        val result = dataSource.updateActivity(activity)
        assertTrue(result.isSuccess)

        @Suppress("UNCHECKED_CAST")
        val mapCaptor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, Any?>>
        verify(mockDocRef).set(mapCaptor.capture(), eq(SetOptions.merge()))
        assertFalse(mapCaptor.value.containsKey("hasAttendanceRecorded"))
        assertEquals("اجتماع تدريبي معدل", mapCaptor.value["title"])
    }

    @Test
    fun `evaluationMatrixViewModel computes badge bonuses from injected repositories offline`() = runTest {
        val scoutRepo = mock(ScoutRepository::class.java)
        val attendanceRepo = mock(AttendanceRepository::class.java)
        val evaluationRepo = mock(EvaluationRepository::class.java)
        val settingsRepo = mock(SettingsRepository::class.java)
        val progressRepo = mock(ProgressRepository::class.java)
        val hobbyRepo = mock(HobbyBadgeRepository::class.java)

        val scout = Scout(id = "s1", name = "أحمد", surname = "سالم", status = Scout.STATUS_ACTIVE)
        val pointsSettings = AttendancePointsSettings(kafaaBadgePoints = 10, extraHobbyBadgePoints = 5)

        `when`(scoutRepo.observeScouts()).thenReturn(flowOf(listOf(scout)))
        `when`(attendanceRepo.getTrainingMeetingsRegister()).thenReturn(flowOf(emptyList()))
        `when`(settingsRepo.observePointsSettings()).thenReturn(flowOf(pointsSettings))
        `when`(evaluationRepo.observeAllEvaluations()).thenReturn(flowOf(emptyList()))
        `when`(evaluationRepo.observeCriteria()).thenReturn(flowOf(emptyList()))
        `when`(progressRepo.observeAllScoutBadges()).thenReturn(
            flowOf(
                listOf(
                    ScoutBadge(scoutId = "s1", badgeLevel = "acceptance", revokedAt = null),
                    ScoutBadge(scoutId = "s1", badgeLevel = "bronze", revokedAt = 123L)
                )
            )
        )
        `when`(progressRepo.observeAllRequirements()).thenReturn(
            flowOf(listOf(ProgressRequirement(id = "r1", linkedHobbyBadgesCount = 1)))
        )
        `when`(hobbyRepo.observeAllHobbyBadgesGranted()).thenReturn(
            flowOf(
                listOf(
                    ScoutHobbyBadge(scoutId = "s1", badgeId = "h1", revokedAt = null),
                    ScoutHobbyBadge(scoutId = "s1", badgeId = "h2", revokedAt = null),
                    ScoutHobbyBadge(scoutId = "s1", badgeId = "h3", revokedAt = null)
                )
            )
        )

        val viewModel = EvaluationMatrixViewModel(
            scoutRepository = scoutRepo,
            attendanceRepository = attendanceRepo,
            evaluationRepository = evaluationRepo,
            settingsRepository = settingsRepo,
            progressRepository = progressRepo,
            hobbyBadgeRepository = hobbyRepo
        )

        val state = viewModel.uiState.first { !it.isLoading }
        assertEquals(1, state.rows.size)
        val row = state.rows.first()
        assertEquals(10, row.kafaaPoints)
        assertEquals(10, row.hobbyPoints) // (3 active - 1 required) * 5 = 10
        assertEquals(20.0, row.totalScore ?: 0.0, 0.001)
    }
}
