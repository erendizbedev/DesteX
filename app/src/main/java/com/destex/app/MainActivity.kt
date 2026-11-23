package com.destex.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private var notlar by mutableStateOf(emptyList<String>())
    private var erisim by mutableStateOf(false)
    private var sonra: (() -> Unit)? = null

    private val izinler =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
            val devam = sonra
            sonra = null
            if (micVar()) {
                devam?.invoke()
            } else {
                Asistan.geri.bildir(Olay.Hata, "Mikrofon izni verilmedi. Android ayarlarından DesteX için mikrofona izin ver")
            }
        }

    private fun micVar() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun micIzniIste(devam: () -> Unit) {
        if (micVar()) {
            devam()
            return
        }
        sonra = devam
        val liste = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) liste.add(Manifest.permission.POST_NOTIFICATIONS)
        izinler.launch(liste.toTypedArray())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Asistan.baslat(this)
        Asistan.arkaPlanIstegi = { moveTaskToBack(true) }
        if (micVar()) Asistan.uyandirmaServisiBaslat()
        setContent {
            DesteXTema {
                if (Asistan.ekran == "notlar") {
                    BackHandler { Asistan.ekran = "ana" }
                    NotlarEkrani(
                        notlar = notlar,
                        oku = { notlariOku() },
                        sil = {
                            Notlar.sil(this)
                            notlar = emptyList()
                            Asistan.geri.bildir(Olay.Basari, "Notların hepsi silindi")
                        },
                        geri = { Asistan.ekran = "ana" },
                    )
                } else {
                    AnaEkran(
                        durum = Asistan.durum,
                        duyulan = Asistan.duyulan,
                        uyandirma = Asistan.uyandirma,
                        erisim = erisim,
                        konusBasildi = { konusBasildi() },
                        notlarAc = {
                            notlar = Notlar.hepsi(this)
                            Asistan.ekran = "notlar"
                        },
                        uyandirmaDegistir = { uyandirmaDegistir() },
                        erisimAc = {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                    )
                }
            }
        }
        niyetiIsle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        niyetiIsle(intent)
    }

    private fun niyetiIsle(niyet: Intent?) {
        if (Asistan.ekran == "notlar") notlar = Notlar.hepsi(this)
        if (niyet?.getBooleanExtra(DINLE, false) == true) {
            niyet.removeExtra(DINLE)
            micIzniIste { Asistan.dinle() }
        }
    }

    override fun onResume() {
        super.onResume()
        erisim = EkranServisi.ornek != null
        if (Asistan.ekran == "notlar") notlar = Notlar.hepsi(this)
    }

    private fun konusBasildi() {
        if (Asistan.dinliyor()) {
            Asistan.iptal()
        } else {
            micIzniIste { Asistan.dinle() }
        }
    }

    private fun uyandirmaDegistir() {
        if (Asistan.uyandirma) {
            Asistan.uyandirmaDegistir(false)
        } else {
            micIzniIste { Asistan.uyandirmaDegistir(true) }
        }
    }

    private fun notlariOku() {
        val liste = notlar
        Asistan.geri.bildir(
            Olay.Basari,
            if (liste.isEmpty()) {
                "Hiç notun yok"
            } else {
                "${liste.size} notun var. " + liste.takeLast(10).mapIndexed { i, s -> "${i + 1}. $s" }.joinToString(" ")
            },
        )
    }

    override fun onDestroy() {
        Asistan.arkaPlanIstegi = null
        super.onDestroy()
    }

    companion object {
        const val DINLE = "dinle"
    }
}

@Composable
fun AnaEkran(
    durum: String,
    duyulan: String,
    uyandirma: Boolean,
    erisim: Boolean,
    konusBasildi: () -> Unit,
    notlarAc: () -> Unit,
    uyandirmaDegistir: () -> Unit,
    erisimAc: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Renk.Zemin)
            .safeDrawingPadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("DesteX", color = Renk.Metin, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(durum, color = Renk.Vurgu, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
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
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KucukDugme("Notlar", Modifier.weight(1f), notlarAc)
            KucukDugme(if (uyandirma) "Uyandırma açık" else "Uyandırma kapalı", Modifier.weight(1f), uyandirmaDegistir)
            KucukDugme(if (erisim) "Ekran erişimi açık" else "Ekran erişimi kapalı", Modifier.weight(1f), erisimAc)
        }
        Button(
            onClick = konusBasildi,
            modifier = Modifier.size(170.dp),
            shape = CircleShape,
            border = BorderStroke(6.dp, Renk.Vurgu),
            colors = ButtonDefaults.buttonColors(containerColor = Renk.Kart, contentColor = Renk.Vurgu),
        ) {
            Text("Konuş", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun KucukDugme(yazi: String, modifier: Modifier, tikla: () -> Unit) {
    Button(
        onClick = tikla,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, Renk.Cerceve),
        colors = ButtonDefaults.buttonColors(containerColor = Renk.Kart, contentColor = Renk.Vurgu),
    ) {
        Text(yazi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

@Composable
fun NotlarEkrani(notlar: List<String>, oku: () -> Unit, sil: () -> Unit, geri: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Renk.Zemin)
            .safeDrawingPadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Notlar", color = Renk.Metin, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Renk.Kart, RoundedCornerShape(20.dp))
                .border(2.dp, Renk.Soluk, RoundedCornerShape(20.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (notlar.isEmpty()) {
                Text("Hiç notun yok", color = Renk.Soluk, fontSize = 22.sp)
            } else {
                Text("${notlar.size} not, sonuncular", color = Renk.Soluk, fontSize = 18.sp)
                notlar.takeLast(5).reversed().forEach {
                    Text(
                        it,
                        color = Renk.Metin,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KucukDugme("Oku", Modifier.weight(1f), oku)
            KucukDugme("Hepsini sil", Modifier.weight(1f), sil)
            KucukDugme("Geri", Modifier.weight(1f), geri)
        }
    }
}
