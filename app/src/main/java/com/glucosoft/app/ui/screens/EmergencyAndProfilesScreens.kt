package com.glucosoft.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.glucosoft.app.model.BiomedicalCatalog
import com.glucosoft.app.model.EmergencyContact
import com.glucosoft.app.model.GlucoseReading
import com.glucosoft.app.model.GlucoseSeverity
import com.glucosoft.app.model.GlucoseUnit
import com.glucosoft.app.model.LocalProfile
import com.glucosoft.app.model.PatientTab
import com.glucosoft.app.model.ProfileReminderConfig
import com.glucosoft.app.service.ReminderManager
import com.glucosoft.app.ui.theme.JetBrainsMonoFamily
import com.glucosoft.app.ui.theme.RoseAlert
import com.glucosoft.app.ui.theme.rememberStatusColorPair
import com.glucosoft.app.util.GlucoseMath
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun EmergencyScreen(
    activeProfile: LocalProfile,
    latestReading: GlucoseReading?,
    onSaveContact: (EmergencyContact) -> Unit,
    onRequestCall: (EmergencyContact) -> Unit,
    onOpenGuide: () -> Unit
) {
    val existing = activeProfile.emergencyContact
    var isEditing by remember(existing) { mutableStateOf(existing == null) }
    var nameInput by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var relationInput by remember(existing) { mutableStateOf(existing?.relation ?: "") }
    var phoneInput by remember(existing) { mutableStateOf(existing?.phone ?: "") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("emergency_screen_card")
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Perfil activo: ${activeProfile.name}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(RoseAlert.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneInTalk,
                            contentDescription = null,
                            tint = RoseAlert,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = "Contacto de emergencia",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            text = "Persona de confianza o médico de ${activeProfile.name} si necesitas ayuda.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!isEditing && existing != null) {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Contacto guardado en este teléfono",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = existing.name,
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Relación: ${existing.relation}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Teléfono: ${existing.phone}",
                                style = MaterialTheme.typography.titleLarge,
                                fontFamily = JetBrainsMonoFamily,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    if (latestReading != null) {
                        val cautionColors = rememberStatusColorPair(GlucoseSeverity.CAUTION)
                        val formatted = GlucoseMath.formatGlucoseValue(
                            latestReading.valueMgDl,
                            activeProfile.unitPreference
                        )
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = cautionColors.containerColor
                            ),
                            border = BorderStroke(1.dp, cautionColors.borderColor)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = cautionColors.contentColor,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = "Dato útil para tu llamada: tu última medición registrada fue de $formatted ${activeProfile.unitPreference.symbol} (${latestReading.context.label}).",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = cautionColors.contentColor
                                )
                            }
                        }
                    }

                    // Acción principal de la pantalla Ayuda: Llamar (56 dp)
                    Button(
                        onClick = { onRequestCall(existing) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RoseAlert,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 58.dp)
                            .testTag("emergency_call_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Llamar a ${existing.name}",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }

                    OutlinedButton(
                        onClick = { isEditing = true },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .testTag("emergency_edit_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Cambiar datos del contacto",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                } else {
                    Text(
                        text = "Escribe los datos de la persona a quien deseas llamar en caso de emergencia:",
                        style = MaterialTheme.typography.bodyLarge
                    )

                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = {
                            nameInput = it
                            nameError = null
                        },
                        label = { Text("Nombre de la persona (ejemplo: Laura Vargas)") },
                        isError = nameError != null,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("emergency_name_input")
                    )
                    if (nameError != null) {
                        Text(
                            text = nameError!!,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        )
                    }

                    OutlinedTextField(
                        value = relationInput,
                        onValueChange = { relationInput = it },
                        label = { Text("Relación (ejemplo: Hija, Esposo, Médico)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("emergency_relation_input")
                    )

                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = {
                            phoneInput = it
                            phoneError = null
                        },
                        label = { Text("Número de teléfono (ejemplo: +57 300 412 8890)") },
                        isError = phoneError != null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("emergency_phone_input")
                    )
                    if (phoneError != null) {
                        Text(
                            text = phoneError!!,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        )
                    }

                    Button(
                        onClick = {
                            nameError = null
                            phoneError = null
                            val cleanName = nameInput.trim()
                            val cleanPhone = phoneInput.trim()
                            if (cleanName.isEmpty()) {
                                nameError = "Escribe el nombre de tu contacto de emergencia."
                            }
                            if (cleanPhone.length < 5) {
                                phoneError = "Escribe un número de teléfono válido. Conservamos lo escrito."
                            }
                            if (nameError == null && phoneError == null) {
                                onSaveContact(
                                    EmergencyContact(
                                        name = cleanName,
                                        relation = relationInput.trim().ifEmpty { "Familiar / Confianza" },
                                        phone = cleanPhone
                                    )
                                )
                                isEditing = false
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .testTag("save_emergency_contact_button")
                    ) {
                        Text(
                            text = "Guardar contacto de emergencia",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    if (existing != null) {
                        OutlinedButton(
                            onClick = { isEditing = false },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Text("Cancelar cambios")
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                OutlinedButton(
                    onClick = onOpenGuide,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ver guía paso a paso de la aplicación")
                }
            }
        }
    }
}

@Composable
fun EmergencyCallConfirmDialog(
    contact: EmergencyContact?,
    context: Context,
    onDismiss: () -> Unit
) {
    if (contact == null) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "¿Llamar a ${contact.name}?",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() }
            )
        },
        text = {
            Text(
                text = "Se abrirá el teléfono con el número ${contact.phone} (${contact.relation}). La aplicación nunca llama sin tu confirmación.",
                style = MaterialTheme.typography.bodyLarge
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        val sanitized = contact.phone.replace(Regex("[^0-9+]"), "")
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$sanitized")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {
                    }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = RoseAlert,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .heightIn(min = 52.dp)
                    .testTag("confirm_emergency_call_button")
            ) {
                Icon(imageVector = Icons.Default.Phone, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sí, abrir teléfono", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cancelar")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfilesScreen(
    profiles: List<LocalProfile>,
    activeProfile: LocalProfile,
    allReadings: List<GlucoseReading>,
    onSelectProfile: (String) -> Unit,
    onSelectTab: (PatientTab) -> Unit,
    onCreateProfile: (name: String, unit: GlucoseUnit) -> Unit,
    onUpdateProfile: (id: String, name: String, unit: GlucoseUnit, minMgDl: Int, maxMgDl: Int) -> Unit,
    onChangeProfileUnit: (id: String, unit: GlucoseUnit) -> Unit,
    onDeleteProfile: (String) -> Unit,
    reminders: List<ProfileReminderConfig> = emptyList(),
    onSaveProfileReminder: (profileId: String, enabled: Boolean, hour: Int, minute: Int) -> Unit = { _, _, _, _ -> },
    onTriggerTestReminder: (profileId: String) -> Unit = {},
    onExportBackup: (targetUri: Uri, password: String, onResult: (Boolean, String) -> Unit) -> Unit = { _, _, _ -> },
    onRestoreBackup: (sourceUri: Uri, password: String, onResult: (Boolean, String) -> Unit) -> Unit = { _, _, _ -> }
) {
    var newName by remember { mutableStateOf("") }
    var newUnit by remember { mutableStateOf(GlucoseUnit.MG_DL) }
    var newNameError by remember { mutableStateOf<String?>(null) }
    var editingProfile by remember { mutableStateOf<LocalProfile?>(null) }
    var profilePendingDelete by remember { mutableStateOf<LocalProfile?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("profiles_screen_card")
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Perfiles en este teléfono (${profiles.size} de ${BiomedicalCatalog.MAX_LOCAL_PROFILES})",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = "Cada persona tiene su historial, unidad, meta orientativa y contacto de emergencia por separado, sin contraseñas ni internet.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                profiles.forEach { p ->
                    val isCurrent = p.id == activeProfile.id
                    val count = allReadings.count { it.profileId == p.id }
                    val minStr = GlucoseMath.formatGlucoseValue(p.targetMinMgDl, p.unitPreference)
                    val maxStr = GlucoseMath.formatGlucoseValue(p.targetMaxMgDl, p.unitPreference)

                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCurrent) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            }
                        ),
                        border = BorderStroke(
                            width = if (isCurrent) 2.dp else 1.dp,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (isCurrent) Icons.Default.CheckCircle else Icons.Default.Person,
                                    contentDescription = null,
                                    tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (isCurrent) "${p.name} (Perfil activo)" else p.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Text(
                                text = "• $count ${if (count == 1) "medición guardada" else "mediciones guardadas"}\n• Unidad: ${p.unitPreference.symbol} · Meta orientativa: $minStr–$maxStr ${p.unitPreference.symbol}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            if (!isCurrent) {
                                Button(
                                    onClick = {
                                        onSelectProfile(p.id)
                                        onSelectTab(PatientTab.HOME)
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 52.dp)
                                        .testTag("use_profile_${p.id}")
                                ) {
                                    Text(
                                        text = "Usar el perfil de ${p.name}",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { editingProfile = p },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("edit_profile_${p.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Editar perfil")
                                }

                                OutlinedButton(
                                    onClick = { profilePendingDelete = p },
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("delete_profile_${p.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Eliminar")
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                // Cambio rápido de unidad para el perfil activo
                Text(
                    text = "Unidad de medida para ${activeProfile.name} (actual: ${activeProfile.unitPreference.symbol})",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Cambia entre mg/dL o mmol/L según las indicaciones de tu médico.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    GlucoseUnit.entries.forEach { u ->
                        val active = activeProfile.unitPreference == u
                        if (active) {
                            Button(
                                onClick = { onChangeProfileUnit(activeProfile.id, u) },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.heightIn(min = 50.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = "Seleccionada")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Usando ${u.symbol}", fontFamily = JetBrainsMonoFamily)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { onChangeProfileUnit(activeProfile.id, u) },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.heightIn(min = 50.dp)
                            ) {
                                Text("Cambiar a ${u.symbol}", fontFamily = JetBrainsMonoFamily)
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                if (profiles.size < BiomedicalCatalog.MAX_LOCAL_PROFILES) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PersonAdd,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Agregar otra persona (${profiles.size} de ${BiomedicalCatalog.MAX_LOCAL_PROFILES})",
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }

                            OutlinedTextField(
                                value = newName,
                                onValueChange = {
                                    if (it.length <= 32) newName = it
                                    newNameError = null
                                },
                                label = { Text("Nombre del nuevo perfil (ejemplo: Sofía)") },
                                isError = newNameError != null,
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("new_profile_name_input")
                            )

                            if (newNameError != null) {
                                Text(
                                    text = newNameError!!,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                                )
                            }

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                GlucoseUnit.entries.forEach { u ->
                                    val selected = newUnit == u
                                    if (selected) {
                                        Button(
                                            onClick = { newUnit = u },
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.Check, contentDescription = null)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Unidad: ${u.symbol}", fontFamily = JetBrainsMonoFamily)
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = { newUnit = u },
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) {
                                            Text("Elegir ${u.symbol}", fontFamily = JetBrainsMonoFamily)
                                        }
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    val clean = newName.trim()
                                    if (clean.isEmpty()) {
                                        newNameError = "Escribe un nombre para crear el nuevo perfil."
                                    } else {
                                        onCreateProfile(clean, newUnit)
                                        newName = ""
                                        newNameError = null
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .testTag("create_profile_button")
                            ) {
                                Text(
                                    text = "Crear perfil nuevo",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Ya existen 3 perfiles locales (límite máximo en este teléfono). Si deseas crear otro, primero elimina uno existente.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Sección «Recordatorios»: recordatorios locales diarios opcionales por perfil
        val activeReminderConfig = remember(reminders, activeProfile.id) {
            reminders.find { it.profileId == activeProfile.id }
                ?: ProfileReminderConfig(profileId = activeProfile.id, enabled = false, hour = 8, minute = 0)
        }
        ProfilesRemindersSectionCard(
            activeProfile = activeProfile,
            reminderConfig = activeReminderConfig,
            onSaveReminder = { enabled, hour, minute ->
                onSaveProfileReminder(activeProfile.id, enabled, hour, minute)
            },
            onTriggerTestReminder = {
                onTriggerTestReminder(activeProfile.id)
            }
        )

        // Sección «Copia de seguridad»: guardar copia cifrada con contraseña y recuperar datos locales
        ProfilesBackupSectionCard(
            onExportBackup = onExportBackup,
            onRestoreBackup = onRestoreBackup
        )
    }

    // Modal para editar nombre, unidad y metas orientativas del perfil
    val toEdit = editingProfile
    if (toEdit != null) {
        var editName by remember(toEdit) { mutableStateOf(toEdit.name) }
        var editUnit by remember(toEdit) { mutableStateOf(toEdit.unitPreference) }
        var editMin by remember(toEdit) {
            mutableStateOf(GlucoseMath.formatGlucoseValue(toEdit.targetMinMgDl, toEdit.unitPreference))
        }
        var editMax by remember(toEdit) {
            mutableStateOf(GlucoseMath.formatGlucoseValue(toEdit.targetMaxMgDl, toEdit.unitPreference))
        }
        var editError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { editingProfile = null },
            title = {
                Text(
                    text = "Editar perfil de ${toEdit.name}",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() }
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = {
                            if (it.length <= 32) editName = it
                            editError = null
                        },
                        label = { Text("Nombre del perfil") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlucoseUnit.entries.forEach { u ->
                            val selected = editUnit == u
                            if (selected) {
                                Button(
                                    onClick = {
                                        editUnit = u
                                        editMin = if (u == GlucoseUnit.MMOL_L) "3.9" else "70"
                                        editMax = if (u == GlucoseUnit.MMOL_L) "7.8" else "140"
                                    },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text(u.symbol, fontFamily = JetBrainsMonoFamily)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        editUnit = u
                                        editMin = if (u == GlucoseUnit.MMOL_L) "3.9" else "70"
                                        editMax = if (u == GlucoseUnit.MMOL_L) "7.8" else "140"
                                    },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text(u.symbol, fontFamily = JetBrainsMonoFamily)
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = editMin,
                        onValueChange = {
                            editMin = it
                            editError = null
                        },
                        label = { Text("Meta mínima orientativa (${editUnit.symbol})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = editMax,
                        onValueChange = {
                            editMax = it
                            editError = null
                        },
                        label = { Text("Meta máxima orientativa (${editUnit.symbol})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (editError != null) {
                        Text(
                            text = editError!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Text(
                        text = "Estos valores son orientativos según las indicaciones de tu profesional de salud.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanName = editName.trim()
                        val minNum = editMin.replace(',', '.').toDoubleOrNull()
                        val maxNum = editMax.replace(',', '.').toDoubleOrNull()
                        if (cleanName.isEmpty()) {
                            editError = "El nombre no puede quedar vacío."
                            return@Button
                        }
                        if (minNum == null || maxNum == null || maxNum <= minNum) {
                            editError = "La meta máxima debe ser mayor que la meta mínima."
                            return@Button
                        }
                        val minMg = if (editUnit == GlucoseUnit.MMOL_L) {
                            GlucoseMath.mmolToMgDl(minNum)
                        } else {
                            minNum.roundToInt()
                        }
                        val maxMg = if (editUnit == GlucoseUnit.MMOL_L) {
                            GlucoseMath.mmolToMgDl(maxNum)
                        } else {
                            maxNum.roundToInt()
                        }
                        onUpdateProfile(toEdit.id, cleanName, editUnit, minMg, maxMg)
                        editingProfile = null
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Guardar cambios")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { editingProfile = null },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Confirmación de eliminación de perfil
    val pendingDel = profilePendingDelete
    if (pendingDel != null) {
        val count = allReadings.count { it.profileId == pendingDel.id }
        AlertDialog(
            onDismissRequest = { profilePendingDelete = null },
            title = {
                Text(
                    text = "¿Eliminar el perfil de ${pendingDel.name}?",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() }
                )
            },
            text = {
                Text(
                    text = "Si confirmas, se borrarán permanentemente $count ${if (count == 1) "medición guardada" else "mediciones guardadas"} junto con su contacto de emergencia en este teléfono. Esta acción no se puede deshacer.",
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteProfile(pendingDel.id)
                        profilePendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RoseAlert,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Sí, eliminar permanentemente")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { profilePendingDelete = null },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("No, conservar")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfilesRemindersSectionCard(
    activeProfile: LocalProfile,
    reminderConfig: ProfileReminderConfig,
    onSaveReminder: (enabled: Boolean, hour: Int, minute: Int) -> Unit,
    onTriggerTestReminder: () -> Unit
) {
    val context = LocalContext.current
    var draftEnabled by remember(reminderConfig.profileId, reminderConfig.enabled) {
        mutableStateOf(reminderConfig.enabled)
    }
    var draftHour by remember(reminderConfig.profileId, reminderConfig.hour) {
        mutableStateOf(reminderConfig.hour.coerceIn(0, 23))
    }
    var draftMinute by remember(reminderConfig.profileId, reminderConfig.minute) {
        mutableStateOf(reminderConfig.minute.coerceIn(0, 59))
    }
    var permissionMessage by remember(reminderConfig.profileId) {
        mutableStateOf<String?>(null)
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val availability = ReminderManager.checkNotificationAvailability(context)
        if (!granted || !availability.canPostNotifications) {
            permissionMessage = availability.honestDiagnosticMessage()
                ?: "Permiso de notificaciones denegado. Activa las notificaciones de Glucosoft en los Ajustes de Android para recibir el aviso."
        } else {
            permissionMessage = null
        }
        draftEnabled = true
        onSaveReminder(true, draftHour, draftMinute)
    }

    val formattedDraftTime = String.format(Locale.US, "%02d:%02d", draftHour, draftMinute)

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profiles_reminders_card")
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (draftEnabled) {
                            Icons.Default.NotificationsActive
                        } else {
                            Icons.Default.NotificationsOff
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Recordatorios para ${activeProfile.name}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = if (reminderConfig.enabled) {
                            "Aviso diario activo a las ${reminderConfig.formattedTime}"
                        } else {
                            "Desactivado por defecto (opcional)"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = "Recibe una notificación diaria en este teléfono para recordar medir tu glucosa. El aviso nunca muestra tus valores médicos en la pantalla bloqueada y ofrece «Registrar ahora» o «Más tarde» (15 minutos).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Selector real de hora y minuto con botones grandes (+/-) y horas frecuentes
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Hora elegida: $formattedDraftTime",
                        style = MaterialTheme.typography.headlineMedium,
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("reminder_selected_time_text")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Control de Hora (00..23)
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Hora (0–23)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { draftHour = (draftHour + 23) % 24 },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("reminder_hour_minus_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Remove,
                                        contentDescription = "Restar una hora"
                                    )
                                }
                                Text(
                                    text = String.format(Locale.US, "%02d", draftHour),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold
                                )
                                OutlinedButton(
                                    onClick = { draftHour = (draftHour + 1) % 24 },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("reminder_hour_plus_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Sumar una hora"
                                    )
                                }
                            }
                        }

                        // Control de Minutos (00..59)
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Minutos",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { draftMinute = (draftMinute + 55) % 60 },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("reminder_minute_minus_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Remove,
                                        contentDescription = "Restar 5 minutos"
                                    )
                                }
                                Text(
                                    text = String.format(Locale.US, "%02d", draftMinute),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold
                                )
                                OutlinedButton(
                                    onClick = { draftMinute = (draftMinute + 5) % 60 },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("reminder_minute_plus_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Sumar 5 minutos"
                                    )
                                }
                            }
                        }
                    }

                    // Horas rápidas frecuentes para facilitar la elección con un toque
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val presets = listOf(7 to 0, 8 to 0, 12 to 30, 19 to 0, 21 to 0)
                        presets.forEach { (h, m) ->
                            val label = String.format(Locale.US, "%02d:%02d", h, m)
                            val isSelected = draftHour == h && draftMinute == m
                            if (isSelected) {
                                Button(
                                    onClick = {
                                        draftHour = h
                                        draftMinute = m
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .semantics { selected = true }
                                        .testTag("reminder_preset_${h}_${m}")
                                ) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(label, fontFamily = JetBrainsMonoFamily)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        draftHour = h
                                        draftMinute = m
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .semantics { selected = false }
                                        .testTag("reminder_preset_${h}_${m}")
                                ) {
                                    Text(label, fontFamily = JetBrainsMonoFamily)
                                }
                            }
                        }
                    }
                }
            }

            if (permissionMessage != null) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite }
                        .testTag("reminder_permission_warning_card")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = permissionMessage!!,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Botones para activar/guardar o desactivar el recordatorio
            Button(
                onClick = {
                    val availability = ReminderManager.checkNotificationAvailability(context)
                    if (Build.VERSION.SDK_INT >= 33 && !availability.hasRuntimePermission) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        permissionMessage = availability.honestDiagnosticMessage()
                        draftEnabled = true
                        onSaveReminder(true, draftHour, draftMinute)
                    }
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .testTag("reminder_save_button")
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (reminderConfig.enabled) {
                        "Guardar hora del recordatorio ($formattedDraftTime)"
                    } else {
                        "Activar recordatorio a las $formattedDraftTime"
                    },
                    style = MaterialTheme.typography.titleMedium
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (reminderConfig.enabled || draftEnabled) {
                    OutlinedButton(
                        onClick = {
                            draftEnabled = false
                            permissionMessage = null
                            onSaveReminder(false, draftHour, draftMinute)
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("reminder_enable_toggle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsOff,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Desactivar recordatorio")
                    }
                }

                OutlinedButton(
                    onClick = {
                        val availability = ReminderManager.checkNotificationAvailability(context)
                        permissionMessage = availability.honestDiagnosticMessage()
                        onTriggerTestReminder()
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("reminder_test_notification_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Probar aviso")
                }
            }
        }
    }
}

private enum class BackupOperationMode {
    EXPORT,
    RESTORE
}

@Composable
fun ProfilesBackupSectionCard(
    onExportBackup: (targetUri: Uri, password: String, onResult: (Boolean, String) -> Unit) -> Unit,
    onRestoreBackup: (sourceUri: Uri, password: String, onResult: (Boolean, String) -> Unit) -> Unit
) {
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var pendingMode by remember { mutableStateOf<BackupOperationMode?>(null) }
    var passwordInput by remember { mutableStateOf("") }
    var passwordConfirmInput by remember { mutableStateOf("") }
    var passwordDialogError by remember { mutableStateOf<String?>(null) }
    var statusResultMessage by remember { mutableStateOf<String?>(null) }
    var statusIsSuccess by remember { mutableStateOf(true) }

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri == null) {
            statusIsSuccess = false
            statusResultMessage = "Guardado de copia cancelado. No se creó ningún archivo."
        } else {
            pendingUri = uri
            pendingMode = BackupOperationMode.EXPORT
            passwordInput = ""
            passwordConfirmInput = ""
            passwordDialogError = null
        }
    }

    val openBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            statusIsSuccess = false
            statusResultMessage = "Recuperación cancelada. Tus datos actuales no se modificaron."
        } else {
            pendingUri = uri
            pendingMode = BackupOperationMode.RESTORE
            passwordInput = ""
            passwordConfirmInput = ""
            passwordDialogError = null
        }
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profiles_backup_card")
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Backup,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Copia de seguridad",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = "Archivo local cifrado con contraseña (sin cuentas ni servidores)",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = "Guarda una copia protegida con contraseña para conservar o llevar a otro teléfono tus perfiles, mediciones históricas, etiquetas, notas, unidades, fotos de comidas y recordatorios.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = {
                    createBackupLauncher.launch("glucosoft_copia_seguridad.gbkp")
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .testTag("backup_save_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Guardar copia",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            OutlinedButton(
                onClick = {
                    openBackupLauncher.launch(arrayOf("*/*"))
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp)
                    .testTag("backup_restore_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Restore,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Recuperar mis datos",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (statusResultMessage != null) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (statusIsSuccess) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite }
                        .testTag("backup_status_result_card")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (statusIsSuccess) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (statusIsSuccess) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            }
                        )
                        Text(
                            text = statusResultMessage!!,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (statusIsSuccess) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            }
                        )
                    }
                }
            }
        }
    }

    val activeMode = pendingMode
    val activeUri = pendingUri
    if (activeMode != null && activeUri != null) {
        val isExport = activeMode == BackupOperationMode.EXPORT
        AlertDialog(
            onDismissRequest = {
                pendingMode = null
                pendingUri = null
                statusIsSuccess = false
                statusResultMessage = if (isExport) {
                    "Guardado de copia cancelado."
                } else {
                    "Recuperación cancelada. Tus datos actuales no se modificaron."
                }
            },
            title = {
                Text(
                    text = if (isExport) {
                        "Proteger copia con contraseña"
                    } else {
                        "Contraseña de la copia de seguridad"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() }
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.testTag("backup_password_dialog")
                ) {
                    Text(
                        text = if (isExport) {
                            "Elige una contraseña de al menos 4 caracteres para cifrar el archivo. La necesitarás cuando quieras recuperar tus datos."
                        } else {
                            "Escribe la contraseña con la que guardaste esta copia. Tus datos actuales se conservarán y se combinarán sin duplicados."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )

                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = {
                            passwordInput = it
                            passwordDialogError = null
                        },
                        label = { Text("Contraseña") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("backup_password_input")
                    )

                    if (isExport) {
                        OutlinedTextField(
                            value = passwordConfirmInput,
                            onValueChange = {
                                passwordConfirmInput = it
                                passwordDialogError = null
                            },
                            label = { Text("Repetir contraseña") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("backup_password_confirm_input")
                        )
                    }

                    if (passwordDialogError != null) {
                        Text(
                            text = passwordDialogError!!,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = passwordInput.trim()
                        if (trimmed.length < 4) {
                            passwordDialogError = "La contraseña debe tener al menos 4 caracteres."
                            return@Button
                        }
                        if (isExport && trimmed != passwordConfirmInput.trim()) {
                            passwordDialogError = "Las contraseñas no coinciden. Revisa e intenta de nuevo."
                            return@Button
                        }
                        val targetUri = activeUri
                        pendingMode = null
                        pendingUri = null
                        if (isExport) {
                            onExportBackup(targetUri, trimmed) { ok, msg ->
                                statusIsSuccess = ok
                                statusResultMessage = msg
                            }
                        } else {
                            onRestoreBackup(targetUri, trimmed) { ok, msg ->
                                statusIsSuccess = ok
                                statusResultMessage = msg
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("backup_password_confirm_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isExport) "Guardar copia cifrada" else "Recuperar datos")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        pendingMode = null
                        pendingUri = null
                        statusIsSuccess = false
                        statusResultMessage = if (isExport) {
                            "Guardado de copia cancelado."
                        } else {
                            "Recuperación cancelada. Tus datos actuales no se modificaron."
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("backup_password_cancel_button")
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
}

