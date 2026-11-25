package com.glucosoft.app.service

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.glucosoft.app.MainActivity
import com.glucosoft.app.R
import com.glucosoft.app.model.ProfileReminderConfig
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

data class ReminderNotificationAvailability(
    val hasRuntimePermission: Boolean,
    val areAppNotificationsEnabled: Boolean,
    val isChannelEnabled: Boolean
) {
    val canPostNotifications: Boolean
        get() = hasRuntimePermission && areAppNotificationsEnabled && isChannelEnabled

    fun honestDiagnosticMessage(): String? {
        return when {
            !hasRuntimePermission ->
                "El permiso de notificaciones está desactivado. Para recibir avisos diarios, concédelo al activar el recordatorio o desde los Ajustes de Android."
            !areAppNotificationsEnabled ->
                "Las notificaciones de Glucosoft están desactivadas en los ajustes del teléfono. Actívalas en Ajustes > Aplicaciones > Glucosoft > Notificaciones."
            !isChannelEnabled ->
                "El canal «Recordatorios de medición» está silenciado o desactivado en Android. Actívalo en los ajustes de notificaciones de la aplicación."
            else -> null
        }
    }
}

data class ReminderActionResolution(
    val actionType: ReminderActionType,
    val profileId: String,
    val snoozeTargetEpochMs: Long? = null,
    val opensManualEntryForm: Boolean = false,
    val autoSavesGlucoseValue: Boolean = false
)

enum class ReminderActionType {
    REGISTER_NOW,
    SNOOZE_15_MINUTES
}

/**
 * Gestor de recordatorios diarios locales por perfil.
 * - Programa alarmas locales en Android (`AlarmManager`) sin servidores externos.
 * - Incluye las acciones «Registrar ahora» (abre el formulario manual en el perfil correspondiente
 *   sin guardar ningún valor automáticamente) y «Más tarde» (pospone 15 minutos una vez por pulsación).
 * - Protege la privacidad en pantalla bloqueada (`VISIBILITY_PRIVATE` con versión pública sin datos médicos).
 */
object ReminderManager {
    const val CHANNEL_ID = "glucosoft_daily_reminders_v1"
    const val CHANNEL_NAME = "Recordatorios de medición"
    const val CHANNEL_DESCRIPTION = "Avisos diarios opcionales para recordar anotar tu medición de glucosa"

    const val ACTION_TRIGGER_DAILY_REMINDER = "com.glucosoft.app.action.TRIGGER_DAILY_REMINDER"
    const val ACTION_TRIGGER_SNOOZE_REMINDER = "com.glucosoft.app.action.TRIGGER_SNOOZE_REMINDER"
    const val ACTION_SNOOZE_REMINDER = "com.glucosoft.app.action.SNOOZE_REMINDER"
    const val ACTION_OPEN_MANUAL_FROM_REMINDER = "com.glucosoft.app.action.OPEN_MANUAL_FROM_REMINDER"

    const val EXTRA_REMINDER_PROFILE_ID = "extra_reminder_profile_id"
    const val EXTRA_REMINDER_PROFILE_NAME = "extra_reminder_profile_name"

    const val SNOOZE_DURATION_MS = 15L * 60L * 1000L // 15 minutos exactos

    /**
     * Calcula el próximo instante epoch (`ms`) para la hora y minutos diarios elegidos (`hour`, `minute`)
     * en la zona horaria local indicada. Si la hora de hoy ya pasó (`<= nowMs`), programa para mañana.
     */
    fun computeNextDailyTriggerAtMs(
        hour: Int,
        minute: Int,
        nowMs: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long {
        val safeHour = hour.coerceIn(0, 23)
        val safeMinute = minute.coerceIn(0, 59)
        val cal = Calendar.getInstance(timeZone, Locale("es", "ES"))
        cal.timeInMillis = nowMs
        cal.set(Calendar.HOUR_OF_DAY, safeHour)
        cal.set(Calendar.MINUTE, safeMinute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= nowMs) {
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return cal.timeInMillis
    }

    /**
     * Calcula el instante exacto al pulsar «Más tarde»: pospone 15 minutos una vez por pulsación.
     */
    fun computeSnoozeTriggerAtMs(nowMs: Long = System.currentTimeMillis()): Long {
        return nowMs + SNOOZE_DURATION_MS
    }

    /**
     * Resuelve de forma determinista el efecto de las acciones de notificación para pruebas y ejecución:
     * - «Registrar ahora» abre el formulario manual en el perfil indicado y NUNCA guarda un valor automáticamente.
     * - «Más tarde» pospone exactamente 15 minutos desde el instante de la pulsación.
     */
    fun resolveNotificationAction(
        action: String?,
        profileId: String?,
        nowMs: Long = System.currentTimeMillis()
    ): ReminderActionResolution? {
        val cleanProfileId = profileId?.trim().orEmpty()
        if (cleanProfileId.isEmpty()) return null
        return when (action) {
            ACTION_OPEN_MANUAL_FROM_REMINDER -> ReminderActionResolution(
                actionType = ReminderActionType.REGISTER_NOW,
                profileId = cleanProfileId,
                snoozeTargetEpochMs = null,
                opensManualEntryForm = true,
                autoSavesGlucoseValue = false
            )
            ACTION_SNOOZE_REMINDER -> ReminderActionResolution(
                actionType = ReminderActionType.SNOOZE_15_MINUTES,
                profileId = cleanProfileId,
                snoozeTargetEpochMs = computeSnoozeTriggerAtMs(nowMs),
                opensManualEntryForm = false,
                autoSavesGlucoseValue = false
            )
            else -> null
        }
    }

    /**
     * Extrae el `profileId` si el `Intent` recibido por `MainActivity` proviene de la acción
     * «Registrar ahora» o de tocar la notificación del recordatorio.
     */
    fun extractProfileIdFromOpenIntent(intent: Intent?): String? {
        if (intent == null) return null
        if (intent.action != ACTION_OPEN_MANUAL_FROM_REMINDER) return null
        return intent.getStringExtra(EXTRA_REMINDER_PROFILE_ID)?.takeIf { it.isNotBlank() }
    }

    fun notificationIdForProfile(profileId: String): Int {
        return 40_000 + (abs(profileId.hashCode()) % 10_000)
    }

    private fun dailyAlarmRequestCodeForProfile(profileId: String): Int {
        return 50_000 + (abs(profileId.hashCode()) % 10_000)
    }

    private fun snoozeAlarmRequestCodeForProfile(profileId: String): Int {
        return 60_000 + (abs(profileId.hashCode()) % 10_000)
    }

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val existing = nm.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = CHANNEL_DESCRIPTION
                    enableVibration(true)
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    /**
     * Verifica con sinceridad si el permiso `POST_NOTIFICATIONS` (Android 13+) y el canal de notificaciones
     * están activos en el sistema.
     */
    fun checkNotificationAvailability(context: Context): ReminderNotificationAvailability {
        ensureNotificationChannel(context)
        val hasRuntimePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val compatManager = NotificationManagerCompat.from(context)
        val areAppNotificationsEnabled = try {
            compatManager.areNotificationsEnabled()
        } catch (_: Exception) {
            true
        }

        val isChannelEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val channel = nm?.getNotificationChannel(CHANNEL_ID)
            channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
        } else {
            true
        }

        return ReminderNotificationAvailability(
            hasRuntimePermission = hasRuntimePermission,
            areAppNotificationsEnabled = areAppNotificationsEnabled,
            isChannelEnabled = isChannelEnabled
        )
    }

    /**
     * Programa o cancela las alarmas de un perfil según su configuración actual (`config`).
     * Reemplaza cualquier alarma previa del mismo perfil para evitar duplicados.
     */
    fun syncProfileReminderSchedule(
        context: Context,
        config: ProfileReminderConfig,
        profileName: String,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val dailyIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_TRIGGER_DAILY_REMINDER
            putExtra(EXTRA_REMINDER_PROFILE_ID, config.profileId)
            putExtra(EXTRA_REMINDER_PROFILE_NAME, profileName)
        }
        val dailyPendingIntent = PendingIntent.getBroadcast(
            context,
            dailyAlarmRequestCodeForProfile(config.profileId),
            dailyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_TRIGGER_SNOOZE_REMINDER
            putExtra(EXTRA_REMINDER_PROFILE_ID, config.profileId)
            putExtra(EXTRA_REMINDER_PROFILE_NAME, profileName)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            snoozeAlarmRequestCodeForProfile(config.profileId),
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Cancelar alarmas previas antes de reprogramar o al desactivar
        alarmManager.cancel(dailyPendingIntent)
        alarmManager.cancel(snoozePendingIntent)

        if (!config.enabled) {
            dismissNotificationForProfile(context, config.profileId)
            return
        }

        val nextDailyAtMs = computeNextDailyTriggerAtMs(
            hour = config.hour,
            minute = config.minute,
            nowMs = nowMs
        )
        scheduleAlarmCompat(alarmManager, nextDailyAtMs, dailyPendingIntent)

        val snoozeAtMs = config.snoozedUntilMs
        if (snoozeAtMs != null && snoozeAtMs > nowMs) {
            scheduleAlarmCompat(alarmManager, snoozeAtMs, snoozePendingIntent)
        }
    }

    /**
     * Programa una alarma única de posposición («Más tarde», +15 minutos) para un perfil.
     */
    fun scheduleSnoozeAlarm(
        context: Context,
        profileId: String,
        profileName: String,
        snoozeTriggerAtMs: Long
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val snoozeIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_TRIGGER_SNOOZE_REMINDER
            putExtra(EXTRA_REMINDER_PROFILE_ID, profileId)
            putExtra(EXTRA_REMINDER_PROFILE_NAME, profileName)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            snoozeAlarmRequestCodeForProfile(profileId),
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(snoozePendingIntent)
        scheduleAlarmCompat(alarmManager, snoozeTriggerAtMs, snoozePendingIntent)
    }

    fun cancelProfileReminder(context: Context, profileId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        if (alarmManager != null) {
            val dailyIntent = Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_TRIGGER_DAILY_REMINDER
                putExtra(EXTRA_REMINDER_PROFILE_ID, profileId)
            }
            val dailyPi = PendingIntent.getBroadcast(
                context,
                dailyAlarmRequestCodeForProfile(profileId),
                dailyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(dailyPi)

            val snoozeIntent = Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_TRIGGER_SNOOZE_REMINDER
                putExtra(EXTRA_REMINDER_PROFILE_ID, profileId)
            }
            val snoozePi = PendingIntent.getBroadcast(
                context,
                snoozeAlarmRequestCodeForProfile(profileId),
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(snoozePi)
        }
        dismissNotificationForProfile(context, profileId)
    }

    fun dismissNotificationForProfile(context: Context, profileId: String) {
        try {
            NotificationManagerCompat.from(context).cancel(notificationIdForProfile(profileId))
        } catch (_: Exception) {
        }
    }

    /**
     * Muestra la notificación local de recordatorio para el perfil indicado.
     * - No expone valores de glucosa ni datos clínicos en la pantalla de bloqueo (`VISIBILITY_PRIVATE`).
     * - Incluye las acciones «Registrar ahora» y «Más tarde» (+15 min).
     */
    fun showReminderNotification(
        context: Context,
        profileId: String,
        profileName: String,
        isTestNotification: Boolean = false
    ): Boolean {
        val availability = checkNotificationAvailability(context)
        if (!availability.canPostNotifications) {
            return false
        }

        val notificationId = notificationIdForProfile(profileId)
        val openManualIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_MANUAL_FROM_REMINDER
            putExtra(EXTRA_REMINDER_PROFILE_ID, profileId)
            putExtra(EXTRA_REMINDER_PROFILE_NAME, profileName)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val registerNowPendingIntent = PendingIntent.getActivity(
            context,
            dailyAlarmRequestCodeForProfile(profileId) + 100,
            openManualIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeBroadcastIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_SNOOZE_REMINDER
            putExtra(EXTRA_REMINDER_PROFILE_ID, profileId)
            putExtra(EXTRA_REMINDER_PROFILE_NAME, profileName)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            snoozeAlarmRequestCodeForProfile(profileId) + 100,
            snoozeBroadcastIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Versión pública genérica para pantalla bloqueada: sin nombre ni datos de salud privados
        val publicNotification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Glucosoft")
            .setContentText("Tienes un recordatorio pendiente")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val titleText = if (isTestNotification) {
            "Aviso de prueba · Glucosoft"
        } else {
            "Hora de tu medición en Glucosoft"
        }
        val bodyText = "Perfil: $profileName. Toca «Registrar ahora» para anotar tu medición o «Más tarde» para posponer 15 minutos."

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(titleText)
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicNotification)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setContentIntent(registerNowPendingIntent)
            .addAction(
                0,
                "Registrar ahora",
                registerNowPendingIntent
            )
            .addAction(
                0,
                "Más tarde",
                snoozePendingIntent
            )

        return try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
            true
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun scheduleAlarmCompat(
        alarmManager: AlarmManager,
        triggerAtMs: Long,
        pendingIntent: PendingIntent
    ) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMs,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMs,
                    pendingIntent
                )
            }
        } catch (_: SecurityException) {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerAtMs,
                pendingIntent
            )
        }
    }
}
