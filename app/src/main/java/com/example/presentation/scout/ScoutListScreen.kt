package com.example.presentation.scout

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.example.domain.progress.BadgeLevel
import com.example.presentation.common.AppAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.scout.Scout
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.example.presentation.common.CustomTopAppBar
import com.example.presentation.common.ResponsiveFilterRow

/**
 * شاشة قائمة الأشبال بسرب النُّجُب (Scout List Screen)
 * تعرض القائمة بشكل حي وتفاعلي وتدعم البحث والتصفية وزر الاتصال المباشر بالولي.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScoutListScreen(
    viewModel: ScoutViewModel,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToAdd: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.listUiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var scoutToArchive by remember { mutableStateOf<Scout?>(null) }
    var scoutToUnarchive by remember { mutableStateOf<Scout?>(null) }
    var scoutToDelete by remember { mutableStateOf<Scout?>(null) }
    var scoutForPhonePicker by remember { mutableStateOf<Scout?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CustomTopAppBar(
                title = stringResource(id = R.string.scouts_title),
                subtitle = stringResource(id = R.string.scouts_subtitle),
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("scout_list_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.cancel_action),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAdd,
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                shape = CircleShape,
                modifier = Modifier
                    .testTag("add_scout_fab")
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(id = R.string.add_scout_button),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // شريط البحث
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                placeholder = {
                    Text(
                        text = stringResource(id = R.string.search_hint),
                        maxLines = 1
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag("scout_search_bar")
            )

            // شرائح التصفية وشارة العداد الحي
            ResponsiveFilterRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == ScoutFilter.ACTIVE,
                    onClick = { viewModel.onFilterSelected(ScoutFilter.ACTIVE) },
                    label = { Text(stringResource(id = R.string.filter_active)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.testTag("filter_chip_active")
                )
                FilterChip(
                    selected = selectedFilter == ScoutFilter.ALL,
                    onClick = { viewModel.onFilterSelected(ScoutFilter.ALL) },
                    label = { Text(stringResource(id = R.string.filter_all)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.testTag("filter_chip_all")
                )
                FilterChip(
                    selected = selectedFilter == ScoutFilter.ARCHIVED,
                    onClick = { viewModel.onFilterSelected(ScoutFilter.ARCHIVED) },
                    label = { Text(stringResource(id = R.string.filter_archived)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.testTag("filter_chip_archived")
                )

                // شارة العداد الديناميكي للأشبال النشطين
                if (uiState is ScoutListUiState.Success) {
                    val count = (uiState as ScoutListUiState.Success).activeCount
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.active_scouts_count_badge, count),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // محتوى القائمة حسب الحالة
            when (val state = uiState) {
                is ScoutListUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("scouts_loading_indicator")
                        )
                    }
                }

                is ScoutListUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                is ScoutListUiState.Success -> {
                    if (state.filteredScouts.isEmpty()) {
                        // حالة القائمة الفارغة
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(88.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (searchQuery.isNotEmpty()) Icons.Default.Search else Icons.Default.Groups,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(48.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = if (searchQuery.isNotEmpty()) {
                                        stringResource(id = R.string.empty_search_title)
                                    } else {
                                        stringResource(id = R.string.empty_scouts_title)
                                    },
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (searchQuery.isNotEmpty()) {
                                        stringResource(id = R.string.empty_search_desc)
                                    } else {
                                        stringResource(id = R.string.empty_scouts_desc)
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        // عرض القائمة السلسة عبر LazyColumn
                        // BUG 3 FIX: إضافة مسافة سفلية 88dp لتجنب تداخل زر FAB مع آخر بطاقة في القائمة
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("scouts_lazy_column"),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                top = 8.dp,
                                end = 16.dp,
                                bottom = 88.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(
                                items = state.filteredScouts,
                                key = { it.id }
                            ) { scout ->
                                // === RTL MAPPING DOCUMENTATION ===
                                // في Compose مع اتجاه RTL (من اليمين إلى اليسار):
                                // - StartToEnd: يتحرك الصندوق من بداية التخطيط (اليمين في RTL) إلى نهايته (اليسار).
                                //   الاتجاه البصري الفعلي: سحب لليسار (Swipe LEFT) -> يؤدي إلى الحذف (DELETE).
                                //   الخلفية: حمراء (error)، الأيقونة: Delete، المحاذاة: CenterStart (اليمين في RTL - منطقة الكشف).
                                // - EndToStart: يتحرك الصندوق من نهاية التخطيط (اليسار في RTL) إلى بدايته (اليمين).
                                //   الاتجاه البصري الفعلي: سحب لليمين (Swipe RIGHT):
                                //   * إذا كان الشبل نشطاً (active): الخلفية خضراء (#2E7D32)، الأيقونة: Archive، حوار: أرشفة الشبل.
                                //   * إذا كان الشبل مؤرشفاً (archived): الخلفية ثانوية (secondaryContainer)، الأيقونة: Unarchive، حوار: إلغاء الأرشفة.
                                //   المحاذاة: CenterEnd (اليسار في RTL - منطقة الكشف).
                                val dismissState = rememberSwipeToDismissBoxState(
                                    positionalThreshold = { totalDistance -> totalDistance * 0.4f },
                                    confirmValueChange = { dismissValue ->
                                        when (dismissValue) {
                                            SwipeToDismissBoxValue.EndToStart -> {
                                                // الاتجاه البصري: سحب لليمين (Swipe RIGHT)
                                                if (scout.status == Scout.STATUS_ARCHIVED || scout.status == "archived") {
                                                    scoutToUnarchive = scout
                                                } else {
                                                    scoutToArchive = scout
                                                }
                                                false // عدم الإخفاء التلقائي؛ انتظار قرار القائد في حوار التأكيد
                                            }
                                            SwipeToDismissBoxValue.StartToEnd -> {
                                                // الاتجاه البصري: سحب لليسار (Swipe LEFT) -> حذف الشبل
                                                scoutToDelete = scout
                                                false // عدم الإخفاء التلقائي؛ انتظار قرار القائد في حوار التأكيد
                                            }
                                            SwipeToDismissBoxValue.Settled -> false
                                        }
                                    }
                                )

                                SwipeToDismissBox(
                                    state = dismissState,
                                    modifier = Modifier.testTag("swipe_box_${scout.id}"),
                                    backgroundContent = {
                                        val target = dismissState.targetValue
                                        val isRightSwipe = target == SwipeToDismissBoxValue.EndToStart // swipe RIGHT in RTL
                                        val isArchivedScout = scout.status == Scout.STATUS_ARCHIVED || scout.status == "archived"

                                        val backgroundColor = when {
                                            isRightSwipe && isArchivedScout -> MaterialTheme.colorScheme.secondaryContainer
                                            isRightSwipe -> Color(0xFF2E7D32) // scout green
                                            else -> MaterialTheme.colorScheme.error // red
                                        }

                                        val backgroundIcon = when {
                                            isRightSwipe && isArchivedScout -> Icons.Default.Unarchive
                                            isRightSwipe -> Icons.Default.Archive
                                            else -> Icons.Default.Delete
                                        }

                                        val iconTint = when {
                                            isRightSwipe && isArchivedScout -> MaterialTheme.colorScheme.onSecondaryContainer
                                            else -> Color.White
                                        }

                                        // Icon alignment (Reveal area):
                                        //   عند سحب البطاقة لليمين تنكشف الجهة اليسرى -> في RTL هي Alignment.CenterEnd
                                        //   عند سحب البطاقة لليسار تنكشف الجهة اليمنى -> في RTL هي Alignment.CenterStart
                                        val alignment = if (isRightSwipe) {
                                            Alignment.CenterEnd
                                        } else {
                                            Alignment.CenterStart
                                        }

                                        if (target != SwipeToDismissBoxValue.Settled) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(backgroundColor)
                                                    .padding(horizontal = 16.dp),
                                                contentAlignment = alignment
                                            ) {
                                                Icon(
                                                    imageVector = backgroundIcon,
                                                    contentDescription = null,
                                                    tint = iconTint,
                                                    modifier = Modifier.size(32.dp)
                                                )
                                            }
                                        } else {
                                            Box(modifier = Modifier.fillMaxSize())
                                        }
                                    }
                                ) {
                                    ScoutItemCard(
                                        scout = scout,
                                        grantedBadges = state.grantedBadgesByScout[scout.id].orEmpty(),
                                        inProgressBadges = state.inProgressBadgesByScout[scout.id].orEmpty(),
                                        onClick = { onNavigateToDetail(scout.id) },
                                        onPhonePickerRequest = { scoutForPhonePicker = it }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // مربع حوار تأكيد الأرشفة (عند السحب لليمين لشبل نشط)
    scoutToArchive?.let { scout ->
        AppAlertDialog(
            onDismissRequest = { scoutToArchive = null },
            title = {
                Text(
                    text = stringResource(id = R.string.dialog_archive_scout_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = stringResource(id = R.string.confirm_archive_scout_body_format, scout.fullName),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = scout.id
                        scoutToArchive = null
                        viewModel.archiveScout(id) {
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = "تمت أرشفة الشبل",
                                    actionLabel = "تراجع",
                                    duration = androidx.compose.material3.SnackbarDuration.Short
                                )
                                if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                    viewModel.unarchiveScout(id)
                                }
                            }
                        }
                    },
                    modifier = Modifier.testTag("confirm_archive_button")
                ) {
                    Text(
                        text = stringResource(id = R.string.action_archive),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { scoutToArchive = null }) {
                    Text(stringResource(id = R.string.cancel_action))
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    // مربع حوار تأكيد إلغاء الأرشفة (عند السحب لليمين لشبل مؤرشف)
    scoutToUnarchive?.let { scout ->
        AppAlertDialog(
            onDismissRequest = { scoutToUnarchive = null },
            title = {
                Text(
                    text = stringResource(id = R.string.dialog_unarchive_scout_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = stringResource(id = R.string.confirm_unarchive_scout_body_format, scout.fullName),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = scout.id
                        scoutToUnarchive = null
                        viewModel.unarchiveScout(id) {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    message = "تم إلغاء أرشفة الشبل",
                                    duration = androidx.compose.material3.SnackbarDuration.Short
                                )
                            }
                        }
                    },
                    modifier = Modifier.testTag("confirm_unarchive_button")
                ) {
                    Text(
                        text = stringResource(id = R.string.action_confirm_unarchive),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { scoutToUnarchive = null }) {
                    Text(stringResource(id = R.string.cancel_action))
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    // مربع حوار تأكيد الحذف (عند السحب لليسار)
    scoutToDelete?.let { scout ->
        AppAlertDialog(
            onDismissRequest = { scoutToDelete = null },
            title = {
                Text(
                    text = stringResource(id = R.string.dialog_delete_scout_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Text(
                    text = stringResource(id = R.string.confirm_delete_scout_body_format, scout.fullName),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = scout.id
                        scoutToDelete = null
                        viewModel.deleteScout(id) {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    message = "تم حذف الشبل",
                                    duration = androidx.compose.material3.SnackbarDuration.Short
                                )
                            }
                        }
                    },
                    modifier = Modifier.testTag("confirm_delete_button")
                ) {
                    Text(
                        text = stringResource(id = R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { scoutToDelete = null }) {
                    Text(stringResource(id = R.string.cancel_action))
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    scoutForPhonePicker?.let { scout ->
        PhonePickerDialog(
            scout = scout,
            onDismiss = { scoutForPhonePicker = null }
        )
    }
}

/**
 * بطاقة الشبل الفردية في القائمة (تصميم مدمج وأنيق يوزع البيانات والشارات بشكل متناسق)
 */
@Composable
private fun ScoutItemCard(
    scout: Scout,
    grantedBadges: Set<BadgeLevel> = emptySet(),
    inProgressBadges: Set<BadgeLevel> = emptySet(),
    onClick: () -> Unit,
    onPhonePickerRequest: ((Scout) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val phones = scout.allPhones()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("scout_card_${scout.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ---- ROW 1: Info (Single Line Layout) ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // RIGHT (Start in RTL): Scout Name
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = scout.fullName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!scout.isActive) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = stringResource(id = R.string.status_archived),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // CENTER: Age
                Text(
                    text = "${scout.age} سنة",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                // LEFT (End in RTL): Guardian + Call Icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.weight(1f)
                ) {
                    if (scout.guardianName.isNotBlank()) {
                        Text(
                            text = scout.guardianName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    if (phones.isNotEmpty()) {
                        val isDark = isSystemInDarkTheme()
                        val greenColor = if (isDark) Color(0xFFA5D6A7) else Color(0xFF4CAF50)
                        IconButton(
                            onClick = {
                                if (phones.size == 1) {
                                    val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                                        data = Uri.parse("tel:${phones.first()}")
                                    }
                                    context.startActivity(dialIntent)
                                } else {
                                    onPhonePickerRequest?.invoke(scout)
                                }
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("call_guardian_icon_${scout.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = "اتصال",
                                tint = greenColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // ---- ROW 2: Badges (Horizontal 4-in-a-row) ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScoutBadgeItem(
                    shortName = stringResource(id = R.string.badge_short_acceptance),
                    isGranted = grantedBadges.contains(BadgeLevel.ACCEPTANCE),
                    isInProgress = inProgressBadges.contains(BadgeLevel.ACCEPTANCE)
                )
                ScoutBadgeItem(
                    shortName = stringResource(id = R.string.badge_short_beginner),
                    isGranted = grantedBadges.contains(BadgeLevel.BEGINNER),
                    isInProgress = inProgressBadges.contains(BadgeLevel.BEGINNER)
                )
                ScoutBadgeItem(
                    shortName = stringResource(id = R.string.badge_short_second),
                    isGranted = grantedBadges.contains(BadgeLevel.SECOND),
                    isInProgress = inProgressBadges.contains(BadgeLevel.SECOND)
                )
                ScoutBadgeItem(
                    shortName = stringResource(id = R.string.badge_short_first),
                    isGranted = grantedBadges.contains(BadgeLevel.FIRST),
                    isInProgress = inProgressBadges.contains(BadgeLevel.FIRST)
                )
            }
        }
    }
}

/**
 * عنصر شارة كفاءة مفرد (أيقونة وتحتها اسم الشارة)
 */
@Composable
private fun ScoutBadgeItem(
    shortName: String,
    isGranted: Boolean,
    isInProgress: Boolean,
    modifier: Modifier = Modifier
) {
    val tint = when {
        isGranted -> MaterialTheme.colorScheme.primary
        isInProgress -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Column(
        modifier = modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.WorkspacePremium,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = shortName,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                lineHeight = 13.sp
            ),
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center
        )
    }
}
