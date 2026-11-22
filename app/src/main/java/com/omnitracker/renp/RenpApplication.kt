package com.omnitracker.renp

import android.app.Application
import com.omnitracker.renp.di.dataModule
import com.omnitracker.renp.di.presentationModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class RenpApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@RenpApplication)
            modules(dataModule, presentationModule)
        }
    }
}
