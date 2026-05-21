# VitalFlow 🌊

> **Your all-in-one AI-powered health companion for Android**

VitalFlow combines medication tracking, vitals monitoring, cycle predictions, hydration logging, and
an AI health coach into a single glassmorphism-designed Android app.

---

## ✨ Features

| Category                   | What it does                                                                                                               |
|----------------------------|----------------------------------------------------------------------------------------------------------------------------|
| 💊 **Medications**         | Schedule reminders, track pill inventory, swipe to mark as taken, scan prescriptions via camera                            |
| 📊 **Vitals**              | Log Heart Rate, Blood Pressure, SpO2 & Weight — manually or via Bluetooth BLE devices (auto-reconnects last paired device) |
| 🌊 **Hydration**           | Track daily water intake with animated progress, customisable goal, home-screen widget                                     |
| 🌸 **Cycle Tracking**      | Period calendar with drag-to-select, AI cycle predictions, symptom logging, phase-aware "Today in Cycle" card              |
| 🤖 **AI Coach**            | Gemini-powered floating chatbot, daily health tips, food log analysis via camera, AI-generated health reports              |
| 🍎 **Food Logging**        | Snap a photo → Gemini identifies food + estimates macros; 7-day calorie bar chart                                          |
| 📈 **Reports**             | AI doctor summary + 30-day stats; export to PDF or Excel                                                                   |
| ❤️ **Health Connect**      | Steps, heart rate & sleep pulled from Health Connect                                                                       |
| 🔔 **Smart Notifications** | Medication reminders, water nudges, low-stock alerts (≤ 7 pills left), snooze support                                      |
| 🏠 **Widgets**             | Water intake widget + Next Medication widget for the home screen                                                           |

---

## 🎨 Design

**Glassmorphism** with a floating pill bottom nav.

- Page backgrounds: soft teal → lavender gradient (light) / deep navy → midnight (dark)
- Cards: frosted glass (`#CCF0FFFE`) + `1dp` border + `20dp` corners
- Typography: `TextAppearance.VitalFlow.*` scale (Display → Caption) using custom font
- True OLED black mode toggle for AMOLED screens
- Shimmer skeleton loading on Meds, Vitals, and Home stats
- Lottie micro-animations throughout (water splash, confetti, greeting, empty states)

---

## 🛠 Tech Stack

| Layer            | Technology                                                       |
|------------------|------------------------------------------------------------------|
| Language         | Java (primary) + 1 Kotlin file (`HealthConnectManager.kt`)       |
| Architecture     | Single-activity, MVVM, Hilt DI                                   |
| Database         | Room v2.8 + RxJava3 (`room-rxjava3`)                             |
| Async            | RxJava3 + `CompositeDisposable`                                  |
| Navigation       | Navigation Component (single back-stack)                         |
| Auth             | Firebase Auth (Google Sign-In via Credential Manager)            |
| Cloud sync       | Firebase Firestore (two-way via `SyncRepository`)                |
| AI               | Gemini REST API via Retrofit (`gemini-3-flash-preview`)          |
| Charts           | MPAndroidChart                                                   |
| BLE              | Android GATT stack — HR / SpO2 / BP / Weight scales              |
| Workers          | WorkManager (`MedicationWorker`, `ReminderWorker`, `SyncWorker`) |
| Animations       | Lottie + ObjectAnimator                                          |
| Image loading    | Glide                                                            |
| Skeleton loading | Facebook Shimmer                                                 |
| Reports          | Custom PDF via `PdfReportManager`, Excel via Apache POI          |
| Min SDK          | 26 (Android 8.0)                                                 |
| Target SDK       | 36                                                               |

---

## 🏗 Architecture

```
app/src/main/java/com/vitalflow/app/
├── MainActivity.java          — Single activity, bottom nav, floating chat host
├── VitalFlowApp.java          — @HiltAndroidApp, theme + OLED applied on cold start
├── data/
│   ├── AppDatabase.java       — Room DB (version 9)
│   ├── dao/AppDao.java        — All DB queries (RxJava3 + LiveData)
│   ├── entity/                — Vital, Medication, DailyHealth, PeriodLog, FoodLog
│   └── repository/            — GeminiRepository, SyncRepository
├── di/                        — DatabaseModule, NetworkModule (Hilt)
├── ui/
│   ├── home/                  — HomeFragment + HomeViewModel
│   ├── meds/                  — MedsFragment + MedsViewModel + AddMedBottomSheet
│   ├── vitals/                — VitalsFragment + VitalsViewModel + AddVitalBottomSheet (BLE)
│   ├── period/                — PeriodFragment + PeriodViewModel
│   ├── profile/               — ProfileFragment
│   ├── report/                — ReportFragment + ReportViewModel
│   ├── auth/                  — LoginFragment
│   ├── onboarding/            — OnboardingFragment (collects name + goals on first run)
│   └── chatbot/               — FloatingChatManager, GeminiKeyBottomSheet
├── utils/
│   ├── MedicationWorker.java  — Periodic reminders + low-stock alerts
│   ├── NotificationHelper.java
│   ├── PdfReportManager.java
│   ├── DataExportHelper.java  — Excel export (Apache POI)
│   └── HealthConnectManager.kt
└── widget/
    ├── WaterWidgetProvider.java
    └── MedWidgetProvider.java
```

---

## 🚀 Getting Started

### Prerequisites

- Android Studio Hedgehog or later
- Android device / emulator API 26+
- A Firebase project (Auth + Firestore)
- A Gemini API key from [Google AI Studio](https://aistudio.google.com/)

### Setup

1. **Clone the repo**
   ```bash
   git clone https://github.com/your-username/VitalFlow.git
   cd VitalFlow
   ```

2. **Add `google-services.json`**  
   Download from your Firebase Console and place it in `app/`.

3. **Build & run**
   ```bash
   ./gradlew assembleDebug
   ```

4. **Add your Gemini key**  
   Open the app → Profile → AI Configuration → paste your key.  
   Alternatively, enter it during onboarding on the "Meet Your AI Coach" slide.

---

## 📱 Screens

| Home                                                                                | Medications                                                       | Vitals                                                                 |
|-------------------------------------------------------------------------------------|-------------------------------------------------------------------|------------------------------------------------------------------------|
| Hero greeting, hydration ring, stat cards, adherence score, food log, calorie chart | Glass cards, status strip, swipe-to-take/delete, skeleton shimmer | Multi-type chart (HR/BP/SpO2/Weight), BLE scanner, tap-to-detail popup |

| Cycle                                                  | Report                                                           | Profile                                                                 |
|--------------------------------------------------------|------------------------------------------------------------------|-------------------------------------------------------------------------|
| Drag-select calendar, predictions, Today-in-Cycle card | AI doctor summary, 30-day stats, adherence bar, PDF/Excel export | Dark mode, OLED toggle, Gemini config, persona selector, biometric lock |

---

## 🔑 Key SharedPreferences

| File          | Key                   | Purpose                          |
|---------------|-----------------------|----------------------------------|
| `vital_prefs` | `user_gemini_key`     | Gemini API key                   |
| `vital_prefs` | `daily_water_goal_ml` | Water goal set during onboarding |
| `vital_prefs` | `sleep_goal_hours`    | Sleep goal set during onboarding |
| `theme_prefs` | `is_dark_mode`        | Dark mode toggle                 |
| `theme_prefs` | `oled_mode`           | OLED true-black mode             |
| `ble_prefs`   | `last_device_address` | Last paired BLE device MAC       |

---

## 📄 License

© 2026 Arpith Kumar Dubey
