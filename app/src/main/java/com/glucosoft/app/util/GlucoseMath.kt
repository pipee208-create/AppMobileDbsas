package com.glucosoft.app.util

import com.glucosoft.app.model.DateRangeFilter
import com.glucosoft.app.model.GlucoseReading
import com.glucosoft.app.model.GlucoseSeverity
import com.glucosoft.app.model.GlucoseUnit
import com.glucosoft.app.model.MeasurementContext
import com.glucosoft.app.model.SeverityEvaluation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

data class GlucoseStats(
    val count: Int = 0,
    val avgMgDl: Double = 0.0,
    val minMgDl: Int = 0,
    val maxMgDl: Int = 0,
    val inRangePercent: Int = 0,
    val normalCount: Int = 0,
    val cautionCount: Int = 0,
    val alertCount: Int = 0,
    val estimatedGmi: Double = 0.0
)

data class DeduplicationResult(
    val uniqueReadings: List<GlucoseReading>,
    val skippedDuplicates: Int
)

data class ManualInputValidation(
    val isEmpty: Boolean,
    val isValid: Boolean,
    val parsedMgDl: Int?,
    val errorMessage: String?
)

object GlucoseMath {
    const val MGDL_PER_MMOL = 18.0182
    private val esLocale = Locale("es", "ES")

    fun mgDlToMmol(mgDl: Number): Double {
        val raw = mgDl.toDouble() / MGDL_PER_MMOL
        return (raw * 10.0).roundToInt() / 10.0
    }

    fun mmolToMgDl(mmol: Double): Int =
        (mmol * MGDL_PER_MMOL).roundToInt()

    fun formatGlucoseValue(valueMgDl: Number, unit: GlucoseUnit): String {
        return if (unit == GlucoseUnit.MMOL_L) {
            String.format(Locale.US, "%.1f", valueMgDl.toDouble() / MGDL_PER_MMOL)
        } else {
            valueMgDl.toDouble().roundToInt().toString()
        }
    }

    fun convertForChart(valueMgDl: Number, unit: GlucoseUnit): Float {
        return if (unit == GlucoseUnit.MMOL_L) {
            mgDlToMmol(valueMgDl).toFloat()
        } else {
            valueMgDl.toDouble().roundToInt().toFloat()
        }
    }

    /**
     * Valida la entrada manual de glucosa.
     * - Si está vacía, isEmpty = true, isValid = false (Guardar deshabilitado, sin mostrar error rojo).
     * - Si tiene texto inválido o fuera de rango (25–600 mg/dL o 1.4–33.3 mmol/L), isValid = false y explica el error.
     * - Si es un número válido en rango, isValid = true y devuelve parsedMgDl.
     */
    fun validateManualGlucoseInput(rawInput: String, unit: GlucoseUnit): ManualInputValidation {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return ManualInputValidation(
                isEmpty = true,
                isValid = false,
                parsedMgDl = null,
                errorMessage = null
            )
        }

        val normalized = trimmed.replace(',', '.')
        val numeric = normalized.toDoubleOrNull()
        val minAllowed = if (unit == GlucoseUnit.MMOL_L) 1.4 else 25.0
        val maxAllowed = if (unit == GlucoseUnit.MMOL_L) 33.3 else 600.0
        val minText = if (unit == GlucoseUnit.MMOL_L) "1.4" else "25"
        val maxText = if (unit == GlucoseUnit.MMOL_L) "33.3" else "600"
        val exampleText = if (unit == GlucoseUnit.MMOL_L) "5.8" else "110"

        if (numeric == null || !numeric.isFinite()) {
            return ManualInputValidation(
                isEmpty = false,
                isValid = false,
                parsedMgDl = null,
                errorMessage = "Escribe solo números para el valor de glucosa (por ejemplo, $exampleText ${unit.symbol}). Conservamos lo que escribiste."
            )
        }

        if (numeric < minAllowed || numeric > maxAllowed) {
            return ManualInputValidation(
                isEmpty = false,
                isValid = false,
                parsedMgDl = null,
                errorMessage = "El valor debe estar entre $minText y $maxText ${unit.symbol}. Revisa el número escrito."
            )
        }

        val mgDl = if (unit == GlucoseUnit.MMOL_L) {
            mmolToMgDl(numeric)
        } else {
            numeric.roundToInt()
        }
        return ManualInputValidation(
            isEmpty = false,
            isValid = true,
            parsedMgDl = mgDl,
            errorMessage = null
        )
    }

    /**
     * Al cambiar de unidad en el formulario manual:
     * Si el campo está vacío, permanece vacío ("").
     * Si ya había texto escrito por la persona, se conserva sin inventar valores.
     */
    fun preserveInputOnUnitChange(currentInput: String): String {
        return if (currentInput.isBlank()) "" else currentInput
    }

    /**
     * Filtra lecturas de un perfil según período y momento del día.
     */
    fun filterReadings(
        readings: List<GlucoseReading>,
        rangeFilter: DateRangeFilter,
        contextFilter: MeasurementContext?,
        nowMs: Long = System.currentTimeMillis()
    ): List<GlucoseReading> {
        val cutoff = rangeFilter.days?.let { nowMs - it * 24L * 3600L * 1000L } ?: 0L
        return readings.filter { r ->
            val inDate = cutoff == 0L || r.timestamp >= cutoff
            val inContext = contextFilter == null || r.context == contextFilter
            inDate && inContext
        }
    }

    /**
     * Calcula el inicio y fin en milisegundos del día desplazado `dayOffset` respecto a `nowMs`
     * (0 = Hoy, -1 = Ayer, -2 = Hace 2 días, etc.).
     */
    fun dayBoundsForOffset(dayOffset: Int, nowMs: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = nowMs
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        cal.add(java.util.Calendar.DAY_OF_YEAR, dayOffset)
        val startMs = cal.timeInMillis
        cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
        val endExclusiveMs = cal.timeInMillis
        return startMs to endExclusiveMs
    }

    /**
     * Filtra lecturas para una fecha específica del diario interactivo (`dayOffset`: 0 = Hoy, -1 = Ayer, etc.)
     * respetando también el filtro de momento si está activo.
     */
    fun filterReadingsByDayOffset(
        readings: List<GlucoseReading>,
        dayOffset: Int,
        contextFilter: MeasurementContext?,
        nowMs: Long = System.currentTimeMillis()
    ): List<GlucoseReading> {
        val (startMs, endExclusiveMs) = dayBoundsForOffset(dayOffset, nowMs)
        return readings.filter { r ->
            val inDay = r.timestamp in startMs until endExclusiveMs
            val inContext = contextFilter == null || r.context == contextFilter
            inDay && inContext
        }
    }

    /**
     * Título legible para la barra del diario por día.
     */
    fun formatDiaryDayTitle(dayOffset: Int, nowMs: Long = System.currentTimeMillis()): String {
        val (startMs, _) = dayBoundsForOffset(dayOffset, nowMs)
        val fmt = SimpleDateFormat("d 'de' MMM", esLocale)
        val datePart = fmt.format(Date(startMs))
        return when (dayOffset) {
            0 -> "Hoy, $datePart"
            -1 -> "Ayer, $datePart"
            else -> {
                val dayFmt = SimpleDateFormat("EEE d 'de' MMM", esLocale)
                dayFmt.format(Date(startMs)).replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(esLocale) else it.toString()
                }
            }
        }
    }

    fun formatTimeOnly(timestampMs: Long): String {
        val fmt = SimpleDateFormat("HH:mm", esLocale)
        return fmt.format(Date(timestampMs))
    }

    /**
     * Evaluación orientativa según rangos configurables del perfil.
     * No constituye diagnóstico médico.
     */
    fun evaluateReadingSeverity(
        valueMgDl: Int,
        context: MeasurementContext,
        targetMinMgDl: Int = 70,
        targetMaxMgDl: Int = 140
    ): SeverityEvaluation {
        val isPostMeal =
            context == MeasurementContext.POST_MEAL_1H || context == MeasurementContext.POST_MEAL_2H
        val lowAlertThreshold = max(40, targetMinMgDl - 15)
        val effectiveUpperBound = if (isPostMeal) max(targetMaxMgDl, 180) else targetMaxMgDl
        val highAlertThreshold = max(effectiveUpperBound + 60, 240)

        return when {
            valueMgDl < lowAlertThreshold -> SeverityEvaluation(
                severity = GlucoseSeverity.ALERT,
                label = "Valor muy bajo · Atención",
                detail = "Valor orientativo muy por debajo de tu meta. Si sientes malestar, busca apoyo y comunícate con tu contacto de emergencia o profesional de salud."
            )
            valueMgDl < targetMinMgDl -> SeverityEvaluation(
                severity = GlucoseSeverity.CAUTION,
                label = "Por debajo de tu meta · Precaución",
                detail = "Resultado orientativo por debajo de tu rango configurado. Sigue las indicaciones de tu profesional de salud."
            )
            valueMgDl > highAlertThreshold -> SeverityEvaluation(
                severity = GlucoseSeverity.ALERT,
                label = "Valor muy alto · Atención",
                detail = "Valor orientativo muy elevado respecto a tu rango configurado. Consulta con tu profesional de salud según tu plan de cuidado."
            )
            valueMgDl <= effectiveUpperBound -> SeverityEvaluation(
                severity = GlucoseSeverity.NORMAL,
                label = "Dentro de tu meta orientativa",
                detail = if (isPostMeal) {
                    "Dentro de tu rango de referencia después de comer."
                } else {
                    "Dentro del rango orientativo configurado en tu perfil."
                }
            )
            else -> SeverityEvaluation(
                severity = GlucoseSeverity.CAUTION,
                label = "Por encima de tu meta · Precaución",
                detail = "Resultado orientativo superior a tu rango configurado. Estos colores son solo una guía visual y no reemplazan el criterio médico."
            )
        }
    }

    fun computeGlucoseStats(
        readings: List<GlucoseReading>,
        targetMinMgDl: Int = 70,
        targetMaxMgDl: Int = 140
    ): GlucoseStats {
        if (readings.isEmpty()) return GlucoseStats()

        var sum = 0.0
        var minVal = Int.MAX_VALUE
        var maxVal = Int.MIN_VALUE
        var normalCount = 0
        var cautionCount = 0
        var alertCount = 0

        for (r in readings) {
            sum += r.valueMgDl
            if (r.valueMgDl < minVal) minVal = r.valueMgDl
            if (r.valueMgDl > maxVal) maxVal = r.valueMgDl
            val ev = evaluateReadingSeverity(r.valueMgDl, r.context, targetMinMgDl, targetMaxMgDl)
            when (ev.severity) {
                GlucoseSeverity.NORMAL -> normalCount++
                GlucoseSeverity.CAUTION -> cautionCount++
                GlucoseSeverity.ALERT -> alertCount++
            }
        }

        val avgMgDl = sum / readings.size
        val gmiRaw = 3.31 + 0.02392 * avgMgDl
        val estimatedGmi = (gmiRaw * 10.0).roundToInt() / 10.0
        val inRangePercent = ((normalCount.toDouble() / readings.size) * 100.0).roundToInt()

        return GlucoseStats(
            count = readings.size,
            avgMgDl = avgMgDl,
            minMgDl = minVal,
            maxMgDl = maxVal,
            inRangePercent = inRangePercent,
            normalCount = normalCount,
            cautionCount = cautionCount,
            alertCount = alertCount,
            estimatedGmi = estimatedGmi
        )
    }

    fun deduplicateIncomingReadings(
        existing: List<GlucoseReading>,
        incoming: List<GlucoseReading>
    ): DeduplicationResult {
        val existingKeys = existing.map { item ->
            "${item.profileId}:${item.timestamp / 60000L}:${item.valueMgDl}"
        }.toMutableSet()

        val unique = mutableListOf<GlucoseReading>()
        var skipped = 0

        incoming.forEachIndexed { idx, candidate ->
            val key = "${candidate.profileId}:${candidate.timestamp / 60000L}:${candidate.valueMgDl}"
            if (existingKeys.contains(key)) {
                skipped++
            } else {
                existingKeys.add(key)
                unique.add(
                    candidate.copy(id = "rd_sync_${System.currentTimeMillis()}_$idx")
                )
            }
        }

        return DeduplicationResult(uniqueReadings = unique, skippedDuplicates = skipped)
    }

    fun formatShortDateTime(timestampMs: Long): String {
        val fmt = SimpleDateFormat("dd MMM, HH:mm", esLocale)
        return fmt.format(Date(timestampMs))
    }

    fun formatDayHeader(timestampMs: Long): String {
        val fmt = SimpleDateFormat("EEEE, d 'de' MMMM", esLocale)
        val raw = fmt.format(Date(timestampMs))
        return raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(esLocale) else it.toString() }
    }

    private val SPANISH_MONTH_NAMES = listOf(
        "enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"
    )

    private val SPANISH_WEEKDAY_NAMES = mapOf(
        java.util.Calendar.MONDAY to "lunes",
        java.util.Calendar.TUESDAY to "martes",
        java.util.Calendar.WEDNESDAY to "miércoles",
        java.util.Calendar.THURSDAY to "jueves",
        java.util.Calendar.FRIDAY to "viernes",
        java.util.Calendar.SATURDAY to "sábado",
        java.util.Calendar.SUNDAY to "domingo"
    )

    val SPANISH_WEEKDAY_SHORT_HEADERS = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")

    /**
     * Obtiene la fecha civil local actual (o correspondiente a `timestampMs`) en la zona horaria indicada
     * (por defecto la del teléfono, ej. `America/Bogota` UTC-5).
     */
    fun currentCivilDate(
        nowMs: Long = System.currentTimeMillis(),
        timeZone: java.util.TimeZone = java.util.TimeZone.getDefault()
    ): LocalCivilDate {
        val cal = java.util.Calendar.getInstance(timeZone, esLocale)
        cal.timeInMillis = nowMs
        return LocalCivilDate(
            year = cal.get(java.util.Calendar.YEAR),
            month = cal.get(java.util.Calendar.MONTH) + 1,
            dayOfMonth = cal.get(java.util.Calendar.DAY_OF_MONTH)
        )
    }

    /**
     * Formatea un instante en `AAAA-MM-DD HH:MM` usando la zona horaria civil local.
     */
    fun formatManualDateTimeInput(
        timestampMs: Long = System.currentTimeMillis(),
        timeZone: java.util.TimeZone = java.util.TimeZone.getDefault()
    ): String {
        val cal = java.util.Calendar.getInstance(timeZone, esLocale)
        cal.timeInMillis = timestampMs
        return String.format(
            Locale.US,
            "%04d-%02d-%02d %02d:%02d",
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH),
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE)
        )
    }

    /**
     * Calcula la cantidad de días reales del mes (`month` 1..12) del año indicado.
     */
    fun daysInMonth(year: Int, month: Int): Int {
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"), esLocale)
        cal.clear()
        cal.set(java.util.Calendar.YEAR, year)
        cal.set(java.util.Calendar.MONTH, (month - 1).coerceIn(0, 11))
        cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
        return cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
    }

    /**
     * Ajusta un día del mes al máximo válido del mes/año destino (ej. 31 -> 28/29 en febrero).
     */
    fun clampDayToMonth(year: Int, month: Int, dayOfMonth: Int): Int {
        val maxDay = daysInMonth(year, month)
        return dayOfMonth.coerceIn(1, maxDay)
    }

    /**
     * Verifica si una fecha civil (`year`, `month` 1..12, `dayOfMonth`) existe realmente en el calendario.
     */
    fun isValidCivilDate(year: Int, month: Int, dayOfMonth: Int): Boolean {
        if (year !in 1900..2100) return false
        if (month !in 1..12) return false
        val maxDay = daysInMonth(year, month)
        return dayOfMonth in 1..maxDay
    }

    /**
     * Analiza de forma estricta una cadena `AAAA-MM-DD HH:MM` como fecha y hora civil local,
     * sin desplazamientos UTC que cambien el día al día anterior en zonas como Colombia (UTC-5).
     */
    fun parseManualDateTimeOrNull(raw: String): LocalCivilDateTime? {
        val trimmed = raw.trim()
        val match = Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})\s+(\d{1,2}):(\d{2})$""").matchEntire(trimmed)
            ?: return null
        val year = match.groupValues[1].toIntOrNull() ?: return null
        val month = match.groupValues[2].toIntOrNull() ?: return null
        val day = match.groupValues[3].toIntOrNull() ?: return null
        val hour = match.groupValues[4].toIntOrNull() ?: return null
        val minute = match.groupValues[5].toIntOrNull() ?: return null

        if (!isValidCivilDate(year, month, day)) return null
        if (hour !in 0..23 || minute !in 0..59) return null

        return LocalCivilDateTime(
            date = LocalCivilDate(year = year, month = month, dayOfMonth = day),
            hour = hour,
            minute = minute
        )
    }

    /**
     * Convierte una cadena `AAAA-MM-DD HH:MM` a milisegundos epoch en la zona horaria local indicada,
     * garantizando que el día civil local coincida exactamente con el día elegido.
     */
    fun parseManualDateTimeToEpochMsOrNull(
        raw: String,
        timeZone: java.util.TimeZone = java.util.TimeZone.getDefault()
    ): Long? {
        val parsed = parseManualDateTimeOrNull(raw) ?: return null
        val cal = java.util.Calendar.getInstance(timeZone, esLocale)
        cal.isLenient = false
        cal.clear()
        cal.set(java.util.Calendar.YEAR, parsed.date.year)
        cal.set(java.util.Calendar.MONTH, parsed.date.month - 1)
        cal.set(java.util.Calendar.DAY_OF_MONTH, parsed.date.dayOfMonth)
        cal.set(java.util.Calendar.HOUR_OF_DAY, parsed.hour)
        cal.set(java.util.Calendar.MINUTE, parsed.minute)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return try {
            cal.timeInMillis
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Extrae la fecha civil (`AAAA-MM-DD`) de la entrada actual del borrador aunque la hora esté en edición;
     * si no hay una fecha civil válida, devuelve la fecha local de hoy según el teléfono.
     */
    fun extractCivilDateOrToday(
        rawDateTimeInput: String,
        nowMs: Long = System.currentTimeMillis(),
        timeZone: java.util.TimeZone = java.util.TimeZone.getDefault()
    ): LocalCivilDate {
        parseManualDateTimeOrNull(rawDateTimeInput)?.let { return it.date }
        val datePrefixMatch = Regex("""^\s*(\d{4})-(\d{1,2})-(\d{1,2})\b""").find(rawDateTimeInput)
        if (datePrefixMatch != null) {
            val y = datePrefixMatch.groupValues[1].toIntOrNull()
            val m = datePrefixMatch.groupValues[2].toIntOrNull()
            val d = datePrefixMatch.groupValues[3].toIntOrNull()
            if (y != null && m != null && d != null && isValidCivilDate(y, m, d)) {
                return LocalCivilDate(year = y, month = m, dayOfMonth = d)
            }
        }
        return currentCivilDate(nowMs = nowMs, timeZone = timeZone)
    }

    /**
     * Extrae la hora y minutos (`HH:mm`) escritos en `rawDateTimeInput`, o usa la hora local actual si no son válidos.
     */
    fun extractHourAndMinuteOrNow(
        rawDateTimeInput: String,
        nowMs: Long = System.currentTimeMillis(),
        timeZone: java.util.TimeZone = java.util.TimeZone.getDefault()
    ): Pair<Int, Int> {
        parseManualDateTimeOrNull(rawDateTimeInput)?.let { return it.hour to it.minute }
        val timeMatch = Regex("""\b(\d{1,2}):(\d{2})\s*$""").find(rawDateTimeInput.trim())
        if (timeMatch != null) {
            val h = timeMatch.groupValues[1].toIntOrNull()
            val min = timeMatch.groupValues[2].toIntOrNull()
            if (h != null && min != null && h in 0..23 && min in 0..59) {
                return h to min
            }
        }
        val cal = java.util.Calendar.getInstance(timeZone, esLocale)
        cal.timeInMillis = nowMs
        return cal.get(java.util.Calendar.HOUR_OF_DAY) to cal.get(java.util.Calendar.MINUTE)
    }

    /**
     * Al confirmar (`Aceptar`) en el calendario mensual:
     * Cambia únicamente el día civil (`AAAA-MM-DD`) por `selectedDate` y conserva exactamente
     * la hora y los minutos (`HH:mm`) que estaban introducidos en el borrador.
     */
    fun applySelectedCivilDatePreservingTime(
        currentDateTimeInput: String,
        selectedDate: LocalCivilDate,
        nowMs: Long = System.currentTimeMillis(),
        timeZone: java.util.TimeZone = java.util.TimeZone.getDefault()
    ): String {
        val (hour, minute) = extractHourAndMinuteOrNow(
            rawDateTimeInput = currentDateTimeInput,
            nowMs = nowMs,
            timeZone = timeZone
        )
        val validDay = clampDayToMonth(selectedDate.year, selectedDate.month, selectedDate.dayOfMonth)
        return String.format(
            Locale.US,
            "%04d-%02d-%02d %02d:%02d",
            selectedDate.year,
            selectedDate.month.coerceIn(1, 12),
            validDay,
            hour,
            minute
        )
    }

    /**
     * Desplaza una fecha civil por meses (`deltaMonths`, ej. -1 o +1) o años (`deltaYears`, ej. -1 o +1),
     * ajustando el día seleccionado al máximo válido del nuevo mes.
     */
    fun shiftCivilDateMonthYear(
        date: LocalCivilDate,
        deltaMonths: Int = 0,
        deltaYears: Int = 0
    ): LocalCivilDate {
        val totalZeroBasedMonth = (date.year + deltaYears) * 12 + (date.month - 1) + deltaMonths
        val newYear = Math.floorDiv(totalZeroBasedMonth, 12).coerceIn(1900, 2100)
        val newMonth = Math.floorMod(totalZeroBasedMonth, 12) + 1
        val newDay = clampDayToMonth(newYear, newMonth, date.dayOfMonth)
        return LocalCivilDate(year = newYear, month = newMonth, dayOfMonth = newDay)
    }

    /**
     * Construye las celdas de la cuadrícula mensual (lunes = columna 0 .. domingo = columna 6).
     * Los espacios vacíos antes del día 1 y al final de la última semana se representan con `null`.
     */
    fun buildSpanishMonthGridCells(year: Int, month: Int): List<Int?> {
        val safeMonth = month.coerceIn(1, 12)
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"), esLocale)
        cal.clear()
        cal.set(java.util.Calendar.YEAR, year)
        cal.set(java.util.Calendar.MONTH, safeMonth - 1)
        cal.set(java.util.Calendar.DAY_OF_MONTH, 1)

        val dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK)
        // En Calendar: MONDAY = 2 .. SUNDAY = 1 -> convertir a índice lunes=0 .. domingo=6
        val leadingEmptyCells = when (dayOfWeek) {
            java.util.Calendar.MONDAY -> 0
            java.util.Calendar.TUESDAY -> 1
            java.util.Calendar.WEDNESDAY -> 2
            java.util.Calendar.THURSDAY -> 3
            java.util.Calendar.FRIDAY -> 4
            java.util.Calendar.SATURDAY -> 5
            java.util.Calendar.SUNDAY -> 6
            else -> 0
        }

        val totalDays = daysInMonth(year, safeMonth)
        val cells = ArrayList<Int?>(leadingEmptyCells + totalDays + 7)
        repeat(leadingEmptyCells) { cells.add(null) }
        for (day in 1..totalDays) {
            cells.add(day)
        }
        while (cells.size % 7 != 0) {
            cells.add(null)
        }
        return cells
    }

    /**
     * Título en español para el encabezado del calendario mensual (ej. «Noviembre de 2026»).
     */
    fun formatSpanishMonthYearTitle(year: Int, month: Int): String {
        val monthName = SPANISH_MONTH_NAMES[(month - 1).coerceIn(0, 11)]
        val capitalizedMonth = monthName.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(esLocale) else it.toString()
        }
        return "$capitalizedMonth de $year"
    }

    /**
     * Formatea el día civil elegido en español claro para el control «Fecha de la medición»
     * (ej. «Hoy, miércoles 25 de noviembre de 2026» o «Miércoles 25 de noviembre de 2026»).
     */
    fun formatSpanishCivilDateLabel(
        date: LocalCivilDate,
        nowMs: Long = System.currentTimeMillis(),
        timeZone: java.util.TimeZone = java.util.TimeZone.getDefault()
    ): String {
        val cal = java.util.Calendar.getInstance(timeZone, esLocale)
        cal.clear()
        cal.set(java.util.Calendar.YEAR, date.year)
        cal.set(java.util.Calendar.MONTH, (date.month - 1).coerceIn(0, 11))
        cal.set(java.util.Calendar.DAY_OF_MONTH, clampDayToMonth(date.year, date.month, date.dayOfMonth))
        cal.set(java.util.Calendar.HOUR_OF_DAY, 12)

        val weekdayName = SPANISH_WEEKDAY_NAMES[cal.get(java.util.Calendar.DAY_OF_WEEK)] ?: ""
        val monthName = SPANISH_MONTH_NAMES[(date.month - 1).coerceIn(0, 11)]
        val today = currentCivilDate(nowMs = nowMs, timeZone = timeZone)

        return if (date == today) {
            "Hoy, $weekdayName ${date.dayOfMonth} de $monthName de ${date.year}"
        } else {
            val capitalizedWeekday = weekdayName.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(esLocale) else it.toString()
            }
            "$capitalizedWeekday ${date.dayOfMonth} de $monthName de ${date.year}"
        }
    }

    /**
     * Compara dos fechas civiles locales (`LocalCivilDate`).
     * Devuelve < 0 si `a` es anterior a `b`, 0 si son el mismo día, > 0 si `a` es posterior a `b`.
     */
    fun compareCivilDates(a: LocalCivilDate, b: LocalCivilDate): Int {
        if (a.year != b.year) return a.year.compareTo(b.year)
        if (a.month != b.month) return a.month.compareTo(b.month)
        return a.dayOfMonth.compareTo(b.dayOfMonth)
    }

    /**
     * Indica si `date` está en el futuro respecto al día civil actual en la zona horaria indicada.
     */
    fun isCivilDateInFuture(
        date: LocalCivilDate,
        nowMs: Long = System.currentTimeMillis(),
        timeZone: java.util.TimeZone = java.util.TimeZone.getDefault(),
        today: LocalCivilDate = currentCivilDate(nowMs = nowMs, timeZone = timeZone)
    ): Boolean {
        return compareCivilDates(date, today) > 0
    }

    /**
     * Calcula los límites `[startMs, endExclusiveMs)` en milisegundos epoch para un `LocalCivilDate`
     * en la zona horaria civil local indicada.
     */
    fun civilDateBoundsMs(
        date: LocalCivilDate,
        timeZone: java.util.TimeZone = java.util.TimeZone.getDefault()
    ): Pair<Long, Long> {
        val cal = java.util.Calendar.getInstance(timeZone, esLocale)
        cal.clear()
        cal.set(java.util.Calendar.YEAR, date.year)
        cal.set(java.util.Calendar.MONTH, (date.month - 1).coerceIn(0, 11))
        cal.set(java.util.Calendar.DAY_OF_MONTH, clampDayToMonth(date.year, date.month, date.dayOfMonth))
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val startMs = cal.timeInMillis
        cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
        val endExclusiveMs = cal.timeInMillis
        return startMs to endExclusiveMs
    }

    /**
     * Construye los 7 días de la semana de lunes a domingo en hora local para `weekOffset`
     * (`0` = esta semana, `-1` = semana anterior, `-2` = hace dos semanas, etc.).
     */
    fun buildMondayToSundayWeek(
        weekOffset: Int = 0,
        nowMs: Long = System.currentTimeMillis(),
        timeZone: java.util.TimeZone = java.util.TimeZone.getDefault(),
        referenceDate: LocalCivilDate = currentCivilDate(nowMs = nowMs, timeZone = timeZone)
    ): List<LocalCivilDate> {
        val cal = java.util.Calendar.getInstance(timeZone, esLocale)
        cal.clear()
        cal.set(java.util.Calendar.YEAR, referenceDate.year)
        cal.set(java.util.Calendar.MONTH, referenceDate.month - 1)
        cal.set(java.util.Calendar.DAY_OF_MONTH, referenceDate.dayOfMonth)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 12)

        val dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK)
        // Lunes = 0 .. Domingo = 6
        val offsetFromMonday = when (dayOfWeek) {
            java.util.Calendar.MONDAY -> 0
            java.util.Calendar.TUESDAY -> 1
            java.util.Calendar.WEDNESDAY -> 2
            java.util.Calendar.THURSDAY -> 3
            java.util.Calendar.FRIDAY -> 4
            java.util.Calendar.SATURDAY -> 5
            java.util.Calendar.SUNDAY -> 6
            else -> 0
        }
        cal.add(java.util.Calendar.DAY_OF_MONTH, -offsetFromMonday + (weekOffset * 7))

        return (0 until 7).map { idx ->
            if (idx > 0) {
                cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
            }
            LocalCivilDate(
                year = cal.get(java.util.Calendar.YEAR),
                month = cal.get(java.util.Calendar.MONTH) + 1,
                dayOfMonth = cal.get(java.util.Calendar.DAY_OF_MONTH)
            )
        }
    }

    /**
     * Construye las 7 burbujas de «Mi semana» (lunes a domingo) con conteo de mediciones reales
     * para el perfil activo (`profileId`), marca de hoy y detección de día futuro.
     */
    fun buildWeekDayBubbles(
        readings: List<GlucoseReading>,
        profileId: String,
        weekOffset: Int = 0,
        nowMs: Long = System.currentTimeMillis(),
        timeZone: java.util.TimeZone = java.util.TimeZone.getDefault(),
        referenceDate: LocalCivilDate = currentCivilDate(nowMs = nowMs, timeZone = timeZone),
        today: LocalCivilDate = currentCivilDate(nowMs = nowMs, timeZone = timeZone)
    ): List<WeekDayBubble> {
        val weekDates = buildMondayToSundayWeek(
            weekOffset = weekOffset,
            nowMs = nowMs,
            timeZone = timeZone,
            referenceDate = referenceDate
        )
        val profileReadings = readings.filter { it.profileId == profileId }

        return weekDates.mapIndexed { index, civilDate ->
            val dayReadings = readingsForProfileOnCivilDate(
                readings = profileReadings,
                profileId = profileId,
                date = civilDate,
                timeZone = timeZone
            )
            WeekDayBubble(
                date = civilDate,
                shortDayLabel = SPANISH_WEEKDAY_SHORT_HEADERS.getOrElse(index) { "" },
                dayNumber = civilDate.dayOfMonth,
                isToday = civilDate == today,
                isFuture = compareCivilDates(civilDate, today) > 0,
                readingsCount = dayReadings.size
            )
        }
    }

    /**
     * Filtra las mediciones reales de un perfil (`profileId`) correspondientes a un día civil local (`date`)
     * y las devuelve ordenadas cronológicamente por hora (`timestamp ASC`).
     */
    fun readingsForProfileOnCivilDate(
        readings: List<GlucoseReading>,
        profileId: String,
        date: LocalCivilDate,
        timeZone: java.util.TimeZone = java.util.TimeZone.getDefault()
    ): List<GlucoseReading> {
        val (startMs, endExclusiveMs) = civilDateBoundsMs(date = date, timeZone = timeZone)
        return readings
            .filter { it.profileId == profileId && it.timestamp in startMs until endExclusiveMs }
            .sortedBy { it.timestamp }
    }

    /**
     * Título legible en español para el rango de la semana mostrada en «Mi semana»
     * (ej. «23 al 29 de noviembre de 2026» o «28 de septiembre al 4 de octubre de 2026»).
     */
    fun formatSpanishWeekRangeLabel(weekDays: List<LocalCivilDate>): String {
        if (weekDays.isEmpty()) return ""
        return formatSpanishWeekRangeLabel(monday = weekDays.first(), sunday = weekDays.last())
    }

    fun formatSpanishWeekRangeLabel(
        monday: LocalCivilDate,
        sunday: LocalCivilDate
    ): String {
        val firstMonth = SPANISH_MONTH_NAMES[(monday.month - 1).coerceIn(0, 11)]
        val lastMonth = SPANISH_MONTH_NAMES[(sunday.month - 1).coerceIn(0, 11)]
        return when {
            monday.year == sunday.year && monday.month == sunday.month ->
                "${monday.dayOfMonth} al ${sunday.dayOfMonth} de $lastMonth de ${sunday.year}"
            monday.year == sunday.year ->
                "${monday.dayOfMonth} de $firstMonth al ${sunday.dayOfMonth} de $lastMonth de ${sunday.year}"
            else ->
                "${monday.dayOfMonth} de $firstMonth de ${monday.year} al ${sunday.dayOfMonth} de $lastMonth de ${sunday.year}"
        }
    }

    fun formatSpanishCivilDate(
        date: LocalCivilDate,
        timeZone: java.util.TimeZone = java.util.TimeZone.getDefault()
    ): String {
        val cal = java.util.Calendar.getInstance(timeZone, esLocale)
        cal.clear()
        cal.set(java.util.Calendar.YEAR, date.year)
        cal.set(java.util.Calendar.MONTH, (date.month - 1).coerceIn(0, 11))
        cal.set(java.util.Calendar.DAY_OF_MONTH, clampDayToMonth(date.year, date.month, date.dayOfMonth))
        cal.set(java.util.Calendar.HOUR_OF_DAY, 12)

        val weekdayName = SPANISH_WEEKDAY_NAMES[cal.get(java.util.Calendar.DAY_OF_WEEK)] ?: ""
        val monthName = SPANISH_MONTH_NAMES[(date.month - 1).coerceIn(0, 11)]
        val capitalizedWeekday = weekdayName.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(esLocale) else it.toString()
        }
        return "$capitalizedWeekday ${date.dayOfMonth} de $monthName de ${date.year}"
    }

    fun formatTimeOfDay(timestampMs: Long): String = formatTimeOnly(timestampMs)
}

/**
 * Representa un día civil local (`month` 1..12) sin desfase por zona horaria UTC.
 */
data class LocalCivilDate(
    val year: Int,
    val month: Int,
    val dayOfMonth: Int
)

data class LocalCivilDateTime(
    val date: LocalCivilDate,
    val hour: Int,
    val minute: Int
)

/**
 * Modelo de cada una de las 7 burbujas de «Mi semana» (lunes a domingo).
 */
data class WeekDayBubble(
    val date: LocalCivilDate,
    val shortDayLabel: String,
    val dayNumber: Int,
    val isToday: Boolean,
    val isFuture: Boolean,
    val readingsCount: Int
) {
    val dayOfMonth: Int
        get() = dayNumber
}
