package com.example.radaresp32
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment


import androidx.compose.ui.geometry.Size


import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*

import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import android.bluetooth.BluetoothAdapter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun ConnessioneBluetooth(btAdapter: BluetoothAdapter?) {
    // Stato per angolo e distanza ricevuti
    var angolo by remember { mutableStateOf(0f) }
    var distanza by remember { mutableStateOf(-1f) }

    // Simulazione ricezione dati da Bluetooth (da sostituire con codice reale)
    LaunchedEffect(Unit) {
        while (true) {
            // Simula valori (per test)
            angolo = (angolo + 1) % 181f
            distanza = (50..400).random().toFloat()
            delay(50L)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        RadarCanvas(angolo, distanza)
    }
}

@Composable
fun RadarCanvas(angolo: Float, distanza: Float) {
    val raggioMax = 400f
    val maxTrailSize = 100  // massimo numero di punti nella scia

    // Lista di punti tracciati: ciascun elemento è una Pair(angolo, distanza)
    val trail = remember { mutableStateListOf<Pair<Float, Float>>() }

    // Aggiungi nuovo punto alla scia se distanza è valida
    LaunchedEffect(angolo, distanza) {
        if (distanza in 1f..raggioMax) {
            trail.add(Pair(angolo, distanza))
            if (trail.size > maxTrailSize) {
                trail.removeFirst()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height * 0.9f)
            val step = size.width / 10
            val style = Stroke(width = 1f)

            // Archi concentrici
            for (i in 1..4) {
                drawArc(
                    color = Color.Green,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(center.x - step * i, center.y - step * i),
                    size = Size(step * 2 * i, step * 2 * i),
                    style = style
                )
                drawContext.canvas.nativeCanvas.drawText(
                    "${i * 100} cm",
                    center.x + 5,
                    center.y - step * i + 20,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.GREEN
                        textSize = 28f
                    }
                )

            }

            // Linee radiali
            for (angle in 0..180 step 30) {
                val radians = Math.toRadians(angle.toDouble())
                val endX = center.x + cos(radians).toFloat() * step * 4
                val endY = center.y - sin(radians).toFloat() * step * 4
                drawLine(
                    color = Color.Green,
                    start = center,
                    end = Offset(endX, endY),
                    strokeWidth = 1f
                )
            }

            // Punto ostacolo attuale (rosso)
            val radians = Math.toRadians(angolo.toDouble())
            val endX = center.x + cos(radians).toFloat() * distanza / raggioMax * step * 4
            val endY = center.y - sin(radians).toFloat() * distanza / raggioMax * step * 4
            drawCircle(
                color = Color.Red,
                radius = 10f,
                center = Offset(endX, endY)
            )

            // Scia verde (dal punto più vecchio al più recente)
            trail.forEachIndexed { index, (a, d) ->
                val angleRad = Math.toRadians(a.toDouble())
                val trailX = center.x + cos(angleRad).toFloat() * d / raggioMax * step * 4
                val trailY = center.y - sin(angleRad).toFloat() * d / raggioMax * step * 4
                val alpha = index.toFloat() / trail.size
                drawCircle(
                    color = Color.Green.copy(alpha = alpha),
                    radius = 6f,
                    center = Offset(trailX, trailY)
                )
            }
        }

        // Valori numerici
        Column(
            modifier = Modifier
                .padding(bottom = 120.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Angolo: ${angolo.toInt()}°", color = Color.Green, fontSize = 18.sp)
            Text("Distanza: ${distanza.toInt()} cm", color = Color.Green, fontSize = 18.sp)
        }
    }
}

