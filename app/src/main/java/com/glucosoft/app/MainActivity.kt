package com.glucosoft.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glucosoft.app.model.EmergencyContact
import com.glucosoft.app.model.PatientTab
import com.glucosoft.app.service.ReminderManager
import com.glucosoft.app.ui.GlucosoftViewModel
import com.glucosoft.app.ui.components.ActionFeedbackBanner
import com.glucosoft.app.ui.components.BleCaptureDialog
import com.glucosoft.app.ui.components.DisplayAndTextSettingsDialog
import com.glucosoft.app.ui.components.GlucosoftHeaderBar
import com.glucosoft.app.ui.components.GuidedOnboardingCard
import com.glucosoft.app.ui.components.ManualEntryFullScreenPanel
import com.glucosoft.app.ui.components.MeasureChoiceDialog
import com.glucosoft.app.ui.screens.DevicesScreen
import com.glucosoft.app.ui.screens.EmergencyCallConfirmDialog
import com.glucosoft.app.ui.screens.EmergencyScreen
import com.glucosoft.app.ui.screens.FirstProfileSetupCard
import com.glucosoft.app.ui.screens.HistoryScreen
import com.glucosoft.app.ui.screens.HomeScreen
import com.glucosoft.app.ui.screens.ProfilesScreen
import com.glucosoft.app.ui.theme.GlucosoftTheme
import com.glucosoft.app.util.GlucoseMath

class MainActivity : ComponentActivity() {

    private val viewModel: GlucosoftViewModel by viewModels {
        GlucosoftViewModel.Factory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleReminderIntentIfPresent(intent)
        setContent {
            val isDark by viewModel.isDark.collectAsStateWithLifecycle()
            val textScale by viewModel.textScale.collectAsStateWithLifecycle()

            GlucosoftTheme(
                darkTheme = isDark,
                textScaleMode = textScale
            ) {
                GlucosoftAppContent(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleReminderIntentIfPresent(intent)
    }

    private fun handleReminderIntentIfPresent(intent: Intent?) {
        val targetProfileId = ReminderManager.extractProfileIdFromOpenIntent(intent) ?: return
        viewModel.handleReminderRegisterNowAction(targetProfileId)
        intent?.action = null
    }
}

internal data class BottomNavItemSpec(
    val tab: PatientTab,
    val label: String,
    val icon: ImageVector
)

internal data class BottomNavAllocatedSlot(
    val tab: PatientTab,
    val label: String,
    val requiredTextWidthDp: Float,
    val requiredSlotWidthDp: Float,
    val allocatedSlotWidthDp: Float,
    val minTouchHeightDp: Float = 52f
)

internal data class BottomNavLayoutPlan(
    val useSingleFiveColumnBar: Boolean,
    val rows: List<List<BottomNavAllocatedSlot>>
)

/**
 * Estima el ancho tipográfico requerido en dp para una etiqueta de una sola línea
 * con el tamaño de fuente `fontSizeSp` y la escala `fontScale` del sistema.
 */
internal fun estimateSingleLineNavLabelWidthDp(
    label: String,
    fontSizeSp: Float,
    fontScale: Float
): Float {
    val effectiveFontSizeDp = fontSizeSp * fontScale
    var emSum = 0f
    for (ch in label) {
        emSum += when (ch) {
            'i', 'l', 'I' -> 0.26f
            't', 'r', 'f' -> 0.34f
            's', 'c' -> 0.48f
            'y' -> 0.50f
            'a', 'e', 'o' -> 0.52f
            'n', 'd', 'u' -> 0.54f
            'P', 'A', 'H' -> 0.64f
            'M' -> 0.78f
            else -> 0.54f
        }
    }
    return emSum * effectiveFontSizeDp
}

/**
 * Calcula si las 5 etiquetas caben completas en las 5 columnas iguales de NavigationBar;
 * cuando falta espacio real (p. ej. 360 dp con fontScale 1.8–2.0), adapta a filas (3+2, o 2+2+1
 * si la escala combinada es aún mayor) asignando ancho suficiente a cada etiqueta completa
 * y controles táctiles >= 48 dp sin reducir el tamaño de letra.
 */
internal fun planBottomNavigationLayout(
    availableWidthDp: Float,
    labelTextWidthsDp: List<Pair<PatientTab, Pair<String, Float>>>
): BottomNavLayoutPlan {
    val singleRowColumnWidthDp = availableWidthDp / 5f
    val singleRowHorizontalPaddingPerItemDp = 12f
    val usableSingleColumnTextWidthDp = (singleRowColumnWidthDp - singleRowHorizontalPaddingPerItemDp).coerceAtLeast(0f)

    val allFitInFiveEqualColumns = labelTextWidthsDp.all { (_, labelAndWidth) ->
        labelAndWidth.second <= usableSingleColumnTextWidthDp
    }

    if (allFitInFiveEqualColumns) {
        val singleRow = labelTextWidthsDp.map { (tab, labelAndWidth) ->
            BottomNavAllocatedSlot(
                tab = tab,
                label = labelAndWidth.first,
                requiredTextWidthDp = labelAndWidth.second,
                requiredSlotWidthDp = labelAndWidth.second + singleRowHorizontalPaddingPerItemDp,
                allocatedSlotWidthDp = singleRowColumnWidthDp,
                minTouchHeightDp = 56f
            )
        }
        return BottomNavLayoutPlan(
            useSingleFiveColumnBar = true,
            rows = listOf(singleRow)
        )
    }

    val outerHorizontalPaddingDp = 12f // 6.dp a cada lado
    val interItemGapDp = 6f
    val itemInnerHorizontalPaddingDp = 8f // 4.dp a cada lado

    fun buildAllocatedRow(slice: List<Pair<PatientTab, Pair<String, Float>>>): List<BottomNavAllocatedSlot>? {
        val totalGaps = interItemGapDp * (slice.size - 1).coerceAtLeast(0)
        val distributableWidth = (availableWidthDp - outerHorizontalPaddingDp - totalGaps).coerceAtLeast(0f)
        val requiredSlotWidths = slice.map { (_, labelAndWidth) ->
            labelAndWidth.second + itemInnerHorizontalPaddingDp
        }
        val sumRequired = requiredSlotWidths.sum().coerceAtLeast(1f)
        if (sumRequired > distributableWidth) {
            return null
        }
        return slice.mapIndexed { index, (tab, labelAndWidth) ->
            val reqSlot = requiredSlotWidths[index]
            val allocated = distributableWidth * (reqSlot / sumRequired)
            BottomNavAllocatedSlot(
                tab = tab,
                label = labelAndWidth.first,
                requiredTextWidthDp = labelAndWidth.second,
                requiredSlotWidthDp = reqSlot,
                allocatedSlotWidthDp = allocated,
                minTouchHeightDp = 52f
            )
        }
    }

    // Distribución preferida cuando no caben 5 en una fila: 3 + 2 filas
    val row1Of3Plus2 = buildAllocatedRow(labelTextWidthsDp.take(3))
    val row2Of3Plus2 = buildAllocatedRow(labelTextWidthsDp.drop(3))
    if (row1Of3Plus2 != null && row2Of3Plus2 != null) {
        return BottomNavLayoutPlan(
            useSingleFiveColumnBar = false,
            rows = listOf(row1Of3Plus2, row2Of3Plus2)
        )
    }

    // Respaldo adicional (2 + 2 + 1) si se combina simultáneamente Letra Grande de la app con 200 % del sistema
    val fallbackSlices = listOf(
        labelTextWidthsDp.subList(0, 2),
        labelTextWidthsDp.subList(2, 4),
        labelTextWidthsDp.subList(4, 5)
    )
    val fallbackRows = fallbackSlices.map { slice ->
        val totalGaps = interItemGapDp * (slice.size - 1).coerceAtLeast(0)
        val distributableWidth = (availableWidthDp - outerHorizontalPaddingDp - totalGaps).coerceAtLeast(1f)
        val requiredSlotWidths = slice.map { (_, labelAndWidth) ->
            labelAndWidth.second + itemInnerHorizontalPaddingDp
        }
        val sumRequired = requiredSlotWidths.sum().coerceAtLeast(1f)
        slice.mapIndexed { index, (tab, labelAndWidth) ->
            val reqSlot = requiredSlotWidths[index]
            val allocated = distributableWidth * (reqSlot / sumRequired)
            BottomNavAllocatedSlot(
                tab = tab,
                label = labelAndWidth.first,
                requiredTextWidthDp = labelAndWidth.second,
                requiredSlotWidthDp = reqSlot,
                allocatedSlotWidthDp = allocated,
                minTouchHeightDp = 52f
            )
        }
    }
    return BottomNavLayoutPlan(
        useSingleFiveColumnBar = false,
        rows = fallbackRows
    )
}

@Composable
private fun GlucosoftAdaptiveBottomBar(
    activeTab: PatientTab,
    onSelectTab: (PatientTab) -> Unit
) {
    val navItems = remember {
        listOf(
            BottomNavItemSpec(PatientTab.HOME, "Inicio", Icons.Default.MonitorHeart),
            BottomNavItemSpec(PatientTab.HISTORY, "Historial", Icons.Default.History),
            BottomNavItemSpec(PatientTab.DEVICES, "Medidor", Icons.Default.Bluetooth),
            BottomNavItemSpec(PatientTab.EMERGENCY, "Ayuda", Icons.Default.PhoneInTalk),
            BottomNavItemSpec(PatientTab.PROFILES, "Perfiles", Icons.Default.People)
        )
    }
    val itemByTab = remember(navItems) { navItems.associateBy { it.tab } }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = MaterialTheme.typography.labelMedium
    val fontSizeSp = labelStyle.fontSize.value
    val fontScale = density.fontScale

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val availableWidthDp = maxWidth.value
        val measuredWidths = remember(availableWidthDp, fontSizeSp, fontScale, labelStyle) {
            navItems.map { item ->
                val measuredPx = textMeasurer.measure(
                    text = AnnotatedString(item.label),
                    style = labelStyle,
                    maxLines = 1,
                    softWrap = false
                ).size.width
                val measuredDp = with(density) { measuredPx.toDp().value }
                val estimatedDp = estimateSingleLineNavLabelWidthDp(
                    label = item.label,
                    fontSizeSp = fontSizeSp,
                    fontScale = fontScale
                )
                item.tab to (item.label to maxOf(measuredDp, estimatedDp))
            }
        }

        val plan = remember(availableWidthDp, measuredWidths) {
            planBottomNavigationLayout(
                availableWidthDp = availableWidthDp,
                labelTextWidthsDp = measuredWidths
            )
        }

        if (plan.useSingleFiveColumnBar) {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                navItems.forEach { item ->
                    NavigationBarItem(
                        selected = activeTab == item.tab,
                        onClick = { onSelectTab(item.tab) },
                        icon = { Icon(imageVector = item.icon, contentDescription = item.label) },
                        label = {
                            Text(
                                text = item.label,
                                maxLines = 1,
                                softWrap = false
                            )
                        },
                        modifier = Modifier.testTag("nav_tab_${item.tab.name.lowercase()}")
                    )
                }
            }
        } else {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(NavigationBarDefaults.windowInsets)
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    plan.rows.forEach { rowSlots ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            rowSlots.forEach { slot ->
                                val spec = itemByTab.getValue(slot.tab)
                                val isSelected = activeTab == slot.tab
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) {
                                            MaterialTheme.colorScheme.secondaryContainer
                                        } else {
                                            Color.Transparent
                                        }
                                    ),
                                    border = if (isSelected) {
                                        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                    } else {
                                        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                                    },
                                    modifier = Modifier
                                        .weight(slot.requiredSlotWidthDp.coerceAtLeast(1f))
                                        .heightIn(min = 52.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable(role = Role.Tab) {
                                            onSelectTab(slot.tab)
                                        }
                                        .semantics {
                                            selected = isSelected
                                            stateDescription = if (isSelected) "Seleccionado" else "No seleccionado"
                                        }
                                        .testTag("nav_tab_${slot.tab.name.lowercase()}")
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 4.dp, vertical = 6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = spec.icon,
                                            contentDescription = spec.label,
                                            tint = if (isSelected) {
                                                MaterialTheme.colorScheme.onSecondaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = spec.label,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.onSecondaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            softWrap = false
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
}

@Composable
fun GlucosoftAppContent(viewModel: GlucosoftViewModel) {
    val context = LocalContext.current
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val readings by viewModel.readings.collectAsStateWithLifecycle()
    val activeProfileId by viewModel.activeProfileId.collectAsStateWithLifecycle()
    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val isDark by viewModel.isDark.collectAsStateWithLifecycle()
    val textScale by viewModel.textScale.collectAsStateWithLifecycle()
    val showGuide by viewModel.showGuide.collectAsStateWithLifecycle()
    val rangeFilter by viewModel.rangeFilter.collectAsStateWithLifecycle()
    val contextFilter by viewModel.contextFilter.collectAsStateWithLifecycle()
    val actionFeedback by viewModel.actionFeedback.collectAsStateWithLifecycle()
    val isMeasureChoiceOpen by viewModel.isMeasureChoiceOpen.collectAsStateWithLifecycle()
    val isManualSheetOpen by viewModel.isManualSheetOpen.collectAsStateWithLifecycle()
    val isBleSheetOpen by viewModel.isBleSheetOpen.collectAsStateWithLifecycle()
    val defaultBleContext by viewModel.defaultBleContext.collectAsStateWithLifecycle()
    val manualDrafts by viewModel.manualDrafts.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val preloadedManualDate by viewModel.preloadedManualDate.collectAsStateWithLifecycle()
    val connectedSession by viewModel.bleManager.connectedSession.collectAsStateWithLifecycle()

    var contactPendingCall by remember { mutableStateOf<EmergencyContact?>(null) }
    var isDisplaySettingsOpen by remember { mutableStateOf(false) }

    val activeProfile = remember(profiles, activeProfileId) {
        profiles.find { it.id == activeProfileId } ?: profiles.firstOrNull()
    }
    val activeProfileReminder = remember(reminders, activeProfile) {
        activeProfile?.let { prof -> reminders.find { it.profileId == prof.id } }
    }

    val profileReadings = remember(readings, activeProfile) {
        if (activeProfile == null) {
            emptyList()
        } else {
            readings.filter { it.profileId == activeProfile.id }
                .sortedByDescending { it.timestamp }
        }
    }

    val filteredReadings = remember(profileReadings, rangeFilter, contextFilter) {
        GlucoseMath.filterReadings(
            readings = profileReadings,
            rangeFilter = rangeFilter,
            contextFilter = contextFilter
        )
    }

    if (activeTab != PatientTab.HOME) {
        BackHandler {
            viewModel.selectTab(PatientTab.HOME)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                )
            )
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                GlucosoftHeaderBar(
                    activeProfileName = activeProfile?.name,
                    activeProfileUnitSymbol = activeProfile?.unitPreference?.symbol,
                    currentTab = activeTab,
                    onNavigateBackToHome = if (activeTab != PatientTab.HOME) {
                        { viewModel.selectTab(PatientTab.HOME) }
                    } else {
                        null
                    },
                    onOpenProfilesTab = { viewModel.selectTab(PatientTab.PROFILES) }
                )
            },
            bottomBar = {
                if (activeProfile != null) {
                    GlucosoftAdaptiveBottomBar(
                        activeTab = activeTab,
                        onSelectTab = { tab -> viewModel.selectTab(tab) }
                    )
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
                        .widthIn(max = 720.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    ActionFeedbackBanner(message = actionFeedback)

                    GuidedOnboardingCard(
                        isOpen = showGuide,
                        onClose = { viewModel.closeGuide() },
                        onJumpToTab = { tab -> viewModel.selectTab(tab) },
                        onOpenMeasureModal = { viewModel.setMeasureChoiceOpen(true) }
                    )

                    if (activeProfile == null) {
                        FirstProfileSetupCard(
                            isDark = isDark,
                            textScale = textScale,
                            onCreateProfile = { name, unit -> viewModel.createProfile(name, unit) },
                            onOpenDisplaySettings = { isDisplaySettingsOpen = true },
                            onOpenGuide = { viewModel.openGuide() }
                        )
                    } else {
                        when (activeTab) {
                            PatientTab.HOME -> HomeScreen(
                                profiles = profiles,
                                activeProfile = activeProfile,
                                allProfileReadings = profileReadings,
                                filteredReadings = filteredReadings,
                                rangeFilter = rangeFilter,
                                connectedSession = connectedSession,
                                isDark = isDark,
                                textScale = textScale,
                                onSelectProfile = { id -> viewModel.selectProfile(id) },
                                onSelectRange = { viewModel.setRangeFilter(it) },
                                onOpenNewMeasurement = { viewModel.setMeasureChoiceOpen(true) },
                                onOpenManualMeasurement = { viewModel.setManualSheetOpen(true) },
                                onOpenBleModal = { viewModel.setBleSheetOpen(true) },
                                onOpenDisplaySettings = { isDisplaySettingsOpen = true },
                                onSelectTab = { viewModel.selectTab(it) },
                                onExportPdf = {
                                    viewModel.exportPdfReport(
                                        readingsToExport = filteredReadings,
                                        rangeLabel = rangeFilter.fullLabel,
                                        shareViaSystem = false
                                    )
                                },
                                onRequestCallContact = { contactPendingCall = it },
                                activeProfileReminder = activeProfileReminder,
                                onOpenManualMeasurementForDate = { civilDate ->
                                    viewModel.openManualMeasurementForDate(civilDate)
                                },
                                onOpenRemindersSettings = {
                                    viewModel.selectTab(PatientTab.PROFILES)
                                }
                            )

                            PatientTab.HISTORY -> HistoryScreen(
                                activeProfile = activeProfile,
                                allProfileReadings = profileReadings,
                                filteredReadings = filteredReadings,
                                rangeFilter = rangeFilter,
                                contextFilter = contextFilter,
                                onSelectRange = { viewModel.setRangeFilter(it) },
                                onSelectContext = { viewModel.setContextFilter(it) },
                                onSavePdf = {
                                    viewModel.exportPdfReport(
                                        readingsToExport = filteredReadings,
                                        rangeLabel = rangeFilter.fullLabel,
                                        shareViaSystem = false
                                    )
                                },
                                onSharePdf = {
                                    viewModel.exportPdfReport(
                                        readingsToExport = filteredReadings,
                                        rangeLabel = rangeFilter.fullLabel,
                                        shareViaSystem = true
                                    )
                                },
                                onDeleteReading = { viewModel.deleteReading(it) }
                            )

                            PatientTab.DEVICES -> DevicesScreen(
                                activeProfile = activeProfile,
                                connectedSession = connectedSession,
                                onOpenBleModal = { viewModel.setBleSheetOpen(true) },
                                onOpenManualEntry = { viewModel.setManualSheetOpen(true) }
                            )

                            PatientTab.EMERGENCY -> EmergencyScreen(
                                activeProfile = activeProfile,
                                latestReading = profileReadings.firstOrNull(),
                                onSaveContact = { contact ->
                                    viewModel.updateEmergencyContact(activeProfile.id, contact)
                                },
                                onRequestCall = { contactPendingCall = it },
                                onOpenGuide = { viewModel.openGuide() }
                            )

                            PatientTab.PROFILES -> ProfilesScreen(
                                profiles = profiles,
                                activeProfile = activeProfile,
                                allReadings = readings,
                                onSelectProfile = { viewModel.selectProfile(it) },
                                onSelectTab = { viewModel.selectTab(it) },
                                onCreateProfile = { name, unit -> viewModel.createProfile(name, unit) },
                                onUpdateProfile = { id, name, unit, minMg, maxMg ->
                                    viewModel.updateProfile(id, name, unit, minMg, maxMg)
                                },
                                onChangeProfileUnit = { id, unit ->
                                    viewModel.changeProfileUnit(id, unit)
                                },
                                onDeleteProfile = { viewModel.deleteProfile(it) },
                                reminders = reminders,
                                onSaveProfileReminder = { profileId, enabled, hour, minute ->
                                    viewModel.saveProfileReminder(profileId, enabled, hour, minute)
                                },
                                onTriggerTestReminder = { profileId ->
                                    viewModel.triggerTestReminder(profileId)
                                },
                                onExportBackup = { targetUri, password, onResult ->
                                    viewModel.exportEncryptedBackup(targetUri, password, onResult)
                                },
                                onRestoreBackup = { sourceUri, password, onResult ->
                                    viewModel.restoreEncryptedBackup(sourceUri, password, onResult)
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        // Paneles amplios de pantalla completa para Registro Manual y Conexión Bluetooth
        if (activeProfile != null) {
            ManualEntryFullScreenPanel(
                isOpen = isManualSheetOpen,
                activeProfile = activeProfile,
                existingDraft = manualDrafts[activeProfile.id],
                connectedSession = connectedSession,
                preloadedCivilDate = preloadedManualDate,
                onUpdateDraft = { draft -> viewModel.updateManualDraft(draft) },
                onClearDraft = { profId -> viewModel.clearManualDraft(profId) },
                onDismiss = { viewModel.setManualSheetOpen(false) },
                onSwitchToBluetooth = {
                    viewModel.setManualSheetOpen(false)
                    viewModel.setBleSheetOpen(true)
                },
                onSaveReading = { valueMgDl, unit, ts, ctx, devId, customModel, tags, note, photoPath ->
                    viewModel.addManualReading(
                        valueMgDl = valueMgDl,
                        originalUnit = unit,
                        timestampMs = ts,
                        context = ctx,
                        deviceId = devId,
                        customDeviceModel = customModel,
                        quickTags = tags,
                        note = note,
                        photoPath = photoPath
                    )
                }
            )

            BleCaptureDialog(
                isOpen = isBleSheetOpen,
                profileName = activeProfile.name,
                bleManager = viewModel.bleManager,
                defaultContext = defaultBleContext,
                onChangeDefaultContext = { viewModel.setDefaultBleContext(it) },
                onConnectDevice = { viewModel.connectBleDevice(it) },
                onOpenManualEntry = { viewModel.setManualSheetOpen(true) },
                onDismiss = { viewModel.setBleSheetOpen(false) }
            )

            MeasureChoiceDialog(
                isOpen = isMeasureChoiceOpen,
                activeProfileName = activeProfile.name,
                connectedSession = connectedSession,
                onDismiss = { viewModel.setMeasureChoiceOpen(false) },
                onChooseManual = {
                    viewModel.setMeasureChoiceOpen(false)
                    viewModel.setManualSheetOpen(true)
                },
                onChooseBluetooth = {
                    viewModel.setMeasureChoiceOpen(false)
                    viewModel.setBleSheetOpen(true)
                }
            )
        }
    }

    DisplayAndTextSettingsDialog(
        isOpen = isDisplaySettingsOpen,
        isDark = isDark,
        textScale = textScale,
        onToggleTextScale = { viewModel.toggleTextScale() },
        onToggleTheme = { viewModel.toggleTheme() },
        onOpenGuide = { viewModel.openGuide() },
        onDismiss = { isDisplaySettingsOpen = false }
    )

    EmergencyCallConfirmDialog(
        contact = contactPendingCall,
        context = context,
        onDismiss = { contactPendingCall = null }
    )
}
