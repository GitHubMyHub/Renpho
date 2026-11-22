<div align="center">

# ⚖️ Renpho

### Your smart scale, your data — a modern, local-first body composition tracker

*A from-scratch Jetpack Compose companion app for Renpho / Qingniu Bluetooth body-composition scales.*

<br/>

![Kotlin](https://img.shields.io/badge/Kotlin-2.4.20-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Bluetooth LE](https://img.shields.io/badge/Bluetooth-LE-0082FC?style=for-the-badge&logo=bluetooth&logoColor=white)

![Architecture](https://img.shields.io/badge/architecture-MVI-00A5A8?style=flat-square)
![DI](https://img.shields.io/badge/DI-Koin-F28C28?style=flat-square)
![Storage](https://img.shields.io/badge/storage-Room%20%2B%20DataStore-6B4FBB?style=flat-square)
![Tests](https://img.shields.io/badge/unit%20tests-27%20passing-2E9E5B?style=flat-square)
![Cloud](https://img.shields.io/badge/cloud-none%20%E2%80%94%20100%25%20on--device-555?style=flat-square)

<br/>

<table>
  <tr>
    <td align="center"><img src="docs/screenshots/dashboard.png" width="200" alt="Dashboard"/><br/><sub><b>Dashboard</b></sub></td>
    <td align="center"><img src="docs/screenshots/weigh-in.png" width="200" alt="Weigh-in"/><br/><sub><b>Weigh-in</b></sub></td>
    <td align="center"><img src="docs/screenshots/history.png" width="200" alt="History"/><br/><sub><b>History</b></sub></td>
    <td align="center"><img src="docs/screenshots/onboarding.png" width="200" alt="Onboarding"/><br/><sub><b>Onboarding</b></sub></td>
  </tr>
</table>

</div>

---

## ✨ Highlights

<table>
<tr>
<td width="50%" valign="top">

### 📡 Talks directly to your scale
Native Bluetooth LE implementation of the **Qingniu / Yolanda** protocol used by most Renpho scales (`QN-Scale`, `Yolanda-CS20*`, …) plus the standard **Weight Scale (0x181D)** and **Body Composition (0x181B)** services. Live weight while you stand on it, automatic pairing, auto-reconnect.

</td>
<td width="50%" valign="top">

### 🧬 13 body metrics
Weight · BMI · Body fat · Visceral fat · Subcutaneous fat · Body water · Muscle mass · Skeletal muscle · Fat-free weight · Bone mass · Protein · BMR · Metabolic age — each with a colour-coded health rating.

</td>
</tr>
<tr>
<td width="50%" valign="top">

### 📈 Trends that matter
Interactive trend chart per metric with **1W / 1M / 3M / 1Y / All** ranges, goal line, min / max / average / change at a glance.

</td>
<td width="50%" valign="top">

### 🔒 Private by design
No account, no cloud, no tracking. Everything lives on your phone (Room + DataStore). Export to **CSV** whenever you want.

</td>
</tr>
<tr>
<td width="50%" valign="top">

### 🎯 Goals & units
Goal weight with progress bar, **kg / lb / st** and **cm / ft-in**, manual entries with date & time picker.

</td>
<td width="50%" valign="top">

### 🔁 History that stays consistent
Raw weight + impedance are kept for every weigh-in, so all history is **recalculated automatically** when the formulas improve.

</td>
</tr>
</table>

---

## 🧭 How it works

```mermaid
sequenceDiagram
    autonumber
    actor You
    participant App as Renpho app
    participant BLE as AndroidBleScaleManager
    participant Scale as QN scale (0xFFF0)
    participant Calc as BodyCompositionCalculator
    participant DB as Room

    You->>App: Tap "Step on scale"
    App->>BLE: startScan()
    You->>Scale: Step on (barefoot)
    Scale-->>BLE: Advertises "QN-Scale"
    BLE->>Scale: connect · enable notify · config (0x13) · time (0x02)
    loop while standing
        Scale-->>BLE: 0x10 weight (unstable)
        BLE-->>App: live weight
    end
    Scale-->>BLE: 0x10 weight (stable) + impedance
    BLE->>Scale: ack (0x1F)
    BLE-->>App: ScaleReading(weight, impedance)
    App->>Calc: weight · impedance · height · age · sex
    Calc-->>App: 13 metrics
    App->>DB: save measurement
    App-->>You: Result card ✅
```

---

## 🏗️ Architecture

Single-module app with clean layering by package, following **MVI** (State · Action · Event) with a `Root` / `Screen` composable split.

```mermaid
flowchart LR
    subgraph Presentation
        UI[Compose Screens<br/>Root + Screen] --> VM[ViewModels<br/>State · Action · Event]
    end
    subgraph Domain
        VM --> IF[Interfaces<br/>MeasurementDataSource<br/>UserPreferences<br/>ScaleManager]
        VM --> CALC[BodyCompositionCalculator<br/>MeasurementFactory]
    end
    subgraph Data
        IF -.-> ROOM[(Room<br/>measurements.db)]
        IF -.-> DS[(DataStore<br/>profile & settings)]
        IF -.-> BLE[AndroidBleScaleManager]
        BLE --> PROTO[QnScaleProtocol<br/>StandardScaleProtocol]
    end
    PROTO <-. GATT .-> SCALE{{⚖️ Scale}}
```

<details>
<summary><b>📁 Project structure</b></summary>

```
app/src/main/java/com/omnitracker/renpho/
├── RenphoApplication.kt            # Koin start-up
├── MainActivity.kt / MainViewModel.kt
├── core/presentation/              # Metric definitions, unit formatting, ObserveAsEvents
├── domain/
│   ├── model/                      # UserProfile, Measurement, BodyComposition, …
│   ├── scale/ScaleManager.kt       # BLE contract (status, readings, scan, connect)
│   ├── BodyCompositionCalculator.kt
│   └── MeasurementFactory.kt
├── data/
│   ├── ble/                        # Android GATT layer + pure protocol state machines
│   └── local/                      # Room DB, DataStore prefs, recalculator
├── di/AppModules.kt                # Koin modules
├── navigation/                     # Type-safe routes + NavHost with bottom bar
├── feature/
│   ├── profile/   dashboard/   measure/
│   ├── history/   addentry/    settings/
└── ui/                             # Theme, MetricTile, StatusBadge, TrendChart (Canvas)
```

</details>

### Tech stack

| Layer | Technology |
|---|---|
| UI | Jetpack Compose · Material 3 · custom Canvas trend chart |
| Navigation | Navigation Compose 2.10 (type-safe `@Serializable` routes) |
| State | ViewModel · `StateFlow` · `Channel` events (MVI) |
| DI | Koin 4.2 |
| Persistence | Room 2.8 (KSP) · DataStore Preferences |
| Bluetooth | Android BLE GATT · coroutine-serialised operation queue |
| Build | AGP 9.6 · Kotlin 2.4.20 · Gradle version catalog · core library desugaring |
| Tests | JUnit 4 — protocol parsers, calculator, fat distribution (27 tests) |

---

## 🧪 Body composition model

The original Renpho app computes metrics in a closed native library. This app uses **published equations, calibrated against values shown by the Renpho app**:

| Metric | Method |
|---|---|
| Fat-free mass | Sun et al. 2003 (*Am J Clin Nutr*) × foot-to-foot calibration |
| Body fat | weight − fat-free mass |
| Body water · Bone | fixed fractions of fat-free mass |
| Muscle mass | fat-free mass − bone |
| Skeletal muscle | Janssen et al. 2000 (*J Appl Physiol*) × calibration |
| Protein | (fat-free mass − water − bone) / weight |
| BMR | Katch–McArdle |
| Visceral / subcutaneous fat | fat mass split by age- and sex-dependent visceral share |
| Metabolic age | age where population BMR (Mifflin–St Jeor) equals yours *(not yet calibrated)* |

> [!NOTE]
> Values are **estimates** for tracking trends — not a medical diagnosis. Body composition needs a **barefoot** measurement; with socks or manual entries only weight and BMI are recorded.

---

## 🔌 Supported scales

| Protocol | Service | Advertised names | Status |
|---|---|---|---|
| Qingniu / Yolanda (type 2) | `0xFFF0` | `QN-Scale`, `QN-Scale1` | ✅ verified on real hardware |
| Qingniu / Yolanda (type 1) | `0xFFE0` | `QN-*`, `Yolanda-CS20*`, `Renpho-*` | 🟡 implemented, untested |
| Bluetooth SIG Weight / Body Composition | `0x181D` / `0x181B` | any | 🟡 implemented, untested |

<details>
<summary><b>🔍 QN protocol cheat-sheet</b></summary>

| Direction | Opcode | Meaning |
|---|---|---|
| scale → app | `0x12` | scale info — byte 10 bit 0 = 0.01 kg resolution |
| scale → app | `0x14` | time-sync request |
| scale → app | `0x10` | weight: `[3..4]` weight BE, `[5]` 0 = live / 1 = stable, `[6..7]` impedance BE |
| app → scale | `0x13` | config (unit kg/lb) |
| app → scale | `0x02` / `0x20` | time (seconds since 2000-01-01) |
| app → scale | `0x1F` | measurement acknowledged |

Every outgoing packet ends with an 8-bit additive checksum.

</details>

---

## 🚀 Getting started

**Requirements:** Android Studio (latest canary/beta for AGP 9.6) · JDK 17+ · a phone with Bluetooth LE (Android 7.0+)

```bash
# build
./gradlew assembleDebug

# run unit tests
./gradlew testDebugUnitTest

# install on a connected phone
./gradlew installDebug
```

Then:

1. **Create your profile** — sex, birth date and height are required for body composition.
2. Tap **Step on scale**, allow *Nearby devices*, and step on barefoot.
3. The scale is paired automatically — next time it connects on its own.

---

## 🗺️ Roadmap

- [ ] Calibrate body fat sensitivity with paired weigh-ins (same step-on in both apps)
- [ ] Metabolic age model matching the vendor app
- [ ] Multiple users / family profiles
- [ ] Health Connect sync
- [ ] Home-screen widget with latest weight
- [ ] Localisation (German first)

---

## ⚠️ Disclaimer

This is an **unofficial**, independent project. It is not affiliated with, endorsed by or connected to Renpho or Qingniu. "Renpho" is a trademark of its respective owner and is used here only to describe compatibility; rename the app before distributing it. No vendor code or assets are included — the Bluetooth protocol support exists for interoperability with hardware you own.

<div align="center">
<br/>
<sub>Built with Kotlin, Compose and a bathroom scale. ⚖️💚</sub>
</div>
