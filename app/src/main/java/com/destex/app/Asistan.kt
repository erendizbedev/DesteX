package com.destex.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Ses tanıma, komutlar ve geri bildirimi tek yerde toplar; uygulama, servisler ve tuş kısayolu bunu kullanır. */
object Asistan {
    const val HAZIR = "Hazır, seni bekliyorum"

    var durum by mutableStateOf(HAZIR)
    var duyulan by mutableStateOf("")
    var uyandirma by mutableStateOf(false)
    var ekran by mutableStateOf("ana")

    /** MainActivity'nin kendini arka plana almasını sağlayan bağlantı. */
    var arkaPlanIstegi: (() -> Unit)? = null

    private enum class Mod { Bosta, Komut, Uyanma }

    private var mod = Mod.Bosta
    private var beklenen: Devam? = null
    private var ardisikHata = 0
    private var basladi = false
    private val el = Handler(Looper.getMainLooper())
    private val uyanmaYeniden = Runnable { dongu() }

    private lateinit var ctx: Context
    lateinit var geri: Geribildirim
        private set
    private lateinit var tanima: SesTanima
    private lateinit var komut: KomutIsleyici

    fun hazirMi() = basladi

    fun baslat(c: Context) {
        if (basladi) return
        basladi = true
        ctx = c.applicationContext
        geri = Geribildirim(ctx)
        komut = KomutIsleyici(ctx) { niyet ->
            try {
                (EkranServisi.ornek ?: ctx).startActivity(niyet)
            } catch (e: Exception) {
                geri.bildir(Olay.Hata, "Bu ekranı açamadım")
            }
        }
        tanima = SesTanima(
            ctx,
            kismi = { if (mod == Mod.Komut) duyulan = it },
            sonuc = { sonuc(it) },
            hata = { hata(it) },
        )
        uyandirma = Ayar.uyandirma(ctx)
    }

    fun arkayaAl(): Boolean {
        val istek = arkaPlanIstegi ?: return false
        istek()
        return true
    }

    private fun micVar() = ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun dinliyor() = mod == Mod.Komut

    /** Tuş kısayolu ve düğme için: dinliyorsa durdurur, dinlemiyorsa başlatır. */
    fun dinleyiciDegistir() {
        if (dinliyor()) iptal() else dinle()
    }

    fun dinle() {
        if (!tanima.kullanilabilir) {
            geri.bildir(Olay.Hata, "Bu cihazda ses tanıma servisi yok")
            return
        }
        if (!micVar()) {
            geri.bildir(Olay.Hata, "Mikrofon izni yok. DesteX uygulamasını açıp izin ver")
            return
        }
        el.removeCallbacks(uyanmaYeniden)
        tanima.iptal()
        mod = Mod.Komut
        Durum.dinliyor = true
        durum = "Dinleniyor"
        duyulan = ""
        geri.bildir(Olay.DinlemeBasladi, null)
        tanima.baslat(true)
    }

    fun iptal() {
        if (mod != Mod.Komut) return
        tanima.iptal()
        Durum.dinliyor = false
        mod = Mod.Bosta
        durum = HAZIR
        beklenen = null
        geri.bildir(Olay.DinlemeBitti, null)
        sonraUyanma()
    }

    private fun sonuc(metin: String) {
        if (mod == Mod.Uyanma) {
            uyanmaSonucu(metin)
            return
        }
        Durum.dinliyor = false
        mod = Mod.Bosta
        duyulan = metin
        durum = HAZIR
        isle(metin)
    }

    private fun isle(metin: String) {
        val bekleyen = beklenen
        beklenen = null
        when (bekleyen) {
            Devam.Not -> {
                Notlar.ekle(ctx, Yazim.cumleYap(metin))
                cevapla(Cevap(Olay.Basari, "Not kaydedildi"))
                return
            }
            Devam.Arama -> {
                if (!komut.isle("ara $metin") { cevapla(it) }) cevapla(Cevap(Olay.Hata, "Aramayı yapamadım"))
                return
            }
            null -> {}
        }
        if (!komut.isle(metin) { cevapla(it) }) {
            cevapla(Cevap(Olay.Hata, "Duydum: $metin. Bu komutu bilmiyorum"))
        }
    }

    private fun cevapla(c: Cevap) {
        if (c.devam != null) {
            beklenen = c.devam
            geri.bildir(c.olay, c.metin) { dinle() }
        } else {
            geri.bildir(c.olay, c.metin) { sonraUyanma() }
        }
    }

    private fun hata(mesaj: String) {
        if (mod == Mod.Uyanma) {
            ardisikHata++
            el.postDelayed(uyanmaYeniden, if (ardisikHata > 5) 5000L else 500L)
            return
        }
        Durum.dinliyor = false
        mod = Mod.Bosta
        durum = HAZIR
        beklenen = null
        geri.bildir(Olay.Hata, mesaj) { sonraUyanma() }
    }

    // ---------- Uyandırma: "DesteX" diye seslenince dinler ----------

    private val uyanmaKalibi = Regex("^deste(x|ks|ex|eks)")

    private fun uyanmaKelimesindenSonra(metin: String): String? {
        val kelimeler = metin.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        for (i in kelimeler.indices) {
            val k = Yazim.sade(kelimeler[i]).filter { it.isLetterOrDigit() }
            val sonraki = Yazim.sade(kelimeler.getOrNull(i + 1).orEmpty()).filter { it.isLetterOrDigit() }
            if (uyanmaKalibi.containsMatchIn(k)) return kelimeler.drop(i + 1).joinToString(" ")
            if (k == "deste" && sonraki in setOf("x", "ex", "eks", "ks")) return kelimeler.drop(i + 2).joinToString(" ")
        }
        return null
    }

    private fun uyanmaSonucu(metin: String) {
        ardisikHata = 0
        val kalan = uyanmaKelimesindenSonra(metin)
        if (kalan == null) {
            el.postDelayed(uyanmaYeniden, 300)
            return
        }
        if (kalan.isBlank()) {
            dinle()
            return
        }
        mod = Mod.Bosta
        duyulan = kalan
        durum = HAZIR
        isle(kalan)
    }

    private fun dongu() {
        if (!uyandirma || mod == Mod.Komut) return
        if (!tanima.kullanilabilir || !micVar()) return
        mod = Mod.Uyanma
        tanima.baslat(false)
    }

    private fun sonraUyanma() {
        if (uyandirma && mod == Mod.Bosta) {
            el.removeCallbacks(uyanmaYeniden)
            el.postDelayed(uyanmaYeniden, 500)
        }
    }

    /** Uyandırma servisi başlayınca çağrılır. */
    fun uyanmaBaslat() {
        el.removeCallbacks(uyanmaYeniden)
        if (mod == Mod.Uyanma) mod = Mod.Bosta
        dongu()
    }

    fun uyanmaDurdur() {
        el.removeCallbacks(uyanmaYeniden)
        if (mod == Mod.Uyanma) {
            tanima.iptal()
            mod = Mod.Bosta
        }
    }

    fun uyandirmaServisiBaslat() {
        if (!uyandirma || !micVar()) return
        try {
            ctx.startForegroundService(Intent(ctx, UyandirmaServisi::class.java))
        } catch (e: Exception) {
            uyandirma = false
            Ayar.uyandirmaYaz(ctx, false)
        }
    }

    fun uyandirmaDegistir(acik: Boolean) {
        uyandirma = acik
        Ayar.uyandirmaYaz(ctx, acik)
        if (acik) {
            uyandirmaServisiBaslat()
        } else {
            ctx.stopService(Intent(ctx, UyandirmaServisi::class.java))
            uyanmaDurdur()
        }
    }
}
