package com.destex.app

import android.content.Context
import org.json.JSONArray

object Durum {
    @Volatile
    var dinliyor = false
}

object Ayar {
    private const val DOSYA = "destex"

    private fun tercih(c: Context) = c.getSharedPreferences(DOSYA, Context.MODE_PRIVATE)

    fun otomatikBildirim(c: Context): Boolean = tercih(c).getBoolean("bildirim_otomatik", true)
    fun otomatikBildirimYaz(c: Context, acik: Boolean) = tercih(c).edit().putBoolean("bildirim_otomatik", acik).apply()

    fun uyandirma(c: Context): Boolean = tercih(c).getBoolean("uyandirma", false)
    fun uyandirmaYaz(c: Context, acik: Boolean) = tercih(c).edit().putBoolean("uyandirma", acik).apply()

    fun ekranOkuyucu(c: Context): Boolean = tercih(c).getBoolean("ekran_okuyucu", false)
    fun ekranOkuyucuYaz(c: Context, acik: Boolean) = tercih(c).edit().putBoolean("ekran_okuyucu", acik).apply()

    fun sesAdi(c: Context): String? = tercih(c).getString("ses_adi", null)
    fun sesAdiYaz(c: Context, ad: String) = tercih(c).edit().putString("ses_adi", ad).apply()

    fun hiz(c: Context): Float = tercih(c).getFloat("konusma_hizi", 1.0f)
    fun hizYaz(c: Context, hiz: Float) = tercih(c).edit().putFloat("konusma_hizi", hiz).apply()
}

object Notlar {
    private const val ANAHTAR = "notlar"

    private fun tercih(c: Context) = c.getSharedPreferences("destex", Context.MODE_PRIVATE)

    fun hepsi(c: Context): List<String> {
        val d = try {
            JSONArray(tercih(c).getString(ANAHTAR, "[]") ?: "[]")
        } catch (e: Exception) {
            JSONArray()
        }
        return List(d.length()) { d.getString(it) }
    }

    fun ekle(c: Context, metin: String) = yaz(c, hepsi(c) + metin)

    fun sil(c: Context) = yaz(c, emptyList())

    private fun yaz(c: Context, liste: List<String>) {
        val d = JSONArray()
        liste.forEach { d.put(it) }
        tercih(c).edit().putString(ANAHTAR, d.toString()).apply()
    }
}
