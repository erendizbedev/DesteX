package com.destex.app

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Türkçe Vikipedi üzerinden arama yapar ve kısa özet döndürür. */
object Arama {
    fun ara(soru: String, teslim: (String?, Boolean) -> Unit) {
        Thread {
            var internetYok = false
            val sonuc = try {
                vikipedi(soru)
            } catch (e: java.io.IOException) {
                internetYok = true
                null
            } catch (e: Exception) {
                null
            }
            Handler(Looper.getMainLooper()).post { teslim(sonuc, internetYok) }
        }.start()
    }

    private fun al(adres: String): String {
        val c = URL(adres).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "DesteX/1.0 (Android)")
        return try {
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun kodla(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun vikipedi(soru: String): String? {
        val bulunan = JSONObject(
            al("https://tr.wikipedia.org/w/api.php?action=query&list=search&srlimit=1&format=json&srsearch=" + kodla(soru)),
        ).getJSONObject("query").getJSONArray("search")
        if (bulunan.length() == 0) return null
        val baslik = bulunan.getJSONObject(0).getString("title")
        val ozet = JSONObject(al("https://tr.wikipedia.org/api/rest_v1/page/summary/" + kodla(baslik)))
        val metin = ozet.optString("extract").trim()
        if (metin.isEmpty()) return null
        return kisalt(metin)
    }

    private fun kisalt(metin: String): String {
        val cumleler = metin.split(Regex("(?<=[.!?])\\s+"))
        val sb = StringBuilder()
        for (c in cumleler) {
            if (sb.length + c.length > 450 && sb.isNotEmpty()) break
            sb.append(c).append(' ')
            if (sb.length > 250) break
        }
        return sb.toString().trim()
    }
}
