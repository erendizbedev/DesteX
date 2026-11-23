package com.destex.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder

/** "DesteX" diye seslenmeyi dinlemek için arka planda çalışan servis. Sesli bildirim vermez. */
class UyandirmaServisi : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val yonetici = getSystemService(NotificationManager::class.java)
        yonetici.createNotificationChannel(
            NotificationChannel(KANAL, "DesteX dinleme", NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                enableVibration(false)
            },
        )
        val tikla = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val bildirim = Notification.Builder(this, KANAL)
            .setContentTitle("DesteX seni dinliyor")
            .setContentText("DesteX diye seslen")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(tikla)
            .setOngoing(true)
            .build()
        startForeground(1, bildirim, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        Asistan.baslat(applicationContext)
        Asistan.uyanmaBaslat()
        return START_STICKY
    }

    override fun onDestroy() {
        Asistan.uyanmaDurdur()
        super.onDestroy()
    }

    companion object {
        const val KANAL = "destex_dinleme"
    }
}
