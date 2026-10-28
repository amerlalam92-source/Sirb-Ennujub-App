package com.example.presentation.quran

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.presentation.common.CustomTopAppBar
import com.example.presentation.quran.schools.QuranSchoolsListScreen
import com.example.presentation.quran.schools.QuranSchoolsViewModel
import com.example.presentation.quran.tracking.QuranScoutsTrackingContent
import com.example.presentation.quran.tracking.QuranScoutsViewModel

/**
 * الشاشة الرئيسية لمتابعة حفظ القرآن الكريم
 * تحتوي على تبويبين:
 * 1. المدارس القرآنية (Schools catalog)
 * 2. متابعة الأشبال (Scouts tracking)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuranTrackingScreen(
    schoolsViewModel: QuranSchoolsViewModel,
    scoutsViewModel: QuranScoutsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabTitles = listOf(
        stringResource(R.string.quran_tracking_tab_schools),
        stringResource(R.string.quran_tracking_tab_scouts)
    )

    Scaffold(
        modifier = modifier.testTag("quran_tracking_screen"),
        topBar = {
            CustomTopAppBar(
                title = stringResource(R.string.quran_tracking_title),
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("quran_tracking_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_action)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = Modifier.testTag("quran_tabs_row")
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = {
                        Text(
                            text = tabTitles[0],
                            fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.testTag("quran_tab_schools")
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = {
                        Text(
                            text = tabTitles[1],
                            fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Groups,
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.testTag("quran_tab_scouts")
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedTabIndex) {
                    0 -> {
                        QuranSchoolsListScreen(
                            viewModel = schoolsViewModel,
                            onNavigateBack = onNavigateBack,
                            showTopBar = false
                        )
                    }
                    1 -> {
                        QuranScoutsTrackingContent(
                            viewModel = scoutsViewModel
                        )
                    }
                }
            }
        }
    }
}
