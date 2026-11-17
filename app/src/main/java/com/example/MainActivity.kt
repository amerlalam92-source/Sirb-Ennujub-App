package com.example

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.settings.LocalSettingsCache
import com.example.domain.preferences.ThemeMode
import com.example.navigation.ScoutNavGraph
import com.example.presentation.auth.LoginViewModel
import com.example.presentation.common.ForceUpdateScreen
import com.example.presentation.common.LocalAppDensity
import com.example.presentation.common.LocalOnZoomClick
import com.example.presentation.common.UiScaleDialog
import com.example.presentation.update.UpdateViewModel
import com.example.ui.theme.ScoutTheme
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * النشاط الرئيسي للتطبيق (MainActivity)
 * يضبط اتجاه RTL العربي ويطبق شاشة Splash التمهيدية والسمة الكشفية ووضع العرض المحفوظ محلياً والتنقل بخط Cairo عالمياً
 * ويدعم تكبير/تصغير واجهة المستخدم العامة (UI Scale) بنطاق 0.8f إلى 1.2f مع الحفظ الدائم،
 * ويفرض تجاهل مقياس خط النظام عبر attachBaseContext وتوفير LocalAppDensity للنوافذ والحوارات.
 */
class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val config = Configuration(newBase.resources.configuration)
        config.fontScale = 1.0f // Force ignore system font size
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // تهيئة واجهة AndroidX SplashScreen
        installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContainer = (application as ScoutApplication).container

        setContent {
            val uiScaleFlow = remember(appContainer) { appContainer.uiScalePreferences.getUiScale() }
            val uiScale by uiScaleFlow
                .collectAsState(initial = appContainer.uiScalePreferences.getCurrentUiScale())
            var showZoomDialog by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            val originalDensity = LocalDensity.current

            // Calculate the zoomed density
            val scaledDensity = Density(
                density = originalDensity.density * uiScale,
                fontScale = 1.0f // Always 1.0f
            )

            val appContext = LocalContext.current.applicationContext
            val currentLeaderFlow = remember(appContainer) { appContainer.authRepository.observeCurrentLeader() }
            val currentLeader by currentLeaderFlow.collectAsState(initial = null)

            val initialBranding = remember(appContext) {
                com.example.domain.settings.BrandingSettings(logoBase64 = LocalSettingsCache.getLogo(appContext))
            }
            val brandingFlow = remember(appContainer, currentLeader?.uid) {
                appContainer.brandingRepository.observeBranding().map { settings ->
                    val cachedLogo = LocalSettingsCache.getLogo(appContext)
                    if (currentLeader == null && settings.logoBase64 == null && cachedLogo != null) {
                        com.example.domain.settings.BrandingSettings(logoBase64 = cachedLogo)
                    } else {
                        settings
                    }
                }
            }
            val brandingSettings by brandingFlow
                .collectAsState(initial = initialBranding)

            androidx.compose.runtime.LaunchedEffect(brandingSettings.logoBase64, currentLeader?.uid) {
                com.example.presentation.reports.PdfExporter.currentLogoBase64 = brandingSettings.logoBase64
                if (currentLeader != null || brandingSettings.logoBase64 != null) {
                    LocalSettingsCache.saveLogo(appContext, brandingSettings.logoBase64)
                }
            }

            val initialTerms = remember(appContext) {
                LocalSettingsCache.getTerms(appContext) ?: com.example.domain.settings.CustomTerms()
            }
            val termsFlow = remember(appContainer, currentLeader?.uid) {
                appContainer.termsRepository.observeTerms().map { terms ->
                    val cachedTerms = LocalSettingsCache.getTerms(appContext)
                    if (currentLeader == null && terms == com.example.domain.settings.CustomTerms() && cachedTerms != null) {
                        cachedTerms
                    } else {
                        terms
                    }
                }
            }
            val customTerms by termsFlow
                .collectAsState(initial = initialTerms)

            androidx.compose.runtime.LaunchedEffect(customTerms, currentLeader?.uid) {
                com.example.presentation.reports.PdfExporter.currentTerms = customTerms
                if (currentLeader != null || customTerms != com.example.domain.settings.CustomTerms()) {
                    LocalSettingsCache.saveTerms(appContext, customTerms)
                }
            }

            CompositionLocalProvider(
                LocalAppDensity provides scaledDensity,
                LocalDensity provides scaledDensity,
                LocalOnZoomClick provides { showZoomDialog = true },
                com.example.presentation.common.LocalAppLogo provides brandingSettings.logoBase64,
                com.example.presentation.common.LocalAppTerms provides customTerms
            ) {
                val themeModeFlow = remember(appContainer) { appContainer.themePreferences.getThemeMode() }
                val themeMode by themeModeFlow
                    .collectAsState(initial = appContainer.themePreferences.getCurrentThemeMode())

                ScoutTheme(themeMode = themeMode) {
                    if (showZoomDialog) {
                        UiScaleDialog(
                            currentScale = uiScale,
                            onScaleChanged = { scale ->
                                scope.launch { appContainer.uiScalePreferences.setUiScale(scale) }
                            },
                            onDismiss = { showZoomDialog = false }
                        )
                    }

                    val loginViewModel: LoginViewModel = viewModel(
                        factory = LoginViewModel.Factory(
                            repository = appContainer.authRepository,
                            googleSignInHelper = appContainer.googleSignInHelper,
                            brandingRepository = appContainer.brandingRepository,
                            context = appContext
                        )
                    )

                    val updateViewModel: UpdateViewModel = viewModel(
                        factory = UpdateViewModel.Factory(
                            updateRepository = appContainer.updateRepository,
                            authRepository = appContainer.authRepository
                        )
                    )
                    val isForceUpdateRequired by updateViewModel.isForceUpdateRequired.collectAsStateWithLifecycle()
                    val appUpdateInfo by updateViewModel.appUpdateInfo.collectAsStateWithLifecycle()

                    Box(modifier = Modifier.fillMaxSize()) {
                        ScoutNavGraph(
                            appContainer = appContainer,
                            loginViewModel = loginViewModel,
                            modifier = Modifier.fillMaxSize()
                        )

                        if (isForceUpdateRequired) {
                            ForceUpdateScreen(
                                apkUrl = appUpdateInfo?.apkUrl.orEmpty(),
                                latestVersionName = appUpdateInfo?.latestVersionName.orEmpty(),
                                onSkip = { updateViewModel.skipForceUpdate() }
                            )
                        }
                    }
                }
            }
        }
    }
}
