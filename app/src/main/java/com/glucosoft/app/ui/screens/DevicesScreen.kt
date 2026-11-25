package com.glucosoft.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.glucosoft.app.model.BiomedicalCatalog
import com.glucosoft.app.model.GlucoseSeverity
import com.glucosoft.app.model.LocalProfile
import com.glucosoft.app.service.ConnectedMeterSession
import com.glucosoft.app.ui.theme.rememberStatusColorPair

/**
 * Sección Medidor:
 * - Distingue con claridad el botón principal «Conectar medidor por Bluetooth» (56 dp) de «Registrar a mano» (52 dp).
 * - Muestra únicamente los medidores cuyo protocolo de comunicación Bluetooth está confirmado en el catálogo.
 */
@Composable
fun DevicesScreen(
    activeProfile: LocalProfile,
    connectedSession: ConnectedMeterSession?,
    onOpenBleModal: () -> Unit,
    onOpenManualEntry: () -> Unit
) {
    var expandedDeviceId by remember { mutableStateOf<String?>(null) }

    val normalColors = rememberStatusColorPair(GlucoseSeverity.NORMAL)
    val cautionColors = rememberStatusColorPair(GlucoseSeverity.CAUTION)

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("devices_header_card")
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Mi medidor",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            text = "Se guardará en: ${activeProfile.name}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (connectedSession != null) {
                    val sessionColors = if (connectedSession.isReadyForMeasurements) {
                        normalColors
                    } else {
                        cautionColors
                    }
                    val sessionIcon = if (connectedSession.isReadyForMeasurements) {
                        Icons.Default.CheckCircle
                    } else {
                        Icons.Default.Warning
                    }
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = sessionColors.containerColor
                        ),
                        border = BorderStroke(1.5.dp, sessionColors.borderColor)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = sessionIcon,
                                contentDescription = null,
                                tint = sessionColors.contentColor
                            )
                            Text(
                                text = "${connectedSession.matchedModelName} (${connectedSession.deviceName}) · ${connectedSession.connectionMessage}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = sessionColors.contentColor
                            )
                        }
                    }
                }

                // Acción principal: Conectar medidor (56 dp)
                Button(
                    onClick = onOpenBleModal,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .testTag("devices_open_ble_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Bluetooth,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (connectedSession != null) {
                            "Gestionar medidor conectado"
                        } else {
                            "Conectar medidor por Bluetooth"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                // Acción secundaria claramente diferenciada: Registrar a mano
                OutlinedButton(
                    onClick = onOpenManualEntry,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .testTag("devices_open_manual_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.EditNote,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Registrar a mano",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }

        // Lista de medidores con protocolo Bluetooth confirmado en la fuente única del catálogo
        val confirmedBluetoothModels = remember { BiomedicalCatalog.confirmedBluetoothModels() }

        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Medidores con protocolo confirmado",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() }
                )

                confirmedBluetoothModels.forEach { dev ->
                    val isExpanded = expandedDeviceId == dev.id.rawId

                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = BorderStroke(
                            width = if (isExpanded) 2.dp else 1.dp,
                            color = if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .clickable(role = Role.Button) {
                                expandedDeviceId = if (isExpanded) null else dev.id.rawId
                            }
                            .testTag("device_card_${dev.id.rawId}")
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bluetooth,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Text(
                                    text = dev.displayName,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // Etiqueta breve de validación pendiente con el medidor físico
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = normalColors.containerColor
                                ),
                                border = BorderStroke(1.dp, normalColors.borderColor),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bluetooth,
                                        contentDescription = null,
                                        tint = normalColors.contentColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = dev.pendingTestBadgeLabel,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = normalColors.contentColor,
                                        textAlign = TextAlign.Start,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            // Acción «Ver ficha» / «Cerrar ficha» apilada debajo para no quitar ancho al nombre del medidor
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (isExpanded) "Cerrar ficha" else "Ver ficha",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            // Ficha técnica desplegable con un toque
                            AnimatedVisibility(visible = isExpanded) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("device_sheet_${dev.id.rawId}")
                                ) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                    Text(
                                        text = dev.summary,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = dev.protocolNotes,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "• Memoria: ${dev.memoryCapacityLabel}\n" +
                                            "• Tiempo de lectura: ${dev.confirmedReadTimeSeconds ?: "Variable"} segundos\n" +
                                            "• Segunda muestra de confirmación: ${if (dev.supportsMultiSample) "Sí" else "No"}\n" +
                                            "• Identificadores Bluetooth confirmados: ${dev.confirmedBleIdentifiers.joinToString(", ")}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
