package com.glucosoft.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

data class SavedPhotoFileResult(
    val fileName: String,
    val file: File,
    val wasNewlyCreated: Boolean
)

/**
 * Almacenamiento local privado de fotos de comida adjuntas a mediciones.
 * - No solicita permisos amplios de galería: copia únicamente la foto elegida por la persona
 *   mediante el selector del sistema (`PickVisualMedia`) a `filesDir/meal_photos/`.
 * - Guarda nombres de archivo relativos (`photo_<id>.jpg`) para que las copias de seguridad
 *   cifradas puedan restaurarse en otro teléfono sin depender de rutas absolutas del sistema.
 * - Nunca sobrescribe una foto existente con igual nombre y contenido diferente: si el archivo
 *   ya existe con los mismos bytes lo reutiliza sin reescribir; si existe con contenido distinto
 *   asigna un nombre determinista con huella SHA-256 de sus bytes.
 */
object MealPhotoStorage {
    const val MEAL_PHOTOS_DIR_NAME = "meal_photos"
    private const val MAX_IMAGE_DIMENSION_PX = 1280
    private const val JPEG_QUALITY = 85
    const val MAX_RAW_PHOTO_BYTES = 12 * 1024 * 1024 // 12 MB límite defensivo de lectura/importación

    fun photosDir(baseFilesDir: File): File {
        val dir = File(baseFilesDir, MEAL_PHOTOS_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun sanitizeFileName(rawName: String?): String {
        val base = rawName
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.replace(Regex("[^A-Za-z0-9._-]"), "_")
            ?.trim('_')
            .orEmpty()
        return if (base.isNotBlank() && base.length <= 96) {
            if (base.endsWith(".jpg", ignoreCase = true) ||
                base.endsWith(".jpeg", ignoreCase = true) ||
                base.endsWith(".png", ignoreCase = true)
            ) {
                base
            } else {
                "$base.jpg"
            }
        } else {
            "meal_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg"
        }
    }

    /**
     * Resuelve el archivo físico dentro de `filesDir/meal_photos/` a partir de `photoPath`
     * (compatible tanto con nombre relativo como con ruta absoluta existente).
     */
    fun resolvePhotoFile(baseFilesDir: File, photoPath: String?): File? {
        if (photoPath.isNullOrBlank()) return null
        val dir = photosDir(baseFilesDir)
        val cleanName = sanitizeFileName(photoPath)
        val candidateInDir = File(dir, cleanName)
        if (candidateInDir.exists() && candidateInDir.isFile) {
            return candidateInDir
        }
        val directFile = File(photoPath)
        if (directFile.exists() && directFile.isFile) {
            return directFile
        }
        return candidateInDir
    }

    fun photoExists(baseFilesDir: File, photoPath: String?): Boolean {
        val resolved = resolvePhotoFile(baseFilesDir, photoPath) ?: return false
        return resolved.exists() && resolved.isFile && resolved.length() in 1L..MAX_RAW_PHOTO_BYTES.toLong()
    }

    /**
     * Lee un `InputStream` de forma acotada hasta `maxBytes`. Devuelve `null` si supera el límite.
     */
    fun readBoundedStreamBytes(
        input: InputStream,
        maxBytes: Int = MAX_RAW_PHOTO_BYTES
    ): ByteArray? {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        var total = 0
        while (true) {
            val read = input.read(chunk)
            if (read <= 0) break
            total += read
            if (total > maxBytes) {
                return null
            }
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }

    /**
     * Copia y comprime una imagen seleccionada desde un `Uri` del selector del sistema Android
     * al directorio privado de la aplicación. Devuelve el nombre relativo del archivo guardado.
     */
    fun savePhotoFromUri(context: Context, sourceUri: Uri): String? {
        return try {
            val rawBytes = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                readBoundedStreamBytes(input, MAX_RAW_PHOTO_BYTES)
            } ?: return null

            if (rawBytes.isEmpty()) return null
            val compressedOrRaw = tryDownscaleAndCompressJpeg(rawBytes) ?: rawBytes
            savePhotoBytes(
                baseFilesDir = context.filesDir,
                bytes = compressedOrRaw
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Guarda los bytes de una foto en `baseFilesDir/meal_photos/` sin sobrescribir jamás una foto
     * existente que tenga el mismo nombre y contenido diferente.
     * - Si el archivo ya existe con idéntico contenido byte a byte, lo reutiliza (`wasNewlyCreated = false`).
     * - Si el archivo ya existe con contenido diferente, genera un nombre alternativo determinista
     *   basado en el hash SHA-256 del contenido.
     * - Si crea un archivo nuevo en disco y se proporciona `newlyCreatedFilesOut`, lo registra allí
     *   para permitir limpiar únicamente archivos nuevos si una transacción se aborta.
     */
    fun savePhotoBytesDetailed(
        baseFilesDir: File,
        bytes: ByteArray,
        preferredFileName: String? = null,
        newlyCreatedFilesOut: MutableList<File>? = null
    ): SavedPhotoFileResult? {
        if (bytes.isEmpty() || bytes.size > MAX_RAW_PHOTO_BYTES) return null
        return try {
            val dir = photosDir(baseFilesDir)
            val initialName = if (!preferredFileName.isNullOrBlank()) {
                sanitizeFileName(preferredFileName)
            } else {
                "meal_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg"
            }

            val initialFile = File(dir, initialName)
            if (initialFile.exists() && initialFile.isFile) {
                val existingBytes = readFileBoundedOrNull(initialFile, MAX_RAW_PHOTO_BYTES)
                if (existingBytes != null && existingBytes.contentEquals(bytes)) {
                    return SavedPhotoFileResult(
                        fileName = initialName,
                        file = initialFile,
                        wasNewlyCreated = false
                    )
                }

                // Colisión de nombre con contenido distinto: NUNCA sobrescribir el archivo existente
                val dotIdx = initialName.lastIndexOf('.')
                val stem = if (dotIdx > 0) initialName.substring(0, dotIdx).take(64) else initialName.take(64)
                val ext = if (dotIdx > 0) initialName.substring(dotIdx) else ".jpg"
                val contentHash12 = sha256Hex(bytes).take(12)

                var attemptCounter = 1
                while (attemptCounter <= 1000) {
                    val candidateName = if (attemptCounter == 1) {
                        "${stem}_$contentHash12$ext"
                    } else {
                        "${stem}_${contentHash12}_$attemptCounter$ext"
                    }
                    val candidateFile = File(dir, candidateName)
                    if (!candidateFile.exists()) {
                        candidateFile.outputStream().use { out ->
                            out.write(bytes)
                            out.flush()
                        }
                        newlyCreatedFilesOut?.add(candidateFile)
                        return SavedPhotoFileResult(
                            fileName = candidateName,
                            file = candidateFile,
                            wasNewlyCreated = true
                        )
                    }
                    val candidateBytes = readFileBoundedOrNull(candidateFile, MAX_RAW_PHOTO_BYTES)
                    if (candidateBytes != null && candidateBytes.contentEquals(bytes)) {
                        return SavedPhotoFileResult(
                            fileName = candidateName,
                            file = candidateFile,
                            wasNewlyCreated = false
                        )
                    }
                    attemptCounter += 1
                }
                return null
            }

            initialFile.outputStream().use { out ->
                out.write(bytes)
                out.flush()
            }
            newlyCreatedFilesOut?.add(initialFile)
            SavedPhotoFileResult(
                fileName = initialName,
                file = initialFile,
                wasNewlyCreated = true
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Guarda los bytes de una foto en `baseFilesDir/meal_photos/` y devuelve su nombre relativo.
     */
    fun savePhotoBytes(
        baseFilesDir: File,
        bytes: ByteArray,
        preferredFileName: String? = null,
        newlyCreatedFilesOut: MutableList<File>? = null
    ): String? {
        return savePhotoBytesDetailed(
            baseFilesDir = baseFilesDir,
            bytes = bytes,
            preferredFileName = preferredFileName,
            newlyCreatedFilesOut = newlyCreatedFilesOut
        )?.fileName
    }

    fun readPhotoBytes(baseFilesDir: File, photoPath: String?): ByteArray? {
        val file = resolvePhotoFile(baseFilesDir, photoPath) ?: return null
        return readFileBoundedOrNull(file, MAX_RAW_PHOTO_BYTES)
    }

    private fun readFileBoundedOrNull(file: File, maxBytes: Int): ByteArray? {
        if (!file.exists() || !file.isFile || file.length() <= 0L || file.length() > maxBytes.toLong()) {
            return null
        }
        return try {
            file.inputStream().use { input ->
                readBoundedStreamBytes(input, maxBytes)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun deletePhoto(baseFilesDir: File, photoPath: String?): Boolean {
        val file = resolvePhotoFile(baseFilesDir, photoPath) ?: return false
        return try {
            if (file.exists()) file.delete() else false
        } catch (_: Exception) {
            false
        }
    }

    fun decodeImageBitmapOrNull(baseFilesDir: File, photoPath: String?): ImageBitmap? {
        val file = resolvePhotoFile(baseFilesDir, photoPath) ?: return null
        if (!file.exists() || !file.isFile) return null
        return try {
            BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
        } catch (_: Throwable) {
            null
        }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            sb.append(String.format(Locale.US, "%02x", b.toInt() and 0xFF))
        }
        return sb.toString()
    }

    private fun tryDownscaleAndCompressJpeg(rawBytes: ByteArray): ByteArray? {
        return try {
            val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size) ?: return null
            val width = decoded.width
            val height = decoded.height
            if (width <= 0 || height <= 0) return null

            val maxDim = max(width, height)
            val scaledBitmap = if (maxDim > MAX_IMAGE_DIMENSION_PX) {
                val ratio = MAX_IMAGE_DIMENSION_PX.toFloat() / maxDim.toFloat()
                val targetW = (width * ratio).roundToInt().coerceAtLeast(1)
                val targetH = (height * ratio).roundToInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
            } else {
                decoded
            }

            val out = ByteArrayOutputStream()
            val ok = scaledBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            if (scaledBitmap !== decoded) {
                scaledBitmap.recycle()
            }
            decoded.recycle()
            if (ok) out.toByteArray().takeIf { it.isNotEmpty() } else null
        } catch (_: Throwable) {
            null
        }
    }
}
