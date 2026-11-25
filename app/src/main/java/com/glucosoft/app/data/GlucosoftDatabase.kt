package com.glucosoft.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import com.glucosoft.app.model.BiomedicalDeviceId
import com.glucosoft.app.model.CaptureMethod
import com.glucosoft.app.model.EmergencyContact
import com.glucosoft.app.model.GlucoseReading
import com.glucosoft.app.model.GlucoseUnit
import com.glucosoft.app.model.LocalProfile
import com.glucosoft.app.model.MeasurementContext
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "local_profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val unitPreference: String,
    val targetMinMgDl: Int,
    val targetMaxMgDl: Int,
    val accentHex: String,
    val createdAt: Long,
    val updatedAt: Long?,
    val emergencyName: String?,
    val emergencyRelation: String?,
    val emergencyPhone: String?
) {
    fun toDomain(): LocalProfile {
        val contact = if (!emergencyName.isNullOrBlank() && !emergencyPhone.isNullOrBlank()) {
            EmergencyContact(
                name = emergencyName,
                relation = emergencyRelation ?: "Familiar / Confianza",
                phone = emergencyPhone
            )
        } else {
            null
        }
        return LocalProfile(
            id = id,
            name = name,
            unitPreference = GlucoseUnit.fromSymbol(unitPreference),
            targetMinMgDl = targetMinMgDl,
            targetMaxMgDl = targetMaxMgDl,
            accentHex = accentHex,
            createdAt = createdAt,
            updatedAt = updatedAt,
            emergencyContact = contact
        )
    }

    companion object {
        fun fromDomain(profile: LocalProfile): ProfileEntity = ProfileEntity(
            id = profile.id,
            name = profile.name,
            unitPreference = profile.unitPreference.symbol,
            targetMinMgDl = profile.targetMinMgDl,
            targetMaxMgDl = profile.targetMaxMgDl,
            accentHex = profile.accentHex,
            createdAt = profile.createdAt,
            updatedAt = profile.updatedAt,
            emergencyName = profile.emergencyContact?.name,
            emergencyRelation = profile.emergencyContact?.relation,
            emergencyPhone = profile.emergencyContact?.phone
        )
    }
}

@Entity(tableName = "glucose_readings")
data class ReadingEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val valueMgDl: Int,
    val originalUnit: String,
    val timestamp: Long,
    val context: String,
    val quickTagsJoined: String,
    val note: String,
    val captureMethod: String,
    val deviceModel: String,
    val deviceId: String,
    val deviceInfoSummary: String?,
    val photoPath: String? = null
) {
    fun toDomain(): GlucoseReading = GlucoseReading(
        id = id,
        profileId = profileId,
        valueMgDl = valueMgDl,
        originalUnit = GlucoseUnit.fromSymbol(originalUnit),
        timestamp = timestamp,
        context = MeasurementContext.fromRaw(context),
        quickTags = if (quickTagsJoined.isBlank()) emptyList() else quickTagsJoined.split("||"),
        note = note,
        captureMethod = CaptureMethod.fromRaw(captureMethod),
        deviceModel = deviceModel,
        deviceId = BiomedicalDeviceId.fromRaw(deviceId),
        deviceInfoSummary = deviceInfoSummary,
        photoPath = photoPath?.takeIf { it.isNotBlank() }
    )

    companion object {
        fun fromDomain(reading: GlucoseReading): ReadingEntity = ReadingEntity(
            id = reading.id,
            profileId = reading.profileId,
            valueMgDl = reading.valueMgDl,
            originalUnit = reading.originalUnit.symbol,
            timestamp = reading.timestamp,
            context = reading.context.rawId,
            quickTagsJoined = reading.quickTags.joinToString("||"),
            note = reading.note,
            captureMethod = reading.captureMethod.rawId,
            deviceModel = reading.deviceModel,
            deviceId = reading.deviceId.rawId,
            deviceInfoSummary = reading.deviceInfoSummary,
            photoPath = reading.photoPath?.takeIf { it.isNotBlank() }
        )
    }
}

@Entity(tableName = "profile_reminders")
data class ReminderEntity(
    @PrimaryKey val profileId: String,
    val enabled: Boolean,
    val hour: Int,
    val minute: Int,
    val snoozedUntilMs: Long?
) {
    fun toDomain(): com.glucosoft.app.model.ProfileReminderConfig =
        com.glucosoft.app.model.ProfileReminderConfig(
            profileId = profileId,
            enabled = enabled,
            hour = hour.coerceIn(0, 23),
            minute = minute.coerceIn(0, 59),
            snoozedUntilMs = snoozedUntilMs
        )

    companion object {
        fun fromDomain(config: com.glucosoft.app.model.ProfileReminderConfig): ReminderEntity =
            ReminderEntity(
                profileId = config.profileId,
                enabled = config.enabled,
                hour = config.hour.coerceIn(0, 23),
                minute = config.minute.coerceIn(0, 59),
                snoozedUntilMs = config.snoozedUntilMs
            )
    }
}

@Dao
interface GlucosoftDao {
    @Query("SELECT * FROM local_profiles ORDER BY createdAt ASC")
    fun observeProfiles(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM local_profiles ORDER BY createdAt ASC")
    suspend fun getAllProfilesSnapshot(): List<ProfileEntity>

    @Query("SELECT * FROM glucose_readings ORDER BY timestamp DESC")
    fun observeReadings(): Flow<List<ReadingEntity>>

    @Query("SELECT * FROM glucose_readings ORDER BY timestamp DESC")
    suspend fun getAllReadingsSnapshot(): List<ReadingEntity>

    @Query("SELECT * FROM profile_reminders")
    fun observeReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM profile_reminders")
    suspend fun getAllRemindersSnapshot(): List<ReminderEntity>

    @Query("SELECT * FROM profile_reminders WHERE profileId = :profileId LIMIT 1")
    suspend fun getReminderForProfile(profileId: String): ReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ReminderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminders(reminders: List<ReminderEntity>)

    @Query("DELETE FROM profile_reminders WHERE profileId = :profileId")
    suspend fun deleteReminderForProfile(profileId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfiles(profiles: List<ProfileEntity>)

    @Update
    suspend fun updateProfile(profile: ProfileEntity)

    @Query("DELETE FROM local_profiles WHERE id = :profileId")
    suspend fun deleteProfileById(profileId: String)

    @Query("DELETE FROM glucose_readings WHERE profileId = :profileId")
    suspend fun deleteReadingsForProfile(profileId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReading(reading: ReadingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReadings(readings: List<ReadingEntity>)

    @Query("DELETE FROM glucose_readings WHERE id = :readingId")
    suspend fun deleteReadingById(readingId: String)
}

@Database(
    entities = [ProfileEntity::class, ReadingEntity::class, ReminderEntity::class],
    version = 2,
    exportSchema = false
)
abstract class GlucosoftDatabase : RoomDatabase() {
    abstract fun glucosoftDao(): GlucosoftDao

    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE glucose_readings ADD COLUMN photoPath TEXT")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `profile_reminders` (
                        `profileId` TEXT NOT NULL,
                        `enabled` INTEGER NOT NULL,
                        `hour` INTEGER NOT NULL,
                        `minute` INTEGER NOT NULL,
                        `snoozedUntilMs` INTEGER,
                        PRIMARY KEY(`profileId`)
                    )
                    """.trimIndent()
                )
            }
        }

        @Volatile
        private var INSTANCE: GlucosoftDatabase? = null

        fun getInstance(context: Context): GlucosoftDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GlucosoftDatabase::class.java,
                    "glucosoft_local_v2.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
