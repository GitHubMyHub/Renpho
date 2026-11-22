package com.omnitracker.renpho.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.omnitracker.renpho.domain.model.BodyComposition
import com.omnitracker.renpho.domain.model.Measurement
import com.omnitracker.renpho.domain.model.MeasurementSource
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Entity(tableName = "measurements")
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMillis: Long,
    val weightKg: Float,
    val impedanceOhm: Int?,
    val source: String,
    val bmi: Float,
    val bodyFatPercent: Float?,
    val visceralFatRating: Int? = null,
    val subcutaneousFatPercent: Float? = null,
    val fatFreeMassKg: Float?,
    val waterPercent: Float?,
    val muscleMassKg: Float?,
    val skeletalMusclePercent: Float?,
    val boneMassKg: Float?,
    val proteinPercent: Float?,
    val bmrKcal: Int?,
    val metabolicAge: Int?,
)

@Dao
interface MeasurementDao {
    @Query("SELECT * FROM measurements ORDER BY timestampMillis DESC")
    fun observeAll(): Flow<List<MeasurementEntity>>

    @Query("SELECT * FROM measurements WHERE id = :id")
    fun observe(id: Long): Flow<MeasurementEntity?>

    @Insert
    suspend fun insert(entity: MeasurementEntity): Long

    @Query("SELECT * FROM measurements")
    suspend fun getAll(): List<MeasurementEntity>

    @Update
    suspend fun update(entities: List<MeasurementEntity>)

    @Query("DELETE FROM measurements WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM measurements")
    suspend fun deleteAll()
}

@Database(entities = [MeasurementEntity::class], version = 3, exportSchema = false)
abstract class MeasurementDatabase : RoomDatabase() {
    abstract val measurementDao: MeasurementDao

    companion object {
        /** v2: visceral & subcutaneous fat columns (filled by MeasurementRecalculator). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE measurements ADD COLUMN visceralFatRating INTEGER")
                db.execSQL("ALTER TABLE measurements ADD COLUMN subcutaneousFatPercent REAL")
            }
        }

        /** v3: data-only skeletal muscle rescale (superseded by MeasurementRecalculator). */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "UPDATE measurements SET skeletalMusclePercent = skeletalMusclePercent * " +
                        "1.083 " +
                        "WHERE skeletalMusclePercent IS NOT NULL AND impedanceOhm IS NOT NULL",
                )
            }
        }
    }
}

fun MeasurementEntity.toMeasurement() = Measurement(
    id = id,
    timestamp = Instant.ofEpochMilli(timestampMillis),
    weightKg = weightKg,
    impedanceOhm = impedanceOhm,
    source = runCatching { MeasurementSource.valueOf(source) }.getOrDefault(MeasurementSource.MANUAL),
    composition = BodyComposition(
        bmi = bmi,
        bodyFatPercent = bodyFatPercent,
        visceralFatRating = visceralFatRating,
        subcutaneousFatPercent = subcutaneousFatPercent,
        fatFreeMassKg = fatFreeMassKg,
        waterPercent = waterPercent,
        muscleMassKg = muscleMassKg,
        skeletalMusclePercent = skeletalMusclePercent,
        boneMassKg = boneMassKg,
        proteinPercent = proteinPercent,
        bmrKcal = bmrKcal,
        metabolicAge = metabolicAge,
    ),
)

fun Measurement.toEntity() = MeasurementEntity(
    id = id,
    timestampMillis = timestamp.toEpochMilli(),
    weightKg = weightKg,
    impedanceOhm = impedanceOhm,
    source = source.name,
    bmi = composition.bmi,
    bodyFatPercent = composition.bodyFatPercent,
    visceralFatRating = composition.visceralFatRating,
    subcutaneousFatPercent = composition.subcutaneousFatPercent,
    fatFreeMassKg = composition.fatFreeMassKg,
    waterPercent = composition.waterPercent,
    muscleMassKg = composition.muscleMassKg,
    skeletalMusclePercent = composition.skeletalMusclePercent,
    boneMassKg = composition.boneMassKg,
    proteinPercent = composition.proteinPercent,
    bmrKcal = composition.bmrKcal,
    metabolicAge = composition.metabolicAge,
)
