package com.zenith.tuner

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class ZenithApplication : Application() {

    companion object {
        const val CHANNEL_ID = "zenith_dsp_channel"
        const val CHANNEL_NAME = "Zenith Acoustic Engine"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Zero-latency acoustic correction service for boAt Nirvana Zenith"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
