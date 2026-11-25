package com.glucosoft.app.data

import com.glucosoft.app.model.BiomedicalCatalog
import com.glucosoft.app.model.BiomedicalDeviceId
import com.glucosoft.app.model.CaptureMethod
import com.glucosoft.app.model.EmergencyContact
import com.glucosoft.app.model.GlucoseReading
import com.glucosoft.app.model.GlucoseUnit
import com.glucosoft.app.model.LocalProfile
import com.glucosoft.app.model.MeasurementContext
import com.glucosoft.app.model.ProfileReminderConfig
import com.glucosoft.app.model.TextScaleMode
import com.glucosoft.app.util.MealPhotoStorage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

enum class BackupFailureReason {
    EMPTY_OR_SHORT_PASSWORD,
    CORRUPTED_OR_INVALID_FILE,
    UNSUPPORTED_VERSION,
    WRONG_PASSWORD_OR_TAMPERED,
    MISSING_REFERENCED_PHOTO,
    CAPACITY_LIMIT_EXCEEDED,
    PROFILE_LIMIT_EXCEEDED,
    STORAGE_TRANSACTION_ERROR
}

data class BackupPhotoPayload(
    val readingId: String,
    val fileName: String,
    val bytes: ByteArray
)

data class BackupPayloadSnapshot(
    val formatVersion: Int = BackupManager.CURRENT_FORMAT_VERSION,
    val exportedAtMs: Long = System.currentTimeMillis(),
    val activeProfileId: String,
    val isDarkTheme: Boolean,
    val textScaleMode: TextScaleMode,
    val profiles: List<LocalProfile>,
    val readings: List<GlucoseReading>,
    val reminders: List<ProfileReminderConfig>,
    val photosByReadingId: Map<String, BackupPhotoPayload>,
    val missingPhotoReadingIds: List<String> = emptyList(),
    val oversizedPhotoReadingIds: List<String> = emptyList()
)

data class BackupRestoreSummary(
    val newProfilesAdded: Int,
    val matchedProfiles: Int,
    val newReadingsAdded: Int,
    val skippedDuplicateReadings: Int,
    val restoredPhotosCount: Int,
    val restoredRemindersCount: Int,
    val message: String
)

data class MergedBackupState(
    val profilesToSave: List<LocalProfile>,
    val readingsToSave: List<GlucoseReading>,
    val remindersToSave: List<ProfileReminderConfig>,
    val activeProfileId: String,
    val isDarkTheme: Boolean,
    val textScaleMode: TextScaleMode,
    val summary: BackupRestoreSummary
)

sealed class BackupOperationResult<out T> {
    data class Success<T>(val value: T) : BackupOperationResult<T>()
    data class Failure(
        val reason: BackupFailureReason,
        val userMessage: String
    ) : BackupOperationResult<Nothing>()
}

/**
 * Gestor de copia de seguridad local cifrada y recuperación portable entre teléfonos.
 * - Utiliza derivación de clave `PBKDF2WithHmacSHA256` (65.536 iteraciones, sal aleatoria de 16 bytes)
 *   y cifrado autenticado `AES/GCM/NoPadding` (256 bits, IV aleatorio de 12 bytes, etiqueta de 128 bits
 *   con AAD vinculada a la cabecera y versión).
 * - Empaqueta perfiles, mediciones históricas, contexto, etiquetas rápidas, notas, unidades,
 *   preferencias visuales, horarios de recordatorio y los bytes reales de las fotos de comida adjuntas.
 * - Verifica que la copia que se genera cumpla exactamente los mismos límites de importación y que
 *   ninguna foto referenciada esté ausente o supere la capacidad antes de dejar una copia aparentemente correcta.
 * - Valida cabecera, versión, contraseña e integridad criptográfica ANTES de modificar cualquier dato.
 * - Fusiona por identidad estable de lectura (`id`) y perfil (`profileId`), asignando IDs alternativos
 *   deterministas ante colisiones entre perfiles distintos y conservando mediciones distintas tomadas
 *   en el mismo minuto con el mismo valor (incluso con notas o fotos distintas) tras restaurar una y dos veces.
 * - Respeta el límite de 3 perfiles locales; si la combinación lo excede, aborta sin alterar ningún dato.
 * - Ejecuta la lectura y escritura de perfiles, mediciones y horarios en una sola transacción Room,
 *   limpiando únicamente archivos de foto recién creados si la transacción se aborta, y aplicando
 *   preferencias y programación de avisos solo tras confirmar la transacción.
 */
object BackupManager {
    private val HEADER_MAGIC = "GLUCOSOFT_BKP_EB".toByteArray(StandardCharsets.US_ASCII) // 16 bytes
    private const val INNER_PAYLOAD_MAGIC = "GLUCOSOFT_PAYLOAD_V1"
    const val CURRENT_FORMAT_VERSION = 1
    const val MIN_PASSWORD_LENGTH = 4

    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val PBKDF2_ITERATIONS = 65_536
    private const val AES_KEY_BITS = 256
    private const val SALT_LENGTH_BYTES = 16
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val GCM_TAG_BITS = 128

    // Límites compartidos entre exportación e importación
    const val MAX_BACKUP_BYTES = 64 * 1024 * 1024 // 64 MB límite total de archivo cifrado
    const val MAX_PROFILES_IN_BACKUP = BiomedicalCatalog.MAX_LOCAL_PROFILES // 3 perfiles
    const val MAX_READINGS_IN_BACKUP = 100_000
    const val MAX_REMINDERS_IN_BACKUP = 50
    const val MAX_TAGS_PER_READING = 50
    const val MAX_SINGLE_PHOTO_BYTES = MealPhotoStorage.MAX_RAW_PHOTO_BYTES // 12 MB por foto
    const val MAX_TOTAL_PHOTOS_BYTES = 48 * 1024 * 1024 // 48 MB total de fotos
    const val MIN_GLUCOSE_MG_DL = 20
    const val MAX_GLUCOSE_MG_DL = 600

    fun validatePasswordOrFailure(password: String): BackupOperationResult.Failure? {
        val trimmed = password.trim()
        if (trimmed.length < MIN_PASSWORD_LENGTH) {
            return BackupOperationResult.Failure(
                reason = BackupFailureReason.EMPTY_OR_SHORT_PASSWORD,
                userMessage = "Escribe una contraseña de al menos $MIN_PASSWORD_LENGTH caracteres para proteger tu copia de seguridad."
            )
        }
        return null
    }

    /**
     * Lee un `InputStream` de forma acotada hasta `maxBytes` sin usar `readBytes()` ilimitado.
     */
    fun readBoundedInputStream(
        input: InputStream,
        maxBytes: Int = MAX_BACKUP_BYTES
    ): BackupOperationResult<ByteArray> {
        return try {
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            var total = 0
            while (true) {
                val read = input.read(chunk)
                if (read <= 0) break
                total += read
                if (total > maxBytes) {
                    return BackupOperationResult.Failure(
                        reason = BackupFailureReason.CAPACITY_LIMIT_EXCEEDED,
                        userMessage = "El archivo seleccionado supera el tamaño máximo permitido para una copia de seguridad (${maxBytes / (1024 * 1024)} MB). Tus datos actuales no se modificaron."
                    )
                }
                buffer.write(chunk, 0, read)
            }
            val bytes = buffer.toByteArray()
            if (bytes.isEmpty()) {
                BackupOperationResult.Failure(
                    reason = BackupFailureReason.CORRUPTED_OR_INVALID_FILE,
                    userMessage = "El archivo seleccionado está vacío."
                )
            } else {
                BackupOperationResult.Success(bytes)
            }
        } catch (e: Exception) {
            BackupOperationResult.Failure(
                reason = BackupFailureReason.CORRUPTED_OR_INVALID_FILE,
                userMessage = "No se pudo leer el archivo seleccionado: ${e.message ?: "error de lectura"}."
            )
        }
    }

    /**
     * Genera el ID alternativo determinista cuando el ID original de una medición colisiona
     * con una medición perteneciente a otro perfil distinto.
     */
    fun deterministicAlternativeReadingId(originalReadingId: String, profileId: String): String {
        val cleanOriginal = originalReadingId.trim()
        val cleanProfile = profileId.trim()
        val suffix = "__prof_$cleanProfile"
        return if (cleanOriginal.endsWith(suffix)) {
            cleanOriginal
        } else {
            "$cleanOriginal$suffix"
        }
    }

    /**
     * Verifica que el `BackupPayloadSnapshot` que se va a exportar no tenga fotos ausentes
     * y cumpla exactamente los mismos límites de capacidad y formato que exige la importación.
     */
    fun validateSnapshotForExport(snapshot: BackupPayloadSnapshot): BackupOperationResult.Failure? {
        if (snapshot.formatVersion != CURRENT_FORMAT_VERSION) {
            return BackupOperationResult.Failure(
                reason = BackupFailureReason.UNSUPPORTED_VERSION,
                userMessage = "La versión de formato (${snapshot.formatVersion}) no es compatible para exportar."
            )
        }
        if (snapshot.profiles.isEmpty()) {
            return BackupOperationResult.Failure(
                reason = BackupFailureReason.CORRUPTED_OR_INVALID_FILE,
                userMessage = "No hay perfiles creados para guardar en la copia de seguridad."
            )
        }
        if (snapshot.profiles.size > MAX_PROFILES_IN_BACKUP) {
            return BackupOperationResult.Failure(
                reason = BackupFailureReason.CAPACITY_LIMIT_EXCEEDED,
                userMessage = "La copia supera el límite máximo de $MAX_PROFILES_IN_BACKUP perfiles permitidos."
            )
        }
        if (snapshot.profiles.any { it.id.trim().isEmpty() || it.name.trim().isEmpty() }) {
            return BackupOperationResult.Failure(
                reason = BackupFailureReason.CORRUPTED_OR_INVALID_FILE,
                userMessage = "Uno de los perfiles tiene identificador o nombre vacío."
            )
        }
        if (snapshot.readings.size > MAX_READINGS_IN_BACKUP) {
            return BackupOperationResult.Failure(
                reason = BackupFailureReason.CAPACITY_LIMIT_EXCEEDED,
                userMessage = "El historial supera el límite máximo de $MAX_READINGS_IN_BACKUP mediciones por copia de seguridad."
            )
        }
        if (snapshot.reminders.size > MAX_REMINDERS_IN_BACKUP) {
            return BackupOperationResult.Failure(
                reason = BackupFailureReason.CAPACITY_LIMIT_EXCEEDED,
                userMessage = "Los recordatorios superan el límite máximo permitido ($MAX_REMINDERS_IN_BACKUP)."
            )
        }
        if (snapshot.missingPhotoReadingIds.isNotEmpty()) {
            return BackupOperationResult.Failure(
                reason = BackupFailureReason.MISSING_REFERENCED_PHOTO,
                userMessage = "No se pudo guardar la copia porque faltan ${snapshot.missingPhotoReadingIds.size} archivo(s) de foto de comida referenciados en tus mediciones. Revisa las fotos adjuntas antes de exportar."
            )
        }
        if (snapshot.oversizedPhotoReadingIds.isNotEmpty()) {
            return BackupOperationResult.Failure(
                reason = BackupFailureReason.CAPACITY_LIMIT_EXCEEDED,
                userMessage = "No se pudo guardar la copia porque ${snapshot.oversizedPhotoReadingIds.size} foto(s) adjunta(s) superan el tamaño máximo de ${MAX_SINGLE_PHOTO_BYTES / (1024 * 1024)} MB por imagen."
            )
        }

        var totalPhotoBytes = 0L
        for (reading in snapshot.readings) {
            if (reading.id.trim().isEmpty() || reading.profileId.trim().isEmpty()) {
                return BackupOperationResult.Failure(
                    reason = BackupFailureReason.CORRUPTED_OR_INVALID_FILE,
                    userMessage = "Una medición contiene identificador vacío y no puede exportarse."
                )
            }
            if (reading.valueMgDl !in MIN_GLUCOSE_MG_DL..MAX_GLUCOSE_MG_DL) {
                return BackupOperationResult.Failure(
                    reason = BackupFailureReason.CORRUPTED_OR_INVALID_FILE,
                    userMessage = "Una medición (${reading.valueMgDl} mg/dL) está fuera del rango válido ($MIN_GLUCOSE_MG_DL–$MAX_GLUCOSE_MG_DL mg/dL)."
                )
            }
            if (reading.quickTags.size > MAX_TAGS_PER_READING) {
                return BackupOperationResult.Failure(
                    reason = BackupFailureReason.CAPACITY_LIMIT_EXCEEDED,
                    userMessage = "Una medición supera el límite de $MAX_TAGS_PER_READING etiquetas rápidas."
                )
            }
            val hasReferencedPath = !reading.photoPath.isNullOrBlank()
            val payload = snapshot.photosByReadingId[reading.id]
            if (hasReferencedPath && (payload == null || payload.bytes.isEmpty())) {
                return BackupOperationResult.Failure(
                    reason = BackupFailureReason.MISSING_REFERENCED_PHOTO,
                    userMessage = "No se pudo guardar la copia porque la foto adjunta de la medición (${reading.photoPath}) no se encuentra disponible."
                )
            }
            if (payload != null) {
                if (payload.bytes.isEmpty() || payload.bytes.size > MAX_SINGLE_PHOTO_BYTES) {
                    return BackupOperationResult.Failure(
                        reason = BackupFailureReason.CAPACITY_LIMIT_EXCEEDED,
                        userMessage = "La foto adjunta (${payload.fileName}) supera la capacidad máxima por imagen (${MAX_SINGLE_PHOTO_BYTES / (1024 * 1024)} MB)."
                    )
                }
                totalPhotoBytes += payload.bytes.size.toLong()
                if (totalPhotoBytes > MAX_TOTAL_PHOTOS_BYTES.toLong()) {
                    return BackupOperationResult.Failure(
                        reason = BackupFailureReason.CAPACITY_LIMIT_EXCEEDED,
                        userMessage = "El volumen total de fotos adjuntas supera la capacidad máxima de la copia de seguridad (${MAX_TOTAL_PHOTOS_BYTES / (1024 * 1024)} MB)."
                    )
                }
            }
        }

        for (rem in snapshot.reminders) {
            if (rem.profileId.trim().isEmpty() || rem.hour !in 0..23 || rem.minute !in 0..59) {
                return BackupOperationResult.Failure(
                    reason = BackupFailureReason.CORRUPTED_OR_INVALID_FILE,
                    userMessage = "Un horario de recordatorio contiene valores fuera de rango."
                )
            }
        }
        return null
    }

    /**
     * Construye y cifra una copia de seguridad completa en un arreglo de bytes portable,
     * comprobando antes que no falten fotos referenciadas y que el resultado pueda abrirse
     * dentro de los mismos límites de importación.
     */
    fun createEncryptedBackupBytes(
        password: String,
        snapshot: BackupPayloadSnapshot
    ): BackupOperationResult<ByteArray> {
        validatePasswordOrFailure(password)?.let { return it }
        validateSnapshotForExport(snapshot)?.let { return it }

        return try {
            val plaintextBytes = serializePlaintextPayload(snapshot)
            // Verificar que el payload serializado pueda abrirse con los mismos límites de importación
            val verifiedRoundTrip = deserializePlaintextPayload(plaintextBytes, snapshot.formatVersion)
            if (verifiedRoundTrip == null) {
                return BackupOperationResult.Failure(
                    reason = BackupFailureReason.CAPACITY_LIMIT_EXCEEDED,
                    userMessage = "La copia generada excede los límites de verificación de importación y fue cancelada para evitar un archivo ilegible."
                )
            }

            val random = SecureRandom()
            val salt = ByteArray(SALT_LENGTH_BYTES).also { random.nextBytes(it) }
            val iv = ByteArray(GCM_IV_LENGTH_BYTES).also { random.nextBytes(it) }

            val secretKey = deriveAesKey(password = password.trim(), salt = salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(buildAadBytes(snapshot.formatVersion))
            val ciphertext = cipher.doFinal(plaintextBytes)

            val out = ByteArrayOutputStream(HEADER_MAGIC.size + 32 + ciphertext.size)
            DataOutputStream(out).use { dos ->
                dos.write(HEADER_MAGIC)
                dos.writeInt(snapshot.formatVersion)
                dos.writeInt(salt.size)
                dos.write(salt)
                dos.writeInt(iv.size)
                dos.write(iv)
                dos.writeInt(ciphertext.size)
                dos.write(ciphertext)
                dos.flush()
            }
            val finalEncryptedBytes = out.toByteArray()
            if (finalEncryptedBytes.size > MAX_BACKUP_BYTES) {
                return BackupOperationResult.Failure(
                    reason = BackupFailureReason.CAPACITY_LIMIT_EXCEEDED,
                    userMessage = "La copia de seguridad cifrada supera el límite máximo de ${MAX_BACKUP_BYTES / (1024 * 1024)} MB."
                )
            }
            BackupOperationResult.Success(finalEncryptedBytes)
        } catch (e: Exception) {
            BackupOperationResult.Failure(
                reason = BackupFailureReason.CORRUPTED_OR_INVALID_FILE,
                userMessage = "No fue posible generar la copia de seguridad cifrada: ${e.message ?: "error interno"}."
            )
        }
    }

    /**
     * Descifra y valida íntegramente un archivo de copia de seguridad ANTES de tocar datos locales.
     */
    fun decryptAndValidateBackupBytes(
        password: String,
        encryptedBytes: ByteArray
    ): BackupOperationResult<BackupPayloadSnapshot> {
        validatePasswordOrFailure(password)?.let { return it }

        if (encryptedBytes.size > MAX_BACKUP_BYTES) {
            return BackupOperationResult.Failure(
                reason = BackupFailureReason.CAPACITY_LIMIT_EXCEEDED,
                userMessage = "El archivo seleccionado supera el tamaño máximo permitido (${MAX_BACKUP_BYTES / (1024 * 1024)} MB)."
            )
        }
        if (encryptedBytes.size < HEADER_MAGIC.size + 16) {
            return BackupOperationResult.Failure(
                reason = BackupFailureReason.CORRUPTED_OR_INVALID_FILE,
                userMessage = "El archivo seleccionado no es una copia de seguridad válida de Glucosoft o está dañado."
            )
        }

        return try {
            DataInputStream(ByteArrayInputStream(encryptedBytes)).use { dis ->
                val magic = ByteArray(HEADER_MAGIC.size)
                dis.readFully(magic)
                if (!magic.contentEquals(HEADER_MAGIC)) {
                    return BackupOperationResult.Failure(
                        reason = BackupFailureReason.CORRUPTED_OR_INVALID_FILE,
                        userMessage = "El archivo seleccionado no pertenece a una copia de seguridad de Glucosoft."
                    )
                }

                val version = dis.readInt()
                if (version != CURRENT_FORMAT_VERSION) {
                    return BackupOperationResult.Failure(
                        reason = BackupFailureReason.UNSUPPORTED_VERSION,
                        userMessage = "Esta copia de seguridad tiene una versión no compatible (versión $version). Actualiza la aplicación antes de restaurar."
                    )
                }

                val saltLen = dis.readInt()
                if (saltLen != SALT_LENGTH_BYTES) {
                    return BackupOperationResult.Failure(
                        reason = BackupFailureReason.CORRUPTED_OR_INVALID_FILE,
                        userMessage = "La cabecera de seguridad del archivo está dañada."
                    )
                }
                val salt = ByteArray(saltLen)
                dis.readFully(salt)

                val ivLen = dis.readInt()
                if (ivLen != GCM_IV_LENGTH_BYTES) {
                    return BackupOperationResult.Failure(
                        reason = BackupFailureReason.CORRUPTED_OR_INVALID_FILE,
                        userMessage = "El vector de cifrado del archivo está dañado."
                    )
                }
                val iv = ByteArray(ivLen)
                dis.readFully(iv)

                val cipherLen = dis.readInt()
                if (cipherLen <= 16 || cipherLen > encryptedBytes.size) {
                    return BackupOperationResult.Failure(
                        reason = BackupFailureReason.CORRUPTED_OR_INVALID_FILE,
                        userMessage = "El contenido cifrado del archivo está incompleto o truncado."
                    )
                }
                val ciphertext = ByteArray(cipherLen)
                dis.readFully(ciphertext)

                val secretKey = deriveAesKey(password = password.trim(), salt = salt)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
                cipher.updateAAD(buildAadBytes(version))

                val plaintext = try {
                    cipher.doFinal(ciphertext)
                } catch (_: AEADBadTagException) {
                    return BackupOperationResult.Failure(
                        reason = BackupFailureReason.WRONG_PASSWORD_OR_TAMPERED,
                        userMessage = "La contraseña no es correcta o el archivo fue modificado. Tus datos actuales siguen intactos."
                    )
                } catch (_: BadPaddingException) {
                    return BackupOperationResult.Failure(
                        reason = BackupFailureReason.WRONG_PASSWORD_OR_TAMPERED,
                        userMessage = "La contraseña no es correcta o el archivo fue modificado. Tus datos actuales siguen intactos."
                    )
                }

                val parsed = deserializePlaintextPayload(plaintext, version)
                    ?: return BackupOperationResult.Failure(
                        reason = BackupFailureReason.CORRUPTED_OR_INVALID_FILE,
                        userMessage = "El contenido interno de la copia de seguridad está dañado. Tus datos actuales no se modificaron."
                    )

                BackupOperationResult.Success(parsed)
            }
        } catch (_: EOFException) {
            BackupOperationResult.Failure(
                reason = BackupFailureReason.CORRUPTED_OR_INVALID_FILE,
                userMessage = "El archivo de copia de seguridad está incompleto o truncado. Tus datos actuales no se modificaron."
            )
        } catch (_: Exception) {
            BackupOperationResult.Failure(
                reason = BackupFailureReason.CORRUPTED_OR_INVALID_FILE,
                userMessage = "No se pudo abrir el archivo de copia de seguridad porque está dañado."
            )
        }
    }

    /**
     * Fusiona un `BackupPayloadSnapshot` ya descifrado y validado con el estado local actual.
     * - Respeta el límite de 3 perfiles locales (`BiomedicalCatalog.MAX_LOCAL_PROFILES`); si se excede,
     *   conserva todos los datos del teléfono sin escribir archivos y explica el conflicto.
     * - Fusiona lecturas por identidad estable (`id`) y perfil (`profileId`), usando IDs alternativos
     *   deterministas si otro perfil distinto ya usa el mismo ID técnico.
     * - Nunca sobrescribe una foto existente con igual nombre y contenido diferente.
     * - Registra en `newlyCreatedFilesOut` únicamente los archivos de foto recién creados en disco
     *   para poder limpiarlos si la transacción Room se aborta.
     */
    fun mergeValidatedSnapshotWithLocalState(
        snapshot: BackupPayloadSnapshot,
        existingProfiles: List<LocalProfile>,
        existingReadings: List<GlucoseReading>,
        existingReminders: List<ProfileReminderConfig>,
        currentActiveProfileId: String,
        baseFilesDir: File,
        newlyCreatedFilesOut: MutableList<File>? = null
    ): BackupOperationResult<MergedBackupState> {
        // 1. Fusionar perfiles respetando el límite máximo de 3 perfiles locales
        val mergedProfilesById = LinkedHashMap<String, LocalProfile>()
        for (existing in existingProfiles) {
            mergedProfilesById[existing.id] = existing
        }

        var newProfilesAdded = 0
        var matchedProfiles = 0
        for (incomingProfile in snapshot.profiles) {
            val current = mergedProfilesById[incomingProfile.id]
            if (current == null) {
                mergedProfilesById[incomingProfile.id] = incomingProfile
                newProfilesAdded += 1
            } else {
                matchedProfiles += 1
                val incomingUpdatedAt = incomingProfile.updatedAt ?: incomingProfile.createdAt
                val currentUpdatedAt = current.updatedAt ?: current.createdAt
                val preferIncomingMeta = incomingUpdatedAt >= currentUpdatedAt
                val mergedContact = if (preferIncomingMeta) {
                    incomingProfile.emergencyContact ?: current.emergencyContact
                } else {
                    current.emergencyContact ?: incomingProfile.emergencyContact
                }
                mergedProfilesById[incomingProfile.id] = if (preferIncomingMeta) {
                    incomingProfile.copy(emergencyContact = mergedContact)
                } else {
                    current.copy(emergencyContact = mergedContact)
                }
            }
        }

        if (mergedProfilesById.size > BiomedicalCatalog.MAX_LOCAL_PROFILES) {
            return BackupOperationResult.Failure(
                reason = BackupFailureReason.PROFILE_LIMIT_EXCEEDED,
                userMessage = "No se puede recuperar esta copia porque al combinar los perfiles del teléfono (${existingProfiles.size}) con los de la copia se alcanzarían ${mergedProfilesById.size} perfiles, superando el límite máximo de ${BiomedicalCatalog.MAX_LOCAL_PROFILES} perfiles. Tus datos actuales se conservaron intactos."
            )
        }

        val validProfileIds = mergedProfilesById.keys.toSet()

        // 2. Fusionar mediciones históricas por identidad de lectura (id) y perfil (profileId)
        val mergedReadings = ArrayList<GlucoseReading>(existingReadings.size + snapshot.readings.size)
        val indexByReadingId = HashMap<String, Int>(existingReadings.size + snapshot.readings.size)
        existingReadings.forEachIndexed { idx, reading ->
            mergedReadings.add(reading)
            indexByReadingId[reading.id] = idx
        }

        var newReadingsAdded = 0
        var skippedDuplicateReadings = 0
        var restoredPhotosCount = 0

        for (incomingReading in snapshot.readings) {
            if (incomingReading.profileId !in validProfileIds) {
                // Nunca asignar una lectura a un perfil que no existe
                continue
            }

            val photoPayload = snapshot.photosByReadingId[incomingReading.id]
            val altIdForProfile = deterministicAlternativeReadingId(
                originalReadingId = incomingReading.id,
                profileId = incomingReading.profileId
            )

            val existingIdxAtOriginal = indexByReadingId[incomingReading.id]
            val existingAtOriginal = existingIdxAtOriginal?.let { mergedReadings[it] }
            val existingIdxAtAlt = indexByReadingId[altIdForProfile]
            val existingAtAlt = existingIdxAtAlt?.let { mergedReadings[it] }

            // Determinar si la lectura ya existe para ESTE mismo perfil (por su ID original o por su ID alternativo determinista)
            val matchedExistingIdxForSameProfile: Int? = when {
                existingAtOriginal != null && existingAtOriginal.profileId == incomingReading.profileId ->
                    existingIdxAtOriginal
                existingAtAlt != null && existingAtAlt.profileId == incomingReading.profileId ->
                    existingIdxAtAlt
                else -> null
            }

            if (matchedExistingIdxForSameProfile != null) {
                skippedDuplicateReadings += 1
                // Si la lectura ya existía para este perfil pero le faltaba su archivo de foto, restaurarlo sin sobrescribir fotos ajenas
                if (photoPayload != null && photoPayload.bytes.isNotEmpty()) {
                    val currentReading = mergedReadings[matchedExistingIdxForSameProfile]
                    val alreadyHasFile = MealPhotoStorage.photoExists(baseFilesDir, currentReading.photoPath)
                    if (!alreadyHasFile) {
                        val savedPhoto = MealPhotoStorage.savePhotoBytesDetailed(
                            baseFilesDir = baseFilesDir,
                            bytes = photoPayload.bytes,
                            preferredFileName = photoPayload.fileName,
                            newlyCreatedFilesOut = newlyCreatedFilesOut
                        )
                            ?: return BackupOperationResult.Failure(
                                reason = BackupFailureReason.STORAGE_TRANSACTION_ERROR,
                                userMessage = "No se pudo guardar una foto adjunta durante la recuperación. Tus datos actuales no se modificaron."
                            )
                        mergedReadings[matchedExistingIdxForSameProfile] =
                            currentReading.copy(photoPath = savedPhoto.fileName)
                        restoredPhotosCount += 1
                    }
                }
                continue
            }

            // Si el ID original ya está ocupado por OTRO perfil distinto, usar el ID alternativo determinista
            val safeReadingId = if (existingAtOriginal != null && existingAtOriginal.profileId != incomingReading.profileId) {
                altIdForProfile
            } else {
                incomingReading.id
            }

            var restoredPhotoPath: String? = null
            if (photoPayload != null && photoPayload.bytes.isNotEmpty()) {
                val savedPhoto = MealPhotoStorage.savePhotoBytesDetailed(
                    baseFilesDir = baseFilesDir,
                    bytes = photoPayload.bytes,
                    preferredFileName = photoPayload.fileName,
                    newlyCreatedFilesOut = newlyCreatedFilesOut
                )
                    ?: return BackupOperationResult.Failure(
                        reason = BackupFailureReason.STORAGE_TRANSACTION_ERROR,
                        userMessage = "No se pudo guardar una foto de comida durante la recuperación. Tus datos actuales no se modificaron."
                    )
                restoredPhotoPath = savedPhoto.fileName
                restoredPhotosCount += 1
            }

            val finalReading = incomingReading.copy(
                id = safeReadingId,
                photoPath = restoredPhotoPath ?: incomingReading.photoPath?.takeIf {
                    MealPhotoStorage.photoExists(baseFilesDir, it)
                }
            )
            val addedIndex = mergedReadings.size
            mergedReadings.add(finalReading)
            indexByReadingId[finalReading.id] = addedIndex
            newReadingsAdded += 1
        }

        // 3. Fusionar recordatorios por perfil
        val mergedRemindersByProfile = LinkedHashMap<String, ProfileReminderConfig>()
        for (existingRem in existingReminders) {
            if (existingRem.profileId in validProfileIds) {
                mergedRemindersByProfile[existingRem.profileId] = existingRem
            }
        }
        var restoredRemindersCount = 0
        for (incomingRem in snapshot.reminders) {
            if (incomingRem.profileId in validProfileIds) {
                mergedRemindersByProfile[incomingRem.profileId] = incomingRem
                restoredRemindersCount += 1
            }
        }

        val resolvedActiveProfileId = when {
            snapshot.activeProfileId in validProfileIds -> snapshot.activeProfileId
            currentActiveProfileId in validProfileIds -> currentActiveProfileId
            mergedProfilesById.isNotEmpty() -> mergedProfilesById.keys.first()
            else -> ""
        }

        val summaryMessage = buildSpanishSummaryMessage(
            newProfilesAdded = newProfilesAdded,
            matchedProfiles = matchedProfiles,
            newReadingsAdded = newReadingsAdded,
            skippedDuplicateReadings = skippedDuplicateReadings,
            restoredPhotosCount = restoredPhotosCount,
            restoredRemindersCount = restoredRemindersCount
        )

        val summary = BackupRestoreSummary(
            newProfilesAdded = newProfilesAdded,
            matchedProfiles = matchedProfiles,
            newReadingsAdded = newReadingsAdded,
            skippedDuplicateReadings = skippedDuplicateReadings,
            restoredPhotosCount = restoredPhotosCount,
            restoredRemindersCount = restoredRemindersCount,
            message = summaryMessage
        )

        return BackupOperationResult.Success(
            MergedBackupState(
                profilesToSave = mergedProfilesById.values.toList(),
                readingsToSave = mergedReadings,
                remindersToSave = mergedRemindersByProfile.values.toList(),
                activeProfileId = resolvedActiveProfileId,
                isDarkTheme = snapshot.isDarkTheme,
                textScaleMode = snapshot.textScaleMode,
                summary = summary
            )
        )
    }

    /**
     * Descifra, valida y fusiona una copia de seguridad con el estado local actual.
     * - Si la validación, contraseña o límite de 3 perfiles fallan, limpia cualquier archivo nuevo y no modifica el estado.
     * - Es idempotente y repetible: fusiona por identidad de lectura (`id`) y perfil (`profileId`).
     */
    fun restoreAndMergeFromEncryptedBytes(
        password: String,
        encryptedBytes: ByteArray,
        existingProfiles: List<LocalProfile>,
        existingReadings: List<GlucoseReading>,
        existingReminders: List<ProfileReminderConfig>,
        currentActiveProfileId: String,
        baseFilesDir: File
    ): BackupOperationResult<MergedBackupState> {
        val decryptedResult = decryptAndValidateBackupBytes(
            password = password,
            encryptedBytes = encryptedBytes
        )
        val snapshot = when (decryptedResult) {
            is BackupOperationResult.Failure -> return decryptedResult
            is BackupOperationResult.Success -> decryptedResult.value
        }

        val newlyCreatedFiles = mutableListOf<File>()
        val mergeResult = mergeValidatedSnapshotWithLocalState(
            snapshot = snapshot,
            existingProfiles = existingProfiles,
            existingReadings = existingReadings,
            existingReminders = existingReminders,
            currentActiveProfileId = currentActiveProfileId,
            baseFilesDir = baseFilesDir,
            newlyCreatedFilesOut = newlyCreatedFiles
        )
        if (mergeResult is BackupOperationResult.Failure) {
            cleanUpNewlyCreatedFiles(newlyCreatedFiles)
        }
        return mergeResult
    }

    /**
     * Ejecuta la restauración completa usando UNA SOLA transacción para leer y aplicar perfiles,
     * mediciones y horarios, limpiando únicamente los archivos de foto recién creados si la
     * transacción falla o se aborta, y aplicando preferencias y avisos solo tras confirmar.
     */
    suspend fun executeTransactionalRestore(
        password: String,
        encryptedBytes: ByteArray,
        currentActiveProfileId: String,
        baseFilesDir: File,
        runInSingleTransaction: suspend (suspend () -> Unit) -> Unit,
        readExistingProfiles: suspend () -> List<LocalProfile>,
        readExistingReadings: suspend () -> List<GlucoseReading>,
        readExistingReminders: suspend () -> List<ProfileReminderConfig>,
        writeMergedProfiles: suspend (List<LocalProfile>) -> Unit,
        writeMergedReadings: suspend (List<GlucoseReading>) -> Unit,
        writeMergedReminders: suspend (List<ProfileReminderConfig>) -> Unit,
        onAfterTransactionCommitted: suspend (MergedBackupState) -> Unit = {}
    ): BackupOperationResult<MergedBackupState> {
        // 1. Descifrar y validar cabecera, versión, contraseña y etiqueta AEAD ANTES de abrir la transacción
        val decryptedResult = decryptAndValidateBackupBytes(
            password = password,
            encryptedBytes = encryptedBytes
        )
        val snapshot = when (decryptedResult) {
            is BackupOperationResult.Failure -> return decryptedResult
            is BackupOperationResult.Success -> decryptedResult.value
        }

        val newlyCreatedPhotoFiles = mutableListOf<File>()
        var committedState: MergedBackupState? = null
        var domainFailure: BackupOperationResult.Failure? = null

        try {
            runInSingleTransaction {
                val existingProfiles = readExistingProfiles()
                val existingReadings = readExistingReadings()
                val existingReminders = readExistingReminders()

                when (
                    val mergedResult = mergeValidatedSnapshotWithLocalState(
                        snapshot = snapshot,
                        existingProfiles = existingProfiles,
                        existingReadings = existingReadings,
                        existingReminders = existingReminders,
                        currentActiveProfileId = currentActiveProfileId,
                        baseFilesDir = baseFilesDir,
                        newlyCreatedFilesOut = newlyCreatedPhotoFiles
                    )
                ) {
                    is BackupOperationResult.Failure -> {
                        domainFailure = mergedResult
                        cleanUpNewlyCreatedFiles(newlyCreatedPhotoFiles)
                    }
                    is BackupOperationResult.Success -> {
                        val merged = mergedResult.value
                        writeMergedProfiles(merged.profilesToSave)
                        writeMergedReadings(merged.readingsToSave)
                        writeMergedReminders(merged.remindersToSave)
                        committedState = merged
                    }
                }
            }
        } catch (t: Throwable) {
            // Si la transacción Room falla o se aborta, limpiar ÚNICAMENTE los archivos de foto recién creados
            cleanUpNewlyCreatedFiles(newlyCreatedPhotoFiles)
            return domainFailure ?: BackupOperationResult.Failure(
                reason = BackupFailureReason.STORAGE_TRANSACTION_ERROR,
                userMessage = "Error al guardar los datos recuperados en el teléfono: ${t.message ?: "transacción abortada"}. Tus datos y fotos anteriores siguen intactos."
            )
        }

        domainFailure?.let { failure ->
            cleanUpNewlyCreatedFiles(newlyCreatedPhotoFiles)
            return failure
        }

        val confirmed = committedState
            ?: return BackupOperationResult.Failure(
                reason = BackupFailureReason.STORAGE_TRANSACTION_ERROR,
                userMessage = "No se pudo confirmar la transacción de recuperación."
            )

        // Aplicar preferencias visuales, perfil activo y programación de recordatorios SOLO tras confirmar la transacción
        onAfterTransactionCommitted(confirmed)
        return BackupOperationResult.Success(confirmed)
    }

    fun cleanUpNewlyCreatedFiles(files: List<File>) {
        for (file in files) {
            try {
                if (file.exists() && file.isFile) {
                    file.delete()
                }
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Construye el `BackupPayloadSnapshot` leyendo los archivos reales de fotos desde `baseFilesDir`
     * y registrando explícitamente si alguna foto referenciada falta o supera el límite de capacidad.
     */
    fun buildSnapshotFromLocalData(
        profiles: List<LocalProfile>,
        readings: List<GlucoseReading>,
        reminders: List<ProfileReminderConfig>,
        activeProfileId: String,
        isDarkTheme: Boolean,
        textScaleMode: TextScaleMode,
        baseFilesDir: File,
        exportedAtMs: Long = System.currentTimeMillis()
    ): BackupPayloadSnapshot {
        val photosByReadingId = LinkedHashMap<String, BackupPhotoPayload>()
        val missingPhotoReadingIds = mutableListOf<String>()
        val oversizedPhotoReadingIds = mutableListOf<String>()

        for (reading in readings) {
            val path = reading.photoPath
            if (!path.isNullOrBlank()) {
                val resolvedFile = MealPhotoStorage.resolvePhotoFile(baseFilesDir, path)
                if (resolvedFile == null || !resolvedFile.exists() || !resolvedFile.isFile || resolvedFile.length() <= 0L) {
                    missingPhotoReadingIds.add(reading.id)
                } else if (resolvedFile.length() > MAX_SINGLE_PHOTO_BYTES.toLong()) {
                    oversizedPhotoReadingIds.add(reading.id)
                } else {
                    val bytes = MealPhotoStorage.readPhotoBytes(baseFilesDir, path)
                    if (bytes == null || bytes.isEmpty()) {
                        missingPhotoReadingIds.add(reading.id)
                    } else {
                        photosByReadingId[reading.id] = BackupPhotoPayload(
                            readingId = reading.id,
                            fileName = MealPhotoStorage.sanitizeFileName(path),
                            bytes = bytes
                        )
                    }
                }
            }
        }
        return BackupPayloadSnapshot(
            formatVersion = CURRENT_FORMAT_VERSION,
            exportedAtMs = exportedAtMs,
            activeProfileId = activeProfileId,
            isDarkTheme = isDarkTheme,
            textScaleMode = textScaleMode,
            profiles = profiles,
            readings = readings,
            reminders = reminders,
            photosByReadingId = photosByReadingId,
            missingPhotoReadingIds = missingPhotoReadingIds,
            oversizedPhotoReadingIds = oversizedPhotoReadingIds
        )
    }

    private fun buildSpanishSummaryMessage(
        newProfilesAdded: Int,
        matchedProfiles: Int,
        newReadingsAdded: Int,
        skippedDuplicateReadings: Int,
        restoredPhotosCount: Int,
        restoredRemindersCount: Int
    ): String {
        val parts = mutableListOf<String>()
        val totalProfiles = newProfilesAdded + matchedProfiles
        parts.add(
            if (newProfilesAdded > 0) {
                "$totalProfiles perfil(es) procesado(s) ($newProfilesAdded nuevo(s))"
            } else {
                "$totalProfiles perfil(es) verificado(s)"
            }
        )
        parts.add("$newReadingsAdded medición(es) nueva(s) recuperada(s)")
        if (skippedDuplicateReadings > 0) {
            parts.add("$skippedDuplicateReadings medición(es) ya existente(s) conservada(s) sin duplicar")
        }
        if (restoredPhotosCount > 0) {
            parts.add("$restoredPhotosCount foto(s) de comida restaurada(s)")
        }
        if (restoredRemindersCount > 0) {
            parts.add("$restoredRemindersCount horario(s) de recordatorio recuperado(s)")
        }
        return "Datos recuperados correctamente: ${parts.joinToString(", ")}."
    }

    private fun buildAadBytes(version: Int): ByteArray {
        val buf = ByteBuffer.allocate(HEADER_MAGIC.size + 4)
        buf.put(HEADER_MAGIC)
        buf.putInt(version)
        return buf.array()
    }

    private fun deriveAesKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, AES_KEY_BITS)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val keyBytes = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun serializePlaintextPayload(snapshot: BackupPayloadSnapshot): ByteArray {
        val out = ByteArrayOutputStream(8192)
        DataOutputStream(out).use { dos ->
            dos.writeUTF(INNER_PAYLOAD_MAGIC)
            dos.writeInt(snapshot.formatVersion)
            dos.writeLong(snapshot.exportedAtMs)
            dos.writeUTF(snapshot.activeProfileId)
            dos.writeBoolean(snapshot.isDarkTheme)
            dos.writeUTF(if (snapshot.textScaleMode == TextScaleMode.LARGE) "large" else "normal")

            // Perfiles
            dos.writeInt(snapshot.profiles.size)
            for (profile in snapshot.profiles) {
                dos.writeUTF(profile.id)
                dos.writeUTF(profile.name)
                dos.writeUTF(profile.unitPreference.symbol)
                dos.writeInt(profile.targetMinMgDl)
                dos.writeInt(profile.targetMaxMgDl)
                dos.writeUTF(profile.accentHex)
                dos.writeLong(profile.createdAt)
                val updatedAt = profile.updatedAt
                dos.writeBoolean(updatedAt != null)
                if (updatedAt != null) {
                    dos.writeLong(updatedAt)
                }
                val contact = profile.emergencyContact
                dos.writeBoolean(contact != null)
                if (contact != null) {
                    dos.writeUTF(contact.name)
                    dos.writeUTF(contact.relation)
                    dos.writeUTF(contact.phone)
                }
            }

            // Mediciones y fotos adjuntas
            dos.writeInt(snapshot.readings.size)
            for (reading in snapshot.readings) {
                dos.writeUTF(reading.id)
                dos.writeUTF(reading.profileId)
                dos.writeInt(reading.valueMgDl)
                dos.writeUTF(reading.originalUnit.symbol)
                dos.writeLong(reading.timestamp)
                dos.writeUTF(reading.context.rawId)
                dos.writeInt(reading.quickTags.size)
                for (tag in reading.quickTags) {
                    dos.writeUTF(tag)
                }
                dos.writeUTF(reading.note)
                dos.writeUTF(reading.captureMethod.rawId)
                dos.writeUTF(reading.deviceModel)
                dos.writeUTF(reading.deviceId.rawId)
                val infoSummary = reading.deviceInfoSummary
                dos.writeBoolean(infoSummary != null)
                if (infoSummary != null) {
                    dos.writeUTF(infoSummary)
                }

                val photo = snapshot.photosByReadingId[reading.id]
                val hasPhotoBytes = photo != null && photo.bytes.isNotEmpty()
                dos.writeBoolean(hasPhotoBytes)
                if (hasPhotoBytes) {
                    dos.writeUTF(photo.fileName)
                    dos.writeInt(photo.bytes.size)
                    dos.write(photo.bytes)
                }
            }

            // Recordatorios por perfil
            dos.writeInt(snapshot.reminders.size)
            for (rem in snapshot.reminders) {
                dos.writeUTF(rem.profileId)
                dos.writeBoolean(rem.enabled)
                dos.writeInt(rem.hour)
                dos.writeInt(rem.minute)
                val snooze = rem.snoozedUntilMs
                dos.writeBoolean(snooze != null)
                if (snooze != null) {
                    dos.writeLong(snooze)
                }
            }
            dos.flush()
        }
        return out.toByteArray()
    }

    private fun deserializePlaintextPayload(
        plaintext: ByteArray,
        expectedVersion: Int
    ): BackupPayloadSnapshot? {
        return try {
            DataInputStream(ByteArrayInputStream(plaintext)).use { dis ->
                val innerMagic = dis.readUTF()
                if (innerMagic != INNER_PAYLOAD_MAGIC) return null
                val innerVersion = dis.readInt()
                if (innerVersion != expectedVersion) return null
                val exportedAtMs = dis.readLong()
                val activeProfileId = dis.readUTF()
                val isDarkTheme = dis.readBoolean()
                val textScaleRaw = dis.readUTF()
                val textScaleMode = if (textScaleRaw == "large") TextScaleMode.LARGE else TextScaleMode.NORMAL

                val profilesCount = dis.readInt()
                if (profilesCount <= 0 || profilesCount > MAX_PROFILES_IN_BACKUP) return null
                val profiles = ArrayList<LocalProfile>(profilesCount)
                for (i in 0 until profilesCount) {
                    val id = dis.readUTF().trim()
                    val name = dis.readUTF().trim()
                    if (id.isEmpty() || name.isEmpty()) return null
                    val unit = GlucoseUnit.fromSymbol(dis.readUTF())
                    val minMgDl = dis.readInt().coerceIn(40, 200)
                    val maxMgDl = dis.readInt().coerceIn(minMgDl + 10, 350)
                    val accentHex = dis.readUTF()
                    val createdAt = dis.readLong()
                    val hasUpdatedAt = dis.readBoolean()
                    val updatedAt = if (hasUpdatedAt) dis.readLong() else null
                    val hasContact = dis.readBoolean()
                    val contact = if (hasContact) {
                        val cName = dis.readUTF()
                        val cRel = dis.readUTF()
                        val cPhone = dis.readUTF()
                        EmergencyContact(name = cName, relation = cRel, phone = cPhone)
                    } else {
                        null
                    }
                    profiles.add(
                        LocalProfile(
                            id = id,
                            name = name,
                            unitPreference = unit,
                            targetMinMgDl = minMgDl,
                            targetMaxMgDl = maxMgDl,
                            accentHex = accentHex,
                            createdAt = createdAt,
                            updatedAt = updatedAt,
                            emergencyContact = contact
                        )
                    )
                }

                val readingsCount = dis.readInt()
                if (readingsCount < 0 || readingsCount > MAX_READINGS_IN_BACKUP) return null
                val readings = ArrayList<GlucoseReading>(readingsCount)
                val photosByReadingId = LinkedHashMap<String, BackupPhotoPayload>()
                var totalPhotoBytes = 0L
                for (i in 0 until readingsCount) {
                    val id = dis.readUTF().trim()
                    val profileId = dis.readUTF().trim()
                    val valueMgDl = dis.readInt()
                    if (id.isEmpty() || profileId.isEmpty() || valueMgDl !in MIN_GLUCOSE_MG_DL..MAX_GLUCOSE_MG_DL) return null
                    val originalUnit = GlucoseUnit.fromSymbol(dis.readUTF())
                    val timestamp = dis.readLong()
                    val context = MeasurementContext.fromRaw(dis.readUTF())
                    val tagsCount = dis.readInt()
                    if (tagsCount < 0 || tagsCount > MAX_TAGS_PER_READING) return null
                    val tags = ArrayList<String>(tagsCount)
                    for (t in 0 until tagsCount) {
                        tags.add(dis.readUTF())
                    }
                    val note = dis.readUTF()
                    val captureMethod = CaptureMethod.fromRaw(dis.readUTF())
                    val deviceModel = dis.readUTF()
                    val deviceId = BiomedicalDeviceId.fromRaw(dis.readUTF())
                    val hasSummary = dis.readBoolean()
                    val summary = if (hasSummary) dis.readUTF() else null
                    val hasPhoto = dis.readBoolean()
                    var photoFileName: String? = null
                    if (hasPhoto) {
                        val rawName = dis.readUTF()
                        val photoLen = dis.readInt()
                        if (photoLen <= 0 || photoLen > MAX_SINGLE_PHOTO_BYTES) return null
                        totalPhotoBytes += photoLen.toLong()
                        if (totalPhotoBytes > MAX_TOTAL_PHOTOS_BYTES.toLong()) return null
                        val photoBytes = ByteArray(photoLen)
                        dis.readFully(photoBytes)
                        photoFileName = MealPhotoStorage.sanitizeFileName(rawName)
                        photosByReadingId[id] = BackupPhotoPayload(
                            readingId = id,
                            fileName = photoFileName,
                            bytes = photoBytes
                        )
                    }
                    readings.add(
                        GlucoseReading(
                            id = id,
                            profileId = profileId,
                            valueMgDl = valueMgDl,
                            originalUnit = originalUnit,
                            timestamp = timestamp,
                            context = context,
                            quickTags = tags,
                            note = note,
                            captureMethod = captureMethod,
                            deviceModel = deviceModel,
                            deviceId = deviceId,
                            deviceInfoSummary = summary,
                            photoPath = photoFileName
                        )
                    )
                }

                val remindersCount = dis.readInt()
                if (remindersCount < 0 || remindersCount > MAX_REMINDERS_IN_BACKUP) return null
                val reminders = ArrayList<ProfileReminderConfig>(remindersCount)
                for (i in 0 until remindersCount) {
                    val profileId = dis.readUTF().trim()
                    val enabled = dis.readBoolean()
                    val hour = dis.readInt()
                    val minute = dis.readInt()
                    if (profileId.isEmpty() || hour !in 0..23 || minute !in 0..59) return null
                    val hasSnooze = dis.readBoolean()
                    val snoozedUntilMs = if (hasSnooze) dis.readLong() else null
                    reminders.add(
                        ProfileReminderConfig(
                            profileId = profileId,
                            enabled = enabled,
                            hour = hour,
                            minute = minute,
                            snoozedUntilMs = snoozedUntilMs
                        )
                    )
                }

                BackupPayloadSnapshot(
                    formatVersion = innerVersion,
                    exportedAtMs = exportedAtMs,
                    activeProfileId = activeProfileId,
                    isDarkTheme = isDarkTheme,
                    textScaleMode = textScaleMode,
                    profiles = profiles,
                    readings = readings,
                    reminders = reminders,
                    photosByReadingId = photosByReadingId
                )
            }
        } catch (_: Exception) {
            null
        }
    }
}
