package com.omnitracker.renpho.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.omnitracker.renpho.MainViewModel
import com.omnitracker.renpho.data.ble.AndroidBleScaleManager
import com.omnitracker.renpho.data.local.DataStoreUserPreferences
import com.omnitracker.renpho.data.local.MeasurementDatabase
import com.omnitracker.renpho.data.local.MeasurementRecalculator
import com.omnitracker.renpho.data.local.RoomMeasurementDataSource
import com.omnitracker.renpho.domain.MeasurementDataSource
import com.omnitracker.renpho.domain.UserPreferences
import com.omnitracker.renpho.domain.scale.ScaleManager
import com.omnitracker.renpho.feature.addentry.AddEntryViewModel
import com.omnitracker.renpho.feature.dashboard.DashboardViewModel
import com.omnitracker.renpho.feature.history.HistoryViewModel
import com.omnitracker.renpho.feature.history.MeasurementDetailViewModel
import com.omnitracker.renpho.feature.measure.MeasureViewModel
import com.omnitracker.renpho.feature.profile.ProfileViewModel
import com.omnitracker.renpho.feature.settings.SettingsViewModel
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
