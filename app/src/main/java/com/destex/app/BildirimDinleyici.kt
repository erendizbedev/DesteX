package com.destex.app

import android.app.Notification
import android.content.pm.ApplicationInfo
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class BildirimDinleyici : NotificationListenerService() {
    private lateinit var geri: Geribildirim

    override fun onCreate() {
        super.onCreate()
        geri = Geribildirim(applicationContext)
        ornek = this
    }

    override fun onDestroy() {
        ornek = null
        geri.kapat()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!Ayar.otomatikBildirim(this)) return
        if (Durum.dinliyor) return
        val cumle = cumle(sbn) ?: return
        geri.bildir(Olay.Basari, cumle)
    }

    private fun cumle(sbn: StatusBarNotification): String? {
        if (sbn.packageName == packageName) return null
        if (sbn.isOngoing) return null
        val bildirim = sbn.notification
        if (bildirim.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        val ekler = bildirim.extras
        val baslik = ekler.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val metin = (ekler.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: ekler.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (baslik.isBlank() && metin.isBlank()) return null
        @Suppress("DEPRECATION")
        val bilgi = ekler.getParcelable<ApplicationInfo>("android.appInfo")
        val uygulama = bilgi?.loadLabel(packageManager)?.toString() ?: sbn.packageName
        return "$uygulama: $baslik. $metin"
    }

    fun aktifOzet(): List<String> =
        try {
            activeNotifications.mapNotNull { cumle(it) }.take(5)
        } catch (e: Exception) {
            emptyList()
        }

    companion object {
        var ornek: BildirimDinleyici? = null
    }
}
