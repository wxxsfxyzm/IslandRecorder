package com.island.recorder

import android.app.Application
import com.island.recorder.di.coreModule
import com.island.recorder.di.settingsModule
import com.island.recorder.di.viewModelModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.lsposed.hiddenapibypass.HiddenApiBypass
import timber.log.Timber

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        HiddenApiBypass.addHiddenApiExemptions("")

        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())

        startKoin {
            // Koin Android Logger
            androidLogger()
            // Koin Android Context
            androidContext(this@App)
            // use modules
            modules(coreModule, settingsModule, viewModelModule)
        }
    }
}
