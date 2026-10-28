package com.example

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.domain.preferences.ThemeMode
import com.example.navigation.ScoutNavGraph
import com.example.presentation.auth.LoginViewModel
import com.example.presentation.common.LocalAppDensity
import com.example.presentation.common.LocalOnZoomClick
import com.example.presentation.common.UiScaleDialog
import com.example.ui.theme.ScoutTheme
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
            val uiScale by appContainer.uiScalePreferences.getUiScale()
                .collectAsState(initial = appContainer.uiScalePreferences.getCurrentUiScale())
            var showZoomDialog by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            val originalDensity = LocalDensity.current

            // Calculate the zoomed density
            val scaledDensity = Density(
                density = originalDensity.density * uiScale,
                fontScale = 1.0f // Always 1.0f
            )

            CompositionLocalProvider(
                LocalAppDensity provides scaledDensity,
                LocalDensity provides scaledDensity,
                LocalOnZoomClick provides { showZoomDialog = true }
            ) {
                val themeMode by appContainer.themePreferences.getThemeMode()
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
                            googleSignInHelper = appContainer.googleSignInHelper
                        )
                    )

                    ScoutNavGraph(
                        appContainer = appContainer,
                        loginViewModel = loginViewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
