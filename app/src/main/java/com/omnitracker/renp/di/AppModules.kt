package com.omnitracker.renp.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.omnitracker.renp.MainViewModel
import com.omnitracker.renp.data.ble.AndroidBleScaleManager
import com.omnitracker.renp.data.local.DataStoreUserPreferences
import com.omnitracker.renp.data.local.MeasurementDatabase
import com.omnitracker.renp.data.local.MeasurementRecalculator
import com.omnitracker.renp.data.local.RoomMeasurementDataSource
import com.omnitracker.renp.domain.MeasurementDataSource
import com.omnitracker.renp.domain.UserPreferences
import com.omnitracker.renp.domain.scale.ScaleManager
import com.omnitracker.renp.feature.addentry.AddEntryViewModel
import com.omnitracker.renp.feature.dashboard.DashboardViewModel
import com.omnitracker.renp.feature.history.HistoryViewModel
import com.omnitracker.renp.feature.history.MeasurementDetailViewModel
import com.omnitracker.renp.feature.measure.MeasureViewModel
import com.omnitracker.renp.feature.profile.ProfileViewModel
import com.omnitracker.renp.feature.settings.SettingsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

private val Context.userDataStore by preferencesDataStore(name = "user_preferences")

val dataModule = module {
    single {
        Room.databaseBuilder(androidContext(), MeasurementDatabase::class.java, "measurements.db")
            .addMigrations(MeasurementDatabase.MIGRATION_1_2, MeasurementDatabase.MIGRATION_2_3)
            .build()
    }
    singleOf(::MeasurementRecalculator)
    single { get<MeasurementDatabase>().measurementDao }
    single<UserPreferences> { DataStoreUserPreferences(androidContext().userDataStore) }
    singleOf(::RoomMeasurementDataSource) bind MeasurementDataSource::class
    singleOf(::AndroidBleScaleManager) bind ScaleManager::class
}

val presentationModule = module {
    viewModelOf(::MainViewModel)
    viewModelOf(::ProfileViewModel)
    viewModelOf(::DashboardViewModel)
    viewModelOf(::MeasureViewModel)
    viewModelOf(::HistoryViewModel)
    viewModelOf(::MeasurementDetailViewModel)
    viewModelOf(::AddEntryViewModel)
    viewModelOf(::SettingsViewModel)
}
