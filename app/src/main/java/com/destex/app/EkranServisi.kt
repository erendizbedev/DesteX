package com.destex.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Display
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class Duzenleme { SonKelime, SonHarf, Hepsi, Satir, HepsiniSec, Kopyala, Kes, Yapistir, Basa, Sona }

class EkranServisi : AccessibilityService() {
    companion object {
        @Volatile
        var ornek: EkranServisi? = null
    }

    private class Parca(val metin: String, val kutu: Rect)

    private val el = Handler(Looper.getMainLooper())
    private var sonYukari = 0L
    private var sonAsagi = 0L
    private var sonKombo = 0L
    private var sonOkunan = ""
    private var sonOkunanZaman = 0L

    private var zorlaPaket: String? = null
    private var zorlaTeslim: ((Cevap) -> Unit)? = null
    private var zorlaBitis = 0L
    private var zorlaAdim = 0
    private val zorlaKontrol = Runnable { zorlaDenemesi() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        ornek = this
        Asistan.baslat(applicationContext)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        ornek = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        ornek = null
        super.onDestroy()
    }

    override fun onInterrupt() {}

    // ---------- Ekran okuyucu ----------

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        e ?: return
        if (!Ayar.ekranOkuyucu(this) || !Asistan.hazirMi()) return
        if (e.packageName == packageName || e.isPassword) return
        val metin: String? = when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_SELECTED -> etiket(e)
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> yazilan(e)
            else -> null
        }
        if (metin.isNullOrBlank()) return
        val simdi = SystemClock.uptimeMillis()
        if (metin == sonOkunan && simdi - sonOkunanZaman < 700) return
        sonOkunan = metin
        sonOkunanZaman = simdi
        Asistan.geri.oku(metin)
    }

    private fun etiket(e: AccessibilityEvent): String? {
        val kaynak = e.source
        var s = e.text.joinToString(" ") { it.toString() }.trim()
        if (s.isBlank()) s = e.contentDescription?.toString().orEmpty().trim()
        if (s.isBlank() && kaynak != null) s = etiketi(kaynak)
        if (s.isBlank()) return null
        if (kaynak != null) {
            if (kaynak.isEditable) s += ", yazı alanı"
            else if (e.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED && kaynak.isCheckable) {
                s += if (kaynak.isChecked) ", açık" else ", kapalı"
            }
        }
        return s
    }

    private fun yazilan(e: AccessibilityEvent): String? {
        val s = e.text.firstOrNull()?.toString().orEmpty()
        if (e.removedCount > 0 && e.addedCount == 0) return "silindi"
        if (e.addedCount !in 1..20) return null
        val bas = e.fromIndex.coerceIn(0, s.length)
        val son = (bas + e.addedCount).coerceAtMost(s.length)
        val parca = s.substring(bas, son)
        return if (parca.isBlank()) "boşluk" else parca
    }

    // ---------- Tuş ile uyandırma: iki ses tuşuna birlikte bas ----------

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val kod = event.keyCode
        if (kod != KeyEvent.KEYCODE_VOLUME_UP && kod != KeyEvent.KEYCODE_VOLUME_DOWN) return false
        val simdi = SystemClock.uptimeMillis()
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (kod == KeyEvent.KEYCODE_VOLUME_UP) sonYukari = simdi else sonAsagi = simdi
            if (sonYukari > 0 && sonAsagi > 0 && abs(sonYukari - sonAsagi) < 400) {
                sonYukari = 0
                sonAsagi = 0
                sonKombo = simdi
                el.post { Asistan.dinleyiciDegistir() }
                return true
            }
        }
        return sonKombo > 0 && simdi - sonKombo < 1500
    }

    // ---------- Ekranı okuma ----------

    private fun etiketi(n: AccessibilityNodeInfo): String {
        if (n.isPassword) return ""
        val s = n.text?.toString()
            ?: n.contentDescription?.toString()
            ?: n.hintText?.toString()
        return s.orEmpty().trim()
    }

    private fun sonek(n: AccessibilityNodeInfo): String {
        val sinif = n.className?.toString().orEmpty()
        return when {
            n.isEditable -> ", yazı alanı"
            sinif.contains("Switch") || sinif.contains("CheckBox") || sinif.contains("Toggle") ->
                if (n.isChecked) ", açık" else ", kapalı"
            sinif.contains("Button") || (n.isClickable && n.text == null) -> ", düğme"
            else -> ""
        }
    }

    private fun topla(n: AccessibilityNodeInfo, sonuc: MutableList<Parca>, derinlik: Int = 0) {
        if (derinlik > 60 || !n.isVisibleToUser) return
        val etiket = etiketi(n)
        if (etiket.isNotBlank()) {
            val kutu = Rect()
            n.getBoundsInScreen(kutu)
            sonuc.add(Parca(etiket + sonek(n), kutu))
        }
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            topla(c, sonuc, derinlik + 1)
        }
    }

    private fun uygulamaAdi(paket: String?): String {
        if (paket == null) return ""
        return try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(paket, 0)).toString()
        } catch (e: Exception) {
            paket
        }
    }

    fun suAnkiPaket(): String? {
        val p = rootInActiveWindow?.packageName?.toString()
        return if (p == packageName) null else p
    }

    /** DesteX öndeyse önce arka plana alır ki komut diğer uygulamada çalışsın. */
    private fun hazirla(devam: () -> Unit) {
        if (rootInActiveWindow?.packageName?.toString() == packageName && Asistan.arkayaAl()) {
            el.postDelayed(devam, 900)
        } else {
            devam()
        }
    }

    fun tarif(zorlaOcr: Boolean, teslim: (Cevap) -> Unit) = hazirla {
        val kok = rootInActiveWindow
        val parcalar = mutableListOf<Parca>()
        if (kok != null && !zorlaOcr) topla(kok, parcalar)
        parcalar.sortWith(compareBy<Parca>({ it.kutu.top / 40 }, { it.kutu.left }))
        val liste = parcalar.map { it.metin }.distinct()
        val baslik = "Ekranda ${uygulamaAdi(kok?.packageName?.toString())}: "
        if (liste.size >= 3 && !zorlaOcr) {
            teslim(Cevap(Olay.Basari, baslik + ozet(liste)))
        } else {
            goruntuAl { bmp ->
                if (bmp == null) {
                    teslim(
                        if (liste.isEmpty()) Cevap(Olay.Hata, "Ekrandan okunacak bir şey alamadım, bu ekran görüntü almaya izin vermiyor olabilir")
                        else Cevap(Olay.Basari, baslik + ozet(liste)),
                    )
                } else {
                    ocr(bmp) { satirlar ->
                        val goruntu = satirlar.orEmpty()
                            .sortedWith(compareBy<Pair<String, Rect>>({ it.second.top / 40 }, { it.second.left }))
                            .map { it.first }
                            .filter { s -> liste.none { it.contains(s, ignoreCase = true) } }
                        val hepsi = liste + goruntu
                        teslim(
                            if (hepsi.isEmpty()) Cevap(Olay.Basari, "Ekranda okunabilir bir yazı göremedim")
                            else Cevap(Olay.Basari, baslik + ozet(hepsi)),
                        )
                    }
                }
            }
        }
    }

    private fun ozet(liste: List<String>): String {
        val ilk = liste.take(40).joinToString(". ")
        return if (liste.size > 40) "$ilk. Daha fazlası var" else ilk
    }

    // ---------- Görüntü ve yazı tanıma ----------

    private fun goruntuAl(cb: (Bitmap?) -> Unit) {
        if (Build.VERSION.SDK_INT < 30) {
            cb(null)
            return
        }
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(r: ScreenshotResult) {
                    val tampon = r.hardwareBuffer
                    val bmp = try {
                        Bitmap.wrapHardwareBuffer(tampon, r.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
                    } catch (e: Exception) {
                        null
                    }
                    tampon.close()
                    cb(bmp)
                }

                override fun onFailure(kod: Int) {
                    cb(null)
                }
            },
        )
    }

    private fun ocr(bmp: Bitmap, bitti: (List<Pair<String, Rect>>?) -> Unit) {
        val tanima = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        tanima.process(InputImage.fromBitmap(bmp, 0))
            .addOnSuccessListener { s ->
                val satirlar = s.textBlocks.flatMap { b -> b.lines.map { Pair(it.text, it.boundingBox ?: Rect()) } }
                tanima.close()
                bitti(satirlar)
            }
            .addOnFailureListener {
                tanima.close()
                bitti(null)
            }
    }

    // ---------- Dokunma ve kaydırma ----------

    private fun kompakt(s: String) = Yazim.sade(s).filter { it.isLetterOrDigit() }

    private fun adayTopla(n: AccessibilityNodeInfo, sonuc: MutableList<Pair<AccessibilityNodeInfo, String>>, derinlik: Int = 0) {
        if (derinlik > 60 || !n.isVisibleToUser) return
        val e = etiketi(n)
        if (e.isNotBlank()) sonuc.add(Pair(n, e))
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            adayTopla(c, sonuc, derinlik + 1)
        }
    }

    private fun eslestir(adaylar: List<Pair<AccessibilityNodeInfo, String>>, hedef: String): AccessibilityNodeInfo? {
        val h = kompakt(hedef)
        if (h.length < 2) return null
        adaylar.firstOrNull { kompakt(it.second) == h }?.let { return it.first }
        adaylar.filter { kompakt(it.second).length >= 2 && h.contains(kompakt(it.second)) }
            .maxByOrNull { kompakt(it.second).length }?.let { return it.first }
        adaylar.filter { kompakt(it.second).contains(h) }
            .minByOrNull { it.second.length }?.let { return it.first }
        return null
    }

    private fun dokunNokta(x: Float, y: Float): Boolean {
        val yol = Path().apply { moveTo(x, y) }
        val jest = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(yol, 0, 60))
            .build()
        return dispatchGesture(jest, null, null)
    }

    private fun tikla(dugum: AccessibilityNodeInfo, hedef: String): Cevap {
        var n: AccessibilityNodeInfo? = dugum
        var adim = 0
        while (n != null && adim < 7) {
            if (n.isClickable && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return Cevap(Olay.Basari, "$hedef dokunuldu")
            }
            n = n.parent
            adim++
        }
        val kutu = Rect()
        dugum.getBoundsInScreen(kutu)
        return if (!kutu.isEmpty && dokunNokta(kutu.exactCenterX(), kutu.exactCenterY())) {
            Cevap(Olay.Basari, "$hedef dokunuldu")
        } else {
            Cevap(Olay.Hata, "$hedef öğesine dokunamadım")
        }
    }

    fun dokun(hedef: String, teslim: (Cevap) -> Unit) = hazirla {
        val kok = rootInActiveWindow
        val secilen = if (kok != null) {
            val adaylar = mutableListOf<Pair<AccessibilityNodeInfo, String>>()
            adayTopla(kok, adaylar)
            eslestir(adaylar, hedef)
        } else {
            null
        }
        if (secilen != null) {
            teslim(tikla(secilen, hedef))
        } else {
            goruntuAl { bmp ->
                if (bmp == null) {
                    teslim(Cevap(Olay.Hata, "$hedef öğesini ekranda bulamadım"))
                } else {
                    ocr(bmp) { satirlar ->
                        val k = kompakt(hedef)
                        val bulunan = satirlar.orEmpty()
                            .filter { kompakt(it.first).contains(k) }
                            .minByOrNull { it.first.length }
                        teslim(
                            if (bulunan != null && dokunNokta(bulunan.second.exactCenterX(), bulunan.second.exactCenterY())) {
                                Cevap(Olay.Basari, "$hedef dokunuldu")
                            } else {
                                Cevap(Olay.Hata, "$hedef öğesini ekranda bulamadım")
                            },
                        )
                    }
                }
            }
        }
    }

    private fun kaydirilabilirTopla(n: AccessibilityNodeInfo, sonuc: MutableList<AccessibilityNodeInfo>, derinlik: Int = 0) {
        if (derinlik > 60 || !n.isVisibleToUser) return
        if (n.isScrollable) sonuc.add(n)
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            kaydirilabilirTopla(c, sonuc, derinlik + 1)
        }
    }

    fun kaydir(ileri: Boolean, teslim: (Cevap) -> Unit) = hazirla {
        val kok = rootInActiveWindow
        val liste = mutableListOf<AccessibilityNodeInfo>()
        if (kok != null) kaydirilabilirTopla(kok, liste)
        val hedef = liste.maxByOrNull {
            val k = Rect()
            it.getBoundsInScreen(k)
            k.width().toLong() * k.height()
        }
        val eylem = if (ileri) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        val tamam = (hedef != null && hedef.performAction(eylem)) || kaydirmaJesti(ileri)
        teslim(if (tamam) Cevap(Olay.Basari, "Kaydırdım") else Cevap(Olay.Hata, "Kaydırılacak bir yer bulamadım"))
    }

    private fun kaydirmaJesti(ileri: Boolean): Boolean {
        val d = resources.displayMetrics
        val x = d.widthPixels / 2f
        val bas = d.heightPixels * (if (ileri) 0.7f else 0.3f)
        val son = d.heightPixels * (if (ileri) 0.3f else 0.7f)
        val yol = Path().apply {
            moveTo(x, bas)
            lineTo(x, son)
        }
        val jest = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(yol, 0, 300))
            .build()
        return dispatchGesture(jest, null, null)
    }

    fun global(eylem: Int): Boolean = performGlobalAction(eylem)

    // ---------- Yazma ve düzenleme ----------

    private fun duzenlenebilirBul(n: AccessibilityNodeInfo, derinlik: Int = 0): AccessibilityNodeInfo? {
        if (derinlik > 60) return null
        if (n.isEditable && n.isVisibleToUser) return n
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            duzenlenebilirBul(c, derinlik + 1)?.let { return it }
        }
        return null
    }

    private fun alan(): AccessibilityNodeInfo? {
        val kok = rootInActiveWindow ?: return null
        val odak = kok.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (odak != null && odak.isEditable) return odak
        return duzenlenebilirBul(kok)
    }

    private fun metniOku(n: AccessibilityNodeInfo): String =
        if (n.isShowingHintText) "" else n.text?.toString().orEmpty()

    private fun secim(n: AccessibilityNodeInfo, m: String): Pair<Int, Int> {
        var a = n.textSelectionStart
        var b = n.textSelectionEnd
        if (a < 0 || b < 0) {
            a = m.length
            b = m.length
        }
        return Pair(min(a, b).coerceIn(0, m.length), max(a, b).coerceIn(0, m.length))
    }

    private fun yerlestir(n: AccessibilityNodeInfo, yeni: String, imlec: Int): Boolean {
        val b = Bundle()
        b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, yeni)
        if (!n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)) return false
        imleciAyarla(n, imlec, imlec)
        return true
    }

    private fun imleciAyarla(n: AccessibilityNodeInfo, bas: Int, son: Int) {
        val s = Bundle()
        s.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, bas)
        s.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, son)
        n.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, s)
    }

    fun yaz(metin: String, teslim: (Cevap) -> Unit) = hazirla {
        val n = alan()
        if (n == null) {
            teslim(Cevap(Olay.Hata, "Yazılacak bir alan bulamadım. Önce yazı alanına dokun"))
        } else {
            val eski = metniOku(n)
            val (a, b) = secim(n, eski)
            val onek = if (a > 0 && !eski[a - 1].isWhitespace() && metin.isNotEmpty() &&
                !metin[0].isWhitespace() && metin[0] !in ".,;:!?)]}"
            ) " " else ""
            val ekle = onek + metin
            val yeni = eski.substring(0, a) + ekle + eski.substring(b)
            teslim(
                if (yerlestir(n, yeni, a + ekle.length)) Cevap(Olay.Basari, "Yazıldı")
                else Cevap(Olay.Hata, "Bu alana yazamadım"),
            )
        }
    }

    fun duzenle(islem: Duzenleme, teslim: (Cevap) -> Unit) = hazirla {
        val n = alan()
        if (n == null) {
            teslim(Cevap(Olay.Hata, "Düzenlenecek bir yazı alanı bulamadım. Önce yazı alanına dokun"))
        } else {
            val eski = metniOku(n)
            val (a, b) = secim(n, eski)
            val tamam: Boolean
            val mesaj: String
            when (islem) {
                Duzenleme.SonKelime -> {
                    val once = eski.substring(0, a).trimEnd()
                    val i = once.lastIndexOfAny(charArrayOf(' ', '\n', '\t'))
                    val kalan = if (i < 0) "" else once.substring(0, i + 1)
                    tamam = yerlestir(n, kalan + eski.substring(b), kalan.length)
                    mesaj = "Son kelime silindi"
                }
                Duzenleme.SonHarf -> {
                    if (a != b) {
                        tamam = yerlestir(n, eski.removeRange(a, b), a)
                    } else if (a > 0) {
                        tamam = yerlestir(n, eski.removeRange(a - 1, a), a - 1)
                    } else {
                        tamam = true
                    }
                    mesaj = "Silindi"
                }
                Duzenleme.Satir -> {
                    val bas = eski.lastIndexOf('\n', a - 1) + 1
                    val son = eski.indexOf('\n', a).let { if (it < 0) eski.length else it + 1 }
                    tamam = yerlestir(n, eski.removeRange(bas, son), bas)
                    mesaj = "Satır silindi"
                }
                Duzenleme.Hepsi -> {
                    tamam = yerlestir(n, "", 0)
                    mesaj = "Yazının hepsi silindi"
                }
                Duzenleme.HepsiniSec -> {
                    imleciAyarla(n, 0, eski.length)
                    tamam = true
                    mesaj = "Hepsi seçildi"
                }
                Duzenleme.Kopyala -> {
                    tamam = n.performAction(AccessibilityNodeInfo.ACTION_COPY)
                    mesaj = "Kopyalandı"
                }
                Duzenleme.Kes -> {
                    tamam = n.performAction(AccessibilityNodeInfo.ACTION_CUT)
                    mesaj = "Kesildi"
                }
                Duzenleme.Yapistir -> {
                    tamam = n.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                    mesaj = "Yapıştırıldı"
                }
                Duzenleme.Basa -> {
                    imleciAyarla(n, 0, 0)
                    tamam = true
                    mesaj = "İmleç başta"
                }
                Duzenleme.Sona -> {
                    imleciAyarla(n, eski.length, eski.length)
                    tamam = true
                    mesaj = "İmleç sonda"
                }
            }
            teslim(if (tamam) Cevap(Olay.Basari, mesaj) else Cevap(Olay.Hata, "Bu işlemi yapamadım"))
        }
    }

    // ---------- Uygulamayı kapatma (Ayarlar > Zorla durdur) ----------

    fun zorlaDurdur(paket: String, teslim: (Cevap) -> Unit) {
        if (paket == packageName) {
            teslim(Cevap(Olay.Hata, "Kendimi kapatamam"))
            return
        }
        zorlaPaket = paket
        zorlaTeslim = teslim
        zorlaAdim = 0
        zorlaBitis = SystemClock.uptimeMillis() + 9000
        try {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$paket"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (e: Exception) {
            zorlaBitir(Cevap(Olay.Hata, "Uygulama ayarlarını açamadım"))
            return
        }
        el.postDelayed(zorlaKontrol, 800)
    }

    private fun zorlaBitir(c: Cevap) {
        el.removeCallbacks(zorlaKontrol)
        val t = zorlaTeslim
        zorlaPaket = null
        zorlaTeslim = null
        t?.invoke(c)
    }

    private fun tiklanabilirBul(n: AccessibilityNodeInfo, etiketler: List<String>, derinlik: Int = 0): AccessibilityNodeInfo? {
        if (derinlik > 60) return null
        if (Yazim.sade(etiketi(n)) in etiketler) {
            var x: AccessibilityNodeInfo? = n
            var adim = 0
            while (x != null && adim < 5) {
                if (x.isClickable) return x
                x = x.parent
                adim++
            }
        }
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            tiklanabilirBul(c, etiketler, derinlik + 1)?.let { return it }
        }
        return null
    }

    private fun zorlaDenemesi() {
        if (zorlaPaket == null) return
        if (SystemClock.uptimeMillis() > zorlaBitis) {
            zorlaBitir(Cevap(Olay.Hata, "Uygulamayı kapatamadım"))
            return
        }
        val kok = rootInActiveWindow
        if (kok != null) {
            val etiketler = if (zorlaAdim == 0) {
                listOf("zorla durdur", "durmaya zorla", "force stop", "durdur")
            } else {
                listOf("tamam", "ok", "zorla durdur", "durdur", "force stop")
            }
            val dugme = tiklanabilirBul(kok, etiketler)
            if (dugme != null) {
                if (!dugme.isEnabled) {
                    zorlaBitir(Cevap(Olay.Basari, "Uygulama zaten kapalı"))
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    return
                }
                if (dugme.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    if (zorlaAdim == 0) {
                        zorlaAdim = 1
                    } else {
                        zorlaBitir(Cevap(Olay.Basari, "Uygulama kapatıldı"))
                        performGlobalAction(GLOBAL_ACTION_HOME)
                        return
                    }
                }
            }
        }
        el.postDelayed(zorlaKontrol, 600)
    }
}
