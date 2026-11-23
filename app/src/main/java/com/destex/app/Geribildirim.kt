package com.destex.app

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
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
    private val el = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var hazir = false
    private var basarisiz = false
    private var bekleyen: String? = null
    private var bekleyenBitince: (() -> Unit)? = null
    private val bitinceler = HashMap<String, () -> Unit>()
    private var sayac = 0

    private val dinleyici = object : UtteranceProgressListener() {
        override fun onStart(id: String?) {}
        override fun onDone(id: String?) = bitir(id)

        @Deprecated("Deprecated in Java")
        override fun onError(id: String?) = bitir(id)
        override fun onError(id: String?, kod: Int) = bitir(id)
    }

    init {
        sesMotoruBaslat(SHERPA_MOTORU)
    }

    private fun bitir(id: String?) {
        val cb = synchronized(bitinceler) { bitinceler.remove(id) } ?: return
        el.post(cb)
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
                    if (motor != null) sesMotoruBaslat(null) else bozuldu()
                } else {
                    t.setOnUtteranceProgressListener(dinleyici)
                    t.setSpeechRate(Ayar.hiz(context))
                    val kayitli = Ayar.sesAdi(context)
                    if (kayitli != null) trSesler(t).firstOrNull { it.name == kayitli }?.let { t.voice = it }
                    tts = t
                    hazir = true
                    bekleyen?.let { konus(it, bekleyenBitince) }
                    bekleyen = null
                    bekleyenBitince = null
                }
            } else {
                t?.shutdown()
                if (motor != null) sesMotoruBaslat(null) else bozuldu()
            }
        }, motor)
    }

    private fun bozuldu() {
        basarisiz = true
        bekleyenBitince?.let { el.post(it) }
        bekleyen = null
        bekleyenBitince = null
    }

    private fun trSesler(t: TextToSpeech): List<Voice> =
        (t.voices ?: emptySet<Voice>()).filter { it.locale.language == "tr" }.sortedBy { it.name }

    private fun konus(cumle: String, bitince: (() -> Unit)?) {
        val id = "d${++sayac}"
        synchronized(bitinceler) {
            bitinceler.clear()
            if (bitince != null) bitinceler[id] = bitince
        }
        tts?.speak(cumle, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    /** Titreşim verir, cümle varsa okur; okuma bitince [bitince] çağrılır. */
    fun bildir(olay: Olay, cumle: String?, bitince: (() -> Unit)? = null) {
        titresim.vibrate(VibrationEffect.createWaveform(olay.desen, -1))
        if (cumle == null) {
            tts?.stop()
            bekleyen = null
            bekleyenBitince = null
            synchronized(bitinceler) { bitinceler.clear() }
            return
        }
        oku(cumle, bitince)
    }

    /** Titreşimsiz okur (ekran okuyucu için). */
    fun oku(cumle: String, bitince: (() -> Unit)? = null) {
        when {
            hazir -> konus(cumle, bitince)
            basarisiz -> bitince?.let { el.post(it) }
            else -> {
                bekleyen = cumle
                bekleyenBitince = bitince
            }
        }
    }

    fun sustur() {
        tts?.stop()
        synchronized(bitinceler) { bitinceler.clear() }
    }

    /** Türkçe sesler arasında sıradakine geçer; yeni sesin adını döndürür. */
    fun sesDegistir(): String? {
        val t = tts ?: return null
        val liste = trSesler(t)
        if (liste.size < 2) return null
        val simdi = liste.indexOfFirst { it.name == t.voice?.name }
        val yeni = liste[(simdi + 1) % liste.size]
        t.voice = yeni
        Ayar.sesAdiYaz(context, yeni.name)
        return yeni.name
    }

    fun hizAyarla(carpan: Float): Float {
        val yeni = (Ayar.hiz(context) * carpan).coerceIn(0.5f, 2.0f)
        Ayar.hizYaz(context, yeni)
        tts?.setSpeechRate(yeni)
        return yeni
    }

    fun kapat() {
        tts?.shutdown()
    }

    companion object {
        const val SHERPA_MOTORU = "org.woheller69.ttsengine"
    }
}
