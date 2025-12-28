package com.adwaizer.promptforge

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PromptForgeApp : Application() {

    companion object {
        const val CHANNEL_ID_SERVICE = "prompt_forge_service"
        const val CHANNEL_ID_ENHANCEMENT = "prompt_forge_enhancement"
        const val NOTIFICATION_ID_SERVICE = 1001
        const val NOTIFICATION_ID_RESULT = 1002
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // Service channel (low priority, persistent)
            val serviceChannel = NotificationChannel(
                CHANNEL_ID_SERVICE,
                "PromptForge Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the AI model ready for instant enhancement"
                setShowBadge(false)
            }

            // Enhancement results channel
            val enhancementChannel = NotificationChannel(
                CHANNEL_ID_ENHANCEMENT,
                "Enhancement Results",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Shows enhanced prompts ready to copy"
                setShowBadge(true)
            }

            notificationManager.createNotificationChannel(serviceChannel)
            notificationManager.createNotificationChannel(enhancementChannel)
        }
    }
}
