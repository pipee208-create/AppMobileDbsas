package com.glucosoft.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.glucosoft.app.model.BiomedicalCatalog
import com.glucosoft.app.model.LocalProfile
import com.glucosoft.app.model.MeasurementContext
import com.glucosoft.app.model.PatientTab
import com.glucosoft.app.model.TextScaleMode

import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow

fun contextIconFor(context: MeasurementContext): ImageVector {
    return when (context) {
        MeasurementContext.FASTING -> Icons.Default.WbSunny
        MeasurementContext.PRE_MEAL -> Icons.Default.Restaurant
        MeasurementContext.POST_MEAL_1H -> Icons.Default.Timer
        MeasurementContext.POST_MEAL_2H -> Icons.Default.Schedule
        MeasurementContext.BEDTIME -> Icons.Default.Bedtime
        MeasurementContext.SYMPTOMS -> Icons.Default.HealthAndSafety
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GlucosoftHeaderBar(
    activeProfileName: String?,
    activeProfileUnitSymbol: String?,
    currentTab: PatientTab,
    onNavigateBackToHome: (() -> Unit)?,
    onOpenProfilesTab: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp
    ) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .padding(end = 12.dp)
            ) {
                if (onNavigateBackToHome != null && currentTab != PatientTab.HOME) {
                    OutlinedButton(
                        onClick = onNavigateBackToHome,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("header_back_home_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver a Inicio",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Inicio",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                } else {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "G",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Text(
                    text = if (currentTab == PatientTab.HOME) "Glucosoft" else currentTab.label,
                    style = MaterialTheme.typography.titleLarge.copy(
                        lineBreak = LineBreak.Simple,
                        hyphens = Hyphens.None
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                    modifier = Modifier
                        .semantics { heading() }
                        .testTag("header_app_title")
                )
            }

            // El perfil activo se muestra una sola vez en el encabezado; si es largo o con letra al 200 %, baja a una segunda fila legible con salto por palabras
            if (activeProfileName != null) {
                OutlinedButton(
                    onClick = onOpenProfilesTab,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("header_active_profile_chip")
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Perfil activo",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (activeProfileUnitSymbol != null) {
                            "$activeProfileName · $activeProfileUnitSymbol"
                        } else {
                            activeProfileName
                        },
                        style = MaterialTheme.typography.labelLarge.copy(
                            lineBreak = LineBreak.Simple,
                            hyphens = Hyphens.None
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * Barra superior de perfil activo y acceso agrupado «Pantalla y letra»
 * con las preferencias actuales visibles.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActiveProfileAndDisplayBar(
    profiles: List<LocalProfile>,
    activeProfile: LocalProfile,
    isDark: Boolean,
    textScale: TextScaleMode,
    onSelectProfile: (String) -> Unit,
    onGoToProfilesTab: () -> Unit,
    onOpenDisplaySettings: () -> Unit
) {
    val scaleLabel = if (textScale == TextScaleMode.LARGE) "Letra grande" else "Letra normal"
    val themeLabel = if (isDark) "Modo oscuro" else "Modo claro"

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("active_profile_display_bar")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Perfil activo: ${activeProfile.name} (${activeProfile.unitPreference.symbol})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (profiles.size > 1 || profiles.size < BiomedicalCatalog.MAX_LOCAL_PROFILES) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    profiles.forEach { prof ->
                        val isSelected = prof.id == activeProfile.id
                        if (isSelected) {
                            Button(
                                onClick = { onSelectProfile(prof.id) },
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("profile_chip_${prof.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Perfil seleccionado",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = prof.name, style = MaterialTheme.typography.labelLarge)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { onSelectProfile(prof.id) },
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("profile_chip_${prof.id}")
                            ) {
                                Text(
                                    text = "Cambiar a ${prof.name}",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }

                    if (profiles.size < BiomedicalCatalog.MAX_LOCAL_PROFILES) {
                        OutlinedButton(
                            onClick = onGoToProfilesTab,
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("add_profile_shortcut_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PersonAdd,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Otro perfil", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            // Visible grouped access "Pantalla y letra" showing current preferences
            OutlinedButton(
                onClick = onOpenDisplaySettings,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("open_display_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.FormatSize,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Pantalla y letra",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Actual: $scaleLabel · $themeLabel",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Diálogo amplio de «Pantalla y letra» que agrupa tamaño de letra,
 * modo claro/oscuro y acceso a la guía rápida.
 */
@Composable
fun DisplayAndTextSettingsDialog(
    isOpen: Boolean,
    isDark: Boolean,
    textScale: TextScaleMode,
    onToggleTextScale: () -> Unit,
    onToggleTheme: () -> Unit,
    onOpenGuide: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen) return
    val isLarge = textScale == TextScaleMode.LARGE

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Pantalla y letra",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() }
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.testTag("display_settings_dialog")
            ) {
                Text(
                    text = "Ajusta el tamaño de las letras y el brillo de la pantalla para leer con mayor comodidad.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Tamaño de letra
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
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
                                imageVector = Icons.Default.FormatSize,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Tamaño de letra: ${if (isLarge) "Grande" else "Normal"}",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Button(
                            onClick = onToggleTextScale,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .testTag("toggle_text_scale_button")
                        ) {
                            Text(
                                text = if (isLarge) "Cambiar a letra normal" else "Activar letra grande",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }

                // Tema claro / oscuro
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
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
                                imageVector = if (isDark) Icons.Default.DarkMode else Icons.Default.LightMode,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Color de pantalla: ${if (isDark) "Modo oscuro" else "Modo claro"}",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        OutlinedButton(
                            onClick = onToggleTheme,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .testTag("toggle_theme_button")
                        ) {
                            Text(
                                text = if (isDark) "Cambiar a modo claro" else "Cambiar a modo oscuro",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }

                // Guía rápida
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onOpenGuide()
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .testTag("open_guide_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Abrir guía rápida paso a paso",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Listo", style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}

@Composable
fun ActionFeedbackBanner(message: String?) {
    AnimatedVisibility(visible = !message.isNullOrBlank()) {
        if (message != null) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag("action_feedback_banner")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Confirmación",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

private data class GuideStepSpec(
    val title: String,
    val description: String,
    val actionLabel: String,
    val targetTab: PatientTab?,
    val openMeasureModal: Boolean,
    val icon: ImageVector
)

private val GUIDE_STEPS = listOf(
    GuideStepSpec(
        title = "1. Crea o elige tu perfil personal",
        description = "Solo escribe tu nombre y elige tu unidad de medida (mg/dL o mmol/L). Hasta 3 personas pueden usar la aplicación en el mismo teléfono con sus datos separados.",
        actionLabel = "Ir a Perfiles",
        targetTab = PatientTab.PROFILES,
        openMeasureModal = false,
        icon = Icons.Default.People
    ),
    GuideStepSpec(
        title = "2. Registra tu medición",
        description = "Toca «Registrar medición» para anotar el número de tu glucómetro a mano en una pantalla amplia, o recibirlo por Bluetooth.",
        actionLabel = "Registrar medición",
        targetTab = null,
        openMeasureModal = true,
        icon = Icons.Default.MonitorHeart
    ),
    GuideStepSpec(
        title = "3. Conecta un glucómetro",
        description = "En la sección Medidor puedes buscar tu glucómetro Bluetooth compatible o consultar los medidores con protocolo confirmado.",
        actionLabel = "Ir a Medidor",
        targetTab = PatientTab.DEVICES,
        openMeasureModal = false,
        icon = Icons.Default.Bluetooth
    ),
    GuideStepSpec(
        title = "4. Historial, informe PDF y ayuda",
        description = "En Historial revisa tus lecturas con letra grande y guarda tu informe en PDF para el médico. En Ayuda tienes tu contacto de emergencia siempre a mano.",
        actionLabel = "Abrir Historial",
        targetTab = PatientTab.HISTORY,
        openMeasureModal = false,
        icon = Icons.Default.History
    )
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GuidedOnboardingCard(
    isOpen: Boolean,
    onClose: () -> Unit,
    onJumpToTab: (PatientTab) -> Unit,
    onOpenMeasureModal: () -> Unit
) {
    if (!isOpen) return
    var stepIndex by remember { mutableIntStateOf(0) }
    val current = GUIDE_STEPS[stepIndex]
    val isLast = stepIndex == GUIDE_STEPS.lastIndex

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("guided_onboarding_card")
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = current.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Guía rápida · Paso ${stepIndex + 1} de ${GUIDE_STEPS.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = current.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.semantics { heading() }
                        )
                    }
                }
                OutlinedButton(
                    onClick = onClose,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("close_guide_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Cerrar", style = MaterialTheme.typography.labelMedium)
                }
            }

            Text(
                text = current.description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.semantics {
                    contentDescription = "Paso ${stepIndex + 1} de ${GUIDE_STEPS.size}"
                }
            ) {
                GUIDE_STEPS.forEachIndexed { idx, _ ->
                    Box(
                        modifier = Modifier
                            .height(10.dp)
                            .width(if (idx == stepIndex) 32.dp else 12.dp)
                            .clip(CircleShape)
                            .background(
                                if (idx == stepIndex) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline
                                }
                            )
                    )
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        onClose()
                        if (current.openMeasureModal) {
                            onOpenMeasureModal()
                        } else if (current.targetTab != null) {
                            onJumpToTab(current.targetTab)
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .heightIn(min = 52.dp)
                        .testTag("guide_action_button")
                ) {
                    Text(text = current.actionLabel, style = MaterialTheme.typography.labelLarge)
                }

                if (stepIndex > 0) {
                    OutlinedButton(
                        onClick = { stepIndex-- },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.heightIn(min = 52.dp)
                    ) {
                        Icon(imageVector = Icons.Default.ChevronLeft, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Anterior", style = MaterialTheme.typography.labelLarge)
                    }
                }

                if (isLast) {
                    OutlinedButton(
                        onClick = onClose,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .heightIn(min = 52.dp)
                            .testTag("skip_guide_button")
                    ) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("¡Listo, empezar!", style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    OutlinedButton(
                        onClick = { stepIndex++ },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.heightIn(min = 52.dp)
                    ) {
                        Text("Siguiente paso", style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null)
                    }
                    TextButton(
                        onClick = onClose,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("skip_guide_button")
                    ) {
                        Text("Omitir guía", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
