package com.destex.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Renk {
    val Zemin = Color(0xFF0A0A0D)
    val Kart = Color(0xFF15151B)
    val Cerceve = Color(0xFF3A3A44)
    val Metin = Color(0xFFF5F5F7)
    val Soluk = Color(0xFFB8B8C4)
    val Vurgu = Color(0xFFFFD27A)
    val Hata = Color(0xFFFF8A80)
}

@Composable
fun DesteXTema(icerik: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Renk.Zemin,
            surface = Renk.Kart,
            primary = Renk.Vurgu,
            onBackground = Renk.Metin,
            onSurface = Renk.Metin,
            onPrimary = Renk.Zemin,
            error = Renk.Hata,
        ),
        content = icerik,
    )
}
