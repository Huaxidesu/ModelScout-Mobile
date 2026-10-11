package com.modelscout.mobile

import android.app.Application
import com.modelscout.mobile.di.AppContainer

class ModelScoutApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
