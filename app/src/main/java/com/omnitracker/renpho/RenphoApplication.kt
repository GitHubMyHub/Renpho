package com.omnitracker.renpho

import android.app.Application
import com.omnitracker.renpho.di.dataModule
import com.omnitracker.renpho.di.presentationModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class RenphoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@RenphoApplication)
            modules(dataModule, presentationModule)
        }
    }
}
