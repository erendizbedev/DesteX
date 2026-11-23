package com.destex.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private lateinit var geri: Geribildirim
    private lateinit var tanima: SesTanima
    private lateinit var komut: KomutIsleyici

    private var durum by mutableStateOf(HAZIR)
    private var duyulan by mutableStateOf("")
    private var dinliyor = false

    private val izinIstegi =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { verildi ->
            if (verildi) {
                dinle()
            } else {
                geri.bildir(Olay.Hata, "Mikrofon izni verilmedi. Android ayarlarından DesteX için mikrofona izin ver")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        geri = Geribildirim(this)
        komut = KomutIsleyici(this) { niyet ->
            try {
                startActivity(niyet)
            } catch (e: Exception) {
                geri.bildir(Olay.Hata, "Bu ekranı açamadım")
            }
        }
        tanima = SesTanima(
            this,
            kismi = { duyulan = it },
            sonuc = {
                dinliyor = false
                duyulan = it
                durum = HAZIR
                val cevap = komut.isle(it)
                if (cevap != null) {
                    geri.bildir(cevap.first, cevap.second)
                } else {
                    geri.bildir(Olay.Hata, "Duydum: $it. Bu komutu bilmiyorum. Fener, pil, ses, parlaklık veya Wi-Fi de")
                }
            },
            hata = {
                dinliyor = false
                durum = HAZIR
                geri.bildir(Olay.Hata, it)
            },
        )
        setContent {
            DesteXTema {
                AnaEkran(durum, duyulan) { konusBasildi() }
            }
        }
    }

    private fun konusBasildi() {
        if (dinliyor) {
            tanima.iptal()
            dinliyor = false
            durum = HAZIR
            geri.bildir(Olay.DinlemeBitti, null)
            return
        }
        if (!tanima.kullanilabilir) {
            geri.bildir(Olay.Hata, "Bu cihazda ses tanıma servisi yok")
            return
        }
        val izinVar = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (izinVar) dinle() else izinIstegi.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun dinle() {
        dinliyor = true
        durum = "Dinleniyor"
        duyulan = ""
        geri.bildir(Olay.DinlemeBasladi, null)
        tanima.baslat()
    }

    override fun onStop() {
        if (dinliyor) {
            tanima.iptal()
            dinliyor = false
            durum = HAZIR
        }
        super.onStop()
    }

    override fun onDestroy() {
        tanima.kapat()
        geri.kapat()
        super.onDestroy()
    }

    companion object {
        const val HAZIR = "Hazır, seni bekliyorum"
    }
}

@Composable
fun AnaEkran(durum: String, duyulan: String, konusBasildi: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Renk.Zemin)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("DesteX", color = Renk.Metin, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(
            durum,
            color = Renk.Vurgu,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Button(
            onClick = konusBasildi,
            modifier = Modifier.size(200.dp),
            shape = CircleShape,
            border = BorderStroke(6.dp, Renk.Vurgu),
            colors = ButtonDefaults.buttonColors(containerColor = Renk.Kart, contentColor = Renk.Vurgu),
        ) {
            Text("Konuş", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Renk.Kart, RoundedCornerShape(20.dp))
                .border(2.dp, Renk.Soluk, RoundedCornerShape(20.dp))
                .padding(16.dp),
        ) {
            Text("Duyduğum", color = Renk.Soluk, fontSize = 20.sp)
            Text(
                if (duyulan.isBlank()) "Henüz bir şey duymadım" else duyulan,
                color = Renk.Metin,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
