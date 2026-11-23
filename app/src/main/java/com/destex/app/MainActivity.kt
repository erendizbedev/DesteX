package com.destex.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.remember
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        geri = Geribildirim(this)
        setContent { DesteXTema { AnaEkran(geri) } }
    }

    override fun onDestroy() {
        geri.kapat()
        super.onDestroy()
    }
}

@Composable
fun AnaEkran(geri: Geribildirim) {
    var sonIslem by remember { mutableStateOf("Henüz işlem yok") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Renk.Zemin)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("DesteX", color = Renk.Metin, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Hazır, seni bekliyorum", color = Renk.Vurgu, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Button(
            onClick = {
                sonIslem = "Dinleme denemesi"
                geri.bildir(Olay.DinlemeBasladi, "Dinliyorum")
            },
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
            Text("Son işlem", color = Renk.Soluk, fontSize = 16.sp)
            Text(sonIslem, color = Renk.Metin, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(4.dp))
    }
}
