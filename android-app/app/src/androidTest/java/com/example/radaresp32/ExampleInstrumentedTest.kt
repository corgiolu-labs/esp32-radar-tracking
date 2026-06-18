package com.example.radaresp32

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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import java.io.InputStream
import java.util.*
import kotlin.math.*

@Composable
fun ConnessioneBluetooth(btAdapter: BluetoothAdapter?) {
    val scope = rememberCoroutineScope()
    var connesso by remember { mutableStateOf(false) }
    var messaggio by remember { mutableStateOf("\uD83D\uDCE1 In attesa di connessione...") }

    var angolo by remember { mutableStateOf(0f) }
    var distanza by remember { mutableStateOf(-1f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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
                                val uuid = device.uuids?.firstOrNull()?.uuid
                                    ?: UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
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

            Text(text = messaggio, color = Color.Green)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RadarCanvas(angolo = angolo, distanza = distanza)

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Angolo: ${angolo.toInt()}°",
                color = Color.Green,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (distanza >= 0)
                    "Distanza: ${"%.1f".format(distanza)} cm"
                else "Distanza: OUT OF RANGE",
                color = Color.Green,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

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

@Composable
fun RadarCanvas(angolo: Float, distanza: Float) {
    val trailDuration = 1000L
    val maxTrailLength = 50
    val now = System.currentTimeMillis()
    val lineaVerdeTraccia = remember { mutableStateListOf<Pair<Float, Long>>() }
    val rettangoloTraccia = remember { mutableStateListOf<Triple<Float, Float, Long>>() }

    lineaVerdeTraccia.add(Pair(angolo, now))
    if (lineaVerdeTraccia.size > maxTrailLength) lineaVerdeTraccia.removeFirst()

    if (distanza in 0.0..400.0) {
        rettangoloTraccia.add(Triple(angolo, distanza, now))
        if (rettangoloTraccia.size > maxTrailLength) rettangoloTraccia.removeFirst()
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp)
    ) {
        drawRadar(this, angolo, distanza, lineaVerdeTraccia, rettangoloTraccia, trailDuration)
    }
}

fun drawRadar(
    scope: DrawScope,
    angolo: Float,
    distanza: Float,
    lineaVerdeTraccia: List<Pair<Float, Long>>,
    rettangoloTraccia: List<Triple<Float, Float, Long>>,
    trailDuration: Long
) = with(scope) {
    val now = System.currentTimeMillis()
    val center = Offset(size.width / 2f, size.height * 0.70f)
    val raggioMassimo = size.width / 2f
    val raggioLinea = raggioMassimo * (500f / 400f)

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
    }

    val angoli = listOf(0, 30, 60, 90, 120, 150, 180)
    for (a in angoli) {
        val rad = Math.toRadians(a.toDouble())
        val endX = center.x + raggioMassimo * cos(rad).toFloat()
        val endY = center.y - raggioMassimo * sin(rad).toFloat()
        drawLine(Color.Green, center, Offset(endX, endY), strokeWidth = 2f)
    }

    lineaVerdeTraccia.forEach { (ang, time) ->
        val age = now - time
        if (age < trailDuration) {
            val alpha = 1f - (age.toFloat() / trailDuration)
            val rad = Math.toRadians(ang.toDouble())
            val end = Offset(
                center.x + raggioLinea * cos(rad).toFloat(),
                center.y - raggioLinea * sin(rad).toFloat()
            )
            drawLine(
                color = Color.Green.copy(alpha = alpha),
                start = center,
                end = end,
                strokeWidth = 4f
            )
        }
    }

    rettangoloTraccia.forEach { (ang, dist, time) ->
        val age = now - time
        if (age < trailDuration) {
            val alpha = 1f - (age.toFloat() / trailDuration)
            val scala = raggioMassimo / 400f
            val rad = Math.toRadians(ang.toDouble())
            val altezza = dist * scala
            val base = center
            val top = Offset(
                center.x + altezza * cos(rad).toFloat(),
                center.y - altezza * sin(rad).toFloat()
            )
            val lunghezza = 10f
            drawRect(
                color = Color.Red.copy(alpha = alpha),
                topLeft = Offset(top.x - lunghezza / 2, top.y),
                size = Size(lunghezza, altezza)
            )
        }
    }
}