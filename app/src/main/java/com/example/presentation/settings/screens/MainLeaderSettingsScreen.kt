package com.example.presentation.settings.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.settings.CustomTerms
import com.example.domain.settings.replaceCustomWords
import com.example.presentation.common.AppLogo
import com.example.presentation.common.CustomTopAppBar
import com.example.presentation.common.LocalAppTerms
import com.example.presentation.settings.SettingsEvent
import com.example.presentation.settings.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * شاشة إعدادات القائد الرئيسي (MainLeaderSettingsScreen)
 * تشمل: إدارة قادة السرب، تخصيص شعار التطبيق، وتخصيص الكلمات والمصطلحات.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainLeaderSettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToLeaderManagement: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val currentTerms by viewModel.currentTerms.collectAsStateWithLifecycle()
    val terms = LocalAppTerms.current
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isUploadingLogo by remember { mutableStateOf(false) }

    var scoutSingular by remember { mutableStateOf("") }
    var scoutSingularDefinite by remember { mutableStateOf("") }
    var scoutPlural by remember { mutableStateOf("") }
    var scoutPluralDefinite by remember { mutableStateOf("") }
    var groupName by remember { mutableStateOf("") }
    var groupNameDefinite by remember { mutableStateOf("") }
    var unitName by remember { mutableStateOf("") }
    var unitNameDefinite by remember { mutableStateOf("") }
    var customWordsMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var newOriginalWord by remember { mutableStateOf("") }
    var newReplacementWord by remember { mutableStateOf("") }

    LaunchedEffect(currentTerms) {
        scoutSingular = currentTerms.scoutSingular
        scoutSingularDefinite = currentTerms.scoutSingularDefinite
        scoutPlural = currentTerms.scoutPlural
        scoutPluralDefinite = currentTerms.scoutPluralDefinite
        groupName = currentTerms.groupName
        groupNameDefinite = currentTerms.groupNameDefinite
        unitName = currentTerms.unitName
        unitNameDefinite = currentTerms.unitNameDefinite
        customWordsMap = currentTerms.customWords
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isUploadingLogo = true
            scope.launch(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val originalBitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()

                    if (originalBitmap != null) {
                        val result = viewModel.processAndUploadLogo(originalBitmap)
                        withContext(Dispatchers.Main) {
                            isUploadingLogo = false
                            if (result.isSuccess) {
                                snackbarHostState.showSnackbar(context.getString(R.string.logo_updated_successfully))
                            } else {
                                snackbarHostState.showSnackbar(context.getString(R.string.error_loading_image))
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            isUploadingLogo = false
                            snackbarHostState.showSnackbar(context.getString(R.string.error_loading_image))
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isUploadingLogo = false
                        snackbarHostState.showSnackbar(context.getString(R.string.error_loading_image))
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                else -> {}
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CustomTopAppBar(
                title = "إعدادات القائد الرئيسي",
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("main_leader_settings_back_btn")
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // بطاقة إدارة قادة السرب (Leader Management Hub Card)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("leader_management_hub_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = replaceCustomWords("إدارة قادة ${terms.groupNameDefinite}", terms),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "متابعة طلبات الانضمام المعلقة واعتماد القادة الكشفيين وإدارة الصلاحيات",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        onClick = onNavigateToLeaderManagement,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_leader_management_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("فتح شاشة إدارة القادة")
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // قسم تخصيص شعار التطبيق
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("branding_logo_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(id = R.string.settings_logo_section),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    AppLogo(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentDescription = stringResource(id = R.string.settings_logo_section),
                        contentScale = ContentScale.Fit
                    )

                    Button(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        enabled = !isUploadingLogo,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("change_logo_button")
                    ) {
                        if (isUploadingLogo) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(stringResource(id = R.string.action_change_logo))
                    }
                }
            }

            // قسم تخصيص الكلمات والمصطلحات
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("custom_terms_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(id = R.string.settings_custom_terms_section),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // الحقول الثمانية الأساسية
                    OutlinedTextField(
                        value = scoutSingular,
                        onValueChange = { scoutSingular = it },
                        label = { Text(stringResource(id = R.string.terms_scout_singular)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("terms_scout_singular_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = scoutSingularDefinite,
                        onValueChange = { scoutSingularDefinite = it },
                        label = { Text(stringResource(id = R.string.terms_scout_singular_def)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("terms_scout_singular_def_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = scoutPlural,
                        onValueChange = { scoutPlural = it },
                        label = { Text(stringResource(id = R.string.terms_scout_plural)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("terms_scout_plural_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = scoutPluralDefinite,
                        onValueChange = { scoutPluralDefinite = it },
                        label = { Text(stringResource(id = R.string.terms_scout_plural_def)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("terms_scout_plural_def_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { groupName = it },
                        label = { Text(stringResource(id = R.string.terms_group_singular)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("terms_group_singular_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = groupNameDefinite,
                        onValueChange = { groupNameDefinite = it },
                        label = { Text(stringResource(id = R.string.terms_group_singular_def)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("terms_group_singular_def_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = unitName,
                        onValueChange = { unitName = it },
                        label = { Text(stringResource(id = R.string.terms_unit_name)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("terms_unit_name_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = unitNameDefinite,
                        onValueChange = { unitNameDefinite = it },
                        label = { Text(stringResource(id = R.string.terms_unit_name_def)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("terms_unit_name_def_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // قسم الكلمات الإضافية المخصصة
                    Text(
                        text = stringResource(id = R.string.terms_custom_words),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )

                    // قائمة الكلمات المخصصة الحالية مع زر الحذف
                    if (customWordsMap.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            customWordsMap.forEach { (orig, repl) ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = orig,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "  ←  ",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = repl,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                customWordsMap = customWordsMap - orig
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = stringResource(id = R.string.action_delete),
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // إدخال كلمة مخصصة جديدة
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newOriginalWord,
                            onValueChange = { newOriginalWord = it },
                            label = { Text(stringResource(id = R.string.terms_original_word)) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("new_original_word_input"),
                            shape = RoundedCornerShape(10.dp)
                        )

                        OutlinedTextField(
                            value = newReplacementWord,
                            onValueChange = { newReplacementWord = it },
                            label = { Text(stringResource(id = R.string.terms_replacement_word)) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("new_replacement_word_input"),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Button(
                            onClick = {
                                val o = newOriginalWord.trim()
                                val r = newReplacementWord.trim()
                                if (o.isNotEmpty() && r.isNotEmpty()) {
                                    customWordsMap = customWordsMap + (o to r)
                                    newOriginalWord = ""
                                    newReplacementWord = ""
                                }
                            },
                            enabled = newOriginalWord.isNotBlank() && newReplacementWord.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("add_custom_word_button")
                        ) {
                            Text(stringResource(id = R.string.action_add_word))
                        }
                    }

                    // زر حفظ الكلمات
                    Button(
                        onClick = {
                            val updated = CustomTerms(
                                scoutSingular = scoutSingular.trim().ifEmpty { "شبل" },
                                scoutSingularDefinite = scoutSingularDefinite.trim().ifEmpty { "الشبل" },
                                scoutPlural = scoutPlural.trim().ifEmpty { "أشبال" },
                                scoutPluralDefinite = scoutPluralDefinite.trim().ifEmpty { "الأشبال" },
                                groupName = groupName.trim().ifEmpty { "سرب" },
                                groupNameDefinite = groupNameDefinite.trim().ifEmpty { "السرب" },
                                unitName = unitName.trim().ifEmpty { "النجب" },
                                unitNameDefinite = unitNameDefinite.trim().ifEmpty { "النجب" },
                                customWords = customWordsMap
                            )
                            viewModel.updateCustomTerms(updated)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_custom_terms_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(id = R.string.action_save_terms))
                    }
                }
            }
        }
    }
}
