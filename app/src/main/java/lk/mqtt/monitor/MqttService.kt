package lk.mqtt.monitor

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import androidx.core.app.NotificationCompat

/**
 * Foreground service: keeps the MQTT connection alive while the app is in the
 * background or the screen is off, and shows the last message in the notification.
 * The notification's "Disconnect & Exit" button closes the connection and the whole app.
 */
class MqttService : Service(), MqttManager.Listener {

    companion object {
        const val CHANNEL = "mqtt_status"
        const val NOTIF_ID = 1
        const val ACTION_EXIT = "lk.mqtt.monitor.EXIT"
        @Volatile var running = false
    }

    private var statusLine = "Connecting…"
    private var lastMsg = ""
    private var exiting = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val ch = NotificationChannel(CHANNEL, "MQTT connection", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        MqttManager.addListener(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_EXIT) {
            exitApp()
            return START_NOT_STICKY
        }
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, n)
        }
        running = true
        MqttManager.connect(this)
        return START_STICKY
    }

    /** Disconnect, remove the notification, close every app screen and end the app. */
    private fun exitApp() {
        exiting = true
        running = false
        MqttManager.removeListener(this)
        Prefs(this).autoConnect = false
        MqttManager.disconnect()

        stopForeground(STOP_FOREGROUND_REMOVE)
        getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)

        try {
            getSystemService(ActivityManager::class.java).appTasks.forEach { it.finishAndRemoveTask() }
        } catch (e: Exception) { }

        stopSelf()
        // Give the MQTT client a moment to say goodbye to the broker, then end the process.
        Handler(Looper.getMainLooper()).postDelayed({ Process.killProcess(Process.myPid()) }, 1200)
    }

    override fun onDestroy() {
        running = false
        MqttManager.removeListener(this)
        if (!exiting) MqttManager.disconnect()
        getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
        super.onDestroy()
    }

    override fun onStatus(text: String, connected: Boolean) {
        statusLine = text
        refresh()
    }

    override fun onMessage(m: Msg) {
        lastMsg = "${m.topic}: ${m.payload.take(80)}"
        refresh()
    }

    private fun refresh() {
        if (!running || exiting) return
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val exit = PendingIntent.getService(
            this, 1,
            Intent(this, MqttService::class.java).setAction(ACTION_EXIT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val text = if (lastMsg.isNotEmpty()) lastMsg else statusLine
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("MQTT Monitor · $statusLine")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .addAction(0, "Disconnect & Exit", exit)
            .build()
    }
}
