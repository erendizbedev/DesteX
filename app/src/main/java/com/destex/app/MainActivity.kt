package com.destex.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private lateinit var geri: Geribildirim
    private lateinit var tanima: SesTanima
    private lateinit var komut: KomutIsleyici

    private var durum by mutableStateOf("Hazır, seni bekliyorum")
    private var duyulan by mutableStateOf("")

    private val izinIstegi =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { verildi ->
            if (verildi) dinle() else geri.bildir(Olay.Hata, "Mikrofon izni verilmedi")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        geri = Geribildirim(this)
        komut = KomutIsleyici(this) { startActivity(it) }
        tanima = SesTanima(
            this,
            kismi = { duyulan = it },
            sonuc = {
                duyulan = it
                durum = "Hazır, seni bekliyorum"
                val cevap = komut.isle(it)
                if (cevap != null) geri.bildir(cevap.first, cevap.second)
                else geri.bildir(Olay.DinlemeBitti, "Duydum: $it")
            },
            hata = {
                durum = "Hazır, seni bekliyorum"
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
        if (!tanima.kullanilabilir) {
            geri.bildir(Olay.Hata, "Bu cihazda ses tanıma servisi yok")
            return
        }
        val izinVar = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (izinVar) dinle() else izinIstegi.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun dinle() {
        durum = "Dinliyorum"
        duyulan = ""
        geri.bildir(Olay.DinlemeBasladi, "Dinliyorum")
        tanima.baslat()
    }

    override fun onDestroy() {
        tanima.kapat()
        geri.kapat()
        super.onDestroy()
    }
}

@Composable
fun AnaEkran(durum: String, duyulan: String, konusBasildi: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Renk.Zemin)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("DesteX", color = Renk.Metin, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(durum, color = Renk.Vurgu, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Button(
            onClick = konusBasildi,
            modifier = Modifier
                .size(200.dp)
                .semantics { contentDescription = "Dinlemeyi başlat" },
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
                .padding(16.dp),
        ) {
            Text("Duyduğum", color = Renk.Soluk, fontSize = 16.sp)
            Text(
                if (duyulan.isBlank()) "Henüz bir şey duymadım" else duyulan,
                color = Renk.Metin,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
