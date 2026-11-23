package com.destex.app

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.Settings
import java.util.Locale

class KomutIsleyici(
    private val context: Context,
    private val ekranAc: (Intent) -> Unit,
) {
    private val kamera = context.getSystemService(CameraManager::class.java)
    private val ses = context.getSystemService(AudioManager::class.java)
    private val pil = context.getSystemService(BatteryManager::class.java)

    private val artirKelimeleri = listOf("artır", "yükselt", "aç", "çoğalt", "arttır", "yukarı")
    private val azaltKelimeleri = listOf("azalt", "kıs", "düşür", "karart", "alçalt", "aşağı")

    fun isle(metin: String): Pair<Olay, String>? {
        val m = metin.lowercase(Locale("tr", "TR"))
        return when {
            "fener" in m -> fener(!("kapat" in m || "söndür" in m))
            "pil" in m || "şarj" in m -> pilDurumu()
            "parlaklık" in m -> parlaklik(m)
            "wifi" in m || "wi-fi" in m || "kablosuz" in m -> wifi()
            m.startsWith("ses") || " ses" in m -> sesAyari(m)
            else -> null
        }
    }

    private fun fener(ac: Boolean): Pair<Olay, String> {
        return try {
            val kimlik = kamera.cameraIdList.firstOrNull {
                kamera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return Pair(Olay.Hata, "Bu cihazda fener bulunamadı")
            kamera.setTorchMode(kimlik, ac)
            Pair(Olay.Basari, if (ac) "Fener açıldı" else "Fener kapatıldı")
        } catch (e: Exception) {
            Pair(Olay.Hata, "Feneri çalıştıramadım")
        }
    }

    private fun sesAyari(m: String): Pair<Olay, String>? {
        val yon = when {
            azaltKelimeleri.any { it in m } -> AudioManager.ADJUST_LOWER
            artirKelimeleri.any { it in m } -> AudioManager.ADJUST_RAISE
            else -> return null
        }
        ses.adjustStreamVolume(AudioManager.STREAM_MUSIC, yon, AudioManager.FLAG_SHOW_UI)
        val yuzde = ses.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / ses.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return Pair(Olay.Basari, "Ses yüzde $yuzde")
    }

    private fun parlaklik(m: String): Pair<Olay, String> {
        if (!Settings.System.canWrite(context)) {
            ekranAc(
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return Pair(Olay.OnayGerekli, "Parlaklık için özel izin gerekiyor. İzin ekranını açtım, DesteX'e izin ver")
        }
        val kaynak = context.contentResolver
        val simdi = Settings.System.getInt(kaynak, Settings.System.SCREEN_BRIGHTNESS, 128)
        val yeni = when {
            azaltKelimeleri.any { it in m } -> (simdi - 40).coerceAtLeast(10)
            artirKelimeleri.any { it in m } || "parlat" in m -> (simdi + 40).coerceAtMost(255)
            else -> return Pair(Olay.Hata, "Parlaklığı artır mı azalt mı, anlayamadım")
        }
        Settings.System.putInt(kaynak, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(kaynak, Settings.System.SCREEN_BRIGHTNESS, yeni)
        return Pair(Olay.Basari, "Parlaklık yüzde ${yeni * 100 / 255}")
    }

    private fun pilDurumu(): Pair<Olay, String> {
        val yuzde = pil.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val durum = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val sarj = durum == BatteryManager.BATTERY_STATUS_CHARGING || durum == BatteryManager.BATTERY_STATUS_FULL
        return Pair(Olay.Basari, "Pil yüzde $yuzde" + if (sarj) ", şarj oluyor" else "")
    }

    private fun wifi(): Pair<Olay, String> {
        ekranAc(Intent(Settings.Panel.ACTION_WIFI).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return Pair(Olay.Basari, "Wi-Fi panelini açtım. Android, uygulamaların Wi-Fi'ı doğrudan açıp kapatmasına izin vermiyor")
    }
}
