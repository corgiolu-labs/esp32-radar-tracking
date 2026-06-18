package com.example.radaresp32

// ─────────────────────────────────────────────────────────────────────────────
// IMPORTAZIONI
// ─────────────────────────────────────────────────────────────────────────────
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import kotlin.io.path.outputStream
import kotlin.math.cos
import kotlin.math.sin

// Costanti per i prefissi dei messaggi
private const val MSG_PREFIX_TRACK = "TRACK,"
private const val MSG_PREFIX_ACK = "ACK:"
private const val MSG_PREFIX_NACK = "NACK:"
private const val VALORE_OUT = "OUT"

private fun TextFieldDefaults.customOutlinedTextFieldColors(
    textColor: Color,
    cursorColor: Color,
    focusedBorderColor: Color,
    unfocusedBorderColor: Color,
    containerColor: Color,
    focusedLabelColor: Color,
    unfocusedLabelColor: Color,
    disabledTextColor: Color = textColor.copy(alpha = 0.38f),
    disabledLabelColor: Color = unfocusedLabelColor.copy(alpha = 0.38f),
    disabledIndicatorColor: Color = unfocusedBorderColor.copy(alpha = 0.12f),
    errorCursorColor: Color = cursorColor,
    errorLabelColor: Color = Color.Red,
    errorTextColor: Color = textColor,
    errorIndicatorColor: Color = Color.Red,
    errorSupportingTextColor: Color = Color.Red,
    disabledPlaceholderColor: Color = textColor.copy(alpha = 0.38f),
    focusedPlaceholderColor: Color = textColor.copy(alpha = 0.6f),
    unfocusedPlaceholderColor: Color = textColor.copy(alpha = 0.6f),
    disabledLeadingIconColor: Color = unfocusedLabelColor.copy(alpha = 0.38f),
    errorLeadingIconColor: Color = unfocusedLabelColor,
    focusedLeadingIconColor: Color = focusedLabelColor,
    unfocusedLeadingIconColor: Color = unfocusedLabelColor,
    disabledTrailingIconColor: Color = unfocusedLabelColor.copy(alpha = 0.38f),
    errorTrailingIconColor: Color = Color.Red,
    focusedTrailingIconColor: Color = focusedLabelColor,
    unfocusedTrailingIconColor: Color = unfocusedLabelColor,
    focusedSupportingTextColor: Color = focusedLabelColor,
    unfocusedSupportingTextColor: Color = unfocusedLabelColor,
    disabledSupportingTextColor: Color = unfocusedLabelColor.copy(alpha = 0.38f)
): TextFieldColors {
    return outlinedTextFieldColors(
        textColor = textColor,
        cursorColor = cursorColor,
        focusedIndicatorColor = focusedBorderColor,
        unfocusedIndicatorColor = unfocusedBorderColor,
        containerColor = containerColor,
        focusedLabelColor = focusedLabelColor,
        unfocusedLabelColor = unfocusedLabelColor,
        disabledTextColor = disabledTextColor,
        disabledLabelColor = disabledLabelColor,
        disabledIndicatorColor = disabledIndicatorColor,
        errorCursorColor = errorCursorColor,
        errorLabelColor = errorLabelColor,
        errorTextColor = errorTextColor,
        errorIndicatorColor = errorIndicatorColor,
        errorSupportingTextColor = errorSupportingTextColor,
        disabledPlaceholderColor = disabledPlaceholderColor,
        focusedPlaceholderColor = focusedPlaceholderColor,
        unfocusedPlaceholderColor = unfocusedPlaceholderColor,
        disabledLeadingIconColor = disabledLeadingIconColor,
        errorLeadingIconColor = errorLeadingIconColor,
        focusedLeadingIconColor = focusedLeadingIconColor,
        unfocusedLeadingIconColor = unfocusedLeadingIconColor,
        disabledTrailingIconColor = disabledTrailingIconColor,
        errorTrailingIconColor = errorTrailingIconColor,
        focusedTrailingIconColor = focusedTrailingIconColor,
        unfocusedTrailingIconColor = unfocusedTrailingIconColor,
        focusedSupportingTextColor = focusedSupportingTextColor,
        unfocusedSupportingTextColor = unfocusedSupportingTextColor,
        disabledSupportingTextColor = disabledSupportingTextColor
    )
}

@Composable
fun ConnessioneBluetooth(btAdapter: BluetoothAdapter?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val socket = remember { mutableStateOf<BluetoothSocket?>(null) }
    val connesso = remember { mutableStateOf(false) }
    val mostraDialog = remember { mutableStateOf(false) }
    val messaggio = remember { mutableStateOf("📡 In attesa di connessione...") }
    val stopRicezione = remember { mutableStateOf(false) }
    val angolo = remember { mutableFloatStateOf(0f) }
    val distanza = remember { mutableFloatStateOf(-1f) }
    val velocita = remember { mutableFloatStateOf(50f) }
    val manualeAbilitato = remember { mutableStateOf(false) }
    val angoloManuale = remember { mutableFloatStateOf(90f) }

    val isCurrentlyTracking = remember { mutableStateOf(false) }
    val trackedObjectAngle = remember { mutableFloatStateOf(0f) }
    val trackedObjectDistance = remember { mutableFloatStateOf(-1f) }
    val trackedObjectSpeed = remember { mutableFloatStateOf(0f) }
    val currentConfirmedMaxRangeCm = remember { mutableFloatStateOf(100f) }
    val inputMaxRangeCm = remember { mutableStateOf(currentConfirmedMaxRangeCm.value.toInt().toString()) }

    fun riceviDati(
        inputStream: InputStream,
        stopFlag: State<Boolean>,
        onScanDataReceived: (angle: Float, distance: Float) -> Unit,
        onTrackDataReceived: (angle: Float, distance: Float, speed: Float) -> Unit,
        onAckNackMessageReceived: (message: String, isAck: Boolean) -> Unit,
        onGeneralMessageReceived: (message: String) -> Unit,
        onConfirmedRangeReceived: (range: Float) -> Unit,
        onConnectionLost: () -> Unit
    ) {
        scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(1024)
            var lineBuffer = ""

            while (!stopFlag.value && isActive) {
                try {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead == -1) {
                        if (!stopFlag.value) {
                            withContext(Dispatchers.Main) { onConnectionLost() }
                        }
                        break
                    }
                    if (bytesRead > 0) {
                        lineBuffer += String(buffer, 0, bytesRead, Charsets.UTF_8)
                        var newlineIndex: Int
                        while (lineBuffer.indexOf("\n").also { newlineIndex = it } != -1) {
                            if (stopFlag.value || !isActive) break
                            val rawLine = lineBuffer.substring(0, newlineIndex)
                            lineBuffer = lineBuffer.substring(newlineIndex + 1)
                            val trimmedLine = rawLine.trim()
                            if (trimmedLine.isBlank()) continue

                            withContext(Dispatchers.Main) {
                                when {
                                    trimmedLine.startsWith(MSG_PREFIX_TRACK) -> {
                                        val parts = trimmedLine.substring(MSG_PREFIX_TRACK.length).split(',')
                                        if (parts.size == 3) {
                                            val ang = parts[0].toFloatOrNull()
                                            val dist = parts[1].toFloatOrNull()
                                            val spd = parts[2].toFloatOrNull()
                                            if (ang != null && dist != null && spd != null) {
                                                onTrackDataReceived(ang, dist, spd)
                                            } else {
                                                onGeneralMessageReceived("⚠️ Dati TRACK malformati: $trimmedLine")
                                            }
                                        } else {
                                            onGeneralMessageReceived("⚠️ Dati TRACK incompleti: $trimmedLine")
                                        }
                                    }
                                    trimmedLine.startsWith(MSG_PREFIX_ACK) -> {
                                        val ackMsg = trimmedLine.substring(MSG_PREFIX_ACK.length)
                                        onAckNackMessageReceived(ackMsg, true)
                                        if (ackMsg.startsWith("RANGE=")) {
                                            ackMsg.substringAfter("RANGE=").toFloatOrNull()?.let {
                                                onConfirmedRangeReceived(it)
                                            }
                                        }
                                    }
                                    trimmedLine.startsWith(MSG_PREFIX_NACK) -> {
                                        val nackMsg = trimmedLine.substring(MSG_PREFIX_NACK.length)
                                        onAckNackMessageReceived(nackMsg, false)
                                    }
                                    trimmedLine.contains(',') && trimmedLine.count { it == ',' } == 1 -> {
                                        val parts = trimmedLine.split(',')
                                        val ang = parts[0].toFloatOrNull()
                                        val distStr = parts[1]
                                        if (ang != null) {
                                            val distVal = if (distStr.equals(VALORE_OUT, ignoreCase = true)) -1f else distStr.toFloatOrNull()
                                            if (distVal != null) {
                                                onScanDataReceived(ang, distVal)
                                            } else {
                                                onGeneralMessageReceived("⚠️ Distanza SCAN malformata: $trimmedLine")
                                            }
                                        } else {
                                            onGeneralMessageReceived("⚠️ Angolo SCAN malformato: $trimmedLine")
                                        }
                                    }
                                    else -> {
                                        onGeneralMessageReceived(trimmedLine)
                                    }
                                }
                            }
                        }
                    }
                } catch (e: java.io.IOException) {
                    if (!stopFlag.value && isActive) {
                        withContext(Dispatchers.Main) { onConnectionLost() }
                    }
                    break
                } catch (e: Exception) {
                    if (!stopFlag.value && isActive) {
                        withContext(Dispatchers.Main) {
                            onGeneralMessageReceived("❌ Errore critico ricezione: ${e.message}")
                        }
                    }
                    break
                }
            }
        }
    }

    fun connettiRadar() {
        if (connesso.value) {
            mostraDialog.value = true
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                messaggio.value = "❗ Permesso BLUETOOTH_CONNECT mancante"
                return
            }
        }

        scope.launch(Dispatchers.IO) {
            val device: BluetoothDevice? = btAdapter?.bondedDevices?.find { it.name == "ESP32_Radar" }

            if (device == null) {
                withContext(Dispatchers.Main) { messaggio.value = "⚠️ ESP32_Radar non trovato tra i dispositivi accoppiati." }
                return@launch
            }

            var tempSocket: BluetoothSocket? = null
            try {
                val uuid = device.uuids?.firstOrNull()?.uuid ?: UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                tempSocket = device.createRfcommSocketToServiceRecord(uuid)
                withContext(Dispatchers.Main) { messaggio.value = "🔗 Connessione a ESP32_Radar in corso..." }
                tempSocket.connect()

                withContext(Dispatchers.Main) {
                    socket.value = tempSocket
                    connesso.value = true
                    stopRicezione.value = false
                    messaggio.value = "✅ Connessione riuscita"

                    scope.launch(Dispatchers.IO) {
                        try {
                            val velocitaDaInviare = (200f - velocita.value).toInt()
                            socket.value?.outputStream?.write("VELOCITA:$velocitaDaInviare\n".toByteArray())
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                messaggio.value = "⚠️ Errore invio velocità iniziale: ${e.message}"
                            }
                        }
                    }

                    riceviDati(
                        inputStream = tempSocket.inputStream,
                        stopFlag = stopRicezione,
                        onScanDataReceived = { ang, dist ->
                            isCurrentlyTracking.value = false
                            angolo.value = ang
                            distanza.value = dist
                            if (!messaggio.value.startsWith("✅") && !messaggio.value.startsWith("❌") && !messaggio.value.startsWith("🎯")) {
                                if (messaggio.value != "📡 Scansione...") messaggio.value = "📡 Scansione..."
                            }
                        },
                        onTrackDataReceived = { ang, dist, spd ->
                            isCurrentlyTracking.value = true
                            angolo.value = ang
                            distanza.value = dist
                            trackedObjectAngle.value = ang
                            trackedObjectDistance.value = dist
                            trackedObjectSpeed.value = spd
                            if (messaggio.value != "🎯 Tracking attivo...") messaggio.value = "🎯 Tracking attivo..."
                        },
                        onAckNackMessageReceived = { msg, isAck ->
                            messaggio.value = if (isAck) "✅ $msg" else "❌ $msg"
                        },
                        onGeneralMessageReceived = { msg ->
                            if (!msg.startsWith(MSG_PREFIX_ACK) && !msg.startsWith(MSG_PREFIX_NACK)) {
                                messaggio.value = msg
                            }
                        },
                        onConfirmedRangeReceived = { newRange ->
                            currentConfirmedMaxRangeCm.value = newRange
                        },
                        onConnectionLost = {
                            if (connesso.value) {
                                messaggio.value = "🔌 Connessione persa."
                                try { socket.value?.close() } catch (_: Exception) {}
                                socket.value = null
                                connesso.value = false
                                stopRicezione.value = true
                                isCurrentlyTracking.value = false
                            }
                        }
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    messaggio.value = "❌ Errore connessione: ${e.message}"
                }
                try { tempSocket?.close() } catch (_: Exception) {}
            }
        }
    }

    LaunchedEffect(btAdapter, context) {
        if (btAdapter != null && !connesso.value) {
            // connettiRadar() // Rimosso per evitare connessione automatica all'avvio
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = { connettiRadar() },
                enabled = !connesso.value,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Green)
            ) {
                Text("Connetti", color = Color.Black)
            }
            Button(
                onClick = {
                    scope.launch {
                        stopRicezione.value = true
                        delay(100)
                        try {
                            socket.value?.outputStream?.write("STOP_ALL\n".toByteArray())
                            socket.value?.close()
                        } catch (_: Exception) {
                        }
                        socket.value = null
                        connesso.value = false
                        isCurrentlyTracking.value = false
                        messaggio.value = "🔌 Disconnesso"
                    }
                },
                enabled = connesso.value,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) {
                Text("Disconnetti", color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text("Range Massimo (50-400 cm): Attuale ${currentConfirmedMaxRangeCm.value.toInt()} cm", color = Color.White)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = inputMaxRangeCm.value,
                onValueChange = { newValue ->
                    if (newValue.all { it.isDigit() } && newValue.length <= 3) {
                        inputMaxRangeCm.value = newValue
                    }
                },
                label = { Text("Nuovo Range (cm)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.customOutlinedTextFieldColors(
                    textColor = Color.White,
                    cursorColor = Color.White,
                    focusedBorderColor = Color.Cyan,
                    unfocusedBorderColor = Color.Gray,
                    containerColor = Color.DarkGray.copy(alpha = 0.3f),
                    focusedLabelColor = Color.Cyan,
                    unfocusedLabelColor = Color.Gray
                ),
                enabled = connesso.value && !isCurrentlyTracking.value
            )
            Button(
                onClick = {
                    val newRangeValue = inputMaxRangeCm.value.toIntOrNull()
                    if (newRangeValue != null && newRangeValue in 50..400) {
                        scope.launch(Dispatchers.IO) {
                            try {
                                socket.value?.outputStream?.write("RANGE:$newRangeValue\n".toByteArray())
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    messaggio.value = "⚠️ Errore invio RANGE: ${e.message}"
                                }
                            }
                        }
                    } else {
                        messaggio.value = "⚠️ Range non valido (50-400 cm)"
                    }
                },
                enabled = connesso.value && !isCurrentlyTracking.value,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA000))
            ) {
                Text("Invia Range", color = Color.Black)
            }
        }

        Button(
            onClick = {
                manualeAbilitato.value = !manualeAbilitato.value
                val comando = if (manualeAbilitato.value) "STOP\n" else "START\n"
                scope.launch(Dispatchers.IO) {
                    try {
                        socket.value?.outputStream?.write(comando.toByteArray())
                        withContext(Dispatchers.Main) {
                            messaggio.value = if (manualeAbilitato.value) "🔧 Modalità manuale ATTIVA" else "✅ Scansione AUTO ripresa"
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            messaggio.value = "⚠️ Errore invio ${if (manualeAbilitato.value) "STOP" else "START"}"
                        }
                    }
                }
            },
            enabled = connesso.value,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
        ) {
            Text(if (manualeAbilitato.value) "Riprendi scansione AUTO" else "Posizionamento MANUALE", color = Color.White)
        }

        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val velocitaRealeMs = (200f - velocita.value).toInt()
            Text("Velocità scansione: $velocitaRealeMs ms", color = Color.White)
            Slider(
                value = velocita.value,
                onValueChange = { nuovaVelocitaSlider ->
                    velocita.value = nuovaVelocitaSlider
                    if (connesso.value) {
                        scope.launch(Dispatchers.IO) {
                            try {
                                val valoreInviato = (200f - nuovaVelocitaSlider).toInt()
                                socket.value?.outputStream?.write("VELOCITA:$valoreInviato\n".toByteArray())
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    messaggio.value = "⚠️ Errore invio velocità"
                                }
                            }
                        }
                    }
                },
                valueRange = 0f..190f,
                steps = 18,
                enabled = connesso.value && !manualeAbilitato.value && !isCurrentlyTracking.value
            )
        }

        if (manualeAbilitato.value) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Angolo manuale: ${angoloManuale.value.toInt()}°", color = Color.White)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val width = this.size.width.toFloat()
                                val tapX = offset.x.coerceIn(0f, width)
                                val nuovoAngolo =
                                    ((1f - (tapX / width)) * 180f).coerceIn(0f, 180f)
                                angoloManuale.value = nuovoAngolo

                                scope.launch(Dispatchers.IO) {
                                    try {
                                        socket.value?.outputStream?.write("POSIZIONA:${nuovoAngolo.toInt()}\n".toByteArray())
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            messaggio.value = "⚠️ Errore invio angolo manuale"
                                        }
                                    }
                                }
                            }
                        }
                ) {
                    Slider(
                        value = 180f - angoloManuale.value,
                        onValueChange = { sliderValue ->
                            val angoloReale = (180f - sliderValue).coerceIn(0f, 180f)
                            angoloManuale.value = angoloReale
                            scope.launch(Dispatchers.IO) {
                                try {
                                    socket.value?.outputStream?.write("POSIZIONA:${angoloReale.toInt()}\n".toByteArray())
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        messaggio.value = "⚠️ Errore invio angolo manuale"
                                    }
                                }
                            }
                        },
                        valueRange = 0f..180f,
                        steps = 179,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = connesso.value
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            RadarCanvas(angolo = angolo.value, distanza = distanza.value)
        }

        Spacer(modifier = Modifier.height(10.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = MaterialTheme.shapes.medium,
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(containerColor = Color.DarkGray.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (connesso.value) {
                    if (isCurrentlyTracking.value) {
                        Text("🎯 TARGET ACQUISITO 🎯", color = Color(0xFFE91E63), style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                            InfoText("Angolo: ${trackedObjectAngle.value.toInt()}°")
                            InfoText("Distanza: ${trackedObjectDistance.value.toInt()} cm")
                            InfoText("Velocità: ${trackedObjectSpeed.value.toInt()} units")
                        }
                    } else {
                        Text("📡 DATI SCANSIONE ATTUALE 📡", color = Color(0xFF03A9F4), style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                            InfoText("Angolo Servo: ${angolo.value.toInt()}°")
                            val distText = if (distanza.value < 0) "OUT" else "${distanza.value.toInt()} cm"
                            InfoText("Distanza Rilevata: $distText")
                        }
                    }
                } else {
                    Text("In attesa di connessione per visualizzare i dati...", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = messaggio.value,
            color = when {
                messaggio.value.startsWith("✅") -> Color(0xFF4CAF50)
                messaggio.value.startsWith("❌") -> Color(0xFFF44336)
                messaggio.value.startsWith("🎯") -> Color(0xFFE91E63)
                messaggio.value.startsWith("📡") -> Color(0xFF03A9F4)
                messaggio.value.startsWith("⚠️") -> Color(0xFFFFC107)
                messaggio.value.startsWith("❗") -> Color(0xFFFF9800)
                messaggio.value.startsWith("🔌") -> Color(0xFF757575)
                else -> Color.LightGray
            },
            modifier = Modifier.padding(vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium
        )

        if (mostraDialog.value) {
            AlertDialog(
                onDismissRequest = { mostraDialog.value = false },
                confirmButton = { TextButton(onClick = { mostraDialog.value = false }) { Text("OK") } },
                title = { Text("Sei già connesso") },
                text = { Text("Per connetterti a un altro dispositivo o resettare la connessione, disconnettiti prima.") }
            )
        }
    }
}

@Composable
private fun InfoText(text: String) {
    Text(text, color = Color.White, style = MaterialTheme.typography.bodyMedium)
}

@Composable
fun RadarCanvas(angolo: Float, distanza: Float) {
    val trailDuration = 1000L
    val maxTrailLength = 50
    val lineaVerdeTraccia = remember { mutableStateListOf<Pair<Float, Long>>() }
    val rettangoloTraccia = remember { mutableStateListOf<Triple<Float, Float, Long>>() }

    val currentTime = System.currentTimeMillis()
    lineaVerdeTraccia.add(Pair(angolo, currentTime))
    while (lineaVerdeTraccia.size > maxTrailLength) {
        lineaVerdeTraccia.removeFirst()
    }

    if (distanza >= 0 && distanza <= 400) {
        rettangoloTraccia.add(Triple(angolo, distanza, currentTime))
        while (rettangoloTraccia.size > maxTrailLength) {
            rettangoloTraccia.removeFirst()
        }
    }
    rettangoloTraccia.removeAll { (_, _, time) -> currentTime - time > trailDuration * 2 }

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
    currentAngleDegrees: Float, // Angolo attuale in gradi (0-180)
    currentDistanceCm: Float,   // Distanza attuale in cm
    lineaVerdeTraccia: List<Pair<Float, Long>>, // Lista di (angolo, timestamp)
    rettangoloTraccia: List<Triple<Float, Float, Long>>, // Lista di (angolo, distanza, timestamp)
    trailDuration: Long
) = with(scope) {
    val now = System.currentTimeMillis()
    val canvasWidth = size.width
    val canvasHeight = size.height

    val centerX = canvasWidth / 2f
    val centerY = canvasHeight * 0.85f // Sposta il centro più in basso
    val center = Offset(centerX, centerY)
    val raggioMassimo = min(canvasWidth / 2.1f, centerY * 0.9f) // Adatta il raggio massimo

    // --- Disegna Archi e Etichette Distanza ---
    val distanzeEtichette = listOf(100, 200, 300, 400) // Max range
    val paintText = android.graphics.Paint().apply {
        color = android.graphics.Color.GREEN
        textSize = 24f // Dimensione testo
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.LEFT
    }

    distanzeEtichette.forEachIndexed { index, distCm ->
        val raggioArco = raggioMassimo * (distCm.toFloat() / distanzeEtichette.last().toFloat())
        if (raggioArco > 0) {
            drawArc(
                color = Color.Green.copy(alpha = 0.5f),
                startAngle = 180f, // Inizia da sinistra
                sweepAngle = 180f, // Semicerchio superiore
                useCenter = false,
                topLeft = Offset(center.x - raggioArco, center.y - raggioArco),
                size = Size(raggioArco * 2, raggioArco * 2),
                style = Stroke(width = 1.5f)
            )
            // Disegna etichetta distanza (tranne per l'ultimo arco per evitare sovrapposizioni)
            if (index < distanzeEtichette.size -1) { // Non disegnare per 400cm
                drawContext.canvas.nativeCanvas.drawText(
                    "$distCm", // Testo etichetta
                    center.x + 5f, // Posizione X etichetta
                    center.y - raggioArco - 5f, // Posizione Y etichetta
                    paintText
                )
            }
        }
    }

    // --- Disegna Linee Angolari e Etichette Angolo ---
    val angoliEtichette = listOf(0, 30, 60, 90, 120, 150, 180)
    paintText.textAlign = android.graphics.Paint.Align.CENTER // Centra testo per angoli

    angoliEtichette.forEach { angoloGrado ->
        // Converti angolo da 0-180 (destra-sinistra) a angolo canvas (0 a destra, antiorario)
        val radAngleCanvas = Math.toRadians((180 - angoloGrado).toDouble()) // Inverti e converti
        val endX = center.x + raggioMassimo * cos(radAngleCanvas).toFloat()
        val endY = center.y - raggioMassimo * sin(radAngleCanvas).toFloat() // Y è invertita in canvas
        drawLine(Color.Green.copy(alpha = 0.4f), center, Offset(endX, endY), strokeWidth = 1f)

        // Posiziona etichetta angolo leggermente fuori dal raggio massimo
        val raggioEtichetta = raggioMassimo * 1.08f
        val lx = center.x + raggioEtichetta * cos(radAngleCanvas).toFloat()
        val ly = center.y - raggioEtichetta * sin(radAngleCanvas).toFloat()
        drawContext.canvas.nativeCanvas.drawText(
            "$angoloGrado°",
            lx,
            ly + paintText.textSize / 3, // Aggiusta Y per centrare verticalmente
            paintText
        )
    }

    // --- Disegna Traccia Linea Verde (Scia) ---
    lineaVerdeTraccia.forEach { (angle, timestamp) ->
        val age = now - timestamp
        val alpha = (1f - (age.toFloat() / trailDuration)).coerceIn(0f, 1f)
        if (alpha > 0) {
            val radAngleCanvas = Math.toRadians((180 - angle).toDouble())
            val x = center.x + raggioMassimo * cos(radAngleCanvas).toFloat()
            val y = center.y - raggioMassimo * sin(radAngleCanvas).toFloat()
            drawLine(
                color = Color.Green.copy(alpha = alpha * 0.8f), // Più trasparente per la scia
                start = center,
                end = Offset(x, y),
                strokeWidth = 2f
            )
        }
    }

    // --- Disegna Linea Verde Principale (Posizione Attuale) ---
    val currentRadAngleCanvas = Math.toRadians((180 - currentAngleDegrees).toDouble())
    val current