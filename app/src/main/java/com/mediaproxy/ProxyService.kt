package com.mediaproxy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log

class ProxyService : Service() {

    companion object {
        const val TAG = "MediaProxy"
        const val CHANNEL_ID = "media_proxy_channel"
        const val NOTIFICATION_ID = 1
        const val EXTRA_SOURCES_NAMES = "sources_names"
        const val EXTRA_SOURCES_URLS = "sources_urls"
        const val EXTRA_PORT = "port"

        var isRunning = false
            private set
    }

    private var server: WebDAVServer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val names = intent?.getStringArrayExtra(EXTRA_SOURCES_NAMES) ?: arrayOf("TV Series")
        val urls = intent?.getStringArrayExtra(EXTRA_SOURCES_URLS) ?: arrayOf("http://172.16.172.166:8087")
        val port = intent?.getIntExtra(EXTRA_PORT, 8088) ?: 8088

        val sources = names.zip(urls).map { WebDAVServer.Source(it.first, it.second) }
        val sourceList = sources.joinToString(", ") { it.name }

        val notification = buildNotification("Serving: $sourceList")
        startForeground(NOTIFICATION_ID, notification)

        try {
            server?.stop()
            server = WebDAVServer(port, sources, this)
            server?.start()
            server?.prefetchSources()
            isRunning = true
            Log.i(TAG, "WebDAV proxy started on port $port with ${sources.size} source(s)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start server", e)
            isRunning = false
            stopSelf()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        isRunning = false
        Log.i(TAG, "WebDAV proxy stopped")
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Media Proxy",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "WebDAV proxy service"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Media Proxy")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Media Proxy")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        }
    }
}
