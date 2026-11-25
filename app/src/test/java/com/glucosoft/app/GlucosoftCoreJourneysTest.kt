package com.glucosoft.app

import com.glucosoft.app.model.BiomedicalCatalog
import com.glucosoft.app.model.CaptureMethod
import com.glucosoft.app.model.DateRangeFilter
import com.glucosoft.app.model.EmergencyContact
import com.glucosoft.app.model.GlucoseReading
import com.glucosoft.app.model.GlucoseSeverity
import com.glucosoft.app.model.GlucoseUnit
import com.glucosoft.app.model.LocalProfile
import com.glucosoft.app.model.ManualEntryDraft
import com.glucosoft.app.model.MeasurementContext
import com.glucosoft.app.model.PatientTab
import com.glucosoft.app.ui.theme.calculateContrastRatio
import com.glucosoft.app.ui.theme.statusColorPairFor
import com.glucosoft.app.util.GlucoseMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas unitarias aisladas con datos ficticios exclusivos de prueba.
 * Verifica conversión de unidades, inicio vacío en registro manual, cambio de unidad en vacío,
 * contraste WCAG >= 4.5:1 de colores de estado en ambos temas, filtros y diario interactivo día por día.
 */
class GlucosoftCoreJourneysTest {

    @Test
    fun `manual entry starts empty and remains empty after switching units in empty state`() {
        val emptyDraft = ManualEntryDraft(
            profileId = "p_1",
            unit = GlucoseUnit.MG_DL,
            context = MeasurementContext.FASTING
        )
        assertEquals("", emptyDraft.valueInput)
        assertFalse(emptyDraft.hasAuthenticContent)

        // Validación en vacío -> isEmpty = true, isValid = false y sin error rojo (Guardar deshabilitado)
        val initialValidation = GlucoseMath.validateManualGlucoseInput("", GlucoseUnit.MG_DL)
        assertTrue(initialValidation.isEmpty)
        assertFalse(initialValidation.isValid)
        assertNull(initialValidation.parsedMgDl)
        assertNull(initialValidation.errorMessage)

        // Cambio de unidad en vacío (mg/dL -> mmol/L) conserva vacío sin inventar cifras
        val afterUnitSwitchToMmol = GlucoseMath.preserveInputOnUnitChange(emptyDraft.valueInput)
        assertEquals("", afterUnitSwitchToMmol)
        val mmolEmptyValidation = GlucoseMath.validateManualGlucoseInput(afterUnitSwitchToMmol, GlucoseUnit.MMOL_L)
        assertTrue(mmolEmptyValidation.isEmpty)
        assertFalse(mmolEmptyValidation.isValid)
        assertNull(mmolEmptyValidation.parsedMgDl)
        assertNull(mmolEmptyValidation.errorMessage)

        // Validación de valor válido en mg/dL y mmol/L
        val validMg = GlucoseMath.validateManualGlucoseInput("114", GlucoseUnit.MG_DL)
        assertTrue(validMg.isValid)
        assertEquals(114, validMg.parsedMgDl)

        val validMmol = GlucoseMath.validateManualGlucoseInput("6.5", GlucoseUnit.MMOL_L)
        assertTrue(validMmol.isValid)
        assertNotNull(validMmol.parsedMgDl)

        // Valor fuera de rango
        val outOfBoundsMg = GlucoseMath.validateManualGlucoseInput("12", GlucoseUnit.MG_DL)
        assertFalse(outOfBoundsMg.isValid)
        assertNull(outOfBoundsMg.parsedMgDl)
        assertNotNull(outOfBoundsMg.errorMessage)
    }

    @Test
    fun `status color pairs satisfy WCAG AA contrast ratio of at least 4_5 to 1 in light and dark themes`() {
        for (isDark in listOf(false, true)) {
            for (severity in GlucoseSeverity.entries) {
                val pair = statusColorPairFor(severity = severity, isDark = isDark)
                val ratio = calculateContrastRatio(
                    foreground = pair.contentColor,
                    background = pair.containerColor
                )
                assertTrue(
                    "Contraste insuficiente ($ratio) para $severity en isDark=$isDark",
                    ratio >= 4.5
                )
            }
        }
    }

    @Test
    fun `interactive daily diary filters by Hoy, Dia anterior, Dia siguiente and returns to full period`() {
        val nowMs = 1_732_456_800_000L // Fecha fija de referencia para pruebas aisladas
        val (todayStart, _) = GlucoseMath.dayBoundsForOffset(0, nowMs)
        val (yesterdayStart, _) = GlucoseMath.dayBoundsForOffset(-1, nowMs)
        val (fourDaysAgoStart, _) = GlucoseMath.dayBoundsForOffset(-4, nowMs)

        val isolatedReadings = listOf(
            GlucoseReading(
                id = "today_fasting",
                profileId = "p_1",
                valueMgDl = 108,
                timestamp = todayStart + 8 * 3_600_000L,
                context = MeasurementContext.FASTING,
                deviceModel = "OKMeter Match II"
            ),
            GlucoseReading(
                id = "today_post_meal",
                profileId = "p_1",
                valueMgDl = 142,
                timestamp = todayStart + 14 * 3_600_000L,
                context = MeasurementContext.POST_MEAL_2H,
                deviceModel = "OKMeter Match II"
            ),
            GlucoseReading(
                id = "yesterday_night",
                profileId = "p_1",
                valueMgDl = 119,
                timestamp = yesterdayStart + 21 * 3_600_000L,
                context = MeasurementContext.BEDTIME,
                deviceModel = "OKMeter Match II"
            ),
            GlucoseReading(
                id = "four_days_ago_fasting",
                profileId = "p_1",
                valueMgDl = 102,
                timestamp = fourDaysAgoStart + 7 * 3_600_000L,
                context = MeasurementContext.FASTING,
                deviceModel = "OKMeter Match II"
            )
        )

        // 1. Tocar "Hoy" (offset 0) carga las 2 lecturas de hoy
        val todayReadings = GlucoseMath.filterReadingsByDayOffset(
            readings = isolatedReadings,
            dayOffset = 0,
            contextFilter = null,
            nowMs = nowMs
        )
        assertEquals(2, todayReadings.size)

        // 2. Tocar "Día anterior" (offset -1) carga la lectura de ayer
        val yesterdayReadings = GlucoseMath.filterReadingsByDayOffset(
            readings = isolatedReadings,
            dayOffset = -1,
            contextFilter = null,
            nowMs = nowMs
        )
        assertEquals(1, yesterdayReadings.size)
        assertEquals("yesterday_night", yesterdayReadings.first().id)

        // 3. Tocar un día sin lecturas (offset -2) devuelve estado vacío
        val emptyDayReadings = GlucoseMath.filterReadingsByDayOffset(
            readings = isolatedReadings,
            dayOffset = -2,
            contextFilter = null,
            nowMs = nowMs
        )
        assertTrue(emptyDayReadings.isEmpty())

        // 4. Volver al período completo (7 días) recupera las 4 lecturas; con filtro de momento Ayunas recupera 2
        val fullWeekAll = GlucoseMath.filterReadings(
            readings = isolatedReadings,
            rangeFilter = DateRangeFilter.DAYS_7,
            contextFilter = null,
            nowMs = nowMs
        )
        assertEquals(4, fullWeekAll.size)

        val fullWeekFasting = GlucoseMath.filterReadings(
            readings = isolatedReadings,
            rangeFilter = DateRangeFilter.DAYS_7,
            contextFilter = MeasurementContext.FASTING,
            nowMs = nowMs
        )
        assertEquals(2, fullWeekFasting.size)
    }

    @Test
    fun `conversions between mg_dL and mmol_L preserve clinical accuracy`() {
        val mgDl = 126
        val mmol = GlucoseMath.mgDlToMmol(mgDl)
        assertEquals(7.0, mmol, 0.05)

        val convertedBack = GlucoseMath.mmolToMgDl(7.0)
        assertEquals(126, convertedBack)

        assertEquals("126", GlucoseMath.formatGlucoseValue(126, GlucoseUnit.MG_DL))
        assertEquals("7.0", GlucoseMath.formatGlucoseValue(126, GlucoseUnit.MMOL_L))
    }

    @Test
    fun `orientative clinical evaluation differentiates fasting and postprandial contexts`() {
        val profile = LocalProfile(
            id = "test_profile_1",
            name = "Rosa María (Prueba aislada)",
            unitPreference = GlucoseUnit.MG_DL,
            targetMinMgDl = 70,
            targetMaxMgDl = 130,
            accentHex = "#0F766E"
        )

        val lowAlert = GlucoseMath.evaluateReadingSeverity(
            valueMgDl = 50,
            context = MeasurementContext.FASTING,
            targetMinMgDl = profile.targetMinMgDl,
            targetMaxMgDl = profile.targetMaxMgDl
        )
        assertEquals(GlucoseSeverity.ALERT, lowAlert.severity)

        val lowCaution = GlucoseMath.evaluateReadingSeverity(
            valueMgDl = 62,
            context = MeasurementContext.FASTING,
            targetMinMgDl = profile.targetMinMgDl,
            targetMaxMgDl = profile.targetMaxMgDl
        )
        assertEquals(GlucoseSeverity.CAUTION, lowCaution.severity)

        val inRangeFasting = GlucoseMath.evaluateReadingSeverity(
            valueMgDl = 112,
            context = MeasurementContext.FASTING,
            targetMinMgDl = profile.targetMinMgDl,
            targetMaxMgDl = profile.targetMaxMgDl
        )
        assertEquals(GlucoseSeverity.NORMAL, inRangeFasting.severity)
        assertEquals("Dentro de tu meta orientativa", inRangeFasting.label)

        val elevatedFasting = GlucoseMath.evaluateReadingSeverity(
            valueMgDl = 150,
            context = MeasurementContext.FASTING,
            targetMinMgDl = profile.targetMinMgDl,
            targetMaxMgDl = profile.targetMaxMgDl
        )
        assertEquals(GlucoseSeverity.CAUTION, elevatedFasting.severity)

        val inRangePostMeal = GlucoseMath.evaluateReadingSeverity(
            valueMgDl = 150,
            context = MeasurementContext.POST_MEAL_2H,
            targetMinMgDl = profile.targetMinMgDl,
            targetMaxMgDl = profile.targetMaxMgDl
        )
        assertEquals(GlucoseSeverity.NORMAL, inRangePostMeal.severity)

        val criticalHigh = GlucoseMath.evaluateReadingSeverity(
            valueMgDl = 265,
            context = MeasurementContext.POST_MEAL_2H,
            targetMinMgDl = profile.targetMinMgDl,
            targetMaxMgDl = profile.targetMaxMgDl
        )
        assertEquals(GlucoseSeverity.ALERT, criticalHigh.severity)
    }

    @Test
    fun `deduplication detects identical reading within same minute bucket on same profile`() {
        val bucketMinuteTimestamp = (1_732_450_000_000L / 60_000L) * 60_000L
        val existing = listOf(
            GlucoseReading(
                id = "r_1",
                profileId = "p_1",
                valueMgDl = 118,
                timestamp = bucketMinuteTimestamp + 5_000L,
                context = MeasurementContext.FASTING,
                captureMethod = CaptureMethod.MANUAL,
                deviceModel = "OKMeter Match II"
            )
        )

        val incomingDuplicateAndDifferentProfile = listOf(
            GlucoseReading(
                id = "r_dup",
                profileId = "p_1",
                valueMgDl = 118,
                timestamp = bucketMinuteTimestamp + 25_000L,
                context = MeasurementContext.FASTING,
                captureMethod = CaptureMethod.BLUETOOTH,
                deviceModel = "OKMeter Match II"
            ),
            GlucoseReading(
                id = "r_other_profile",
                profileId = "p_2",
                valueMgDl = 118,
                timestamp = bucketMinuteTimestamp + 25_000L,
                context = MeasurementContext.FASTING,
                captureMethod = CaptureMethod.MANUAL,
                deviceModel = "GlucoLeader Enhance"
            )
        )

        val result = GlucoseMath.deduplicateIncomingReadings(existing, incomingDuplicateAndDifferentProfile)
        assertEquals(1, result.skippedDuplicates)
        assertEquals(1, result.uniqueReadings.size)
        assertEquals("p_2", result.uniqueReadings.first().profileId)
    }

    @Test
    fun `inventory preserves 5 navigation sections, 3 profiles limit, 5 biomedical meters, and stats calculation`() {
        assertEquals(5, PatientTab.entries.size)
        assertEquals(
            listOf("Inicio", "Historial", "Medidor", "Ayuda", "Perfiles"),
            PatientTab.entries.map { it.label }
        )
        assertEquals(3, BiomedicalCatalog.MAX_LOCAL_PROFILES)
        assertEquals(5, BiomedicalCatalog.BIOMEDICAL_DEVICES.size)

        val contact = EmergencyContact(
            name = "Dra. Elena Gómez",
            relation = "Médica tratante",
            phone = "+52 55 1234 5678"
        )
        assertTrue(contact.phone.filter { it.isDigit() }.length >= 7)

        val sampleReadings = listOf(
            GlucoseReading(
                id = "1",
                profileId = "p_1",
                valueMgDl = 105,
                timestamp = 1_732_450_000_000L,
                context = MeasurementContext.FASTING,
                deviceModel = "OKMeter Match II"
            ),
            GlucoseReading(
                id = "2",
                profileId = "p_1",
                valueMgDl = 135,
                timestamp = 1_732_460_000_000L,
                context = MeasurementContext.POST_MEAL_2H,
                deviceModel = "OKMeter Match II"
            )
        )
        val stats = GlucoseMath.computeGlucoseStats(sampleReadings, targetMinMgDl = 70, targetMaxMgDl = 140)
        assertEquals(2, stats.count)
        assertEquals(120.0, stats.avgMgDl, 0.01)
        assertEquals(100, stats.inRangePercent)
    }

    @Test
    fun `spanish monthly calendar changes month and year, preserves exact HH_mm time, and keeps local civil day in Colombia UTC-5`() {
        val bogotaTz = java.util.TimeZone.getTimeZone("America/Bogota")

        // Fecha inicial del borrador con hora y minutos específicos (07:45) en zona horaria de Colombia (UTC-5)
        val initialDraftDateTime = "2026-11-25 07:45"
        val extractedInitial = GlucoseMath.extractCivilDateOrToday(
            rawDateTimeInput = initialDraftDateTime,
            timeZone = bogotaTz
        )
        assertEquals(com.glucosoft.app.util.LocalCivilDate(2026, 11, 25), extractedInitial)

        // Navegar en el calendario mensual: cambiar al mes anterior (octubre) y año anterior (2025) y elegir el día 14
        val afterPrevMonth = GlucoseMath.shiftCivilDateMonthYear(extractedInitial, deltaMonths = -1)
        assertEquals(com.glucosoft.app.util.LocalCivilDate(2026, 10, 25), afterPrevMonth)
        assertEquals("Octubre de 2026", GlucoseMath.formatSpanishMonthYearTitle(afterPrevMonth.year, afterPrevMonth.month))

        val afterPrevYear = GlucoseMath.shiftCivilDateMonthYear(afterPrevMonth, deltaYears = -1)
        assertEquals(com.glucosoft.app.util.LocalCivilDate(2025, 10, 25), afterPrevYear)

        val chosenDate = afterPrevYear.copy(dayOfMonth = 14)

        // Aceptar cambia únicamente el día civil y conserva exactamente la hora y los minutos (07:45)
        val updatedDateTimeInput = GlucoseMath.applySelectedCivilDatePreservingTime(
            currentDateTimeInput = initialDraftDateTime,
            selectedDate = chosenDate,
            timeZone = bogotaTz
        )
        assertEquals("2025-10-14 07:45", updatedDateTimeInput)

        // Verificar que al convertir a timestamp y volver a leer en Colombia (UTC-5) NO se desplaza al día anterior (13 de octubre)
        val savedEpochMs = GlucoseMath.parseManualDateTimeToEpochMsOrNull(
            raw = updatedDateTimeInput,
            timeZone = bogotaTz
        )
        assertNotNull(savedEpochMs)
        val roundTripFormatted = GlucoseMath.formatManualDateTimeInput(
            timestampMs = savedEpochMs!!,
            timeZone = bogotaTz
        )
        assertEquals("2025-10-14 07:45", roundTripFormatted)

        // Incluso a las 00:05 de la madrugada en Colombia (UTC-5), el día civil local sigue siendo el 14 de octubre
        val midnightInput = GlucoseMath.applySelectedCivilDatePreservingTime(
            currentDateTimeInput = "2026-11-25 00:05",
            selectedDate = chosenDate,
            timeZone = bogotaTz
        )
        assertEquals("2025-10-14 00:05", midnightInput)
        val midnightEpochMs = GlucoseMath.parseManualDateTimeToEpochMsOrNull(
            raw = midnightInput,
            timeZone = bogotaTz
        )
        assertNotNull(midnightEpochMs)
        assertEquals(
            com.glucosoft.app.util.LocalCivilDate(2025, 10, 14),
            GlucoseMath.currentCivilDate(nowMs = midnightEpochMs!!, timeZone = bogotaTz)
        )

        // Al reabrir el calendario sobre el borrador actualizado, aparece la fecha del borrador (2025-10-14)
        val reopenedDate = GlucoseMath.extractCivilDateOrToday(
            rawDateTimeInput = updatedDateTimeInput,
            timeZone = bogotaTz
        )
        assertEquals(chosenDate, reopenedDate)
        assertEquals(
            "Martes 14 de octubre de 2025",
            GlucoseMath.formatSpanishCivilDateLabel(reopenedDate, nowMs = savedEpochMs + 86_400_000L, timeZone = bogotaTz)
        )

        // Cuadrícula mensual de octubre 2025 (31 días, múltiplo de 7 columnas Lun..Dom)
        val oct2025Cells = GlucoseMath.buildSpanishMonthGridCells(2025, 10)
        assertEquals(0, oct2025Cells.size % 7)
        assertEquals((1..31).toList(), oct2025Cells.filterNotNull())
    }

    @Test
    fun `Mi semana builds Monday to Sunday week, filters profile readings sorted by time, blocks future days and distributes bubbles in accessible rows`() {
        val bogotaTz = java.util.TimeZone.getTimeZone("America/Bogota")
        // Miércoles 25 de noviembre de 2026 -> semana lunes 23 al domingo 29 de noviembre de 2026
        val today = com.glucosoft.app.util.LocalCivilDate(2026, 11, 25)
        val weekDates = GlucoseMath.buildMondayToSundayWeek(referenceDate = today, weekOffset = 0)
        assertEquals(7, weekDates.size)
        assertEquals(com.glucosoft.app.util.LocalCivilDate(2026, 11, 23), weekDates.first())
        assertEquals(com.glucosoft.app.util.LocalCivilDate(2026, 11, 29), weekDates.last())

        // Semana anterior (offset -1): lunes 16 al domingo 22 de noviembre de 2026
        val prevWeekDates = GlucoseMath.buildMondayToSundayWeek(referenceDate = today, weekOffset = -1)
        assertEquals(com.glucosoft.app.util.LocalCivilDate(2026, 11, 16), prevWeekDates.first())
        assertEquals(com.glucosoft.app.util.LocalCivilDate(2026, 11, 22), prevWeekDates.last())

        val (wedStartMs, _) = GlucoseMath.civilDateBoundsMs(today, bogotaTz)
        val readings = listOf(
            GlucoseReading(
                id = "r_afternoon",
                profileId = "p_maria",
                valueMgDl = 148,
                timestamp = wedStartMs + 14 * 3_600_000L,
                context = MeasurementContext.POST_MEAL_2H,
                deviceModel = "Glucocare Match (GLM-76L)",
                quickTags = listOf("Hice ejercicio"),
                note = "Almuerzo con ensalada",
                photoPath = "meal_123.jpg"
            ),
            GlucoseReading(
                id = "r_morning",
                profileId = "p_maria",
                valueMgDl = 104,
                timestamp = wedStartMs + 7 * 3_600_000L,
                context = MeasurementContext.FASTING,
                deviceModel = "Glucocare Match (GLM-76L)"
            ),
            GlucoseReading(
                id = "r_other_profile",
                profileId = "p_carlos",
                valueMgDl = 190,
                timestamp = wedStartMs + 9 * 3_600_000L,
                context = MeasurementContext.FASTING,
                deviceModel = "Glucocare Match (GLM-76L)"
            )
        )

        val bubbles = GlucoseMath.buildWeekDayBubbles(
            readings = readings,
            profileId = "p_maria",
            referenceDate = today,
            weekOffset = 0,
            today = today,
            timeZone = bogotaTz
        )
        assertEquals(7, bubbles.size)
        assertEquals(listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"), bubbles.map { it.shortDayLabel })

        val wedBubble = bubbles[2]
        assertTrue(wedBubble.isToday)
        assertFalse(wedBubble.isFuture)
        assertEquals(2, wedBubble.readingsCount)

        val thuBubble = bubbles[3]
        assertFalse(thuBubble.isToday)
        assertTrue(thuBubble.isFuture)
        assertTrue(GlucoseMath.isCivilDateInFuture(thuBubble.date, today = today))
        assertFalse(GlucoseMath.isCivilDateInFuture(wedBubble.date, today = today))

        // Al tocar el miércoles, devuelve solo las lecturas de p_maria ordenadas por hora ascendente (mañana -> tarde)
        val selectedDayReadings = GlucoseMath.readingsForProfileOnCivilDate(
            readings = readings,
            profileId = "p_maria",
            date = today,
            timeZone = bogotaTz
        )
        assertEquals(listOf("r_morning", "r_afternoon"), selectedDayReadings.map { it.id })
        assertEquals("meal_123.jpg", selectedDayReadings[1].photoPath)

        // En móvil normal reparte en 4+3; con letra grande o fontScale alto reparte en 3+2+2 (nunca 7 en una sola fila)
        val normalRows = com.glucosoft.app.ui.screens.planMyWeekBubbleRows(
            bubbles = bubbles,
            availableWidthDp = 360f,
            isLargeText = false,
            fontScale = 1.0f
        )
        assertEquals(listOf(4, 3), normalRows.map { it.size })

        val largeTextRows = com.glucosoft.app.ui.screens.planMyWeekBubbleRows(
            bubbles = bubbles,
            availableWidthDp = 360f,
            isLargeText = true,
            fontScale = 1.35f
        )
        assertEquals(listOf(3, 2, 2), largeTextRows.map { it.size })
    }

    @Test
    fun `MealPhotoStorage saves private photo copy, reads bytes, resolves relative filename and deletes cleanly`() {
        val tempDir = java.nio.file.Files.createTempDirectory("glucosoft_meal_photos_test").toFile()
        try {
            val sampleBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3, 4, 5)
            val savedFileName = com.glucosoft.app.util.MealPhotoStorage.savePhotoBytes(
                baseFilesDir = tempDir,
                bytes = sampleBytes,
                preferredFileName = "meal_test_photo.jpg"
            )
            assertNotNull(savedFileName)
            assertEquals("meal_test_photo.jpg", savedFileName)

            val resolvedFile = com.glucosoft.app.util.MealPhotoStorage.resolvePhotoFile(
                baseFilesDir = tempDir,
                photoPath = savedFileName
            )
            assertNotNull(resolvedFile)
            assertTrue(resolvedFile!!.exists())
            assertTrue(com.glucosoft.app.util.MealPhotoStorage.photoExists(tempDir, savedFileName))

            val readBack = com.glucosoft.app.util.MealPhotoStorage.readPhotoBytes(
                baseFilesDir = tempDir,
                photoPath = savedFileName
            )
            assertNotNull(readBack)
            assertTrue(sampleBytes.contentEquals(readBack!!))

            val deleted = com.glucosoft.app.util.MealPhotoStorage.deletePhoto(
                baseFilesDir = tempDir,
                photoPath = savedFileName
            )
            assertTrue(deleted)
            assertFalse(
                com.glucosoft.app.util.MealPhotoStorage.photoExists(
                    baseFilesDir = tempDir,
                    photoPath = savedFileName
                )
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `ReminderManager computes daily alarm trigger, resolves Registrar ahora without auto-saving glucose and snoozes 15 minutes on Mas tarde`() {
        val bogotaTz = java.util.TimeZone.getTimeZone("America/Bogota")
        // 2026-11-25 07:30:00 en Bogotá
        val baseCal = java.util.Calendar.getInstance(bogotaTz).apply {
            set(2026, java.util.Calendar.NOVEMBER, 25, 7, 30, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val now0730 = baseCal.timeInMillis

        // 1. Si el recordatorio es a las 08:00 y son las 07:30, se programa para HOY a las 08:00
        val triggerToday0800 = com.glucosoft.app.service.ReminderManager.computeNextDailyTriggerAtMs(
            hour = 8,
            minute = 0,
            nowMs = now0730,
            timeZone = bogotaTz
        )
        assertEquals(now0730 + 30 * 60_000L, triggerToday0800)

        // 2. Si ya pasaron las 08:00 (ej. 09:00), se programa para MAÑANA a las 08:00
        val now0900 = now0730 + 90 * 60_000L
        val triggerTomorrow0800 = com.glucosoft.app.service.ReminderManager.computeNextDailyTriggerAtMs(
            hour = 8,
            minute = 0,
            nowMs = now0900,
            timeZone = bogotaTz
        )
        val expectedTomorrowCal = java.util.Calendar.getInstance(bogotaTz).apply {
            set(2026, java.util.Calendar.NOVEMBER, 26, 8, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        assertEquals(expectedTomorrowCal.timeInMillis, triggerTomorrow0800)

        // 3. Acción «Más tarde» pospone exactamente 15 minutos una vez por toque
        val snoozeResolution = com.glucosoft.app.service.ReminderManager.resolveNotificationAction(
            action = com.glucosoft.app.service.ReminderManager.ACTION_SNOOZE_REMINDER,
            profileId = "p_elena",
            nowMs = now0900
        )
        assertNotNull(snoozeResolution)
        assertEquals(
            com.glucosoft.app.service.ReminderActionType.SNOOZE_15_MINUTES,
            snoozeResolution!!.actionType
        )
        assertEquals(
            now0900 + com.glucosoft.app.service.ReminderManager.SNOOZE_DURATION_MS,
            snoozeResolution.snoozeTargetEpochMs
        )
        assertFalse(snoozeResolution.opensManualEntryForm)
        assertFalse(snoozeResolution.autoSavesGlucoseValue)

        // 4. Acción «Registrar ahora» selecciona el perfil correcto, abre el formulario manual y NUNCA guarda un valor automático
        val registerNowResolution = com.glucosoft.app.service.ReminderManager.resolveNotificationAction(
            action = com.glucosoft.app.service.ReminderManager.ACTION_OPEN_MANUAL_FROM_REMINDER,
            profileId = "p_elena",
            nowMs = now0900
        )
        assertNotNull(registerNowResolution)
        assertEquals("p_elena", registerNowResolution!!.profileId)
        assertTrue(registerNowResolution.opensManualEntryForm)
        assertFalse("Registrar ahora jamás debe guardar un valor automático", registerNowResolution.autoSavesGlucoseValue)
    }

    @Test
    fun `encrypted backup and restore preserves profiles, readings, tags, notes, meal photo bytes and reminders, rejects wrong password or corruption, and merges idempotently`() {
        val sourceBaseDir = java.nio.file.Files.createTempDirectory("glucosoft_backup_src_files").toFile()
        val targetBaseDir = java.nio.file.Files.createTempDirectory("glucosoft_backup_dst_files").toFile()

        try {
            val photoBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 10, 20, 30, 40, 50)
            val savedPhotoName = com.glucosoft.app.util.MealPhotoStorage.savePhotoBytes(
                baseFilesDir = sourceBaseDir,
                bytes = photoBytes,
                preferredFileName = "meal_desayuno_arepa.jpg"
            )
            assertEquals("meal_desayuno_arepa.jpg", savedPhotoName)

            val profileMaria = LocalProfile(
                id = "prof_maria",
                name = "María López",
                unitPreference = GlucoseUnit.MG_DL,
                targetMinMgDl = 75,
                targetMaxMgDl = 135,
                accentHex = "#0F766E",
                emergencyContact = EmergencyContact("Carlos López", "Hijo", "3101234567")
            )
            val profileJorge = LocalProfile(
                id = "prof_jorge",
                name = "Jorge Pérez",
                unitPreference = GlucoseUnit.MMOL_L,
                targetMinMgDl = 70,
                targetMaxMgDl = 140,
                accentHex = "#1D4ED8"
            )

            val readingMariaWithPhoto = GlucoseReading(
                id = "read_m_1",
                profileId = "prof_maria",
                valueMgDl = 128,
                originalUnit = GlucoseUnit.MG_DL,
                timestamp = 1_764_050_000_000L,
                context = MeasurementContext.POST_MEAL_2H,
                captureMethod = CaptureMethod.MANUAL,
                deviceId = com.glucosoft.app.model.BiomedicalDeviceId.Glucocare_Match,
                deviceModel = "Glucocare Match (GLM-76L)",
                quickTags = listOf("Hice ejercicio", "Comida fuera de lo habitual"),
                note = "Arepa con queso y café",
                photoPath = savedPhotoName
            )
            val readingJorge = GlucoseReading(
                id = "read_j_1",
                profileId = "prof_jorge",
                valueMgDl = 108,
                originalUnit = GlucoseUnit.MMOL_L,
                timestamp = 1_764_060_000_000L,
                context = MeasurementContext.FASTING,
                captureMethod = CaptureMethod.BLUETOOTH,
                deviceId = com.glucosoft.app.model.BiomedicalDeviceId.Glucocare_Match,
                deviceModel = "Glucocare Match (GLM-76L)",
                quickTags = emptyList(),
                note = "En ayunas"
            )

            val reminderMaria = com.glucosoft.app.model.ProfileReminderConfig(
                profileId = "prof_maria",
                enabled = true,
                hour = 7,
                minute = 30
            )

            val snapshot = com.glucosoft.app.data.BackupManager.buildSnapshotFromLocalData(
                profiles = listOf(profileMaria, profileJorge),
                readings = listOf(readingMariaWithPhoto, readingJorge),
                reminders = listOf(reminderMaria),
                activeProfileId = "prof_maria",
                isDarkTheme = true,
                textScaleMode = com.glucosoft.app.model.TextScaleMode.LARGE,
                baseFilesDir = sourceBaseDir,
                exportedAtMs = 1_764_070_000_000L
            )
            assertEquals(1, snapshot.photosByReadingId.size)
            assertEquals("meal_desayuno_arepa.jpg", snapshot.photosByReadingId["read_m_1"]?.fileName)

            val encryptResult = com.glucosoft.app.data.BackupManager.createEncryptedBackupBytes(
                password = "MiClaveSegura2026",
                snapshot = snapshot
            )
            assertTrue(encryptResult is com.glucosoft.app.data.BackupOperationResult.Success)
            val encryptedBytes = (encryptResult as com.glucosoft.app.data.BackupOperationResult.Success).value

            // 1. Contraseña incorrecta debe rechazarse ANTES de tocar datos locales
            val wrongPasswordAttempt = com.glucosoft.app.data.BackupManager.decryptAndValidateBackupBytes(
                password = "ClaveEquivocada",
                encryptedBytes = encryptedBytes
            )
            assertTrue(wrongPasswordAttempt is com.glucosoft.app.data.BackupOperationResult.Failure)
            assertEquals(
                com.glucosoft.app.data.BackupFailureReason.WRONG_PASSWORD_OR_TAMPERED,
                (wrongPasswordAttempt as com.glucosoft.app.data.BackupOperationResult.Failure).reason
            )

            // 2. Archivo truncado o corrupto debe rechazarse sin modificar el estado
            val corruptedBytes = encryptedBytes.copyOf().also {
                it[it.size - 1] = (it[it.size - 1].toInt() xor 0xFF).toByte()
            }
            val corruptedAttempt = com.glucosoft.app.data.BackupManager.decryptAndValidateBackupBytes(
                password = "MiClaveSegura2026",
                encryptedBytes = corruptedBytes
            )
            assertTrue(corruptedAttempt is com.glucosoft.app.data.BackupOperationResult.Failure)

            // 3. Restaurar en otro teléfono que ya tiene una medición local previa:
            //    conserva la medición local existente, restaura perfiles, lecturas, etiquetas, notas,
            //    recordatorios y bytes reales de la foto de comida
            val existingLocalReading = GlucoseReading(
                id = "read_local_existing",
                profileId = "prof_maria",
                valueMgDl = 99,
                originalUnit = GlucoseUnit.MG_DL,
                timestamp = 1_764_010_000_000L,
                context = MeasurementContext.FASTING,
                captureMethod = CaptureMethod.MANUAL,
                deviceModel = "Glucocare Match (GLM-76L)"
            )

            val firstMergeResult = com.glucosoft.app.data.BackupManager.restoreAndMergeFromEncryptedBytes(
                password = "MiClaveSegura2026",
                encryptedBytes = encryptedBytes,
                existingProfiles = listOf(profileMaria),
                existingReadings = listOf(existingLocalReading),
                existingReminders = emptyList(),
                currentActiveProfileId = "prof_maria",
                baseFilesDir = targetBaseDir
            )
            assertTrue(firstMergeResult is com.glucosoft.app.data.BackupOperationResult.Success)
            val firstMerge = (firstMergeResult as com.glucosoft.app.data.BackupOperationResult.Success).value

            assertEquals(2, firstMerge.profilesToSave.size)
            assertEquals(3, firstMerge.readingsToSave.size)
            assertEquals(1, firstMerge.summary.restoredPhotosCount)
            assertEquals(1, firstMerge.summary.restoredRemindersCount)
            assertTrue(firstMerge.summary.message.contains("Datos recuperados correctamente"))

            val restoredPhotoBytes = com.glucosoft.app.util.MealPhotoStorage.readPhotoBytes(
                baseFilesDir = targetBaseDir,
                photoPath = "meal_desayuno_arepa.jpg"
            )
            assertNotNull(restoredPhotoBytes)
            assertTrue(photoBytes.contentEquals(restoredPhotoBytes!!))

            val restoredMariaReading = firstMerge.readingsToSave.first { it.id == "read_m_1" }
            assertEquals(listOf("Hice ejercicio", "Comida fuera de lo habitual"), restoredMariaReading.quickTags)
            assertEquals("Arepa con queso y café", restoredMariaReading.note)
            assertEquals("meal_desayuno_arepa.jpg", restoredMariaReading.photoPath)

            // 4. Repetir la restauración del mismo archivo es idempotente:
            //    0 lecturas nuevas duplicadas, 0 perfiles duplicados y sin cruzar perfiles
            val secondMergeResult = com.glucosoft.app.data.BackupManager.restoreAndMergeFromEncryptedBytes(
                password = "MiClaveSegura2026",
                encryptedBytes = encryptedBytes,
                existingProfiles = firstMerge.profilesToSave,
                existingReadings = firstMerge.readingsToSave,
                existingReminders = firstMerge.remindersToSave,
                currentActiveProfileId = firstMerge.activeProfileId,
                baseFilesDir = targetBaseDir
            )
            assertTrue(secondMergeResult is com.glucosoft.app.data.BackupOperationResult.Success)
            val secondMerge = (secondMergeResult as com.glucosoft.app.data.BackupOperationResult.Success).value

            assertEquals(2, secondMerge.profilesToSave.size)
            assertEquals(3, secondMerge.readingsToSave.size)
            assertEquals(0, secondMerge.summary.newReadingsAdded)
            assertEquals(2, secondMerge.summary.skippedDuplicateReadings)
            assertEquals(
                2,
                secondMerge.readingsToSave.count { it.profileId == "prof_maria" }
            )
            assertEquals(
                1,
                secondMerge.readingsToSave.count { it.profileId == "prof_jorge" }
            )
        } finally {
            sourceBaseDir.deleteRecursively()
            targetBaseDir.deleteRecursively()
        }
    }
}

