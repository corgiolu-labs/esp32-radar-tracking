# 📡 ESP32 Radar – Real-Time Ultrasonic Scanning & Tracking

This project implements a real-time radar system based on ESP32, HC-SR04 ultrasonic sensor and an MG996R servo motor, with Bluetooth Classic data streaming and a Jetpack Compose Android application for visualization.

The radar supports:
- NORMAL mode — classic angular sweep 0–180°
- TRACKING mode — target lock within 1 meter + velocity estimation
- Android App — real-time radar visualization with fading green trace & red distance bar
- Bluetooth Classic — compact data output "angle,distance"
- Servo control — smooth PWM rotation with configurable speed ("VELOCITA:<value>")

---

## 🚀 Features

### Hardware (ESP32)
- HC-SR04 ultrasonic distance measurement
- MG996R high-torque servo rotation
- BluetoothSerial communication
- Manual positioning ("POSIZIONA:<angle>")
- Start/Stop scanning ("START", "STOP")
- Tracking mode ("TRACK")
- Tracking data format: "TRACK,angle,distance,velocity"

---

## 🧠 Data Format

### Normal mode:
angle,distance

Example:
90,200

### Tracking mode:
TRACK,angle,distance,velocity

Example:
TRACK,45,120,32

---

## 🛠️ Hardware Setup

Component | Notes
---------|-------
ESP32 DevKit | Bluetooth Classic enabled
HC-SR04 | Ultrasonic sensor (Trig/Echo)
MG996R Servo | 0–180° rotational sweep
Level Shifter | For Echo pin if needed
5V Power supply | Stable current for servo

---

## 🔌 Wiring Example

ESP32 Pin | Device
----------|--------
IO26 | HC-SR04 Trig
IO27 | HC-SR04 Echo
IO27 | Servo PWM output
5V | Servo & HC-SR04
GND | Common ground

(Matches Alessandro's actual hardware setup.)

---

## 📱 Android App (Jetpack Compose)

The Android application displays the radar in real time with:

- Semicircular radar UI
- Green sweeping line with fading trail
- Red distance bar proportional to obstacle distance
- Angle & distance numerical values
- AUTO / MANUAL toggle
- Slider for scan speed ("VELOCITA:<value>")
- Button for NORMAL / TRACKING modes
- Real-time parsing of "angle,distance" or "TRACK,..."

---

## 📂 Repository Structure

esp32-radar-tracking/
│
├── firmware/
│   └── radar.ino
│
├── android-app/
│   └── (source code)
│
├── images/
│   └── radar.jpg
│
└── README.md

---

## 📸 Photos / Media

Add your pictures here:
- ESP32 + HC-SR04 assembly
- Servo mechanics
- Android radar UI screenshots
- Wiring overview

---

## 🧩 Commands Supported

Command | Description
--------|------------
START | Begin automatic sweep
STOP | Stop scanning
VELOCITA:<value> | Set sweep speed
POSIZIONA:<angle> | Move servo to specific angle
TRACK | Enable tracking mode
NORMAL | Return to normal sweep

---

## 🧪 How It Works

1. The ESP32 rotates the servo from 0° to 180° continuously.
2. It reads distance via HC-SR04.
3. It sends "angle,distance" via Bluetooth.
4. The Android app draws the radar graphics frame by frame.
5. In TRACKING mode:
   - Detects the closest obstacle
   - Locks onto it
   - Computes velocity
   - Sends: TRACK,angle,distance,velocity

---

## 💡 Applications

- Low-cost robotics sensing
- Obstacle scanning
- Educational radar visualization
- Mechatronics demonstrations
- Angular mapping systems

---

## 📧 Contact

For collaborations or custom embedded/robotics projects:  
corgiolu.labs@gmail.com

---

⭐ “Real-time embedded systems with practical applications.”
