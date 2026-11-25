package com.glucosoft.app.ui.screens

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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.glucosoft.app.model.DateRangeFilter
import com.glucosoft.app.model.GlucoseReading
import com.glucosoft.app.model.GlucoseSeverity
import com.glucosoft.app.model.LocalProfile
import com.glucosoft.app.model.MeasurementContext
import com.glucosoft.app.ui.components.MealPhotoAttachmentCard
import com.glucosoft.app.ui.components.MealPhotoViewerDialog
import com.glucosoft.app.ui.components.TrendChartCard
import com.glucosoft.app.ui.components.contextIconFor
import com.glucosoft.app.ui.theme.JetBrainsMonoFamily
import com.glucosoft.app.ui.theme.RoseAlert
import com.glucosoft.app.ui.theme.rememberStatusColorPair
import com.glucosoft.app.util.GlucoseMath

/**
 * Pantalla de Historial estilo Diario Interactivo por Día:
 * - Navegador de fechas reales («Día anterior», «Hoy», «Día siguiente») que carga las lecturas de esa fecha,
 *   con estado vacío, botón de regreso a Hoy y botón para volver al período completo.
 * - Filtros de Período y Momento que muestran el rótulo y la selección una sola vez, con todas las opciones y su marca de selección accesibles.
 * - Acceso tipo burbuja a «Mi tendencia» que abre el gráfico con su alternativa de lista.
 * - Diario por día: cada fila muestra hora, cifra con unidad y momento; tocar la lectura o «Ver detalles»
 *   abre y cierra todas sus notas, etiquetas, origen y datos originales.
 * - Tarjeta separada para guardar o compartir el PDF, y confirmación detallada antes de eliminar.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(
    activeProfile: LocalProfile,
    allProfileReadings: List<GlucoseReading>,
    filteredReadings: List<GlucoseReading>,
    rangeFilter: DateRangeFilter,
    contextFilter: MeasurementContext?,
    onSelectRange: (DateRangeFilter) -> Unit,
    onSelectContext: (MeasurementContext?) -> Unit,
    onSavePdf: () -> Unit,
    onSharePdf: () -> Unit,
    onDeleteReading: (String) -> Unit
) {
    var expandedReadingId by remember { mutableStateOf<String?>(null) }
    var readingPendingDelete by remember { mutableStateOf<GlucoseReading?>(null) }
    var viewingPhotoPath by remember { mutableStateOf<String?>(null) }
    var showPeriodList by remember { mutableStateOf(false) }
    var showContextList by remember { mutableStateOf(false) }
    var showTrendChart by remember { mutableStateOf(false) }
    // null = viendo período completo; 0 = Hoy; -1 = Ayer; -2 = Hace 2 días...
    var selectedDayOffset by remember { mutableStateOf<Int?>(null) }

    val activeReadingsToShow = remember(allProfileReadings, filteredReadings, selectedDayOffset, contextFilter) {
        val offset = selectedDayOffset
        if (offset != null) {
            GlucoseMath.filterReadingsByDayOffset(
                readings = allProfileReadings,
                dayOffset = offset,
                contextFilter = contextFilter
            )
        } else {
            filteredReadings
        }
    }

    val groupedByDay = remember(activeReadingsToShow) {
        activeReadingsToShow.groupBy { GlucoseMath.formatDayHeader(it.timestamp) }
    }
    val stats = remember(activeReadingsToShow, activeProfile) {
        GlucoseMath.computeGlucoseStats(
            activeReadingsToShow,
            activeProfile.targetMinMgDl,
            activeProfile.targetMaxMgDl
        )
    }

    val currentContextLabel = contextFilter?.label ?: "Todos los momentos"
    val dayOffsetValue = selectedDayOffset

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 1. Navegador interactivo del Diario por Día («Día anterior», «Hoy», «Día siguiente»)
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("history_day_navigator_card")
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
                        imageVector = Icons.Default.Today,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (dayOffsetValue != null) {
                            GlucoseMath.formatDiaryDayTitle(dayOffsetValue)
                        } else {
                            "Diario de glucosa"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() }
                    )
                }

                // Controles interactivos por fecha: Día anterior · Hoy · Día siguiente
                // Se apilan o distribuyen sin apretar tres botones con icono en una sola fila estrecha.
                val fontScale = LocalDensity.current.fontScale
                val isTodaySelected = dayOffsetValue == 0

                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val stackDayButtons = fontScale > 1.15f || maxWidth < 340.dp

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isTodaySelected) {
                            Button(
                                onClick = { selectedDayOffset = 0 },
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .semantics {
                                        selected = true
                                        stateDescription = "Seleccionado"
                                    }
                                    .testTag("diary_today_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Seleccionado",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Hoy",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        } else {
                            OutlinedButton(
                                onClick = { selectedDayOffset = 0 },
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .semantics {
                                        selected = false
                                        stateDescription = "No seleccionado"
                                    }
                                    .testTag("diary_today_button")
                            ) {
                                Text(
                                    text = "Hoy",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }

                        if (stackDayButtons) {
                            OutlinedButton(
                                onClick = {
                                    selectedDayOffset = (selectedDayOffset ?: 0) - 1
                                },
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .testTag("diary_prev_day_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronLeft,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Día anterior",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    val current = selectedDayOffset ?: 0
                                    if (current < 0) {
                                        selectedDayOffset = current + 1
                                    }
                                },
                                enabled = (selectedDayOffset ?: 0) < 0,
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .testTag("diary_next_day_button")
                            ) {
                                Text(
                                    text = "Día siguiente",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        selectedDayOffset = (selectedDayOffset ?: 0) - 1
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 52.dp)
                                        .testTag("diary_prev_day_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ChevronLeft,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Día anterior",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        val current = selectedDayOffset ?: 0
                                        if (current < 0) {
                                            selectedDayOffset = current + 1
                                        }
                                    },
                                    enabled = (selectedDayOffset ?: 0) < 0,
                                    shape = RoundedCornerShape(14.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 52.dp)
                                        .testTag("diary_next_day_button")
                                ) {
                                    Text(
                                        text = "Día siguiente",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Si se activa un filtro de día, muestra claramente cómo volver al período completo
                if (dayOffsetValue != null) {
                    OutlinedButton(
                        onClick = { selectedDayOffset = null },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                            .testTag("diary_clear_day_filter_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Volver al período (${rangeFilter.fullLabel})",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }

        // 2. Filtros de Período y Momento (rótulo y selección mostrados una sola vez)
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("history_header_card")
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Filtro 1: Período (rótulo y selección mostrados una sola vez)
                OutlinedButton(
                    onClick = { showPeriodList = !showPeriodList },
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 58.dp)
                        .testTag("toggle_period_filter_list_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Período",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = rangeFilter.fullLabel,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Icon(
                        imageVector = if (showPeriodList) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (showPeriodList) "Ocultar opciones de período" else "Mostrar opciones de período"
                    )
                }

                AnimatedVisibility(visible = showPeriodList) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        DateRangeFilter.entries.forEach { rng ->
                            val isSelected = rangeFilter == rng && selectedDayOffset == null
                            if (isSelected) {
                                Button(
                                    onClick = {
                                        selectedDayOffset = null
                                        onSelectRange(rng)
                                        showPeriodList = false
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 52.dp)
                                        .semantics {
                                            selected = true
                                            stateDescription = "Seleccionado"
                                        }
                                        .testTag("history_period_option_${rng.name.lowercase()}")
                                ) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = "Seleccionado")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = rng.fullLabel,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        selectedDayOffset = null
                                        onSelectRange(rng)
                                        showPeriodList = false
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 52.dp)
                                        .semantics {
                                            selected = false
                                            stateDescription = "No seleccionado"
                                        }
                                        .testTag("history_period_option_${rng.name.lowercase()}")
                                ) {
                                    Text(
                                        text = rng.fullLabel,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                        }
                    }
                }

                // Filtro 2: Momento del día (rótulo y selección mostrados una sola vez)
                OutlinedButton(
                    onClick = { showContextList = !showContextList },
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 58.dp)
                        .testTag("toggle_context_filter_list_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Momento del día",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = currentContextLabel,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Icon(
                        imageVector = if (showContextList) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (showContextList) "Ocultar opciones de momento" else "Mostrar opciones de momento"
                    )
                }

                AnimatedVisibility(visible = showContextList) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        val allSelected = contextFilter == null
                        if (allSelected) {
                            Button(
                                onClick = {
                                    onSelectContext(null)
                                    showContextList = false
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .semantics {
                                        selected = true
                                        stateDescription = "Seleccionado"
                                    }
                                    .testTag("history_context_option_all")
                            ) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = "Seleccionado")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Todos los momentos",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    onSelectContext(null)
                                    showContextList = false
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .semantics {
                                        selected = false
                                        stateDescription = "No seleccionado"
                                    }
                                    .testTag("history_context_option_all")
                            ) {
                                Text(
                                    text = "Todos los momentos",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }

                        MeasurementContext.entries.forEach { ctx ->
                            val isSelected = contextFilter == ctx
                            if (isSelected) {
                                Button(
                                    onClick = {
                                        onSelectContext(ctx)
                                        showContextList = false
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 52.dp)
                                        .semantics {
                                            selected = true
                                            stateDescription = "Seleccionado"
                                        }
                                        .testTag("history_context_option_${ctx.rawId}")
                                ) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = "Seleccionado")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(imageVector = contextIconFor(ctx), contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = ctx.label,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        onSelectContext(ctx)
                                        showContextList = false
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 52.dp)
                                        .semantics {
                                            selected = false
                                            stateDescription = "No seleccionado"
                                        }
                                        .testTag("history_context_option_${ctx.rawId}")
                                ) {
                                    Icon(
                                        imageVector = contextIconFor(ctx),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = ctx.label,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Acceso tipo burbuja a «Mi tendencia» (abre/cierra el gráfico con su alternativa de lista)
        val trendSubtitle = if (stats.count > 0) {
            val avgStr = GlucoseMath.formatGlucoseValue(stats.avgMgDl, activeProfile.unitPreference)
            if (showTrendChart) {
                "Ocultar gráfico y lista"
            } else {
                "Promedio $avgStr ${activeProfile.unitPreference.symbol} (${stats.inRangePercent}% en meta) · Toca para abrir"
            }
        } else {
            "Ver gráfico y resumen en lista"
        }

        BubbleTaskCard(
            icon = Icons.Default.ShowChart,
            title = "Mi tendencia",
            subtitle = trendSubtitle,
            onClick = { showTrendChart = !showTrendChart },
            testTag = "history_toggle_trend_button"
        )

        AnimatedVisibility(visible = showTrendChart) {
            TrendChartCard(
                readings = activeReadingsToShow,
                unit = activeProfile.unitPreference,
                targetMinMgDl = activeProfile.targetMinMgDl,
                targetMaxMgDl = activeProfile.targetMaxMgDl,
                rangeFilter = rangeFilter,
                onSelectRange = { rng ->
                    selectedDayOffset = null
                    onSelectRange(rng)
                }
            )
        }

        // 4. Diario por día: cada fila muestra hora, cifra con unidad y momento; tocar abre/cierra el detalle
        if (groupedByDay.isEmpty()) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("diary_empty_day_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (dayOffsetValue != null) {
                            "No hay mediciones en esta fecha"
                        } else {
                            "No hay mediciones en este filtro"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (dayOffsetValue != null && dayOffsetValue != 0) {
                        Button(
                            onClick = { selectedDayOffset = 0 },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .testTag("diary_return_today_button")
                        ) {
                            Icon(imageVector = Icons.Default.Today, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Volver a Hoy",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            selectedDayOffset = null
                            onSelectRange(DateRangeFilter.ALL)
                            onSelectContext(null)
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .testTag("diary_show_all_history_button")
                    ) {
                        Text(
                            text = "Ver período completo",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        } else {
            groupedByDay.forEach { (dayLabel, items) ->
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = dayLabel,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .semantics { heading() }
                    )

                    items.forEach { item ->
                        val isExpanded = expandedReadingId == item.id
                        val ev = GlucoseMath.evaluateReadingSeverity(
                            item.valueMgDl,
                            item.context,
                            activeProfile.targetMinMgDl,
                            activeProfile.targetMaxMgDl
                        )
                        val statusColors = rememberStatusColorPair(ev.severity)
                        val valueStr = GlucoseMath.formatGlucoseValue(
                            item.valueMgDl,
                            activeProfile.unitPreference
                        )

                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(
                                width = if (isExpanded) 2.dp else 1.dp,
                                color = if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .clickable(role = Role.Button) {
                                    expandedReadingId = if (isExpanded) null else item.id
                                }
                                .testTag("reading_card_${item.id}")
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Fila principal del diario: Hora y Momento con icono (salta a otra línea si compiten)
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Hora clara sin repetir la fecha del encabezado del día
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
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
                                                text = GlucoseMath.formatTimeOnly(item.timestamp),
                                                style = MaterialTheme.typography.labelLarge,
                                                fontFamily = JetBrainsMonoFamily,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }

                                    // Momento con icono reconocible
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = contextIconFor(item.context),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = item.context.label,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                // Cifra y unidad juntas + estado en palabras de alto contraste
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "$valueStr ${activeProfile.unitPreference.symbol}",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = statusColors.containerColor
                                        ),
                                        border = BorderStroke(1.5.dp, statusColors.borderColor)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (ev.severity == GlucoseSeverity.NORMAL) {
                                                    Icons.Default.CheckCircle
                                                } else {
                                                    Icons.Default.Warning
                                                },
                                                contentDescription = null,
                                                tint = statusColors.contentColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = ev.label,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = statusColors.contentColor
                                            )
                                        }
                                    }
                                }

                                // Botón explícito «Ver detalles» / «Ocultar detalles»
                                OutlinedButton(
                                    onClick = {
                                        expandedReadingId = if (isExpanded) null else item.id
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("detail_reading_${item.id}")
                                ) {
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isExpanded) "Ocultar detalles" else "Ver detalles",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }

                                // Panel desplegable con notas, etiquetas, origen, datos originales y acción separada de eliminar
                                AnimatedVisibility(visible = isExpanded) {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("expanded_detail_${item.id}")
                                    ) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                                        Card(
                                            shape = RoundedCornerShape(16.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                                            )
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(14.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = "Notas: ${item.note.ifBlank { "Sin notas personales" }}",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "Etiquetas: ${if (item.quickTags.isNotEmpty()) item.quickTags.joinToString(", ") else "Sin etiquetas"}",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "Origen: ${item.captureMethod.displayLabel} · Equipo: ${item.deviceModel}",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                val origValueFormatted = GlucoseMath.formatGlucoseValue(
                                                    item.valueMgDl,
                                                    item.originalUnit
                                                )
                                                Text(
                                                    text = "Datos originales: $origValueFormatted ${item.originalUnit.symbol} (${item.valueMgDl} mg/dL) · ${GlucoseMath.formatShortDateTime(item.timestamp)}",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = ev.detail,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        if (!item.photoPath.isNullOrBlank()) {
                                            MealPhotoAttachmentCard(
                                                photoPath = item.photoPath,
                                                onOpenFullPhoto = { viewingPhotoPath = item.photoPath },
                                                testTagPrefix = "history_photo_${item.id}"
                                            )
                                        }

                                        OutlinedButton(
                                            onClick = { readingPendingDelete = item },
                                            shape = RoundedCornerShape(14.dp),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = MaterialTheme.colorScheme.error
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 48.dp)
                                                .testTag("delete_reading_${item.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Eliminar esta medición")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Tarjeta exclusiva para Guardar o Compartir el Informe PDF (separada de eliminar lecturas)
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("history_pdf_export_card")
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Informe médico en PDF",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = "${activeReadingsToShow.size} ${if (activeReadingsToShow.size == 1) "lectura incluida" else "lecturas incluidas"} para ${activeProfile.name}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = onSavePdf,
                    enabled = activeReadingsToShow.isNotEmpty(),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .testTag("history_save_pdf_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Guardar informe PDF",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                OutlinedButton(
                    onClick = onSharePdf,
                    enabled = activeReadingsToShow.isNotEmpty(),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .testTag("history_share_pdf_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Compartir informe PDF",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }

    // Diálogo de confirmación que detalla exactamente cuál medición se va a eliminar
    val pendingDel = readingPendingDelete
    if (pendingDel != null) {
        val valFormatted = GlucoseMath.formatGlucoseValue(
            pendingDel.valueMgDl,
            activeProfile.unitPreference
        )
        AlertDialog(
            onDismissRequest = { readingPendingDelete = null },
            title = {
                Text(
                    text = "¿Eliminar esta medición?",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() }
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Se eliminará permanentemente esta lectura de ${activeProfile.name}:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Valor: $valFormatted ${activeProfile.unitPreference.symbol}",
                                style = MaterialTheme.typography.titleLarge,
                                fontFamily = JetBrainsMonoFamily
                            )
                            Text(
                                text = "Momento: ${pendingDel.context.label}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Fecha y hora: ${GlucoseMath.formatShortDateTime(pendingDel.timestamp)}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteReading(pendingDel.id)
                        readingPendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RoseAlert,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("confirm_delete_reading_button")
                ) {
                    Text("Sí, eliminar medición")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { readingPendingDelete = null },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("No, conservar")
                }
            }
        )
    }

    val activePhoto = viewingPhotoPath
    if (!activePhoto.isNullOrBlank()) {
        MealPhotoViewerDialog(
            photoPath = activePhoto,
            onDismiss = { viewingPhotoPath = null }
        )
    }
}
