# Glucosoft App (Android Nativo — Kotlin + Jetpack Compose)

Aplicación Android nativa (`com.glucosoft.app`) para el control diario de glucosa, 100% local y privada sin necesidad de internet ni cuentas en la nube.

## Funcionalidades incluidas
- **Perfiles locales independientes (hasta 3 personas):** Base de datos SQLite local mediante Room (`glucosoft_local_v2.db`) con unidad preferida (`mg/dL` o `mmol/L`), rangos orientativos configurables y contacto de emergencia individual.
- **Registro manual y Bluetooth Low Energy (BLE):**
  - Escáner BLE real (`BluetoothLeScanner` y `BluetoothGatt`) con soporte para el servicio estándar Bluetooth SIG de glucosa (`0x1808` / `0x2A18`) y descarga de memoria (`RACP 0x2A52`).
  - Punto de extensión listo en `BleGlucometerManager.parseProprietaryGlucometerPacket(...)` para integrar tramas propietarias de los glucómetros de Diagnósticos Biomédicos (`OKMeter Match II`, `GlucoLeader Enhance`, `Glucocare D`, `Glucomax`, `Glucocare Match`).
- **Gráfico interactivo y estadísticas:** Tendencia en el tiempo, porcentaje dentro de meta, promedio y GMI orientativo.
- **Reporte clínico en PDF (`PdfDocument`):** Generación de PDF A4 multipágina con resumen estadístico y tabla coloreada de lecturas, listo para guardar o compartir mediante `FileProvider`.
- **Contacto de emergencia con confirmación:** Apertura segura del marcador telefónico (`ACTION_DIAL`) siempre bajo confirmación explícita del usuario.
