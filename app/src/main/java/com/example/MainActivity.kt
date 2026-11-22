package com.example

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.common.DataLoadError
import com.example.data.common.DataLoadErrorKind
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
                    val isDownloadingUpdate by updateViewModel.isDownloading.collectAsStateWithLifecycle()
                    val updateDownloadError by updateViewModel.downloadError.collectAsStateWithLifecycle()
                    val dataLoadErrors by appContainer.dataLoadErrorReporter.errors.collectAsStateWithLifecycle()
                    var dismissedErrors by remember { mutableStateOf<Map<String, DataLoadError>>(emptyMap()) }

                    LaunchedEffect(dataLoadErrors) {
                        dismissedErrors = dismissedErrors.filter { (source, err) ->
                            dataLoadErrors[source] == err
                        }
                    }

                    val visibleErrors = remember(dataLoadErrors, dismissedErrors) {
                        dataLoadErrors.filter { (source, err) ->
                            dismissedErrors[source] != err
                        }
                    }

                    val prioritizedError = remember(visibleErrors) {
                        visibleErrors.values.minByOrNull { error ->
                            when (error.kind) {
                                DataLoadErrorKind.MISSING_INDEX -> 0
                                DataLoadErrorKind.SIGNED_OUT -> 1
                                DataLoadErrorKind.NO_PERMISSION -> 2
                                DataLoadErrorKind.QUOTA -> 3
                                DataLoadErrorKind.OTHER -> 4
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        ScoutNavGraph(
                            appContainer = appContainer,
                            loginViewModel = loginViewModel,
                            modifier = Modifier.fillMaxSize()
                        )

                        AnimatedVisibility(
                            visible = prioritizedError != null && !isForceUpdateRequired,
                            enter = slideInVertically { -it } + fadeIn(),
                            exit = slideOutVertically { -it } + fadeOut(),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .statusBarsPadding()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .widthIn(max = 560.dp)
                        ) {
                            prioritizedError?.let { error ->
                                DataLoadErrorBanner(
                                    error = error,
                                    onDismiss = { dismissedErrors = dismissedErrors + visibleErrors }
                                )
                            }
                        }

                        if (isForceUpdateRequired) {
                            ForceUpdateScreen(
                                latestVersionName = appUpdateInfo?.latestVersionName.orEmpty(),
                                isDownloading = isDownloadingUpdate,
                                downloadError = updateDownloadError,
                                onUpdateClick = { updateViewModel.startUpdateDownload(appContext) },
                                apkUrl = appUpdateInfo?.apkUrl.orEmpty(),
                                onManualDownloadClick = { updateViewModel.openManualDownload(appContext) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DataLoadErrorBanner(
    error: DataLoadError,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryMessage = when (error.kind) {
        DataLoadErrorKind.NO_PERMISSION -> stringResource(R.string.data_error_no_permission)
        DataLoadErrorKind.QUOTA -> stringResource(R.string.data_error_quota)
        DataLoadErrorKind.MISSING_INDEX -> stringResource(R.string.data_error_missing_index)
        DataLoadErrorKind.SIGNED_OUT -> stringResource(R.string.data_error_signed_out)
        DataLoadErrorKind.OTHER -> stringResource(R.string.data_error_other)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("data_load_error_banner"),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        tonalElevation = 4.dp,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.WarningAmber,
                contentDescription = null,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = primaryMessage,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (error.kind == DataLoadErrorKind.MISSING_INDEX && error.message.isNotBlank()) {
                    Text(
                        text = error.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("data_load_error_dismiss_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.data_error_dismiss),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
