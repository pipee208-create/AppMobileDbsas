package com.glucosoft.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.glucosoft.app.data.GlucosoftDatabase
import com.glucosoft.app.data.ReminderEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receptor de alarmas locales y eventos del sistema (`BOOT_COMPLETED`, cambio de hora o zona horaria)
 * para mostrar, posponer («Más tarde», +15 min) o reprogramar los recordatorios diarios por perfil.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        scope.launch {
            try {
                val db = GlucosoftDatabase.getInstance(context)
                val dao = db.glucosoftDao()
                val profilesById = dao.getAllProfilesSnapshot().associate { it.id to it.name }

                when (action) {
                    ReminderManager.ACTION_TRIGGER_DAILY_REMINDER,
                    ReminderManager.ACTION_TRIGGER_SNOOZE_REMINDER -> {
                        val profileId = intent.getStringExtra(ReminderManager.EXTRA_REMINDER_PROFILE_ID)
                            ?.takeIf { it.isNotBlank() } ?: return@launch
                        val reminderEntity = dao.getReminderForProfile(profileId) ?: return@launch
                        val config = reminderEntity.toDomain()
                        if (!config.enabled) {
                            ReminderManager.cancelProfileReminder(context, profileId)
                            return@launch
                        }
                        val profileName = profilesById[profileId]
                            ?: intent.getStringExtra(ReminderManager.EXTRA_REMINDER_PROFILE_NAME)
                            ?: "Paciente"

                        // Limpiar snoozedUntilMs si ya se disparó y reprogramar el aviso del día siguiente
                        val updatedConfig = config.copy(snoozedUntilMs = null)
                        dao.insertReminder(ReminderEntity.fromDomain(updatedConfig))
                        ReminderManager.syncProfileReminderSchedule(
                            context = context,
                            config = updatedConfig,
                            profileName = profileName
                        )
                        ReminderManager.showReminderNotification(
                            context = context,
                            profileId = profileId,
                            profileName = profileName
                        )
                    }

                    ReminderManager.ACTION_SNOOZE_REMINDER -> {
                        val profileId = intent.getStringExtra(ReminderManager.EXTRA_REMINDER_PROFILE_ID)
                            ?.takeIf { it.isNotBlank() } ?: return@launch
                        val profileName = profilesById[profileId]
                            ?: intent.getStringExtra(ReminderManager.EXTRA_REMINDER_PROFILE_NAME)
                            ?: "Paciente"

                        val nowMs = System.currentTimeMillis()
                        val snoozeAtMs = ReminderManager.computeSnoozeTriggerAtMs(nowMs)
                        val existing = dao.getReminderForProfile(profileId)?.toDomain()
                        if (existing != null) {
                            dao.insertReminder(
                                ReminderEntity.fromDomain(existing.copy(snoozedUntilMs = snoozeAtMs))
                            )
                        }
                        ReminderManager.dismissNotificationForProfile(context, profileId)
                        ReminderManager.scheduleSnoozeAlarm(
                            context = context,
                            profileId = profileId,
                            profileName = profileName,
                            snoozeTriggerAtMs = snoozeAtMs
                        )
                    }

                    Intent.ACTION_BOOT_COMPLETED,
                    Intent.ACTION_TIME_CHANGED,
                    Intent.ACTION_TIMEZONE_CHANGED,
                    Intent.ACTION_MY_PACKAGE_REPLACED -> {
                        val reminders = dao.getAllRemindersSnapshot().map { it.toDomain() }
                        val nowMs = System.currentTimeMillis()
                        for (config in reminders) {
                            val profileName = profilesById[config.profileId]
                            if (profileName != null && config.enabled) {
                                ReminderManager.syncProfileReminderSchedule(
                                    context = context,
                                    config = config,
                                    profileName = profileName,
                                    nowMs = nowMs
                                )
                            } else {
                                ReminderManager.cancelProfileReminder(context, config.profileId)
                            }
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
