package com.glucosoft.app.service

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.glucosoft.app.model.GlucoseReading
import com.glucosoft.app.model.GlucoseSeverity
import com.glucosoft.app.model.LocalProfile
import com.glucosoft.app.util.GlucoseMath
import java.io.File
import java.io.FileOutputStream
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PdfExportResult(
    val file: File,
    val fileName: String,
    val sharedViaSystem: Boolean
)

object ClinicalPdfReportGenerator {

    private const val PAGE_WIDTH = 595 // A4 points (72 dpi)
    private const val PAGE_HEIGHT = 842
    private const val MARGIN_X = 36f

    fun exportClinicalPdfReport(
        context: Context,
        profile: LocalProfile,
        readings: List<GlucoseReading>,
        rangeLabel: String,
        shareIfAvailable: Boolean
    ): PdfExportResult {
        val sorted = readings.sortedByDescending { it.timestamp }
        val stats = GlucoseMath.computeGlucoseStats(
            sorted,
            profile.targetMinMgDl,
            profile.targetMaxMgDl
        )
        val unit = profile.unitPreference

        val document = PdfDocument()
        val rowsPerFirstPage = 16
        val rowsPerNextPage = 22

        val chunks = mutableListOf<List<GlucoseReading>>()
        if (sorted.isEmpty()) {
            chunks.add(emptyList())
        } else {
            chunks.add(sorted.take(rowsPerFirstPage))
            var offset = rowsPerFirstPage
            while (offset < sorted.size) {
                chunks.add(sorted.drop(offset).take(rowsPerNextPage))
                offset += rowsPerNextPage
            }
        }

        val totalPages = chunks.size
        chunks.forEachIndexed { pageIdx, pageReadings ->
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIdx + 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            var currentY = drawHeader(canvas, rangeLabel)
            if (pageIdx == 0) {
                currentY = drawSummarySection(canvas, profile, stats, currentY)
            } else {
                currentY += 18f
            }

            drawReadingsTable(canvas, profile, pageReadings, currentY)
            drawFooter(canvas, pageIdx + 1, totalPages)
            document.finishPage(page)
        }

        val safeName = Normalizer.normalize(profile.name, Normalizer.Form.NFD)
            .replace(Regex("[\\p{InCombiningDiacriticalMarks}]"), "")
            .replace(Regex("[^a-zA-Z0-9]+"), "_")
            .trim('_')
            .ifEmpty { "Perfil" }

        val fileName = "Glucosoft_Reporte_$safeName.pdf"
        val reportsDir = File(context.filesDir, "reports").apply { mkdirs() }
        val outFile = File(reportsDir, fileName)
        FileOutputStream(outFile).use { out ->
            document.writeTo(out)
        }
        document.close()

        var shared = false
        if (shareIfAvailable) {
            try {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    outFile
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Reporte de glucosa - ${profile.name}")
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "Historial de glucosa generado con Glucosoft App ($rangeLabel)."
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(shareIntent, "Compartir reporte PDF").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
                shared = true
            } catch (_: Exception) {
                shared = false
            }
        }

        return PdfExportResult(
            file = outFile,
            fileName = fileName,
            sharedViaSystem = shared
        )
    }

    private fun drawHeader(canvas: Canvas, rangeLabel: String): Float {
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 118, 110) // #0F766E
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 84f, headerPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("Glucosoft App", MARGIN_X, 36f, titlePaint)

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(204, 251, 241)
            textSize = 11f
        }
        canvas.drawText("Reporte de Glucosa para Consulta Médica", MARGIN_X, 56f, subPaint)

        val rightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(204, 251, 241)
            textSize = 9.5f
            textAlign = Paint.Align.RIGHT
        }
        val generatedAt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("es", "ES")).format(Date())
        canvas.drawText("Emitido: $generatedAt", PAGE_WIDTH - MARGIN_X, 36f, rightPaint)
        canvas.drawText("Periodo: $rangeLabel", PAGE_WIDTH - MARGIN_X, 54f, rightPaint)

        return 106f
    }

    private fun drawSummarySection(
        canvas: Canvas,
        profile: LocalProfile,
        stats: com.glucosoft.app.util.GlucoseStats,
        startY: Float
    ): Float {
        val unit = profile.unitPreference
        val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("Perfil: ${profile.name}", MARGIN_X, startY, boldPaint)

        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(71, 85, 105)
            textSize = 10f
        }
        val minTarget = GlucoseMath.formatGlucoseValue(profile.targetMinMgDl, unit)
        val maxTarget = GlucoseMath.formatGlucoseValue(profile.targetMaxMgDl, unit)
        canvas.drawText(
            "Unidad: ${unit.symbol}   |   Meta orientativa: $minTarget - $maxTarget ${unit.symbol}   |   Mediciones: ${stats.count}",
            MARGIN_X,
            startY + 18f,
            metaPaint
        )

        val kpis = listOf(
            "Promedio del periodo" to if (stats.count > 0) "${GlucoseMath.formatGlucoseValue(stats.avgMgDl, unit)} ${unit.symbol}" else "—",
            "Dentro de meta" to if (stats.count > 0) "${stats.inRangePercent}% (${stats.normalCount}/${stats.count})" else "—",
            "Mínima / Máxima" to if (stats.count > 0) "${GlucoseMath.formatGlucoseValue(stats.minMgDl, unit)} / ${GlucoseMath.formatGlucoseValue(stats.maxMgDl, unit)} ${unit.symbol}" else "—",
            "GMI orientativo" to if (stats.count > 0) "${stats.estimatedGmi}%" else "—"
        )

        val cardTop = startY + 30f
        val gap = 8f
        val totalWidth = PAGE_WIDTH - MARGIN_X * 2
        val cardWidth = (totalWidth - gap * 3) / 4f
        val cardHeight = 46f

        val cardFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(248, 250, 252)
            style = Paint.Style.FILL
        }
        val cardStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(226, 232, 240)
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val kpiLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 116, 139)
            textSize = 8.5f
        }
        val kpiValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        kpis.forEachIndexed { idx, (label, value) ->
            val left = MARGIN_X + idx * (cardWidth + gap)
            val rect = RectF(left, cardTop, left + cardWidth, cardTop + cardHeight)
            canvas.drawRoundRect(rect, 6f, 6f, cardFill)
            canvas.drawRoundRect(rect, 6f, 6f, cardStroke)
            canvas.drawText(label, left + 8f, cardTop + 17f, kpiLabelPaint)
            canvas.drawText(value, left + 8f, cardTop + 34f, kpiValuePaint)
        }

        val distY = cardTop + cardHeight + 18f
        canvas.drawText(
            "Resumen orientativo:  En meta: ${stats.normalCount}   ·   Precaución: ${stats.cautionCount}   ·   Atención: ${stats.alertCount}",
            MARGIN_X,
            distY,
            metaPaint
        )

        val disclaimerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 116, 139)
            textSize = 8.5f
        }
        canvas.drawText(
            "Nota: Los colores y rangos son orientativos según la configuración del perfil y no constituyen diagnóstico médico.",
            MARGIN_X,
            distY + 14f,
            disclaimerPaint
        )

        return distY + 28f
    }

    private fun drawReadingsTable(
        canvas: Canvas,
        profile: LocalProfile,
        readings: List<GlucoseReading>,
        startY: Float
    ) {
        val unit = profile.unitPreference
        val tableWidth = PAGE_WIDTH - MARGIN_X * 2
        val colOffsets = floatArrayOf(0f, 82f, 152f, 272f, 366f, 448f)
        val headers = arrayOf("Fecha y hora", "Glucosa", "Referencia", "Contexto", "Equipo / Método", "Notas")

        val headBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(241, 245, 249)
            style = Paint.Style.FILL
        }
        val rowLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 0.8f
        }
        val headText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val cellText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 41, 59)
            textSize = 8.2f
        }

        canvas.drawRect(MARGIN_X, startY, MARGIN_X + tableWidth, startY + 22f, headBg)
        headers.forEachIndexed { i, title ->
            canvas.drawText(title, MARGIN_X + colOffsets[i] + 4f, startY + 14f, headText)
        }

        var rowY = startY + 22f
        if (readings.isEmpty()) {
            canvas.drawText(
                "No hay mediciones registradas en este periodo.",
                MARGIN_X + 8f,
                rowY + 20f,
                cellText
            )
            return
        }

        readings.forEach { r ->
            val ev = GlucoseMath.evaluateReadingSeverity(
                r.valueMgDl,
                r.context,
                profile.targetMinMgDl,
                profile.targetMaxMgDl
            )
            val statusPaint = Paint(cellText).apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = when (ev.severity) {
                    GlucoseSeverity.NORMAL -> Color.rgb(5, 150, 105)
                    GlucoseSeverity.CAUTION -> Color.rgb(217, 119, 6)
                    GlucoseSeverity.ALERT -> Color.rgb(220, 38, 38)
                }
            }

            val methodShort = if (r.captureMethod.rawId == "bluetooth") "BT" else "Manual"
            val notesCombined = listOfNotNull(
                r.quickTags.takeIf { it.isNotEmpty() }?.joinToString(", "),
                r.note.takeIf { it.isNotBlank() }
            ).joinToString(" — ").ifEmpty { "—" }

            val textY = rowY + 15f
            canvas.drawText(GlucoseMath.formatShortDateTime(r.timestamp), MARGIN_X + colOffsets[0] + 4f, textY, cellText)
            canvas.drawText("${GlucoseMath.formatGlucoseValue(r.valueMgDl, unit)} ${unit.symbol}", MARGIN_X + colOffsets[1] + 4f, textY, headText)
            canvas.drawText(ev.label.take(22), MARGIN_X + colOffsets[2] + 4f, textY, statusPaint)
            canvas.drawText(r.context.shortLabel, MARGIN_X + colOffsets[3] + 4f, textY, cellText)
            canvas.drawText("${r.deviceModel.take(12)} ($methodShort)", MARGIN_X + colOffsets[4] + 4f, textY, cellText)
            canvas.drawText(notesCombined.take(16), MARGIN_X + colOffsets[5] + 4f, textY, cellText)

            rowY += 22f
            canvas.drawLine(MARGIN_X, rowY, MARGIN_X + tableWidth, rowY, rowLine)
        }
    }

    private fun drawFooter(canvas: Canvas, pageNumber: Int, totalPages: Int) {
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(148, 163, 184)
            textSize = 8.5f
        }
        canvas.drawText(
            "Glucosoft App · Almacenamiento local privado · Página $pageNumber de $totalPages",
            MARGIN_X,
            PAGE_HEIGHT - 24f,
            footerPaint
        )
    }
}
