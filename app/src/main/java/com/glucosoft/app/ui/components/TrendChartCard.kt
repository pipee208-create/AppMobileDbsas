package com.glucosoft.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.glucosoft.app.model.DateRangeFilter
import com.glucosoft.app.model.GlucoseReading
import com.glucosoft.app.model.GlucoseSeverity
import com.glucosoft.app.model.GlucoseUnit
import com.glucosoft.app.ui.theme.AmberCaution
import com.glucosoft.app.ui.theme.EmeraldNormal
import com.glucosoft.app.ui.theme.JetBrainsMonoFamily
import com.glucosoft.app.ui.theme.RoseAlert
import com.glucosoft.app.ui.theme.rememberStatusColorPair
import com.glucosoft.app.util.GlucoseMath
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Gráfico de evolución con alternativa textual completa y botones grandes
 * «Lectura anterior» / «Lectura siguiente» para no exigir acertar en un punto pequeño.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrendChartCard(
    readings: List<GlucoseReading>,
    unit: GlucoseUnit,
    targetMinMgDl: Int,
    targetMaxMgDl: Int,
    rangeFilter: DateRangeFilter,
    onSelectRange: (DateRangeFilter) -> Unit
) {
    val sorted = remember(readings) { readings.sortedBy { it.timestamp } }
    var selectedReadingId by remember(readings) {
        mutableStateOf(sorted.lastOrNull()?.id)
    }
    var showTextualAlternative by remember { mutableStateOf(true) }

    val activeIndex = remember(sorted, selectedReadingId) {
        val idx = sorted.indexOfFirst { it.id == selectedReadingId }
        if (idx >= 0) idx else sorted.lastIndex
    }
    val activeReading = sorted.getOrNull(activeIndex)
    val stats = remember(sorted, targetMinMgDl, targetMaxMgDl) {
        GlucoseMath.computeGlucoseStats(sorted, targetMinMgDl, targetMaxMgDl)
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("trend_chart_card")
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Evolución de tu glucosa (${rangeFilter.fullLabel})",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = "Elige el período que deseas revisar. Puedes recorrer cada toma con los botones grandes o leer el resumen en texto.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Selector de período en FlowRow para que nunca se corte ni en letra al 200%
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                DateRangeFilter.entries.forEach { rng ->
                    val isSelected = rng == rangeFilter
                    if (isSelected) {
                        Button(
                            onClick = { onSelectRange(rng) },
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .semantics {
                                    selected = true
                                    stateDescription = "Seleccionado"
                                }
                                .testTag("range_filter_${rng.name.lowercase()}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Seleccionado",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = rng.fullLabel, style = MaterialTheme.typography.labelLarge)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onSelectRange(rng) },
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .semantics {
                                    selected = false
                                    stateDescription = "No seleccionado"
                                }
                                .testTag("range_filter_${rng.name.lowercase()}")
                        ) {
                            Text(text = rng.fullLabel, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            if (sorted.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Aún no hay mediciones en este período (${rangeFilter.fullLabel})",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Registra una nueva medición o elige «Historial completo» para ver tus lecturas anteriores.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // Alternativa textual del resumen del período (siempre comprensible sin ver el gráfico)
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {}
                        .testTag("chart_text_summary_card")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Resumen en palabras (${stats.count} ${if (stats.count == 1) "medición" else "mediciones"}):",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val avgStr = GlucoseMath.formatGlucoseValue(stats.avgMgDl, unit)
                        val minStr = GlucoseMath.formatGlucoseValue(stats.minMgDl, unit)
                        val maxStr = GlucoseMath.formatGlucoseValue(stats.maxMgDl, unit)
                        Text(
                            text = "• Promedio: $avgStr ${unit.symbol} (${stats.inRangePercent}% dentro de tu meta).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "• Valor más bajo: $minStr ${unit.symbol} · Valor más alto: $maxStr ${unit.symbol}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "• En meta: ${stats.normalCount} · Precaución: ${stats.cautionCount} · Atención: ${stats.alertCount}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Detalle de la lectura seleccionada con botones grandes Anterior / Siguiente
                if (activeReading != null) {
                    val ev = GlucoseMath.evaluateReadingSeverity(
                        activeReading.valueMgDl,
                        activeReading.context,
                        targetMinMgDl,
                        targetMaxMgDl
                    )
                    val statusColors = rememberStatusColorPair(ev.severity)

                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Lectura seleccionada (${activeIndex + 1} de ${sorted.size})",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${GlucoseMath.formatGlucoseValue(activeReading.valueMgDl, unit)} ${unit.symbol}",
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
                                border = BorderStroke(1.dp, statusColors.borderColor)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (ev.severity == GlucoseSeverity.NORMAL) {
                                            Icons.Default.CheckCircle
                                        } else {
                                            Icons.Default.Warning
                                        },
                                        contentDescription = null,
                                        tint = statusColors.contentColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = ev.label,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = statusColors.contentColor
                                    )
                                }
                            }
                            Text(
                                text = "${GlucoseMath.formatShortDateTime(activeReading.timestamp)} · ${activeReading.context.label} · ${activeReading.deviceModel}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (sorted.size > 1) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            if (activeIndex > 0) {
                                                selectedReadingId = sorted[activeIndex - 1].id
                                            }
                                        },
                                        enabled = activeIndex > 0,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("chart_prev_point_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ChevronLeft,
                                            contentDescription = null
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Anterior")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            if (activeIndex < sorted.lastIndex) {
                                                selectedReadingId = sorted[activeIndex + 1].id
                                            }
                                        },
                                        enabled = activeIndex < sorted.lastIndex,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("chart_next_point_button")
                                    ) {
                                        Text("Siguiente")
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = null
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Gráfico visual
                val outlineColor = MaterialTheme.colorScheme.outline
                val lineStrokeColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                val primaryColor = MaterialTheme.colorScheme.primary
                val chartSemanticsDesc =
                    "Gráfico de glucosa con ${sorted.size} mediciones. Promedio ${GlucoseMath.formatGlucoseValue(stats.avgMgDl, unit)} ${unit.symbol}."

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .semantics { contentDescription = chartSemanticsDesc }
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(sorted) {
                                detectTapGestures { tapOffset ->
                                    val w = size.width.toFloat()
                                    val padLeft = 24f
                                    val padRight = 24f
                                    val plotW = max(1f, w - padLeft - padRight)
                                    val minTime = sorted.first().timestamp
                                    val maxTime = sorted.last().timestamp
                                    val span = max(1L, maxTime - minTime).toFloat()

                                    var bestId: String? = null
                                    var bestDist = Float.MAX_VALUE
                                    sorted.forEach { r ->
                                        val x = if (sorted.size == 1) {
                                            padLeft + plotW / 2f
                                        } else {
                                            padLeft + ((r.timestamp - minTime) / span) * plotW
                                        }
                                        val dist = abs(tapOffset.x - x)
                                        if (dist < bestDist) {
                                            bestDist = dist
                                            bestId = r.id
                                        }
                                    }
                                    if (bestId != null) {
                                        selectedReadingId = bestId
                                    }
                                }
                            }
                    ) {
                        val w = size.width
                        val h = size.height
                        val padLeft = 24f
                        val padRight = 24f
                        val padTop = 18f
                        val padBottom = 22f
                        val plotW = max(1f, w - padLeft - padRight)
                        val plotH = max(1f, h - padTop - padBottom)

                        val minDomainMg = min(40, sorted.minOf { it.valueMgDl } - 10).coerceAtLeast(30)
                        val maxDomainMg = max(260, sorted.maxOf { it.valueMgDl } + 15)

                        fun getY(mgDl: Int): Float {
                            val ratio = (mgDl - minDomainMg).toFloat() / max(1, maxDomainMg - minDomainMg).toFloat()
                            return padTop + plotH - ratio * plotH
                        }

                        val bandTop = getY(targetMaxMgDl)
                        val bandBottom = getY(targetMinMgDl)
                        val rectTop = min(bandTop, bandBottom)
                        val rectHeight = max(6f, abs(bandBottom - bandTop))

                        drawRoundRect(
                            color = EmeraldNormal.copy(alpha = 0.14f),
                            topLeft = Offset(padLeft, rectTop),
                            size = Size(plotW, rectHeight),
                            cornerRadius = CornerRadius(10f, 10f)
                        )

                        listOf(60, 100, 140, 180, 240).forEach { tickMg ->
                            if (tickMg in minDomainMg..maxDomainMg) {
                                val y = getY(tickMg)
                                drawLine(
                                    color = outlineColor,
                                    start = Offset(padLeft, y),
                                    end = Offset(w - padRight, y),
                                    strokeWidth = 2f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                                )
                            }
                        }

                        val minTime = sorted.first().timestamp
                        val maxTime = sorted.last().timestamp
                        val span = max(1L, maxTime - minTime).toFloat()

                        val points = sorted.map { r ->
                            val x = if (sorted.size == 1) {
                                padLeft + plotW / 2f
                            } else {
                                padLeft + ((r.timestamp - minTime) / span) * plotW
                            }
                            val y = getY(r.valueMgDl)
                            val ev = GlucoseMath.evaluateReadingSeverity(
                                r.valueMgDl,
                                r.context,
                                targetMinMgDl,
                                targetMaxMgDl
                            )
                            Triple(r, Offset(x, y), ev.severity)
                        }

                        if (points.size >= 2) {
                            val path = Path().apply {
                                moveTo(points.first().second.x, points.first().second.y)
                                points.drop(1).forEach { (_, pt, _) ->
                                    lineTo(pt.x, pt.y)
                                }
                            }
                            drawPath(
                                path = path,
                                color = lineStrokeColor,
                                style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }

                        points.forEach { (r, pt, severity) ->
                            val isSelected = activeReading?.id == r.id
                            if (isSelected) {
                                drawLine(
                                    color = primaryColor.copy(alpha = 0.45f),
                                    start = Offset(pt.x, padTop),
                                    end = Offset(pt.x, h - padBottom),
                                    strokeWidth = 4f
                                )
                            }
                            val dotColor = when (severity) {
                                GlucoseSeverity.NORMAL -> EmeraldNormal
                                GlucoseSeverity.CAUTION -> AmberCaution
                                GlucoseSeverity.ALERT -> RoseAlert
                            }
                            drawCircle(
                                color = Color.White,
                                radius = if (isSelected) 20f else 14f,
                                center = pt
                            )
                            drawCircle(
                                color = dotColor,
                                radius = if (isSelected) 15f else 10f,
                                center = pt
                            )
                        }
                    }
                }

                // Botón para mostrar/ocultar la lista textual de valores del gráfico
                OutlinedButton(
                    onClick = { showTextualAlternative = !showTextualAlternative },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("toggle_chart_text_list_button")
                ) {
                    Icon(
                        imageVector = if (showTextualAlternative) Icons.Default.ShowChart else Icons.Default.List,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (showTextualAlternative) {
                            "Ocultar lista escrita de estas mediciones"
                        } else {
                            "Ver lista escrita de estas mediciones"
                        },
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                if (showTextualAlternative) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.testTag("chart_textual_reading_list")
                    ) {
                        sorted.reversed().take(5).forEach { item ->
                            val ev = GlucoseMath.evaluateReadingSeverity(
                                item.valueMgDl,
                                item.context,
                                targetMinMgDl,
                                targetMaxMgDl
                            )
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "${GlucoseMath.formatGlucoseValue(item.valueMgDl, unit)} ${unit.symbol} — ${ev.label}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${GlucoseMath.formatShortDateTime(item.timestamp)} · ${item.context.label}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            val minFormatted = GlucoseMath.formatGlucoseValue(targetMinMgDl, unit)
            val maxFormatted = GlucoseMath.formatGlucoseValue(targetMaxMgDl, unit)
            Text(
                text = "Aviso orientativo: Los colores y rangos mostrados ($minFormatted–$maxFormatted ${unit.symbol}) son configurables en tu perfil y sirven solo como guía visual. No sustituyen un diagnóstico ni indicación médica profesional.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
