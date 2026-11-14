package com.example.presentation.home

import androidx.compose.foundation.Image
import com.example.presentation.common.AppLogo
import com.example.presentation.common.LocalAppTerms
import com.example.presentation.common.replaceCustomWords
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.navigation.Routes
import com.example.presentation.common.StageTerms

/**
 * محتوى القائمة الجانبية للتطبيق (App Navigation Drawer)
 * يعرض ترويسة كشفية راقية مع شعار السرب، واسم القائد، وروابط التوجيه لجميع أقسام التطبيق.
 */
@Composable
fun AppDrawerContent(
    currentRoute: String?,
    leaderName: String?,
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    isMainLeader: Boolean = false,
    stageTerms: StageTerms = StageTerms()
) {
    val terms = LocalAppTerms.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // ترويسة القائمة الجانبية (الشعار + اسم السرب + اسم الفوج)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppLogo(
                    contentDescription = stringResource(id = R.string.app_name),
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = replaceCustomWords(stringResource(id = R.string.app_name), terms),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = replaceCustomWords(stringResource(id = R.string.scout_group_name), terms),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = replaceCustomWords(leaderName ?: stringResource(id = R.string.scout_leader_default_title), terms),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // عناصر القائمة
        NavigationDrawerItem(
            label = { Text(replaceCustomWords(stringResource(id = R.string.drawer_home), terms)) },
            icon = { Icon(Icons.Default.Home, contentDescription = null) },
            selected = currentRoute == Routes.HOME,
            onClick = { onNavigate(Routes.HOME) },
            modifier = Modifier.testTag("drawer_item_home"),
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary
            )
        )

        NavigationDrawerItem(
            label = { Text(replaceCustomWords("سجل ${terms.scoutPluralDefinite}", terms)) },
            icon = { Icon(Icons.Default.Groups, contentDescription = null) },
            selected = currentRoute == Routes.SCOUTS_LIST,
            onClick = { onNavigate(Routes.SCOUTS_LIST) },
            modifier = Modifier.testTag("drawer_item_scouts"),
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary
            )
        )

        NavigationDrawerItem(
            label = { Text(replaceCustomWords(stringResource(id = R.string.drawer_activities), terms)) },
            icon = { Icon(Icons.Default.Event, contentDescription = null) },
            selected = currentRoute == Routes.ACTIVITIES_LIST,
            onClick = { onNavigate(Routes.ACTIVITIES_LIST) },
            modifier = Modifier.testTag("drawer_item_activities"),
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary
            )
        )

        NavigationDrawerItem(
            label = { Text(replaceCustomWords(stringResource(id = R.string.drawer_attendance), terms)) },
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null) },
            selected = currentRoute == Routes.ATTENDANCE_PICKER,
            onClick = { onNavigate(Routes.ATTENDANCE_PICKER) },
            modifier = Modifier.testTag("drawer_item_attendance"),
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary
            )
        )

        NavigationDrawerItem(
            label = { Text(replaceCustomWords(stringResource(id = R.string.training_meetings_register_title), terms)) },
            icon = { Icon(Icons.Default.EventNote, contentDescription = null) },
            selected = currentRoute == Routes.TRAINING_MEETINGS_REGISTER,
            onClick = { onNavigate(Routes.TRAINING_MEETINGS_REGISTER) },
            modifier = Modifier.testTag("drawer_item_training_meetings_register"),
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary
            )
        )

        NavigationDrawerItem(
            label = { Text(replaceCustomWords(stringResource(id = R.string.drawer_progress), terms)) },
            icon = { Icon(Icons.Default.MilitaryTech, contentDescription = null) },
            selected = currentRoute == Routes.PROGRESS_CATALOG,
            onClick = { onNavigate(Routes.PROGRESS_CATALOG) },
            modifier = Modifier.testTag("drawer_item_progress"),
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary
            )
        )

        NavigationDrawerItem(
            label = { Text(replaceCustomWords(stringResource(id = R.string.drawer_hobby_badges), terms)) },
            icon = { Icon(Icons.Default.WorkspacePremium, contentDescription = null) },
            selected = currentRoute == Routes.HOBBY_BADGES_CATALOG,
            onClick = { onNavigate(Routes.HOBBY_BADGES_CATALOG) },
            modifier = Modifier.testTag("drawer_item_hobby_badges"),
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary
            )
        )

        NavigationDrawerItem(
            label = { Text(replaceCustomWords(stringResource(id = R.string.drawer_quran_tracking), terms)) },
            icon = { Icon(Icons.Default.MenuBook, contentDescription = null) },
            selected = currentRoute == Routes.QURAN_TRACKING,
            onClick = { onNavigate(Routes.QURAN_TRACKING) },
            modifier = Modifier.testTag("drawer_item_quran_tracking"),
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary
            )
        )

        NavigationDrawerItem(
            label = { Text(replaceCustomWords(stringResource(id = R.string.drawer_finance), terms)) },
            icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null) },
            selected = currentRoute == Routes.FINANCE_HOME,
            onClick = { onNavigate(Routes.FINANCE_HOME) },
            modifier = Modifier.testTag("drawer_item_finance"),
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary
            )
        )

        NavigationDrawerItem(
            label = { Text(replaceCustomWords("تقييم ${terms.scoutPluralDefinite}", terms)) },
            icon = { Icon(Icons.Default.Star, contentDescription = null) },
            selected = currentRoute == Routes.EVALUATION_SCREEN,
            onClick = { onNavigate(Routes.EVALUATION_SCREEN) },
            modifier = Modifier.testTag("drawer_item_evaluation"),
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary
            )
        )

        NavigationDrawerItem(
            label = { Text(replaceCustomWords("المصفوفة الشاملة لتقييم ${terms.scoutPluralDefinite}", terms)) },
            icon = { Icon(Icons.Default.GridOn, contentDescription = null) },
            selected = currentRoute == Routes.EVALUATION_MATRIX,
            onClick = { onNavigate(Routes.EVALUATION_MATRIX) },
            modifier = Modifier.testTag("drawer_item_evaluation_matrix"),
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary
            )
        )

        NavigationDrawerItem(
            label = { Text(replaceCustomWords(stringResource(id = R.string.drawer_reports), terms)) },
            icon = { Icon(Icons.Default.Assessment, contentDescription = null) },
            selected = currentRoute == Routes.REPORTS_SCREEN,
            onClick = { onNavigate(Routes.REPORTS_SCREEN) },
            modifier = Modifier.testTag("drawer_item_reports"),
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary
            )
        )

        NavigationDrawerItem(
            label = { Text(replaceCustomWords(stringResource(id = R.string.drawer_audit_log), terms)) },
            icon = { Icon(Icons.Default.History, contentDescription = null) },
            selected = currentRoute == Routes.AUDIT_LOG,
            onClick = { onNavigate(Routes.AUDIT_LOG) },
            modifier = Modifier.testTag("drawer_item_audit_log"),
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary
            )
        )

        NavigationDrawerItem(
            label = { Text(replaceCustomWords(stringResource(id = R.string.drawer_settings), terms)) },
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            selected = currentRoute == Routes.SETTINGS_HUB || currentRoute == Routes.SETTINGS,
            onClick = { onNavigate(Routes.SETTINGS_HUB) },
            modifier = Modifier.testTag("drawer_item_settings"),
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary
            )
        )

        NavigationDrawerItem(
            label = { Text(replaceCustomWords(stringResource(id = R.string.drawer_about), terms)) },
            icon = { Icon(Icons.Default.Info, contentDescription = null) },
            selected = currentRoute == Routes.ABOUT,
            onClick = { onNavigate(Routes.ABOUT) },
            modifier = Modifier.testTag("drawer_item_about"),
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary
            )
        )

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Spacer(modifier = Modifier.height(8.dp))

        NavigationDrawerItem(
            label = { Text(replaceCustomWords(stringResource(id = R.string.drawer_logout), terms)) },
            icon = { Icon(Icons.Default.Logout, contentDescription = null) },
            selected = false,
            onClick = onLogout,
            modifier = Modifier.testTag("drawer_item_logout"),
            colors = NavigationDrawerItemDefaults.colors(
                unselectedIconColor = MaterialTheme.colorScheme.error,
                unselectedTextColor = MaterialTheme.colorScheme.error
            )
        )
    }
}
