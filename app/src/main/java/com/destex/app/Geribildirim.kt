package com.destex.app

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import java.util.Locale

enum class Olay(val desen: LongArray) {
    Basari(longArrayOf(0, 80)),
    DinlemeBasladi(longArrayOf(0, 150)),
    DinlemeBitti(longArrayOf(0, 80, 80, 80)),
    OnayGerekli(longArrayOf(0, 150, 100, 150)),
    Hata(longArrayOf(0, 500)),
}

class Geribildirim(context: Context) {
    private val titresim: Vibrator =
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    private var hazir = false
    private val tts: TextToSpeech = TextToSpeech(context) { durum ->
        if (durum == TextToSpeech.SUCCESS) {
            hazir = true
        }
    }

    init {
        tts.language = Locale("tr", "TR")
    }

    fun bildir(olay: Olay, cumle: String) {
        titresim.vibrate(VibrationEffect.createWaveform(olay.desen, -1))
        if (hazir) tts.speak(cumle, TextToSpeech.QUEUE_FLUSH, null, olay.name)
    }

    fun kapat() {
        tts.shutdown()
    }
}
