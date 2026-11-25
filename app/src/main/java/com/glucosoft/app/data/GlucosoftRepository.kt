package com.glucosoft.app.data

import android.content.Context
import android.content.SharedPreferences
import com.glucosoft.app.model.GlucoseReading
import com.glucosoft.app.model.LocalProfile
import com.glucosoft.app.model.TextScaleMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GlucosoftRepository(
    private val dao: GlucosoftDao,
    context: Context
) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("glucosoft_prefs_v2", Context.MODE_PRIVATE)

    val profilesFlow: Flow<List<LocalProfile>> =
        dao.observeProfiles().map { list -> list.map { it.toDomain() } }

    val readingsFlow: Flow<List<GlucoseReading>> =
        dao.observeReadings().map { list -> list.map { it.toDomain() } }

    val remindersFlow: Flow<List<com.glucosoft.app.model.ProfileReminderConfig>> =
        dao.observeReminders().map { list -> list.map { it.toDomain() } }

    suspend fun saveProfile(profile: LocalProfile) {
        dao.insertProfile(ProfileEntity.fromDomain(profile))
    }

    suspend fun saveBatchProfiles(profiles: List<LocalProfile>) {
        dao.insertProfiles(profiles.map { ProfileEntity.fromDomain(it) })
    }

    suspend fun deleteProfileAndReadings(profileId: String) {
        dao.deleteReadingsForProfile(profileId)
        dao.deleteReminderForProfile(profileId)
        dao.deleteProfileById(profileId)
    }

    suspend fun saveReading(reading: GlucoseReading) {
        dao.insertReading(ReadingEntity.fromDomain(reading))
    }

    suspend fun saveBatchReadings(readings: List<GlucoseReading>) {
        dao.insertReadings(readings.map { ReadingEntity.fromDomain(it) })
    }

    suspend fun deleteReading(readingId: String) {
        dao.deleteReadingById(readingId)
    }

    suspend fun saveReminder(config: com.glucosoft.app.model.ProfileReminderConfig) {
        dao.insertReminder(ReminderEntity.fromDomain(config))
    }

    suspend fun saveBatchReminders(configs: List<com.glucosoft.app.model.ProfileReminderConfig>) {
        dao.insertReminders(configs.map { ReminderEntity.fromDomain(it) })
    }

    suspend fun getReminderForProfile(profileId: String): com.glucosoft.app.model.ProfileReminderConfig? {
        return dao.getReminderForProfile(profileId)?.toDomain()
    }

    suspend fun getAllProfilesSnapshot(): List<LocalProfile> =
        dao.getAllProfilesSnapshot().map { it.toDomain() }

    suspend fun getAllReadingsSnapshot(): List<GlucoseReading> =
        dao.getAllReadingsSnapshot().map { it.toDomain() }

    suspend fun getAllRemindersSnapshot(): List<com.glucosoft.app.model.ProfileReminderConfig> =
        dao.getAllRemindersSnapshot().map { it.toDomain() }

    fun getActiveProfileId(): String =
        prefs.getString(KEY_ACTIVE_PROFILE_ID, "") ?: ""

    fun setActiveProfileId(id: String) {
        prefs.edit().putString(KEY_ACTIVE_PROFILE_ID, id).apply()
    }

    fun isDarkTheme(): Boolean =
        prefs.getBoolean(KEY_DARK_THEME, false)

    fun setDarkTheme(isDark: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_THEME, isDark).apply()
    }

    fun getTextScaleMode(): TextScaleMode {
        val raw = prefs.getString(KEY_TEXT_SCALE, "normal")
        return if (raw == "large") TextScaleMode.LARGE else TextScaleMode.NORMAL
    }

    fun setTextScaleMode(mode: TextScaleMode) {
        val raw = if (mode == TextScaleMode.LARGE) "large" else "normal"
        prefs.edit().putString(KEY_TEXT_SCALE, raw).apply()
    }

    fun isOnboardingDone(): Boolean =
        prefs.getBoolean(KEY_ONBOARDING_DONE, false)

    fun setOnboardingDone(done: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, done).apply()
    }

    companion object {
        private const val KEY_ACTIVE_PROFILE_ID = "glucosoft_active_profile_v1"
        private const val KEY_DARK_THEME = "glucosoft_theme_v1"
        private const val KEY_TEXT_SCALE = "glucosoft_text_scale_v1"
        private const val KEY_ONBOARDING_DONE = "glucosoft_onboarding_done_v1"
    }
}
