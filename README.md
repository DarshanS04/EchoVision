# EchoVision — AI Voice Assistant for the Visually Impaired

EchoVision is a fully voice-driven Android assistant built to empower blind and visually impaired users. It operates **completely hands-free** and works **without requiring any API keys** for its core features. Every action is accompanied by voice feedback so the user always knows what is happening.

---

## 🎯 Core Philosophy

- **No screen required** — every feature is driven by voice commands and confirmed aloud.
- **Always listening in the background** — the app runs as a persistent foreground service.
- **Long-press Volume Up** to wake the assistant at any time, even while YouTube is playing.
- **No internet / no API key** required for most features. Gemini AI is optional and only used for general knowledge questions.

---

## 🔊 How to Activate

| Method | Description |
|---|---|
| **Long-press Volume Up** (≥ 1.5 sec) | Primary activation — works in any app |
| **Volume Up + Volume Down together** | Legacy dual-button activation |
| **Tap the microphone button** | In-app button on the main screen |

> The assistant announces **"Listening."** via TTS before opening the microphone.

---

## 📱 Feature Reference

### 🎵 Media Playback
Uses YouTube and Spotify app deep-links. Falls back to browser if the app is not installed.

| Voice Command | What Happens |
|---|---|
| `Play Waka Waka on YouTube` | Opens YouTube, searches and starts playing |
| `YouTube Bohemian Rhapsody` | Same — navigates directly inside the app |
| `Play Queen on Spotify` | Opens Spotify to the search results |
| `Spotify jazz music` | Same — opens Spotify search directly |

---

### 💬 SMS & WhatsApp
Contacts are resolved from the phone's contact book. No typing needed.

| Voice Command | What Happens |
|---|---|
| `Send SMS to John saying I'm on my way` | Opens SMS app with name and message pre-filled |
| `Text Mom Hello` | Same — goes to the first match in contacts |
| `WhatsApp John let's meet at 5` | Opens WhatsApp chat with that contact |
| `WhatsApp Mom` | Opens WhatsApp chat (no message body) |

---

### 📞 Phone Calls
| Voice Command | What Happens |
|---|---|
| `Call John` | Dials the contact directly |
| `Call John` *(multiple matches)* | Lists all matching contacts by number and asks to choose |
| `Option 2` | Calls the second listed match |

---

### 🗺️ Navigation (No Maps API Key)
Uses standard Android **Geo Intents** — works with Google Maps, OsmAnd, or any maps app.

| Voice Command | What Happens |
|---|---|
| `Navigate to Central Park` | Starts turn-by-turn navigation |
| `Take me to the airport` | Same |
| `Find nearby hospitals` | Opens maps searching hospitals near me |
| `Nearest restaurant` | Shows restaurants near current location |

---

### ⏰ Alarms & Timers
| Voice Command | What Happens |
|---|---|
| `Set alarm for 7 AM` | Clock app opens with the alarm pre-set |
| `Wake me up at 8:30 PM` | Same — detects AM/PM automatically |
| `Set timer for 5 minutes` | Countdown starts immediately |
| `Start a 30 second timer` | Same |
| `Timer for 1 hour 30 minutes` | Combines hours and minutes |

---

### 📅 Calendar & Reminders
| Voice Command | What Happens |
|---|---|
| `Create calendar event for doctor appointment` | Opens Calendar with the title filled in |
| `Add reminder for team meeting` | Same |
| `Open my calendar` | Opens the calendar app |

---

### 🌐 Web, Weather & News
Opens the device's default browser — no API key.

| Voice Command | What Happens |
|---|---|
| `Search for how to make pasta` | Google Search opens in browser |
| `Google Albert Einstein` | Same |
| `What is the weather` | Browser opens current weather |
| `Weather in Mumbai` | Weather for a specific city |
| `Show me the news` | Google News opens |
| `Latest news on technology` | Google News filtered by topic |

---

### 🔔 Read Notifications
| Voice Command | What Happens |
|---|---|
| `Read notifications` | Reads all active notifications aloud: app name, title, and body |
| `Notification` | Same trigger word |

> **Requirement:** Grant EchoVision **Notification Access** in Android Settings → Apps → Special App Access.

---

### 📱 Screen Reader
| Voice Command | What Happens |
|---|---|
| `What is on my screen` | Reads the current screen content aloud |

> **Requirement:** Requires the **EchoVision Accessibility Service** to be active.

---

### 📷 Camera Features
| Voice Command | What Happens |
|---|---|
| `What is in front of me` | Describes objects/scene using camera + AI |
| `Read text` / `OCR` | Reads text in front of the camera |
| `Scan barcode` / `Scan QR` | Scans and reads the barcode/QR value |

---

### ⚙️ System Controls
| Voice Command | What Happens |
|---|---|
| `Battery status` | Reads battery percentage aloud |
| `What time is it` | Reads current time |
| `What is today's date` | Reads today's date |
| `Turn on flashlight` | Toggles torch on/off |
| `Open WiFi` / `WiFi settings` | Opens WiFi control panel |
| `Bluetooth settings` | Opens Bluetooth panel |
| `Set volume up` / `Set volume down` | Adjusts media volume |
| `Brightness settings` | Opens display settings |
| `Airplane mode` | Opens airplane mode settings |
| `Do not disturb` | Opens DND settings |
| `Battery saver` | Opens battery saver settings |
| `Turn on location` | Opens location settings |

---

### 🆘 Emergency SOS
| Voice Command | What Happens |
|---|---|
| `SOS` / `Emergency` / `Help me` | Calls your pre-configured emergency contact and sends location |

---

### 📲 Open Any App
| Voice Command | What Happens |
|---|---|
| `Open WhatsApp` | Launches the app by name |
| `Open Gmail` | Same — works with any installed app |
| `Launch Camera` | Same |

---

## 🛡️ Permissions Required

| Permission | Used For |
|---|---|
| Microphone | Voice recognition |
| Contacts | Calling, SMS, WhatsApp contact resolution |
| Phone | Making direct phone calls |
| SMS | Sending text messages |
| Camera | Object detection, OCR, barcode scanning |
| Location | Navigation, SOS |
| Notification Access | Reading notifications aloud |
| Calendar | Creating events and reminders |
| Accessibility Service | Volume button trigger (works in all apps) |

---

## ⚙️ Setup (First Time)

1. **Install** the app and grant all permissions when prompted.
2. **Enable Accessibility Service**: Settings → Accessibility → EchoVision → Turn on.
3. **Enable Notification Access**: Settings → Apps → Special App Access → Notification Access → EchoVision.
4. *(Optional)* Add a **Gemini API key** in Settings for AI-powered general questions.
5. *(Optional)* Set an **emergency contact** in Settings for SOS.

---

## 📁 Project Structure

```
com.visionassist/
├── commands/
│   ├── AppCommands.java          — Open any app by name
│   ├── CommandRouter.java        — Central command classifier and dispatcher
│   ├── PhoneCommands.java        — Calls with duplicate contact resolution
│   ├── SystemCommands.java       — Battery, time, flashlight, volume, etc.
│   ├── communication/
│   │   └── CommunicationCommands.java  — SMS & WhatsApp
│   ├── media/
│   │   └── MediaCommands.java    — YouTube & Spotify deep-link playback
│   ├── navigation/
│   │   └── ExternalNavigationCommands.java  — Maps navigation & nearby search
│   ├── scheduler/
│   │   └── SchedulerCommands.java  — Alarms, timers, calendar
│   └── web/
│       └── WebCommands.java      — Search, weather, news via browser
├── services/
│   ├── AssistantService.java     — Background foreground service
│   └── NotificationReaderService.java  — Auto-read & on-demand notification reading
├── triggers/
│   └── VolumeButtonTrigger.java  — Long-press & dual-button activation
└── accessibility/
    └── VisionAccessibilityService.java  — Key event interception across all apps
```

---

## 🔑 API Key Policy

| Feature | API Key Needed? |
|---|---|
| All media, navigation, web, SMS, calls | ❌ None |
| Alarm, timer, calendar | ❌ None |
| General AI questions (Gemini) | ✅ Optional |
| Camera scene description (Gemini Vision) | ✅ Optional |

The app is **fully functional without any API key**. Gemini only enhances general-knowledge Q&A and camera description.
