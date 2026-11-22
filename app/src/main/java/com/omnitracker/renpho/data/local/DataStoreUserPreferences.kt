package com.omnitracker.renpho.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.omnitracker.renpho.domain.UserPreferences
import com.omnitracker.renpho.domain.model.AppSettings
import com.omnitracker.renpho.domain.model.HeightUnit
import com.omnitracker.renpho.domain.model.PairedScale
import com.omnitracker.renpho.domain.model.Sex
import com.omnitracker.renpho.domain.model.UserProfile
import com.omnitracker.renpho.domain.model.WeightUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class DataStoreUserPreferences(private val dataStore: DataStore<Preferences>) : UserPreferences {

    override val profile: Flow<UserProfile?> = dataStore.data.map { prefs ->
        val birthDay = prefs[BIRTH_EPOCH_DAY] ?: return@map null
        val height = prefs[HEIGHT_CM] ?: return@map null
        UserProfile(
            name = prefs[NAME].orEmpty(),
            sex = prefs[SEX].toEnum(Sex.MALE),
            birthDate = LocalDate.ofEpochDay(birthDay),
            heightCm = height,
            goalWeightKg = prefs[GOAL_WEIGHT_KG],
        )
    }.distinctUntilChanged()

    override val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        val address = prefs[SCALE_ADDRESS]
        AppSettings(
            weightUnit = prefs[WEIGHT_UNIT].toEnum(WeightUnit.KG),
            heightUnit = prefs[HEIGHT_UNIT].toEnum(HeightUnit.CM),
            pairedScale = address?.let { PairedScale(it, prefs[SCALE_NAME] ?: it) },
        )
    }.distinctUntilChanged()

    override suspend fun saveProfile(profile: UserProfile) {
        dataStore.edit { prefs ->
            prefs[NAME] = profile.name
            prefs[SEX] = profile.sex.name
            prefs[BIRTH_EPOCH_DAY] = profile.birthDate.toEpochDay()
            prefs[HEIGHT_CM] = profile.heightCm
            if (profile.goalWeightKg != null) prefs[GOAL_WEIGHT_KG] = profile.goalWeightKg
            else prefs.remove(GOAL_WEIGHT_KG)
        }
    }

    override suspend fun setWeightUnit(unit: WeightUnit) {
        dataStore.edit { it[WEIGHT_UNIT] = unit.name }
    }

    override suspend fun setHeightUnit(unit: HeightUnit) {
        dataStore.edit { it[HEIGHT_UNIT] = unit.name }
    }

    override suspend fun setGoalWeight(goalWeightKg: Float?) {
        dataStore.edit { prefs ->
            if (goalWeightKg != null) prefs[GOAL_WEIGHT_KG] = goalWeightKg else prefs.remove(GOAL_WEIGHT_KG)
        }
    }

    override suspend fun setPairedScale(scale: PairedScale?) {
        dataStore.edit { prefs ->
            if (scale != null) {
                prefs[SCALE_ADDRESS] = scale.address
                prefs[SCALE_NAME] = scale.name
            } else {
                prefs.remove(SCALE_ADDRESS)
                prefs.remove(SCALE_NAME)
            }
        }
    }

    override suspend fun getCalcVersion(): Int = dataStore.data.first()[CALC_VERSION] ?: 1

    override suspend fun setCalcVersion(version: Int) {
        dataStore.edit { it[CALC_VERSION] = version }
    }

    private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T =
        this?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

    private companion object {
        val NAME = stringPreferencesKey("name")
        val SEX = stringPreferencesKey("sex")
        val BIRTH_EPOCH_DAY = longPreferencesKey("birth_epoch_day")
        val HEIGHT_CM = floatPreferencesKey("height_cm")
        val GOAL_WEIGHT_KG = floatPreferencesKey("goal_weight_kg")
        val WEIGHT_UNIT = stringPreferencesKey("weight_unit")
        val HEIGHT_UNIT = stringPreferencesKey("height_unit")
        val SCALE_ADDRESS = stringPreferencesKey("scale_address")
        val SCALE_NAME = stringPreferencesKey("scale_name")
        val CALC_VERSION = intPreferencesKey("calc_version")
    }
}
