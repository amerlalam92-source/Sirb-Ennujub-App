package com.example.di

import android.content.Context
import com.example.data.activity.ActivityFirestoreDataSource
import com.example.data.activity.ActivityRepositoryImpl
import com.example.data.auth.AuthRepositoryImpl
import com.example.data.auth.FirebaseAuthDataSource
import com.example.data.log.ActivityLogDataSource
import com.example.data.scout.ScoutFirestoreDataSource
import com.example.data.scout.ScoutRepositoryImpl
import com.example.domain.activity.ActivityRepository
import com.example.domain.activity.usecases.AddActivityUseCase
import com.example.domain.activity.usecases.CancelActivityUseCase
import com.example.domain.activity.usecases.DeleteActivityUseCase
import com.example.domain.activity.usecases.GetActivityByIdUseCase
import com.example.domain.activity.usecases.MarkActivityDoneUseCase
import com.example.domain.activity.usecases.MarkActivityDoneWithReportUseCase
import com.example.domain.activity.usecases.ObserveActivitiesUseCase
import com.example.domain.activity.usecases.ObserveUpcomingActivitiesUseCase
import com.example.domain.activity.usecases.UpdateActivityReportUseCase
import com.example.domain.activity.usecases.UpdateActivityUseCase
import com.example.domain.auth.AuthRepository
import com.example.domain.scout.ScoutRepository
import com.example.domain.scout.usecases.AddScoutUseCase
import com.example.domain.scout.usecases.ArchiveScoutUseCase
import com.example.domain.scout.usecases.DeleteScoutUseCase
import com.example.domain.scout.usecases.UnarchiveScoutUseCase
import com.example.domain.scout.usecases.UpdateScoutUseCase
import com.example.data.attendance.AttendanceFirestoreDataSource
import com.example.data.attendance.AttendanceRepositoryImpl
import com.example.domain.attendance.AttendanceRepository
import com.example.domain.attendance.usecases.ComputeScoutStatisticsUseCase
import com.example.domain.attendance.usecases.GetAttendanceForActivityUseCase
import com.example.domain.attendance.usecases.GetAttendanceForScoutUseCase
import com.example.domain.attendance.usecases.GetAttendanceRowsForActivityUseCase
import com.example.domain.attendance.usecases.GetTrainingMeetingsRegisterUseCase
import com.example.domain.attendance.usecases.ObservePointsSettingsUseCase
import com.example.domain.attendance.usecases.SaveAttendanceBatchUseCase
import com.example.domain.attendance.usecases.UpdatePointsSettingsUseCase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings

/**
 * حاوية حقن التبعيات (Dependency Injection Container)
 * توفر كائنات المستودعات ومصادر البيانات وحالات الاستخدام للأشبال والأنشطة والمصادقة.
 */
interface AppContainer {
    val authRepository: AuthRepository
    val googleSignInHelper: com.example.data.auth.GoogleSignInHelper
    val firestore: FirebaseFirestore

    // مستودع وحالات استخدام الأشبال
    val scoutRepository: ScoutRepository
    val addScoutUseCase: AddScoutUseCase
    val updateScoutUseCase: UpdateScoutUseCase
    val archiveScoutUseCase: ArchiveScoutUseCase
    val unarchiveScoutUseCase: UnarchiveScoutUseCase
    val deleteScoutUseCase: DeleteScoutUseCase
    val leaderNameResolver: com.example.data.leader.LeaderNameResolver

    // مستودع وحالات استخدام الأنشطة
    val activityRepository: ActivityRepository
    val observeActivitiesUseCase: ObserveActivitiesUseCase
    val observeUpcomingActivitiesUseCase: ObserveUpcomingActivitiesUseCase
    val getActivityByIdUseCase: GetActivityByIdUseCase
    val addActivityUseCase: AddActivityUseCase
    val updateActivityUseCase: UpdateActivityUseCase
    val markActivityDoneUseCase: MarkActivityDoneUseCase
    val markActivityDoneWithReportUseCase: MarkActivityDoneWithReportUseCase
    val updateActivityReportUseCase: UpdateActivityReportUseCase
    val cancelActivityUseCase: CancelActivityUseCase
    val deleteActivityUseCase: DeleteActivityUseCase

    // مستودع وحالات استخدام الحضور
    val attendanceRepository: AttendanceRepository
    val getAttendanceForActivityUseCase: GetAttendanceForActivityUseCase
    val getAttendanceForScoutUseCase: GetAttendanceForScoutUseCase
    val saveAttendanceBatchUseCase: SaveAttendanceBatchUseCase
    val observePointsSettingsUseCase: ObservePointsSettingsUseCase
    val updatePointsSettingsUseCase: UpdatePointsSettingsUseCase
    val computeScoutStatisticsUseCase: ComputeScoutStatisticsUseCase
    val getAttendanceRowsForActivityUseCase: GetAttendanceRowsForActivityUseCase
    val getTrainingMeetingsRegisterUseCase: GetTrainingMeetingsRegisterUseCase

    // مستودع وحالات استخدام بطاقة التقدم وشارات الكفاءة
    val progressRepository: com.example.domain.progress.ProgressRepository
    val observeAllRequirementsUseCase: com.example.domain.progress.usecases.ObserveAllRequirementsUseCase
    val observeRequirementsByLevelUseCase: com.example.domain.progress.usecases.ObserveRequirementsByLevelUseCase
    val addRequirementUseCase: com.example.domain.progress.usecases.AddRequirementUseCase
    val updateRequirementUseCase: com.example.domain.progress.usecases.UpdateRequirementUseCase
    val archiveRequirementUseCase: com.example.domain.progress.usecases.ArchiveRequirementUseCase
    val unarchiveRequirementUseCase: com.example.domain.progress.usecases.UnarchiveRequirementUseCase
    val moveRequirementUseCase: com.example.domain.progress.usecases.MoveRequirementUseCase
    val deleteRequirementUseCase: com.example.domain.progress.usecases.DeleteRequirementUseCase
    val observeBadgeProgressForScoutUseCase: com.example.domain.progress.usecases.ObserveBadgeProgressForScoutUseCase
    val setRequirementCompletedUseCase: com.example.domain.progress.usecases.SetRequirementCompletedUseCase
    val grantBadgeUseCase: com.example.domain.progress.usecases.GrantBadgeUseCase
    val revokeBadgeUseCase: com.example.domain.progress.usecases.RevokeBadgeUseCase

    // مستودع وحالات استخدام شارات الهواية
    val hobbyBadgeRepository: com.example.domain.hobby.HobbyBadgeRepository
    val observeAllHobbyBadgesUseCase: com.example.domain.hobby.usecases.ObserveAllHobbyBadgesUseCase
    val observeHobbyBadgeByIdUseCase: com.example.domain.hobby.usecases.ObserveHobbyBadgeByIdUseCase
    val addHobbyBadgeUseCase: com.example.domain.hobby.usecases.AddHobbyBadgeUseCase
    val updateHobbyBadgeUseCase: com.example.domain.hobby.usecases.UpdateHobbyBadgeUseCase
    val archiveHobbyBadgeUseCase: com.example.domain.hobby.usecases.ArchiveHobbyBadgeUseCase
    val unarchiveHobbyBadgeUseCase: com.example.domain.hobby.usecases.UnarchiveHobbyBadgeUseCase
    val moveHobbyBadgeUseCase: com.example.domain.hobby.usecases.MoveHobbyBadgeUseCase
    val deleteHobbyBadgeUseCase: com.example.domain.hobby.usecases.DeleteHobbyBadgeUseCase
    val observeRequirementsForBadgeUseCase: com.example.domain.hobby.usecases.ObserveRequirementsForBadgeUseCase
    val observeAllHobbyRequirementsUseCase: com.example.domain.hobby.usecases.ObserveAllHobbyRequirementsUseCase
    val addHobbyRequirementUseCase: com.example.domain.hobby.usecases.AddHobbyRequirementUseCase
    val updateHobbyRequirementUseCase: com.example.domain.hobby.usecases.UpdateHobbyRequirementUseCase
    val archiveHobbyRequirementUseCase: com.example.domain.hobby.usecases.ArchiveHobbyRequirementUseCase
    val unarchiveHobbyRequirementUseCase: com.example.domain.hobby.usecases.UnarchiveHobbyRequirementUseCase
    val moveHobbyRequirementUseCase: com.example.domain.hobby.usecases.MoveHobbyRequirementUseCase
    val deleteHobbyRequirementUseCase: com.example.domain.hobby.usecases.DeleteHobbyRequirementUseCase
    val observeHobbyProgressForScoutUseCase: com.example.domain.hobby.usecases.ObserveHobbyProgressForScoutUseCase
    val setHobbyRequirementCompletedUseCase: com.example.domain.hobby.usecases.SetHobbyRequirementCompletedUseCase
    val observeHobbyBadgesForScoutUseCase: com.example.domain.hobby.usecases.ObserveHobbyBadgesForScoutUseCase
    val observeAllHobbyBadgesGrantedUseCase: com.example.domain.hobby.usecases.ObserveAllHobbyBadgesGrantedUseCase
    val grantHobbyBadgeUseCase: com.example.domain.hobby.usecases.GrantHobbyBadgeUseCase
    val revokeHobbyBadgeUseCase: com.example.domain.hobby.usecases.RevokeHobbyBadgeUseCase
    val observeHobbyBadgeProgressForScoutUseCase: com.example.domain.hobby.usecases.ObserveHobbyBadgeProgressForScoutUseCase

    // تفضيلات السمة ووضع العرض وتكبير الواجهة
    val themePreferences: com.example.data.preferences.ThemePreferences
    val uiScalePreferences: com.example.data.preferences.UiScalePreferences

    // متابعة حفظ القرآن الكريم والمدارس القرآنية
    val quranSchoolRepository: com.example.domain.quran.QuranSchoolRepository
    val scoutQuranEnrollmentRepository: com.example.domain.quran.ScoutQuranEnrollmentRepository
    val observeAllQuranSchoolsUseCase: com.example.domain.quran.usecases.ObserveAllQuranSchoolsUseCase
    val observeActiveQuranSchoolsUseCase: com.example.domain.quran.usecases.ObserveActiveQuranSchoolsUseCase
    val getQuranSchoolByIdUseCase: com.example.domain.quran.usecases.GetQuranSchoolByIdUseCase
    val addQuranSchoolUseCase: com.example.domain.quran.usecases.AddQuranSchoolUseCase
    val updateQuranSchoolUseCase: com.example.domain.quran.usecases.UpdateQuranSchoolUseCase
    val archiveQuranSchoolUseCase: com.example.domain.quran.usecases.ArchiveQuranSchoolUseCase
    val unarchiveQuranSchoolUseCase: com.example.domain.quran.usecases.UnarchiveQuranSchoolUseCase
    val deleteQuranSchoolUseCase: com.example.domain.quran.usecases.DeleteQuranSchoolUseCase
    val observeEnrollmentForScoutUseCase: com.example.domain.quran.usecases.ObserveEnrollmentForScoutUseCase
    val observeAllEnrollmentsUseCase: com.example.domain.quran.usecases.ObserveAllEnrollmentsUseCase
    val observeEnrollmentsForSchoolUseCase: com.example.domain.quran.usecases.ObserveEnrollmentsForSchoolUseCase
    val getEnrollmentForScoutUseCase: com.example.domain.quran.usecases.GetEnrollmentForScoutUseCase
    val saveEnrollmentUseCase: com.example.domain.quran.usecases.SaveEnrollmentUseCase
    val deleteEnrollmentUseCase: com.example.domain.quran.usecases.DeleteEnrollmentUseCase

    // الإدارة المالية
    val financeRepository: com.example.domain.finance.FinanceRepository
    val observeAllTransactionsUseCase: com.example.domain.finance.usecases.ObserveAllTransactionsUseCase
    val observeTransactionsBetweenUseCase: com.example.domain.finance.usecases.ObserveTransactionsBetweenUseCase
    val observeTransactionsByTypeUseCase: com.example.domain.finance.usecases.ObserveTransactionsByTypeUseCase
    val addTransactionUseCase: com.example.domain.finance.usecases.AddTransactionUseCase
    val updateTransactionUseCase: com.example.domain.finance.usecases.UpdateTransactionUseCase
    val deleteTransactionUseCase: com.example.domain.finance.usecases.DeleteTransactionUseCase
    val observeSubscriptionsForActivityUseCase: com.example.domain.finance.usecases.ObserveSubscriptionsForActivityUseCase
    val observeSubscriptionsForScoutUseCase: com.example.domain.finance.usecases.ObserveSubscriptionsForScoutUseCase
    val observeAllSubscriptionsUseCase: com.example.domain.finance.usecases.ObserveAllSubscriptionsUseCase
    val upsertSubscriptionUseCase: com.example.domain.finance.usecases.UpsertSubscriptionUseCase
    val deleteSubscriptionUseCase: com.example.domain.finance.usecases.DeleteSubscriptionUseCase
    val observeFinanceSettingsUseCase: com.example.domain.finance.usecases.ObserveFinanceSettingsUseCase
    val updateFinanceSettingsUseCase: com.example.domain.finance.usecases.UpdateFinanceSettingsUseCase
    val observeFinanceSummaryUseCase: com.example.domain.finance.usecases.ObserveFinanceSummaryUseCase
    val observeScoutDebtsUseCase: com.example.domain.finance.usecases.ObserveScoutDebtsUseCase
    val observeTotalOutstandingDebtUseCase: com.example.domain.finance.usecases.ObserveTotalOutstandingDebtUseCase

    // التقييم الديناميكي للأشبال
    val evaluationFirestoreDataSource: com.example.data.evaluation.EvaluationFirestoreDataSource
    val evaluationRepository: com.example.domain.evaluation.EvaluationRepository
    val observeCriteriaUseCase: com.example.domain.evaluation.usecases.ObserveCriteriaUseCase
    val addCriteriaUseCase: com.example.domain.evaluation.usecases.AddCriteriaUseCase
    val updateCriteriaUseCase: com.example.domain.evaluation.usecases.UpdateCriteriaUseCase
    val archiveCriteriaUseCase: com.example.domain.evaluation.usecases.ArchiveCriteriaUseCase
    val deleteCriteriaUseCase: com.example.domain.evaluation.usecases.DeleteCriteriaUseCase
    val saveEvaluationUseCase: com.example.domain.evaluation.usecases.SaveEvaluationUseCase
    val getEvaluationsForActivityUseCase: com.example.domain.evaluation.usecases.GetEvaluationsForActivityUseCase
    val getEvaluationsForScoutUseCase: com.example.domain.evaluation.usecases.GetEvaluationsForScoutUseCase
    val evaluationViewModelFactory: androidx.lifecycle.ViewModelProvider.Factory
    val settingsRepository: com.example.domain.settings.SettingsRepository
    val evaluationMatrixViewModelFactory: androidx.lifecycle.ViewModelProvider.Factory
}

class DefaultAppContainer(private val context: Context) : AppContainer {

    override val themePreferences: com.example.data.preferences.ThemePreferences by lazy {
        com.example.data.preferences.ThemePreferences(context)
    }

    override val uiScalePreferences: com.example.data.preferences.UiScalePreferences by lazy {
        com.example.data.preferences.UiScalePreferences(context)
    }

    // مصدر بيانات Firebase Auth
    private val firebaseAuth: FirebaseAuth by lazy {
        FirebaseAuth.getInstance()
    }

    // مصدر بيانات Firestore مع تفعيل الحفظ المحلي دون اتصال (Offline Persistence)
    override val firestore: FirebaseFirestore by lazy {
        val db = FirebaseFirestore.getInstance()
        try {
            val settings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                .build()
            db.firestoreSettings = settings
        } catch (_: Exception) {
            // إعدادات الكاش تم تطبيقها مسبقاً
        }
        db
    }

    private val authDataSource: FirebaseAuthDataSource by lazy {
        FirebaseAuthDataSource(firebaseAuth)
    }

    override val authRepository: AuthRepository by lazy {
        AuthRepositoryImpl(authDataSource, firestore)
    }

    override val googleSignInHelper: com.example.data.auth.GoogleSignInHelper by lazy {
        com.example.data.auth.GoogleSignInHelper(context)
    }

    private val activityLogDataSource: ActivityLogDataSource by lazy {
        ActivityLogDataSource(firestore)
    }

    // الأشبال
    private val scoutFirestoreDataSource: ScoutFirestoreDataSource by lazy {
        ScoutFirestoreDataSource(firestore)
    }

    override val scoutRepository: ScoutRepository by lazy {
        ScoutRepositoryImpl(
            dataSource = scoutFirestoreDataSource,
            activityLogDataSource = activityLogDataSource,
            authRepository = authRepository
        )
    }

    override val addScoutUseCase: AddScoutUseCase by lazy {
        AddScoutUseCase(scoutRepository)
    }

    override val updateScoutUseCase: UpdateScoutUseCase by lazy {
        UpdateScoutUseCase(scoutRepository)
    }

    override val archiveScoutUseCase: ArchiveScoutUseCase by lazy {
        ArchiveScoutUseCase(scoutRepository)
    }

    override val unarchiveScoutUseCase: UnarchiveScoutUseCase by lazy {
        UnarchiveScoutUseCase(scoutRepository)
    }

    override val deleteScoutUseCase: DeleteScoutUseCase by lazy {
        DeleteScoutUseCase(scoutRepository)
    }

    override val leaderNameResolver: com.example.data.leader.LeaderNameResolver by lazy {
        com.example.data.leader.LeaderNameResolver(firestore)
    }

    // الأنشطة الكشفية
    private val activityFirestoreDataSource: ActivityFirestoreDataSource by lazy {
        ActivityFirestoreDataSource(firestore)
    }

    override val activityRepository: ActivityRepository by lazy {
        ActivityRepositoryImpl(
            dataSource = activityFirestoreDataSource,
            activityLogDataSource = activityLogDataSource,
            authRepository = authRepository
        )
    }

    override val observeActivitiesUseCase: ObserveActivitiesUseCase by lazy {
        ObserveActivitiesUseCase(activityRepository)
    }

    override val observeUpcomingActivitiesUseCase: ObserveUpcomingActivitiesUseCase by lazy {
        ObserveUpcomingActivitiesUseCase(activityRepository)
    }

    override val getActivityByIdUseCase: GetActivityByIdUseCase by lazy {
        GetActivityByIdUseCase(activityRepository)
    }

    override val addActivityUseCase: AddActivityUseCase by lazy {
        AddActivityUseCase(activityRepository)
    }

    override val updateActivityUseCase: UpdateActivityUseCase by lazy {
        UpdateActivityUseCase(activityRepository)
    }

    override val markActivityDoneUseCase: MarkActivityDoneUseCase by lazy {
        MarkActivityDoneUseCase(activityRepository)
    }

    override val markActivityDoneWithReportUseCase: MarkActivityDoneWithReportUseCase by lazy {
        MarkActivityDoneWithReportUseCase(activityRepository)
    }

    override val updateActivityReportUseCase: UpdateActivityReportUseCase by lazy {
        UpdateActivityReportUseCase(activityRepository)
    }

    override val cancelActivityUseCase: CancelActivityUseCase by lazy {
        CancelActivityUseCase(activityRepository)
    }

    override val deleteActivityUseCase: DeleteActivityUseCase by lazy {
        DeleteActivityUseCase(activityRepository)
    }

    // تسجيل الحضور
    private val attendanceFirestoreDataSource: AttendanceFirestoreDataSource by lazy {
        AttendanceFirestoreDataSource(firestore)
    }

    override val attendanceRepository: AttendanceRepository by lazy {
        AttendanceRepositoryImpl(
            dataSource = attendanceFirestoreDataSource,
            activityDataSource = activityFirestoreDataSource,
            scoutDataSource = scoutFirestoreDataSource,
            activityLogDataSource = activityLogDataSource,
            authRepository = authRepository,
            financeRepository = financeRepository
        )
    }

    override val getAttendanceForActivityUseCase: GetAttendanceForActivityUseCase by lazy {
        GetAttendanceForActivityUseCase(attendanceRepository)
    }

    override val getAttendanceForScoutUseCase: GetAttendanceForScoutUseCase by lazy {
        GetAttendanceForScoutUseCase(attendanceRepository)
    }

    override val saveAttendanceBatchUseCase: SaveAttendanceBatchUseCase by lazy {
        SaveAttendanceBatchUseCase(attendanceRepository)
    }

    override val observePointsSettingsUseCase: ObservePointsSettingsUseCase by lazy {
        ObservePointsSettingsUseCase(attendanceRepository)
    }

    override val updatePointsSettingsUseCase: UpdatePointsSettingsUseCase by lazy {
        UpdatePointsSettingsUseCase(attendanceRepository)
    }

    override val computeScoutStatisticsUseCase: ComputeScoutStatisticsUseCase by lazy {
        ComputeScoutStatisticsUseCase(attendanceRepository)
    }

    override val getAttendanceRowsForActivityUseCase: GetAttendanceRowsForActivityUseCase by lazy {
        GetAttendanceRowsForActivityUseCase(attendanceRepository)
    }

    override val getTrainingMeetingsRegisterUseCase: GetTrainingMeetingsRegisterUseCase by lazy {
        GetTrainingMeetingsRegisterUseCase(attendanceRepository)
    }

    // بطاقة التقدم وشارات الكفاءة
    private val progressFirestoreDataSource: com.example.data.progress.ProgressFirestoreDataSource by lazy {
        com.example.data.progress.ProgressFirestoreDataSource(firestore)
    }

    override val progressRepository: com.example.domain.progress.ProgressRepository by lazy {
        com.example.data.progress.ProgressRepositoryImpl(
            dataSource = progressFirestoreDataSource,
            logDataSource = activityLogDataSource,
            authRepository = authRepository
        )
    }

    override val observeAllRequirementsUseCase: com.example.domain.progress.usecases.ObserveAllRequirementsUseCase by lazy {
        com.example.domain.progress.usecases.ObserveAllRequirementsUseCase(progressRepository)
    }

    override val observeRequirementsByLevelUseCase: com.example.domain.progress.usecases.ObserveRequirementsByLevelUseCase by lazy {
        com.example.domain.progress.usecases.ObserveRequirementsByLevelUseCase(progressRepository)
    }

    override val addRequirementUseCase: com.example.domain.progress.usecases.AddRequirementUseCase by lazy {
        com.example.domain.progress.usecases.AddRequirementUseCase(progressRepository)
    }

    override val updateRequirementUseCase: com.example.domain.progress.usecases.UpdateRequirementUseCase by lazy {
        com.example.domain.progress.usecases.UpdateRequirementUseCase(progressRepository)
    }

    override val archiveRequirementUseCase: com.example.domain.progress.usecases.ArchiveRequirementUseCase by lazy {
        com.example.domain.progress.usecases.ArchiveRequirementUseCase(progressRepository)
    }

    override val unarchiveRequirementUseCase: com.example.domain.progress.usecases.UnarchiveRequirementUseCase by lazy {
        com.example.domain.progress.usecases.UnarchiveRequirementUseCase(progressRepository)
    }

    override val moveRequirementUseCase: com.example.domain.progress.usecases.MoveRequirementUseCase by lazy {
        com.example.domain.progress.usecases.MoveRequirementUseCase(progressRepository)
    }

    override val deleteRequirementUseCase: com.example.domain.progress.usecases.DeleteRequirementUseCase by lazy {
        com.example.domain.progress.usecases.DeleteRequirementUseCase(progressRepository)
    }

    override val observeBadgeProgressForScoutUseCase: com.example.domain.progress.usecases.ObserveBadgeProgressForScoutUseCase by lazy {
        com.example.domain.progress.usecases.ObserveBadgeProgressForScoutUseCase(progressRepository)
    }

    override val setRequirementCompletedUseCase: com.example.domain.progress.usecases.SetRequirementCompletedUseCase by lazy {
        com.example.domain.progress.usecases.SetRequirementCompletedUseCase(progressRepository)
    }

    override val grantBadgeUseCase: com.example.domain.progress.usecases.GrantBadgeUseCase by lazy {
        com.example.domain.progress.usecases.GrantBadgeUseCase(progressRepository)
    }

    override val revokeBadgeUseCase: com.example.domain.progress.usecases.RevokeBadgeUseCase by lazy {
        com.example.domain.progress.usecases.RevokeBadgeUseCase(progressRepository)
    }

    // شارات الهواية
    private val hobbyBadgeFirestoreDataSource: com.example.data.hobby.HobbyBadgeFirestoreDataSource by lazy {
        com.example.data.hobby.HobbyBadgeFirestoreDataSource(firestore)
    }

    override val hobbyBadgeRepository: com.example.domain.hobby.HobbyBadgeRepository by lazy {
        com.example.data.hobby.HobbyBadgeRepositoryImpl(
            dataSource = hobbyBadgeFirestoreDataSource,
            logDataSource = activityLogDataSource,
            authRepository = authRepository
        )
    }

    override val observeAllHobbyBadgesUseCase: com.example.domain.hobby.usecases.ObserveAllHobbyBadgesUseCase by lazy {
        com.example.domain.hobby.usecases.ObserveAllHobbyBadgesUseCase(hobbyBadgeRepository)
    }

    override val observeHobbyBadgeByIdUseCase: com.example.domain.hobby.usecases.ObserveHobbyBadgeByIdUseCase by lazy {
        com.example.domain.hobby.usecases.ObserveHobbyBadgeByIdUseCase(hobbyBadgeRepository)
    }

    override val addHobbyBadgeUseCase: com.example.domain.hobby.usecases.AddHobbyBadgeUseCase by lazy {
        com.example.domain.hobby.usecases.AddHobbyBadgeUseCase(hobbyBadgeRepository)
    }

    override val updateHobbyBadgeUseCase: com.example.domain.hobby.usecases.UpdateHobbyBadgeUseCase by lazy {
        com.example.domain.hobby.usecases.UpdateHobbyBadgeUseCase(hobbyBadgeRepository)
    }

    override val archiveHobbyBadgeUseCase: com.example.domain.hobby.usecases.ArchiveHobbyBadgeUseCase by lazy {
        com.example.domain.hobby.usecases.ArchiveHobbyBadgeUseCase(hobbyBadgeRepository)
    }

    override val unarchiveHobbyBadgeUseCase: com.example.domain.hobby.usecases.UnarchiveHobbyBadgeUseCase by lazy {
        com.example.domain.hobby.usecases.UnarchiveHobbyBadgeUseCase(hobbyBadgeRepository)
    }

    override val moveHobbyBadgeUseCase: com.example.domain.hobby.usecases.MoveHobbyBadgeUseCase by lazy {
        com.example.domain.hobby.usecases.MoveHobbyBadgeUseCase(hobbyBadgeRepository)
    }

    override val deleteHobbyBadgeUseCase: com.example.domain.hobby.usecases.DeleteHobbyBadgeUseCase by lazy {
        com.example.domain.hobby.usecases.DeleteHobbyBadgeUseCase(hobbyBadgeRepository)
    }

    override val observeRequirementsForBadgeUseCase: com.example.domain.hobby.usecases.ObserveRequirementsForBadgeUseCase by lazy {
        com.example.domain.hobby.usecases.ObserveRequirementsForBadgeUseCase(hobbyBadgeRepository)
    }

    override val observeAllHobbyRequirementsUseCase: com.example.domain.hobby.usecases.ObserveAllHobbyRequirementsUseCase by lazy {
        com.example.domain.hobby.usecases.ObserveAllHobbyRequirementsUseCase(hobbyBadgeRepository)
    }

    override val addHobbyRequirementUseCase: com.example.domain.hobby.usecases.AddHobbyRequirementUseCase by lazy {
        com.example.domain.hobby.usecases.AddHobbyRequirementUseCase(hobbyBadgeRepository)
    }

    override val updateHobbyRequirementUseCase: com.example.domain.hobby.usecases.UpdateHobbyRequirementUseCase by lazy {
        com.example.domain.hobby.usecases.UpdateHobbyRequirementUseCase(hobbyBadgeRepository)
    }

    override val archiveHobbyRequirementUseCase: com.example.domain.hobby.usecases.ArchiveHobbyRequirementUseCase by lazy {
        com.example.domain.hobby.usecases.ArchiveHobbyRequirementUseCase(hobbyBadgeRepository)
    }

    override val unarchiveHobbyRequirementUseCase: com.example.domain.hobby.usecases.UnarchiveHobbyRequirementUseCase by lazy {
        com.example.domain.hobby.usecases.UnarchiveHobbyRequirementUseCase(hobbyBadgeRepository)
    }

    override val moveHobbyRequirementUseCase: com.example.domain.hobby.usecases.MoveHobbyRequirementUseCase by lazy {
        com.example.domain.hobby.usecases.MoveHobbyRequirementUseCase(hobbyBadgeRepository)
    }

    override val deleteHobbyRequirementUseCase: com.example.domain.hobby.usecases.DeleteHobbyRequirementUseCase by lazy {
        com.example.domain.hobby.usecases.DeleteHobbyRequirementUseCase(hobbyBadgeRepository)
    }

    override val observeHobbyProgressForScoutUseCase: com.example.domain.hobby.usecases.ObserveHobbyProgressForScoutUseCase by lazy {
        com.example.domain.hobby.usecases.ObserveHobbyProgressForScoutUseCase(hobbyBadgeRepository)
    }

    override val setHobbyRequirementCompletedUseCase: com.example.domain.hobby.usecases.SetHobbyRequirementCompletedUseCase by lazy {
        com.example.domain.hobby.usecases.SetHobbyRequirementCompletedUseCase(hobbyBadgeRepository)
    }

    override val observeHobbyBadgesForScoutUseCase: com.example.domain.hobby.usecases.ObserveHobbyBadgesForScoutUseCase by lazy {
        com.example.domain.hobby.usecases.ObserveHobbyBadgesForScoutUseCase(hobbyBadgeRepository)
    }

    override val observeAllHobbyBadgesGrantedUseCase: com.example.domain.hobby.usecases.ObserveAllHobbyBadgesGrantedUseCase by lazy {
        com.example.domain.hobby.usecases.ObserveAllHobbyBadgesGrantedUseCase(hobbyBadgeRepository)
    }

    override val grantHobbyBadgeUseCase: com.example.domain.hobby.usecases.GrantHobbyBadgeUseCase by lazy {
        com.example.domain.hobby.usecases.GrantHobbyBadgeUseCase(hobbyBadgeRepository)
    }

    override val revokeHobbyBadgeUseCase: com.example.domain.hobby.usecases.RevokeHobbyBadgeUseCase by lazy {
        com.example.domain.hobby.usecases.RevokeHobbyBadgeUseCase(hobbyBadgeRepository)
    }

    override val observeHobbyBadgeProgressForScoutUseCase: com.example.domain.hobby.usecases.ObserveHobbyBadgeProgressForScoutUseCase by lazy {
        com.example.domain.hobby.usecases.ObserveHobbyBadgeProgressForScoutUseCase(hobbyBadgeRepository)
    }

    // متابعة حفظ القرآن الكريم
    private val quranFirestoreDataSource: com.example.data.quran.QuranFirestoreDataSource by lazy {
        com.example.data.quran.QuranFirestoreDataSource(firestore)
    }

    override val quranSchoolRepository: com.example.domain.quran.QuranSchoolRepository by lazy {
        com.example.data.quran.QuranSchoolRepositoryImpl(
            dataSource = quranFirestoreDataSource,
            activityLogDataSource = activityLogDataSource,
            authRepository = authRepository
        )
    }

    override val scoutQuranEnrollmentRepository: com.example.domain.quran.ScoutQuranEnrollmentRepository by lazy {
        com.example.data.quran.ScoutQuranEnrollmentRepositoryImpl(
            dataSource = quranFirestoreDataSource,
            schoolRepository = quranSchoolRepository,
            activityLogDataSource = activityLogDataSource,
            authRepository = authRepository
        )
    }

    override val observeAllQuranSchoolsUseCase: com.example.domain.quran.usecases.ObserveAllQuranSchoolsUseCase by lazy {
        com.example.domain.quran.usecases.ObserveAllQuranSchoolsUseCase(quranSchoolRepository)
    }

    override val observeActiveQuranSchoolsUseCase: com.example.domain.quran.usecases.ObserveActiveQuranSchoolsUseCase by lazy {
        com.example.domain.quran.usecases.ObserveActiveQuranSchoolsUseCase(quranSchoolRepository)
    }

    override val getQuranSchoolByIdUseCase: com.example.domain.quran.usecases.GetQuranSchoolByIdUseCase by lazy {
        com.example.domain.quran.usecases.GetQuranSchoolByIdUseCase(quranSchoolRepository)
    }

    override val addQuranSchoolUseCase: com.example.domain.quran.usecases.AddQuranSchoolUseCase by lazy {
        com.example.domain.quran.usecases.AddQuranSchoolUseCase(quranSchoolRepository)
    }

    override val updateQuranSchoolUseCase: com.example.domain.quran.usecases.UpdateQuranSchoolUseCase by lazy {
        com.example.domain.quran.usecases.UpdateQuranSchoolUseCase(quranSchoolRepository)
    }

    override val archiveQuranSchoolUseCase: com.example.domain.quran.usecases.ArchiveQuranSchoolUseCase by lazy {
        com.example.domain.quran.usecases.ArchiveQuranSchoolUseCase(quranSchoolRepository)
    }

    override val unarchiveQuranSchoolUseCase: com.example.domain.quran.usecases.UnarchiveQuranSchoolUseCase by lazy {
        com.example.domain.quran.usecases.UnarchiveQuranSchoolUseCase(quranSchoolRepository)
    }

    override val deleteQuranSchoolUseCase: com.example.domain.quran.usecases.DeleteQuranSchoolUseCase by lazy {
        com.example.domain.quran.usecases.DeleteQuranSchoolUseCase(quranSchoolRepository)
    }

    override val observeEnrollmentForScoutUseCase: com.example.domain.quran.usecases.ObserveEnrollmentForScoutUseCase by lazy {
        com.example.domain.quran.usecases.ObserveEnrollmentForScoutUseCase(scoutQuranEnrollmentRepository)
    }

    override val observeAllEnrollmentsUseCase: com.example.domain.quran.usecases.ObserveAllEnrollmentsUseCase by lazy {
        com.example.domain.quran.usecases.ObserveAllEnrollmentsUseCase(scoutQuranEnrollmentRepository)
    }

    override val observeEnrollmentsForSchoolUseCase: com.example.domain.quran.usecases.ObserveEnrollmentsForSchoolUseCase by lazy {
        com.example.domain.quran.usecases.ObserveEnrollmentsForSchoolUseCase(scoutQuranEnrollmentRepository)
    }

    override val getEnrollmentForScoutUseCase: com.example.domain.quran.usecases.GetEnrollmentForScoutUseCase by lazy {
        com.example.domain.quran.usecases.GetEnrollmentForScoutUseCase(scoutQuranEnrollmentRepository)
    }

    override val saveEnrollmentUseCase: com.example.domain.quran.usecases.SaveEnrollmentUseCase by lazy {
        com.example.domain.quran.usecases.SaveEnrollmentUseCase(scoutQuranEnrollmentRepository)
    }

    override val deleteEnrollmentUseCase: com.example.domain.quran.usecases.DeleteEnrollmentUseCase by lazy {
        com.example.domain.quran.usecases.DeleteEnrollmentUseCase(scoutQuranEnrollmentRepository)
    }

    // =============================================================
    // الإدارة المالية (Finance Module)
    // =============================================================

    private val financeFirestoreDataSource: com.example.data.finance.FinanceFirestoreDataSource by lazy {
        com.example.data.finance.FinanceFirestoreDataSource(firestore)
    }

    override val financeRepository: com.example.domain.finance.FinanceRepository by lazy {
        com.example.data.finance.FinanceRepositoryImpl(
            dataSource = financeFirestoreDataSource,
            activityLogDataSource = activityLogDataSource,
            authRepository = authRepository,
            firestore = firestore
        )
    }

    override val observeAllTransactionsUseCase: com.example.domain.finance.usecases.ObserveAllTransactionsUseCase by lazy {
        com.example.domain.finance.usecases.ObserveAllTransactionsUseCase(financeRepository)
    }

    override val observeTransactionsBetweenUseCase: com.example.domain.finance.usecases.ObserveTransactionsBetweenUseCase by lazy {
        com.example.domain.finance.usecases.ObserveTransactionsBetweenUseCase(financeRepository)
    }

    override val observeTransactionsByTypeUseCase: com.example.domain.finance.usecases.ObserveTransactionsByTypeUseCase by lazy {
        com.example.domain.finance.usecases.ObserveTransactionsByTypeUseCase(financeRepository)
    }

    override val addTransactionUseCase: com.example.domain.finance.usecases.AddTransactionUseCase by lazy {
        com.example.domain.finance.usecases.AddTransactionUseCase(financeRepository)
    }

    override val updateTransactionUseCase: com.example.domain.finance.usecases.UpdateTransactionUseCase by lazy {
        com.example.domain.finance.usecases.UpdateTransactionUseCase(financeRepository)
    }

    override val deleteTransactionUseCase: com.example.domain.finance.usecases.DeleteTransactionUseCase by lazy {
        com.example.domain.finance.usecases.DeleteTransactionUseCase(financeRepository)
    }

    override val observeSubscriptionsForActivityUseCase: com.example.domain.finance.usecases.ObserveSubscriptionsForActivityUseCase by lazy {
        com.example.domain.finance.usecases.ObserveSubscriptionsForActivityUseCase(financeRepository)
    }

    override val observeSubscriptionsForScoutUseCase: com.example.domain.finance.usecases.ObserveSubscriptionsForScoutUseCase by lazy {
        com.example.domain.finance.usecases.ObserveSubscriptionsForScoutUseCase(financeRepository)
    }

    override val observeAllSubscriptionsUseCase: com.example.domain.finance.usecases.ObserveAllSubscriptionsUseCase by lazy {
        com.example.domain.finance.usecases.ObserveAllSubscriptionsUseCase(financeRepository)
    }

    override val upsertSubscriptionUseCase: com.example.domain.finance.usecases.UpsertSubscriptionUseCase by lazy {
        com.example.domain.finance.usecases.UpsertSubscriptionUseCase(financeRepository)
    }

    override val deleteSubscriptionUseCase: com.example.domain.finance.usecases.DeleteSubscriptionUseCase by lazy {
        com.example.domain.finance.usecases.DeleteSubscriptionUseCase(financeRepository)
    }

    override val observeFinanceSettingsUseCase: com.example.domain.finance.usecases.ObserveFinanceSettingsUseCase by lazy {
        com.example.domain.finance.usecases.ObserveFinanceSettingsUseCase(financeRepository)
    }

    override val updateFinanceSettingsUseCase: com.example.domain.finance.usecases.UpdateFinanceSettingsUseCase by lazy {
        com.example.domain.finance.usecases.UpdateFinanceSettingsUseCase(financeRepository)
    }

    override val observeFinanceSummaryUseCase: com.example.domain.finance.usecases.ObserveFinanceSummaryUseCase by lazy {
        com.example.domain.finance.usecases.ObserveFinanceSummaryUseCase(financeRepository)
    }

    override val observeScoutDebtsUseCase: com.example.domain.finance.usecases.ObserveScoutDebtsUseCase by lazy {
        com.example.domain.finance.usecases.ObserveScoutDebtsUseCase(financeRepository)
    }

    override val observeTotalOutstandingDebtUseCase: com.example.domain.finance.usecases.ObserveTotalOutstandingDebtUseCase by lazy {
        com.example.domain.finance.usecases.ObserveTotalOutstandingDebtUseCase(financeRepository)
    }

    override val evaluationFirestoreDataSource: com.example.data.evaluation.EvaluationFirestoreDataSource by lazy {
        com.example.data.evaluation.EvaluationFirestoreDataSource(firestore)
    }

    override val evaluationRepository: com.example.domain.evaluation.EvaluationRepository by lazy {
        com.example.data.evaluation.EvaluationRepositoryImpl(evaluationFirestoreDataSource)
    }

    override val observeCriteriaUseCase: com.example.domain.evaluation.usecases.ObserveCriteriaUseCase by lazy {
        com.example.domain.evaluation.usecases.ObserveCriteriaUseCase(evaluationRepository)
    }

    override val addCriteriaUseCase: com.example.domain.evaluation.usecases.AddCriteriaUseCase by lazy {
        com.example.domain.evaluation.usecases.AddCriteriaUseCase(evaluationRepository)
    }

    override val updateCriteriaUseCase: com.example.domain.evaluation.usecases.UpdateCriteriaUseCase by lazy {
        com.example.domain.evaluation.usecases.UpdateCriteriaUseCase(evaluationRepository)
    }

    override val archiveCriteriaUseCase: com.example.domain.evaluation.usecases.ArchiveCriteriaUseCase by lazy {
        com.example.domain.evaluation.usecases.ArchiveCriteriaUseCase(evaluationRepository)
    }

    override val deleteCriteriaUseCase: com.example.domain.evaluation.usecases.DeleteCriteriaUseCase by lazy {
        com.example.domain.evaluation.usecases.DeleteCriteriaUseCase(evaluationRepository)
    }

    override val saveEvaluationUseCase: com.example.domain.evaluation.usecases.SaveEvaluationUseCase by lazy {
        com.example.domain.evaluation.usecases.SaveEvaluationUseCase(evaluationRepository)
    }

    override val getEvaluationsForActivityUseCase: com.example.domain.evaluation.usecases.GetEvaluationsForActivityUseCase by lazy {
        com.example.domain.evaluation.usecases.GetEvaluationsForActivityUseCase(evaluationRepository)
    }

    override val getEvaluationsForScoutUseCase: com.example.domain.evaluation.usecases.GetEvaluationsForScoutUseCase by lazy {
        com.example.domain.evaluation.usecases.GetEvaluationsForScoutUseCase(evaluationRepository)
    }

    override val evaluationViewModelFactory: androidx.lifecycle.ViewModelProvider.Factory by lazy {
        com.example.presentation.evaluation.EvaluationViewModel.Factory(
            activityRepository = activityRepository,
            scoutRepository = scoutRepository,
            evaluationRepository = evaluationRepository,
            authRepository = authRepository,
            scoutQuranEnrollmentRepository = scoutQuranEnrollmentRepository,
            attendanceRepository = attendanceRepository
        )
    }

    override val settingsRepository: com.example.domain.settings.SettingsRepository by lazy {
        com.example.data.settings.SettingsRepositoryImpl(attendanceRepository)
    }

    override val evaluationMatrixViewModelFactory: androidx.lifecycle.ViewModelProvider.Factory by lazy {
        com.example.presentation.evaluation.matrix.EvaluationMatrixViewModel.Factory(
            scoutRepository = scoutRepository,
            attendanceRepository = attendanceRepository,
            evaluationRepository = evaluationRepository,
            settingsRepository = settingsRepository
        )
    }
}

