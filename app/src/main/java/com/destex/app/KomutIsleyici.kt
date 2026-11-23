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

    private fun sade(s: String) = s.lowercase(Locale("tr", "TR"))
        .replace('ğ', 'g').replace('ı', 'i').replace('ş', 's')
        .replace('ö', 'o').replace('ü', 'u').replace('ç', 'c')

    private fun artir(t: List<String>) = t.any {
        it.startsWith("artir") || it.startsWith("arttir") || it.startsWith("yukselt") ||
            it.startsWith("cogalt") || it.startsWith("parlat") || it == "ac" || it == "yukari"
    }

    private fun azalt(t: List<String>) = t.any {
        it.startsWith("azalt") || it.startsWith("dusur") || it.startsWith("karart") ||
            it.startsWith("alcalt") || it == "kis" || it == "asagi"
    }

    fun isle(metin: String): Pair<Olay, String>? {
        val m = sade(metin)
        val t = m.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        return try {
            when {
                t.any { it.startsWith("fener") } ->
                    fener(!t.any { it.startsWith("kapat") || it.startsWith("sondur") })
                t.any { it == "pil" || it.startsWith("pili") || it.startsWith("pilin") || it.startsWith("sarj") || it.startsWith("batarya") } ->
                    pilDurumu()
                t.any { it.startsWith("parlak") } -> parlaklik(t)
                m.contains("wifi") || ("wi" in t && "fi" in t) || t.any { it.startsWith("kablosuz") } -> wifi()
                t.any { it == "ses" || it == "sesi" || it == "sesin" || it == "sesini" } -> sesAyari(t)
                else -> null
            }
        } catch (e: Exception) {
            Pair(Olay.Hata, "Komutu çalıştıramadım")
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
            Pair(Olay.Hata, "Feneri çalıştıramadım, kamera başka bir uygulamada açık olabilir")
        }
    }

    private fun sesAyari(t: List<String>): Pair<Olay, String>? {
        val yon = when {
            azalt(t) -> AudioManager.ADJUST_LOWER
            artir(t) -> AudioManager.ADJUST_RAISE
            else -> return null
        }
        val simdi = ses.getStreamVolume(AudioManager.STREAM_MUSIC)
        val en = ses.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (yon == AudioManager.ADJUST_LOWER && simdi <= 1) {
            return Pair(Olay.Basari, "Ses en düşük seviyede, daha fazla kısmıyorum")
        }
        ses.adjustStreamVolume(AudioManager.STREAM_MUSIC, yon, AudioManager.FLAG_SHOW_UI)
        val yuzde = ses.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / en
        return Pair(Olay.Basari, "Ses yüzde $yuzde")
    }

    private fun parlaklik(t: List<String>): Pair<Olay, String> {
        if (!Settings.System.canWrite(context)) {
            ekranAc(
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return Pair(Olay.OnayGerekli, "Parlaklık için özel izin gerekiyor. İzin ekranını açtım, DesteX'e izin ver, sonra komutu tekrarla")
        }
        val kaynak = context.contentResolver
        val simdi = Settings.System.getInt(kaynak, Settings.System.SCREEN_BRIGHTNESS, 128)
        val yeni = when {
            azalt(t) -> (simdi - 40).coerceAtLeast(10)
            artir(t) -> (simdi + 40).coerceAtMost(255)
            else -> return Pair(Olay.Hata, "Parlaklığı artır mı azalt mı, anlayamadım")
        }
        Settings.System.putInt(kaynak, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(kaynak, Settings.System.SCREEN_BRIGHTNESS, yeni)
        return Pair(Olay.Basari, "Parlaklık yüzde ${yeni * 100 / 255}")
    }

    private fun pilDurumu(): Pair<Olay, String> {
        val yuzde = pil.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (yuzde <= 0 || yuzde > 100) return Pair(Olay.Hata, "Pil bilgisini okuyamadım")
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
