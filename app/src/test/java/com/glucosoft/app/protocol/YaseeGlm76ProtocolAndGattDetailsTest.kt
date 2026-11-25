package com.glucosoft.app.protocol

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import com.glucosoft.app.estimateSingleLineNavLabelWidthDp
import com.glucosoft.app.model.BiomedicalCatalog
import com.glucosoft.app.model.BiomedicalDeviceId
import com.glucosoft.app.model.PatientTab
import com.glucosoft.app.planBottomNavigationLayout
import com.glucosoft.app.protocol.YaseeGlm76LiveFrameReader.DecodeResult
import com.glucosoft.app.protocol.YaseeGlm76LiveFrameReader.RejectionReason
import com.glucosoft.app.service.BleGlucometerManager
import com.glucosoft.app.service.ConnectedMeterSession
import com.glucosoft.app.service.DiscoveredGattCharacteristicInfo
import com.glucosoft.app.service.DiscoveredGattServiceInfo
import com.glucosoft.app.service.GattClientHandle
import com.glucosoft.app.service.MainThreadDispatcher
import com.glucosoft.app.service.ParsedBleGlucose
import com.glucosoft.app.service.StandardGlucoseGattCapabilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YaseeGlm76ProtocolAndGattDetailsTest {

    private class QueuedTestMainThreadDispatcher : MainThreadDispatcher {
        private val queue = ArrayDeque<() -> Unit>()
        private val delayedQueue = ArrayDeque<() -> Unit>()

        override fun post(block: () -> Unit) {
            queue.addLast(block)
        }

        override fun postDelayed(delayMs: Long, block: () -> Unit) {
            delayedQueue.addLast(block)
        }

        fun flushMainQueue() {
            while (queue.isNotEmpty()) {
                queue.removeFirst().invoke()
            }
        }

        fun flushDelayedQueue() {
            while (delayedQueue.isNotEmpty()) {
                delayedQueue.removeFirst().invoke()
            }
        }
    }

    private class FakeGattClientHandle(
        private val servicesSnapshot: List<DiscoveredGattServiceInfo>,
        private val supportsStandardGlucose: Boolean,
        private val supportsRacp: Boolean,
        private val discoverServicesResult: Boolean = true,
        var onDisconnectCallback: (() -> Unit)? = null
    ) : GattClientHandle {
        override val rawGatt: BluetoothGatt? = null
        var discoverServicesCalls: Int = 0
        var configureStandardCalls: Int = 0
        var writeRacpCalls: Int = 0
        var disconnectCalls: Int = 0
        var closeCalls: Int = 0

        override fun discoverServices(): Boolean {
            discoverServicesCalls += 1
            return discoverServicesResult
        }

        override fun getDiscoveredServicesSnapshot(): List<DiscoveredGattServiceInfo> {
            return servicesSnapshot
        }

        override fun configureStandardGlucoseService(): StandardGlucoseGattCapabilities {
            configureStandardCalls += 1
            return StandardGlucoseGattCapabilities(
                supportsStandardGlucose = supportsStandardGlucose,
                supportsRacp = supportsRacp,
                racpCharacteristic = null
            )
        }

        override fun writeRacpReportAllRecords(
            racpCharacteristic: BluetoothGattCharacteristic?
        ): Boolean {
            writeRacpCalls += 1
            return true
        }

        override fun disconnect() {
            disconnectCalls += 1
            onDisconnectCallback?.invoke()
        }

        override fun close() {
            closeCalls += 1
        }
    }

    /**
     * Construye una trama BLE SIG estándar (0x2A18) ficticia de 12 bytes para un valor en mg/dL.
     */
    private fun buildStandardSigGlucosePacket(valueMgDl: Int): ByteArray {
        // flags = 0x02 (concentración presente en kg/L, sin time offset)
        // SFLOAT con exponente -5 (0xB en 4 bits superiores) -> valueMgDl * 10^-5 kg/L * 100000 = valueMgDl mg/dL
        val sfloatRaw = (0xB shl 12) or (valueMgDl and 0x0FFF)
        return byteArrayOf(
            0x02,
            0x01, 0x00, // seq = 1
            0xEA.toByte(), 0x07, // year 2026
            11, // month
            25, // day
            8,  // hour
            30, // minute
            0,  // second
            (sfloatRaw and 0xFF).toByte(),
            ((sfloatRaw ushr 8) and 0xFF).toByte()
        )
    }

    /**
     * Construye una trama de 15 bytes recalculando el checksum válido (suma unsigned de
     * CMD + Device_ID + Data[0..7] módulo 65536, high byte primero) para probar cambios de campos.
     */
    private fun buildFrameWithRecalculatedChecksum(
        header0: Int = 0xAA,
        header1: Int = 0x55,
        lengthByte: Int = 0x0C,
        cmd: Int = 0x88,
        deviceId: Int = 0x85,
        data0: Int = 0x34,
        data1: Int = 0x03,
        valueMgDlLittleEndian: Int = 292,
        data4: Int = 0x1C,
        data5: Int = 0x01,
        unitCode: Int = 0x00,
        stripType: Int = 0x10
    ): ByteArray {
        val valLow = valueMgDlLittleEndian and 0xFF
        val valHigh = (valueMgDlLittleEndian ushr 8) and 0xFF
        val body = byteArrayOf(
            cmd.toByte(),
            deviceId.toByte(),
            data0.toByte(),
            data1.toByte(),
            valLow.toByte(),
            valHigh.toByte(),
            data4.toByte(),
            data5.toByte(),
            unitCode.toByte(),
            stripType.toByte()
        )
        val (chkHigh, chkLow) = YaseeGlm76LiveFrameReader.computeChecksumBytes(body)
        return byteArrayOf(
            header0.toByte(),
            header1.toByte(),
            lengthByte.toByte(),
            *body,
            chkHigh.toByte(),
            chkLow.toByte()
        )
    }

    @Test
    fun `decodes exact supplier manual live frame AA 55 0C 88 85 34 03 24 01 1C 01 00 10 01 96 to 292 mg_dL`() {
        val manualFrame = byteArrayOf(
            0xAA.toByte(),
            0x55.toByte(),
            0x0C.toByte(),
            0x88.toByte(),
            0x85.toByte(),
            0x34.toByte(),
            0x03.toByte(),
            0x24.toByte(),
            0x01.toByte(),
            0x1C.toByte(),
            0x01.toByte(),
            0x00.toByte(),
            0x10.toByte(),
            0x01.toByte(),
            0x96.toByte()
        )
        assertEquals(15, manualFrame.size)

        val result = YaseeGlm76LiveFrameReader.decodeLiveFrame(manualFrame)
        assertTrue(result is DecodeResult.VerifiedLiveGlucose)
        val verified = result as DecodeResult.VerifiedLiveGlucose
        assertEquals(292, verified.valueMgDl)
        assertTrue(verified.familyModel.contains("GLM-76L"))
        assertEquals(292, YaseeGlm76LiveFrameReader.parseVerifiedMgDlOrNull(manualFrame))
    }

    @Test
    fun `accepts documented boundary values 20 and 600 mg_dL and rejects 19 and 601 with recalculated checksums`() {
        val minBoundaryFrame = buildFrameWithRecalculatedChecksum(valueMgDlLittleEndian = 20)
        assertEquals(20, YaseeGlm76LiveFrameReader.parseVerifiedMgDlOrNull(minBoundaryFrame))

        val maxBoundaryFrame = buildFrameWithRecalculatedChecksum(valueMgDlLittleEndian = 600)
        assertEquals(600, YaseeGlm76LiveFrameReader.parseVerifiedMgDlOrNull(maxBoundaryFrame))

        val belowMinFrame = buildFrameWithRecalculatedChecksum(valueMgDlLittleEndian = 19)
        assertEquals(
            DecodeResult.Rejected(RejectionReason.VALUE_OUT_OF_RANGE),
            YaseeGlm76LiveFrameReader.decodeLiveFrame(belowMinFrame)
        )
        assertNull(YaseeGlm76LiveFrameReader.parseVerifiedMgDlOrNull(belowMinFrame))

        val zeroValueFrame = buildFrameWithRecalculatedChecksum(valueMgDlLittleEndian = 0)
        assertEquals(
            DecodeResult.Rejected(RejectionReason.VALUE_OUT_OF_RANGE),
            YaseeGlm76LiveFrameReader.decodeLiveFrame(zeroValueFrame)
        )

        val aboveMaxFrame = buildFrameWithRecalculatedChecksum(valueMgDlLittleEndian = 601)
        assertEquals(
            DecodeResult.Rejected(RejectionReason.VALUE_OUT_OF_RANGE),
            YaseeGlm76LiveFrameReader.decodeLiveFrame(aboveMaxFrame)
        )

        val maxUnsigned16Frame = buildFrameWithRecalculatedChecksum(valueMgDlLittleEndian = 0xFFFF)
        assertEquals(
            DecodeResult.Rejected(RejectionReason.VALUE_OUT_OF_RANGE),
            YaseeGlm76LiveFrameReader.decodeLiveFrame(maxUnsigned16Frame)
        )
    }

    @Test
    fun `rejects unauthorized commands, device IDs, units, and non-glucose strips including uric acid 40 and ketone 50`() {
        // Ácido úrico (0x40) con checksum recalculado -> rechazado
        val uricAcidFrame = buildFrameWithRecalculatedChecksum(stripType = 0x40)
        assertEquals(
            DecodeResult.Rejected(RejectionReason.UNAUTHORIZED_STRIP_TYPE),
            YaseeGlm76LiveFrameReader.decodeLiveFrame(uricAcidFrame)
        )

        // Cetonas (0x50) con checksum recalculado -> rechazado
        val ketoneFrame = buildFrameWithRecalculatedChecksum(stripType = 0x50)
        assertEquals(
            DecodeResult.Rejected(RejectionReason.UNAUTHORIZED_STRIP_TYPE),
            YaseeGlm76LiveFrameReader.decodeLiveFrame(ketoneFrame)
        )

        // Unidad distinta de 0x00 (mg/dL) con checksum recalculado -> rechazado
        val nonMgDlUnitFrame = buildFrameWithRecalculatedChecksum(unitCode = 0x01)
        assertEquals(
            DecodeResult.Rejected(RejectionReason.UNAUTHORIZED_UNIT),
            YaseeGlm76LiveFrameReader.decodeLiveFrame(nonMgDlUnitFrame)
        )

        // Comando distinto de 0x88 (ej. lectura de memoria, ACK, hora, reposo) -> rechazado
        for (unauthorizedCmd in listOf(0x01, 0x55, 0x80, 0x87, 0x89, 0xAA)) {
            val cmdFrame = buildFrameWithRecalculatedChecksum(cmd = unauthorizedCmd)
            assertEquals(
                DecodeResult.Rejected(RejectionReason.UNAUTHORIZED_COMMAND),
                YaseeGlm76LiveFrameReader.decodeLiveFrame(cmdFrame)
            )
        }

        // Device_ID distinto de 0x85 (sin ampliar IDs por conjetura) -> rechazado
        for (unauthorizedId in listOf(0x00, 0x76, 0x84, 0x86, 0xFF)) {
            val idFrame = buildFrameWithRecalculatedChecksum(deviceId = unauthorizedId)
            assertEquals(
                DecodeResult.Rejected(RejectionReason.UNAUTHORIZED_DEVICE_ID),
                YaseeGlm76LiveFrameReader.decodeLiveFrame(idFrame)
            )
        }
    }

    @Test
    fun `rejects invalid delimiter, wrong length field, bad checksum, incomplete and concatenated frames`() {
        val valid = buildFrameWithRecalculatedChecksum()

        // Delimitador inválido
        val badHeader0 = valid.clone().apply { this[0] = 0xAB.toByte() }
        assertEquals(
            DecodeResult.Rejected(RejectionReason.INVALID_HEADER_DELIMITER),
            YaseeGlm76LiveFrameReader.decodeLiveFrame(badHeader0)
        )
        val badHeader1 = valid.clone().apply { this[1] = 0x54.toByte() }
        assertEquals(
            DecodeResult.Rejected(RejectionReason.INVALID_HEADER_DELIMITER),
            YaseeGlm76LiveFrameReader.decodeLiveFrame(badHeader1)
        )

        // Longitud declarada incorrecta
        val badLength = valid.clone().apply { this[2] = 0x0D.toByte() }
        assertEquals(
            DecodeResult.Rejected(RejectionReason.INVALID_LENGTH_FIELD),
            YaseeGlm76LiveFrameReader.decodeLiveFrame(badLength)
        )

        // Tramas incompletas y vacías
        assertEquals(
            DecodeResult.Rejected(RejectionReason.INCOMPLETE_OR_CONCATENATED_FRAME),
            YaseeGlm76LiveFrameReader.decodeLiveFrame(ByteArray(0))
        )
        assertEquals(
            DecodeResult.Rejected(RejectionReason.INCOMPLETE_OR_CONCATENATED_FRAME),
            YaseeGlm76LiveFrameReader.decodeLiveFrame(valid.copyOf(14))
        )

        // Tramas concatenadas (dos tramas válidas seguidas = 30 bytes)
        val concatenated = valid + valid
        assertEquals(
            DecodeResult.Rejected(RejectionReason.INCOMPLETE_OR_CONCATENATED_FRAME),
            YaseeGlm76LiveFrameReader.decodeLiveFrame(concatenated)
        )

        // Checksum inválido (alterando último byte o penúltimo byte)
        val badChecksumLow = valid.clone().apply { this[14] = 0x97.toByte() }
        assertEquals(
            DecodeResult.Rejected(RejectionReason.INVALID_CHECKSUM),
            YaseeGlm76LiveFrameReader.decodeLiveFrame(badChecksumLow)
        )
        val badChecksumHigh = valid.clone().apply { this[13] = 0x02.toByte() }
        assertEquals(
            DecodeResult.Rejected(RejectionReason.INVALID_CHECKSUM),
            YaseeGlm76LiveFrameReader.decodeLiveFrame(badChecksumHigh)
        )
    }

    @Test
    fun `GATT service details preserve grouping and properties and copy output excludes MAC, names and health data`() {
        val mask = BluetoothGattCharacteristic.PROPERTY_READ or
            BluetoothGattCharacteristic.PROPERTY_WRITE or
            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
            BluetoothGattCharacteristic.PROPERTY_NOTIFY or
            BluetoothGattCharacteristic.PROPERTY_INDICATE
        val allProps = BleGlucometerManager.extractCharacteristicProperties(mask)
        assertEquals(
            listOf("read", "write", "write sin respuesta", "notify", "indicate"),
            allProps
        )

        val fakeServices = listOf(
            DiscoveredGattServiceInfo(
                uuid = "0000180A-0000-1000-8000-00805F9B34FB",
                characteristics = listOf(
                    DiscoveredGattCharacteristicInfo(
                        uuid = "00002A29-0000-1000-8000-00805F9B34FB",
                        properties = BleGlucometerManager.extractCharacteristicProperties(
                            BluetoothGattCharacteristic.PROPERTY_READ
                        )
                    )
                )
            ),
            DiscoveredGattServiceInfo(
                uuid = "0000FFF0-0000-1000-8000-00805F9B34FB",
                characteristics = listOf(
                    DiscoveredGattCharacteristicInfo(
                        uuid = "0000FFF1-0000-1000-8000-00805F9B34FB",
                        properties = BleGlucometerManager.extractCharacteristicProperties(
                            BluetoothGattCharacteristic.PROPERTY_NOTIFY
                        )
                    ),
                    DiscoveredGattCharacteristicInfo(
                        uuid = "0000FFF2-0000-1000-8000-00805F9B34FB",
                        properties = BleGlucometerManager.extractCharacteristicProperties(
                            BluetoothGattCharacteristic.PROPERTY_WRITE or
                                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
                        )
                    )
                )
            )
        )

        val proprietarySession = ConnectedMeterSession(
            deviceName = "Teléfono Personal de Juan / GLM-76L Serial 998877",
            address = "AA:BB:CC:DD:EE:FF",
            matchedModelId = BiomedicalDeviceId.Glucocare_Match,
            matchedModelName = "Glucocare Match",
            supportsStandardGlucose = false,
            supportsMemorySync = false,
            discoveredServiceCount = fakeServices.size,
            discoveredServiceUuids = fakeServices.map { it.uuid },
            discoveredServices = fakeServices,
            connectionMessage = "Conectado (solo servicio propietario) · Falta confirmar el canal de medición."
        )

        // Distingue conectado de listo para recibir mediciones
        assertFalse(proprietarySession.isReadyForMeasurements)
        assertEquals(2, proprietarySession.discoveredServices.size)
        assertEquals(2, proprietarySession.discoveredServices[1].characteristics.size)

        val copiedText = BleGlucometerManager.formatBluetoothProtocolDetailsForCopy(
            proprietarySession.discoveredServices
        )

        // Conserva agrupación por servicio, UUID de características y propiedades
        assertTrue(copiedText.contains("Servicio 1: 0000180A-0000-1000-8000-00805F9B34FB"))
        assertTrue(copiedText.contains("Característica: 00002A29-0000-1000-8000-00805F9B34FB | Propiedades: read"))
        assertTrue(copiedText.contains("Servicio 2: 0000FFF0-0000-1000-8000-00805F9B34FB"))
        assertTrue(copiedText.contains("Característica: 0000FFF1-0000-1000-8000-00805F9B34FB | Propiedades: notify"))
        assertTrue(copiedText.contains("Característica: 0000FFF2-0000-1000-8000-00805F9B34FB | Propiedades: write, write sin respuesta"))

        // No incluye MAC, ni nombre del teléfono o periférico, ni serial, ni lecturas de salud
        assertFalse(copiedText.contains("AA:BB:CC:DD:EE:FF"))
        assertFalse(copiedText.contains("Juan"))
        assertFalse(copiedText.contains("998877"))
        assertFalse(copiedText.contains("292"))
    }

    @Test
    fun `catalog single source of truth exposes only Glucocare Match GLM-76L for Bluetooth, auto-incorporates newly confirmed models, and rejects ambiguous aliases`() {
        // Todos los modelos internos permanecen en el catálogo para historial, exportación PDF y registro manual
        assertEquals(5, BiomedicalCatalog.BIOMEDICAL_DEVICES.size)

        // Hoy el único modelo con protocolo Bluetooth confirmado es Glucocare Match (GLM-76L)
        val confirmedToday = BiomedicalCatalog.confirmedBluetoothModels()
        assertEquals(1, confirmedToday.size)
        val glm76lSpec = confirmedToday.first()
        assertEquals(BiomedicalDeviceId.Glucocare_Match, glm76lSpec.id)
        assertEquals("Glucocare Match (GLM-76L)", glm76lSpec.displayName)
        assertTrue(glm76lSpec.hasConfirmedProtocol)
        assertEquals(
            listOf(
                "Glucocare Match",
                "GLM-76L",
                "GLM76L",
                "Glucocare Match GLM-76L",
                "Glucocare Match (GLM-76L)"
            ),
            glm76lSpec.confirmedBleIdentifiers
        )
        assertEquals("Prueba con el medidor pendiente", glm76lSpec.pendingTestBadgeLabel)
        assertTrue(
            glm76lSpec.summary.contains("Protocolo Bluetooth confirmado por el proveedor para GLM-76L")
        )
        assertTrue(
            glm76lSpec.protocolNotes.contains("Protocolo Bluetooth confirmado por el proveedor para GLM-76L")
        )

        // Ninguno de los otros 4 modelos ni OTRO_COMPATIBLE están confirmados para Bluetooth hoy
        for (pendingId in listOf(
            BiomedicalDeviceId.OKMeter_Match_II,
            BiomedicalDeviceId.GlucoLeader_Enhance,
            BiomedicalDeviceId.Glucocare_D,
            BiomedicalDeviceId.Glucomax,
            BiomedicalDeviceId.Otro_Compatible
        )) {
            val spec = BiomedicalCatalog.specForId(pendingId)
            assertFalse("El modelo $pendingId debe estar pendiente por defecto", spec.hasConfirmedProtocol)
            assertFalse(spec.isEligibleForPatientBluetooth)
        }

        // Al marcar en pruebas un segundo modelo ficticio como confirmado con sus alias Bluetooth propios,
        // queda automáticamente incorporado al catálogo Bluetooth y elegible para escáner y conexión
        // por su propia ficha, sin lógica dedicada en el filtro común.
        val futureConfirmedCatalog = BiomedicalCatalog.BIOMEDICAL_DEVICES.map { spec ->
            if (spec.id == BiomedicalDeviceId.OKMeter_Match_II) {
                spec.copy(
                    hasConfirmedProtocol = true,
                    confirmedBleIdentifiers = listOf("OKMeter Match II", "OKM-II-BLE")
                )
            } else {
                spec
            }
        }
        val confirmedWithSecondModel = BiomedicalCatalog.confirmedBluetoothModels(futureConfirmedCatalog)
        assertEquals(2, confirmedWithSecondModel.size)
        assertEquals(
            listOf(BiomedicalDeviceId.OKMeter_Match_II, BiomedicalDeviceId.Glucocare_Match),
            confirmedWithSecondModel.map { it.id }
        )
        assertEquals(
            BiomedicalDeviceId.OKMeter_Match_II,
            BleGlucometerManager.isScanAdvertisementEligible(
                advertisedName = "OKM-II-BLE",
                catalog = futureConfirmedCatalog
            )?.id
        )
        assertEquals(
            BiomedicalDeviceId.OKMeter_Match_II,
            BiomedicalCatalog.resolveEligibleBluetoothConnectionSpec(
                discoveredName = "OKMeter Match II",
                matchedSpec = null,
                catalog = futureConfirmedCatalog
            )?.id
        )

        // Si dos modelos registran el mismo alias exacto, debe tratarse como ambiguo y rechazarse (null)
        val ambiguousTwoConfirmedCatalog = BiomedicalCatalog.BIOMEDICAL_DEVICES.map { spec ->
            when (spec.id) {
                BiomedicalDeviceId.OKMeter_Match_II -> spec.copy(
                    hasConfirmedProtocol = true,
                    confirmedBleIdentifiers = listOf("OKMeter Match II", "SHARED-METER-BLE")
                )
                BiomedicalDeviceId.Glucocare_Match -> spec.copy(
                    hasConfirmedProtocol = true,
                    confirmedBleIdentifiers = spec.confirmedBleIdentifiers + "shared-meter-ble"
                )
                else -> spec
            }
        }
        assertNull(
            "Un alias exacto registrado por dos modelos confirmados debe rechazarse por ambiguo",
            BiomedicalCatalog.matchConfirmedBluetoothModel(
                advertisedName = "SHARED-METER-BLE",
                catalog = ambiguousTwoConfirmedCatalog
            )
        )
        assertNull(
            BleGlucometerManager.isScanAdvertisementEligible(
                advertisedName = "SHARED-METER-BLE",
                catalog = ambiguousTwoConfirmedCatalog
            )
        )
    }

    @Test
    fun `BLE scan and connection filter accepts only full normalized confirmed names and rejects Auriculares GLM-76L, Reloj Glucocare Match, prefixes, suffixes and pending models`() {
        // 1. Pruebas positivas de los nombres completos permitidos (con variaciones de mayúsculas/minúsculas y espacios)
        for (validFullName in listOf(
            "Glucocare Match",
            "glucocare match",
            "  Glucocare   Match  ",
            "GLM-76L",
            "glm-76l",
            "  GLM-76L ",
            "GLM76L",
            "glm76l",
            "Glucocare Match GLM-76L",
            "glucocare   match   glm-76l",
            "Glucocare Match (GLM-76L)",
            "  glucocare match (glm-76l)  "
        )) {
            val matched = BleGlucometerManager.isScanAdvertisementEligible(
                advertisedName = validFullName,
                advertisesStandardGlucoseService = false
            )
            assertNotNull("Debe aceptar el nombre completo confirmado: '$validFullName'", matched)
            assertEquals(BiomedicalDeviceId.Glucocare_Match, matched?.id)
        }

        // 2. Pruebas negativas: «Auriculares GLM-76L», «Reloj Glucocare Match», prefijos/sufijos,
        //    nombres genéricos, Glucocare D, GLM-76 sin L, GLM76, otros sufijos, modelos pendientes y «Otro»
        for (rejectedName in listOf(
            "",
            "   ",
            "Auriculares GLM-76L",
            "Reloj Glucocare Match",
            "Auriculares GLM76L",
            "Altavoz Glucocare Match (GLM-76L)",
            "GLM-76L Auriculares",
            "Glucocare Match Reloj",
            "DB Glucocare Match GLM-76L",
            "YASEE GLM-76L",
            "Galaxy Buds2 Pro",
            "Apple Watch de María",
            "Mi Smart Band 8",
            "Glucocare",
            "DB Glucocare",
            "Glucocare D",
            "DB Glucocare D",
            "Glucocare Match D",
            "Glucocare Match II",
            "Glucocare Match 2",
            "Glucocare Match Pro",
            "GLM-76",
            "GLM76",
            "DB GLM-76",
            "GLM-76B",
            "GLM-760",
            "GLM-76L2",
            "GLM-76LX",
            "GLM76L2",
            "OKMeter Match II",
            "OKMeter",
            "GlucoLeader Enhance",
            "GlucoLeader",
            "Glucomax",
            "Advocate",
            "Otro glucómetro compatible",
            "Glucómetro BLE (AA:BB)"
        )) {
            assertNull(
                "Debe rechazar en escaneo Bluetooth: '$rejectedName'",
                BleGlucometerManager.isScanAdvertisementEligible(
                    advertisedName = rejectedName,
                    advertisesStandardGlucoseService = false
                )
            )
            // Incluso si anuncia el servicio 0x1808 sin pertenecer a un modelo confirmado registrado, se rechaza
            assertNull(
                "Debe rechazar '$rejectedName' aunque anuncie 0x1808 si no tiene protocolo confirmado",
                BleGlucometerManager.isScanAdvertisementEligible(
                    advertisedName = rejectedName,
                    advertisesStandardGlucoseService = true
                )
            )
        }

        // 3. Intento de conexión con datos antiguos o previos de un modelo pendiente o con prefijo ajeno -> rechazado sin abrir sesión GATT
        val dispatcher = QueuedTestMainThreadDispatcher()
        val manager = BleGlucometerManager(context = null, mainThreadDispatcher = dispatcher)
        val fakeHandle = FakeGattClientHandle(
            servicesSnapshot = emptyList(),
            supportsStandardGlucose = true,
            supportsRacp = true
        )

        val rejectedPendingAttempt = manager.startAttemptWithHandleForTest(
            address = "AA:BB:CC:DD:EE:99",
            advertisedName = "OKMeter Match II",
            matchedSpec = BiomedicalCatalog.specForId(BiomedicalDeviceId.OKMeter_Match_II),
            handle = fakeHandle
        )
        assertTrue(rejectedPendingAttempt.terminated)
        assertNull(manager.connectedSession.value)
        assertNotNull(manager.errorMessage.value)
        assertTrue(manager.errorMessage.value!!.contains("protocolo Bluetooth confirmado"))

        val rejectedForeignDeviceAttempt = manager.startAttemptWithHandleForTest(
            address = "AA:BB:CC:DD:EE:98",
            advertisedName = "Auriculares GLM-76L",
            matchedSpec = BiomedicalCatalog.specForId(BiomedicalDeviceId.Glucocare_Match),
            handle = fakeHandle
        )
        assertTrue(rejectedForeignDeviceAttempt.terminated)
        assertNull(manager.connectedSession.value)

        // Cualquier callback GATT sobre un intento rechazado es ignorado por completo
        manager.handleGattConnectionStateChange(
            attempt = rejectedPendingAttempt,
            handle = fakeHandle,
            originGatt = null,
            status = BluetoothGatt.GATT_SUCCESS,
            newState = BluetoothProfile.STATE_CONNECTED
        )
        dispatcher.flushMainQueue()
        assertEquals(0, fakeHandle.discoverServicesCalls)
        assertNull(manager.connectedSession.value)

        // El reconocimiento interno separado para historial/registro manual sigue resolviendo todos los modelos conocidos
        assertEquals(
            BiomedicalDeviceId.Glucocare_D,
            BiomedicalCatalog.matchKnownModel("Glucocare D")?.id
        )
        assertEquals(
            BiomedicalDeviceId.OKMeter_Match_II,
            BiomedicalCatalog.matchKnownModel("OKMeter Match II")?.id
        )
        assertEquals(
            BiomedicalDeviceId.GlucoLeader_Enhance,
            BiomedicalCatalog.matchKnownModel("GlucoLeader Enhance")?.id
        )
        assertEquals(
            BiomedicalDeviceId.Glucomax,
            BiomedicalCatalog.matchKnownModel("Glucomax")?.id
        )
    }

    @Test
    fun `BleCaptureDialog search states verify initial, error and empty-after-real-scan with a single primary search action`() {
        val dispatcher = QueuedTestMainThreadDispatcher()
        val manager = BleGlucometerManager(context = null, mainThreadDispatcher = dispatcher)

        // 1. Estado inicial al abrir el diálogo por primera vez (antes de buscar):
        //    NO muestra «No encontramos un medidor compatible cerca», deja «Buscar glucómetro cercano»
        //    como única acción principal de búsqueda y conserva el acceso manual.
        manager.resetSearchStateForDialogOpen()
        val initialState = manager.currentSearchUiStateForTest()
        assertTrue(initialState.showPrimarySearchCard)
        assertFalse(initialState.showEmptyScanResultCard)
        assertNull(initialState.emptyScanTitle)
        assertEquals("Buscar glucómetro cercano", initialState.primarySearchAction.buttonLabel)
        assertEquals("start_ble_scan_button", initialState.primarySearchAction.testTag)
        assertTrue(initialState.primarySearchAction.enabled)
        assertNull(initialState.errorBannerMessage)
        assertTrue(initialState.showManualEntryFallback)

        // 2. Estado de error cuando faltan permisos de Bluetooth:
        //    Muestra el motivo correspondiente, conserva «Buscar glucómetro cercano» como única acción
        //    principal de búsqueda y NO muestra «No encontramos un medidor compatible cerca».
        manager.startSimulatedRealScanForTest(hasPermissions = false, isBluetoothEnabled = true)
        val permissionErrorState = manager.currentSearchUiStateForTest()
        assertTrue(permissionErrorState.showPrimarySearchCard)
        assertFalse(permissionErrorState.showEmptyScanResultCard)
        assertNull(permissionErrorState.emptyScanTitle)
        assertEquals("Buscar glucómetro cercano", permissionErrorState.primarySearchAction.buttonLabel)
        assertNotNull(permissionErrorState.errorBannerMessage)
        assertTrue(permissionErrorState.errorBannerMessage!!.contains("permisos de Bluetooth"))
        assertTrue(permissionErrorState.showManualEntryFallback)

        // 3. Estado de error cuando el Bluetooth está apagado:
        //    Muestra el motivo de Bluetooth apagado, conserva «Buscar glucómetro cercano» y NO muestra «No encontramos...».
        manager.startSimulatedRealScanForTest(hasPermissions = true, isBluetoothEnabled = false)
        val bluetoothOffErrorState = manager.currentSearchUiStateForTest()
        assertTrue(bluetoothOffErrorState.showPrimarySearchCard)
        assertFalse(bluetoothOffErrorState.showEmptyScanResultCard)
        assertNull(bluetoothOffErrorState.emptyScanTitle)
        assertEquals("Buscar glucómetro cercano", bluetoothOffErrorState.primarySearchAction.buttonLabel)
        assertNotNull(bluetoothOffErrorState.errorBannerMessage)
        assertTrue(bluetoothOffErrorState.errorBannerMessage!!.contains("apagado o no disponible"))
        assertTrue(bluetoothOffErrorState.showManualEntryFallback)

        // 4. Búsqueda realmente iniciada (en curso):
        //    Muestra una sola acción principal deshabilitada «Buscando glucómetros cercanos...» y no muestra estado vacío.
        manager.startSimulatedRealScanForTest(hasPermissions = true, isBluetoothEnabled = true)
        val scanningState = manager.currentSearchUiStateForTest()
        assertTrue(scanningState.showPrimarySearchCard)
        assertFalse(scanningState.showEmptyScanResultCard)
        assertEquals("Buscando glucómetros cercanos...", scanningState.primarySearchAction.buttonLabel)
        assertFalse(scanningState.primarySearchAction.enabled)
        assertNull(scanningState.errorBannerMessage)

        // 5. Estado vacío SOLO tras una búsqueda realmente iniciada y terminada sin modelos admitidos:
        //    Muestra «No encontramos un medidor compatible cerca» y «Volver a buscar» como única acción principal
        //    de búsqueda (ocultando la tarjeta superior para no duplicar botones) y conserva el acceso manual.
        dispatcher.flushDelayedQueue()
        val emptyAfterRealScanState = manager.currentSearchUiStateForTest()
        assertFalse(emptyAfterRealScanState.showPrimarySearchCard)
        assertTrue(emptyAfterRealScanState.showEmptyScanResultCard)
        assertEquals("No encontramos un medidor compatible cerca", emptyAfterRealScanState.emptyScanTitle)
        assertEquals("Volver a buscar", emptyAfterRealScanState.primarySearchAction.buttonLabel)
        assertEquals("retry_ble_scan_button", emptyAfterRealScanState.primarySearchAction.testTag)
        assertTrue(emptyAfterRealScanState.primarySearchAction.enabled)
        assertNull(emptyAfterRealScanState.errorBannerMessage)
        assertTrue(emptyAfterRealScanState.showManualEntryFallback)
    }

    @Test
    fun `fast switch A to B invalidates A before closing and ignores queued or late callbacks and readings from A`() {
        val dispatcher = QueuedTestMainThreadDispatcher()
        val manager = BleGlucometerManager(context = null, mainThreadDispatcher = dispatcher)
        val receivedReadings = mutableListOf<Pair<ParsedBleGlucose, ConnectedMeterSession>>()
        manager.onReadingReceived = { reading, session ->
            receivedReadings.add(reading to session)
        }

        // Catálogo de prueba con dos modelos confirmados (A y B) para verificar el cambio rápido A -> B
        val twoConfirmedModelsCatalog = BiomedicalCatalog.BIOMEDICAL_DEVICES.map { spec ->
            if (spec.id == BiomedicalDeviceId.OKMeter_Match_II) {
                spec.copy(
                    hasConfirmedProtocol = true,
                    confirmedBleIdentifiers = listOf("OKMeter Match II")
                )
            } else {
                spec
            }
        }

        val servicesA = listOf(
            DiscoveredGattServiceInfo(
                uuid = "00001808-0000-1000-8000-00805F9B34FB",
                characteristics = listOf(
                    DiscoveredGattCharacteristicInfo(
                        uuid = "00002A18-0000-1000-8000-00805F9B34FB",
                        properties = listOf("notify")
                    ),
                    DiscoveredGattCharacteristicInfo(
                        uuid = "00002A52-0000-1000-8000-00805F9B34FB",
                        properties = listOf("write", "indicate")
                    )
                )
            )
        )
        val handleA = FakeGattClientHandle(
            servicesSnapshot = servicesA,
            supportsStandardGlucose = true,
            supportsRacp = true
        )

        val servicesB = listOf(
            DiscoveredGattServiceInfo(
                uuid = "0000FFF0-0000-1000-8000-00805F9B34FB",
                characteristics = listOf(
                    DiscoveredGattCharacteristicInfo(
                        uuid = "0000FFF1-0000-1000-8000-00805F9B34FB",
                        properties = listOf("notify", "write")
                    )
                )
            )
        )
        val handleB = FakeGattClientHandle(
            servicesSnapshot = servicesB,
            supportsStandardGlucose = false,
            supportsRacp = false
        )

        // 1. Iniciar intento A (con soporte estándar y RACP)
        val attemptA = manager.startAttemptWithHandleForTest(
            address = "11:22:33:44:55:AA",
            advertisedName = "OKMeter Match II",
            matchedSpec = twoConfirmedModelsCatalog.first { it.id == BiomedicalDeviceId.OKMeter_Match_II },
            handle = handleA,
            catalog = twoConfirmedModelsCatalog
        )

        // Si disconnect() de A dispara síncronamente un callback de desconexión durante el cambio a B,
        // A ya debe estar invalidado ANTES de cerrarse.
        handleA.onDisconnectCallback = {
            manager.handleGattConnectionStateChange(
                attempt = attemptA,
                handle = handleA,
                originGatt = null,
                status = BluetoothGatt.GATT_SUCCESS,
                newState = BluetoothProfile.STATE_DISCONNECTED
            )
        }

        // A descubre servicios y recibe una lectura, dejando ambos callbacks YA ENCOLADOS para el hilo principal
        // antes de que la persona cambie rápidamente al medidor B.
        manager.handleGattServicesDiscovered(
            attempt = attemptA,
            handle = handleA,
            originGatt = null,
            status = BluetoothGatt.GATT_SUCCESS
        )
        manager.handleGattCharacteristicChanged(
            attempt = attemptA,
            handle = handleA,
            originGatt = null,
            serviceUuid = BleGlucometerManager.GLUCOSE_SERVICE_UUID,
            charUuid = BleGlucometerManager.GLUCOSE_MEASUREMENT_CHAR_UUID,
            bytes = buildStandardSigGlucosePacket(145)
        )

        // 2. Cambio rápido A -> B antes de que el hilo principal ejecute los callbacks encolados de A
        val attemptB = manager.startAttemptWithHandleForTest(
            address = "11:22:33:44:55:BB",
            advertisedName = "GLM-76L",
            matchedSpec = BiomedicalCatalog.specForId(BiomedicalDeviceId.Glucocare_Match),
            handle = handleB,
            catalog = twoConfirmedModelsCatalog
        )
        assertEquals(1, handleA.disconnectCalls)
        assertEquals(1, handleA.closeCalls)

        // 3. Ejecutar ahora la cola del hilo principal: los eventos encolados de A deben descartarse
        dispatcher.flushMainQueue()
        assertNull(manager.connectedSession.value)
        assertFalse(manager.hasActiveRacpForTest())
        assertTrue(receivedReadings.isEmpty())
        assertEquals("Conectando con GLM-76L...", manager.statusMessage.value)

        // 4. Llegan eventos tardíos de A después de haber iniciado B -> ninguno debe iniciar descubrimiento,
        //    ni suscribir características, ni alterar la sesión B
        val prevConfigureCallsA = handleA.configureStandardCalls
        manager.handleGattConnectionStateChange(
            attempt = attemptA,
            handle = handleA,
            originGatt = null,
            status = BluetoothGatt.GATT_SUCCESS,
            newState = BluetoothProfile.STATE_CONNECTED
        )
        manager.handleGattServicesDiscovered(
            attempt = attemptA,
            handle = handleA,
            originGatt = null,
            status = BluetoothGatt.GATT_SUCCESS
        )
        assertEquals(0, handleA.discoverServicesCalls)
        assertEquals(prevConfigureCallsA, handleA.configureStandardCalls)

        // 5. B conecta y descubre su servicio propietario
        manager.handleGattConnectionStateChange(
            attempt = attemptB,
            handle = handleB,
            originGatt = null,
            status = BluetoothGatt.GATT_SUCCESS,
            newState = BluetoothProfile.STATE_CONNECTED
        )
        assertEquals(1, handleB.discoverServicesCalls)

        manager.handleGattServicesDiscovered(
            attempt = attemptB,
            handle = handleB,
            originGatt = null,
            status = BluetoothGatt.GATT_SUCCESS
        )
        dispatcher.flushMainQueue()

        val activeSessionB = manager.connectedSession.value
        assertNotNull(activeSessionB)
        assertEquals("11:22:33:44:55:BB", activeSessionB?.address)
        assertEquals(BiomedicalDeviceId.Glucocare_Match, activeSessionB?.matchedModelId)
        assertFalse(activeSessionB?.supportsStandardGlucose ?: true)
        assertFalse(manager.hasActiveRacpForTest())

        // 6. Si llega un onCharacteristicChanged con el handle de A (o intento B pero handle de origen A),
        //    o un onConnectionStateChange de desconexión de A, la sesión B permanece intacta y sin lecturas falsas
        manager.handleGattCharacteristicChanged(
            attempt = attemptB,
            handle = handleA,
            originGatt = null,
            serviceUuid = BleGlucometerManager.GLUCOSE_SERVICE_UUID,
            charUuid = BleGlucometerManager.GLUCOSE_MEASUREMENT_CHAR_UUID,
            bytes = buildStandardSigGlucosePacket(180)
        )
        manager.handleGattConnectionStateChange(
            attempt = attemptA,
            handle = handleA,
            originGatt = null,
            status = BluetoothGatt.GATT_SUCCESS,
            newState = BluetoothProfile.STATE_DISCONNECTED
        )
        dispatcher.flushMainQueue()

        assertEquals("11:22:33:44:55:BB", manager.connectedSession.value?.address)
        assertTrue(receivedReadings.isEmpty())
    }

    @Test
    fun `disconnect with pending callbacks and non-GATT_SUCCESS on STATE_CONNECTED prevent stale session or readings`() {
        val dispatcher = QueuedTestMainThreadDispatcher()
        val manager = BleGlucometerManager(context = null, mainThreadDispatcher = dispatcher)
        val receivedReadings = mutableListOf<Pair<ParsedBleGlucose, ConnectedMeterSession>>()
        manager.onReadingReceived = { reading, session ->
            receivedReadings.add(reading to session)
        }

        val servicesStandard = listOf(
            DiscoveredGattServiceInfo(
                uuid = "00001808-0000-1000-8000-00805F9B34FB",
                characteristics = listOf(
                    DiscoveredGattCharacteristicInfo(
                        uuid = "00002A18-0000-1000-8000-00805F9B34FB",
                        properties = listOf("notify")
                    ),
                    DiscoveredGattCharacteristicInfo(
                        uuid = "00002A52-0000-1000-8000-00805F9B34FB",
                        properties = listOf("write", "indicate")
                    )
                )
            )
        )
        val handle1 = FakeGattClientHandle(
            servicesSnapshot = servicesStandard,
            supportsStandardGlucose = true,
            supportsRacp = true
        )

        // 1. Conectar medidor admitido (GLM-76L) que exponga servicio estándar 0x1808 y confirmar que entrega lectura mientras la sesión está activa
        val attempt1 = manager.startAttemptWithHandleForTest(
            address = "AA:BB:CC:DD:EE:01",
            advertisedName = "Glucocare Match",
            matchedSpec = BiomedicalCatalog.specForId(BiomedicalDeviceId.Glucocare_Match),
            handle = handle1
        )
        manager.handleGattServicesDiscovered(
            attempt = attempt1,
            handle = handle1,
            originGatt = null,
            status = BluetoothGatt.GATT_SUCCESS
        )
        dispatcher.flushMainQueue()
        assertNotNull(manager.connectedSession.value)
        assertTrue(manager.hasActiveRacpForTest())

        // Lectura en sesión activa -> se entrega correctamente
        manager.handleGattCharacteristicChanged(
            attempt = attempt1,
            handle = handle1,
            originGatt = null,
            serviceUuid = BleGlucometerManager.GLUCOSE_SERVICE_UUID,
            charUuid = BleGlucometerManager.GLUCOSE_MEASUREMENT_CHAR_UUID,
            bytes = buildStandardSigGlucosePacket(118)
        )
        dispatcher.flushMainQueue()
        assertEquals(1, receivedReadings.size)
        assertEquals(118, receivedReadings.first().first.valueMgDl)
        assertEquals("AA:BB:CC:DD:EE:01", receivedReadings.first().second.address)

        // 2. Encolar una segunda lectura, un nuevo onServicesDiscovered y una sincronización RACP,
        //    y llamar a disconnect() ANTES de que el hilo principal procese esos callbacks pendientes
        manager.requestStoredMemoryRecords()
        assertTrue(manager.isSyncingMemory.value)
        manager.handleGattCharacteristicChanged(
            attempt = attempt1,
            handle = handle1,
            originGatt = null,
            serviceUuid = BleGlucometerManager.GLUCOSE_SERVICE_UUID,
            charUuid = BleGlucometerManager.GLUCOSE_MEASUREMENT_CHAR_UUID,
            bytes = buildStandardSigGlucosePacket(210)
        )
        manager.handleGattServicesDiscovered(
            attempt = attempt1,
            handle = handle1,
            originGatt = null,
            status = BluetoothGatt.GATT_SUCCESS
        )

        manager.disconnect()
        assertNull(manager.connectedSession.value)
        assertFalse(manager.hasActiveRacpForTest())
        assertFalse(manager.isSyncingMemory.value)

        // Al vaciar las colas pendientes tras disconnect(), nada debe revivir la sesión ni entregar la lectura 210
        dispatcher.flushMainQueue()
        dispatcher.flushDelayedQueue()
        assertNull(manager.connectedSession.value)
        assertFalse(manager.hasActiveRacpForTest())
        assertEquals(1, receivedReadings.size)
        assertEquals("El glucómetro se desconectó correctamente.", manager.statusMessage.value)

        // 3. Un status != GATT_SUCCESS (p. ej. 133) con STATE_CONNECTED debe tratarse como fallo
        //    y NO iniciar discoverServices()
        val handleFailed = FakeGattClientHandle(
            servicesSnapshot = servicesStandard,
            supportsStandardGlucose = true,
            supportsRacp = true
        )
        val attemptFailed = manager.startAttemptWithHandleForTest(
            address = "AA:BB:CC:DD:EE:02",
            advertisedName = "GLM-76L",
            matchedSpec = BiomedicalCatalog.specForId(BiomedicalDeviceId.Glucocare_Match),
            handle = handleFailed
        )
        manager.handleGattConnectionStateChange(
            attempt = attemptFailed,
            handle = handleFailed,
            originGatt = null,
            status = 133,
            newState = BluetoothProfile.STATE_CONNECTED
        )
        dispatcher.flushMainQueue()

        assertEquals(0, handleFailed.discoverServicesCalls)
        assertEquals(1, handleFailed.closeCalls)
        assertNull(manager.connectedSession.value)
        assertNotNull(manager.errorMessage.value)
        assertTrue(manager.errorMessage.value!!.contains("133"))
    }

    @Test
    fun `bottom navigation bar keeps 5-column row when labels fit and adapts to 3+2 rows at 360dp and fontScale 2_0`() {
        val tabs = listOf(
            PatientTab.HOME to "Inicio",
            PatientTab.HISTORY to "Historial",
            PatientTab.DEVICES to "Medidor",
            PatientTab.EMERGENCY to "Ayuda",
            PatientTab.PROFILES to "Perfiles"
        )
        val baseLabelMediumSp = 15.5f

        // 1. En 360 dp con fontScale = 1.0, las 5 etiquetas caben en la barra estándar de 5 columnas iguales
        val normalWidthsAt360 = tabs.map { (tab, label) ->
            tab to (label to estimateSingleLineNavLabelWidthDp(label, baseLabelMediumSp, fontScale = 1.0f))
        }
        val normalPlan = planBottomNavigationLayout(
            availableWidthDp = 360f,
            labelTextWidthsDp = normalWidthsAt360
        )
        assertTrue(normalPlan.useSingleFiveColumnBar)
        assertEquals(1, normalPlan.rows.size)
        assertEquals(5, normalPlan.rows.first().size)
        normalPlan.rows.flatten().forEach { slot ->
            assertTrue(
                "La etiqueta ${slot.label} debe caber en su columna a escala 1.0",
                slot.allocatedSlotWidthDp >= slot.requiredSlotWidthDp
            )
            assertTrue(slot.minTouchHeightDp >= 48f)
        }

        // 2. En 360 dp con fontScale = 2.0 (200 %), «Historial» y «Perfiles» no caben en 72 dp (1/5 de 360 dp),
        //    por lo que la barra se adapta a 3 + 2 filas con cada etiqueta completa y altura táctil >= 48 dp
        val scale200WidthsAt360 = tabs.map { (tab, label) ->
            tab to (label to estimateSingleLineNavLabelWidthDp(label, baseLabelMediumSp, fontScale = 2.0f))
        }
        val historialWidthAt200 = scale200WidthsAt360.first { it.first == PatientTab.HISTORY }.second.second
        val perfilesWidthAt200 = scale200WidthsAt360.first { it.first == PatientTab.PROFILES }.second.second
        assertTrue("Historial a 200% ($historialWidthAt200 dp) excede los 60 dp útiles de 1/5 de 360 dp", historialWidthAt200 > 60f)
        assertTrue("Perfiles a 200% ($perfilesWidthAt200 dp) excede los 60 dp útiles de 1/5 de 360 dp", perfilesWidthAt200 > 60f)

        val planAt360FontScale2 = planBottomNavigationLayout(
            availableWidthDp = 360f,
            labelTextWidthsDp = scale200WidthsAt360
        )
        assertFalse(planAt360FontScale2.useSingleFiveColumnBar)
        assertEquals(2, planAt360FontScale2.rows.size)
        assertEquals(3, planAt360FontScale2.rows[0].size)
        assertEquals(2, planAt360FontScale2.rows[1].size)

        planAt360FontScale2.rows.flatten().forEach { slot ->
            assertTrue(
                "La etiqueta ${slot.label} debe recibir ancho completo sin recorte a 360dp y fontScale=2.0 (asignado=${slot.allocatedSlotWidthDp}, requerido=${slot.requiredSlotWidthDp})",
                slot.allocatedSlotWidthDp + 0.01f >= slot.requiredSlotWidthDp
            )
            assertTrue(
                "El control táctil de ${slot.label} debe tener al menos 48 dp de alto",
                slot.minTouchHeightDp >= 48f
            )
        }
    }

    @Test
    fun `customer service card in Home uses exact title, Colombian phone number, ACTION_DIAL tel_+573176562755 and legible fallback without dialer`() {
        assertEquals("Diagnósticos Biomédicos", com.glucosoft.app.ui.screens.CUSTOMER_SERVICE_COMPANY_NAME)
        assertEquals("Servicio al usuario", com.glucosoft.app.ui.screens.CUSTOMER_SERVICE_TITLE)
        assertEquals("317 656 2755", com.glucosoft.app.ui.screens.CUSTOMER_SERVICE_DISPLAY_NUMBER)
        assertTrue(com.glucosoft.app.ui.screens.CUSTOMER_SERVICE_ACCESSIBILITY_DESCRIPTION.contains("Diagnósticos Biomédicos"))
        assertTrue(com.glucosoft.app.ui.screens.CUSTOMER_SERVICE_ACCESSIBILITY_DESCRIPTION.contains("Servicio al usuario"))
        assertTrue(com.glucosoft.app.ui.screens.CUSTOMER_SERVICE_ACCESSIBILITY_DESCRIPTION.contains("317 656 2755"))
        assertEquals("tel:+573176562755", com.glucosoft.app.ui.screens.CUSTOMER_SERVICE_DIAL_URI)
        assertEquals(
            android.content.Intent.ACTION_DIAL,
            com.glucosoft.app.ui.screens.CUSTOMER_SERVICE_INTENT_ACTION
        )
        assertFalse(
            com.glucosoft.app.ui.screens.CUSTOMER_SERVICE_INTENT_ACTION == android.content.Intent.ACTION_CALL
        )

        // Verificar el orden de secciones desplazables en Inicio:
        // 1) Perfil CON contacto de emergencia y paneles opcionales visibles
        val orderWithContact = com.glucosoft.app.ui.screens.buildHomeScrollSectionOrder(
            hasEmergencyContact = true,
            showTrendSection = true,
            hasMultipleProfiles = true,
            showNoDialerFeedback = true
        )
        assertEquals(
            listOf(
                "home_hero_card",
                "home_my_week_card",
                "home_emergency_card",
                "home_view_history_button",
                "home_bubble_trend",
                "home_connect_ble_button",
                "home_export_pdf_button",
                "home_reminders_button",
                "open_display_settings_button",
                "home_trend_chart_section",
                "home_profile_switcher_card",
                "home_customer_service_button",
                "home_customer_service_no_dialer_card"
            ),
            orderWithContact
        )
        assertEquals(1, orderWithContact.count { it == "home_customer_service_button" })

        // 2) Perfil SIN contacto de emergencia y sin paneles opcionales abiertos
        val orderWithoutContact = com.glucosoft.app.ui.screens.buildHomeScrollSectionOrder(
            hasEmergencyContact = false,
            showTrendSection = false,
            hasMultipleProfiles = false,
            showNoDialerFeedback = false
        )
        assertEquals(
            listOf(
                "home_hero_card",
                "home_my_week_card",
                "home_setup_emergency_button",
                "home_view_history_button",
                "home_bubble_trend",
                "home_connect_ble_button",
                "home_export_pdf_button",
                "home_reminders_button",
                "open_display_settings_button",
                "home_customer_service_button"
            ),
            orderWithoutContact
        )
        assertEquals("home_customer_service_button", orderWithoutContact.last())

        var capturedAction: String? = null
        var capturedUri: String? = null
        val opened = com.glucosoft.app.ui.screens.tryOpenCustomerServiceDialer(
            context = null,
            intentFactory = { action, dialUri ->
                capturedAction = action
                capturedUri = dialUri
                android.content.Intent()
            },
            launcher = { /* marcador nativo disponible */ }
        )
        assertTrue(opened)
        assertEquals(android.content.Intent.ACTION_DIAL, capturedAction)
        assertEquals("tel:+57317656562755".replace("5656", "56"), capturedUri)

        // Si el dispositivo no dispone de marcador telefónico, devuelve false y muestra el mensaje legible con el número
        val failedWhenNoDialer = com.glucosoft.app.ui.screens.tryOpenCustomerServiceDialer(
            context = null,
            intentFactory = { _, _ -> android.content.Intent() },
            launcher = { throw android.content.ActivityNotFoundException("No dialer app") }
        )
        assertFalse(failedWhenNoDialer)
        assertTrue(com.glucosoft.app.ui.screens.CUSTOMER_SERVICE_NO_DIALER_MESSAGE.contains("317 656 2755"))
    }

    @Test
    fun `production GLM-76L live reception handles supplier 0x88 frame, fragmentation, noise recovery, burst deduplication and equal readings at different moments`() {
        var fakeClockMs = 1_764_070_000_000L
        val dispatcher = QueuedTestMainThreadDispatcher()
        val manager = BleGlucometerManager(
            context = null,
            mainThreadDispatcher = dispatcher,
            clockMs = { fakeClockMs }
        )
        val receivedReadings = mutableListOf<Pair<ParsedBleGlucose, ConnectedMeterSession>>()
        manager.onReadingReceived = { reading, session ->
            receivedReadings.add(reading to session)
        }

        val vendorServiceUuid = java.util.UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
        val vendorNotifyUuid = java.util.UUID.fromString("0000fff4-0000-1000-8000-00805f9b34fb")

        val servicesGlm76L = listOf(
            DiscoveredGattServiceInfo(
                uuid = vendorServiceUuid.toString().uppercase(),
                characteristics = listOf(
                    DiscoveredGattCharacteristicInfo(
                        uuid = vendorNotifyUuid.toString().uppercase(),
                        properties = listOf("notify")
                    )
                )
            )
        )
        val handleGlm76L = FakeGattClientHandle(
            servicesSnapshot = servicesGlm76L,
            supportsStandardGlucose = false,
            supportsRacp = false
        )

        val attempt = manager.startAttemptWithHandleForTest(
            address = "AA:BB:CC:76:76:01",
            advertisedName = "Glucocare Match (GLM-76L)",
            matchedSpec = BiomedicalCatalog.specForId(BiomedicalDeviceId.Glucocare_Match),
            handle = handleGlm76L
        )
        manager.handleGattConnectionStateChange(
            attempt = attempt,
            handle = handleGlm76L,
            originGatt = null,
            status = BluetoothGatt.GATT_SUCCESS,
            newState = BluetoothProfile.STATE_CONNECTED
        )

        // Descubrimiento GATT sin 0x1808 pero con característica notify del proveedor:
        // Glucocare_Match habilita recepción pasiva de trama 0x88 sin inventar UUIDs ni escribir comandos
        manager.handleGattServicesDiscovered(
            attempt = attempt,
            handle = handleGlm76L,
            originGatt = null,
            status = BluetoothGatt.GATT_SUCCESS
        )
        dispatcher.flushMainQueue()

        val sessionAfterDiscovery = manager.connectedSession.value
        assertNotNull(sessionAfterDiscovery)
        assertTrue(
            "GLM-76L con característica notify debe quedar listo para recibir tramas 0x88 en vivo",
            sessionAfterDiscovery!!.isReadyForMeasurements
        )
        assertFalse(sessionAfterDiscovery.supportsStandardGlucose)

        fun decodeHex(hex: String): ByteArray =
            hex.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.map { it.toInt(16).toByte() }.toByteArray()

        // 1. Trama válida 0x88 del ejemplo del proveedor (292 mg/dL) fragmentada en 3 paquetes BLE
        val fullFrame292 = decodeHex(
            "AA 55 0C 88 85 34 03 24 01 1C 01 00 10 01 96"
        )
        val chunk1 = fullFrame292.copyOfRange(0, 5)
        val chunk2 = fullFrame292.copyOfRange(5, 11)
        val chunk3 = fullFrame292.copyOfRange(11, 15)

        manager.handleGattCharacteristicChanged(
            attempt = attempt,
            handle = handleGlm76L,
            originGatt = null,
            serviceUuid = vendorServiceUuid,
            charUuid = vendorNotifyUuid,
            bytes = chunk1
        )
        dispatcher.flushMainQueue()
        assertEquals(0, receivedReadings.size)

        manager.handleGattCharacteristicChanged(
            attempt = attempt,
            handle = handleGlm76L,
            originGatt = null,
            serviceUuid = vendorServiceUuid,
            charUuid = vendorNotifyUuid,
            bytes = chunk2
        )
        dispatcher.flushMainQueue()
        assertEquals(0, receivedReadings.size)

        manager.handleGattCharacteristicChanged(
            attempt = attempt,
            handle = handleGlm76L,
            originGatt = null,
            serviceUuid = vendorServiceUuid,
            charUuid = vendorNotifyUuid,
            bytes = chunk3
        )
        dispatcher.flushMainQueue()

        assertEquals(1, receivedReadings.size)
        assertEquals(292, receivedReadings[0].first.valueMgDl)
        assertEquals(fakeClockMs, receivedReadings[0].first.timestampMs)
        assertEquals(BiomedicalDeviceId.Glucocare_Match, receivedReadings[0].second.matchedModelId)

        // 2. Reintento inmediato de transmisión BLE (1,2 segundos después con el mismo valor 292 mg/dL):
        //    se filtra como ráfaga duplicada
        fakeClockMs += 1_200L
        manager.handleGattCharacteristicChanged(
            attempt = attempt,
            handle = handleGlm76L,
            originGatt = null,
            serviceUuid = vendorServiceUuid,
            charUuid = vendorNotifyUuid,
            bytes = fullFrame292
        )
        dispatcher.flushMainQueue()
        assertEquals("Una ráfaga de reintento a 1,2 s no debe duplicar la lectura", 1, receivedReadings.size)

        // 3. Otra medición real con el mismo valor 292 mg/dL tomada en otro momento (10 segundos después):
        //    SÍ se registra con su nuevo timestamp de recepción
        fakeClockMs += 10_000L
        val secondMeasurementTime = fakeClockMs
        manager.handleGattCharacteristicChanged(
            attempt = attempt,
            handle = handleGlm76L,
            originGatt = null,
            serviceUuid = vendorServiceUuid,
            charUuid = vendorNotifyUuid,
            bytes = fullFrame292
        )
        dispatcher.flushMainQueue()
        assertEquals(
            "Una medición igual en otro momento (>= 4s después) no debe perderse",
            2,
            receivedReadings.size
        )
        assertEquals(292, receivedReadings[1].first.valueMgDl)
        assertEquals(secondMeasurementTime, receivedReadings[1].first.timestampMs)

        // 4. Bytes basura / trama corrupta seguida de una trama válida de 110 mg/dL (0x006E):
        //    CMD=0x88, ID=0x85, Data=[34, 03, 6E, 00, 1C, 01, 00, 10]
        //    Suma = 0x88+0x85+0x34+0x03+0x6E+0x00+0x1C+0x01+0x00+0x10 = 0x01DF -> Checksum = 01 DF
        fakeClockMs += 15_000L
        val corruptedNoiseAndValid110 = decodeHex(
            "FF 00 AA 55 0C 88 85 00 00 00 00 00 00 00 10 00 00 " +
                "AA 55 0C 88 85 34 03 6E 00 1C 01 00 10 01 DF"
        )
        manager.handleGattCharacteristicChanged(
            attempt = attempt,
            handle = handleGlm76L,
            originGatt = null,
            serviceUuid = vendorServiceUuid,
            charUuid = vendorNotifyUuid,
            bytes = corruptedNoiseAndValid110
        )
        dispatcher.flushMainQueue()
        assertEquals(3, receivedReadings.size)
        assertEquals(110, receivedReadings[2].first.valueMgDl)

        // 5. Tira reactiva que no es glucosa (0x20 ácido úrico) se rechaza sin agregar lectura
        fakeClockMs += 15_000L
        val uricAcidFrame = decodeHex(
            "AA 55 0C 88 85 34 03 24 01 1C 01 00 20 01 A6"
        )
        manager.handleGattCharacteristicChanged(
            attempt = attempt,
            handle = handleGlm76L,
            originGatt = null,
            serviceUuid = vendorServiceUuid,
            charUuid = vendorNotifyUuid,
            bytes = uricAcidFrame
        )
        dispatcher.flushMainQueue()
        assertEquals(3, receivedReadings.size)
    }
}


