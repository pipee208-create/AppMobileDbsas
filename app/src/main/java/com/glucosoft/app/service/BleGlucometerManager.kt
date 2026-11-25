package com.glucosoft.app.service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.glucosoft.app.model.BiomedicalCatalog
import com.glucosoft.app.model.BiomedicalDeviceId
import com.glucosoft.app.model.BiomedicalDeviceSpec
import com.glucosoft.app.protocol.Glm76lBleProtocolAdapter
import com.glucosoft.app.protocol.LiveFrameBurstDeduplicator
import com.glucosoft.app.protocol.YaseeGlm76LiveFrameReader
import com.glucosoft.app.protocol.YaseeLiveFrameStreamAssembler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar
import java.util.UUID
import kotlin.math.pow
import kotlin.math.roundToInt

data class DiscoveredBleDevice(
    val address: String,
    val advertisedName: String,
    val rssi: Int,
    val matchedSpec: BiomedicalDeviceSpec?,
    val rawDevice: BluetoothDevice
)

data class DiscoveredGattCharacteristicInfo(
    val uuid: String,
    val properties: List<String>
)

data class DiscoveredGattServiceInfo(
    val uuid: String,
    val characteristics: List<DiscoveredGattCharacteristicInfo>
)

data class ConnectedMeterSession(
    val deviceName: String,
    val address: String,
    val matchedModelId: BiomedicalDeviceId,
    val matchedModelName: String,
    val supportsStandardGlucose: Boolean,
    val supportsMemorySync: Boolean,
    val isReadyForMeasurements: Boolean = supportsStandardGlucose,
    val discoveredServiceCount: Int,
    val discoveredServiceUuids: List<String>,
    val discoveredServices: List<DiscoveredGattServiceInfo> = emptyList(),
    val connectionMessage: String
)

data class ParsedBleGlucose(
    val valueMgDl: Int,
    val timestampMs: Long
)

enum class BlePrimarySearchAction(
    val buttonLabel: String,
    val testTag: String,
    val enabled: Boolean
) {
    START_SEARCH(
        buttonLabel = "Buscar glucómetro cercano",
        testTag = "start_ble_scan_button",
        enabled = true
    ),
    SCANNING_IN_PROGRESS(
        buttonLabel = "Buscando glucómetros cercanos...",
        testTag = "start_ble_scan_button",
        enabled = false
    ),
    RETRY_AFTER_EMPTY_SCAN(
        buttonLabel = "Volver a buscar",
        testTag = "retry_ble_scan_button",
        enabled = true
    )
}

data class BleCaptureSearchUiState(
    val showPrimarySearchCard: Boolean,
    val showEmptyScanResultCard: Boolean,
    val emptyScanTitle: String?,
    val primarySearchAction: BlePrimarySearchAction,
    val errorBannerMessage: String?,
    val statusBannerMessage: String?,
    val showManualEntryFallback: Boolean = true
)

/**
 * Abstracción del despachador al hilo principal para vincular e invalidar
 * callbacks ya encolados tanto en tiempo real de Android como en pruebas unitarias.
 */
interface MainThreadDispatcher {
    fun post(block: () -> Unit)
    fun postDelayed(delayMs: Long, block: () -> Unit)
}

class AndroidMainThreadDispatcher : MainThreadDispatcher {
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    override fun post(block: () -> Unit) {
        mainHandler.post(block)
    }

    override fun postDelayed(delayMs: Long, block: () -> Unit) {
        mainHandler.postDelayed(block, delayMs)
    }
}

internal data class StandardGlucoseGattCapabilities(
    val supportsStandardGlucose: Boolean,
    val supportsRacp: Boolean,
    val racpCharacteristic: BluetoothGattCharacteristic? = null
)

internal interface GattClientHandle {
    val rawGatt: BluetoothGatt?
    fun discoverServices(): Boolean
    fun getDiscoveredServicesSnapshot(): List<DiscoveredGattServiceInfo>
    fun configureStandardGlucoseService(): StandardGlucoseGattCapabilities
    fun configureModelPassiveNotifications(modelId: BiomedicalDeviceId): Int = 0
    fun writeRacpReportAllRecords(racpCharacteristic: BluetoothGattCharacteristic?): Boolean
    fun disconnect()
    fun close()
}

internal class ConnectionAttemptContext(
    val attemptId: Long,
    val deviceAddress: String,
    val advertisedName: String,
    val matchedSpec: BiomedicalDeviceSpec?
) {
    @Volatile
    var terminated: Boolean = false

    val yaseeStreamAssembler = YaseeLiveFrameStreamAssembler()
    val liveBurstDeduplicator = LiveFrameBurstDeduplicator()
}

class BleGlucometerManager internal constructor(
    private val context: Context?,
    private val mainThreadDispatcher: MainThreadDispatcher,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {
    constructor(context: Context) : this(context, AndroidMainThreadDispatcher())

    private val bluetoothManager: BluetoothManager?
        get() = context?.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    @Volatile
    private var activeAttemptId: Long = 0L

    @Volatile
    private var currentAttempt: ConnectionAttemptContext? = null

    @Volatile
    private var activeGatt: BluetoothGatt? = null

    @Volatile
    private var activeGattHandle: GattClientHandle? = null

    @Volatile
    private var racpCharacteristic: BluetoothGattCharacteristic? = null

    @Volatile
    private var activeRacpSupported: Boolean = false

    @Volatile
    private var activeScanAttemptId: Long = 0L

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _hasCompletedRealScan = MutableStateFlow(false)
    val hasCompletedRealScan: StateFlow<Boolean> = _hasCompletedRealScan.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredBleDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredBleDevice>> = _discoveredDevices.asStateFlow()

    private val _connectedSession = MutableStateFlow<ConnectedMeterSession?>(null)
    val connectedSession: StateFlow<ConnectedMeterSession?> = _connectedSession.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isSyncingMemory = MutableStateFlow(false)
    val isSyncingMemory: StateFlow<Boolean> = _isSyncingMemory.asStateFlow()

    var onReadingReceived: ((ParsedBleGlucose, ConnectedMeterSession) -> Unit)? = null

    fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
    }

    fun hasBlePermissions(): Boolean {
        val ctx = context ?: return false
        return requiredPermissions().all { perm ->
            ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun clearMessages() {
        _statusMessage.value = null
        _errorMessage.value = null
    }

    /**
     * Restablece el estado inicial de búsqueda al abrir el diálogo de conexión Bluetooth
     * cuando no hay escaneo en curso ni sesión conectada.
     */
    fun resetSearchStateForDialogOpen() {
        if (!_isScanning.value && _connectedSession.value == null) {
            _hasCompletedRealScan.value = false
            _discoveredDevices.value = emptyList()
            _statusMessage.value = null
            _errorMessage.value = null
        }
    }

    /**
     * Verifica que el evento o tarea encolada para el hilo principal pertenezca
     * exactamente al intento de conexión activo y a su instancia GATT de origen.
     */
    @Synchronized
    private fun isAttemptActive(
        attempt: ConnectionAttemptContext?,
        handle: GattClientHandle?,
        originGatt: BluetoothGatt? = null
    ): Boolean {
        if (attempt == null || attempt.terminated) return false
        if (attempt.attemptId == 0L || attempt.attemptId != activeAttemptId) return false
        if (currentAttempt !== attempt) return false
        val currentHandle = activeGattHandle ?: return false
        if (handle == null || handle !== currentHandle) return false
        if (originGatt != null && originGatt !== activeGatt) return false
        if (currentHandle.rawGatt != null && originGatt != null && currentHandle.rawGatt !== originGatt) {
            return false
        }
        return true
    }

    /**
     * Invalida la conexión anterior ANTES de llamar a disconnect()/close() para que
     * cualquier callback síncrono o en vuelo disparado durante el cierre sea descartado.
     */
    @Synchronized
    private fun invalidatePreviousConnectionBeforeClose(): Pair<BluetoothGatt?, GattClientHandle?> {
        currentAttempt?.terminated = true
        currentAttempt = null
        activeAttemptId += 1L
        val previousGatt = activeGatt
        val previousHandle = activeGattHandle
        activeGatt = null
        activeGattHandle = null
        racpCharacteristic = null
        activeRacpSupported = false
        return previousGatt to previousHandle
    }

    @SuppressLint("MissingPermission")
    private fun closeInvalidatedConnection(
        previousGatt: BluetoothGatt?,
        previousHandle: GattClientHandle?
    ) {
        try {
            previousHandle?.disconnect()
            previousHandle?.close()
        } catch (_: Exception) {
        }
        if (previousHandle == null && previousGatt != null) {
            try {
                previousGatt.disconnect()
                previousGatt.close()
            } catch (_: Exception) {
            }
        }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            val res = result ?: return
            val dev = res.device ?: return
            val name = (dev.name ?: res.scanRecord?.deviceName ?: "").trim()
            val serviceUuids = res.scanRecord?.serviceUuids?.map { it.uuid } ?: emptyList()
            val advertisesGlucose = serviceUuids.contains(GLUCOSE_SERVICE_UUID)

            val matched = isScanAdvertisementEligible(
                advertisedName = name,
                advertisesStandardGlucoseService = advertisesGlucose
            ) ?: return

            val item = DiscoveredBleDevice(
                address = dev.address,
                advertisedName = name,
                rssi = res.rssi,
                matchedSpec = matched,
                rawDevice = dev
            )
            val current = _discoveredDevices.value.toMutableList()
            val idx = current.indexOfFirst { it.address == item.address }
            if (idx >= 0) {
                current[idx] = item
            } else {
                current.add(item)
            }
            _discoveredDevices.value = current
        }

        override fun onScanFailed(errorCode: Int) {
            _isScanning.value = false
            _hasCompletedRealScan.value = false
            _statusMessage.value = null
            _errorMessage.value =
                "No se pudo iniciar el escaneo Bluetooth (código $errorCode). Verifica que el Bluetooth esté encendido."
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        clearMessages()
        _hasCompletedRealScan.value = false
        if (!hasBlePermissions()) {
            _errorMessage.value =
                "Se requieren permisos de Bluetooth para buscar glucómetros cercanos."
            return
        }
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _errorMessage.value =
                "El Bluetooth de este dispositivo está apagado o no disponible. Enciéndelo para buscar tu glucómetro o anota la medición manualmente."
            return
        }
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            _errorMessage.value =
                "Este entorno no dispone de radio Bluetooth LE física activa. Puedes registrar tus mediciones manualmente."
            return
        }

        val scanId = ++activeScanAttemptId
        _discoveredDevices.value = emptyList()
        _isScanning.value = true
        _statusMessage.value = "Buscando glucómetros Bluetooth cercanos..."

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(null, settings, scanCallback)

        mainThreadDispatcher.postDelayed(10_000L) {
            if (_isScanning.value && activeScanAttemptId == scanId) {
                stopScan()
                _hasCompletedRealScan.value = true
                if (_discoveredDevices.value.isEmpty()) {
                    _statusMessage.value = null
                } else {
                    _statusMessage.value =
                        "Se encontraron ${_discoveredDevices.value.size} equipo(s). Toca uno para conectar."
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        activeScanAttemptId += 1L
        if (!_isScanning.value) return
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: Exception) {
        }
        _isScanning.value = false
    }

    private inner class AndroidGattClientHandle(
        override val rawGatt: BluetoothGatt
    ) : GattClientHandle {

        @SuppressLint("MissingPermission")
        override fun discoverServices(): Boolean = rawGatt.discoverServices()

        override fun getDiscoveredServicesSnapshot(): List<DiscoveredGattServiceInfo> {
            return buildDiscoveredServicesSnapshot(rawGatt.services ?: emptyList())
        }

        @SuppressLint("MissingPermission")
        override fun configureStandardGlucoseService(): StandardGlucoseGattCapabilities {
            var supportsStandard = false
            var supportsRacp = false
            var discoveredRacp: BluetoothGattCharacteristic? = null

            val glucoseService = rawGatt.getService(GLUCOSE_SERVICE_UUID)
            if (glucoseService != null) {
                val measChar = glucoseService.getCharacteristic(GLUCOSE_MEASUREMENT_CHAR_UUID)
                if (measChar != null) {
                    enableNotifications(rawGatt, measChar)
                    supportsStandard = true
                }
                val racp = glucoseService.getCharacteristic(RACP_CHAR_UUID)
                if (racp != null) {
                    discoveredRacp = racp
                    supportsRacp = true
                }
            }

            return StandardGlucoseGattCapabilities(
                supportsStandardGlucose = supportsStandard,
                supportsRacp = supportsRacp,
                racpCharacteristic = discoveredRacp
            )
        }

        @SuppressLint("MissingPermission")
        override fun configureModelPassiveNotifications(modelId: BiomedicalDeviceId): Int {
            if (!Glm76lBleProtocolAdapter.supportsModel(modelId)) return 0
            var subscribedCount = 0
            val services = rawGatt.services ?: emptyList()
            for (service in services) {
                val serviceUuidStr = service.uuid.toString().uppercase()
                val characteristics = service.characteristics ?: emptyList()
                for (characteristic in characteristics) {
                    val props = extractCharacteristicProperties(characteristic.properties)
                    if (Glm76lBleProtocolAdapter.isEligiblePassiveNotifyCharacteristic(serviceUuidStr, props)) {
                        enableNotifications(rawGatt, characteristic)
                        subscribedCount += 1
                    }
                }
            }
            return subscribedCount
        }

        @SuppressLint("MissingPermission")
        override fun writeRacpReportAllRecords(
            racpCharacteristic: BluetoothGattCharacteristic?
        ): Boolean {
            val racp = racpCharacteristic ?: return false
            racp.value = byteArrayOf(0x01, 0x01)
            return rawGatt.writeCharacteristic(racp)
        }

        @SuppressLint("MissingPermission")
        override fun disconnect() {
            rawGatt.disconnect()
        }

        @SuppressLint("MissingPermission")
        override fun close() {
            rawGatt.close()
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(discovered: DiscoveredBleDevice) {
        stopScan()
        clearMessages()

        // 1. Invalidar primero la conexión anterior y luego cerrarla
        val (previousGatt, previousHandle) = invalidatePreviousConnectionBeforeClose()
        _isSyncingMemory.value = false
        _connectedSession.value = null
        closeInvalidatedConnection(previousGatt, previousHandle)

        // 2. Validar que el equipo pertenezca a un modelo con protocolo Bluetooth confirmado en el catálogo
        val confirmedSpec = BiomedicalCatalog.resolveEligibleBluetoothConnectionSpec(
            discoveredName = discovered.advertisedName,
            matchedSpec = discovered.matchedSpec
        )
        if (confirmedSpec == null) {
            _statusMessage.value = null
            _errorMessage.value =
                "Este equipo no corresponde a un medidor con protocolo Bluetooth confirmado. Puedes volver a buscar o registrar tu medición a mano."
            return
        }

        _statusMessage.value = "Conectando con ${discovered.advertisedName}..."

        // 3. Registrar el nuevo intento de conexión
        val attempt = synchronized(this) {
            val newAttemptId = ++activeAttemptId
            ConnectionAttemptContext(
                attemptId = newAttemptId,
                deviceAddress = discovered.address,
                advertisedName = discovered.advertisedName,
                matchedSpec = confirmedSpec
            ).also {
                currentAttempt = it
            }
        }

        val callback = object : BluetoothGattCallback() {
            @Volatile
            private var boundGatt: BluetoothGatt? = null

            @Volatile
            private var boundHandle: AndroidGattClientHandle? = null

            @Synchronized
            fun resolveAttemptHandle(originGatt: BluetoothGatt): AndroidGattClientHandle? {
                synchronized(this@BleGlucometerManager) {
                    if (attempt.terminated || attempt.attemptId != activeAttemptId || currentAttempt !== attempt) {
                        return null
                    }
                    val existing = boundGatt
                    if (existing != null && existing !== originGatt) {
                        return null
                    }
                    var handle = boundHandle
                    if (handle == null) {
                        boundGatt = originGatt
                        handle = AndroidGattClientHandle(originGatt)
                        boundHandle = handle
                        activeGatt = originGatt
                        activeGattHandle = handle
                    }
                    return if (activeGatt === originGatt && activeGattHandle === handle) handle else null
                }
            }

            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                val handle = resolveAttemptHandle(gatt)
                if (handle == null) {
                    if (boundGatt === gatt || activeGatt !== gatt) {
                        try {
                            gatt.close()
                        } catch (_: Exception) {
                        }
                    }
                    return
                }
                handleGattConnectionStateChange(
                    attempt = attempt,
                    handle = handle,
                    originGatt = gatt,
                    status = status,
                    newState = newState
                )
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                val handle = resolveAttemptHandle(gatt) ?: return
                handleGattServicesDiscovered(
                    attempt = attempt,
                    handle = handle,
                    originGatt = gatt,
                    status = status
                )
            }

            @Deprecated("Deprecated in API 33")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                val handle = resolveAttemptHandle(gatt) ?: return
                val bytes = characteristic.value?.copyOf() ?: return
                val serviceUuid = characteristic.service?.uuid ?: return
                handleGattCharacteristicChanged(
                    attempt = attempt,
                    handle = handle,
                    originGatt = gatt,
                    serviceUuid = serviceUuid,
                    charUuid = characteristic.uuid,
                    bytes = bytes
                )
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                val handle = resolveAttemptHandle(gatt) ?: return
                val serviceUuid = characteristic.service?.uuid ?: return
                handleGattCharacteristicChanged(
                    attempt = attempt,
                    handle = handle,
                    originGatt = gatt,
                    serviceUuid = serviceUuid,
                    charUuid = characteristic.uuid,
                    bytes = value.copyOf()
                )
            }
        }

        val createdGatt = discovered.rawDevice.connectGatt(context, false, callback)
        if (createdGatt != null) {
            callback.resolveAttemptHandle(createdGatt)
        } else {
            synchronized(this) {
                if (currentAttempt === attempt && activeAttemptId == attempt.attemptId) {
                    attempt.terminated = true
                    currentAttempt = null
                    activeGatt = null
                    activeGattHandle = null
                }
            }
            _statusMessage.value = null
            _errorMessage.value =
                "No se pudo iniciar la conexión GATT con ${discovered.advertisedName}."
        }
    }

    /**
     * Punto de entrada interno para pruebas de regresión deterministas con conexiones GATT simuladas.
     */
    internal fun startAttemptWithHandleForTest(
        address: String,
        advertisedName: String,
        matchedSpec: BiomedicalDeviceSpec?,
        handle: GattClientHandle,
        catalog: List<BiomedicalDeviceSpec> = BiomedicalCatalog.BIOMEDICAL_DEVICES
    ): ConnectionAttemptContext {
        stopScan()
        clearMessages()

        val (previousGatt, previousHandle) = invalidatePreviousConnectionBeforeClose()
        _isSyncingMemory.value = false
        _connectedSession.value = null
        closeInvalidatedConnection(previousGatt, previousHandle)

        val confirmedSpec = BiomedicalCatalog.resolveEligibleBluetoothConnectionSpec(
            discoveredName = advertisedName,
            matchedSpec = matchedSpec,
            catalog = catalog
        )
        if (confirmedSpec == null) {
            _statusMessage.value = null
            _errorMessage.value =
                "Este equipo no corresponde a un medidor con protocolo Bluetooth confirmado. Puedes volver a buscar o registrar tu medición a mano."
            return synchronized(this) {
                val rejectedId = ++activeAttemptId
                ConnectionAttemptContext(
                    attemptId = rejectedId,
                    deviceAddress = address,
                    advertisedName = advertisedName,
                    matchedSpec = null
                ).apply {
                    terminated = true
                }
            }
        }

        _statusMessage.value = "Conectando con $advertisedName..."

        return synchronized(this) {
            val newAttemptId = ++activeAttemptId
            ConnectionAttemptContext(
                attemptId = newAttemptId,
                deviceAddress = address,
                advertisedName = advertisedName,
                matchedSpec = confirmedSpec
            ).also {
                currentAttempt = it
                activeGatt = handle.rawGatt
                activeGattHandle = handle
            }
        }
    }

    internal fun handleGattConnectionStateChange(
        attempt: ConnectionAttemptContext,
        handle: GattClientHandle,
        originGatt: BluetoothGatt?,
        status: Int,
        newState: Int
    ) {
        if (!isAttemptActive(attempt, handle, originGatt)) {
            return
        }

        // Cualquier status distinto de GATT_SUCCESS se trata como fallo, incluso ante STATE_CONNECTED
        if (status != BluetoothGatt.GATT_SUCCESS) {
            val attemptIdSnapshot = synchronized(this) {
                if (!isAttemptActive(attempt, handle, originGatt)) return
                attempt.terminated = true
                activeGatt = null
                activeGattHandle = null
                racpCharacteristic = null
                activeRacpSupported = false
                attempt.attemptId
            }
            closeInvalidatedConnection(originGatt, handle)

            mainThreadDispatcher.post {
                val shouldUpdateUi = synchronized(this) {
                    activeAttemptId == attemptIdSnapshot && currentAttempt === attempt
                }
                if (!shouldUpdateUi) return@post
                synchronized(this) {
                    if (activeAttemptId == attemptIdSnapshot && currentAttempt === attempt) {
                        currentAttempt = null
                        racpCharacteristic = null
                        activeRacpSupported = false
                    }
                }
                _isSyncingMemory.value = false
                _connectedSession.value = null
                _statusMessage.value = null
                _errorMessage.value =
                    "No se pudo completar la conexión con ${attempt.advertisedName} (estado $status)."
            }
            return
        }

        if (newState == BluetoothProfile.STATE_CONNECTED) {
            if (!isAttemptActive(attempt, handle, originGatt)) return

            mainThreadDispatcher.post {
                if (!isAttemptActive(attempt, handle, originGatt)) return@post
                _statusMessage.value =
                    "Conectado a ${attempt.advertisedName}. Descubriendo servicios GATT..."
            }

            if (!isAttemptActive(attempt, handle, originGatt)) return
            val discoveryStarted = handle.discoverServices()
            if (!discoveryStarted) {
                val attemptIdSnapshot = synchronized(this) {
                    if (!isAttemptActive(attempt, handle, originGatt)) return
                    attempt.terminated = true
                    activeGatt = null
                    activeGattHandle = null
                    racpCharacteristic = null
                    activeRacpSupported = false
                    attempt.attemptId
                }
                closeInvalidatedConnection(originGatt, handle)

                mainThreadDispatcher.post {
                    val shouldUpdateUi = synchronized(this) {
                        activeAttemptId == attemptIdSnapshot && currentAttempt === attempt
                    }
                    if (!shouldUpdateUi) return@post
                    synchronized(this) {
                        if (activeAttemptId == attemptIdSnapshot && currentAttempt === attempt) {
                            currentAttempt = null
                            racpCharacteristic = null
                            activeRacpSupported = false
                        }
                    }
                    _isSyncingMemory.value = false
                    _connectedSession.value = null
                    _statusMessage.value = null
                    _errorMessage.value =
                        "No se pudo iniciar el descubrimiento de servicios en ${attempt.advertisedName}."
                }
            }
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            val attemptIdSnapshot = synchronized(this) {
                if (!isAttemptActive(attempt, handle, originGatt)) return
                attempt.terminated = true
                activeGatt = null
                activeGattHandle = null
                racpCharacteristic = null
                activeRacpSupported = false
                attempt.attemptId
            }
            closeInvalidatedConnection(originGatt, handle)

            mainThreadDispatcher.post {
                val shouldUpdateUi = synchronized(this) {
                    activeAttemptId == attemptIdSnapshot && currentAttempt === attempt
                }
                if (!shouldUpdateUi) return@post
                synchronized(this) {
                    if (activeAttemptId == attemptIdSnapshot && currentAttempt === attempt) {
                        currentAttempt = null
                        racpCharacteristic = null
                        activeRacpSupported = false
                    }
                }
                _isSyncingMemory.value = false
                _connectedSession.value = null
                _statusMessage.value = "Se desconectó ${attempt.advertisedName}."
            }
        }
    }

    internal fun handleGattServicesDiscovered(
        attempt: ConnectionAttemptContext,
        handle: GattClientHandle,
        originGatt: BluetoothGatt?,
        status: Int
    ) {
        if (!isAttemptActive(attempt, handle, originGatt)) return

        if (status != BluetoothGatt.GATT_SUCCESS) {
            mainThreadDispatcher.post {
                if (!isAttemptActive(attempt, handle, originGatt)) return@post
                racpCharacteristic = null
                activeRacpSupported = false
                _isSyncingMemory.value = false
                _connectedSession.value = null
                _errorMessage.value =
                    "Falló el descubrimiento de servicios GATT (código $status). No se muestran servicios anteriores."
            }
            return
        }

        // Solo enumerar UUID de servicios, características y propiedades (sin leer valores de características)
        val discoveredServicesSnapshot = handle.getDiscoveredServicesSnapshot()
        if (!isAttemptActive(attempt, handle, originGatt)) return

        // Suscribir únicamente si el intento sigue activo
        val capabilities = handle.configureStandardGlucoseService()
        if (!isAttemptActive(attempt, handle, originGatt)) return

        val uuids = discoveredServicesSnapshot.map { it.uuid }
        val matched = attempt.matchedSpec
        val modelDisplay = matched?.displayName ?: attempt.advertisedName
        val modelId = matched?.id ?: BiomedicalDeviceId.Otro_Compatible

        val supportsStandard = capabilities.supportsStandardGlucose
        val supportsRacp = capabilities.supportsRacp
        val passiveNotifyCandidates = if (!supportsStandard && Glm76lBleProtocolAdapter.supportsModel(modelId)) {
            handle.configureModelPassiveNotifications(modelId)
            Glm76lBleProtocolAdapter.findPassiveNotifyCandidateCount(discoveredServicesSnapshot)
        } else {
            0
        }
        val supportsPassiveLive0x88 = passiveNotifyCandidates > 0
        val readyForMeasurements = supportsStandard || supportsPassiveLive0x88

        val message = when {
            supportsStandard && supportsRacp ->
                "Conectado · Listo para recibir mediciones en vivo y descargar memoria del equipo."
            supportsStandard ->
                "Conectado · Listo para recibir mediciones en vivo."
            supportsPassiveLive0x88 ->
                "Conectado · Listo para recibir mediciones en vivo del GLM-76L (trama 0x88). Memoria histórica pendiente de unidad física."
            else ->
                "Conectado (solo servicio propietario) · Falta confirmar el canal de medición."
        }

        val session = ConnectedMeterSession(
            deviceName = attempt.advertisedName,
            address = attempt.deviceAddress,
            matchedModelId = modelId,
            matchedModelName = modelDisplay,
            supportsStandardGlucose = supportsStandard,
            supportsMemorySync = supportsRacp,
            isReadyForMeasurements = readyForMeasurements,
            discoveredServiceCount = discoveredServicesSnapshot.size,
            discoveredServiceUuids = uuids,
            discoveredServices = discoveredServicesSnapshot,
            connectionMessage = message
        )

        mainThreadDispatcher.post {
            synchronized(this) {
                if (!isAttemptActive(attempt, handle, originGatt)) return@post
                racpCharacteristic = if (supportsRacp) capabilities.racpCharacteristic else null
                activeRacpSupported = supportsRacp
            }
            _connectedSession.value = session
            when {
                supportsStandard -> {
                    _errorMessage.value = null
                    _statusMessage.value =
                        "Conectado a $modelDisplay y listo para recibir mediciones."
                }
                supportsPassiveLive0x88 -> {
                    _errorMessage.value = null
                    _statusMessage.value =
                        "Conectado a $modelDisplay y listo para recibir resultados en vivo (0x88). Puedes abrir «Detalles Bluetooth» para consultar sus UUID."
                }
                else -> {
                    _statusMessage.value = null
                    _errorMessage.value =
                        "Equipo conectado, pero solo expone servicios propietarios. Falta confirmar el canal de medición; abre «Detalles Bluetooth» para consultar o copiar sus UUID y propiedades."
                }
            }
        }
    }

    internal fun handleGattCharacteristicChanged(
        attempt: ConnectionAttemptContext,
        handle: GattClientHandle,
        originGatt: BluetoothGatt?,
        serviceUuid: UUID,
        charUuid: UUID,
        bytes: ByteArray
    ) {
        if (!isAttemptActive(attempt, handle, originGatt)) return

        val parsedReadings: List<ParsedBleGlucose> = if (charUuid == GLUCOSE_MEASUREMENT_CHAR_UUID) {
            listOfNotNull(parseStandardGlucoseMeasurement(bytes))
        } else if (Glm76lBleProtocolAdapter.supportsModel(attempt.matchedSpec?.id)) {
            val channelKey = Glm76lBleProtocolAdapter.buildChannelKey(serviceUuid, charUuid)
            val verifiedFrames = attempt.yaseeStreamAssembler.onIncomingChunk(
                channelKey = channelKey,
                chunk = bytes
            )
            val nowMs = clockMs()
            verifiedFrames.mapNotNull { verified ->
                attempt.liveBurstDeduplicator.acceptOrFilterDuplicate(
                    valueMgDl = verified.valueMgDl,
                    receivedAtMs = nowMs
                )
            }
        } else {
            listOfNotNull(parseProprietaryGlucometerPacket(serviceUuid, charUuid, bytes, clockMs()))
        }

        if (parsedReadings.isEmpty()) return

        mainThreadDispatcher.post {
            if (!isAttemptActive(attempt, handle, originGatt)) return@post
            val activeSession = _connectedSession.value ?: return@post
            if (activeSession.address != attempt.deviceAddress) return@post
            for (parsed in parsedReadings) {
                onReadingReceived?.invoke(parsed, activeSession)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(characteristic, true)
        val cccd = characteristic.getDescriptor(CCCD_UUID)
        if (cccd != null) {
            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(cccd)
        }
    }

    @SuppressLint("MissingPermission")
    fun requestStoredMemoryRecords() {
        val attemptSnapshot: ConnectionAttemptContext?
        val handleSnapshot: GattClientHandle?
        val gattSnapshot: BluetoothGatt?
        val racpSnapshot: BluetoothGattCharacteristic?
        val racpSupportedSnapshot: Boolean

        synchronized(this) {
            attemptSnapshot = currentAttempt
            handleSnapshot = activeGattHandle
            gattSnapshot = activeGatt
            racpSnapshot = racpCharacteristic
            racpSupportedSnapshot = activeRacpSupported
        }

        val session = _connectedSession.value
        if (attemptSnapshot == null ||
            handleSnapshot == null ||
            !isAttemptActive(attemptSnapshot, handleSnapshot, gattSnapshot) ||
            !racpSupportedSnapshot ||
            session == null ||
            !session.supportsMemorySync ||
            session.address != attemptSnapshot.deviceAddress
        ) {
            _errorMessage.value =
                "Este glucómetro no confirmó soporte RACP estándar para descarga masiva de memoria."
            return
        }

        _errorMessage.value = null
        _isSyncingMemory.value = true
        _statusMessage.value = "Solicitando tomas guardadas en la memoria del glucómetro..."

        // Standard BLE Record Access Control Point: Report All Stored Records [0x01, 0x01]
        handleSnapshot.writeRacpReportAllRecords(racpSnapshot)

        mainThreadDispatcher.postDelayed(3000L) {
            if (!isAttemptActive(attemptSnapshot, handleSnapshot, gattSnapshot)) return@postDelayed
            _isSyncingMemory.value = false
            _statusMessage.value = "Sincronización de memoria completada."
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        val (previousGatt, previousHandle) = invalidatePreviousConnectionBeforeClose()
        _isSyncingMemory.value = false
        _connectedSession.value = null
        _statusMessage.value = "El glucómetro se desconectó correctamente."
        _errorMessage.value = null
        closeInvalidatedConnection(previousGatt, previousHandle)
    }

    internal fun hasActiveRacpForTest(): Boolean = synchronized(this) {
        activeRacpSupported && currentAttempt != null && activeGattHandle != null
    }

    /**
     * Inicia una ventana de búsqueda real simulada para pruebas unitarias deterministas
     * del ciclo inicial -> buscando -> vacío (con temporizador encolado) o error.
     */
    internal fun startSimulatedRealScanForTest(
        hasPermissions: Boolean = true,
        isBluetoothEnabled: Boolean = true
    ) {
        clearMessages()
        _hasCompletedRealScan.value = false
        if (!hasPermissions) {
            _errorMessage.value =
                "Se requieren permisos de Bluetooth para buscar glucómetros cercanos."
            return
        }
        if (!isBluetoothEnabled) {
            _errorMessage.value =
                "El Bluetooth de este dispositivo está apagado o no disponible. Enciéndelo para buscar tu glucómetro o anota la medición manualmente."
            return
        }

        val scanId = ++activeScanAttemptId
        _discoveredDevices.value = emptyList()
        _isScanning.value = true
        _statusMessage.value = "Buscando glucómetros Bluetooth cercanos..."

        mainThreadDispatcher.postDelayed(10_000L) {
            if (_isScanning.value && activeScanAttemptId == scanId) {
                _isScanning.value = false
                _hasCompletedRealScan.value = true
                if (_discoveredDevices.value.isEmpty()) {
                    _statusMessage.value = null
                } else {
                    _statusMessage.value =
                        "Se encontraron ${_discoveredDevices.value.size} equipo(s). Toca uno para conectar."
                }
            }
        }
    }

    internal fun currentSearchUiStateForTest(
        eligibleDevicesCount: Int = _discoveredDevices.value.count {
            BiomedicalCatalog.resolveEligibleBluetoothConnectionSpec(it.advertisedName, it.matchedSpec) != null
        }
    ): BleCaptureSearchUiState {
        return resolveBleCaptureSearchUiState(
            isScanning = _isScanning.value,
            hasCompletedRealScan = _hasCompletedRealScan.value,
            eligibleDevicesCount = eligibleDevicesCount,
            hasConnectedSession = _connectedSession.value != null,
            errorMessage = _errorMessage.value,
            statusMessage = _statusMessage.value
        )
    }

    companion object {
        val GLUCOSE_SERVICE_UUID: UUID = UUID.fromString("00001808-0000-1000-8000-00805f9b34fb")
        val GLUCOSE_MEASUREMENT_CHAR_UUID: UUID = UUID.fromString("00002a18-0000-1000-8000-00805f9b34fb")
        val RACP_CHAR_UUID: UUID = UUID.fromString("00002a52-0000-1000-8000-00805f9b34fb")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        /**
         * Resuelve de forma determinista el estado visual de búsqueda en BleCaptureDialog:
         * - Estado inicial (antes de buscar): muestra únicamente «Buscar glucómetro cercano» y el acceso manual;
         *   NO muestra «No encontramos un medidor compatible cerca».
         * - Estado de error (faltan permisos, Bluetooth apagado o fallo de inicio): muestra el motivo
         *   correspondiente, conserva «Buscar glucómetro cercano» como única acción principal de búsqueda
         *   y el acceso manual; NO muestra «No encontramos un medidor compatible cerca».
         * - Estado vacío tras búsqueda real terminada sin modelos admitidos: muestra
         *   «No encontramos un medidor compatible cerca» con «Volver a buscar» como única acción
         *   principal de búsqueda (ocultando la tarjeta superior para no duplicar botones) y el acceso manual.
         */
        fun resolveBleCaptureSearchUiState(
            isScanning: Boolean,
            hasCompletedRealScan: Boolean,
            eligibleDevicesCount: Int,
            hasConnectedSession: Boolean,
            errorMessage: String?,
            statusMessage: String?
        ): BleCaptureSearchUiState {
            val cleanError = errorMessage?.takeIf { it.isNotBlank() }
            val cleanStatus = statusMessage?.takeIf { it.isNotBlank() }

            val isEmptyAfterCompletedRealScan = !isScanning &&
                hasCompletedRealScan &&
                eligibleDevicesCount == 0 &&
                !hasConnectedSession &&
                cleanError == null

            val primaryAction = when {
                isScanning -> BlePrimarySearchAction.SCANNING_IN_PROGRESS
                isEmptyAfterCompletedRealScan -> BlePrimarySearchAction.RETRY_AFTER_EMPTY_SCAN
                else -> BlePrimarySearchAction.START_SEARCH
            }

            return BleCaptureSearchUiState(
                showPrimarySearchCard = !isEmptyAfterCompletedRealScan,
                showEmptyScanResultCard = isEmptyAfterCompletedRealScan,
                emptyScanTitle = if (isEmptyAfterCompletedRealScan) {
                    "No encontramos un medidor compatible cerca"
                } else {
                    null
                },
                primarySearchAction = primaryAction,
                errorBannerMessage = cleanError,
                statusBannerMessage = if (isEmptyAfterCompletedRealScan) null else cleanStatus,
                showManualEntryFallback = true
            )
        }

        /**
         * Evalúa si un anuncio Bluetooth de escaneo es elegible para mostrarse al paciente:
         * exige coincidencia inequívoca con un modelo que tenga protocolo Bluetooth confirmado
         * en el catálogo. Un nombre no vacío, auriculares/relojes, modelos pendientes, «Otro»
         * o anunciar el servicio 0x1808 sin modelo confirmado registrado no bastan.
         */
        @Suppress("UNUSED_PARAMETER")
        fun isScanAdvertisementEligible(
            advertisedName: String?,
            advertisesStandardGlucoseService: Boolean = false,
            catalog: List<BiomedicalDeviceSpec> = BiomedicalCatalog.BIOMEDICAL_DEVICES
        ): BiomedicalDeviceSpec? {
            return BiomedicalCatalog.matchConfirmedBluetoothModel(
                advertisedName = advertisedName,
                catalog = catalog
            )
        }

        /**
         * Extrae únicamente las 5 propiedades de protocolo solicitadas:
         * read, write, write sin respuesta, notify e indicate.
         */
        fun extractCharacteristicProperties(propertyBitmask: Int): List<String> {
            val props = mutableListOf<String>()
            if ((propertyBitmask and BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                props.add("read")
            }
            if ((propertyBitmask and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) {
                props.add("write")
            }
            if ((propertyBitmask and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                props.add("write sin respuesta")
            }
            if ((propertyBitmask and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                props.add("notify")
            }
            if ((propertyBitmask and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
                props.add("indicate")
            }
            return props
        }

        /**
         * Construye la lista agrupada de servicios GATT y sus características con propiedades,
         * sin leer ningún valor de característica.
         */
        fun buildDiscoveredServicesSnapshot(
            services: List<BluetoothGattService>
        ): List<DiscoveredGattServiceInfo> {
            return services.map { service ->
                val chars = (service.characteristics ?: emptyList()).map { characteristic ->
                    DiscoveredGattCharacteristicInfo(
                        uuid = characteristic.uuid.toString().uppercase(),
                        properties = extractCharacteristicProperties(characteristic.properties)
                    )
                }
                DiscoveredGattServiceInfo(
                    uuid = service.uuid.toString().uppercase(),
                    characteristics = chars
                )
            }
        }

        /**
         * Genera una salida legible para el portapapeles con ÚNICAMENTE los identificadores
         * de protocolo (UUID de servicios y características) y sus propiedades:
         * sin MAC, sin nombre personal del teléfono o periférico, sin serial, sin historial ni valores de salud.
         */
        fun formatBluetoothProtocolDetailsForCopy(
            services: List<DiscoveredGattServiceInfo>
        ): String {
            if (services.isEmpty()) {
                return "Sin servicios GATT descubiertos."
            }
            return buildString {
                services.forEachIndexed { index, service ->
                    if (index > 0) append("\n\n")
                    append("Servicio ${index + 1}: ${service.uuid}")
                    if (service.characteristics.isEmpty()) {
                        append("\n  Característica: (ninguna)")
                    } else {
                        service.characteristics.forEach { characteristic ->
                            val propsLabel = if (characteristic.properties.isEmpty()) {
                                "ninguna (read/write/write sin respuesta/notify/indicate)"
                            } else {
                                characteristic.properties.joinToString(", ")
                            }
                            append("\n  Característica: ${characteristic.uuid} | Propiedades: $propsLabel")
                        }
                    }
                }
            }
        }

        /**
         * Decodifica lectura de glucosa según formato Bluetooth SIG estándar (0x2A18).
         */
        fun parseStandardGlucoseMeasurement(bytes: ByteArray): ParsedBleGlucose? {
            if (bytes.size < 12) return null
            val flags = bytes[0].toInt() and 0xFF
            val hasTimeOffset = (flags and 0x01) != 0
            val hasConcentration = (flags and 0x02) != 0
            if (!hasConcentration) return null

            var timestampMs = System.currentTimeMillis()
            if (bytes.size >= 10) {
                val year = ((bytes[4].toInt() and 0xFF) shl 8) or (bytes[3].toInt() and 0xFF)
                val month = bytes[5].toInt() and 0xFF
                val day = bytes[6].toInt() and 0xFF
                val hour = bytes[7].toInt() and 0xFF
                val minute = bytes[8].toInt() and 0xFF
                val second = bytes[9].toInt() and 0xFF

                if (year >= 2000 && month in 1..12 && day in 1..31) {
                    var offsetMinutes = 0
                    if (hasTimeOffset && bytes.size >= 12) {
                        val rawOff = ((bytes[11].toInt() and 0xFF) shl 8) or (bytes[10].toInt() and 0xFF)
                        offsetMinutes = if (rawOff >= 0x8000) rawOff - 0x10000 else rawOff
                    }
                    val cal = Calendar.getInstance().apply {
                        set(year, month - 1, day, hour, minute, second)
                        set(Calendar.MILLISECOND, 0)
                    }
                    timestampMs = cal.timeInMillis + offsetMinutes * 60_000L
                }
            }

            val concOffset = if (hasTimeOffset) 12 else 10
            if (bytes.size < concOffset + 2) return null

            val raw = ((bytes[concOffset + 1].toInt() and 0xFF) shl 8) or (bytes[concOffset].toInt() and 0xFF)
            val mantissaRaw = raw and 0x0FFF
            val mantissa = if (mantissaRaw >= 0x0800) mantissaRaw - 0x1000 else mantissaRaw
            val expRaw = (raw shr 12) and 0x0F
            val exponent = if (expRaw >= 0x08) expRaw - 0x10 else expRaw

            val value = mantissa * 10.0.pow(exponent.toDouble())
            val isMolPerL = (flags and 0x04) != 0
            val mgDl = if (isMolPerL) value * 18018.2 else value * 100000.0
            if (mgDl.isNaN() || mgDl < 20.0 || mgDl > 650.0) return null

            return ParsedBleGlucose(
                valueMgDl = mgDl.roundToInt(),
                timestampMs = timestampMs
            )
        }

        /**
         * Decodifica una trama completa del protocolo propietario YASEE GLM-76L (resultado en vivo 0x88),
         * asignando el instante de recepción del teléfono.
         */
        @Suppress("UNUSED_PARAMETER")
        fun parseProprietaryGlucometerPacket(
            serviceUuid: UUID,
            charUuid: UUID,
            payload: ByteArray,
            receivedAtMs: Long = System.currentTimeMillis()
        ): ParsedBleGlucose? {
            val verifiedMgDl = YaseeGlm76LiveFrameReader.parseVerifiedMgDlOrNull(payload) ?: return null
            return ParsedBleGlucose(
                valueMgDl = verifiedMgDl,
                timestampMs = receivedAtMs
            )
        }
    }
}
