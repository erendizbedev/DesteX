package com.destex.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class SesTanima(
    context: Context,
    private val kismi: (String) -> Unit,
    private val sonuc: (String) -> Unit,
    private val hata: (String) -> Unit,
) {
    val kullanilabilir = SpeechRecognizer.isRecognitionAvailable(context)
    private val tanimlayici: SpeechRecognizer? =
        try {
            if (kullanilabilir) SpeechRecognizer.createSpeechRecognizer(context) else null
        } catch (e: Exception) {
            null
        }
    private var cevrimdisi = true

    init {
        tanimlayici?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(sonuclar: Bundle?) {
                val metin = sonuclar?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (metin.isNullOrBlank()) hata("Seni anlayamadım") else sonuc(metin)
            }

            override fun onPartialResults(kismiSonuc: Bundle?) {
                kismiSonuc?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    ?.let { if (it.isNotBlank()) kismi(it) }
            }

            override fun onError(kod: Int) {
                when (kod) {
                    SpeechRecognizer.ERROR_CLIENT -> return
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> {
                        if (cevrimdisi) {
                            cevrimdisi = false
                            calistir()
                        } else {
                            hata("Türkçe ses tanıma paketi yüklü değil")
                        }
                        return
                    }
                }
                hata(
                    when (kod) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "Seni anlayamadım"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Ses duymadım"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mikrofon izni yok"
                        SpeechRecognizer.ERROR_AUDIO -> "Mikrofona erişemedim"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Ses tanıma meşgul, tekrar dene"
                        SpeechRecognizer.ERROR_NETWORK,
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "İnternet gerekiyor, ses tanıma çalışmadı"
                        SpeechRecognizer.ERROR_SERVER -> "Ses tanıma servisi hata verdi"
                        else -> "Ses tanıma çalışmadı"
                    },
                )
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    fun baslat() {
        cevrimdisi = true
        calistir()
    }

    private fun calistir() {
        val niyet = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, cevrimdisi)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        try {
            tanimlayici?.startListening(niyet)
        } catch (e: Exception) {
            hata("Ses tanımayı başlatamadım")
        }
    }

    fun iptal() {
        tanimlayici?.cancel()
    }

    fun kapat() {
        tanimlayici?.destroy()
    }
}
