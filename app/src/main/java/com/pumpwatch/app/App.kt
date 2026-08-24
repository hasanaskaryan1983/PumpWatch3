package com.pumpwatch.app

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val crashFile = java.io.File(filesDir, "crash.log")
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            try {
                crashFile.writeText(e.stackTraceToString())
            } catch (_: Exception) { }
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }
}
