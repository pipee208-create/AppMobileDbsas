package com.glucosoft.app.protocol

import com.glucosoft.app.model.BiomedicalDeviceId
import com.glucosoft.app.service.DiscoveredGattServiceInfo
import com.glucosoft.app.service.ParsedBleGlucose
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * Lector en producción Kotlin puro para tramas de resultado EN VIVO (CMD 0x88)
 * del protocolo YASEE confirmado por el proveedor para Glucocare Match (GLM-76L).
 *
 * - Valida cabecera `AA 55`, longitud `0x0C` (15 bytes totales), comando `0x88`,
 *   identificador `0x85`, unidad `0x00` (mg/dL), tira `0x10` (glucosa en sangre),
 *   rango `20..600 mg/dL` y checksum unsigned de 16 bits sobre `CMD + ID + Data[0..7]`
 *   (high byte primero, low byte después).
 * - Incluye reensamblador de flujo (`YaseeLiveFrameStreamAssembler`) para manejar
 *   fragmentación BLE, concatenación o ruido previo en el canal de notificación.
 * - Incluye filtro de ráfagas de reintento (`LiveFrameBurstDeduplicator`) que asigna
 *   el instante de recepción del teléfono y evita duplicados inmediatos de hardware
 *   sin perder mediciones con el mismo valor tomadas en momentos distintos.
 * - La descarga de memoria histórica (`0x8A`) se mantiene pendiente de confirmar
 *   el canal de escritura con una unidad física, sin inventar UUIDs ni escribir comandos.
 */
object YaseeGlm76LiveFrameReader {

    const val FAMILY_MODEL_LABEL: String = "Glucocare Match GLM-76 / GLM-76L"
    const val HEADER_BYTE_0: Int = 0xAA
    const val HEADER_BYTE_1: Int = 0x55
    const val EXPECTED_LENGTH_FIELD: Int = 0x0C // CMD(1) + Device_ID(1) + Data(8) + Checksum(2) = 12
    const val EXPECTED_TOTAL_FRAME_BYTES: Int = 15 // Header(2) + Length(1) + 12 = 15
    const val CMD_LIVE_RESULT: Int = 0x88
    const val CMD_HISTORY_RECORD_PENDING: Int = 0x8A
    const val DEVICE_ID_GLM76_FAMILY: Int = 0x85
    const val UNIT_MG_DL: Int = 0x00
    const val STRIP_TYPE_BLOOD_GLUCOSE: Int = 0x10
    const val STRIP_TYPE_URIC_ACID: Int = 0x40
    const val STRIP_TYPE_KETONE: Int = 0x50
    const val MIN_VERIFIED_MG_DL: Int = 20
    const val MAX_VERIFIED_MG_DL: Int = 600

    enum class RejectionReason {
        INCOMPLETE_OR_CONCATENATED_FRAME,
        INVALID_HEADER_DELIMITER,
        INVALID_LENGTH_FIELD,
        INVALID_CHECKSUM,
        UNAUTHORIZED_COMMAND,
        UNAUTHORIZED_DEVICE_ID,
        UNAUTHORIZED_UNIT,
        UNAUTHORIZED_STRIP_TYPE,
        VALUE_OUT_OF_RANGE
    }

    sealed class DecodeResult {
        /**
         * Este mensaje en vivo no incluye fecha, hora, momento de comida ni número de registro.
         * Solo expone el valor verificable en mg/dL dentro del rango documentado 20..600.
         */
        data class VerifiedLiveGlucose(
            val valueMgDl: Int,
            val familyModel: String = FAMILY_MODEL_LABEL
        ) : DecodeResult()

        data class Rejected(val reason: RejectionReason) : DecodeResult()
    }

    /**
     * Calcula el checksum unsigned de 16 bits (módulo 65536) sobre CMD + Device_ID + Data.
     * Devuelve el par (highByte, lowByte).
     */
    fun computeChecksumBytes(payloadWithoutHeaderLengthAndChecksum: ByteArray): Pair<Int, Int> {
        var sum = 0
        for (b in payloadWithoutHeaderLengthAndChecksum) {
            sum = (sum + (b.toInt() and 0xFF)) and 0xFFFF
        }
        val highByte = (sum ushr 8) and 0xFF
        val lowByte = sum and 0xFF
        return highByte to lowByte
    }

    /**
     * Decodifica una trama completa individual de 15 bytes de resultado en vivo del GLM-76 / GLM-76L.
     */
    fun decodeLiveFrame(frame: ByteArray): DecodeResult {
        // 1. Verificar delimitador de cabecera AA 55 si hay al menos 2 bytes
        if (frame.size >= 2) {
            val b0 = frame[0].toInt() and 0xFF
            val b1 = frame[1].toInt() and 0xFF
            if (b0 != HEADER_BYTE_0 || b1 != HEADER_BYTE_1) {
                return DecodeResult.Rejected(RejectionReason.INVALID_HEADER_DELIMITER)
            }
        } else {
            return DecodeResult.Rejected(RejectionReason.INCOMPLETE_OR_CONCATENATED_FRAME)
        }

        // 2. Verificar byte de longitud (0x0C) si hay al menos 3 bytes
        if (frame.size >= 3) {
            val lengthField = frame[2].toInt() and 0xFF
            if (lengthField != EXPECTED_LENGTH_FIELD) {
                return DecodeResult.Rejected(RejectionReason.INVALID_LENGTH_FIELD)
            }
        }

        // 3. Rechazar tramas incompletas (< 15 bytes) o concatenadas (> 15 bytes)
        if (frame.size != EXPECTED_TOTAL_FRAME_BYTES) {
            return DecodeResult.Rejected(RejectionReason.INCOMPLETE_OR_CONCATENATED_FRAME)
        }

        // 4. Verificar checksum (suma unsigned de CMD + Device_ID + Data[0..7] módulo 65536, high byte primero)
        val bodyForChecksum = frame.copyOfRange(3, 13)
        val (expectedChecksumHigh, expectedChecksumLow) = computeChecksumBytes(bodyForChecksum)
        val actualChecksumHigh = frame[13].toInt() and 0xFF
        val actualChecksumLow = frame[14].toInt() and 0xFF
        if (actualChecksumHigh != expectedChecksumHigh || actualChecksumLow != expectedChecksumLow) {
            return DecodeResult.Rejected(RejectionReason.INVALID_CHECKSUM)
        }

        // 5. Verificar CMD = 0x88 (resultado en vivo)
        val cmd = frame[3].toInt() and 0xFF
        if (cmd != CMD_LIVE_RESULT) {
            return DecodeResult.Rejected(RejectionReason.UNAUTHORIZED_COMMAND)
        }

        // 6. Verificar Device_ID = 0x85 (familia GLM-76 / GLM-76L confirmada en el ejemplo del proveedor)
        val deviceId = frame[4].toInt() and 0xFF
        if (deviceId != DEVICE_ID_GLM76_FAMILY) {
            return DecodeResult.Rejected(RejectionReason.UNAUTHORIZED_DEVICE_ID)
        }

        // 7. Data[0..7] corresponde a frame[5..12]
        // data[6] (frame[11]) = Unidad: solo 0x00 (mg/dL)
        val unitCode = frame[11].toInt() and 0xFF
        if (unitCode != UNIT_MG_DL) {
            return DecodeResult.Rejected(RejectionReason.UNAUTHORIZED_UNIT)
        }

        // data[7] (frame[12]) = Tipo de tira: solo 0x10 (glucosa); rechaza ácido úrico (0x40), cetonas (0x50) y otros
        val stripType = frame[12].toInt() and 0xFF
        if (stripType != STRIP_TYPE_BLOOD_GLUCOSE) {
            return DecodeResult.Rejected(RejectionReason.UNAUTHORIZED_STRIP_TYPE)
        }

        // data[2..3] (frame[7..8]) = Valor sin signo little-endian
        val lowByte = frame[7].toInt() and 0xFF
        val highByte = frame[8].toInt() and 0xFF
        val valueMgDl = (highByte shl 8) or lowByte

        // 8. Validar rango documentado 20..600 mg/dL
        if (valueMgDl !in MIN_VERIFIED_MG_DL..MAX_VERIFIED_MG_DL) {
            return DecodeResult.Rejected(RejectionReason.VALUE_OUT_OF_RANGE)
        }

        return DecodeResult.VerifiedLiveGlucose(valueMgDl = valueMgDl)
    }

    /**
     * Devuelve únicamente el valor verificable en mg/dL (20..600) o null si la trama es rechazada.
     */
    fun parseVerifiedMgDlOrNull(frame: ByteArray): Int? {
        return (decodeLiveFrame(frame) as? DecodeResult.VerifiedLiveGlucose)?.valueMgDl
    }
}

/**
 * Reensamblador de flujo BLE para tramas en vivo del GLM-76L (`AA 55 0C 88 85 ...`).
 * - Soporta paquetes BLE fragmentados en 2 o más notificaciones (p. ej. 8 bytes + 7 bytes).
 * - Soporta múltiples tramas concatenadas en una misma notificación o ráfaga.
 * - Descarta ruido o tramas corruptas previas buscando el siguiente delimitador `AA 55 0C`.
 */
class YaseeLiveFrameStreamAssembler {
    private val buffersByChannel = LinkedHashMap<String, ByteArrayOutputStream>()

    @Synchronized
    fun onIncomingChunk(
        channelKey: String = "default",
        chunk: ByteArray
    ): List<YaseeGlm76LiveFrameReader.DecodeResult.VerifiedLiveGlucose> {
        if (chunk.isEmpty()) return emptyList()
        val stream = buffersByChannel.getOrPut(channelKey) { ByteArrayOutputStream(64) }
        stream.write(chunk)

        var current = stream.toByteArray()
        if (current.size > MAX_CHANNEL_BUFFER_BYTES) {
            current = current.copyOfRange(current.size - MAX_CHANNEL_BUFFER_BYTES, current.size)
        }

        val verifiedOut = mutableListOf<YaseeGlm76LiveFrameReader.DecodeResult.VerifiedLiveGlucose>()
        var cursor = 0

        while (cursor < current.size) {
            // Buscar inicio de cabecera AA
            val b0 = current[cursor].toInt() and 0xFF
            if (b0 != YaseeGlm76LiveFrameReader.HEADER_BYTE_0) {
                cursor += 1
                continue
            }
            // Si AA es el último byte recibido hasta ahora, esperar el siguiente fragmento
            if (cursor + 1 >= current.size) {
                break
            }
            val b1 = current[cursor + 1].toInt() and 0xFF
            if (b1 != YaseeGlm76LiveFrameReader.HEADER_BYTE_1) {
                cursor += 1
                continue
            }
            // Tenemos AA 55; si aún no llegó el byte de longitud, esperar más bytes
            if (cursor + 2 >= current.size) {
                break
            }
            val lenByte = current[cursor + 2].toInt() and 0xFF
            if (lenByte != YaseeGlm76LiveFrameReader.EXPECTED_LENGTH_FIELD) {
                // No es una trama de longitud 0x0C; avanzar tras AA 55 para buscar otra cabecera
                cursor += 2
                continue
            }
            // Si aún no están completos los 15 bytes de la trama AA 55 0C, esperar el siguiente fragmento
            if (cursor + YaseeGlm76LiveFrameReader.EXPECTED_TOTAL_FRAME_BYTES > current.size) {
                break
            }

            val candidateFrame = current.copyOfRange(
                cursor,
                cursor + YaseeGlm76LiveFrameReader.EXPECTED_TOTAL_FRAME_BYTES
            )
            when (val decoded = YaseeGlm76LiveFrameReader.decodeLiveFrame(candidateFrame)) {
                is YaseeGlm76LiveFrameReader.DecodeResult.VerifiedLiveGlucose -> {
                    verifiedOut.add(decoded)
                    cursor += YaseeGlm76LiveFrameReader.EXPECTED_TOTAL_FRAME_BYTES
                }
                is YaseeGlm76LiveFrameReader.DecodeResult.Rejected -> {
                    if (decoded.reason == YaseeGlm76LiveFrameReader.RejectionReason.INVALID_CHECKSUM) {
                        // Podría haber un falso AA 55 0C en ruido antes de una trama real
                        cursor += 2
                    } else {
                        // Trama bien formada pero de otro comando (ej. 0x8A) o tira no admitida (0x40/0x50): consumir los 15 bytes
                        cursor += YaseeGlm76LiveFrameReader.EXPECTED_TOTAL_FRAME_BYTES
                    }
                }
            }
        }

        stream.reset()
        if (cursor < current.size) {
            stream.write(current, cursor, current.size - cursor)
        }
        return verifiedOut
    }

    @Synchronized
    fun reset() {
        buffersByChannel.clear()
    }

    companion object {
        private const val MAX_CHANNEL_BUFFER_BYTES = 256
    }
}

/**
 * Deduplicador de ráfagas de reintento para tramas en vivo del GLM-76L.
 * - La trama en vivo `0x88` no trae timestamp ni ID de registro.
 * - Asigna el instante de recepción del teléfono (`receivedAtMs`).
 * - Filtra reenvíos idénticos inmediatos del glucómetro dentro de una ventana corta de reintento
 *   (`retryBurstWindowMs`, por defecto 4 segundos) en la misma sesión, pero CONSERVA mediciones
 *   con el mismo valor de glucosa tomadas en momentos distintos (fuera de la ventana de ráfaga).
 */
class LiveFrameBurstDeduplicator(
    private val retryBurstWindowMs: Long = DEFAULT_RETRY_BURST_WINDOW_MS
) {
    private var lastAcceptedValueMgDl: Int? = null
    private var lastAcceptedTimestampMs: Long? = null

    @Synchronized
    fun acceptOrFilterDuplicate(
        valueMgDl: Int,
        receivedAtMs: Long
    ): ParsedBleGlucose? {
        val prevValue = lastAcceptedValueMgDl
        val prevTime = lastAcceptedTimestampMs
        if (prevValue != null && prevTime != null) {
            val deltaMs = receivedAtMs - prevTime
            if (prevValue == valueMgDl && deltaMs in 0..retryBurstWindowMs) {
                return null
            }
        }
        lastAcceptedValueMgDl = valueMgDl
        lastAcceptedTimestampMs = receivedAtMs
        return ParsedBleGlucose(
            valueMgDl = valueMgDl,
            timestampMs = receivedAtMs
        )
    }

    @Synchronized
    fun reset() {
        lastAcceptedValueMgDl = null
        lastAcceptedTimestampMs = null
    }

    companion object {
        const val DEFAULT_RETRY_BURST_WINDOW_MS: Long = 4_000L
    }
}

/**
 * Adaptador de protocolo en producción para `BiomedicalDeviceId.Glucocare_Match` (GLM-76L).
 * - No inventa UUIDs GATT ni escribe comandos en características desconocidas.
 * - Si aún no se ha fijado un UUID único de notificación, permite suscripción pasiva acotada
 *   únicamente a características `notify`/`indicate` de servicios no genéricos descubiertos en la sesión,
 *   decodificando exclusivamente tramas `0x88` autenticadas por cabecera, ID `0x85` y checksum.
 * - Mantiene la descarga de memoria histórica (`0x8A`) pendiente de confirmar el canal de escritura
 *   con una unidad física.
 */
object Glm76lBleProtocolAdapter {
    private val GENERIC_GATT_SERVICE_UUIDS = setOf(
        "00001800-0000-1000-8000-00805F9B34FB", // Generic Access
        "00001801-0000-1000-8000-00805F9B34FB", // Generic Attribute
        "00001808-0000-1000-8000-00805F9B34FB", // Standard Glucose Service (manejado por ruta SIG)
        "0000180A-0000-1000-8000-00805F9B34FB", // Device Information
        "0000180F-0000-1000-8000-00805F9B34FB"  // Battery Service
    )

    fun supportsModel(modelId: BiomedicalDeviceId?): Boolean {
        return modelId == BiomedicalDeviceId.Glucocare_Match
    }

    fun isEligiblePassiveNotifyCharacteristic(
        serviceUuid: String,
        characteristicProperties: List<String>
    ): Boolean {
        if (serviceUuid.uppercase() in GENERIC_GATT_SERVICE_UUIDS) return false
        return characteristicProperties.any { it == "notify" || it == "indicate" }
    }

    fun findPassiveNotifyCandidateCount(
        discoveredServices: List<DiscoveredGattServiceInfo>
    ): Int {
        var count = 0
        for (service in discoveredServices) {
            if (service.uuid.uppercase() in GENERIC_GATT_SERVICE_UUIDS) continue
            for (ch in service.characteristics) {
                if (ch.properties.any { it == "notify" || it == "indicate" }) {
                    count += 1
                }
            }
        }
        return count
    }

    fun buildChannelKey(serviceUuid: UUID, charUuid: UUID): String {
        return "${serviceUuid.toString().uppercase()}/${charUuid.toString().uppercase()}"
    }
}
