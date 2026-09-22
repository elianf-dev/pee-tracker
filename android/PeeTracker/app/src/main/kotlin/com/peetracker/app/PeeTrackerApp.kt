package com.peetracker.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.content.getSystemService
import com.peetracker.app.notifications.NotificationChannels
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PeeTrackerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            NotificationChannels.GROUP_ACTIVITY,
            "Group activity",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "New leaders, new members, streaks, and weekly results for your group"
        }
        getSystemService<NotificationManager>()?.createNotificationChannel(channel)
    }
}
