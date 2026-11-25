package com.glucosoft.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.glucosoft.app.model.BiomedicalCatalog
import com.glucosoft.app.model.BiomedicalDeviceId
import com.glucosoft.app.model.GlucoseSeverity
import com.glucosoft.app.model.GlucoseUnit
import com.glucosoft.app.model.LocalProfile
import com.glucosoft.app.model.ManualEntryDraft
import com.glucosoft.app.model.MeasurementContext
import com.glucosoft.app.service.BleGlucometerManager
import com.glucosoft.app.service.ConnectedMeterSession
import com.glucosoft.app.service.DiscoveredBleDevice
import com.glucosoft.app.ui.theme.JetBrainsMonoFamily
import com.glucosoft.app.ui.theme.rememberStatusColorPair
import com.glucosoft.app.util.GlucoseMath
import com.glucosoft.app.util.LocalCivilDate
import com.glucosoft.app.util.MealPhotoStorage

@Composable
fun MeasureChoiceDialog(
    isOpen: Boolean,
    activeProfileName: String,
    connectedSession: ConnectedMeterSession?,
    onDismiss: () -> Unit,
    onChooseManual: () -> Unit,
    onChooseBluetooth: () -> Unit
) {
    if (!isOpen) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Registrar medición",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = "Se guardará en: $activeProfileName",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Elige cómo deseas anotar tu lectura de glucosa:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = onChooseManual,
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .testTag("choice_manual_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.EditNote,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Registrar a mano",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Escribir el número del medidor en pantalla amplia",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                OutlinedButton(
                    onClick = onChooseBluetooth,
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .testTag("choice_bluetooth_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Bluetooth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (connectedSession != null) {
                                "Recibir de ${connectedSession.matchedModelName}"
                            } else {
                                "Conectar medidor Bluetooth"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Buscar un glucómetro físico cercano",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cancelar", style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}

/**
 * Pantalla amplia a pantalla completa para registro manual:
 * - Una medición manual nueva empieza vacía (""), también después de cambiar de unidad en vacío.
 * - Muestra «Escribe el número que ves en tu medidor» y habilita «Guardar medición» solo cuando la persona ingresa un valor válido.
 * - Si existe un borrador auténtico de ese perfil (escrito previamente por la persona sin guardar), lo identifica explícitamente como borrador con opción de descartarlo.
 * - Antes de guardar utiliza «Perfil activo» y «Se guardará en», reservando la confirmación para después de guardar.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManualEntryFullScreenPanel(
    isOpen: Boolean,
    activeProfile: LocalProfile,
    existingDraft: ManualEntryDraft?,
    connectedSession: ConnectedMeterSession?,
    preloadedCivilDate: LocalCivilDate? = null,
    onUpdateDraft: (ManualEntryDraft) -> Unit,
    onClearDraft: (String) -> Unit,
    onDismiss: () -> Unit,
    onSwitchToBluetooth: () -> Unit,
    onSaveReading: (
        valueMgDl: Int,
        originalUnit: GlucoseUnit,
        timestampMs: Long,
        context: MeasurementContext,
        deviceId: BiomedicalDeviceId,
        customDeviceModel: String,
        quickTags: List<String>,
        note: String,
        photoPath: String?
    ) -> Unit
) {
    if (!isOpen) return

    val context = LocalContext.current
    val authenticDraft = remember(activeProfile.id) {
        existingDraft?.takeIf { it.profileId == activeProfile.id && it.hasAuthenticContent }
    }

    var isRestoredDraft by remember(activeProfile.id) {
        mutableStateOf(authenticDraft != null)
    }
    var entryUnit by remember(activeProfile.id) {
        mutableStateOf(authenticDraft?.unit ?: activeProfile.unitPreference)
    }
    // Una medición manual nueva empieza vacía ("") a menos que exista un borrador auténtico escrito por el usuario
    var valueInput by remember(activeProfile.id) {
        mutableStateOf(authenticDraft?.valueInput ?: "")
    }
    var selectedContext by remember(activeProfile.id) {
        mutableStateOf(authenticDraft?.context ?: MeasurementContext.FASTING)
    }
    var showOptionalDetails by remember(activeProfile.id) {
        mutableStateOf(!authenticDraft?.photoPath.isNullOrBlank())
    }

    var dateTimeInput by remember(activeProfile.id, preloadedCivilDate) {
        val baseInput = authenticDraft?.dateTimeInput?.takeIf { it.isNotBlank() }
            ?: GlucoseMath.formatManualDateTimeInput()
        val initialInput = if (preloadedCivilDate != null && !GlucoseMath.isCivilDateInFuture(preloadedCivilDate)) {
            GlucoseMath.applySelectedCivilDatePreservingTime(
                currentDateTimeInput = baseInput,
                selectedDate = preloadedCivilDate
            )
        } else {
            baseInput
        }
        mutableStateOf(initialInput)
    }
    var dateModifiedByUser by remember(activeProfile.id, preloadedCivilDate) {
        mutableStateOf(authenticDraft?.dateModifiedByUser == true || preloadedCivilDate != null)
    }
    var isCalendarOpen by remember(activeProfile.id) {
        mutableStateOf(false)
    }

    var selectedDeviceId by remember(activeProfile.id, connectedSession) {
        mutableStateOf(
            authenticDraft?.deviceId
                ?: connectedSession?.matchedModelId
                ?: BiomedicalDeviceId.OKMeter_Match_II
        )
    }
    var customModelName by remember(activeProfile.id) {
        mutableStateOf(authenticDraft?.customModelName ?: "")
    }
    val selectedTags = remember(activeProfile.id) {
        mutableStateListOf<String>().apply {
            if (authenticDraft != null) {
                addAll(authenticDraft.quickTags)
            }
        }
    }
    var noteInput by remember(activeProfile.id) {
        mutableStateOf(authenticDraft?.note ?: "")
    }
    var photoPath by remember(activeProfile.id) {
        mutableStateOf(authenticDraft?.photoPath)
    }
    var isViewingFullPhoto by remember(activeProfile.id) {
        mutableStateOf(false)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { pickedUri ->
        if (pickedUri != null) {
            val savedPath = MealPhotoStorage.savePhotoFromUri(context, pickedUri)
            if (savedPath != null) {
                photoPath = savedPath
                showOptionalDetails = true
            }
        }
    }

    val allSpecs = remember {
        BiomedicalCatalog.BIOMEDICAL_DEVICES + BiomedicalCatalog.OTHER_DEVICE_SPEC
    }
    val currentSpec = allSpecs.find { it.id == selectedDeviceId } ?: BiomedicalCatalog.OTHER_DEVICE_SPEC

    val validation = remember(valueInput, entryUnit) {
        GlucoseMath.validateManualGlucoseInput(valueInput, entryUnit)
    }

    val parsedDateMs = remember(dateTimeInput) {
        GlucoseMath.parseManualDateTimeToEpochMsOrNull(dateTimeInput)
    }
    val currentCivilDate = remember(dateTimeInput) {
        GlucoseMath.extractCivilDateOrToday(dateTimeInput)
    }
    val isFutureCivilDate = remember(currentCivilDate) {
        GlucoseMath.isCivilDateInFuture(currentCivilDate)
    }
    val selectedDateSpanishLabel = remember(currentCivilDate) {
        GlucoseMath.formatSpanishCivilDateLabel(currentCivilDate)
    }
    val currentTimeLabel = remember(dateTimeInput) {
        val (h, m) = GlucoseMath.extractHourAndMinuteOrNow(dateTimeInput)
        String.format(java.util.Locale.US, "%02d:%02d", h, m)
    }
    val dateTimeError = when {
        parsedDateMs == null ->
            "Usa el formato AAAA-MM-DD HH:MM (ejemplo: ${GlucoseMath.formatManualDateTimeInput()}) o toca «Usar fecha y hora actual»."
        isFutureCivilDate ->
            "No se pueden registrar mediciones en un día futuro. Elige hoy o un día anterior en el calendario."
        else -> null
    }

    val canSave = validation.isValid && parsedDateMs != null && !isFutureCivilDate

    val persistDraftAndClose = {
        val currentDraft = ManualEntryDraft(
            profileId = activeProfile.id,
            valueInput = valueInput,
            unit = entryUnit,
            context = selectedContext,
            deviceId = selectedDeviceId,
            customModelName = customModelName,
            quickTags = selectedTags.toList(),
            note = noteInput,
            dateTimeInput = dateTimeInput,
            dateModifiedByUser = dateModifiedByUser,
            photoPath = photoPath
        )
        onUpdateDraft(currentDraft)
        onDismiss()
    }

    BackHandler { persistDraftAndClose() }

    val attemptSave = {
        val validMgDl = validation.parsedMgDl
        val validDateMs = parsedDateMs
        if (validation.isValid && validMgDl != null && validDateMs != null && !isFutureCivilDate) {
            onClearDraft(activeProfile.id)
            onSaveReading(
                validMgDl,
                entryUnit,
                validDateMs,
                selectedContext,
                selectedDeviceId,
                customModelName,
                selectedTags.toList(),
                noteInput,
                photoPath
            )
            onDismiss()
        } else if (validDateMs == null || isFutureCivilDate) {
            showOptionalDetails = true
        }
    }

    if (isCalendarOpen) {
        SpanishMonthlyCalendarDialog(
            initialSelectedDate = currentCivilDate,
            preservedTimeLabel = currentTimeLabel,
            onDismiss = {
                isCalendarOpen = false
            },
            onConfirmDate = { chosenDate ->
                dateTimeInput = GlucoseMath.applySelectedCivilDatePreservingTime(
                    currentDateTimeInput = dateTimeInput,
                    selectedDate = chosenDate
                )
                dateModifiedByUser = true
                isCalendarOpen = false
            }
        )
    }

    if (isViewingFullPhoto && !photoPath.isNullOrBlank()) {
        MealPhotoViewerDialog(
            photoPath = photoPath,
            onDismiss = { isViewingFullPhoto = false }
        )
    }

    val cautionColors = rememberStatusColorPair(GlucoseSeverity.CAUTION)
    val minAllowedLabel = if (entryUnit == GlucoseUnit.MMOL_L) "1.4" else "25"
    val maxAllowedLabel = if (entryUnit == GlucoseUnit.MMOL_L) "33.3" else "600"

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("manual_entry_dialog"),
        color = MaterialTheme.colorScheme.background
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    shadowElevation = 2.dp
                ) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = persistDraftAndClose,
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("close_manual_entry_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Volver", style = MaterialTheme.typography.labelLarge)
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Registrar a mano",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.semantics { heading() }
                            )
                            Text(
                                text = "Se guardará en: ${activeProfile.name}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            },
            bottomBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .navigationBarsPadding()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val summaryLine = when {
                            validation.isEmpty ->
                                "Escribe el número que ves en tu medidor · Se guardará en: ${activeProfile.name}"
                            validation.isValid ->
                                "${if (isRestoredDraft) "Borrador · " else ""}Se guardará en ${activeProfile.name}: ${valueInput.trim()} ${entryUnit.symbol} · ${selectedContext.label}"
                            else ->
                                "Revisa el número escrito (${minAllowedLabel}–${maxAllowedLabel} ${entryUnit.symbol}) · Se guardará en: ${activeProfile.name}"
                        }
                        Text(
                            text = summaryLine,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("manual_entry_summary_text")
                        )
                        Button(
                            onClick = attemptSave,
                            enabled = canSave,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .testTag("save_manual_reading_button")
                        ) {
                            Icon(imageVector = Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Guardar medición",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 640.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Si existe un borrador auténtico de este perfil, se identifica explícitamente como borrador
                    if (isRestoredDraft) {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = cautionColors.containerColor
                            ),
                            border = BorderStroke(1.5.dp, cautionColors.borderColor),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("manual_authentic_draft_banner")
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.EditNote,
                                        contentDescription = "Borrador",
                                        tint = cautionColors.contentColor
                                    )
                                    Text(
                                        text = "Borrador sin guardar de ${activeProfile.name}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = cautionColors.contentColor
                                    )
                                }
                                Text(
                                    text = "Recuperamos los datos que habías empezado a escribir en este perfil antes de cerrar.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = cautionColors.contentColor
                                )
                                OutlinedButton(
                                    onClick = {
                                        onClearDraft(activeProfile.id)
                                        valueInput = ""
                                        noteInput = ""
                                        customModelName = ""
                                        selectedTags.clear()
                                        entryUnit = activeProfile.unitPreference
                                        selectedContext = MeasurementContext.FASTING
                                        dateTimeInput = GlucoseMath.formatManualDateTimeInput()
                                        dateModifiedByUser = false
                                        isRestoredDraft = false
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("discard_manual_draft_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = null
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Descartar borrador y empezar vacío")
                                }
                            }
                        }
                    }

                    // Acceso rápido a Bluetooth si prefiere no escribir a mano (sin repetir el nombre del perfil ya visible arriba)
                    OutlinedButton(
                        onClick = {
                            persistDraftAndClose()
                            onSwitchToBluetooth()
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Bluetooth, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Conectar medidor por Bluetooth")
                    }

                    // 1. Valor y Unidad siempre identificables (empieza vacío)
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Escribe el número que ves en tu medidor",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .semantics { heading() }
                                    .testTag("manual_empty_prompt_text")
                            )

                            OutlinedTextField(
                                value = valueInput,
                                onValueChange = { valueInput = it },
                                label = { Text("Glucosa (${entryUnit.symbol})") },
                                placeholder = { Text("Escribe el número que ves en tu medidor") },
                                suffix = {
                                    Text(
                                        text = entryUnit.symbol,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                },
                                isError = !validation.isEmpty && !validation.isValid,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                textStyle = MaterialTheme.typography.displayLarge.copy(
                                    fontFamily = JetBrainsMonoFamily
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("manual_glucose_input")
                            )

                            // Error explicado junto al campo conservando lo escrito
                            if (!validation.isEmpty && validation.errorMessage != null) {
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .semantics { liveRegion = LiveRegionMode.Polite }
                                        .testTag("manual_value_error_text")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = "Error en el valor",
                                            tint = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Text(
                                            text = validation.errorMessage,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                GlucoseUnit.entries.forEach { u ->
                                    val isSelected = entryUnit == u
                                    if (isSelected) {
                                        Button(
                                            onClick = {
                                                valueInput = GlucoseMath.preserveInputOnUnitChange(valueInput)
                                                entryUnit = u
                                            },
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier
                                                .heightIn(min = 50.dp)
                                                .semantics {
                                                    selected = true
                                                    stateDescription = "Seleccionada"
                                                }
                                                .testTag("manual_unit_${u.name.lowercase()}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Seleccionada"
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = u.symbol,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontFamily = JetBrainsMonoFamily
                                            )
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = {
                                                valueInput = GlucoseMath.preserveInputOnUnitChange(valueInput)
                                                entryUnit = u
                                            },
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier
                                                .heightIn(min = 50.dp)
                                                .semantics {
                                                    selected = false
                                                    stateDescription = "No seleccionada"
                                                }
                                                .testTag("manual_unit_${u.name.lowercase()}")
                                        ) {
                                            Text(
                                                text = u.symbol,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontFamily = JetBrainsMonoFamily
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 1.b. Fecha de la medición: control grande y visible con icono de calendario antes de «¿En qué momento te mediste?»
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 72.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .clickable(role = Role.Button) {
                                isCalendarOpen = true
                            }
                            .semantics(mergeDescendants = true) {
                                role = Role.Button
                            }
                            .testTag("manual_measurement_date_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = "Calendario",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Fecha de la medición",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = selectedDateSpanishLabel,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.testTag("manual_measurement_date_label")
                                )
                                Text(
                                    text = "Hora conservada: $currentTimeLabel · Toca para abrir el calendario",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // 2. Momento de la medición: opciones grandes de icono y palabra, con selección visible (usando exactamente las opciones existentes)
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "¿En qué momento te mediste?",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.semantics { heading() }
                            )

                            MeasurementContext.entries.forEach { ctx ->
                                val isSelected = selectedContext == ctx
                                Card(
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                                        }
                                    ),
                                    border = BorderStroke(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 64.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .clickable(role = Role.RadioButton) {
                                            selectedContext = ctx
                                        }
                                        .semantics {
                                            selected = isSelected
                                            stateDescription = if (isSelected) "Seleccionado" else "No seleccionado"
                                        }
                                        .testTag("manual_context_${ctx.rawId}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        Icon(
                                            imageVector = contextIconFor(ctx),
                                            contentDescription = null,
                                            tint = if (isSelected) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.primary
                                            },
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Text(
                                                text = ctx.label,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                color = if (isSelected) {
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                }
                                            )
                                            Text(
                                                text = ctx.description,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = if (isSelected) {
                                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.88f)
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                        }
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Seleccionado",
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(26.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. Opcional agrupado en «Añadir detalles»
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showOptionalDetails = !showOptionalDetails },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 54.dp)
                                    .testTag("toggle_optional_details_button")
                            ) {
                                Icon(
                                    imageVector = if (showOptionalDetails) {
                                        Icons.Default.ExpandLess
                                    } else {
                                        Icons.Default.ExpandMore
                                    },
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (showOptionalDetails) {
                                        "Ocultar detalles opcionales"
                                    } else {
                                        "Añadir detalles (glucómetro, fecha, etiquetas y notas)"
                                    },
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }

                            Text(
                                text = "Equipo: ${currentSpec.displayName} · Fecha: $dateTimeInput",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            AnimatedVisibility(visible = showOptionalDetails) {
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                                    // Fecha y hora
                                    Text(
                                        text = "Fecha y hora de la medición",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    OutlinedTextField(
                                        value = dateTimeInput,
                                        onValueChange = {
                                            dateTimeInput = it
                                            dateModifiedByUser = true
                                        },
                                        label = { Text("Fecha y hora (AAAA-MM-DD HH:MM)") },
                                        isError = dateTimeError != null,
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("manual_datetime_input")
                                    )
                                    if (dateTimeError != null) {
                                        Text(
                                            text = dateTimeError,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            dateTimeInput = GlucoseMath.formatManualDateTimeInput()
                                            dateModifiedByUser = true
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("use_current_datetime_button")
                                    ) {
                                        Text("Usar fecha y hora actual")
                                    }

                                    // Modelo del glucómetro
                                    Text(
                                        text = "Modelo del glucómetro",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    allSpecs.forEach { spec ->
                                        val isDevSelected = selectedDeviceId == spec.id
                                        if (isDevSelected) {
                                            Button(
                                                onClick = { selectedDeviceId = spec.id },
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(min = 48.dp)
                                                    .semantics { selected = true }
                                                    .testTag("manual_device_${spec.id.rawId}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Seleccionado"
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = spec.displayName,
                                                    style = MaterialTheme.typography.labelLarge
                                                )
                                            }
                                        } else {
                                            OutlinedButton(
                                                onClick = { selectedDeviceId = spec.id },
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(min = 48.dp)
                                                    .semantics { selected = false }
                                                    .testTag("manual_device_${spec.id.rawId}")
                                            ) {
                                                Text(
                                                    text = spec.displayName,
                                                    style = MaterialTheme.typography.labelLarge
                                                )
                                            }
                                        }
                                    }

                                    if (selectedDeviceId == BiomedicalDeviceId.Otro_Compatible) {
                                        OutlinedTextField(
                                            value = customModelName,
                                            onValueChange = { customModelName = it },
                                            singleLine = true,
                                            label = { Text("Nombre del glucómetro (opcional)") },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("manual_custom_device_input")
                                        )
                                    }

                                    // Etiquetas rápidas
                                    Text(
                                        text = "Etiquetas rápidas (opcional)",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        BiomedicalCatalog.QUICK_TAGS.forEach { tag ->
                                            val active = selectedTags.contains(tag)
                                            if (active) {
                                                Button(
                                                    onClick = { selectedTags.remove(tag) },
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier
                                                        .heightIn(min = 48.dp)
                                                        .semantics { selected = true }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Seleccionado"
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(tag)
                                                }
                                            } else {
                                                OutlinedButton(
                                                    onClick = { selectedTags.add(tag) },
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier
                                                        .heightIn(min = 48.dp)
                                                        .semantics { selected = false }
                                                ) {
                                                    Text(tag)
                                                }
                                            }
                                        }
                                    }

                                    // Foto de comida opcional con el selector del sistema Android (sin permisos amplios de galería)
                                    Text(
                                        text = "Foto de tu comida (opcional)",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "Puedes adjuntar una foto de tu plato para recordar qué comiste. Se guarda una copia privada en tu teléfono.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                photoPickerLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 50.dp)
                                                .testTag("manual_attach_meal_photo_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PhotoCamera,
                                                contentDescription = null
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (photoPath.isNullOrBlank()) {
                                                    "Elegir foto de comida"
                                                } else {
                                                    "Cambiar foto de comida"
                                                },
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                        }
                                        if (!photoPath.isNullOrBlank()) {
                                            OutlinedButton(
                                                onClick = {
                                                    MealPhotoStorage.deletePhoto(context.filesDir, photoPath)
                                                    photoPath = null
                                                },
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier
                                                    .heightIn(min = 50.dp)
                                                    .testTag("manual_remove_meal_photo_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.DeleteOutline,
                                                    contentDescription = "Quitar foto"
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Quitar", style = MaterialTheme.typography.labelLarge)
                                            }
                                        }
                                    }

                                    val currentPhotoPath = photoPath
                                    if (!currentPhotoPath.isNullOrBlank()) {
                                        MealPhotoAttachmentCard(
                                            photoPath = currentPhotoPath,
                                            onOpenFullPhoto = { isViewingFullPhoto = true },
                                            modifier = Modifier.testTag("manual_meal_photo_preview_card")
                                        )
                                    }

                                    // Notas
                                    OutlinedTextField(
                                        value = noteInput,
                                        onValueChange = { noteInput = it },
                                        label = { Text("Notas personales (opcional)") },
                                        placeholder = { Text("Ej. Caminé 20 minutos antes de cenar...") },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("manual_note_input")
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

/**
 * Tarjeta compacta y accesible que muestra la miniatura de una foto de comida adjunta
 * y permite abrirla en tamaño completo al tocarla.
 */
@Composable
fun MealPhotoAttachmentCard(
    photoPath: String,
    onOpenFullPhoto: (String) -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String? = null
) {
    val context = LocalContext.current
    val imageBitmap = remember(photoPath) {
        MealPhotoStorage.decodeImageBitmapOrNull(context.filesDir, photoPath)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
        ),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
        modifier = modifier
            .then(if (!testTagPrefix.isNullOrBlank()) Modifier.testTag(testTagPrefix) else Modifier)
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button) {
                onOpenFullPhoto(photoPath)
            }
            .semantics(mergeDescendants = true) {
                role = Role.Button
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = "Miniatura de foto de comida",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = "Foto de comida adjunta",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Foto de comida adjunta",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Toca aquí para ver la foto completa",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Icon(
                imageVector = Icons.Default.ZoomIn,
                contentDescription = "Ver foto completa",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

/**
 * Visor ampliado para ver en detalle la foto de comida adjunta a una medición.
 */
@Composable
fun MealPhotoViewerDialog(
    photoPath: String?,
    onDismiss: () -> Unit
) {
    if (photoPath.isNullOrBlank()) return
    val context = LocalContext.current
    val imageBitmap = remember(photoPath) {
        MealPhotoStorage.decodeImageBitmapOrNull(context.filesDir, photoPath)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .padding(16.dp)
                .testTag("meal_photo_viewer_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Foto de la comida",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { heading() }
                )

                if (imageBitmap != null) {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = "Foto ampliada de la comida registrada",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 220.dp, max = 420.dp)
                            .clip(RoundedCornerShape(18.dp))
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Archivo de foto local ($photoPath)",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .testTag("close_meal_photo_viewer_button")
                ) {
                    Text("Cerrar foto", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/**
 * Calendario mensual visual en español para «Registrar a mano»:
 * - Se abre directamente sin necesidad de teclado.
 * - Inicia en la fecha actual del borrador (o hoy según el teléfono si es nueva).
 * - Permite cambiar de mes y de año, y elegir cualquier día en la cuadrícula mensual.
 * - «Aceptar» cambia únicamente el día civil conservando exactamente la hora y los minutos.
 * - «Cancelar» o «Atrás» cierra el calendario sin modificar el borrador.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpanishMonthlyCalendarDialog(
    initialSelectedDate: LocalCivilDate,
    preservedTimeLabel: String,
    onDismiss: () -> Unit,
    onConfirmDate: (LocalCivilDate) -> Unit
) {
    var pendingSelectedDate by remember(initialSelectedDate) {
        mutableStateOf(initialSelectedDate)
    }

    val monthTitle = remember(pendingSelectedDate.year, pendingSelectedDate.month) {
        GlucoseMath.formatSpanishMonthYearTitle(
            year = pendingSelectedDate.year,
            month = pendingSelectedDate.month
        )
    }
    val selectedDateFullLabel = remember(pendingSelectedDate) {
        GlucoseMath.formatSpanishCivilDateLabel(pendingSelectedDate)
    }
    val gridCells = remember(pendingSelectedDate.year, pendingSelectedDate.month) {
        GlucoseMath.buildSpanishMonthGridCells(
            year = pendingSelectedDate.year,
            month = pendingSelectedDate.month
        )
    }
    val weekRows = remember(gridCells) {
        gridCells.chunked(7)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .testTag("manual_spanish_calendar_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Fecha de la medición",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = selectedDateFullLabel,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("calendar_selected_date_summary")
                    )
                    Text(
                        text = "Se conservará la hora $preservedTimeLabel",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                // Cambio de año
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(
                        onClick = {
                            pendingSelectedDate = GlucoseMath.shiftCivilDateMonthYear(
                                date = pendingSelectedDate,
                                deltaYears = -1
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("calendar_prev_year_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Año anterior"
                        )
                        Text("Año", style = MaterialTheme.typography.labelLarge)
                    }

                    Text(
                        text = "Año ${pendingSelectedDate.year}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                            .testTag("calendar_year_label")
                    )

                    OutlinedButton(
                        onClick = {
                            pendingSelectedDate = GlucoseMath.shiftCivilDateMonthYear(
                                date = pendingSelectedDate,
                                deltaYears = 1
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("calendar_next_year_button")
                    ) {
                        Text("Año", style = MaterialTheme.typography.labelLarge)
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Año siguiente"
                        )
                    }
                }

                // Cambio de mes
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(
                        onClick = {
                            pendingSelectedDate = GlucoseMath.shiftCivilDateMonthYear(
                                date = pendingSelectedDate,
                                deltaMonths = -1
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("calendar_prev_month_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Mes anterior"
                        )
                        Text("Mes", style = MaterialTheme.typography.labelLarge)
                    }

                    Text(
                        text = monthTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                            .testTag("calendar_month_year_title")
                    )

                    OutlinedButton(
                        onClick = {
                            pendingSelectedDate = GlucoseMath.shiftCivilDateMonthYear(
                                date = pendingSelectedDate,
                                deltaMonths = 1
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("calendar_next_month_button")
                    ) {
                        Text("Mes", style = MaterialTheme.typography.labelLarge)
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Mes siguiente"
                        )
                    }
                }

                // Encabezado de días de la semana en español (Lun..Dom)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    GlucoseMath.SPANISH_WEEKDAY_SHORT_HEADERS.forEach { dayHeader ->
                        Text(
                            text = dayHeader,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 4.dp)
                        )
                    }
                }

                // Cuadrícula mensual de días
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    weekRows.forEach { week ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            week.forEach { dayNumber ->
                                if (dayNumber == null) {
                                    Spacer(
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                    )
                                } else {
                                    val isDaySelected = dayNumber == pendingSelectedDate.dayOfMonth
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isDaySelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                        },
                                        border = BorderStroke(
                                            width = if (isDaySelected) 2.dp else 1.dp,
                                            color = if (isDaySelected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.outline
                                            }
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable(role = Role.Button) {
                                                pendingSelectedDate = pendingSelectedDate.copy(
                                                    dayOfMonth = dayNumber
                                                )
                                            }
                                            .semantics {
                                                selected = isDaySelected
                                                stateDescription = if (isDaySelected) {
                                                    "Día $dayNumber seleccionado"
                                                } else {
                                                    "Día $dayNumber"
                                                }
                                            }
                                            .testTag("calendar_day_$dayNumber")
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 48.dp)
                                                .padding(vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = dayNumber.toString(),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = if (isDaySelected) {
                                                    FontWeight.Bold
                                                } else {
                                                    FontWeight.SemiBold
                                                },
                                                color = if (isDaySelected) {
                                                    MaterialTheme.colorScheme.onPrimary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                },
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                // Acciones Cancelar y Aceptar
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("calendar_cancel_button")
                    ) {
                        Text("Cancelar", style = MaterialTheme.typography.labelLarge)
                    }

                    Button(
                        onClick = { onConfirmDate(pendingSelectedDate) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("calendar_confirm_button")
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Aceptar", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

/**
 * Panel amplio de conexión Bluetooth con el glucómetro.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BleCaptureDialog(
    isOpen: Boolean,
    profileName: String,
    bleManager: BleGlucometerManager,
    defaultContext: MeasurementContext,
    onChangeDefaultContext: (MeasurementContext) -> Unit,
    onConnectDevice: (DiscoveredBleDevice) -> Unit,
    onOpenManualEntry: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    BackHandler { onDismiss() }

    val isScanning by bleManager.isScanning.collectAsState()
    val hasCompletedRealScan by bleManager.hasCompletedRealScan.collectAsState()
    val discoveredDevices by bleManager.discoveredDevices.collectAsState()
    val connectedSession by bleManager.connectedSession.collectAsState()
    val statusMessage by bleManager.statusMessage.collectAsState()
    val errorMessage by bleManager.errorMessage.collectAsState()
    val isSyncingMemory by bleManager.isSyncingMemory.collectAsState()

    var showCompatibilityList by remember { mutableStateOf(false) }

    LaunchedEffect(isOpen) {
        if (isOpen) {
            bleManager.resetSearchStateForDialogOpen()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        bleManager.startScan()
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("ble_capture_dialog"),
        color = MaterialTheme.colorScheme.background
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    shadowElevation = 2.dp
                ) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Volver", style = MaterialTheme.typography.labelLarge)
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Conectar medidor",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.semantics { heading() }
                            )
                            Text(
                                text = "Se guardará en: $profileName",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 640.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val context = LocalContext.current
                    val normalColors = rememberStatusColorPair(GlucoseSeverity.NORMAL)
                    val cautionColors = rememberStatusColorPair(GlucoseSeverity.CAUTION)
                    val session = connectedSession

                    if (session != null) {
                        var isBleDetailsExpanded by remember(session.address, session.discoveredServices) {
                            mutableStateOf(false)
                        }
                        var copiedProtocolFeedback by remember(session.address, session.discoveredServices) {
                            mutableStateOf(false)
                        }

                        val statusPair = if (session.isReadyForMeasurements) normalColors else cautionColors
                        val readinessHeading = if (session.isReadyForMeasurements) {
                            "Conectado · Listo para recibir mediciones"
                        } else {
                            "Conectado · Falta confirmar el canal de medición"
                        }

                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = statusPair.containerColor
                            ),
                            border = BorderStroke(1.5.dp, statusPair.borderColor),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ble_connected_session_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (session.isReadyForMeasurements) {
                                            Icons.Default.CheckCircle
                                        } else {
                                            Icons.Default.Warning
                                        },
                                        contentDescription = readinessHeading,
                                        tint = statusPair.contentColor
                                    )
                                    Text(
                                        text = "$readinessHeading (${session.matchedModelName})",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = statusPair.contentColor
                                    )
                                }
                                Text(
                                    text = "Equipo: ${session.deviceName} · ${session.connectionMessage}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = statusPair.contentColor
                                )

                                if (session.isReadyForMeasurements) {
                                    Text(
                                        text = "Momento predeterminado para lecturas recibidas: ${defaultContext.label}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = statusPair.contentColor
                                    )

                                    MeasurementContext.entries.forEach { ctx ->
                                        val isSel = defaultContext == ctx
                                        if (isSel) {
                                            Button(
                                                onClick = { onChangeDefaultContext(ctx) },
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(min = 48.dp)
                                                    .semantics { selected = true }
                                            ) {
                                                Icon(imageVector = Icons.Default.Check, contentDescription = "Seleccionado")
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(ctx.label)
                                            }
                                        } else {
                                            OutlinedButton(
                                                onClick = { onChangeDefaultContext(ctx) },
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(min = 48.dp)
                                                    .semantics { selected = false }
                                            ) {
                                                Text(ctx.label)
                                            }
                                        }
                                    }
                                } else {
                                    Text(
                                        text = "Este equipo solo expone servicios propietarios. No se realiza ninguna suscripción ni escritura automática hasta confirmar el canal de medición.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = statusPair.contentColor
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (session.supportsMemorySync) {
                                        Button(
                                            onClick = { bleManager.requestStoredMemoryRecords() },
                                            enabled = !isSyncingMemory,
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 52.dp)
                                        ) {
                                            Text(
                                                if (isSyncingMemory) "Leyendo memoria..." else "Traer tomas guardadas"
                                            )
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = { bleManager.disconnect() },
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 52.dp)
                                            .testTag("ble_disconnect_button")
                                    ) {
                                        Text("Desconectar")
                                    }
                                }
                            }
                        }

                        // Única ficha plegada «Detalles Bluetooth», visible también para equipo propietario/no reconocido conectado
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ble_details_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable(role = Role.Button) {
                                            isBleDetailsExpanded = !isBleDetailsExpanded
                                        }
                                        .padding(vertical = 8.dp, horizontal = 4.dp)
                                        .testTag("toggle_ble_details_button"),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Detalles Bluetooth",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${session.discoveredServices.size} servicio(s) GATT detectado(s) · Toca para ${if (isBleDetailsExpanded) "ocultar" else "ver"}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        imageVector = if (isBleDetailsExpanded) {
                                            Icons.Default.ExpandLess
                                        } else {
                                            Icons.Default.ExpandMore
                                        },
                                        contentDescription = if (isBleDetailsExpanded) {
                                            "Ocultar detalles Bluetooth"
                                        } else {
                                            "Mostrar detalles Bluetooth"
                                        },
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                AnimatedVisibility(visible = isBleDetailsExpanded) {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("ble_details_expanded_content")
                                    ) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                                        if (session.discoveredServices.isEmpty()) {
                                            Text(
                                                text = "Sin servicios GATT descubiertos.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        } else {
                                            session.discoveredServices.forEachIndexed { idx, srv ->
                                                Card(
                                                    shape = RoundedCornerShape(14.dp),
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                                    ),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column(
                                                        modifier = Modifier.padding(12.dp),
                                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Text(
                                                            text = "Servicio ${idx + 1}: ${srv.uuid}",
                                                            style = MaterialTheme.typography.labelLarge,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                        if (srv.characteristics.isEmpty()) {
                                                            Text(
                                                                text = "Característica: (ninguna)",
                                                                style = MaterialTheme.typography.labelMedium,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        } else {
                                                            srv.characteristics.forEach { ch ->
                                                                val propsText = if (ch.properties.isEmpty()) {
                                                                    "ninguna (read/write/write sin respuesta/notify/indicate)"
                                                                } else {
                                                                    ch.properties.joinToString(", ")
                                                                }
                                                                Text(
                                                                    text = "• Característica: ${ch.uuid}\n  Propiedades: $propsText",
                                                                    style = MaterialTheme.typography.labelMedium,
                                                                    color = MaterialTheme.colorScheme.onSurface
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                val sanitizedReport =
                                                    BleGlucometerManager.formatBluetoothProtocolDetailsForCopy(
                                                        session.discoveredServices
                                                    )
                                                val clipboard =
                                                    context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                                clipboard?.setPrimaryClip(
                                                    ClipData.newPlainText(
                                                        "Detalles Bluetooth GATT",
                                                        sanitizedReport
                                                    )
                                                )
                                                copiedProtocolFeedback = true
                                            },
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 50.dp)
                                                .testTag("copy_bluetooth_details_button")
                                        ) {
                                            Text(
                                                text = "Copiar datos Bluetooth",
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                        }

                                        if (copiedProtocolFeedback) {
                                            Text(
                                                text = "Datos copiados: incluye solo UUID de servicios, características y propiedades (sin MAC, nombres ni datos de salud).",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.testTag("copied_bluetooth_details_feedback")
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val eligibleDiscoveredDevices = remember(discoveredDevices) {
                        discoveredDevices.filter { dev ->
                            BiomedicalCatalog.resolveEligibleBluetoothConnectionSpec(
                                discoveredName = dev.advertisedName,
                                matchedSpec = dev.matchedSpec
                            ) != null
                        }
                    }
                    val confirmedBluetoothModels = remember {
                        BiomedicalCatalog.confirmedBluetoothModels()
                    }
                    val searchUiState = remember(
                        isScanning,
                        hasCompletedRealScan,
                        eligibleDiscoveredDevices.size,
                        connectedSession != null,
                        errorMessage,
                        statusMessage
                    ) {
                        BleGlucometerManager.resolveBleCaptureSearchUiState(
                            isScanning = isScanning,
                            hasCompletedRealScan = hasCompletedRealScan,
                            eligibleDevicesCount = eligibleDiscoveredDevices.size,
                            hasConnectedSession = connectedSession != null,
                            errorMessage = errorMessage,
                            statusMessage = statusMessage
                        )
                    }

                    if (searchUiState.showPrimarySearchCard) {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Búsqueda real de glucómetro Bluetooth",
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Text(
                                    text = "Enciende el Bluetooth de tu glucómetro y pulsa el botón para buscar. Las lecturas recibidas se guardarán en el perfil de $profileName.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Button(
                                    onClick = {
                                        if (bleManager.hasBlePermissions()) {
                                            bleManager.startScan()
                                        } else {
                                            permissionLauncher.launch(bleManager.requiredPermissions())
                                        }
                                    },
                                    enabled = searchUiState.primarySearchAction.enabled,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 56.dp)
                                        .testTag(searchUiState.primarySearchAction.testTag)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.BluetoothSearching,
                                        contentDescription = null
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = searchUiState.primarySearchAction.buttonLabel,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                        }
                    }

                    if (eligibleDiscoveredDevices.isNotEmpty()) {
                        Text(
                            text = "Equipos encontrados (${eligibleDiscoveredDevices.size}):",
                            style = MaterialTheme.typography.titleMedium
                        )
                        eligibleDiscoveredDevices.forEach { dev ->
                            val resolvedSpec = BiomedicalCatalog.resolveEligibleBluetoothConnectionSpec(
                                discoveredName = dev.advertisedName,
                                matchedSpec = dev.matchedSpec
                            )
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onConnectDevice(dev) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = dev.advertisedName,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = "${resolvedSpec?.displayName ?: "Medidor compatible"} · Señal ${dev.rssi} dBm",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Button(
                                        onClick = { onConnectDevice(dev) },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Text("Conectar")
                                    }
                                }
                            }
                        }
                    } else if (searchUiState.showEmptyScanResultCard) {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ble_no_compatible_devices_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = searchUiState.emptyScanTitle ?: "No encontramos un medidor compatible cerca",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Verifica que tu glucómetro con protocolo confirmado esté encendido y con Bluetooth activo, o registra tu medición a mano.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = {
                                        if (bleManager.hasBlePermissions()) {
                                            bleManager.startScan()
                                        } else {
                                            permissionLauncher.launch(bleManager.requiredPermissions())
                                        }
                                    },
                                    enabled = searchUiState.primarySearchAction.enabled,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 52.dp)
                                        .testTag(searchUiState.primarySearchAction.testTag)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.BluetoothSearching,
                                        contentDescription = null
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = searchUiState.primarySearchAction.buttonLabel,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                        }
                    }

                    if (!searchUiState.statusBannerMessage.isNullOrBlank()) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Text(
                                text = searchUiState.statusBannerMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }

                    if (!searchUiState.errorBannerMessage.isNullOrBlank()) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Aviso",
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = searchUiState.errorBannerMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onOpenManualEntry()
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 54.dp)
                            .testTag("ble_fallback_manual_button")
                    ) {
                        Icon(imageVector = Icons.Default.EditNote, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Registrar medición a mano",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    OutlinedButton(
                        onClick = { showCompatibilityList = !showCompatibilityList },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (showCompatibilityList) {
                                "Ocultar medidores con protocolo confirmado"
                            } else {
                                "Ver medidores con protocolo confirmado"
                            },
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    if (showCompatibilityList) {
                        confirmedBluetoothModels.forEach { spec ->
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = spec.displayName,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = spec.pendingTestBadgeLabel,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = spec.protocolNotes,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
