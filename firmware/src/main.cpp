#include <Arduino.h>
#include <BluetoothSerial.h>

// --- PROTOTIPI FUNZIONI ---
void processBluetoothCommand(String cmd);
void radarLogic();
void performNormalScan();
// void attemptToTrackTarget(int initialAngle, float initialDistance); // Non più usata direttamente così
bool fineTuneAndFollow(int& currentLockAngle, float& currentLockDistance);
void moveServo(int angle);
float measureDistance();
float measureDistanceAtAngle(int angle);
void sendScanData(int angle, float distance);
void sendTrackData(int angle, float distance, float speed);

// --- PIN ---
const int PWM_PIN  = 25;
const int TRIG_PIN = 26;
const int ECHO_PIN = 27;

// --- COSTANTI SENSORE & SERVO ---
const float SOUND_SPEED_CM_US = 0.0343;
const int ANGLE_STEP_NORMAL_SCAN = 2;

const int SERVO_MIN_PULSE_US = 500;
const int SERVO_MAX_PULSE_US = 2500;

// --- MODALITÀ E SOGLIE ---
bool manualOverrideStop = false;
float LOCK_MAX_DISTANCE_CM = 100.0; // Default, ora modificabile dall'app
float LOCK_MIN_DISTANCE_CM = 7.0;
unsigned long TRACKING_LOST_TIMEOUT_MS = 1500;
unsigned long FINE_TUNE_INTERVAL_MS_TRACKING = 60;

// --- STATO RADAR ---
BluetoothSerial SerialBT;
unsigned long lastServoMoveTimeNormalScanMs = 0;
int currentRadarAngle = 90;
bool isScanningForward = true;
int servoNormalScanDelayMs = 40;

// --- INSEGUIMENTO AUTOMATICO ---
bool isCurrentlyTracking = false;
unsigned long targetLastSeenDuringTrackMs = 0;
unsigned long lastFineTuneDuringTrackMs = 0;
float lastTrackedDistCm = 0;
unsigned long lastTrackTimeMsForSpeed = 0;


// --- COSTANTI MOVIMENTO E MISURAZIONE ---
const int DELAY_AFTER_SERVO_MOVE_US = 20000; // 20ms
const int TRACK_FINE_TUNE_ANGLE_STEP = 5;
const int TRACK_SEARCH_ON_LOSS_ANGLE_STEP = 15;

// ─────────────────────────────────────────────────────
void setup() {
  pinMode(PWM_PIN, OUTPUT);
  pinMode(TRIG_PIN, OUTPUT);
  pinMode(ECHO_PIN, INPUT);

  Serial.begin(115200);
  SerialBT.begin("ESP32_Radar");

  Serial.println("📡 Radar Autonomo Pronto (Range default: " + String(LOCK_MAX_DISTANCE_CM) + " cm)");
  SerialBT.println("📡 Connessione Bluetooth avviata. Range default: " + String(LOCK_MAX_DISTANCE_CM) + " cm"); // Informa anche l'app

  moveServo(currentRadarAngle);
  delayMicroseconds(DELAY_AFTER_SERVO_MOVE_US * 2);
  lastServoMoveTimeNormalScanMs = millis();
}

// ─────────────────────────────────────────────────────
void loop() {
  if (SerialBT.available()) {
    String cmd = SerialBT.readStringUntil('\n');
    processBluetoothCommand(cmd);
  }

  if (manualOverrideStop) {
    return;
  }
  radarLogic();
}

// ─────────────────────────────────────────────────────
void radarLogic() {
  if (isCurrentlyTracking) {
    int lockedAngle = currentRadarAngle;
    float lockedDist = lastTrackedDistCm;

    if (!fineTuneAndFollow(lockedAngle, lockedDist)) {
      Serial.println("🎯 Inseguimento perso. Ritorno a scansione.");
      isCurrentlyTracking = false;
    } else {
      currentRadarAngle = lockedAngle;
      lastTrackedDistCm = lockedDist;
    }
  } else {
    performNormalScan();
  }
}

// ─────────────────────────────────────────────────────
void performNormalScan() {
  unsigned long currentTimeMs = millis();

  if (currentTimeMs - lastServoMoveTimeNormalScanMs >= servoNormalScanDelayMs) {
    if (isScanningForward) {
      currentRadarAngle += ANGLE_STEP_NORMAL_SCAN;
      if (currentRadarAngle >= 180) { currentRadarAngle = 180; isScanningForward = false; }
    } else {
      currentRadarAngle -= ANGLE_STEP_NORMAL_SCAN;
      if (currentRadarAngle <= 0) { currentRadarAngle = 0; isScanningForward = true; }
    }
    lastServoMoveTimeNormalScanMs = currentTimeMs;

    float distance = measureDistanceAtAngle(currentRadarAngle);
    sendScanData(currentRadarAngle, distance);

    // Usa LOCK_MAX_DISTANCE_CM che può essere stato aggiornato dall'app
    if (distance >= LOCK_MIN_DISTANCE_CM && distance <= LOCK_MAX_DISTANCE_CM) {
      Serial.println("👁️ Oggetto rilevato a A" + String(currentRadarAngle) + " D" + String(distance) + ". Aggancio (Range Max: " + String(LOCK_MAX_DISTANCE_CM) + "cm)");
      isCurrentlyTracking = true;
      targetLastSeenDuringTrackMs = millis();
      lastFineTuneDuringTrackMs = millis();
      lastTrackedDistCm = distance;
      lastTrackTimeMsForSpeed = millis();
      // currentRadarAngle è già impostato sull'angolo dell'oggetto trovato
      return;
    }
  }
}

// ─────────────────────────────────────────────────────
bool fineTuneAndFollow(int& currentLockAngle, float& currentLockDistance) {
  unsigned long nowMs = millis();

  if (nowMs - lastFineTuneDuringTrackMs < FINE_TUNE_INTERVAL_MS_TRACKING) {
    return true; // Non è ora per un altro fine-tune
  }
  lastFineTuneDuringTrackMs = nowMs;

  float distCenter = measureDistanceAtAngle(currentLockAngle);

  if (distCenter < LOCK_MIN_DISTANCE_CM || distCenter > LOCK_MAX_DISTANCE_CM) {
    Serial.println("👣 Lock perso al centro (A" + String(currentLockAngle) + " D" + String(distCenter) + "). Ricerca breve...");
    for (int offset = -TRACK_SEARCH_ON_LOSS_ANGLE_STEP; offset <= TRACK_SEARCH_ON_LOSS_ANGLE_STEP; offset += TRACK_SEARCH_ON_LOSS_ANGLE_STEP) {
        if (offset == 0) continue;
        int testAngle = constrain(currentLockAngle + offset, 0, 180);
        float testDist = measureDistanceAtAngle(testAngle);
        if (testDist >= LOCK_MIN_DISTANCE_CM && testDist <= LOCK_MAX_DISTANCE_CM) {
            Serial.println("👣 Ritrovato durante ricerca breve a A" + String(testAngle) + " D" + String(testDist));
            currentLockAngle = testAngle;
            currentLockDistance = testDist;
            targetLastSeenDuringTrackMs = nowMs;
            // Invia dati TRACK
            if (lastTrackTimeMsForSpeed > 0 && (nowMs - lastTrackTimeMsForSpeed) > 10) {
                 float speed = (lastTrackedDistCm - currentLockDistance) / ((nowMs - lastTrackTimeMsForSpeed)/1000.0f);
                 sendTrackData(currentLockAngle, currentLockDistance, speed);
            } else {
                 sendTrackData(currentLockAngle, currentLockDistance, 0);
            }
            lastTrackedDistCm = currentLockDistance;
            lastTrackTimeMsForSpeed = nowMs;
            return true;
        }
    }
    // Se la ricerca breve non ha funzionato
    if (nowMs - targetLastSeenDuringTrackMs > TRACKING_LOST_TIMEOUT_MS) {
        return false; // Timeout definitivo
    }
    return true; // Non ancora timeout, ma il lock non è solido
  }

  float bestDistThisTune = distCenter;
  int bestAngleThisTune = currentLockAngle;

  int angleLeft = constrain(currentLockAngle - TRACK_FINE_TUNE_ANGLE_STEP, 0, 180);
  float distLeft = measureDistanceAtAngle(angleLeft);
  if (distLeft >= LOCK_MIN_DISTANCE_CM && distLeft < bestDistThisTune) {
    bestDistThisTune = distLeft;
    bestAngleThisTune = angleLeft;
  }

  int angleRight = constrain(currentLockAngle + TRACK_FINE_TUNE_ANGLE_STEP, 0, 180);
  float distRight = measureDistanceAtAngle(angleRight);
  if (distRight >= LOCK_MIN_DISTANCE_CM && distRight < bestDistThisTune) {
    bestDistThisTune = distRight;
    bestAngleThisTune = angleRight;
  }

  currentLockAngle = bestAngleThisTune;
  currentLockDistance = bestDistThisTune;
  targetLastSeenDuringTrackMs = nowMs;

  float currentSpeed = 0;
  if (lastTrackTimeMsForSpeed > 0 && (nowMs - lastTrackTimeMsForSpeed) > 10) {
      float deltaTimeSeconds = (nowMs - lastTrackTimeMsForSpeed) / 1000.0f;
      currentSpeed = (lastTrackedDistCm - currentLockDistance) / deltaTimeSeconds;
  }
  sendTrackData(currentLockAngle, currentLockDistance, currentSpeed);
  //Serial.println("🐾 Inseguimento: A" + String(currentLockAngle) + " D" + String(currentLockDistance,1) + " V" + String(currentSpeed,1));

  lastTrackedDistCm = currentLockDistance;
  lastTrackTimeMsForSpeed = nowMs;

  return true;
}

// ─────────────────────────────────────────────────────
float measureDistanceAtAngle(int angle) {
  moveServo(angle);
  delayMicroseconds(DELAY_AFTER_SERVO_MOVE_US);
  return measureDistance();
}

// ─────────────────────────────────────────────────────
void moveServo(int angle) {
  angle = constrain(angle, 0, 180);
  int pulse = map(angle, 0, 180, SERVO_MIN_PULSE_US, SERVO_MAX_PULSE_US);
  digitalWrite(PWM_PIN, HIGH);
  delayMicroseconds(pulse);
  digitalWrite(PWM_PIN, LOW);
}

// ─────────────────────────────────────────────────────
float measureDistance() {
  digitalWrite(TRIG_PIN, LOW);
  delayMicroseconds(2);
  digitalWrite(TRIG_PIN, HIGH);
  delayMicroseconds(10);
  digitalWrite(TRIG_PIN, LOW);
  unsigned long duration_us = pulseIn(ECHO_PIN, HIGH, 35000);
  if (duration_us == 0) { return -1.0; }
  return duration_us * SOUND_SPEED_CM_US / 2.0;
}

// ─────────────────────────────────────────────────────
void sendScanData(int angle, float distance) {
  String out = String(angle) + "," + (distance < 0 ? "OUT" : String(distance, 1));
  SerialBT.println(out);
}

// ─────────────────────────────────────────────────────
void sendTrackData(int angle, float distance, float speed) {
  String out = "TRACK," + String(angle) + "," + String(distance, 1) + "," + String(speed, 1);
  SerialBT.println(out);
}

// ─────────────────────────────────────────────────────
void processBluetoothCommand(String cmd) {
  cmd.trim();
  Serial.println("BT CMD RX: " + cmd); // Log del comando ricevuto

  if (cmd.startsWith("VELOCITA:")) {
    int val = cmd.substring(9).toInt();
    if (val > 0) { // Aggiunto controllo per valore sensato
        servoNormalScanDelayMs = constrain(val, 20, 200);
        Serial.println("⚙️ Vel scansione impostata a: " + String(servoNormalScanDelayMs) + " ms");
        SerialBT.println("ACK:VELOCITA=" + String(servoNormalScanDelayMs));
    } else {
        Serial.println("⚠️ Vel scansione non valida: " + cmd);
        SerialBT.println("NACK:VELOCITA invalida");
    }
  } else if (cmd == "STOP") {
    manualOverrideStop = true;
    isCurrentlyTracking = false;
    Serial.println("🛑 Radar fermato manualmente.");
    SerialBT.println("ACK:STOP");
  } else if (cmd == "START") {
    manualOverrideStop = false;
    Serial.println("▶️ Radar ripreso manualmente.");
    SerialBT.println("ACK:START");
  } else if (cmd.startsWith("RANGE:")) { // NUOVO BLOCCO PER IL COMANDO RANGE
    String valStr = cmd.substring(6);
    float newRange = valStr.toFloat();
    if (newRange >= LOCK_MIN_DISTANCE_CM && newRange <= 400.0) { // Imposta un limite superiore ragionevole (es. 400cm)
      LOCK_MAX_DISTANCE_CM = newRange;
      Serial.println("📏 Range di aggancio impostato a: " + String(LOCK_MAX_DISTANCE_CM) + " cm");
      SerialBT.println("ACK:RANGE=" + String(LOCK_MAX_DISTANCE_CM)); // Invia conferma all'app
    } else {
      Serial.println("⚠️ Valore range non valido: " + valStr);
      SerialBT.println("NACK:RANGE invalido (" + valStr + ")"); // Invia errore all'app
    }
  } else {
    Serial.println("❓ Comando BT sconosciuto: " + cmd);
    SerialBT.println("NACK:Comando '" + cmd + "' sconosciuto");
  }
}

