package com.destex.app

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import java.util.Locale

enum class Olay(val desen: LongArray) {
    Basari(longArrayOf(0, 80)),
    DinlemeBasladi(longArrayOf(0, 150)),
    DinlemeBitti(longArrayOf(0, 80, 80, 80)),
    OnayGerekli(longArrayOf(0, 150, 100, 150, 100, 150)),
    Hata(longArrayOf(0, 500)),
}

class Geribildirim(private val context: Context) {
    private val titresim: Vibrator =
        if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    private var tts: TextToSpeech? = null
    private var hazir = false
    private var bekleyen: String? = null

    init {
        sesMotoruBaslat(SHERPA_MOTORU)
    }

    private fun sesMotoruBaslat(motor: String?) {
        var yeni: TextToSpeech? = null
        yeni = TextToSpeech(context, { durum ->
            val t = yeni
            if (durum == TextToSpeech.SUCCESS && t != null) {
                t.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                val dil = t.setLanguage(Locale("tr", "TR"))
                if (dil == TextToSpeech.LANG_MISSING_DATA || dil == TextToSpeech.LANG_NOT_SUPPORTED) {
                    t.shutdown()
                    if (motor != null) sesMotoruBaslat(null)
                } else {
                    tts = t
                    hazir = true
                    bekleyen?.let { konus(it) }
                    bekleyen = null
                }
            } else {
                t?.shutdown()
                if (motor != null) sesMotoruBaslat(null)
            }
        }, motor)
    }

    private fun konus(cumle: String) {
        tts?.speak(cumle, TextToSpeech.QUEUE_FLUSH, null, "destex")
    }

    fun bildir(olay: Olay, cumle: String?) {
        titresim.vibrate(VibrationEffect.createWaveform(olay.desen, -1))
        if (cumle == null) {
            tts?.stop()
            bekleyen = null
        } else if (hazir) {
            konus(cumle)
        } else {
            bekleyen = cumle
        }
    }

    fun kapat() {
        tts?.shutdown()
    }

    companion object {
        const val SHERPA_MOTORU = "org.woheller69.ttsengine"
    }
}
