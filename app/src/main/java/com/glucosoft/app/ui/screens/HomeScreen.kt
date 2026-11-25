package com.glucosoft.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.glucosoft.app.model.DateRangeFilter
import com.glucosoft.app.model.EmergencyContact
import com.glucosoft.app.model.GlucoseReading
import com.glucosoft.app.model.GlucoseSeverity
import com.glucosoft.app.model.GlucoseUnit
import com.glucosoft.app.model.LocalProfile
import com.glucosoft.app.model.PatientTab
import com.glucosoft.app.model.ProfileReminderConfig
import com.glucosoft.app.model.TextScaleMode
import com.glucosoft.app.service.ConnectedMeterSession
import com.glucosoft.app.ui.components.MealPhotoAttachmentCard
import com.glucosoft.app.ui.components.MealPhotoViewerDialog
import com.glucosoft.app.ui.components.TrendChartCard
import com.glucosoft.app.ui.components.contextIconFor
import com.glucosoft.app.ui.theme.JetBrainsMonoFamily
import com.glucosoft.app.ui.theme.rememberStatusColorPair
import com.glucosoft.app.util.GlucoseMath
import com.glucosoft.app.util.LocalCivilDate
import com.glucosoft.app.util.WeekDayBubble

const val CUSTOMER_SERVICE_COMPANY_NAME = "Diagnósticos Biomédicos"
const val CUSTOMER_SERVICE_TITLE = "Servicio al usuario"
const val CUSTOMER_SERVICE_DISPLAY_NUMBER = "317 656 2755"
const val CUSTOMER_SERVICE_ACCESSIBILITY_DESCRIPTION =
    "Diagnósticos Biomédicos, Servicio al usuario, 317 656 2755"
const val CUSTOMER_SERVICE_DIAL_URI = "tel:+573176562755"
const val CUSTOMER_SERVICE_INTENT_ACTION = Intent.ACTION_DIAL
const val CUSTOMER_SERVICE_NO_DIALER_MESSAGE =
    "Este dispositivo no dispone de marcador telefónico. Puedes comunicarte con Servicio al usuario de Diagnósticos Biomédicos al número 317 656 2755 (+57 317 656 2755)."

fun buildCustomerServiceDialIntent(): Intent {
    return Intent(CUSTOMER_SERVICE_INTENT_ACTION, Uri.parse(CUSTOMER_SERVICE_DIAL_URI))
}

/**
 * Devuelve el orden verificable de los bloques desplazables de Inicio,
 * garantizando que «Mi semana» esté cerca de la última medición, que el contacto/ayuda de emergencia
 * personal quede arriba y que el único botón corporativo de Diagnósticos Biomédicos
 * («Servicio al usuario · 317 656 2755») junto con su aviso si falta marcador queden al final de Inicio.
 */
fun buildHomeScrollSectionOrder(
    hasEmergencyContact: Boolean,
    showTrendSection: Boolean,
    hasMultipleProfiles: Boolean,
    showNoDialerFeedback: Boolean
): List<String> {
    return buildList {
        add("home_hero_card")
        add("home_my_week_card")
        if (hasEmergencyContact) {
            add("home_emergency_card")
        } else {
            add("home_setup_emergency_button")
        }
        add("home_view_history_button")
        add("home_bubble_trend")
        add("home_connect_ble_button")
        add("home_export_pdf_button")
        add("home_reminders_button")
        add("open_display_settings_button")
        if (showTrendSection) {
            add("home_trend_chart_section")
        }
        if (hasMultipleProfiles) {
            add("home_profile_switcher_card")
        }
        add("home_customer_service_button")
        if (showNoDialerFeedback) {
            add("home_customer_service_no_dialer_card")
        }
    }
}

fun tryOpenCustomerServiceDialer(
    context: Context?,
    intentFactory: (action: String, dialUri: String) -> Intent = { action, dialUri ->
        Intent(action, Uri.parse(dialUri))
    },
    launcher: (Intent) -> Unit = { intent ->
        context?.startActivity(intent) ?: throw IllegalStateException("No context")
    }
): Boolean {
    return try {
        val intent = intentFactory(CUSTOMER_SERVICE_INTENT_ACTION, CUSTOMER_SERVICE_DIAL_URI)
        if (context != null && context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        launcher(intent)
        true
    } catch (_: Exception) {
        false
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FirstProfileSetupCard(
    isDark: Boolean,
    textScale: TextScaleMode,
    onCreateProfile: (name: String, unit: GlucoseUnit) -> Unit,
    onOpenDisplaySettings: () -> Unit,
    onOpenGuide: () -> Unit
) {
    var nameInput by remember { mutableStateOf("") }
    var unitSelection by remember { mutableStateOf(GlucoseUnit.MG_DL) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val scaleLabel = if (textScale == TextScaleMode.LARGE) "Letra grande" else "Letra normal"
    val themeLabel = if (isDark) "Modo oscuro" else "Modo claro"

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("first_profile_card")
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Crea tu perfil para comenzar",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = "Sin contraseñas ni internet. Tus lecturas se guardan en este teléfono (hasta 3 perfiles).",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = nameInput,
                onValueChange = {
                    if (it.length <= 32) nameInput = it
                    errorMsg = null
                },
                label = { Text("Tu nombre (ejemplo: María López)") },
                isError = errorMsg != null,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("first_profile_name_input")
            )

            if (errorMsg != null) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = errorMsg!!,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            Text(
                text = "Unidad de tu glucómetro:",
                style = MaterialTheme.typography.titleMedium
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                GlucoseUnit.entries.forEach { u ->
                    val isSelected = unitSelection == u
                    if (isSelected) {
                        Button(
                            onClick = { unitSelection = u },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .heightIn(min = 52.dp)
                                .semantics { selected = true }
                                .testTag("first_profile_unit_${u.name.lowercase()}")
                        ) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = "Seleccionada")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = u.symbol, fontFamily = JetBrainsMonoFamily)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { unitSelection = u },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .heightIn(min = 52.dp)
                                .semantics { selected = false }
                                .testTag("first_profile_unit_${u.name.lowercase()}")
                        ) {
                            Text(text = u.symbol, fontFamily = JetBrainsMonoFamily)
                        }
                    }
                }
            }

            Button(
                onClick = {
                    val trimmed = nameInput.trim()
                    if (trimmed.isEmpty()) {
                        errorMsg = "Escribe un nombre para crear tu perfil."
                    } else {
                        onCreateProfile(trimmed, unitSelection)
                    }
                },
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 58.dp)
                    .testTag("create_first_profile_button")
            ) {
                Text(
                    text = "Crear perfil y comenzar",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            BubbleTaskCard(
                icon = Icons.Default.FormatSize,
                title = "Pantalla y letra",
                subtitle = "$scaleLabel · $themeLabel",
                onClick = onOpenDisplaySettings,
                testTag = "first_profile_display_settings_button"
            )

            BubbleTaskCard(
                icon = Icons.AutoMirrored.Filled.HelpOutline,
                title = "Guía rápida",
                subtitle = "Ver cómo usar la aplicación paso a paso",
                onClick = onOpenGuide,
                testTag = "first_profile_guide_button"
            )
        }
    }
}

/**
 * Tarjeta tipo burbuja, entera y pulsable, con esquinas redondeadas,
 * un icono reconocible y un rótulo breve de texto grande.
 */
@Composable
fun BubbleTaskCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    eyebrowTitle: String? = null,
    accessibilityDescription: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    iconBackgroundColor: Color = MaterialTheme.colorScheme.primaryContainer,
    iconTint: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    borderColor: Color = MaterialTheme.colorScheme.outline
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) containerColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        border = BorderStroke(1.5.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 78.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .semantics(mergeDescendants = true) {
                role = Role.Button
                if (!accessibilityDescription.isNullOrBlank()) {
                    contentDescription = accessibilityDescription
                }
            }
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(iconBackgroundColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(26.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (!eyebrowTitle.isNullOrBlank()) {
                    Text(
                        text = eyebrowTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = subtitle,
                    style = if (!eyebrowTitle.isNullOrBlank()) {
                        MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        MaterialTheme.typography.labelMedium
                    },
                    color = if (!eyebrowTitle.isNullOrBlank()) {
                        contentColor
                    } else {
                        contentColor.copy(alpha = 0.85f)
                    },
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = contentColor.copy(alpha = 0.7f),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * Pantalla de Inicio estilo diario interactivo para personas mayores:
 * 1) Tarjeta «Última medición» con cifra y unidad juntas, hora, momento con icono y estado en palabras.
 *    Explicación larga desplegable con «Qué significa», manteniendo visibles los avisos importantes.
 *    Botón grande «Registrar medición» (58 dp).
 * 2) El perfil activo se muestra una sola vez en el encabezado superior (sin repetirse en varias tarjetas).
 * 3) Tarjetas tipo burbuja, enteras y pulsables, para «Diario por día», «Mi tendencia»,
 *    «Mi medidor», «Informe PDF», «Ayuda» y «Pantalla y letra».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    profiles: List<LocalProfile>,
    activeProfile: LocalProfile,
    allProfileReadings: List<GlucoseReading>,
    filteredReadings: List<GlucoseReading>,
    rangeFilter: DateRangeFilter,
    connectedSession: ConnectedMeterSession?,
    isDark: Boolean,
    textScale: TextScaleMode,
    onSelectProfile: (String) -> Unit,
    onSelectRange: (DateRangeFilter) -> Unit,
    onOpenNewMeasurement: () -> Unit,
    onOpenManualMeasurement: () -> Unit,
    onOpenBleModal: () -> Unit,
    onOpenDisplaySettings: () -> Unit,
    onSelectTab: (PatientTab) -> Unit,
    onExportPdf: () -> Unit,
    onRequestCallContact: (EmergencyContact) -> Unit,
    activeProfileReminder: ProfileReminderConfig? = null,
    onOpenManualMeasurementForDate: (LocalCivilDate) -> Unit = { _ -> onOpenManualMeasurement() },
    onOpenRemindersSettings: () -> Unit = { onSelectTab(PatientTab.PROFILES) }
) {
    var showWhatItMeans by remember { mutableStateOf(false) }
    var showTrendSection by remember { mutableStateOf(false) }
    var showNoDialerFeedback by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val latestReading = allProfileReadings.firstOrNull()
    val latestEvaluation = latestReading?.let {
        GlucoseMath.evaluateReadingSeverity(
            it.valueMgDl,
            it.context,
            activeProfile.targetMinMgDl,
            activeProfile.targetMaxMgDl
        )
    }
    val stats = remember(filteredReadings, activeProfile) {
        GlucoseMath.computeGlucoseStats(
            filteredReadings,
            activeProfile.targetMinMgDl,
            activeProfile.targetMaxMgDl
        )
    }

    val scaleLabel = if (textScale == TextScaleMode.LARGE) "Letra grande" else "Letra normal"
    val themeLabel = if (isDark) "Modo oscuro" else "Modo claro"
    val fontScale = LocalDensity.current.fontScale

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 1. Tarjeta principal «Última medición» + botón grande «Registrar medición»
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("home_hero_card")
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MonitorHeart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(
                        text = "Última medición",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() }
                    )
                }

                if (latestReading != null && latestEvaluation != null) {
                    val statusColors = rememberStatusColorPair(latestEvaluation.severity)
                    val valueFormatted = GlucoseMath.formatGlucoseValue(
                        latestReading.valueMgDl,
                        activeProfile.unitPreference
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Cifra y unidad juntas, reconocibles de un vistazo
                        Text(
                            text = "$valueFormatted ${activeProfile.unitPreference.symbol}",
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("latest_glucose_value")
                        )

                        // Estado en palabras con alto contraste (>= 4,5:1 en claro y oscuro)
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = statusColors.containerColor
                            ),
                            border = BorderStroke(1.5.dp, statusColors.borderColor)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (latestEvaluation.severity == GlucoseSeverity.NORMAL) {
                                        Icons.Default.CheckCircle
                                    } else {
                                        Icons.Default.Warning
                                    },
                                    contentDescription = null,
                                    tint = statusColors.contentColor,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = latestEvaluation.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = statusColors.contentColor
                                )
                            }
                        }

                        // Hora y momento con icono reconocible
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = contextIconFor(latestReading.context),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = latestReading.context.label,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = GlucoseMath.formatShortDateTime(latestReading.timestamp),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        // Aviso importante visible de inmediato si hay alerta
                        if (latestEvaluation.severity == GlucoseSeverity.ALERT) {
                            Text(
                                text = "Si sientes malestar, comunícate con tu contacto de emergencia o tu médico.",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = statusColors.contentColor
                            )
                        }

                        // Explicación orientativa larga disponible con un toque en «Qué significa»
                        OutlinedButton(
                            onClick = { showWhatItMeans = !showWhatItMeans },
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("toggle_what_it_means_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (showWhatItMeans) "Ocultar explicación" else "Qué significa",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = if (showWhatItMeans) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null
                            )
                        }

                        AnimatedVisibility(visible = showWhatItMeans) {
                            val minGoal = GlucoseMath.formatGlucoseValue(activeProfile.targetMinMgDl, activeProfile.unitPreference)
                            val maxGoal = GlucoseMath.formatGlucoseValue(activeProfile.targetMaxMgDl, activeProfile.unitPreference)
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("what_it_means_panel")
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = latestEvaluation.detail,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Meta orientativa configurada: $minGoal–$maxGoal ${activeProfile.unitPreference.symbol} · Origen: ${latestReading.captureMethod.displayLabel} (${latestReading.deviceModel}).",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Sin lecturas registradas aún",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Toca «Registrar medición» para anotar el número de tu glucómetro.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Acción principal grande (~58 dp) para uso cómodo con una mano
                Button(
                    onClick = onOpenNewMeasurement,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 58.dp)
                        .testTag("new_measurement_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Registrar medición",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
        }

        // 1b. «Mi semana»: 7 burbujas grandes (lunes a domingo) en hora local con marca de Hoy,
        //     selección por forma/borde además del color, lecturas reales del día ordenadas por hora
        //     y botón para registrar medición en el día elegido.
        MyWeekSectionCard(
            activeProfile = activeProfile,
            allProfileReadings = allProfileReadings,
            textScale = textScale,
            fontScale = fontScale,
            onOpenManualMeasurementForDate = onOpenManualMeasurementForDate
        )

        // 2. Acceso rápido a Ayuda y Emergencia (siempre visible y con confirmación de llamada)
        val emergency = activeProfile.emergencyContact
        val alertColors = rememberStatusColorPair(GlucoseSeverity.ALERT)
        if (emergency != null) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = alertColors.containerColor),
                border = BorderStroke(1.5.dp, alertColors.borderColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_emergency_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(role = Role.Button) { onRequestCallContact(emergency) }
                            .padding(vertical = 4.dp)
                            .testTag("home_call_emergency_button"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(alertColors.borderColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = "Llamar a ${emergency.name}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = alertColors.contentColor,
                                textAlign = TextAlign.Start,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "${emergency.relation} · ${emergency.phone}",
                                style = MaterialTheme.typography.labelLarge,
                                color = alertColors.contentColor,
                                textAlign = TextAlign.Start,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = alertColors.contentColor
                        )
                    }

                    OutlinedButton(
                        onClick = { onSelectTab(PatientTab.EMERGENCY) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ver sección Ayuda")
                    }
                }
            }
        } else {
            BubbleTaskCard(
                icon = Icons.Default.PhoneInTalk,
                title = "Ayuda y emergencia",
                subtitle = "Guardar teléfono de un familiar o médico",
                onClick = { onSelectTab(PatientTab.EMERGENCY) },
                testTag = "home_setup_emergency_button",
                containerColor = alertColors.containerColor,
                contentColor = alertColors.contentColor,
                iconBackgroundColor = alertColors.borderColor,
                iconTint = Color.White,
                borderColor = alertColors.borderColor
            )
        }

        // 3. Tarjetas tipo burbuja, enteras y pulsables:
        //    En teléfonos siempre una tarjeta a todo el ancho por fila, y siempre una columna con letra grande
        //    o escala alta del sistema. En pantallas anchas permite dos columnas solo si quedan al menos
        //    ~280–300 dp por tarjeta tras márgenes (>= 600 dp de ancho disponible).
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val useTwoColumns = textScale == TextScaleMode.NORMAL && fontScale <= 1.15f && maxWidth >= 600.dp

            val diarioSubtitle = "${allProfileReadings.size} ${if (allProfileReadings.size == 1) "lectura" else "lecturas"} por fecha"
            val trendSubtitle = if (stats.count > 0) {
                val avgStr = GlucoseMath.formatGlucoseValue(stats.avgMgDl, activeProfile.unitPreference)
                if (showTrendSection) "Ocultar gráfico" else "Promedio $avgStr ${activeProfile.unitPreference.symbol}"
            } else {
                "Gráfico y lista"
            }
            val meterSubtitle = when {
                connectedSession == null -> "Bluetooth y modelos"
                connectedSession.isReadyForMeasurements -> "Listo: ${connectedSession.matchedModelName}"
                else -> "Conectado (canal pendiente)"
            }
            val pdfSubtitle = if (filteredReadings.isNotEmpty()) {
                "Guardar reporte médico"
            } else {
                "Registra 1 toma primero"
            }
            val reminderSubtitle = if (activeProfileReminder?.enabled == true) {
                "Activo todos los días a las ${activeProfileReminder.formattedTime}"
            } else {
                "Avisos diarios opcionales"
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (useTwoColumns) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        BubbleTaskCard(
                            icon = Icons.Default.History,
                            title = "Diario",
                            subtitle = diarioSubtitle,
                            onClick = { onSelectTab(PatientTab.HISTORY) },
                            testTag = "home_view_history_button",
                            modifier = Modifier.weight(1f)
                        )
                        BubbleTaskCard(
                            icon = Icons.Default.ShowChart,
                            title = "Mi tendencia",
                            subtitle = trendSubtitle,
                            onClick = { showTrendSection = !showTrendSection },
                            testTag = "home_bubble_trend",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        BubbleTaskCard(
                            icon = Icons.Default.Bluetooth,
                            title = "Mi medidor",
                            subtitle = meterSubtitle,
                            onClick = onOpenBleModal,
                            testTag = "home_connect_ble_button",
                            modifier = Modifier.weight(1f)
                        )
                        BubbleTaskCard(
                            icon = Icons.Default.PictureAsPdf,
                            title = "Informe PDF",
                            subtitle = pdfSubtitle,
                            onClick = onExportPdf,
                            enabled = filteredReadings.isNotEmpty(),
                            testTag = "home_export_pdf_button",
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    BubbleTaskCard(
                        icon = Icons.Default.History,
                        title = "Diario",
                        subtitle = diarioSubtitle,
                        onClick = { onSelectTab(PatientTab.HISTORY) },
                        testTag = "home_view_history_button"
                    )
                    BubbleTaskCard(
                        icon = Icons.Default.ShowChart,
                        title = "Mi tendencia",
                        subtitle = trendSubtitle,
                        onClick = { showTrendSection = !showTrendSection },
                        testTag = "home_bubble_trend"
                    )
                    BubbleTaskCard(
                        icon = Icons.Default.Bluetooth,
                        title = "Mi medidor",
                        subtitle = meterSubtitle,
                        onClick = onOpenBleModal,
                        testTag = "home_connect_ble_button"
                    )
                    BubbleTaskCard(
                        icon = Icons.Default.PictureAsPdf,
                        title = "Informe PDF",
                        subtitle = pdfSubtitle,
                        onClick = onExportPdf,
                        enabled = filteredReadings.isNotEmpty(),
                        testTag = "home_export_pdf_button"
                    )
                }

                // Burbuja «Recordatorios» para avisos diarios opcionales por perfil
                BubbleTaskCard(
                    icon = Icons.Default.NotificationsActive,
                    title = "Recordatorios",
                    subtitle = reminderSubtitle,
                    onClick = onOpenRemindersSettings,
                    testTag = "home_reminders_button"
                )

                // Burbuja «Pantalla y letra» con las preferencias actuales visibles
                BubbleTaskCard(
                    icon = Icons.Default.FormatSize,
                    title = "Pantalla y letra",
                    subtitle = "$scaleLabel · $themeLabel",
                    onClick = onOpenDisplaySettings,
                    testTag = "open_display_settings_button"
                )
            }
        }

        // 4. Gráfico y alternativa de lista al tocar «Mi tendencia»
        AnimatedVisibility(visible = showTrendSection) {
            TrendChartCard(
                readings = filteredReadings,
                unit = activeProfile.unitPreference,
                targetMinMgDl = activeProfile.targetMinMgDl,
                targetMaxMgDl = activeProfile.targetMaxMgDl,
                rangeFilter = rangeFilter,
                onSelectRange = onSelectRange
            )
        }

        // Cambio rápido entre perfiles si hay más de 1 creado (sin repetir el título del perfil activo)
        if (profiles.size > 1) {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_profile_switcher_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Cambiar de persona",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        profiles.forEach { prof ->
                            val isSelected = prof.id == activeProfile.id
                            if (isSelected) {
                                Button(
                                    onClick = { onSelectProfile(prof.id) },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .semantics { selected = true }
                                        .testTag("profile_chip_${prof.id}")
                                ) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = "Seleccionado")
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(prof.name)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { onSelectProfile(prof.id) },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .semantics { selected = false }
                                        .testTag("profile_chip_${prof.id}")
                                ) {
                                    Text(prof.name)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Zona final separada para el único acceso corporativo de la empresa Diagnósticos Biomédicos,
        //    claramente distinguible del contacto personal de emergencia (rojo):
        //    identifica juntos en líneas legibles «Diagnósticos Biomédicos», «Servicio al usuario» y «317 656 2755»,
        //    con icono de auriculares/soporte y colores corporativos/del tema accesibles.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            BubbleTaskCard(
                icon = Icons.Default.SupportAgent,
                eyebrowTitle = CUSTOMER_SERVICE_COMPANY_NAME,
                title = CUSTOMER_SERVICE_TITLE,
                subtitle = CUSTOMER_SERVICE_DISPLAY_NUMBER,
                accessibilityDescription = CUSTOMER_SERVICE_ACCESSIBILITY_DESCRIPTION,
                onClick = {
                    val opened = tryOpenCustomerServiceDialer(context)
                    showNoDialerFeedback = !opened
                },
                testTag = "home_customer_service_button",
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                iconBackgroundColor = MaterialTheme.colorScheme.secondary,
                iconTint = MaterialTheme.colorScheme.onSecondary,
                borderColor = MaterialTheme.colorScheme.secondary
            )

            if (showNoDialerFeedback) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite }
                        .testTag("home_customer_service_no_dialer_card")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.SupportAgent,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = CUSTOMER_SERVICE_NO_DIALER_MESSAGE,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Reparte las 7 burbujas de «Mi semana» en filas amplias para evitar 7 objetivos diminutos
 * o texto cortado en teléfonos verticales y con letra grande:
 * - Con letra grande o escala del sistema > 1,20: filas de máximo 3 burbujas (3 + 2 + 2).
 * - En móvil con letra normal (< 640 dp): 2 filas (4 + 3).
 * - En pantallas anchas (>= 640 dp) con letra normal: 4 + 3 para mantener burbujas grandes y cómodas.
 */
fun planMyWeekBubbleRows(
    bubbles: List<WeekDayBubble>,
    availableWidthDp: Float,
    isLargeText: Boolean,
    fontScale: Float
): List<List<WeekDayBubble>> {
    if (bubbles.isEmpty()) return emptyList()
    val useThreeRowLayout = isLargeText || fontScale > 1.20f || availableWidthDp < 340f
    return if (useThreeRowLayout) {
        listOf(
            bubbles.take(3),
            bubbles.drop(3).take(2),
            bubbles.drop(5)
        ).filter { it.isNotEmpty() }
    } else {
        listOf(
            bubbles.take(4),
            bubbles.drop(4)
        ).filter { it.isNotEmpty() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MyWeekSectionCard(
    activeProfile: LocalProfile,
    allProfileReadings: List<GlucoseReading>,
    textScale: TextScaleMode,
    fontScale: Float,
    onOpenManualMeasurementForDate: (LocalCivilDate) -> Unit
) {
    val today = remember { GlucoseMath.currentCivilDate() }
    var weekOffset by remember(activeProfile.id) { mutableStateOf(0) }
    var selectedDate by remember(activeProfile.id) { mutableStateOf(today) }
    var viewingPhotoPath by remember { mutableStateOf<String?>(null) }

    val weekBubbles = remember(allProfileReadings, activeProfile.id, weekOffset, today) {
        GlucoseMath.buildWeekDayBubbles(
            readings = allProfileReadings,
            profileId = activeProfile.id,
            referenceDate = today,
            weekOffset = weekOffset,
            today = today
        )
    }

    val weekRangeLabel = remember(weekBubbles) {
        if (weekBubbles.isNotEmpty()) {
            GlucoseMath.formatSpanishWeekRangeLabel(
                monday = weekBubbles.first().date,
                sunday = weekBubbles.last().date
            )
        } else {
            ""
        }
    }

    val selectedDayReadings = remember(allProfileReadings, activeProfile.id, selectedDate) {
        GlucoseMath.readingsForProfileOnCivilDate(
            readings = allProfileReadings,
            profileId = activeProfile.id,
            date = selectedDate
        )
    }

    val isSelectedFuture = remember(selectedDate, today) {
        GlucoseMath.compareCivilDates(selectedDate, today) > 0
    }

    val isSelectedToday = remember(selectedDate, today) {
        selectedDate == today
    }

    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_my_week_card")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Mi semana",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = if (weekOffset == 0) {
                            "Esta semana ($weekRangeLabel)"
                        } else {
                            "Semana del $weekRangeLabel"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Controles accesibles para navegar a la semana anterior y volver a esta semana
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val nextOffset = weekOffset - 1
                        weekOffset = nextOffset
                        val newWeek = GlucoseMath.buildMondayToSundayWeek(
                            referenceDate = today,
                            weekOffset = nextOffset
                        )
                        if (selectedDate !in newWeek && newWeek.isNotEmpty()) {
                            selectedDate = newWeek.first()
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("my_week_prev_week_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Semana anterior")
                }

                if (weekOffset != 0 || !isSelectedToday) {
                    Button(
                        onClick = {
                            weekOffset = 0
                            selectedDate = today
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("my_week_return_current_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Today,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Volver a esta semana")
                    }
                }
            }

            // Siete burbujas grandes y espaciadas (lunes a domingo), repartidas en filas en móvil
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val rows = remember(weekBubbles, maxWidth.value, textScale, fontScale) {
                    planMyWeekBubbleRows(
                        bubbles = weekBubbles,
                        availableWidthDp = maxWidth.value,
                        isLargeText = textScale == TextScaleMode.LARGE,
                        fontScale = fontScale
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    rows.forEach { rowBubbles ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowBubbles.forEach { bubble ->
                                val isSelected = bubble.date == selectedDate
                                MyWeekDayBubbleItem(
                                    bubble = bubble,
                                    isSelected = isSelected,
                                    onClick = { selectedDate = bubble.date },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            // Detalle del día seleccionado: fecha clara en español, lecturas ordenadas por hora y botón para registrar
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("my_week_selected_day_panel")
            ) {
                Text(
                    text = if (isSelectedToday) {
                        "Hoy · ${GlucoseMath.formatSpanishCivilDate(selectedDate)}"
                    } else {
                        GlucoseMath.formatSpanishCivilDate(selectedDate)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (selectedDayReadings.isEmpty()) {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("my_week_empty_day_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (isSelectedFuture) {
                                    "Este día aún no llega. No se pueden registrar mediciones en días futuros."
                                } else {
                                    "No hay mediciones registradas en este día para ${activeProfile.name}."
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (!isSelectedFuture) {
                                Text(
                                    text = "Puedes anotar una medición con la fecha de este día pulsando el botón de abajo.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        selectedDayReadings.forEach { reading ->
                            val ev = GlucoseMath.evaluateReadingSeverity(
                                reading.valueMgDl,
                                reading.context,
                                activeProfile.targetMinMgDl,
                                activeProfile.targetMaxMgDl
                            )
                            val statusColors = rememberStatusColorPair(ev.severity)
                            val formattedVal = GlucoseMath.formatGlucoseValue(
                                reading.valueMgDl,
                                activeProfile.unitPreference
                            )

                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("my_week_reading_${reading.id}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "$formattedVal ${activeProfile.unitPreference.symbol}",
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontFamily = JetBrainsMonoFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Card(
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = statusColors.containerColor
                                            ),
                                            border = BorderStroke(1.dp, statusColors.borderColor)
                                        ) {
                                            Text(
                                                text = ev.label,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = statusColors.contentColor,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                            )
                                        }
                                    }

                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Schedule,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = GlucoseMath.formatTimeOfDay(reading.timestamp),
                                                style = MaterialTheme.typography.labelLarge,
                                                fontFamily = JetBrainsMonoFamily,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = contextIconFor(reading.context),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = reading.context.label,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }

                                    if (reading.quickTags.isNotEmpty()) {
                                        Text(
                                            text = "Etiquetas: ${reading.quickTags.joinToString(", ")}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    if (reading.note.isNotBlank()) {
                                        Text(
                                            text = "Nota: ${reading.note}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    if (!reading.photoPath.isNullOrBlank()) {
                                        MealPhotoAttachmentCard(
                                            photoPath = reading.photoPath,
                                            onOpenFullPhoto = { viewingPhotoPath = reading.photoPath },
                                            testTagPrefix = "my_week_photo_${reading.id}"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = { onOpenManualMeasurementForDate(selectedDate) },
                    enabled = !isSelectedFuture,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 54.dp)
                        .testTag("my_week_register_for_day_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isSelectedToday) {
                            "Registrar medición de hoy"
                        } else if (isSelectedFuture) {
                            "No disponible en días futuros"
                        } else {
                            "Registrar medición en este día"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }

    val activePhoto = viewingPhotoPath
    if (!activePhoto.isNullOrBlank()) {
        MealPhotoViewerDialog(
            photoPath = activePhoto,
            onDismiss = { viewingPhotoPath = null }
        )
    }
}

@Composable
private fun MyWeekDayBubbleItem(
    bubble: WeekDayBubble,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Selección distinguible por forma, borde grueso e icono además del color
    val shape = if (isSelected) RoundedCornerShape(22.dp) else RoundedCornerShape(16.dp)
    val border = when {
        isSelected -> BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
        bubble.isToday -> BorderStroke(2.dp, MaterialTheme.colorScheme.secondary)
        else -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)
    }
    val containerColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        bubble.isToday -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }
    val contentColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    val readingsDesc = when (bubble.readingsCount) {
        0 -> "sin mediciones"
        1 -> "1 medición"
        else -> "${bubble.readingsCount} mediciones"
    }
    val todayDesc = if (bubble.isToday) ", Hoy" else ""
    val selectedDesc = if (isSelected) "Seleccionado" else "No seleccionado"

    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = border,
        modifier = modifier
            .heightIn(min = 82.dp)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                selected = isSelected
                stateDescription = "$selectedDesc$todayDesc, $readingsDesc"
            }
            .testTag("my_week_day_bubble_${bubble.date.year}_${bubble.date.month}_${bubble.date.dayOfMonth}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = bubble.shortDayLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected || bubble.isToday) FontWeight.Bold else FontWeight.SemiBold,
                    color = contentColor,
                    textAlign = TextAlign.Center
                )
            }

            Text(
                text = bubble.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                textAlign = TextAlign.Center
            )

            if (bubble.isToday) {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = "Hoy",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            } else if (bubble.readingsCount > 0) {
                Text(
                    text = if (bubble.readingsCount == 1) "1 toma" else "${bubble.readingsCount} tomas",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

