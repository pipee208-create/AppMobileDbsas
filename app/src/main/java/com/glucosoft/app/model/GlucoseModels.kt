package com.glucosoft.app.model

enum class GlucoseUnit(val symbol: String) {
    MG_DL("mg/dL"),
    MMOL_L("mmol/L");

    companion object {
        fun fromSymbol(symbol: String?): GlucoseUnit =
            if (symbol == "mmol/L") MMOL_L else MG_DL
    }
}

enum class BiomedicalDeviceId(val rawId: String) {
    OKMeter_Match_II("OKMeter_Match_II"),
    GlucoLeader_Enhance("GlucoLeader_Enhance"),
    Glucocare_D("Glucocare_D"),
    Glucomax("Glucomax"),
    Glucocare_Match("Glucocare_Match"),
    Otro_Compatible("Otro_Compatible");

    companion object {
        fun fromRaw(raw: String?): BiomedicalDeviceId =
            entries.find { it.rawId == raw } ?: Otro_Compatible
    }
}

enum class HardwareVerificationStatus {
    NEEDS_PHYSICAL_VALIDATION,
    STANDARD_BLE_CANDIDATE,
    NON_BLE_BASE_VARIANT_POSSIBLE
}

/**
 * Especificación de cada glucómetro del catálogo.
 * - `hasConfirmedProtocol` y `confirmedBleIdentifiers` constituyen la única fuente de verdad
 *   para las superficies Bluetooth de cara al paciente (por defecto pendiente: `false` y lista vacía).
 * - Los modelos con protocolo pendiente conservan su ficha e identificador interno para
 *   el historial, la exportación PDF y el registro manual.
 */
data class BiomedicalDeviceSpec(
    val id: BiomedicalDeviceId,
    val displayName: String,
    val advertisedNames: List<String>,
    val confirmedReadTimeSeconds: Int?,
    val supportsMultiSample: Boolean,
    val memoryCapacityLabel: String,
    val hasConfirmedBluetooth: Boolean,
    val verificationStatus: HardwareVerificationStatus,
    val protocolNotes: String,
    val summary: String,
    val hasConfirmedProtocol: Boolean = false,
    val confirmedBleIdentifiers: List<String> = emptyList(),
    val pendingTestBadgeLabel: String = "Prueba con el medidor pendiente"
) {
    val isEligibleForPatientBluetooth: Boolean
        get() = hasConfirmedProtocol &&
            id != BiomedicalDeviceId.Otro_Compatible &&
            confirmedBleIdentifiers.any { it.isNotBlank() }
}

enum class MeasurementContext(
    val rawId: String,
    val label: String,
    val shortLabel: String,
    val description: String
) {
    FASTING("fasting", "En ayunas", "En ayunas", "Al despertar, antes de desayunar"),
    PRE_MEAL("pre_meal", "Antes de comer", "Antes de comer", "Justo antes del almuerzo o la cena"),
    POST_MEAL_1H("post_meal_1h", "Una hora después de comer", "1h después", "Una hora después de terminar tu comida"),
    POST_MEAL_2H("post_meal_2h", "Dos horas después de comer", "2h después", "Control habitual dos horas después de comer"),
    BEDTIME("bedtime", "Antes de dormir", "Al dormir", "Para revisar cómo estás antes de descansar"),
    SYMPTOMS("symptoms", "Después de presentar síntomas", "Con síntomas", "Mareo, temblor, sudoración o cansancio");

    companion object {
        fun fromRaw(raw: String?): MeasurementContext =
            entries.find { it.rawId == raw } ?: FASTING
    }
}

enum class GlucoseSeverity {
    NORMAL,
    CAUTION,
    ALERT
}

data class SeverityEvaluation(
    val severity: GlucoseSeverity,
    val label: String,
    val detail: String
)

enum class CaptureMethod(val rawId: String, val displayLabel: String) {
    MANUAL("manual", "Registro manual"),
    BLUETOOTH("bluetooth", "Bluetooth");

    companion object {
        fun fromRaw(raw: String?): CaptureMethod =
            if (raw == "bluetooth") BLUETOOTH else MANUAL
    }
}

data class EmergencyContact(
    val name: String,
    val relation: String,
    val phone: String
)

data class LocalProfile(
    val id: String,
    val name: String,
    val unitPreference: GlucoseUnit,
    val targetMinMgDl: Int = 70,
    val targetMaxMgDl: Int = 140,
    val accentHex: String = "#0F766E",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long? = null,
    val emergencyContact: EmergencyContact? = null
)

data class GlucoseReading(
    val id: String,
    val profileId: String,
    val valueMgDl: Int,
    val originalUnit: GlucoseUnit = GlucoseUnit.MG_DL,
    val timestamp: Long,
    val context: MeasurementContext,
    val quickTags: List<String> = emptyList(),
    val note: String = "",
    val captureMethod: CaptureMethod = CaptureMethod.MANUAL,
    val deviceModel: String,
    val deviceId: BiomedicalDeviceId = BiomedicalDeviceId.OKMeter_Match_II,
    val deviceInfoSummary: String? = null,
    val photoPath: String? = null
)

/**
 * Configuración opcional de recordatorio diario local por perfil.
 * Desactivado por defecto (`enabled = false`).
 */
data class ProfileReminderConfig(
    val profileId: String,
    val enabled: Boolean = false,
    val hour: Int = 8,
    val minute: Int = 0,
    val snoozedUntilMs: Long? = null
) {
    val formattedTime: String
        get() = String.format(java.util.Locale.US, "%02d:%02d", hour.coerceIn(0, 23), minute.coerceIn(0, 59))
}

/**
 * Borrador auténtico de registro manual por perfil.
 * Solo se considera borrador recuperable cuando la persona escribió algún dato sin llegar a guardar.
 */
data class ManualEntryDraft(
    val profileId: String,
    val valueInput: String = "",
    val unit: GlucoseUnit,
    val context: MeasurementContext = MeasurementContext.FASTING,
    val deviceId: BiomedicalDeviceId = BiomedicalDeviceId.OKMeter_Match_II,
    val customModelName: String = "",
    val quickTags: List<String> = emptyList(),
    val note: String = "",
    val dateTimeInput: String = "",
    val dateModifiedByUser: Boolean = false,
    val photoPath: String? = null
) {
    val hasAuthenticContent: Boolean
        get() = valueInput.isNotBlank() ||
            note.isNotBlank() ||
            quickTags.isNotEmpty() ||
            customModelName.isNotBlank() ||
            dateModifiedByUser ||
            !photoPath.isNullOrBlank()
}

enum class PatientTab(val label: String) {
    HOME("Inicio"),
    HISTORY("Historial"),
    DEVICES("Medidor"),
    EMERGENCY("Ayuda"),
    PROFILES("Perfiles")
}

enum class TextScaleMode {
    NORMAL,
    LARGE
}

enum class DateRangeFilter(val shortLabel: String, val fullLabel: String, val days: Int?) {
    DAYS_7("7 días", "Últimos 7 días", 7),
    DAYS_14("14 días", "Últimos 14 días", 14),
    DAYS_30("30 días", "Últimos 30 días", 30),
    ALL("Todo", "Historial completo", null)
}

object BiomedicalCatalog {
    const val MAX_LOCAL_PROFILES = 3

    val PROFILE_COLORS = listOf("#0F766E", "#0284C7", "#4F46E5")

    val QUICK_TAGS = listOf(
        "Comí postre",
        "Hice ejercicio",
        "Tomé medicina",
        "Día estresante",
        "Tomé agua",
        "Comida ligera"
    )

    val BIOMEDICAL_DEVICES = listOf(
        BiomedicalDeviceSpec(
            id = BiomedicalDeviceId.OKMeter_Match_II,
            displayName = "OKMeter Match II",
            advertisedNames = listOf("Match II", "OKMeter", "OK-Meter", "OK Pro"),
            confirmedReadTimeSeconds = 5,
            supportsMultiSample = false,
            memoryCapacityLabel = "Hasta 1,000 memorias internas",
            hasConfirmedBluetooth = false,
            verificationStatus = HardwareVerificationStatus.NON_BLE_BASE_VARIANT_POSSIBLE,
            protocolNotes = "La versión estándar de OKMeter Match II opera de forma local sin Bluetooth integrado; variantes Bluetooth de la línea OKMeter deben validarse con el equipo físico para confirmar si implementan el perfil estándar de glucosa o un servicio propietario.",
            summary = "Medidor rápido de 5 segundos · Requiere validación física si tu lote incluye Bluetooth"
        ),
        BiomedicalDeviceSpec(
            id = BiomedicalDeviceId.GlucoLeader_Enhance,
            displayName = "GlucoLeader Enhance",
            advertisedNames = listOf("GlucoLeader", "Glucoleader", "Enhance", "HMD"),
            confirmedReadTimeSeconds = 5,
            supportsMultiSample = true,
            memoryCapacityLabel = "Hasta 800 memorias internas",
            hasConfirmedBluetooth = true,
            verificationStatus = HardwareVerificationStatus.NEEDS_PHYSICAL_VALIDATION,
            protocolNotes = "Cuenta con variantes Bluetooth para sincronización móvil (fabricante HMD Biomedical / Diagnósticos Biomédicos). Se debe validar físicamente si expone el perfil estándar de glucosa o protocolo serial propietario.",
            summary = "Permite segunda confirmación · Versión Bluetooth pendiente de validación física"
        ),
        BiomedicalDeviceSpec(
            id = BiomedicalDeviceId.Glucocare_D,
            displayName = "Glucocare D",
            advertisedNames = listOf("Glucocare D", "DB Life"),
            confirmedReadTimeSeconds = 5,
            supportsMultiSample = false,
            memoryCapacityLabel = "180 a 1,000 memorias según lote",
            hasConfirmedBluetooth = true,
            verificationStatus = HardwareVerificationStatus.NEEDS_PHYSICAL_VALIDATION,
            protocolNotes = "Modelos recientes de la serie Glucocare incluyen Bluetooth; otros lotes funcionan únicamente con memoria local. Requiere prueba física para confirmar características de transmisión y lectura de memoria.",
            summary = "Lectura rápida de 5 segundos · Pendiente de validación física según lote"
        ),
        BiomedicalDeviceSpec(
            id = BiomedicalDeviceId.Glucomax,
            displayName = "Glucomax",
            advertisedNames = listOf("Glucomax", "GlucoMax"),
            confirmedReadTimeSeconds = 8,
            supportsMultiSample = false,
            memoryCapacityLabel = "Hasta 450 memorias internas",
            hasConfirmedBluetooth = false,
            verificationStatus = HardwareVerificationStatus.NEEDS_PHYSICAL_VALIDATION,
            protocolNotes = "Equipo electroquímico de 8 segundos. Debe verificarse con la unidad física si posee radio Bluetooth activa o si sus resultados se registran manualmente en la aplicación.",
            summary = "Lectura en 8 segundos · Requiere confirmar disponibilidad Bluetooth en unidad física"
        ),
        BiomedicalDeviceSpec(
            id = BiomedicalDeviceId.Glucocare_Match,
            displayName = "Glucocare Match (GLM-76L)",
            advertisedNames = listOf(
                "Glucocare Match",
                "GLM-76L",
                "GLM76L",
                "Glucocare Match GLM-76L",
                "Glucocare Match (GLM-76L)"
            ),
            confirmedReadTimeSeconds = 8,
            supportsMultiSample = false,
            memoryCapacityLabel = "Memoria interna según versión (GLM-76L)",
            hasConfirmedBluetooth = true,
            verificationStatus = HardwareVerificationStatus.STANDARD_BLE_CANDIDATE,
            protocolNotes = "Protocolo Bluetooth confirmado por el proveedor para GLM-76L (trama de resultado en vivo 0x88 con identificador 0x85 en mg/dL). Sigue pendiente la prueba de conexión con una unidad física para confirmar los canales UUID de servicio/notificación y verificar la descarga de memoria. Si un equipo expone el servicio estándar 0x1808, también se conserva su soporte.",
            summary = "Protocolo Bluetooth confirmado por el proveedor para GLM-76L · Prueba con el medidor pendiente",
            hasConfirmedProtocol = true,
            confirmedBleIdentifiers = listOf(
                "Glucocare Match",
                "GLM-76L",
                "GLM76L",
                "Glucocare Match GLM-76L",
                "Glucocare Match (GLM-76L)"
            ),
            pendingTestBadgeLabel = "Prueba con el medidor pendiente"
        )
    )

    val OTHER_DEVICE_SPEC = BiomedicalDeviceSpec(
        id = BiomedicalDeviceId.Otro_Compatible,
        displayName = "Otro glucómetro compatible",
        advertisedNames = emptyList(),
        confirmedReadTimeSeconds = null,
        supportsMultiSample = false,
        memoryCapacityLabel = "Variable según fabricante",
        hasConfirmedBluetooth = true,
        verificationStatus = HardwareVerificationStatus.STANDARD_BLE_CANDIDATE,
        protocolNotes = "Dispositivo con servicio estándar Bluetooth de glucosa (0x1808) o equipo ingresado manualmente.",
        summary = "Equipo estándar o registro manual personalizado",
        hasConfirmedProtocol = false,
        confirmedBleIdentifiers = emptyList()
    )

    fun specForId(id: BiomedicalDeviceId): BiomedicalDeviceSpec =
        BIOMEDICAL_DEVICES.find { it.id == id } ?: OTHER_DEVICE_SPEC

    /**
     * Única fuente de verdad para superficies Bluetooth de cara al paciente:
     * devuelve exclusivamente los modelos del catálogo cuyo protocolo ha sido incorporado y confirmado
     * (`hasConfirmedProtocol == true` y con `confirmedBleIdentifiers` definidos).
     */
    fun confirmedBluetoothModels(
        catalog: List<BiomedicalDeviceSpec> = BIOMEDICAL_DEVICES
    ): List<BiomedicalDeviceSpec> {
        return catalog.filter { it.isEligibleForPatientBluetooth }
    }

    private fun normalizeWhitespace(value: String): String =
        value.trim().replace(Regex("\\s+"), " ")

    private fun normalizeBluetoothName(value: String): String =
        normalizeWhitespace(value).lowercase()

    /**
     * Conjunto de nombres completos e identificadores Bluetooth registrados en la ficha de un modelo
     * para comparación de nombre anunciado completo normalizado (mayúsculas/minúsculas y espacios).
     */
    private fun registeredConfirmedBluetoothNames(spec: BiomedicalDeviceSpec): Set<String> {
        return (spec.confirmedBleIdentifiers + spec.displayName)
            .map { normalizeBluetoothName(it) }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    /**
     * Reconoce únicamente un modelo con protocolo confirmado (`isEligibleForPatientBluetooth == true`)
     * comparando el nombre anunciado completo normalizado (mayúsculas/minúsculas y espacios) contra
     * los identificadores registrados del modelo (`confirmedBleIdentifiers`) y su nombre completo (`displayName`).
     *
     * - No acepta coincidencias parciales por prefijos/sufijos ni palabras dentro de otro texto
     *   (como «Auriculares GLM-76L» o «Reloj Glucocare Match»).
     * - No contiene lógica dedicada a un modelo concreto: cualquier modelo futuro habilitado en el
     *   catálogo se reconoce automáticamente por su propia ficha.
     * - Exige coincidencia inequívoca de un único modelo: si dos modelos registran el mismo alias
     *   exacto, se rechaza por ambiguo (`null`).
     */
    fun matchConfirmedBluetoothModel(
        advertisedName: String?,
        catalog: List<BiomedicalDeviceSpec> = BIOMEDICAL_DEVICES
    ): BiomedicalDeviceSpec? {
        if (advertisedName.isNullOrBlank()) return null
        val normalized = normalizeBluetoothName(advertisedName)
        if (normalized.isEmpty()) return null

        val confirmedModels = confirmedBluetoothModels(catalog)
        if (confirmedModels.isEmpty()) return null

        val matchingConfirmedModels = confirmedModels.filter { spec ->
            normalized in registeredConfirmedBluetoothNames(spec)
        }
        // Solo aceptar si coincide con exactamente un modelo confirmado
        if (matchingConfirmedModels.size != 1) {
            return null
        }

        val matchedSpec = matchingConfirmedModels.first()

        // Verificar que ningún otro modelo distinto del catálogo tenga registrado exactamente el mismo alias
        val conflictsWithAnotherCatalogModel = catalog.any { other ->
            other.id != matchedSpec.id &&
                normalized in (other.confirmedBleIdentifiers + other.advertisedNames + other.displayName)
                    .map { normalizeBluetoothName(it) }
                    .filter { it.isNotEmpty() }
                    .toSet()
        }
        if (conflictsWithAnotherCatalogModel) {
            return null
        }

        return matchedSpec
    }

    /**
     * Valida si un equipo descubierto (o proveniente de datos antiguos/mostrados previamente)
     * es elegible para mostrarse en el buscador y para iniciar conexión Bluetooth.
     */
    fun resolveEligibleBluetoothConnectionSpec(
        discoveredName: String?,
        matchedSpec: BiomedicalDeviceSpec? = null,
        catalog: List<BiomedicalDeviceSpec> = BIOMEDICAL_DEVICES
    ): BiomedicalDeviceSpec? {
        val byAdvertisedName = matchConfirmedBluetoothModel(discoveredName, catalog) ?: return null
        if (matchedSpec != null && matchedSpec.id != byAdvertisedName.id) {
            return null
        }
        return byAdvertisedName
    }

    /**
     * Verifica si `alias` aparece en `text` delimitado por bordes de palabra/código
     * para búsquedas internas/históricas separadas del filtro Bluetooth de paciente.
     */
    private fun containsUnambiguousIdentifier(text: String, alias: String): Boolean {
        val target = normalizeBluetoothName(alias)
        if (target.isEmpty()) return false
        var startIndex = text.indexOf(target)
        while (startIndex >= 0) {
            val endIndex = startIndex + target.length
            val validStart = startIndex == 0 ||
                (!text[startIndex - 1].isLetterOrDigit() && text[startIndex - 1] != '-')
            val validEnd = endIndex == text.length ||
                (!text[endIndex].isLetterOrDigit() && text[endIndex] != '-')
            if (validStart && validEnd) {
                return true
            }
            startIndex = text.indexOf(target, startIndex + 1)
        }
        return false
    }

    /**
     * Reconoce cualquier modelo del catálogo (confirmado o pendiente) para uso interno,
     * historial, exportación y registro manual, separado del filtro estricto de escaneo Bluetooth.
     */
    fun matchKnownModel(
        advertisedName: String?,
        catalog: List<BiomedicalDeviceSpec> = BIOMEDICAL_DEVICES
    ): BiomedicalDeviceSpec? {
        if (advertisedName.isNullOrBlank()) return null
        val normalized = normalizeBluetoothName(advertisedName)
        if (normalized.isEmpty()) return null

        // 1. Primero verificar si coincide exactamente con un modelo de protocolo confirmado
        matchConfirmedBluetoothModel(advertisedName, catalog)?.let { return it }

        // 2. Si contiene un código GLM-76 sin L u otro sufijo no confirmado, no asignarlo a Glucocare Match
        val tokens = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")
            .findAll(normalized)
            .map { it.value }
            .toList()
        if (tokens.any { it.startsWith("glm-76") || it.startsWith("glm76") }) {
            val glm76lConfirmed = catalog
                .filter { it.isEligibleForPatientBluetooth }
                .flatMap { registeredConfirmedBluetoothNames(it) }
                .toSet()
            if (tokens.any { (it.startsWith("glm-76") || it.startsWith("glm76")) && it !in glm76lConfirmed }) {
                return null
            }
        }

        // 3. Evaluar modelos del catálogo por especificidad con límites de palabra para historial/uso interno
        val candidates = catalog
            .flatMap { spec ->
                (listOf(spec.displayName) + spec.advertisedNames + spec.confirmedBleIdentifiers)
                    .map { normalizeBluetoothName(it) }
                    .filter { it.isNotEmpty() }
                    .map { alias -> spec to alias }
            }
            .sortedByDescending { (_, alias) -> alias.length }

        return candidates.firstOrNull { (_, alias) ->
            containsUnambiguousIdentifier(normalized, alias)
        }?.first
    }
}
