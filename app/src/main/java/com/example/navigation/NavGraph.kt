package com.example.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import kotlinx.coroutines.launch
import androidx.navigation.navArgument
import com.example.di.AppContainer
import com.example.presentation.activity.ActivityDetailScreen
import com.example.presentation.activity.ActivityFormScreen
import com.example.presentation.activity.ActivityListScreen
import com.example.presentation.activity.ActivityViewModel
import com.example.presentation.attendance.AttendanceActivityPickerScreen
import com.example.presentation.attendance.AttendanceEntryScreen
import com.example.presentation.attendance.AttendanceViewModel
import com.example.presentation.attendance.TrainingMeetingsRegisterScreen
import com.example.presentation.attendance.TrainingMeetingsRegisterViewModel
import com.example.presentation.audit.AuditLogScreen
import com.example.presentation.audit.AuditLogViewModel
import com.example.presentation.auth.LoginScreen
import com.example.presentation.auth.LoginViewModel
import com.example.presentation.auth.SignUpScreen
import com.example.presentation.auth.SignUpViewModel
import com.example.presentation.home.HomeScreen
import com.example.presentation.home.HomeViewModel
import com.example.presentation.about.AboutScreen
import com.example.presentation.reports.GeneralReportsScreen
import com.example.presentation.reports.AttendanceReportScreen
import com.example.presentation.reports.ActivitiesReportScreen
import com.example.presentation.reports.ProgressReportScreen
import com.example.presentation.reports.LeadersEvaluationReportScreen
import com.example.presentation.reports.GeneralReportsViewModel
import com.example.presentation.scout.ScoutDetailScreen
import com.example.presentation.scout.ScoutFormScreen
import com.example.presentation.scout.ScoutListScreen
import com.example.presentation.scout.ScoutViewModel
import androidx.compose.runtime.remember
import com.example.presentation.settings.SettingsHubScreen
import com.example.presentation.settings.SettingsViewModel
import com.example.presentation.settings.screens.AccountSettingsScreen
import com.example.presentation.settings.screens.FinanceSettingsScreen
import com.example.presentation.settings.screens.GeneralSettingsScreen
import com.example.presentation.settings.screens.MainLeaderSettingsScreen
import com.example.presentation.splash.SplashScreen

/**
 * معرفات مسارات التنقل في التطبيق (Navigation Routes)
 */
object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val SIGNUP = "signup"
    const val HOME = "home"

    // مسارات الأشبال
    const val SCOUTS_LIST = "scouts_list"
    const val SCOUT_DETAIL = "scout_detail/{scoutId}"
    const val SCOUT_INFO = "scout_info/{scoutId}"
    const val SCOUT_FORM = "scout_form?scoutId={scoutId}"

    fun scoutDetail(scoutId: String): String = "scout_detail/$scoutId"
    fun scoutInfo(scoutId: String): String = "scout_info/$scoutId"
    fun scoutForm(scoutId: String? = null): String =
        if (scoutId.isNullOrBlank()) "scout_form" else "scout_form?scoutId=$scoutId"

    // مسارات الأنشطة الكشفية
    const val ACTIVITIES_LIST = "activities_list"
    const val ACTIVITY_DETAIL = "activity_detail/{activityId}"
    const val ACTIVITY_FORM = "activity_form?activityId={activityId}"

    fun activityDetail(activityId: String): String = "activity_detail/$activityId"
    fun activityForm(activityId: String? = null): String =
        if (activityId.isNullOrBlank()) "activity_form" else "activity_form?activityId=$activityId"

    // مسارات تسجيل الحضور الكشفي
    const val ATTENDANCE_PICKER = "attendance_picker"
    const val ATTENDANCE_ENTRY = "attendance_entry/{activityId}"
    const val TRAINING_MEETINGS_REGISTER = "training_meetings_register"

    fun attendanceEntry(activityId: String): String = "attendance_entry/$activityId"

    // مسار بطاقة التقدم (شارات الكفاءة)
    const val PROGRESS_CATALOG = "progress_catalog"
    const val SCOUT_PROGRESS = "scout_progress/{scoutId}"
    const val SCOUT_BADGE_PROGRESS = "scout_badge_progress/{scoutId}/{badgeKey}"

    fun scoutProgress(scoutId: String): String = "scout_progress/$scoutId"
    fun scoutBadgeProgress(scoutId: String, badgeKey: String): String = "scout_badge_progress/$scoutId/$badgeKey"

    // مسارات شارات الهواية
    const val HOBBY_BADGES_CATALOG = "hobby_badges_catalog"
    const val HOBBY_BADGE_REQUIREMENTS = "hobby_badge_requirements/{badgeId}"
    const val SCOUT_HOBBY_BADGES = "scout_hobby_badges/{scoutId}"
    const val SCOUT_SINGLE_HOBBY_BADGE = "scout_hobby_badge/{scoutId}/{badgeId}"

    fun hobbyBadgeRequirements(badgeId: String): String = "hobby_badge_requirements/$badgeId"
    fun scoutHobbyBadges(scoutId: String): String = "scout_hobby_badges/$scoutId"
    fun scoutSingleHobbyBadge(scoutId: String, badgeId: String): String = "scout_hobby_badge/$scoutId/$badgeId"

    // مسارات الإعدادات والمعلومات والتقارير
    const val SETTINGS = "settings"
    const val SETTINGS_HUB = "settings_hub"
    const val SETTINGS_GENERAL = "settings_general"
    const val SETTINGS_FINANCE = "settings_finance"
    const val SETTINGS_MAIN_LEADER = "settings_main_leader"
    const val SETTINGS_ACCOUNT = "settings_account"
    const val ABOUT = "about"
    const val REPORTS_SCREEN = "reports_screen"
    const val REPORTS_PLACEHOLDER = "reports_screen"
    const val ATTENDANCE_REPORT = "attendance_report"
    const val ACTIVITIES_REPORT = "activities_report"
    const val PROGRESS_REPORT = "progress_report"
    const val LEADERS_EVALUATION_REPORT = "leaders_evaluation_report"
    const val AUDIT_LOG = "audit_log"

    // مسارات متابعة حفظ القرآن الكريم
    const val QURAN_TRACKING = "quran_tracking"
    const val QURAN_SCHOOLS = "quran_schools"

    // مسارات الإدارة المالية
    const val FINANCE_HOME = "finance_home"
    const val FINANCE_COLLECTIONS = "finance_collections"
    const val FINANCE_SUBSCRIPTIONS = "finance_subscriptions"
    const val FINANCE_SUBSCRIPTION_DETAIL = "finance_subscription_detail/{activityId}"
    const val FINANCE_EXPENSES = "finance_expenses"
    const val FINANCE_DONATIONS = "finance_donations"
    const val FINANCE_REPORTS = "finance_reports"
    const val FINANCE_ACTIVITY_REPORTS = "finance_activity_reports"
    const val SCOUT_STATEMENTS_PICKER = "scout_statements_picker"

    // مسارات تقييم الأشبال (Dynamic Evaluation System)
    const val EVALUATION_SCREEN = "evaluation_screen"
    const val EVALUATION_MATRIX = "evaluation_matrix"

    // مسارات إدارة القادة والاعتماد
    const val PENDING_APPROVAL = "pending_approval"
    const val LEADER_MANAGEMENT = "leader_management"

    fun financeSubscriptionDetail(activityId: String): String = "finance_subscription_detail/$activityId"
}

@Composable
fun ScoutNavGraph(
    appContainer: AppContainer,
    loginViewModel: LoginViewModel,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val currentFirebaseUser by appContainer.authRepository.observeAuthState()
        .collectAsStateWithLifecycle(initialValue = appContainer.authRepository.currentUser)

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    LaunchedEffect(currentRoute, currentFirebaseUser?.uid, currentFirebaseUser?.isEmailVerified) {
        val allowedUnverifiedRoutes = setOf(
            Routes.SPLASH,
            Routes.LOGIN,
            Routes.SIGNUP,
            Routes.PENDING_APPROVAL
        )
        val user = currentFirebaseUser
        if (user != null && !user.isEmailVerified && currentRoute != null && currentRoute !in allowedUnverifiedRoutes) {
            navController.navigate(Routes.PENDING_APPROVAL) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
        modifier = modifier.fillMaxSize(),
        enterTransition = { slideInHorizontally(initialOffsetX = { -it }) + fadeIn() },
        exitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() },
        popEnterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
        popExitTransition = { slideOutHorizontally(targetOffsetX = { -it }) + fadeOut() }
    ) {
        // شاشة البداية
        composable(Routes.SPLASH) {
            SplashScreen(
                authRepository = appContainer.authRepository,
                onNavigateToLogin = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
                onNavigateToHome = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
                onNavigateToPendingApproval = {
                    navController.navigate(Routes.PENDING_APPROVAL) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }

        // شاشة تسجيل الدخول
        composable(Routes.LOGIN) {
            LoginScreen(
                viewModel = loginViewModel,
                onLoginSuccess = {
                    navController.navigate(Routes.SPLASH) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToSignUp = {
                    navController.navigate(Routes.SIGNUP)
                }
            )
        }

        // شاشة إنشاء حساب جديد
        composable(Routes.SIGNUP) {
            val signUpViewModel: SignUpViewModel = viewModel(
                factory = SignUpViewModel.Factory(
                    repository = appContainer.authRepository,
                    googleSignInHelper = appContainer.googleSignInHelper
                )
            )

            SignUpScreen(
                viewModel = signUpViewModel,
                onSignUpSuccess = {
                    navController.navigate(Routes.PENDING_APPROVAL) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateBackToLogin = {
                    navController.popBackStack()
                }
            )
        }

        // الشاشة الرئيسية (لوحة التحكم)
        composable(Routes.HOME) {
            val appContext = LocalContext.current.applicationContext
            val homeViewModel: HomeViewModel = viewModel(
                factory = HomeViewModel.Factory(
                    authRepository = appContainer.authRepository,
                    firestore = appContainer.firestore,
                    financeRepository = appContainer.financeRepository,
                    updateRepository = appContainer.updateRepository,
                    context = appContext,
                    evaluationRepository = appContainer.evaluationRepository
                )
            )

            HomeScreen(
                viewModel = homeViewModel,
                authRepository = appContainer.authRepository,
                scoutRepository = appContainer.scoutRepository,
                activityRepository = appContainer.activityRepository,
                themePreferences = appContainer.themePreferences,
                onNavigateToScouts = {
                    navController.navigate(Routes.SCOUTS_LIST)
                },
                onNavigateToActivities = {
                    navController.navigate(Routes.ACTIVITIES_LIST)
                },
                onNavigateToAttendance = {
                    navController.navigate(Routes.ATTENDANCE_PICKER)
                },
                onLogoutSuccess = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS_HUB)
                },
                onNavigateToReports = {
                    navController.navigate(Routes.REPORTS_SCREEN)
                },
                onNavigateToAbout = {
                    navController.navigate(Routes.ABOUT)
                },
                onNavigateToTrainingRegister = {
                    navController.navigate(Routes.TRAINING_MEETINGS_REGISTER)
                },
                onNavigateToProgressCatalog = {
                    navController.navigate(Routes.PROGRESS_CATALOG)
                },
                onNavigateToHobbyBadgesCatalog = {
                    navController.navigate(Routes.HOBBY_BADGES_CATALOG)
                },
                onNavigateToQuranTracking = {
                    navController.navigate(Routes.QURAN_TRACKING)
                },
                onNavigateToFinance = {
                    navController.navigate(Routes.FINANCE_HOME)
                },
                onNavigateToEvaluation = {
                    navController.navigate(Routes.EVALUATION_SCREEN)
                },
                onNavigateToEvaluationMatrix = {
                    navController.navigate(Routes.EVALUATION_MATRIX)
                },
                onNavigateToLeaderManagement = {
                    navController.navigate(Routes.LEADER_MANAGEMENT)
                },
                onNavigateToAuditLog = {
                    navController.navigate(Routes.AUDIT_LOG)
                }
            )
        }

        // شاشة قائمة الأشبال
        composable(Routes.SCOUTS_LIST) {
            val scoutViewModel: ScoutViewModel = viewModel(
                factory = ScoutViewModel.Factory(
                    repository = appContainer.scoutRepository,
                    addScoutUseCase = appContainer.addScoutUseCase,
                    updateScoutUseCase = appContainer.updateScoutUseCase,
                    archiveScoutUseCase = appContainer.archiveScoutUseCase,
                    deleteScoutUseCase = appContainer.deleteScoutUseCase,
                    progressRepository = appContainer.progressRepository
                )
            )

            ScoutListScreen(
                viewModel = scoutViewModel,
                onNavigateToDetail = { scoutId ->
                    navController.navigate(Routes.scoutDetail(scoutId))
                },
                onNavigateToAdd = {
                    navController.navigate(Routes.scoutForm())
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // شاشة تفاصيل الشبل
        composable(
            route = Routes.SCOUT_DETAIL,
            arguments = listOf(
                navArgument("scoutId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val scoutId = backStackEntry.arguments?.getString("scoutId") ?: ""
            val scoutViewModel: ScoutViewModel = viewModel(
                factory = ScoutViewModel.Factory(
                    repository = appContainer.scoutRepository,
                    addScoutUseCase = appContainer.addScoutUseCase,
                    updateScoutUseCase = appContainer.updateScoutUseCase,
                    archiveScoutUseCase = appContainer.archiveScoutUseCase,
                    deleteScoutUseCase = appContainer.deleteScoutUseCase,
                    computeScoutStatisticsUseCase = appContainer.computeScoutStatisticsUseCase,
                    activityRepository = appContainer.activityRepository,
                    observeBadgeProgressForScoutUseCase = appContainer.observeBadgeProgressForScoutUseCase,
                    setRequirementCompletedUseCase = appContainer.setRequirementCompletedUseCase,
                    grantBadgeUseCase = appContainer.grantBadgeUseCase,
                    revokeBadgeUseCase = appContainer.revokeBadgeUseCase,
                    authRepository = appContainer.authRepository,
                    observeHobbyBadgeProgressForScoutUseCase = appContainer.observeHobbyBadgeProgressForScoutUseCase,
                    financeRepository = appContainer.financeRepository,
                    progressRepository = appContainer.progressRepository
                )
            )

            ScoutDetailScreen(
                scoutId = scoutId,
                viewModel = scoutViewModel,
                onNavigateToEdit = { id ->
                    navController.navigate(Routes.scoutForm(id))
                },
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToProgress = { id ->
                    navController.navigate(Routes.scoutProgress(id))
                },
                onNavigateToHobbyBadges = { id ->
                    navController.navigate(Routes.scoutHobbyBadges(id))
                },
                onNavigateToInfo = { id ->
                    navController.navigate(Routes.scoutInfo(id))
                }
            )
        }

        // شاشة معلومات الشبل التفصيلية
        composable(
            route = Routes.SCOUT_INFO,
            arguments = listOf(
                navArgument("scoutId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val scoutId = backStackEntry.arguments?.getString("scoutId") ?: ""
            val scoutInfoViewModel: com.example.presentation.scout.ScoutInfoViewModel = viewModel(
                factory = com.example.presentation.scout.ScoutInfoViewModel.Factory(
                    scoutId = scoutId,
                    repository = appContainer.scoutRepository,
                    leaderNameResolver = appContainer.leaderNameResolver
                )
            )

            com.example.presentation.scout.ScoutInfoScreen(
                viewModel = scoutInfoViewModel,
                onNavigateToEdit = { id ->
                    navController.navigate(Routes.scoutForm(id))
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // شاشة استمارة الشبل
        composable(
            route = Routes.SCOUT_FORM,
            arguments = listOf(
                navArgument("scoutId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val scoutId = backStackEntry.arguments?.getString("scoutId")
            val scoutViewModel: ScoutViewModel = viewModel(
                factory = ScoutViewModel.Factory(
                    repository = appContainer.scoutRepository,
                    addScoutUseCase = appContainer.addScoutUseCase,
                    updateScoutUseCase = appContainer.updateScoutUseCase,
                    archiveScoutUseCase = appContainer.archiveScoutUseCase,
                    deleteScoutUseCase = appContainer.deleteScoutUseCase
                )
            )

            ScoutFormScreen(
                scoutId = scoutId,
                viewModel = scoutViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // شاشة قائمة الأنشطة الكشفية
        composable(Routes.ACTIVITIES_LIST) {
            val activityViewModel: ActivityViewModel = viewModel(
                factory = ActivityViewModel.Factory(
                    repository = appContainer.activityRepository,
                    observeActivitiesUseCase = appContainer.observeActivitiesUseCase,
                    getActivityByIdUseCase = appContainer.getActivityByIdUseCase,
                    addActivityUseCase = appContainer.addActivityUseCase,
                    updateActivityUseCase = appContainer.updateActivityUseCase,
                    markActivityDoneUseCase = appContainer.markActivityDoneUseCase,
                    markActivityDoneWithReportUseCase = appContainer.markActivityDoneWithReportUseCase,
                    updateActivityReportUseCase = appContainer.updateActivityReportUseCase,
                    cancelActivityUseCase = appContainer.cancelActivityUseCase,
                    deleteActivityUseCase = appContainer.deleteActivityUseCase,
                    attendanceRepository = appContainer.attendanceRepository,
                    getAttendanceForActivityUseCase = appContainer.getAttendanceForActivityUseCase,
                    getAttendanceRowsForActivityUseCase = appContainer.getAttendanceRowsForActivityUseCase,
                    termsRepository = appContainer.termsRepository
                )
            )

            ActivityListScreen(
                viewModel = activityViewModel,
                onNavigateToDetail = { activityId ->
                    navController.navigate(Routes.activityDetail(activityId))
                },
                onNavigateToAdd = {
                    navController.navigate(Routes.activityForm())
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // شاشة تفاصيل النشاط الكشفي
        composable(
            route = Routes.ACTIVITY_DETAIL,
            arguments = listOf(
                navArgument("activityId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val activityId = backStackEntry.arguments?.getString("activityId") ?: ""
            val activityViewModel: ActivityViewModel = viewModel(
                factory = ActivityViewModel.Factory(
                    repository = appContainer.activityRepository,
                    observeActivitiesUseCase = appContainer.observeActivitiesUseCase,
                    getActivityByIdUseCase = appContainer.getActivityByIdUseCase,
                    addActivityUseCase = appContainer.addActivityUseCase,
                    updateActivityUseCase = appContainer.updateActivityUseCase,
                    markActivityDoneUseCase = appContainer.markActivityDoneUseCase,
                    markActivityDoneWithReportUseCase = appContainer.markActivityDoneWithReportUseCase,
                    updateActivityReportUseCase = appContainer.updateActivityReportUseCase,
                    cancelActivityUseCase = appContainer.cancelActivityUseCase,
                    deleteActivityUseCase = appContainer.deleteActivityUseCase,
                    attendanceRepository = appContainer.attendanceRepository,
                    getAttendanceForActivityUseCase = appContainer.getAttendanceForActivityUseCase,
                    getAttendanceRowsForActivityUseCase = appContainer.getAttendanceRowsForActivityUseCase,
                    termsRepository = appContainer.termsRepository
                )
            )

            ActivityDetailScreen(
                activityId = activityId,
                viewModel = activityViewModel,
                onNavigateToEdit = { id ->
                    navController.navigate(Routes.activityForm(id))
                },
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToAttendance = { actId ->
                    navController.navigate(Routes.attendanceEntry(actId))
                }
            )
        }

        // شاشة استمارة النشاط الكشفي
        composable(
            route = Routes.ACTIVITY_FORM,
            arguments = listOf(
                navArgument("activityId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val activityId = backStackEntry.arguments?.getString("activityId")
            val activityViewModel: ActivityViewModel = viewModel(
                factory = ActivityViewModel.Factory(
                    repository = appContainer.activityRepository,
                    observeActivitiesUseCase = appContainer.observeActivitiesUseCase,
                    getActivityByIdUseCase = appContainer.getActivityByIdUseCase,
                    addActivityUseCase = appContainer.addActivityUseCase,
                    updateActivityUseCase = appContainer.updateActivityUseCase,
                    markActivityDoneUseCase = appContainer.markActivityDoneUseCase,
                    markActivityDoneWithReportUseCase = appContainer.markActivityDoneWithReportUseCase,
                    updateActivityReportUseCase = appContainer.updateActivityReportUseCase,
                    cancelActivityUseCase = appContainer.cancelActivityUseCase,
                    deleteActivityUseCase = appContainer.deleteActivityUseCase,
                    attendanceRepository = appContainer.attendanceRepository,
                    getAttendanceForActivityUseCase = appContainer.getAttendanceForActivityUseCase,
                    termsRepository = appContainer.termsRepository
                )
            )

            ActivityFormScreen(
                activityId = activityId,
                viewModel = activityViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // شاشة اختيار النشاط لتسجيل الحضور
        composable(Routes.ATTENDANCE_PICKER) {
            val attendanceViewModel: AttendanceViewModel = viewModel(
                factory = AttendanceViewModel.Factory(
                    activityRepository = appContainer.activityRepository,
                    scoutRepository = appContainer.scoutRepository,
                    attendanceRepository = appContainer.attendanceRepository,
                    getAttendanceForActivityUseCase = appContainer.getAttendanceForActivityUseCase,
                    saveAttendanceBatchUseCase = appContainer.saveAttendanceBatchUseCase,
                    observePointsSettingsUseCase = appContainer.observePointsSettingsUseCase,
                    financeRepository = appContainer.financeRepository,
                    settingsRepository = appContainer.settingsRepository,
                    writeFailureReporter = appContainer.writeFailureReporter
                )
            )

            AttendanceActivityPickerScreen(
                viewModel = attendanceViewModel,
                onNavigateToEntry = { actId ->
                    navController.navigate(Routes.attendanceEntry(actId))
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // شاشة معالج تسجيل الحضور
        composable(
            route = Routes.ATTENDANCE_ENTRY,
            arguments = listOf(
                navArgument("activityId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val activityId = backStackEntry.arguments?.getString("activityId") ?: ""
            val attendanceViewModel: AttendanceViewModel = viewModel(
                factory = AttendanceViewModel.Factory(
                    activityRepository = appContainer.activityRepository,
                    scoutRepository = appContainer.scoutRepository,
                    attendanceRepository = appContainer.attendanceRepository,
                    getAttendanceForActivityUseCase = appContainer.getAttendanceForActivityUseCase,
                    saveAttendanceBatchUseCase = appContainer.saveAttendanceBatchUseCase,
                    observePointsSettingsUseCase = appContainer.observePointsSettingsUseCase,
                    financeRepository = appContainer.financeRepository,
                    settingsRepository = appContainer.settingsRepository,
                    writeFailureReporter = appContainer.writeFailureReporter
                )
            )

            AttendanceEntryScreen(
                activityId = activityId,
                viewModel = attendanceViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // مسار الإعدادات (توجيه لمركز الإعدادات Settings Hub)
        composable(Routes.SETTINGS) { backStackEntry ->
            val settingsViewModel: SettingsViewModel = viewModel(
                viewModelStoreOwner = backStackEntry,
                factory = SettingsViewModel.Factory(
                    observePointsSettingsUseCase = appContainer.observePointsSettingsUseCase,
                    updatePointsSettingsUseCase = appContainer.updatePointsSettingsUseCase,
                    financeRepository = appContainer.financeRepository,
                    observeCriteriaUseCase = appContainer.observeCriteriaUseCase,
                    addCriteriaUseCase = appContainer.addCriteriaUseCase,
                    archiveCriteriaUseCase = appContainer.archiveCriteriaUseCase,
                    deleteCriteriaUseCase = appContainer.deleteCriteriaUseCase,
                    authRepository = appContainer.authRepository,
                    brandingRepository = appContainer.brandingRepository,
                    termsRepository = appContainer.termsRepository,
                    activityRepository = appContainer.activityRepository
                )
            )

            SettingsHubScreen(
                viewModel = settingsViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToGeneralSettings = {
                    navController.navigate(Routes.SETTINGS_GENERAL)
                },
                onNavigateToFinanceSettings = {
                    navController.navigate(Routes.SETTINGS_FINANCE)
                },
                onNavigateToMainLeaderSettings = {
                    navController.navigate(Routes.SETTINGS_MAIN_LEADER)
                },
                onNavigateToAccountSettings = {
                    navController.navigate(Routes.SETTINGS_ACCOUNT)
                }
            )
        }

        // شاشة مركز الإعدادات الرئيسية (Settings Hub)
        composable(Routes.SETTINGS_HUB) { backStackEntry ->
            val settingsViewModel: SettingsViewModel = viewModel(
                viewModelStoreOwner = backStackEntry,
                factory = SettingsViewModel.Factory(
                    observePointsSettingsUseCase = appContainer.observePointsSettingsUseCase,
                    updatePointsSettingsUseCase = appContainer.updatePointsSettingsUseCase,
                    financeRepository = appContainer.financeRepository,
                    observeCriteriaUseCase = appContainer.observeCriteriaUseCase,
                    addCriteriaUseCase = appContainer.addCriteriaUseCase,
                    archiveCriteriaUseCase = appContainer.archiveCriteriaUseCase,
                    deleteCriteriaUseCase = appContainer.deleteCriteriaUseCase,
                    authRepository = appContainer.authRepository,
                    brandingRepository = appContainer.brandingRepository,
                    termsRepository = appContainer.termsRepository,
                    activityRepository = appContainer.activityRepository
                )
            )

            SettingsHubScreen(
                viewModel = settingsViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToGeneralSettings = {
                    navController.navigate(Routes.SETTINGS_GENERAL)
                },
                onNavigateToFinanceSettings = {
                    navController.navigate(Routes.SETTINGS_FINANCE)
                },
                onNavigateToMainLeaderSettings = {
                    navController.navigate(Routes.SETTINGS_MAIN_LEADER)
                },
                onNavigateToAccountSettings = {
                    navController.navigate(Routes.SETTINGS_ACCOUNT)
                }
            )
        }

        // الشاشات الفرعية للإعدادات - تشارك نفس كائن SettingsViewModel مع SettingsHubScreen
        composable(Routes.SETTINGS_GENERAL) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(Routes.SETTINGS_HUB)
            }
            val settingsViewModel: SettingsViewModel = viewModel(
                viewModelStoreOwner = parentEntry,
                factory = SettingsViewModel.Factory(
                    observePointsSettingsUseCase = appContainer.observePointsSettingsUseCase,
                    updatePointsSettingsUseCase = appContainer.updatePointsSettingsUseCase,
                    financeRepository = appContainer.financeRepository,
                    observeCriteriaUseCase = appContainer.observeCriteriaUseCase,
                    addCriteriaUseCase = appContainer.addCriteriaUseCase,
                    archiveCriteriaUseCase = appContainer.archiveCriteriaUseCase,
                    deleteCriteriaUseCase = appContainer.deleteCriteriaUseCase,
                    authRepository = appContainer.authRepository,
                    brandingRepository = appContainer.brandingRepository,
                    termsRepository = appContainer.termsRepository,
                    activityRepository = appContainer.activityRepository
                )
            )
            GeneralSettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.SETTINGS_FINANCE) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(Routes.SETTINGS_HUB)
            }
            val settingsViewModel: SettingsViewModel = viewModel(
                viewModelStoreOwner = parentEntry,
                factory = SettingsViewModel.Factory(
                    observePointsSettingsUseCase = appContainer.observePointsSettingsUseCase,
                    updatePointsSettingsUseCase = appContainer.updatePointsSettingsUseCase,
                    financeRepository = appContainer.financeRepository,
                    observeCriteriaUseCase = appContainer.observeCriteriaUseCase,
                    addCriteriaUseCase = appContainer.addCriteriaUseCase,
                    archiveCriteriaUseCase = appContainer.archiveCriteriaUseCase,
                    deleteCriteriaUseCase = appContainer.deleteCriteriaUseCase,
                    authRepository = appContainer.authRepository,
                    brandingRepository = appContainer.brandingRepository,
                    termsRepository = appContainer.termsRepository,
                    activityRepository = appContainer.activityRepository
                )
            )
            FinanceSettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.SETTINGS_MAIN_LEADER) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(Routes.SETTINGS_HUB)
            }
            val settingsViewModel: SettingsViewModel = viewModel(
                viewModelStoreOwner = parentEntry,
                factory = SettingsViewModel.Factory(
                    observePointsSettingsUseCase = appContainer.observePointsSettingsUseCase,
                    updatePointsSettingsUseCase = appContainer.updatePointsSettingsUseCase,
                    financeRepository = appContainer.financeRepository,
                    observeCriteriaUseCase = appContainer.observeCriteriaUseCase,
                    addCriteriaUseCase = appContainer.addCriteriaUseCase,
                    archiveCriteriaUseCase = appContainer.archiveCriteriaUseCase,
                    deleteCriteriaUseCase = appContainer.deleteCriteriaUseCase,
                    authRepository = appContainer.authRepository,
                    brandingRepository = appContainer.brandingRepository,
                    termsRepository = appContainer.termsRepository,
                    activityRepository = appContainer.activityRepository
                )
            )
            MainLeaderSettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToLeaderManagement = {
                    navController.navigate(Routes.LEADER_MANAGEMENT)
                }
            )
        }

        composable(Routes.SETTINGS_ACCOUNT) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(Routes.SETTINGS_HUB)
            }
            val settingsViewModel: SettingsViewModel = viewModel(
                viewModelStoreOwner = parentEntry,
                factory = SettingsViewModel.Factory(
                    observePointsSettingsUseCase = appContainer.observePointsSettingsUseCase,
                    updatePointsSettingsUseCase = appContainer.updatePointsSettingsUseCase,
                    financeRepository = appContainer.financeRepository,
                    observeCriteriaUseCase = appContainer.observeCriteriaUseCase,
                    addCriteriaUseCase = appContainer.addCriteriaUseCase,
                    archiveCriteriaUseCase = appContainer.archiveCriteriaUseCase,
                    deleteCriteriaUseCase = appContainer.deleteCriteriaUseCase,
                    authRepository = appContainer.authRepository,
                    brandingRepository = appContainer.brandingRepository,
                    termsRepository = appContainer.termsRepository,
                    activityRepository = appContainer.activityRepository
                )
            )
            AccountSettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToLogin = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // شاشة حول التطبيق
        composable(Routes.ABOUT) {
            AboutScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // شاشة التقارير العامة
        composable(Routes.REPORTS_SCREEN) {
            GeneralReportsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToAttendanceReport = {
                    navController.navigate(Routes.ATTENDANCE_REPORT)
                },
                onNavigateToActivitiesReport = {
                    navController.navigate(Routes.ACTIVITIES_REPORT)
                },
                onNavigateToProgressReport = {
                    navController.navigate(Routes.PROGRESS_REPORT)
                },
                onNavigateToEvaluationMatrix = {
                    navController.navigate(Routes.EVALUATION_MATRIX)
                },
                onNavigateToLeadersEvaluationReport = {
                    navController.navigate(Routes.LEADERS_EVALUATION_REPORT)
                },
                onNavigateToAuditLog = {
                    navController.navigate(Routes.AUDIT_LOG)
                }
            )
        }

        // شاشة تقرير الحضور الشامل
        composable(Routes.ATTENDANCE_REPORT) {
            val generalReportsViewModel: GeneralReportsViewModel = viewModel(
                factory = appContainer.generalReportsViewModelFactory
            )
            AttendanceReportScreen(
                viewModel = generalReportsViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // شاشة تقرير النشاط العام
        composable(Routes.ACTIVITIES_REPORT) {
            val generalReportsViewModel: GeneralReportsViewModel = viewModel(
                factory = appContainer.generalReportsViewModelFactory
            )
            ActivitiesReportScreen(
                viewModel = generalReportsViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // شاشة تقرير التقدم الشامل (مصفوفة الشارات)
        composable(Routes.PROGRESS_REPORT) {
            val generalReportsViewModel: GeneralReportsViewModel = viewModel(
                factory = appContainer.generalReportsViewModelFactory
            )
            ProgressReportScreen(
                viewModel = generalReportsViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // شاشة تقرير تقييم القادة
        composable(Routes.LEADERS_EVALUATION_REPORT) {
            val generalReportsViewModel: GeneralReportsViewModel = viewModel(
                factory = appContainer.generalReportsViewModelFactory
            )
            LeadersEvaluationReportScreen(
                viewModel = generalReportsViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // شاشة سجل العمليات (سجل التدقيق الشامل)
        composable(Routes.AUDIT_LOG) {
            val auditLogViewModel: AuditLogViewModel = viewModel(
                factory = appContainer.auditLogViewModelFactory
            )
            AuditLogScreen(
                viewModel = auditLogViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // شاشة سجل الاجتماعات التدريبية الكشفية
        composable(Routes.TRAINING_MEETINGS_REGISTER) {
            val registerViewModel: TrainingMeetingsRegisterViewModel = viewModel(
                factory = TrainingMeetingsRegisterViewModel.Factory(
                    appContainer.getTrainingMeetingsRegisterUseCase
                )
            )

            TrainingMeetingsRegisterScreen(
                viewModel = registerViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // شاشة كتالوج بطاقة التقدم (شارات الكفاءة)
        composable(Routes.PROGRESS_CATALOG) {
            val catalogViewModel: com.example.presentation.progress.ProgressCatalogViewModel = viewModel(
                factory = com.example.presentation.progress.ProgressCatalogViewModel.Factory(
                    observeAllRequirementsUseCase = appContainer.observeAllRequirementsUseCase,
                    addRequirementUseCase = appContainer.addRequirementUseCase,
                    updateRequirementUseCase = appContainer.updateRequirementUseCase,
                    archiveRequirementUseCase = appContainer.archiveRequirementUseCase,
                    unarchiveRequirementUseCase = appContainer.unarchiveRequirementUseCase,
                    moveRequirementUseCase = appContainer.moveRequirementUseCase,
                    deleteRequirementUseCase = appContainer.deleteRequirementUseCase,
                    observeAllHobbyBadgesUseCase = appContainer.observeAllHobbyBadgesUseCase
                )
            )

            com.example.presentation.progress.ProgressCatalogScreen(
                viewModel = catalogViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // شاشة تفاصيل بطاقة التقدم لشبل معين
        composable(
            route = Routes.SCOUT_PROGRESS,
            arguments = listOf(
                navArgument("scoutId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val scoutId = backStackEntry.arguments?.getString("scoutId") ?: ""
            val progressDetailViewModel: com.example.presentation.progress.ScoutProgressDetailViewModel = viewModel(
                factory = com.example.presentation.progress.ScoutProgressDetailViewModel.Factory(
                    scoutId = scoutId,
                    scoutRepository = appContainer.scoutRepository,
                    observeBadgeProgressForScoutUseCase = appContainer.observeBadgeProgressForScoutUseCase,
                    setRequirementCompletedUseCase = appContainer.setRequirementCompletedUseCase,
                    grantBadgeUseCase = appContainer.grantBadgeUseCase,
                    revokeBadgeUseCase = appContainer.revokeBadgeUseCase,
                    authRepository = appContainer.authRepository
                )
            )

            com.example.presentation.progress.ScoutProgressDetailScreen(
                viewModel = progressDetailViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToBadge = { scId, badgeKey ->
                    navController.navigate(Routes.scoutBadgeProgress(scId, badgeKey))
                }
            )
        }

        // شاشة تقدم شارة كفاءة مفردة لشبل معين
        composable(
            route = Routes.SCOUT_BADGE_PROGRESS,
            arguments = listOf(
                navArgument("scoutId") { type = NavType.StringType },
                navArgument("badgeKey") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val scoutId = backStackEntry.arguments?.getString("scoutId") ?: ""
            val badgeKey = backStackEntry.arguments?.getString("badgeKey") ?: ""
            val singleBadgeViewModel: com.example.presentation.progress.ScoutSingleBadgeProgressViewModel = viewModel(
                factory = com.example.presentation.progress.ScoutSingleBadgeProgressViewModel.Factory(
                    scoutId = scoutId,
                    badgeKey = badgeKey,
                    scoutRepository = appContainer.scoutRepository,
                    observeBadgeProgressForScoutUseCase = appContainer.observeBadgeProgressForScoutUseCase,
                    setRequirementCompletedUseCase = appContainer.setRequirementCompletedUseCase,
                    grantBadgeUseCase = appContainer.grantBadgeUseCase,
                    revokeBadgeUseCase = appContainer.revokeBadgeUseCase,
                    authRepository = appContainer.authRepository,
                    observeAllHobbyBadgesUseCase = appContainer.observeAllHobbyBadgesUseCase
                )
            )

            com.example.presentation.progress.ScoutSingleBadgeProgressScreen(
                viewModel = singleBadgeViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // شاشة كتالوج شارات الهواية
        composable(Routes.HOBBY_BADGES_CATALOG) {
            val hobbyCatalogViewModel: com.example.presentation.hobby.HobbyBadgesCatalogViewModel = viewModel(
                factory = com.example.presentation.hobby.HobbyBadgesCatalogViewModel.Factory(
                    observeAllHobbyBadgesUseCase = appContainer.observeAllHobbyBadgesUseCase,
                    observeAllHobbyRequirementsUseCase = appContainer.observeAllHobbyRequirementsUseCase,
                    addHobbyBadgeUseCase = appContainer.addHobbyBadgeUseCase,
                    updateHobbyBadgeUseCase = appContainer.updateHobbyBadgeUseCase,
                    archiveHobbyBadgeUseCase = appContainer.archiveHobbyBadgeUseCase,
                    unarchiveHobbyBadgeUseCase = appContainer.unarchiveHobbyBadgeUseCase,
                    moveHobbyBadgeUseCase = appContainer.moveHobbyBadgeUseCase,
                    deleteHobbyBadgeUseCase = appContainer.deleteHobbyBadgeUseCase
                )
            )

            com.example.presentation.hobby.HobbyBadgesCatalogScreen(
                viewModel = hobbyCatalogViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToRequirements = { badgeId ->
                    navController.navigate(Routes.hobbyBadgeRequirements(badgeId))
                }
            )
        }

        // شاشة متطلبات شارة الهواية
        composable(
            route = Routes.HOBBY_BADGE_REQUIREMENTS,
            arguments = listOf(
                navArgument("badgeId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val badgeId = backStackEntry.arguments?.getString("badgeId") ?: ""
            val hobbyReqsViewModel: com.example.presentation.hobby.HobbyBadgeRequirementsViewModel = viewModel(
                factory = com.example.presentation.hobby.HobbyBadgeRequirementsViewModel.Factory(
                    badgeId = badgeId,
                    observeHobbyBadgeByIdUseCase = appContainer.observeHobbyBadgeByIdUseCase,
                    observeRequirementsForBadgeUseCase = appContainer.observeRequirementsForBadgeUseCase,
                    addHobbyRequirementUseCase = appContainer.addHobbyRequirementUseCase,
                    updateHobbyRequirementUseCase = appContainer.updateHobbyRequirementUseCase,
                    archiveHobbyRequirementUseCase = appContainer.archiveHobbyRequirementUseCase,
                    unarchiveHobbyRequirementUseCase = appContainer.unarchiveHobbyRequirementUseCase,
                    moveHobbyRequirementUseCase = appContainer.moveHobbyRequirementUseCase,
                    deleteHobbyRequirementUseCase = appContainer.deleteHobbyRequirementUseCase
                )
            )

            com.example.presentation.hobby.HobbyBadgeRequirementsScreen(
                viewModel = hobbyReqsViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // شاشة شارات الهواية لشبل معين
        composable(
            route = Routes.SCOUT_HOBBY_BADGES,
            arguments = listOf(
                navArgument("scoutId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val scoutId = backStackEntry.arguments?.getString("scoutId") ?: ""
            val scoutHobbyViewModel: com.example.presentation.hobby.ScoutHobbyBadgesViewModel = viewModel(
                factory = com.example.presentation.hobby.ScoutHobbyBadgesViewModel.Factory(
                    scoutId = scoutId,
                    scoutRepository = appContainer.scoutRepository,
                    observeHobbyBadgeProgressForScoutUseCase = appContainer.observeHobbyBadgeProgressForScoutUseCase
                )
            )

            com.example.presentation.hobby.ScoutHobbyBadgesScreen(
                scoutId = scoutId,
                viewModel = scoutHobbyViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToSingleBadge = { scId, badgeId ->
                    navController.navigate(Routes.scoutSingleHobbyBadge(scId, badgeId))
                }
            )
        }

        // شاشة شارة هواية واحدة لشبل معين
        composable(
            route = Routes.SCOUT_SINGLE_HOBBY_BADGE,
            arguments = listOf(
                navArgument("scoutId") { type = NavType.StringType },
                navArgument("badgeId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val scoutId = backStackEntry.arguments?.getString("scoutId") ?: ""
            val badgeId = backStackEntry.arguments?.getString("badgeId") ?: ""
            val singleHobbyBadgeViewModel: com.example.presentation.hobby.ScoutSingleHobbyBadgeViewModel = viewModel(
                factory = com.example.presentation.hobby.ScoutSingleHobbyBadgeViewModel.Factory(
                    scoutId = scoutId,
                    badgeId = badgeId,
                    scoutRepository = appContainer.scoutRepository,
                    observeHobbyBadgeProgressForScoutUseCase = appContainer.observeHobbyBadgeProgressForScoutUseCase,
                    setHobbyRequirementCompletedUseCase = appContainer.setHobbyRequirementCompletedUseCase,
                    grantHobbyBadgeUseCase = appContainer.grantHobbyBadgeUseCase,
                    revokeHobbyBadgeUseCase = appContainer.revokeHobbyBadgeUseCase
                )
            )

            com.example.presentation.hobby.ScoutSingleHobbyBadgeScreen(
                viewModel = singleHobbyBadgeViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // شاشة دليل المدارس القرآنية
        composable(route = Routes.QURAN_SCHOOLS) {
            val quranSchoolsViewModel: com.example.presentation.quran.schools.QuranSchoolsViewModel = viewModel(
                factory = com.example.presentation.quran.schools.QuranSchoolsViewModel.Factory(
                    observeAllSchoolsUseCase = appContainer.observeAllQuranSchoolsUseCase,
                    observeAllEnrollmentsUseCase = appContainer.observeAllEnrollmentsUseCase,
                    addQuranSchoolUseCase = appContainer.addQuranSchoolUseCase,
                    updateQuranSchoolUseCase = appContainer.updateQuranSchoolUseCase,
                    archiveQuranSchoolUseCase = appContainer.archiveQuranSchoolUseCase,
                    unarchiveQuranSchoolUseCase = appContainer.unarchiveQuranSchoolUseCase,
                    deleteQuranSchoolUseCase = appContainer.deleteQuranSchoolUseCase
                )
            )

            com.example.presentation.quran.schools.QuranSchoolsListScreen(
                viewModel = quranSchoolsViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // شاشة متابعة حفظ القرآن الكريم الرئيسية (تتضمن تبويبي المدارس ومتابعة الأشبال)
        composable(route = Routes.QURAN_TRACKING) {
            val quranSchoolsViewModel: com.example.presentation.quran.schools.QuranSchoolsViewModel = viewModel(
                factory = com.example.presentation.quran.schools.QuranSchoolsViewModel.Factory(
                    observeAllSchoolsUseCase = appContainer.observeAllQuranSchoolsUseCase,
                    observeAllEnrollmentsUseCase = appContainer.observeAllEnrollmentsUseCase,
                    addQuranSchoolUseCase = appContainer.addQuranSchoolUseCase,
                    updateQuranSchoolUseCase = appContainer.updateQuranSchoolUseCase,
                    archiveQuranSchoolUseCase = appContainer.archiveQuranSchoolUseCase,
                    unarchiveQuranSchoolUseCase = appContainer.unarchiveQuranSchoolUseCase,
                    deleteQuranSchoolUseCase = appContainer.deleteQuranSchoolUseCase
                )
            )

            val quranScoutsViewModel: com.example.presentation.quran.tracking.QuranScoutsViewModel = viewModel(
                factory = com.example.presentation.quran.tracking.QuranScoutsViewModel.provideFactory(
                    scoutRepository = appContainer.scoutRepository,
                    observeAllEnrollmentsUseCase = appContainer.observeAllEnrollmentsUseCase,
                    observeActiveQuranSchoolsUseCase = appContainer.observeActiveQuranSchoolsUseCase,
                    saveEnrollmentUseCase = appContainer.saveEnrollmentUseCase,
                    deleteEnrollmentUseCase = appContainer.deleteEnrollmentUseCase
                )
            )

            com.example.presentation.quran.QuranTrackingScreen(
                schoolsViewModel = quranSchoolsViewModel,
                scoutsViewModel = quranScoutsViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // =============================================================
        // مسارات الإدارة المالية (Finance Module Routes)
        // =============================================================

        // شاشة الإدارة المالية الرئيسية
        composable(route = Routes.FINANCE_HOME) {
            val financeHomeViewModel: com.example.presentation.finance.FinanceHomeViewModel = viewModel(
                factory = com.example.presentation.finance.FinanceHomeViewModel.Factory(
                    repository = appContainer.financeRepository
                )
            )

            com.example.presentation.finance.FinanceHomeScreen(
                viewModel = financeHomeViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToCollections = {
                    navController.navigate(Routes.FINANCE_COLLECTIONS)
                },
                onNavigateToSubscriptions = {
                    navController.navigate(Routes.FINANCE_SUBSCRIPTIONS)
                },
                onNavigateToExpenses = {
                    navController.navigate(Routes.FINANCE_EXPENSES)
                },
                onNavigateToDonations = {
                    navController.navigate(Routes.FINANCE_DONATIONS)
                },
                onNavigateToReports = {
                    navController.navigate(Routes.FINANCE_REPORTS)
                }
            )
        }

        // شاشة التحصيلات المالية الموحدة (اشتراكات ومساهمات)
        composable(route = Routes.FINANCE_COLLECTIONS) {
            val collectionsViewModel: com.example.presentation.finance.collections.CollectionsViewModel = viewModel(
                factory = com.example.presentation.finance.collections.CollectionsViewModel.Factory(
                    repository = appContainer.financeRepository
                )
            )

            com.example.presentation.finance.collections.CollectionsScreen(
                viewModel = collectionsViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToSubscriptionDetail = { activityId ->
                    navController.navigate(Routes.financeSubscriptionDetail(activityId))
                }
            )
        }

        // شاشة قائمة اشتراكات الأنشطة
        composable(route = Routes.FINANCE_SUBSCRIPTIONS) {
            val subscriptionsListViewModel: com.example.presentation.finance.subscriptions.SubscriptionsListViewModel = viewModel(
                factory = com.example.presentation.finance.subscriptions.SubscriptionsListViewModel.Factory(
                    repository = appContainer.financeRepository
                )
            )

            com.example.presentation.finance.subscriptions.SubscriptionsListScreen(
                viewModel = subscriptionsListViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToDetail = { activityId ->
                    navController.navigate(Routes.financeSubscriptionDetail(activityId))
                }
            )
        }

        // شاشة تفاصيل اشتراكات النشاط والتحصيل
        composable(
            route = Routes.FINANCE_SUBSCRIPTION_DETAIL,
            arguments = listOf(
                navArgument("activityId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val activityId = backStackEntry.arguments?.getString("activityId") ?: ""
            val subscriptionDetailViewModel: com.example.presentation.finance.subscriptions.SubscriptionDetailViewModel = viewModel(
                factory = com.example.presentation.finance.subscriptions.SubscriptionDetailViewModel.Factory(
                    activityId = activityId,
                    repository = appContainer.financeRepository,
                    activityRepository = appContainer.activityRepository
                )
            )

            com.example.presentation.finance.subscriptions.SubscriptionDetailScreen(
                viewModel = subscriptionDetailViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // شاشة المصاريف
        composable(route = Routes.FINANCE_EXPENSES) {
            val expensesViewModel: com.example.presentation.finance.expenses.ExpensesViewModel = viewModel(
                factory = com.example.presentation.finance.expenses.ExpensesViewModel.Factory(
                    repository = appContainer.financeRepository
                )
            )
            com.example.presentation.finance.expenses.ExpensesScreen(
                viewModel = expensesViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // شاشة التبرعات والمساهمات
        composable(route = Routes.FINANCE_DONATIONS) {
            val donationsViewModel: com.example.presentation.finance.donations.DonationsViewModel = viewModel(
                factory = com.example.presentation.finance.donations.DonationsViewModel.Factory(
                    repository = appContainer.financeRepository
                )
            )
            com.example.presentation.finance.donations.DonationsScreen(
                viewModel = donationsViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(route = Routes.FINANCE_REPORTS) { backStackEntry ->
            val financeReportsViewModel: com.example.presentation.finance.reports.FinanceReportsViewModel = viewModel(
                viewModelStoreOwner = backStackEntry,
                factory = com.example.presentation.finance.reports.FinanceReportsViewModel.Factory(
                    financeRepository = appContainer.financeRepository,
                    scoutRepository = appContainer.scoutRepository,
                    termsRepository = appContainer.termsRepository
                )
            )
            com.example.presentation.finance.reports.FinanceReportsScreen(
                viewModel = financeReportsViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToScoutPicker = {
                    navController.navigate(Routes.SCOUT_STATEMENTS_PICKER)
                },
                onNavigateToActivityReports = {
                    navController.navigate(Routes.FINANCE_ACTIVITY_REPORTS)
                }
            )
        }

        composable(route = Routes.FINANCE_ACTIVITY_REPORTS) {
            val activityReportsViewModel: com.example.presentation.finance.reports.ActivityFinancialReportsViewModel = viewModel(
                factory = com.example.presentation.finance.reports.ActivityFinancialReportsViewModel.Factory(
                    financeRepository = appContainer.financeRepository,
                    scoutRepository = appContainer.scoutRepository,
                    activityRepository = appContainer.activityRepository,
                    termsRepository = appContainer.termsRepository
                )
            )
            com.example.presentation.finance.reports.ActivityFinancialReportsScreen(
                viewModel = activityReportsViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(route = Routes.SCOUT_STATEMENTS_PICKER) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(Routes.FINANCE_REPORTS)
            }
            val financeReportsViewModel: com.example.presentation.finance.reports.FinanceReportsViewModel = viewModel(
                viewModelStoreOwner = parentEntry,
                factory = com.example.presentation.finance.reports.FinanceReportsViewModel.Factory(
                    financeRepository = appContainer.financeRepository,
                    scoutRepository = appContainer.scoutRepository,
                    termsRepository = appContainer.termsRepository
                )
            )
            com.example.presentation.finance.reports.ScoutStatementsPickerScreen(
                viewModel = financeReportsViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // =============================================================
        // مسار تقييم الأشبال (Dynamic Evaluation System)
        // =============================================================
        composable(route = Routes.EVALUATION_SCREEN) {
            val evaluationViewModel: com.example.presentation.evaluation.EvaluationViewModel = viewModel(
                factory = appContainer.evaluationViewModelFactory
            )
            com.example.presentation.evaluation.EvaluationScreen(
                viewModel = evaluationViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToMatrix = {
                    navController.navigate(Routes.EVALUATION_MATRIX)
                }
            )
        }

        // =============================================================
        // مسار المصفوفة الشاملة لتقييم الأشبال (Comprehensive Evaluation Matrix)
        // =============================================================
        composable(route = Routes.EVALUATION_MATRIX) {
            val matrixViewModel: com.example.presentation.evaluation.matrix.EvaluationMatrixViewModel = viewModel(
                factory = appContainer.evaluationMatrixViewModelFactory
            )
            com.example.presentation.evaluation.matrix.EvaluationMatrixScreen(
                viewModel = matrixViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // =============================================================
        // مسار شاشة انتظار الاعتماد وتأكيد البريد الإلكتروني
        // =============================================================
        composable(route = Routes.PENDING_APPROVAL) {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            var isEmailVerified by remember { mutableStateOf(appContainer.authRepository.isEmailVerified) }
            val userEmail = appContainer.authRepository.currentUser?.email
            var isLoading by remember { mutableStateOf(false) }
            var statusMessage by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(Unit) {
                if (!isEmailVerified) {
                    appContainer.authRepository.reloadUser()
                    isEmailVerified = appContainer.authRepository.isEmailVerified
                }
                if (isEmailVerified) {
                    val leader = appContainer.authRepository.getCurrentLeader()
                    if (leader?.status == "approved") {
                        navController.navigate(Routes.HOME) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            }

            com.example.presentation.auth.PendingApprovalScreen(
                isEmailVerified = isEmailVerified,
                userEmail = userEmail,
                isLoading = isLoading,
                statusMessage = statusMessage,
                onResendVerification = {
                    scope.launch {
                        isLoading = true
                        statusMessage = null
                        val result = appContainer.authRepository.sendEmailVerification()
                        isLoading = false
                        statusMessage = if (result.isSuccess) {
                            context.getString(R.string.email_verification_sent)
                        } else {
                            context.getString(R.string.email_verification_resend_failed)
                        }
                    }
                },
                onRefreshStatus = {
                    scope.launch {
                        isLoading = true
                        statusMessage = null
                        appContainer.authRepository.reloadUser()
                        isEmailVerified = appContainer.authRepository.isEmailVerified
                        if (isEmailVerified) {
                            val leader = appContainer.authRepository.getCurrentLeader()
                            if (leader?.status == "approved") {
                                isLoading = false
                                navController.navigate(Routes.HOME) {
                                    popUpTo(0) { inclusive = true }
                                }
                                return@launch
                            } else {
                                statusMessage = context.getString(R.string.account_still_pending_approval)
                            }
                        } else {
                            statusMessage = context.getString(R.string.email_verification_not_yet)
                        }
                        isLoading = false
                    }
                },
                onLogout = {
                    appContainer.authRepository.logout()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // =============================================================
        // مسار إدارة قادة السرب
        // =============================================================
        composable(route = Routes.LEADER_MANAGEMENT) {
            val leaderManagementViewModel: com.example.presentation.leaders.LeaderManagementViewModel = viewModel(
                factory = appContainer.leaderManagementViewModelFactory
            )
            com.example.presentation.leaders.LeaderManagementScreen(
                viewModel = leaderManagementViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}



