package com.destex.app

import android.content.Context

object Durum {
    @Volatile
    var dinliyor = false
}

object Ayar {
    private const val DOSYA = "destex"
    private const val BILDIRIM_OTOMATIK = "bildirim_otomatik"

    fun otomatikBildirim(context: Context): Boolean =
        context.getSharedPreferences(DOSYA, Context.MODE_PRIVATE).getBoolean(BILDIRIM_OTOMATIK, true)

    fun otomatikBildirimYaz(context: Context, acik: Boolean) {
        context.getSharedPreferences(DOSYA, Context.MODE_PRIVATE).edit().putBoolean(BILDIRIM_OTOMATIK, acik).apply()
    }
}
