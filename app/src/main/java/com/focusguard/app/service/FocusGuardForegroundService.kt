package com.focusguard.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.focusguard.app.R
import com.focusguard.app.ui.MainActivity

/**
 * FocusGuardForegroundService
 *
 * A lightweight foreground service that keeps FocusGuard alive in the background.
 * This prevents the OS from killing the accessibility service on low-memory devices.
 *
 * The service itself does no work — all monitoring is event-driven inside the
 * AccessibilityService. This service just holds a foreground notification.
 */
class FocusGuardForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "focusguard_channel"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, FocusGuardForegroundService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, FocusGuardForegroundService::class.java)
            context.stopService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY ensures the service restarts if killed by the OS
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    // ─── Notification ─────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "FocusGuard Active",
            NotificationManager.IMPORTANCE_LOW  // Low importance = no sound/heads-up
        ).apply {
            description = "FocusGuard is monitoring for distractions"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FocusGuard Active")
            .setContentText("Blocking Instagram distractions")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
