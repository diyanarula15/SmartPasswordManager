package com.example.group4app

import android.app.Application
import com.example.group4app.di.AppContainer

class LegenDDRRYApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
