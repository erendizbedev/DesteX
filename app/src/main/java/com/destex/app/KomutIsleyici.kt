package com.destex.app

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.Settings

enum class Devam { Not, Arama }

data class Cevap(val olay: Olay, val metin: String, val devam: Devam? = null)

class KomutIsleyici(
    private val context: Context,
    private val ekranAc: (Intent) -> Unit,
) {
    private val kamera = context.getSystemService(CameraManager::class.java)
    private val ses = context.getSystemService(AudioManager::class.java)
    private val pil = context.getSystemService(BatteryManager::class.java)

    private fun sade(s: String) = Yazim.sade(s)

    private fun kompakt(s: String) = sade(s).filter { it.isLetterOrDigit() }

    private fun artir(t: List<String>) = t.any {
        it.startsWith("artir") || it.startsWith("arttir") || it.startsWith("yukselt") ||
            it.startsWith("cogalt") || it.startsWith("parlat") || it == "ac" || it == "yukari"
    }

    private fun azalt(t: List<String>) = t.any {
        it.startsWith("azalt") || it.startsWith("dusur") || it.startsWith("karart") ||
            it.startsWith("alcalt") || it == "kis" || it == "asagi"
    }

    private fun erisimYok(): Cevap {
        ekranAc(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return Cevap(Olay.OnayGerekli, "Bu işlem için erişilebilirlik izni gerekiyor. Ayarları açtım, DesteX'i bulup aç")
    }

    /** Komutu işler. Bilinmeyen komutta false döner; aksi halde [teslim] mutlaka çağrılır. */
    fun isle(metin: String, teslim: (Cevap) -> Unit): Boolean {
        return try {
            isleKomut(metin, teslim)
        } catch (e: Exception) {
            teslim(Cevap(Olay.Hata, "Komutu çalıştıramadım"))
            true
        }
    }

    private fun isleKomut(metin: String, teslim: (Cevap) -> Unit): Boolean {
        val kelimeler = metin.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val n = kelimeler.map { sade(it) }
        val m = sade(metin)
        val t = m.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        if (n.isEmpty()) return false

        // Yazma: "yaz ..." ve "kod yaz ..."
        if (n[0] == "yaz" || (n[0] == "kod" && n.getOrNull(1) == "yaz")) {
            val kod = n[0] == "kod"
            val icerik = kelimeler.drop(if (kod) 2 else 1).joinToString(" ")
            if (icerik.isBlank()) {
                teslim(Cevap(Olay.Hata, "Ne yazacağımı söylemedin"))
                return true
            }
            val servis = EkranServisi.ornek ?: run { teslim(erisimYok()); return true }
            val yazi = if (kod) Yazim.yaz(icerik, true) else Yazim.cumleYap(Yazim.yaz(icerik, false))
            servis.yaz(yazi, teslim)
            return true
        }

        // Notlar
        val notVar = n.any { it == "not" || it.startsWith("notlar") || it.startsWith("notu") || it == "notum" }
        if (notVar) {
            if (n[0] == "not" && n.getOrNull(1) in setOf("al", "et", "tut", "yaz", "kaydet")) {
                val icerik = kelimeler.drop(2).joinToString(" ")
                if (icerik.isBlank()) {
                    teslim(Cevap(Olay.Basari, "Notu söyle", Devam.Not))
                } else {
                    Notlar.ekle(context, Yazim.cumleYap(icerik))
                    teslim(Cevap(Olay.Basari, "Not kaydedildi"))
                }
                return true
            }
            val liste = Notlar.hepsi(context)
            when {
                n.any { it.startsWith("sil") || it.startsWith("temizle") } -> {
                    Notlar.sil(context)
                    teslim(Cevap(Olay.Basari, "Notların hepsi silindi"))
                }
                n.any { it.startsWith("ac") || it.startsWith("goster") } -> {
                    Asistan.ekran = "notlar"
                    ekranAc(
                        Intent(context, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    )
                    teslim(Cevap(Olay.Basari, "Notlar ekranı açıldı"))
                }
                "son" in n -> teslim(
                    Cevap(Olay.Basari, if (liste.isEmpty()) "Hiç notun yok" else "Son not: ${liste.last()}"),
                )
                n.any { it.startsWith("oku") || it.startsWith("dinle") || it.startsWith("say") } -> {
                    if (liste.isEmpty()) {
                        teslim(Cevap(Olay.Basari, "Hiç notun yok"))
                    } else {
                        val son = liste.takeLast(10)
                        teslim(
                            Cevap(
                                Olay.Basari,
                                "${liste.size} notun var. " + son.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString(" "),
                            ),
                        )
                    }
                }
                else -> return false
            }
            return true
        }

        // Metin düzenleme
        val duzenleme = when {
            n.any { it.startsWith("kelime") } && n.any { it.startsWith("sil") } -> Duzenleme.SonKelime
            n.any { it.startsWith("harf") || it.startsWith("karakter") } && n.any { it.startsWith("sil") } -> Duzenleme.SonHarf
            n.any { it.startsWith("satir") } && n.any { it.startsWith("sil") } -> Duzenleme.Satir
            n.any { it.startsWith("hepsini") || it.startsWith("tamamini") || it.startsWith("tumunu") || it.startsWith("yaziyi") } &&
                n.any { it.startsWith("sil") } -> Duzenleme.Hepsi
            n.any { it.startsWith("hepsini") || it.startsWith("tumunu") || it.startsWith("tamamini") } &&
                n.any { it.startsWith("sec") } -> Duzenleme.HepsiniSec
            n.any { it.startsWith("kopyala") } -> Duzenleme.Kopyala
            n.any { it == "kes" } -> Duzenleme.Kes
            n.any { it.startsWith("yapistir") } -> Duzenleme.Yapistir
            n.any { it.startsWith("basa") } && n.any { it.startsWith("git") || it.startsWith("al") || it.startsWith("imlec") } -> Duzenleme.Basa
            n.any { it.startsWith("sona") } && n.any { it.startsWith("git") || it.startsWith("al") || it.startsWith("imlec") } -> Duzenleme.Sona
            else -> null
        }
        if (duzenleme != null) {
            val servis = EkranServisi.ornek ?: run { teslim(erisimYok()); return true }
            servis.duzenle(duzenleme, teslim)
            return true
        }

        // Ekran okuyucu ve uyandırma
        if (n.any { it.startsWith("okuyucu") }) {
            val kapat = n.any { it.startsWith("kapat") || it.startsWith("durdur") }
            Ayar.ekranOkuyucuYaz(context, !kapat)
            if (!kapat && EkranServisi.ornek == null) {
                teslim(erisimYok())
            } else {
                teslim(Cevap(Olay.Basari, if (kapat) "Ekran okuyucu kapatıldı" else "Ekran okuyucu açıldı"))
            }
            return true
        }
        if (n.any { it.startsWith("uyandirma") || it.startsWith("uyanma") }) {
            val kapat = n.any { it.startsWith("kapat") || it.startsWith("durdur") }
            Asistan.uyandirmaDegistir(!kapat)
            teslim(Cevap(Olay.Basari, if (kapat) "Uyandırma kapatıldı" else "Uyandırma açıldı. DesteX diye seslenebilirsin"))
            return true
        }

        // Konuşma sesi
        if (n.any { it.startsWith("konus") } && n.any { it.startsWith("hizli") || it.startsWith("yavas") }) {
            val hizli = n.any { it.startsWith("hizli") }
            val hiz = Asistan.geri.hizAyarla(if (hizli) 1.2f else 0.85f)
            teslim(Cevap(Olay.Basari, "Konuşma hızı yüzde ${(hiz * 100).toInt()}"))
            return true
        }
        if (n.any { it.startsWith("degistir") } && n.any { it.startsWith("ses") }) {
            val ad = Asistan.geri.sesDegistir()
            teslim(
                if (ad == null) Cevap(Olay.Hata, "Bu ses motorunda başka Türkçe ses yok")
                else Cevap(Olay.Basari, "Ses değişti, şimdi bu sesle konuşuyorum"),
            )
            return true
        }

        // Ekran tarifi ve gezinme
        if (n.any { it.startsWith("goruntu") } && n.any { it == "al" || it.startsWith("cek") }) {
            val servis = EkranServisi.ornek ?: run { teslim(erisimYok()); return true }
            servis.global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
            teslim(Cevap(Olay.Basari, "Ekran görüntüsü alındı"))
            return true
        }
        val ekranTarifi = (n.any { it.startsWith("ekran") } &&
            (n.any { it.startsWith("oku") || it.startsWith("tarif") || it.startsWith("anlat") } || "var" in n)) ||
            (n.any { it.startsWith("goruyor") } && "ne" in n) ||
            (n.any { it.startsWith("goruntu") } && n.any { it.startsWith("oku") })
        if (ekranTarifi) {
            val servis = EkranServisi.ornek ?: run { teslim(erisimYok()); return true }
            servis.tarif(n.any { it.startsWith("goruntu") }, teslim)
            return true
        }
        val global = when {
            "ana" in n && n.any { it.startsWith("ekran") } -> AccessibilityService.GLOBAL_ACTION_HOME
            n.any { it.startsWith("son") } && n.any { it.startsWith("uygulama") } -> AccessibilityService.GLOBAL_ACTION_RECENTS
            n.any { it.startsWith("bildirim") } && n.any { it.startsWith("panel") } -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            n.any { it.startsWith("hizli") } && n.any { it.startsWith("ayar") } -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
            n.size <= 2 && n.any { it == "geri" } -> AccessibilityService.GLOBAL_ACTION_BACK
            else -> null
        }
        if (global != null) {
            val servis = EkranServisi.ornek ?: run { teslim(erisimYok()); return true }
            servis.global(global)
            teslim(Cevap(Olay.Basari, "Tamam"))
            return true
        }
        if (n.any { it.startsWith("kaydir") }) {
            val servis = EkranServisi.ornek ?: run { teslim(erisimYok()); return true }
            val ileri = !n.any { it.startsWith("yukari") || it == "geri" }
            servis.kaydir(ileri, teslim)
            return true
        }
        val dokunKelime = n.indexOfFirst { it.startsWith("dokun") || it.startsWith("tikla") }
        if (dokunKelime >= 0) {
            val servis = EkranServisi.ornek ?: run { teslim(erisimYok()); return true }
            val hedef = kelimeler.filterIndexed { i, k ->
                i != dokunKelime && !sade(k).let { s -> s.startsWith("uzerine") || s.startsWith("ustune") || s == "bir" }
            }.joinToString(" ")
            if (hedef.isBlank()) {
                teslim(Cevap(Olay.Hata, "Neye dokunacağımı söylemedin"))
            } else {
                servis.dokun(hedef, teslim)
            }
            return true
        }

        // Arama
        val sonKelime = n.last()
        val aramaFiil = n.indexOfFirst { it == "ara" || it.startsWith("arastir") }
        if (aramaFiil >= 0 || sonKelime == "nedir" || sonKelime == "kimdir" || (n.size >= 2 && n[n.size - 2] == "ne" && sonKelime == "demek")) {
            val atla = setOf("internette", "googleda", "google", "vikipedide", "hakkinda", "bana", "bir", "nedir", "kimdir", "ne", "demek", "bul")
            val sorgu = kelimeler.filterIndexed { i, k -> i != aramaFiil && sade(k) !in atla }.joinToString(" ")
            if (sorgu.isBlank()) {
                teslim(Cevap(Olay.Basari, "Ne arayayım", Devam.Arama))
            } else {
                ara(sorgu, teslim)
            }
            return true
        }

        // Cihaz ayarları
        val cihaz: Cevap? = when {
            t.any { it.startsWith("bildirim") } -> bildirim(t)
            t.any { it.startsWith("fener") } ->
                fener(!t.any { it.startsWith("kapat") || it.startsWith("sondur") })
            t.any { it == "pil" || it.startsWith("pili") || it.startsWith("pilin") || it.startsWith("sarj") || it.startsWith("batarya") } ->
                pilDurumu()
            t.any { it.startsWith("parlak") } -> parlaklik(t)
            m.contains("wifi") || ("wi" in t && "fi" in t) || t.any { it.startsWith("kablosuz") } -> wifi()
            t.any { it == "ses" || it == "sesi" || it == "sesin" || it == "sesini" } -> sesAyari(t)
            else -> null
        }
        if (cihaz != null) {
            teslim(cihaz)
            return true
        }

        // Uygulama açma ve kapatma
        val kapatma = n.any { it.startsWith("kapat") || it.startsWith("durdur") || it.startsWith("sonlandir") }
        val acma = n.any { it == "ac" || it.startsWith("baslat") || it.startsWith("calistir") }
        if (kapatma || acma) {
            val fiiller = setOf("ac", "kapat", "baslat", "calistir", "durdur", "sonlandir", "uygulama", "uygulamayi", "uygulamasini", "lutfen", "bir")
            val ad = kelimeler.filter { sade(it).let { s -> s !in fiiller && !s.startsWith("uygulama") } }.joinToString(" ")
            if (kapatma) {
                val servis = EkranServisi.ornek ?: run { teslim(erisimYok()); return true }
                val paket = if (ad.isBlank()) servis.suAnkiPaket() else uygulamaBul(ad)?.second
                if (paket == null) {
                    teslim(Cevap(Olay.Hata, if (ad.isBlank()) "Hangi uygulamayı kapatacağımı anlayamadım" else "$ad uygulamasını bulamadım"))
                } else {
                    servis.zorlaDurdur(paket, teslim)
                }
            } else {
                if (ad.isBlank()) return false
                val uygulama = uygulamaBul(ad)
                val niyet = uygulama?.let { context.packageManager.getLaunchIntentForPackage(it.second) }
                if (uygulama == null || niyet == null) {
                    teslim(Cevap(Olay.Hata, "$ad uygulamasını bulamadım"))
                } else {
                    ekranAc(niyet.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    teslim(Cevap(Olay.Basari, "${uygulama.first} açıldı"))
                }
            }
            return true
        }
        return false
    }

    private fun uygulamaBul(ad: String): Pair<String, String>? {
        val pm = context.packageManager
        val liste = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { Pair(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .distinctBy { it.second }
        val hedef = kompakt(ad)
        if (hedef.length < 2) return null
        liste.firstOrNull { kompakt(it.first) == hedef }?.let { return it }
        liste.filter { kompakt(it.first).length >= 3 && hedef.contains(kompakt(it.first)) }
            .maxByOrNull { kompakt(it.first).length }?.let { return it }
        if (hedef.length >= 3) {
            liste.filter { kompakt(it.first).contains(hedef) }.minByOrNull { it.first.length }?.let { return it }
        }
        return null
    }

    private fun ara(sorgu: String, teslim: (Cevap) -> Unit) {
        Arama.ara(sorgu) { sonuc, internetYok ->
            teslim(
                when {
                    sonuc != null -> Cevap(Olay.Basari, sonuc)
                    internetYok -> Cevap(Olay.Hata, "İnternete ulaşamadım")
                    else -> Cevap(Olay.Hata, "$sorgu için bir sonuç bulamadım")
                },
            )
        }
    }

    private fun bildirim(t: List<String>): Cevap {
        if (t.any { it.startsWith("otomatik") }) {
            val kapat = t.any { it.startsWith("kapat") || it.startsWith("durdur") }
            Ayar.otomatikBildirimYaz(context, !kapat)
            return Cevap(Olay.Basari, if (kapat) "Otomatik bildirim okuma kapatıldı" else "Otomatik bildirim okuma açıldı")
        }
        val izinliler = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
        if (!izinliler.contains(context.packageName)) {
            ekranAc(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return Cevap(Olay.OnayGerekli, "Bildirimleri okumak için bildirim erişimi izni gerekiyor. İzin ekranını açtım, DesteX'i bul ve izin ver")
        }
        val liste = BildirimDinleyici.ornek?.aktifOzet().orEmpty()
        if (liste.isEmpty()) return Cevap(Olay.Basari, "Okunacak bildirim yok")
        return Cevap(Olay.Basari, "${liste.size} bildirim var. " + liste.joinToString(". "))
    }

    private fun fener(ac: Boolean): Cevap {
        return try {
            val kimlik = kamera.cameraIdList.firstOrNull {
                kamera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return Cevap(Olay.Hata, "Bu cihazda fener bulunamadı")
            kamera.setTorchMode(kimlik, ac)
            Cevap(Olay.Basari, if (ac) "Fener açıldı" else "Fener kapatıldı")
        } catch (e: Exception) {
            Cevap(Olay.Hata, "Feneri çalıştıramadım, kamera başka bir uygulamada açık olabilir")
        }
    }

    private fun sesAyari(t: List<String>): Cevap? {
        val yon = when {
            azalt(t) -> AudioManager.ADJUST_LOWER
            artir(t) -> AudioManager.ADJUST_RAISE
            else -> return null
        }
        val simdi = ses.getStreamVolume(AudioManager.STREAM_MUSIC)
        val en = ses.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (yon == AudioManager.ADJUST_LOWER && simdi <= 1) {
            return Cevap(Olay.Basari, "Ses en düşük seviyede, daha fazla kısmıyorum")
        }
        ses.adjustStreamVolume(AudioManager.STREAM_MUSIC, yon, AudioManager.FLAG_SHOW_UI)
        val yuzde = ses.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / en
        return Cevap(Olay.Basari, "Ses yüzde $yuzde")
    }

    private fun parlaklik(t: List<String>): Cevap {
        if (!Settings.System.canWrite(context)) {
            ekranAc(
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return Cevap(Olay.OnayGerekli, "Parlaklık için özel izin gerekiyor. İzin ekranını açtım, DesteX'e izin ver, sonra komutu tekrarla")
        }
        val kaynak = context.contentResolver
        val simdi = Settings.System.getInt(kaynak, Settings.System.SCREEN_BRIGHTNESS, 128)
        val yeni = when {
            azalt(t) -> (simdi - 40).coerceAtLeast(10)
            artir(t) -> (simdi + 40).coerceAtMost(255)
            else -> return Cevap(Olay.Hata, "Parlaklığı artır mı azalt mı, anlayamadım")
        }
        Settings.System.putInt(kaynak, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(kaynak, Settings.System.SCREEN_BRIGHTNESS, yeni)
        return Cevap(Olay.Basari, "Parlaklık yüzde ${yeni * 100 / 255}")
    }

    private fun pilDurumu(): Cevap {
        val yuzde = pil.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (yuzde <= 0 || yuzde > 100) return Cevap(Olay.Hata, "Pil bilgisini okuyamadım")
        val durum = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val sarj = durum == BatteryManager.BATTERY_STATUS_CHARGING || durum == BatteryManager.BATTERY_STATUS_FULL
        return Cevap(Olay.Basari, "Pil yüzde $yuzde" + if (sarj) ", şarj oluyor" else "")
    }

    private fun wifi(): Cevap {
        ekranAc(Intent(Settings.Panel.ACTION_WIFI).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return Cevap(Olay.Basari, "Wi-Fi panelini açtım. Android, uygulamaların Wi-Fi'ı doğrudan açıp kapatmasına izin vermiyor")
    }
}
