package com.rsps1008.daymatter.ui

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rsps1008.daymatter.MainViewModel
import com.rsps1008.daymatter.data.CategoryFilter
import com.rsps1008.daymatter.data.CountdownLogic
import com.rsps1008.daymatter.data.DayMatterRepository
import com.rsps1008.daymatter.data.EventCategory
import com.rsps1008.daymatter.data.EventItem
import com.rsps1008.daymatter.data.RepeatType
import com.rsps1008.daymatter.drive.GoogleDriveBackupService
import kotlinx.coroutines.launch
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.max

private val dateFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")

private enum class DriveAction {
    BACKUP,
    RESTORE,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayMatterApp(
    viewModel: MainViewModel = viewModel(),
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val events by viewModel.events.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(CategoryFilter.ALL) }
    var editorTarget by remember { mutableStateOf<EventItem?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var pendingImportCsv by remember { mutableStateOf<String?>(null) }
    var showImportModeDialog by rememberSaveable { mutableStateOf(false) }
    var showClearAllConfirm by rememberSaveable { mutableStateOf(false) }
    var pendingDriveAction by rememberSaveable { mutableStateOf<DriveAction?>(null) }
    var pendingDriveBackupCsv by remember { mutableStateOf<String?>(null) }
    var showDriveRestoreModeDialog by rememberSaveable { mutableStateOf(false) }
    var pendingDriveRestoreReplaceExisting by rememberSaveable { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val csv = context.contentResolver.openInputStream(uri)?.use { input ->
                    input.readBytes().toString(Charsets.UTF_8)
                }.orEmpty()
                pendingImportCsv = csv
                showImportModeDialog = true
            }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            scope.launch {
                val csv = viewModel.exportCsv()
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(csv.toByteArray(Charsets.UTF_8))
                }
                snackbarHostState.showSnackbar(UiText.exportDone())
            }
        }
    }
    val driveAuthLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val activity = context.findActivity() ?: return@rememberLauncherForActivityResult
        val action = pendingDriveAction ?: return@rememberLauncherForActivityResult
        if (result.resultCode != Activity.RESULT_OK || result.data == null) {
            pendingDriveAction = null
            pendingDriveBackupCsv = null
            scope.launch {
                snackbarHostState.showSnackbar(UiText.googleDriveCancelled())
            }
            return@rememberLauncherForActivityResult
        }

        scope.launch {
            runCatching {
                val authResult = GoogleDriveBackupService.resolveAuthorization(activity, result.data!!)
                val accessToken = authResult.accessToken
                    ?: throw IllegalStateException(UiText.googleDriveAuthFailed())
                when (action) {
                    DriveAction.BACKUP -> {
                        val csv = pendingDriveBackupCsv ?: viewModel.exportCsv()
                        GoogleDriveBackupService.backupCsv(accessToken, csv)
                        snackbarHostState.showSnackbar(UiText.googleDriveBackupDone())
                    }
                    DriveAction.RESTORE -> {
                        val csv = GoogleDriveBackupService.restoreCsv(accessToken)
                        viewModel.importCsv(csv, replaceExisting = pendingDriveRestoreReplaceExisting)
                        snackbarHostState.showSnackbar(UiText.googleDriveRestoreDone())
                    }
                }
            }.onFailure { error ->
                snackbarHostState.showSnackbar(UiText.googleDriveOperationFailed(error.message ?: "Unknown error"))
            }
            pendingDriveAction = null
            pendingDriveBackupCsv = null
            pendingDriveRestoreReplaceExisting = false
        }
    }

    val filteredEvents = remember(events, filter) {
        DayMatterRepository.sortEvents(events).filter { filter.matches(it.category) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            UiText.appTitle(),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            UiText.eventsCount(events.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = UiText.settings())
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editorTarget = EventItem(title = "", category = EventCategory.OTHER, date = LocalDate.now()) }) {
                Icon(Icons.Default.Add, contentDescription = UiText.addEvent())
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        Color(0xFF12203A),
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                CategoryTabs(selected = filter, onSelected = { filter = it })

                Spacer(modifier = Modifier.height(16.dp))

                if (filteredEvents.isEmpty()) {
                    EmptyState()
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(
                            items = filteredEvents,
                            key = { it.id },
                        ) { event ->
                            val countdown = CountdownLogic.resolveCountdown(event)
    EventListItem(
        event = event,
        countdownText = countdown.displayText,
        countdownDays = countdown.days,
        detailText = buildString {
                                append(event.category.label)
                                append(" · ")
                                append(countdown.subtitle)
                            },
                            onClick = { editorTarget = event },
                        )
                        }
                    }
                }
            }
        }
    }

    if (editorTarget != null) {
        EventEditorDialog(
            event = editorTarget,
            onDismiss = { editorTarget = null },
            onSave = {
                viewModel.save(it)
                editorTarget = null
            },
            onDelete = { eventId ->
                viewModel.delete(eventId)
                editorTarget = null
            }
        )
    }

    if (showSettings) {
        SettingsDialog(
            onDismiss = { showSettings = false },
            onImport = {
                showSettings = false
                importLauncher.launch(arrayOf("text/*"))
            },
            onExport = {
                showSettings = false
                exportLauncher.launch("daymatter-${LocalDate.now()}.csv")
            },
            onDriveBackup = {
                showSettings = false
                scope.launch {
                    val activity = context.findActivity()
                    if (activity == null) {
                        snackbarHostState.showSnackbar(UiText.googleDriveUnavailable())
                        return@launch
                    }
                    val csv = viewModel.exportCsv()
                    runCatching {
                        GoogleDriveBackupService.authorize(activity)
                    }.onSuccess { authResult ->
                        if (authResult.hasResolution()) {
                            pendingDriveAction = DriveAction.BACKUP
                            pendingDriveBackupCsv = csv
                            val pendingIntent = authResult.pendingIntent
                            if (pendingIntent != null) {
                                driveAuthLauncher.launch(
                                    IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                                )
                            } else {
                                snackbarHostState.showSnackbar(UiText.googleDriveAuthFailed())
                                pendingDriveAction = null
                                pendingDriveBackupCsv = null
                            }
                        } else {
                            val accessToken = authResult.accessToken
                            if (accessToken == null) {
                                snackbarHostState.showSnackbar(UiText.googleDriveAuthFailed())
                            } else {
                                GoogleDriveBackupService.backupCsv(accessToken, csv)
                                snackbarHostState.showSnackbar(UiText.googleDriveBackupDone())
                            }
                        }
                    }.onFailure { error ->
                        snackbarHostState.showSnackbar(UiText.googleDriveBackupFailed(error.message ?: "Unknown error"))
                    }
                }
            },
            onDriveRestore = {
                showSettings = false
                showDriveRestoreModeDialog = true
            },
            onDeleteAll = {
                showSettings = false
                showClearAllConfirm = true
            }
        )
    }

    if (showImportModeDialog) {
        val csv = pendingImportCsv
        AlertDialog(
            onDismissRequest = {
                pendingImportCsv = null
                showImportModeDialog = false
            },
            title = { Text(UiText.importCsvTitle()) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(UiText.importCsvQuestion(), style = MaterialTheme.typography.bodyLarge)
                    FilledTonalButton(
                        onClick = {
                            val input = csv ?: return@FilledTonalButton
                            scope.launch {
                                val result = viewModel.importCsv(input, replaceExisting = false)
                                pendingImportCsv = null
                                showImportModeDialog = false
                                snackbarHostState.showSnackbar(
                                    UiText.importResult(result.imported.size, result.skippedLines.size, replacedExisting = false)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(UiText.importKeepExisting())
                    }
                    Button(
                        onClick = {
                            val input = csv ?: return@Button
                            scope.launch {
                                val result = viewModel.importCsv(input, replaceExisting = true)
                                pendingImportCsv = null
                                showImportModeDialog = false
                                snackbarHostState.showSnackbar(
                                    UiText.importResult(result.imported.size, result.skippedLines.size, replacedExisting = true)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(UiText.importReplaceExisting())
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingImportCsv = null
                        showImportModeDialog = false
                    }
                ) {
                    Text(UiText.cancel())
                }
            }
        )
    }

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text(UiText.deleteAllTitle()) },
            text = {
                Text(
                    UiText.deleteAllBody(),
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            viewModel.clearAll()
                            showClearAllConfirm = false
                            snackbarHostState.showSnackbar(UiText.deleteAllDone())
                        }
                    }
                ) {
                    Text(UiText.delete())
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) {
                    Text(UiText.cancel())
                }
            }
        )
    }

    if (showDriveRestoreModeDialog) {
        AlertDialog(
            onDismissRequest = { showDriveRestoreModeDialog = false },
            title = { Text(UiText.restoreTitle()) },
            text = {
                Text(
                    UiText.restoreBody(),
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDriveRestoreModeDialog = false
                        pendingDriveRestoreReplaceExisting = false
                        scope.launch {
                            val activity = context.findActivity()
                            if (activity == null) {
                                snackbarHostState.showSnackbar(UiText.googleDriveUnavailable())
                                return@launch
                            }
                            runCatching {
                                GoogleDriveBackupService.authorize(activity)
                            }.onSuccess { authResult ->
                                if (authResult.hasResolution()) {
                                    pendingDriveAction = DriveAction.RESTORE
                                    pendingDriveBackupCsv = null
                                    val pendingIntent = authResult.pendingIntent
                                    if (pendingIntent != null) {
                                        driveAuthLauncher.launch(
                                            IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                                        )
                                    } else {
                                        snackbarHostState.showSnackbar(UiText.googleDriveAuthFailed())
                                        pendingDriveAction = null
                                    }
                                } else {
                                    val accessToken = authResult.accessToken
                                    if (accessToken == null) {
                                        snackbarHostState.showSnackbar(UiText.googleDriveAuthFailed())
                                    } else {
                                        val csv = GoogleDriveBackupService.restoreCsv(accessToken)
                                        viewModel.importCsv(csv, replaceExisting = false)
                                        snackbarHostState.showSnackbar(UiText.googleDriveRestoreDone())
                                    }
                                }
                            }.onFailure { error ->
                                snackbarHostState.showSnackbar(UiText.googleDriveRestoreFailed(error.message ?: "Unknown error"))
                            }
                        }
                    }
                ) {
                    Text(UiText.restoreKeepExisting())
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDriveRestoreModeDialog = false
                        pendingDriveRestoreReplaceExisting = true
                        scope.launch {
                            val activity = context.findActivity()
                            if (activity == null) {
                                snackbarHostState.showSnackbar(UiText.googleDriveUnavailable())
                                return@launch
                            }
                            runCatching {
                                GoogleDriveBackupService.authorize(activity)
                            }.onSuccess { authResult ->
                                if (authResult.hasResolution()) {
                                    pendingDriveAction = DriveAction.RESTORE
                                    pendingDriveBackupCsv = null
                                    val pendingIntent = authResult.pendingIntent
                                    if (pendingIntent != null) {
                                        driveAuthLauncher.launch(
                                            IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                                        )
                                    } else {
                                        snackbarHostState.showSnackbar(UiText.googleDriveAuthFailed())
                                        pendingDriveAction = null
                                    }
                                } else {
                                    val accessToken = authResult.accessToken
                                    if (accessToken == null) {
                                        snackbarHostState.showSnackbar(UiText.googleDriveAuthFailed())
                                    } else {
                                        val csv = GoogleDriveBackupService.restoreCsv(accessToken)
                                        viewModel.importCsv(csv, replaceExisting = true)
                                        snackbarHostState.showSnackbar(UiText.googleDriveRestoreDone())
                                    }
                                }
                            }.onFailure { error ->
                                snackbarHostState.showSnackbar(UiText.googleDriveRestoreFailed(error.message ?: "Unknown error"))
                            }
                        }
                    }
                ) {
                    Text(UiText.restoreReplaceExisting())
                }
            }
        )
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

@Composable
private fun CategoryTabs(selected: CategoryFilter, onSelected: (CategoryFilter) -> Unit) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .horizontalScroll(scrollState)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CategoryFilter.entries.forEach { item ->
            FilterChip(
                selected = selected == item,
                onClick = { onSelected(item) },
                label = { Text(item.label, style = MaterialTheme.typography.bodyMedium) },
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(UiText.noEventsTitle(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                UiText.noEventsBody(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EventListItem(
    event: EventItem,
    countdownText: String,
    countdownDays: Long,
    detailText: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(event.category.iconRes),
                    contentDescription = event.category.label,
                    modifier = Modifier
                        .padding(9.dp)
                        .size(22.dp),
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.primary),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    event.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    detailText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = when {
                    countdownDays == 0L -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)
                    countdownDays < 0L -> MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
                    else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                },
            ) {
                Text(
                    text = countdownText,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventEditorDialog(
    event: EventItem?,
    onDismiss: () -> Unit,
    onSave: (EventItem) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val initial = event ?: return
    var title by remember(initial.id) { mutableStateOf(initial.title) }
    var category by remember(initial.id) { mutableStateOf(initial.category) }
    var date by remember(initial.id) { mutableStateOf(initial.date) }
    var repeatType by remember(initial.id) { mutableStateOf(initial.repeatType) }
    var repeatIntervalText by remember(initial.id) { mutableStateOf(if (initial.repeatInterval > 0) initial.repeatInterval.toString() else "") }
    var enableReminder by remember(initial.id) { mutableStateOf(initial.enableReminder) }
    var reminderTime by remember(initial.id) { mutableStateOf(initial.reminderTime ?: LocalTime.of(9, 0)) }
    var showInWidget by remember(initial.id) { mutableStateOf(initial.showInWidget) }
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val interval = repeatIntervalText.toIntOrNull() ?: 0
                    val item = initial.copy(
                        title = title.trim(),
                        category = category,
                        date = date,
                        repeatType = repeatType,
                        repeatInterval = if (repeatType == RepeatType.CUSTOM) max(1, interval) else 0,
                        enableReminder = enableReminder,
                        reminderTime = if (enableReminder) reminderTime else null,
                        showInWidget = showInWidget,
                    )
                    if (item.title.isNotBlank()) onSave(item)
                }
            ) {
                Text(UiText.save())
            }
        },
        dismissButton = {
            Row {
                if (initial.id != 0L) {
                    TextButton(onClick = { onDelete(initial.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(UiText.delete())
                    }
                }
                TextButton(onClick = onDismiss) { Text(UiText.cancel()) }
            }
        },
        title = { Text(UiText.editEventTitle(initial.id == 0L)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(UiText.eventName()) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Text(UiText.category(), fontWeight = FontWeight.Bold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EventCategory.entries.forEach { item ->
                        FilterChip(
                            selected = category == item,
                            onClick = { category = item },
                            label = { Text(item.label) }
                        )
                    }
                }

                Text(UiText.date(), fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = {
                    DatePickerDialog(
                        context,
                        { _, year, month, day ->
                            date = LocalDate.of(year, month + 1, day)
                        },
                        date.year,
                        date.monthValue - 1,
                        date.dayOfMonth,
                    ).show()
                }) {
                    Text(date.format(dateFormatter))
                }

                Text(UiText.repeatRule(), fontWeight = FontWeight.Bold)
                Box {
                    OutlinedButton(onClick = { expanded = true }) {
                        Text(repeatType.label)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        RepeatType.entries.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item.label) },
                                onClick = {
                                    repeatType = item
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                if (repeatType == RepeatType.CUSTOM) {
                    OutlinedTextField(
                        value = repeatIntervalText,
                        onValueChange = { repeatIntervalText = it.filter { ch -> ch.isDigit() } },
                        label = { Text(UiText.repeatEveryXDays()) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = enableReminder, onCheckedChange = { enableReminder = it })
                    Text(UiText.enableReminder())
                    Spacer(modifier = Modifier.weight(1f))
                    if (enableReminder) {
                        OutlinedButton(onClick = {
                            TimePickerDialog(
                                context,
                                { _, hour, minute -> reminderTime = LocalTime.of(hour, minute) },
                                reminderTime.hour,
                                reminderTime.minute,
                                true,
                            ).show()
                        }) {
                            Text(reminderTime.toString())
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = showInWidget, onCheckedChange = { showInWidget = it })
                    Text(UiText.showInWidget())
                }
            }
        }
    )
}

@Composable
private fun SettingsDialog(
    onDismiss: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onDriveBackup: () -> Unit,
    onDriveRestore: () -> Unit,
    onDeleteAll: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(UiText.settings()) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(
                    tonalElevation = 2.dp,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(UiText.localData(), fontWeight = FontWeight.Bold)
                        Text(
                            UiText.localDataDescription(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FilledTonalButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                            Text(UiText.importCsv())
                        }
                        Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
                            Text(UiText.exportCsv())
                        }
                        OutlinedButton(
                            onClick = onDeleteAll,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(UiText.deleteAllData())
                        }
                    }
                }

                Surface(
                    tonalElevation = 2.dp,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(UiText.googleDrive(), fontWeight = FontWeight.Bold)
                        Text(
                            UiText.googleDriveDescription(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FilledTonalButton(onClick = onDriveBackup, modifier = Modifier.fillMaxWidth()) {
                            Text(UiText.googleDriveBackup())
                        }
                        Button(onClick = onDriveRestore, modifier = Modifier.fillMaxWidth()) {
                            Text(UiText.googleDriveRestore())
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(UiText.close()) }
        }
    )
}
