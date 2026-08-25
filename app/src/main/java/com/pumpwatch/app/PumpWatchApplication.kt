package com.pumpwatch.app

import android.app.Application
import android.util.Log
import java.io.File

class PumpWatchApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            try {
                val log = Log.getStackTraceString(throwable)
                File(filesDir, "crash.log").writeText(log)
            } catch (_: Exception) { }
            throw throwable
        }
    }
}
