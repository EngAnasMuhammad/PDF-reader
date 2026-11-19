package com.anasdev.pdfreader.di

import android.app.ActivityManager
import android.app.Application
import com.github.barteksc.pdfviewer.util.Constants
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PDFApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        configurePdfTileCache()
    }

    private fun configurePdfTileCache() {
        val activityManager = getSystemService(ActivityManager::class.java) ?: return
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val totalRamGb = memInfo.totalMem.toDouble() / (1024.0 * 1024.0 * 1024.0)

        Constants.Cache.CACHE_SIZE = when {
            activityManager.isLowRamDevice || totalRamGb <= 4.5 -> 60
            totalRamGb <= 6.5 -> 100
            else -> 150
        }
    }
}
