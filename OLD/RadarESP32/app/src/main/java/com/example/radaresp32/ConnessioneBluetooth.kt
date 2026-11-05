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

    // 👉 Stati radar aggiornabili
    var angolo by remember { mutableStateOf(0f) }
    var distanza by remember { mutableStateOf(-1f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 🔘 Pulsante connessione + messaggio
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

                                    // 🎯 Parsing angolo e distanza
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

            Text(text = messaggio, color = Color.Green)
        }

        // 🟢 Canvas radar grafico
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
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
    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height)
        val raggioMassimo = size.width / 2f

        // 🔄 Effetto fading verde (traccia radar)
        drawRect(Color.Black.copy(alpha = 0.05f), size = size)

        // 🎯 Archi concentrici
        for (i in 1..4) {
            drawArc(
                color = Color.Green,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(center.x - raggioMassimo * i / 4, center.y - raggioMassimo * i / 4),
                size = Size(raggioMassimo * i / 2, raggioMassimo * i / 2),
                style = Stroke(width = 2f)
            )
        }

        // 📐 Linee fisse ogni 30°
        for (a in listOf(0f, 30f, 60f, 90f, 120f, 150f, 180f)) {
            val rad = Math.toRadians(a.toDouble())
            val endX = center.x + raggioMassimo * cos(rad).toFloat()
            val endY = center.y - raggioMassimo * sin(rad).toFloat()
            drawLine(Color.Green, center, Offset(endX, endY), strokeWidth = 2f)
        }

        // 🔁 Linea rotante verde
        val angleRad = Math.toRadians(angolo.toDouble())
        val endX = center.x + raggioMassimo * cos(angleRad).toFloat()
        val endY = center.y - raggioMassimo * sin(angleRad).toFloat()
        drawLine(Color.Green, center, Offset(endX, endY), strokeWidth = 4f)

        // 🔴 Punto ostacolo (se valido)
        if (distanza in 0.0..400.0) {
            val scala = raggioMassimo / 400f
            val px = center.x + distanza * scala * cos(angleRad).toFloat()
            val py = center.y - distanza * scala * sin(angleRad).toFloat()
            drawCircle(Color.Red, radius = 10f, center = Offset(px, py))
        }
    }
}
