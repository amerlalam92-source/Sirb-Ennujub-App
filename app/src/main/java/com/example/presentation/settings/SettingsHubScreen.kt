package com.example.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.presentation.common.CustomTopAppBar
import com.example.presentation.common.LocalAppTerms
import com.example.presentation.common.replaceCustomWords

/**
 * شاشة مركز الإعدادات الرئيسية (Settings Hub Screen)
 * تجمع أقسام الإعدادات في 4 بطاقات رئيسية مع توجيه للشاشات الفرعية.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHubScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToGeneralSettings: () -> Unit,
    onNavigateToFinanceSettings: () -> Unit,
    onNavigateToMainLeaderSettings: () -> Unit,
    onNavigateToAccountSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isMainLeader by viewModel.isMainLeader.collectAsStateWithLifecycle()
    val terms = LocalAppTerms.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CustomTopAppBar(
                title = stringResource(id = R.string.settings_title),
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("settings_hub_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.back_action),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // البطاقة 1: إعدادات التنقيط والتقييم
            item {
                SettingsCategoryCard(
                    title = "إعدادات التنقيط والتقييم",
                    subtitle = "سلم درجات الحضور، ونقاط شارات الكفاية وشارات الهواية الإضافية، ومعايير تقييم القادة",
                    icon = Icons.Default.Assessment,
                    iconContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    iconTint = MaterialTheme.colorScheme.primary,
                    testTag = "settings_card_general",
                    onClick = onNavigateToGeneralSettings
                )
            }

            // البطاقة 2: الإعدادات المالية
            item {
                SettingsCategoryCard(
                    title = "الإعدادات المالية",
                    subtitle = "رسوم اشتراكات الحضور والتأخير والغياب، والرصيد الافتتاحي والعملة",
                    icon = Icons.Default.AttachMoney,
                    iconContainerColor = Color(0xFF2E7D32).copy(alpha = 0.16f),
                    iconTint = Color(0xFF2E7D32),
                    testTag = "settings_card_finance",
                    onClick = onNavigateToFinanceSettings
                )
            }

            // البطاقة 3: إعدادات القائد الرئيسي (تظهر فقط إذا كان القائد رئيسياً)
            if (isMainLeader) {
                item {
                    SettingsCategoryCard(
                        title = "إعدادات القائد الرئيسي",
                        subtitle = replaceCustomWords(
                            "تخصيص شعار ${terms.groupNameDefinite}، تعديل المصطلحات المخصصة، وإدارة قادة ${terms.groupNameDefinite}",
                            terms
                        ),
                        icon = Icons.Default.AdminPanelSettings,
                        iconContainerColor = Color(0xFFE65100).copy(alpha = 0.16f),
                        iconTint = Color(0xFFE65100),
                        testTag = "settings_card_main_leader",
                        onClick = onNavigateToMainLeaderSettings
                    )
                }
            }

            // البطاقة 4: إعدادات الحساب
            item {
                SettingsCategoryCard(
                    title = "إعدادات الحساب",
                    subtitle = "بيانات القائد الشخصية، تعديل كلمة المرور، ومعلومات التسجيل",
                    icon = Icons.Default.ManageAccounts,
                    iconContainerColor = Color(0xFF1565C0).copy(alpha = 0.16f),
                    iconTint = Color(0xFF1565C0),
                    testTag = "settings_card_account",
                    onClick = onNavigateToAccountSettings
                )
            }
        }
    }
}

@Composable
private fun SettingsCategoryCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconContainerColor: Color,
    iconTint: Color,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // حاوية الأيقونة
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(iconContainerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // النصوص والمعلومات
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // سهم التنقل
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
