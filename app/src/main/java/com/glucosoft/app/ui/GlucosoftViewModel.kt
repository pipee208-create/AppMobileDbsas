package com.glucosoft.app.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.room.withTransaction
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.glucosoft.app.data.BackupManager
import com.glucosoft.app.data.BackupOperationResult
import com.glucosoft.app.data.GlucosoftDatabase
import com.glucosoft.app.data.GlucosoftRepository
import com.glucosoft.app.model.BiomedicalCatalog
import com.glucosoft.app.model.BiomedicalDeviceId
import com.glucosoft.app.model.CaptureMethod
import com.glucosoft.app.model.DateRangeFilter
import com.glucosoft.app.model.EmergencyContact
import com.glucosoft.app.model.GlucoseReading
import com.glucosoft.app.model.GlucoseUnit
import com.glucosoft.app.model.LocalProfile
import com.glucosoft.app.model.ManualEntryDraft
import com.glucosoft.app.model.MeasurementContext
import com.glucosoft.app.model.PatientTab
import com.glucosoft.app.model.ProfileReminderConfig
import com.glucosoft.app.model.TextScaleMode
import com.glucosoft.app.service.BleGlucometerManager
import com.glucosoft.app.service.ClinicalPdfReportGenerator
import com.glucosoft.app.service.DiscoveredBleDevice
import com.glucosoft.app.service.ReminderManager
import com.glucosoft.app.util.GlucoseMath
import com.glucosoft.app.util.LocalCivilDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GlucosoftViewModel(
    private val repository: GlucosoftRepository,
    val bleManager: BleGlucometerManager,
    private val appContext: Context
) : ViewModel() {

    val profiles: StateFlow<List<LocalProfile>> = repository.profilesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val readings: StateFlow<List<GlucoseReading>> = repository.readingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reminders: StateFlow<List<ProfileReminderConfig>> = repository.remindersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeProfileId = MutableStateFlow(repository.getActiveProfileId())
    val activeProfileId: StateFlow<String> = _activeProfileId.asStateFlow()

    private val _activeTab = MutableStateFlow(PatientTab.HOME)
    val activeTab: StateFlow<PatientTab> = _activeTab.asStateFlow()

    private val _isDark = MutableStateFlow(repository.isDarkTheme())
    val isDark: StateFlow<Boolean> = _isDark.asStateFlow()

    private val _textScale = MutableStateFlow(repository.getTextScaleMode())
    val textScale: StateFlow<TextScaleMode> = _textScale.asStateFlow()

    private val _showGuide = MutableStateFlow(!repository.isOnboardingDone())
    val showGuide: StateFlow<Boolean> = _showGuide.asStateFlow()

    private val _rangeFilter = MutableStateFlow(DateRangeFilter.DAYS_14)
    val rangeFilter: StateFlow<DateRangeFilter> = _rangeFilter.asStateFlow()

    private val _contextFilter = MutableStateFlow<MeasurementContext?>(null)
    val contextFilter: StateFlow<MeasurementContext?> = _contextFilter.asStateFlow()

    private val _actionFeedback = MutableStateFlow<String?>(null)
    val actionFeedback: StateFlow<String?> = _actionFeedback.asStateFlow()

    private val _isMeasureChoiceOpen = MutableStateFlow(false)
    val isMeasureChoiceOpen: StateFlow<Boolean> = _isMeasureChoiceOpen.asStateFlow()

    private val _isManualSheetOpen = MutableStateFlow(false)
    val isManualSheetOpen: StateFlow<Boolean> = _isManualSheetOpen.asStateFlow()

    private val _preloadedManualDate = MutableStateFlow<LocalCivilDate?>(null)
    val preloadedManualDate: StateFlow<LocalCivilDate?> = _preloadedManualDate.asStateFlow()

    private val _isBleSheetOpen = MutableStateFlow(false)
    val isBleSheetOpen: StateFlow<Boolean> = _isBleSheetOpen.asStateFlow()

    private val _defaultBleContext = MutableStateFlow(MeasurementContext.FASTING)
    val defaultBleContext: StateFlow<MeasurementContext> = _defaultBleContext.asStateFlow()

    private val _manualDrafts = MutableStateFlow<Map<String, ManualEntryDraft>>(emptyMap())
    val manualDrafts: StateFlow<Map<String, ManualEntryDraft>> = _manualDrafts.asStateFlow()

    private val _pendingReminderProfileId = MutableStateFlow<String?>(null)

    private var feedbackJob: Job? = null

    init {
        ReminderManager.ensureNotificationChannel(appContext)

        viewModelScope.launch {
            profiles.collect { list ->
                val pendingRemProfId = _pendingReminderProfileId.value
                if (pendingRemProfId != null && list.any { it.id == pendingRemProfId }) {
                    _pendingReminderProfileId.value = null
                    _activeProfileId.value = pendingRemProfId
                    repository.setActiveProfileId(pendingRemProfId)
                    _activeTab.value = PatientTab.HOME
                    _preloadedManualDate.value = null
                    _isManualSheetOpen.value = true
                } else {
                    val currentId = _activeProfileId.value
                    if (list.isNotEmpty() && list.none { it.id == currentId }) {
                        val firstId = list.first().id
                        _activeProfileId.value = firstId
                        repository.setActiveProfileId(firstId)
                    }
                }
            }
        }

        bleManager.onReadingReceived = { parsed, session ->
            val currentProf = getActiveProfile()
            if (currentProf != null) {
                val candidate = GlucoseReading(
                    id = "rd_ble_${System.currentTimeMillis()}",
                    profileId = currentProf.id,
                    valueMgDl = parsed.valueMgDl,
                    originalUnit = currentProf.unitPreference,
                    timestamp = parsed.timestampMs,
                    context = _defaultBleContext.value,
                    quickTags = emptyList(),
                    note = "Recibido automáticamente desde el glucómetro",
                    captureMethod = CaptureMethod.BLUETOOTH,
                    deviceModel = session.matchedModelName,
                    deviceId = session.matchedModelId,
                    deviceInfoSummary = "Bluetooth (${session.deviceName})"
                )
                val existingForProfile = readings.value.filter { it.profileId == currentProf.id }
                val dedup = GlucoseMath.deduplicateIncomingReadings(existingForProfile, listOf(candidate))
                if (dedup.uniqueReadings.isNotEmpty()) {
                    viewModelScope.launch {
                        repository.saveReading(dedup.uniqueReadings.first())
                        notify(
                            "Medición recibida de ${session.matchedModelName}: " +
                                "${GlucoseMath.formatGlucoseValue(parsed.valueMgDl, currentProf.unitPreference)} " +
                                currentProf.unitPreference.symbol
                        )
                    }
                } else {
                    notify("La lectura recibida de ${session.matchedModelName} ya estaba en tu historial.")
                }
            }
        }
    }

    fun getActiveProfile(): LocalProfile? {
        val list = profiles.value
        return list.find { it.id == _activeProfileId.value } ?: list.firstOrNull()
    }

    fun notify(message: String) {
        _actionFeedback.value = message
        feedbackJob?.cancel()
        feedbackJob = viewModelScope.launch {
            delay(5000L)
            if (_actionFeedback.value == message) {
                _actionFeedback.value = null
            }
        }
    }

    fun selectTab(tab: PatientTab) {
        _activeTab.value = tab
    }

    fun selectProfile(profileId: String) {
        _activeProfileId.value = profileId
        repository.setActiveProfileId(profileId)
        val prof = profiles.value.find { it.id == profileId }
        if (prof != null) {
            notify("Cambiaste al perfil de ${prof.name}.")
        }
    }

    fun toggleTheme() {
        val next = !_isDark.value
        _isDark.value = next
        repository.setDarkTheme(next)
    }

    fun toggleTextScale() {
        val next = if (_textScale.value == TextScaleMode.NORMAL) {
            TextScaleMode.LARGE
        } else {
            TextScaleMode.NORMAL
        }
        _textScale.value = next
        repository.setTextScaleMode(next)
    }

    fun openGuide() {
        _showGuide.value = true
    }

    fun closeGuide() {
        _showGuide.value = false
        repository.setOnboardingDone(true)
    }

    fun setRangeFilter(filter: DateRangeFilter) {
        _rangeFilter.value = filter
    }

    fun setContextFilter(context: MeasurementContext?) {
        _contextFilter.value = context
    }

    fun setMeasureChoiceOpen(open: Boolean) {
        _isMeasureChoiceOpen.value = open
    }

    fun setManualSheetOpen(open: Boolean) {
        if (!open) {
            _preloadedManualDate.value = null
        }
        _isManualSheetOpen.value = open
    }

    /**
     * Abre el formulario manual desde «Mi semana» precargando la fecha civil del día elegido.
     * No permite registrar en días futuros.
     */
    fun openManualMeasurementForDate(selectedDate: LocalCivilDate): Boolean {
        if (GlucoseMath.isCivilDateInFuture(selectedDate)) {
            notify("No se pueden registrar mediciones en un día futuro.")
            return false
        }
        _preloadedManualDate.value = selectedDate
        _isManualSheetOpen.value = true
        return true
    }

    /**
     * Maneja la acción «Registrar ahora» de una notificación de recordatorio:
     * cambia al perfil del recordatorio y abre el formulario manual vacío sin guardar ningún valor por sí sola.
     */
    fun handleReminderRegisterNowAction(profileId: String) {
        if (profileId.isBlank()) return
        ReminderManager.dismissNotificationForProfile(appContext, profileId)
        val existing = profiles.value.find { it.id == profileId }
        if (existing != null) {
            _activeProfileId.value = existing.id
            repository.setActiveProfileId(existing.id)
            _activeTab.value = PatientTab.HOME
            _preloadedManualDate.value = null
            _isManualSheetOpen.value = true
            notify("Formulario abierto para registrar la medición de ${existing.name}.")
        } else {
            _pendingReminderProfileId.value = profileId
            _activeProfileId.value = profileId
            repository.setActiveProfileId(profileId)
            _activeTab.value = PatientTab.HOME
            _preloadedManualDate.value = null
            _isManualSheetOpen.value = true
        }
    }

    fun setBleSheetOpen(open: Boolean) {
        _isBleSheetOpen.value = open
        if (!open) {
            bleManager.stopScan()
        }
    }

    fun setDefaultBleContext(context: MeasurementContext) {
        _defaultBleContext.value = context
    }

    fun updateManualDraft(draft: ManualEntryDraft) {
        val current = _manualDrafts.value.toMutableMap()
        if (draft.hasAuthenticContent) {
            current[draft.profileId] = draft
        } else {
            current.remove(draft.profileId)
        }
        _manualDrafts.value = current
    }

    fun clearManualDraft(profileId: String) {
        val current = _manualDrafts.value.toMutableMap()
        current.remove(profileId)
        _manualDrafts.value = current
    }

    fun createProfile(name: String, unit: GlucoseUnit) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val currentList = profiles.value
        if (currentList.size >= BiomedicalCatalog.MAX_LOCAL_PROFILES) {
            notify("Ya tienes el máximo de 3 perfiles en este dispositivo.")
            return
        }
        val colorHex = BiomedicalCatalog.PROFILE_COLORS[currentList.size % BiomedicalCatalog.PROFILE_COLORS.size]
        val newProfile = LocalProfile(
            id = "prof_${System.currentTimeMillis()}",
            name = trimmed,
            unitPreference = unit,
            targetMinMgDl = 70,
            targetMaxMgDl = 140,
            accentHex = colorHex,
            createdAt = System.currentTimeMillis()
        )
        viewModelScope.launch {
            repository.saveProfile(newProfile)
            _activeProfileId.value = newProfile.id
            repository.setActiveProfileId(newProfile.id)
            notify("Perfil de $trimmed creado y guardado localmente.")
        }
    }

    fun updateProfile(
        profileId: String,
        name: String,
        unit: GlucoseUnit,
        targetMinMgDl: Int,
        targetMaxMgDl: Int
    ) {
        val existing = profiles.value.find { it.id == profileId } ?: return
        val updated = existing.copy(
            name = name.trim().ifEmpty { existing.name },
            unitPreference = unit,
            targetMinMgDl = targetMinMgDl.coerceIn(45, 200),
            targetMaxMgDl = targetMaxMgDl.coerceIn(targetMinMgDl + 20, 350),
            updatedAt = System.currentTimeMillis()
        )
        viewModelScope.launch {
            repository.saveProfile(updated)
            notify("Cambios guardados en el perfil de ${updated.name}.")
        }
    }

    fun changeProfileUnit(profileId: String, unit: GlucoseUnit) {
        val existing = profiles.value.find { it.id == profileId } ?: return
        val updated = existing.copy(
            unitPreference = unit,
            updatedAt = System.currentTimeMillis()
        )
        viewModelScope.launch {
            repository.saveProfile(updated)
            notify("Unidad cambiada a ${unit.symbol} para ${existing.name}.")
        }
    }

    fun updateEmergencyContact(profileId: String, contact: EmergencyContact) {
        val existing = profiles.value.find { it.id == profileId } ?: return
        val updated = existing.copy(
            emergencyContact = contact,
            updatedAt = System.currentTimeMillis()
        )
        viewModelScope.launch {
            repository.saveProfile(updated)
            notify("Contacto de emergencia (${contact.name}) guardado correctamente.")
        }
    }

    fun deleteProfile(profileId: String) {
        val existing = profiles.value.find { it.id == profileId } ?: return
        clearManualDraft(profileId)
        ReminderManager.cancelProfileReminder(appContext, profileId)
        viewModelScope.launch {
            repository.deleteProfileAndReadings(profileId)
            notify("Se eliminó el perfil de ${existing.name} y sus mediciones.")
        }
    }

    fun saveProfileReminder(
        profileId: String,
        enabled: Boolean,
        hour: Int,
        minute: Int
    ) {
        val profile = profiles.value.find { it.id == profileId } ?: return
        val config = ProfileReminderConfig(
            profileId = profileId,
            enabled = enabled,
            hour = hour.coerceIn(0, 23),
            minute = minute.coerceIn(0, 59),
            snoozedUntilMs = null
        )
        viewModelScope.launch {
            repository.saveReminder(config)
            ReminderManager.syncProfileReminderSchedule(
                context = appContext,
                config = config,
                profileName = profile.name
            )
            if (enabled) {
                val availability = ReminderManager.checkNotificationAvailability(appContext)
                val warning = availability.honestDiagnosticMessage()
                if (warning != null) {
                    notify("Horario guardado (${config.formattedTime}), pero atención: $warning")
                } else {
                    notify("Recordatorio diario activado a las ${config.formattedTime} para ${profile.name}.")
                }
            } else {
                notify("Recordatorio diario desactivado para ${profile.name}.")
            }
        }
    }

    fun triggerTestReminder(profileId: String) {
        val profile = profiles.value.find { it.id == profileId } ?: return
        val availability = ReminderManager.checkNotificationAvailability(appContext)
        val diagnostic = availability.honestDiagnosticMessage()
        if (diagnostic != null) {
            notify(diagnostic)
            return
        }
        val posted = ReminderManager.showReminderNotification(
            context = appContext,
            profileId = profile.id,
            profileName = profile.name,
            isTestNotification = true
        )
        if (posted) {
            notify("Aviso de prueba enviado para ${profile.name}. Revisa tus notificaciones.")
        } else {
            notify("No se pudo mostrar el aviso de prueba. Verifica que las notificaciones estén activas en Android.")
        }
    }

    fun exportEncryptedBackup(
        targetUri: Uri,
        password: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val (ok, msg) = withContext(Dispatchers.IO) {
                try {
                    val allProfiles = repository.getAllProfilesSnapshot()
                    val allReadings = repository.getAllReadingsSnapshot()
                    val allReminders = repository.getAllRemindersSnapshot()
                    val snapshot = BackupManager.buildSnapshotFromLocalData(
                        profiles = allProfiles,
                        readings = allReadings,
                        reminders = allReminders,
                        activeProfileId = _activeProfileId.value,
                        isDarkTheme = _isDark.value,
                        textScaleMode = _textScale.value,
                        baseFilesDir = appContext.filesDir
                    )
                    when (val encrypted = BackupManager.createEncryptedBackupBytes(password, snapshot)) {
                        is BackupOperationResult.Failure -> false to encrypted.userMessage
                        is BackupOperationResult.Success -> {
                            val written = appContext.contentResolver.openOutputStream(targetUri)?.use { out ->
                                out.write(encrypted.value)
                                out.flush()
                                true
                            } ?: false
                            if (written) {
                                val photosCount = snapshot.photosByReadingId.size
                                true to "Copia de seguridad cifrada guardada correctamente (${allProfiles.size} perfil(es), ${allReadings.size} medición(es) y $photosCount foto(s))."
                            } else {
                                false to "No se pudo escribir en el archivo elegido. Intenta elegir otra carpeta."
                            }
                        }
                    }
                } catch (e: Exception) {
                    false to "Error al guardar la copia de seguridad: ${e.message ?: "archivo no accesible"}."
                }
            }
            notify(msg)
            onResult(ok, msg)
        }
    }

    fun restoreEncryptedBackup(
        sourceUri: Uri,
        password: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val (ok, msg) = withContext(Dispatchers.IO) {
                try {
                    val fileResult = appContext.contentResolver.openInputStream(sourceUri)?.use { input ->
                        BackupManager.readBoundedInputStream(input)
                    } ?: return@withContext false to "No se pudo abrir la copia seleccionada."
                    val encryptedBytes = when (fileResult) {
                        is BackupOperationResult.Failure -> return@withContext false to fileResult.userMessage
                        is BackupOperationResult.Success -> fileResult.value
                    }
                    val database = GlucosoftDatabase.getInstance(appContext)
                    val restoreResult = BackupManager.executeTransactionalRestore(
                        password = password,
                        encryptedBytes = encryptedBytes,
                        currentActiveProfileId = _activeProfileId.value,
                        baseFilesDir = appContext.filesDir,
                        runInSingleTransaction = { apply -> database.withTransaction { apply() } },
                        readExistingProfiles = repository::getAllProfilesSnapshot,
                        readExistingReadings = repository::getAllReadingsSnapshot,
                        readExistingReminders = repository::getAllRemindersSnapshot,
                        writeMergedProfiles = repository::saveBatchProfiles,
                        writeMergedReadings = repository::saveBatchReadings,
                        writeMergedReminders = repository::saveBatchReminders
                    )

                    when (restoreResult) {
                        is BackupOperationResult.Failure -> false to restoreResult.userMessage
                        is BackupOperationResult.Success -> {
                            val merged = restoreResult.value
                            repository.setActiveProfileId(merged.activeProfileId)
                            repository.setDarkTheme(merged.isDarkTheme)
                            repository.setTextScaleMode(merged.textScaleMode)

                            val profileNamesById = merged.profilesToSave.associate { it.id to it.name }
                            for (reminder in merged.remindersToSave) {
                                val pName = profileNamesById[reminder.profileId] ?: "Paciente"
                                ReminderManager.syncProfileReminderSchedule(
                                    context = appContext,
                                    config = reminder,
                                    profileName = pName
                                )
                            }

                            withContext(Dispatchers.Main) {
                                _activeProfileId.value = merged.activeProfileId
                                _isDark.value = merged.isDarkTheme
                                _textScale.value = merged.textScaleMode
                            }
                            true to merged.summary.message
                        }
                    }
                } catch (e: Exception) {
                    false to "No se pudo recuperar el archivo seleccionado: ${e.message ?: "error de lectura"}."
                }
            }
            notify(msg)
            onResult(ok, msg)
        }
    }

    fun addManualReading(
        valueMgDl: Int,
        originalUnit: GlucoseUnit,
        timestampMs: Long,
        context: MeasurementContext,
        deviceId: BiomedicalDeviceId,
        customDeviceModel: String,
        quickTags: List<String>,
        note: String,
        photoPath: String? = null
    ) {
        val active = getActiveProfile() ?: return
        val clamped = valueMgDl.coerceIn(25, 600)
        val deviceModel = if (deviceId == BiomedicalDeviceId.Otro_Compatible) {
            customDeviceModel.trim().ifEmpty { "Registro manual sin equipo" }
        } else {
            BiomedicalCatalog.specForId(deviceId).displayName
        }

        val reading = GlucoseReading(
            id = "rd_${System.currentTimeMillis()}",
            profileId = active.id,
            valueMgDl = clamped,
            originalUnit = originalUnit,
            timestamp = timestampMs,
            context = context,
            quickTags = quickTags,
            note = note.trim(),
            captureMethod = CaptureMethod.MANUAL,
            deviceModel = deviceModel,
            deviceId = deviceId,
            photoPath = photoPath?.takeIf { it.isNotBlank() }
        )

        clearManualDraft(active.id)
        _preloadedManualDate.value = null
        viewModelScope.launch {
            repository.saveReading(reading)
            val formatted = GlucoseMath.formatGlucoseValue(clamped, active.unitPreference)
            notify("Medición de $formatted ${active.unitPreference.symbol} guardada en el perfil de ${active.name}.")
        }
    }

    fun deleteReading(readingId: String) {
        viewModelScope.launch {
            repository.deleteReading(readingId)
            notify("Medición eliminada del historial.")
        }
    }

    fun exportPdfReport(
        readingsToExport: List<GlucoseReading>,
        rangeLabel: String,
        shareViaSystem: Boolean
    ) {
        val active = getActiveProfile()
        if (active == null) {
            notify("Primero crea un perfil para generar un reporte PDF.")
            return
        }
        if (readingsToExport.isEmpty()) {
            notify("Aún no hay mediciones en este filtro para generar un reporte PDF.")
            return
        }

        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    ClinicalPdfReportGenerator.exportClinicalPdfReport(
                        context = appContext,
                        profile = active,
                        readings = readingsToExport,
                        rangeLabel = rangeLabel,
                        shareIfAvailable = shareViaSystem
                    )
                }
                if (result.sharedViaSystem) {
                    notify("Reporte PDF compartido (${result.fileName}).")
                } else {
                    notify("Reporte PDF generado (${result.fileName}) con ${readingsToExport.size} mediciones.")
                }
            } catch (_: Exception) {
                notify("No se pudo generar el PDF en este momento. Intenta de nuevo.")
            }
        }
    }

    fun connectBleDevice(device: DiscoveredBleDevice) {
        bleManager.connectToDevice(device)
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val db = GlucosoftDatabase.getInstance(context)
            val repo = GlucosoftRepository(db.glucosoftDao(), context)
            val ble = BleGlucometerManager(context.applicationContext)
            return GlucosoftViewModel(repo, ble, context.applicationContext) as T
        }
    }
}
