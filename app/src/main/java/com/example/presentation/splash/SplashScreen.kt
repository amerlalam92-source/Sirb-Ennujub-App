package com.example.presentation.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.auth.AuthRepository
import com.example.domain.settings.replaceCustomWords
import com.example.presentation.common.AppLogo
import com.example.presentation.common.LocalAppTerms
import kotlinx.coroutines.delay

/**
 * شاشة البداية المتحركة (Splash Screen)
 * خلفية بيضاء نقية مع إبراز شعار الفوج الرسمي بحجم كبير وألوان كشفية واضحة.
 */
@Composable
fun SplashScreen(
    authRepository: AuthRepository,
    onNavigateToLogin: () -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToPendingApproval: () -> Unit,
    modifier: Modifier = Modifier
) {
    val terms = LocalAppTerms.current
    val scale = remember { Animatable(0.7f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
        )
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 600)
        )

        // مهلة بسيطة لعرض هوية الفوج الكشفي
        delay(900)

        // فحص حالة الجلسة الحالية، تأكيد البريد الإلكتروني، وحالة اعتماد القائد
        try {
            if (authRepository.currentUser != null) {
                try {
                    authRepository.reloadUser()
                } catch (_: Exception) {
                    // استخدام الحالة المخزنة محلياً عند انقطاع الشبكة
                }

                if (!authRepository.isEmailVerified) {
                    onNavigateToPendingApproval()
                } else {
                    val leader = authRepository.getCurrentLeader()
                    if (leader == null || leader.status != "approved") {
                        onNavigateToPendingApproval()
                    } else {
                        onNavigateToHome()
                    }
                }
            } else {
                onNavigateToLogin()
            }
        } catch (_: Exception) {
            onNavigateToLogin()
        }
    }

    val dynamicGroupTitle = replaceCustomWords(
        "${terms.groupName} ${terms.unitNameDefinite}",
        terms
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("splash_screen_container"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(24.dp)
                .scale(scale.value)
                .alpha(alpha.value)
        ) {
            // شعار الفوج الكشفي الرسمي (مخصص من Firebase أو الأيقونة الافتراضية)
            AppLogo(
                contentDescription = dynamicGroupTitle,
                modifier = Modifier
                    .size(200.dp)
                    .clip(CircleShape)
                    .testTag("splash_app_logo")
            )

            Spacer(modifier = Modifier.height(28.dp))

            // اسم الوحدة / السرب الديناميكي
            Text(
                text = dynamicGroupTitle,
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // الفوج بوضوح عالٍ في الوضعين الفاتح والداكن
            Text(
                text = replaceCustomWords(stringResource(id = R.string.splash_subtitle), terms),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            // مؤشر التحميل
            CircularProgressIndicator(
                modifier = Modifier
                    .size(36.dp)
                    .testTag("splash_progress_indicator"),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp
            )
        }
    }
}
