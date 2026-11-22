package com.omnitracker.renp.domain

import com.omnitracker.renp.domain.model.AppSettings
import com.omnitracker.renp.domain.model.HeightUnit
import com.omnitracker.renp.domain.model.Measurement
import com.omnitracker.renp.domain.model.PairedScale
import com.omnitracker.renp.domain.model.UserProfile
import com.omnitracker.renp.domain.model.WeightUnit
import kotlinx.coroutines.flow.Flow

interface MeasurementDataSource {
    /** All measurements, newest first. */
    fun observeAll(): Flow<List<Measurement>>
    fun observe(id: Long): Flow<Measurement?>
    suspend fun insert(measurement: Measurement): Long
    suspend fun delete(id: Long)
    suspend fun deleteAll()
}

interface UserPreferences {
    /** Emits null until the user completed onboarding. */
    val profile: Flow<UserProfile?>
    val settings: Flow<AppSettings>

    suspend fun saveProfile(profile: UserProfile)
    suspend fun setWeightUnit(unit: WeightUnit)
    suspend fun setHeightUnit(unit: HeightUnit)
    suspend fun setGoalWeight(goalWeightKg: Float?)
    suspend fun setPairedScale(scale: PairedScale?)

    /** Formula version stored measurements were last calculated with. */
    suspend fun getCalcVersion(): Int
    suspend fun setCalcVersion(version: Int)
}
