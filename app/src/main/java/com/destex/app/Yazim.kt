package com.destex.app

import java.util.Locale

/** Konuşulan metni Türkçe yazım kurallarına göre yazıya çevirir. */
object Yazim {
    private val TR = Locale("tr", "TR")

    fun sade(s: String) = s.lowercase(TR)
        .replace('ğ', 'g').replace('ı', 'i').replace('ş', 's')
        .replace('ö', 'o').replace('ü', 'u').replace('ç', 'c')

    private val noktalama = mapOf(
        "nokta" to ".",
        "virgul" to ",",
        "soru isareti" to "?",
        "unlem" to "!",
        "unlem isareti" to "!",
        "iki nokta" to ":",
        "iki nokta ust uste" to ":",
        "noktali virgul" to ";",
        "uc nokta" to "...",
        "tire" to "-",
        "yeni satir" to "\n",
        "alt satir" to "\n",
        "yeni paragraf" to "\n\n",
    )

    private val kod = mapOf(
        "parantez ac" to "(",
        "parantez kapat" to ")",
        "kose parantez ac" to "[",
        "koseli parantez ac" to "[",
        "kose parantez kapat" to "]",
        "koseli parantez kapat" to "]",
        "suslu parantez ac" to "{",
        "suslu parantez kapat" to "}",
        "esittir" to "=",
        "cift esittir" to "==",
        "esit degil" to "!=",
        "buyuktur" to ">",
        "kucuktur" to "<",
        "buyuk esittir" to ">=",
        "kucuk esittir" to "<=",
        "arti" to "+",
        "eksi" to "-",
        "carpi" to "*",
        "bolu" to "/",
        "tirnak" to "\"",
        "cift tirnak" to "\"",
        "tek tirnak" to "'",
        "alt cizgi" to "_",
        "ters bolu" to "\\",
        "dikey cizgi" to "|",
        "ve isareti" to "&",
        "kare" to "#",
        "at isareti" to "@",
        "dolar" to "$",
        "yuzde" to "%",
        "tab" to "\t",
        "bosluk" to " ",
    )

    private val soruKelimeleri = setOf(
        "mi", "mu", "mı", "mü", "neden", "nasil", "nasıl", "kim", "kimin", "nerede", "nereye", "nereden",
        "ne", "nedir", "hangi", "kac", "kaç", "niye", "niçin", "nicin", "ne zaman",
    )

    private fun soruMu(kelimeler: List<String>): Boolean {
        val son = sade(kelimeler.lastOrNull().orEmpty()).trim('.', ',', '?', '!')
        if (son in setOf("mi", "mu", "misin", "misiniz", "miyim", "miyiz", "midir", "mudur")) return true
        if (son.startsWith("mi") || son.startsWith("mu")) {
            if (son.length <= 8) return true
        }
        val ilk = sade(kelimeler.firstOrNull().orEmpty())
        return ilk in setOf("neden", "nasil", "kim", "kimin", "nerede", "nereye", "nereden", "hangi", "kac", "niye", "nicin", "ne")
    }

    private fun bagla(parcalar: List<Pair<String, Boolean>>, kodModu: Boolean): String {
        val sb = StringBuilder()
        var bosluksuz = true
        for ((metin, simge) in parcalar) {
            if (simge) {
                val sagaYapisik = metin in setOf(".", ",", "?", "!", ":", ";", "...", ")", "]", "}")
                val solaBosluk = !sagaYapisik && !kodModu && metin != "\n" && metin != "\n\n"
                if (solaBosluk && sb.isNotEmpty() && !bosluksuz) sb.append(' ')
                sb.append(metin)
                bosluksuz = metin in setOf("\n", "\n\n", "(", "[", "{", "\t", " ") || kodModu
            } else {
                if (sb.isNotEmpty() && !bosluksuz) sb.append(' ')
                sb.append(metin)
                bosluksuz = false
            }
        }
        return sb.toString()
    }

    /** Konuşulan kelimeleri (noktalama sözcükleri dahil) metne çevirir. */
    fun yaz(konusulan: String, kodModu: Boolean): String {
        val kelimeler = konusulan.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (kelimeler.isEmpty()) return ""
        val sozluk = if (kodModu) kod + noktalama else noktalama
        val normal = kelimeler.map { sade(it) }
        val parcalar = mutableListOf<Pair<String, Boolean>>()
        var i = 0
        while (i < kelimeler.size) {
            var eslesti = false
            for (uzun in 3 downTo 1) {
                if (i + uzun > kelimeler.size) continue
                val anahtar = normal.subList(i, i + uzun).joinToString(" ")
                val simge = sozluk[anahtar]
                if (simge != null) {
                    parcalar.add(Pair(simge, true))
                    i += uzun
                    eslesti = true
                    break
                }
            }
            if (!eslesti) {
                parcalar.add(Pair(if (kodModu) kelimeler[i] else kelimeler[i], false))
                i++
            }
        }
        return bagla(parcalar, kodModu)
    }

    /** Düz yazıda: ilk harfi büyütür, sonda noktalama yoksa nokta veya soru işareti koyar. */
    fun cumleYap(metin: String): String {
        var s = metin.trim()
        if (s.isEmpty()) return s
        s = s.replaceFirstChar { it.titlecase(TR) }
        val son = s.last()
        if (son == '.' || son == '?' || son == '!' || son == ':' || son == ';' || son == '\n') return s
        val kelimeler = s.split(Regex("\\s+"))
        return s + if (soruMu(kelimeler)) "?" else "."
    }
}
