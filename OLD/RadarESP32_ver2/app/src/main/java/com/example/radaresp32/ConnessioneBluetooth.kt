package com.example.radaresp32

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import java.io.InputStream
import java.util.*
import kotlin.math.*

@Composable
fun ConnessioneBluetooth(btAdapter: BluetoothAdapter?) {
    val scope = rememberCoroutineScope()
    var connesso by remember { mutableStateOf(false) }
    var messaggio by remember { mutableStateOf("📡 In attesa di connessione...") }

    var angolo by remember { mutableStateOf(0f) }
    var distanza by remember { mutableStateOf(-1f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 🔘 Pulsante connessione + SOLO messaggio (NO angolo/distanza)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = {
                    scope.launch {
                        val device: BluetoothDevice? = btAdapter?.bondedDevices?.find {
                            it.name == "ESP32_Radar"
                        }
                        if (device != null) {
                            try {
                                val uuid = device.uuids.first().uuid
                                val socket = device.createRfcommSocketToServiceRecord(uuid)
                                socket.connect()
                                connesso = true
                                messaggio = "✅ Connessione riuscita"
                                riceviDati(socket.inputStream) { nuovaLinea ->
                                    messaggio = nuovaLinea

                                    val angleMatch = Regex("""Angolo:\s*(\d+)""").find(nuovaLinea)
                                    val distMatch = Regex("""Distanza:\s*([0-9.]+|OUT)""").find(nuovaLinea)

                                    angleMatch?.groupValues?.get(1)?.toFloatOrNull()?.let {
                                        angolo = it
                                    }

                                    distMatch?.groupValues?.get(1)?.let {
                                        distanza = if (it.contains("OUT")) -1f
                                        else it.toFloatOrNull() ?: -1f
                                    }
                                }
                            } catch (e: Exception) {
                                messaggio = "❌ Errore: ${e.message}"
                            }
                        } else {
                            messaggio = "⚠️ ESP32_Radar non trovato"
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Green)
            ) {
                Text("Connetti al radar", color = Color.Black)
            }

            // ✅ Mostra solo lo stato della connessione
            Text(text = messaggio, color = Color.Green)
        }

        // 🔁 Radar + dati numerici più in alto
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 8.dp, end = 8.dp, top = 8.dp), // Sposta tutto in alto
            contentAlignment = Alignment.TopCenter
        ) {
            RadarCanvas(angolo = angolo, distanza = distanza)
        }
    }
}


// 📡 Ricezione dati Bluetooth asincrona
fun riceviDati(input: InputStream, onMessage: (String) -> Unit) {
    CoroutineScope(Dispatchers.IO).launch {
        val buffer = ByteArray(1024)
        var line = ""
        while (true) {
            val bytes = input.read(buffer)
            line += String(buffer, 0, bytes)
            if (line.contains("\n")) {
                val lines = line.split("\n")
                lines.dropLast(1).forEach { onMessage(it.trim()) }
                line = lines.last()
            }
        }
    }
}

// 🎨 Canvas radar dinamico ispirato a Processing
@Composable
fun RadarCanvas(angolo: Float, distanza: Float) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val center = Offset(size.width / 2f, size.height * 0.70f)  // ancora più in alto
            val raggioMassimo = size.width / 2f

            drawRect(Color.Black.copy(alpha = 0.05f), size = size)

            val distanze = listOf(100, 200, 300, 400)
            for (i in distanze.indices) {
                val r = raggioMassimo * (i + 1) / distanze.size
                drawArc(
                    color = Color.Green,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(center.x - r, center.y - r),
                    size = Size(r * 2, r * 2),
                    style = Stroke(width = 2f)
                )

                drawContext.canvas.nativeCanvas.drawText(
                    "${distanze[i]} cm",
                    center.x + 8,
                    center.y - r + 4,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.GREEN
                        textSize = 28f
                        isAntiAlias = true
                    }
                )
            }

            val angoli = listOf(0, 30, 60, 90, 120, 150, 180)
            for (a in angoli) {
                val rad = Math.toRadians(a.toDouble())
                val endX = center.x + raggioMassimo * cos(rad).toFloat()
                val endY = center.y - raggioMassimo * sin(rad).toFloat()
                drawLine(Color.Green, center, Offset(endX, endY), strokeWidth = 2f)

                val labelR = raggioMassimo * 1.05f
                val lx = center.x + labelR * cos(rad).toFloat()
                val ly = center.y - labelR * sin(rad).toFloat()
                drawContext.canvas.nativeCanvas.drawText(
                    "$a°",
                    lx - 20,
                    ly,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.GREEN
                        textSize = 28f
                        isAntiAlias = true
                    }
                )
            }

            val angleRad = Math.toRadians(angolo.toDouble())
            val endX = center.x + raggioMassimo * cos(angleRad).toFloat()
            val endY = center.y - raggioMassimo * sin(angleRad).toFloat()
            drawLine(Color.Green, center, Offset(endX, endY), strokeWidth = 4f)

            if (distanza in 0.0..400.0) {
                val scala = raggioMassimo / 400f
                val px = center.x + distanza * scala * cos(angleRad).toFloat()
                val py = center.y - distanza * scala * sin(angleRad).toFloat()
                drawCircle(Color.Red, radius = 10f, center = Offset(px, py))
            }
        }

        // 📊 Dati numerici — sollevati
        Column(
            modifier = Modifier
                .padding(bottom = 120.dp),  // 🔼 alza tutto
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Angolo: ${angolo.toInt()}°",
                color = Color.Green,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(8.dp))  // Spazio tra le righe
            Text(
                text = if (distanza >= 0) "Distanza: ${"%.1f".format(distanza)} cm" else "Distanza: OUT OF RANGE",
                color = Color.Green,
                style = MaterialTheme.typography.bodyLarge
            )
        }

    }
}



