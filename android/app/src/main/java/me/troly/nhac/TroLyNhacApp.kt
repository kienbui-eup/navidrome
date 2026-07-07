package me.troly.nhac

import android.app.Application

class TroLyNhacApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Global init (DI, settings store, crash reporting) goes here later.
    }
}
