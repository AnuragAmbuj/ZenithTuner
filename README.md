# ZenithTuner: Acoustic Optimization Engine for boAt Nirvana Zenith

[![Rust](https://img.shields.io/badge/Rust-1.75%2B-orange.svg)](https://www.rust-lang.org/)
[![Android](https://img.shields.io/badge/Android-12%2B%20(API%2031%2B)-green.svg)](https://developer.android.com/)
[![Architecture](https://img.shields.io/badge/Arch-arm64--v8a%20%7C%20Zero--GC-blue.svg)]()
[![UI](https://img.shields.io/badge/Jetpack%20Compose-120Hz%20Obsidian-cyan.svg)](https://developer.android.com/jetpack/compose)

A native, zero-latency, zero-garbage-collection acoustic correction pipeline and 120Hz Obsidian interface specifically engineered for the **boAt Nirvana Zenith (Mystique Black)** dual 11mm titanium-coated dynamic driver earbuds.

---

## 1. The Engineering Diagnosis (Why They Sound Muddy)

While the dual 11mm titanium-coated dynamic drivers are physically capable, the stock firmware and consumer tuning ruin acoustic transparency through three distinct layers:

1. **The V-Shaped Frequency Curve**: Stock tuning artificially bloats the mid-bass around **145Hz** to sound "punchy" and spikes the treble around **6.6kHz** to fake "detail," resulting in muffled vocals and listening fatigue.
2. **The 50dB ANC Feedback Loop**: When active ANC is engaged, the internal error-microphone forces a heavy low-frequency boost into the **100Hz–200Hz** range, causing the 11mm diaphragms to physically ring and muddy up fast transients.
3. **AAC Codec Psychoacoustics**: Over Bluetooth AAC at 44.1kHz, high-frequency transients get smeared by aggressive psychoacoustic compression, creating a metallic sizzle on cymbals and sibilant 'S' sounds.

ZenithTuner replaces generic Android equalizers and companion apps with a native **Rust DSP core** interfacing directly with **Android 12 DynamicsProcessing**.

---

## 2. Architecture Overview

```
ZenithTuner/
├── rust-dsp/                          # Panic-safe Rust f64 DSP core (libzenith_dsp.so)
│   ├── Cargo.toml                     # Crate definition (cdylib, rlib, opt-level=3)
│   └── src/
│       └── lib.rs                     # RBJ Biquad filters, 64-band split, preamp headroom, JNI
├── app/
│   ├── build.gradle.kts               # Android 12+ (API 31-35), Compose BOM, NDK arm64-v8a
│   └── src/main/
│       ├── AndroidManifest.xml        # Foreground service (mediaPlayback), session receiver
│       └── java/com/zenith/tuner/
│           ├── ZenithApplication.kt   # Low-importance notification channel configuration
│           ├── MainActivity.kt        # Edge-to-edge Compose container & permission lifecycle
│           ├── dsp/
│           │   ├── ZenithNativeDsp.kt # JNI bridge to libzenith_dsp.so + Kotlin mirror fallback
│           │   ├── DynamicsProcessingManager.kt # 32 Pre-EQ + 32 Post-EQ + 4-Band MBC Damper
│           │   └── AcousticModel.kt   # Immutable data state for 64-band frequency spline
│           ├── service/
│           │   ├── ZenithDspService.kt# Foreground service managing global & player sessions
│           │   └── AudioSessionReceiver.kt # Broadcast receiver for player session IDs
│           └── ui/
│               ├── theme/             # Obsidian Dark palette (#08080A, Titanium, Electric Cyan)
│               ├── components/
│               │   ├── BodePlotCanvas.kt # 120Hz drawWithCache interactive frequency spline
│               │   ├── TactileCards.kt   # Glassmorphic controls (Anti-Mud, Slam, Pinna Gain)
│               │   └── GlassCard.kt      # Reusable obsidian glassmorphic card container
│               └── ZenithTunerScreen.kt  # Root minimalist interface
└── build-rust.sh                      # Automation script to compile & package Rust binaries
```

---

## 3. Step-by-Step Implementation

### Step 1: Neutralizing the Onboard Firmware
Before intercepting audio on Android, clean up the earbud's local NVRAM state:
1. Open the **boAt Hearables** app.
2. Disable **Dolby Audio / Spatial Sound** and **Adaptive EQ** (preventing the firmware from dynamically shifting curves behind your back).
3. Set the internal sound profile to **Balanced / Natural**.
4. Once saved, the earbuds retain this linear acoustic state in hardware.

### Step 2: The Panic-Safe Rust DSP Core (`rust-dsp`)
Calculates exact Robert Bristow-Johnson (RBJ) biquad filters in `f64` precision. Input parameters are strictly clamped to $[10.0\text{Hz}, 0.499 \times f_s]$ to eliminate any possibility of Nyquist singularity or IIR blowups.

#### The 8-Stage Acoustic Correction Target:
| Stage | Filter Type | Frequency ($f_0$) | Quality Factor ($Q$) | Gain ($\Delta\text{dB}$) | Acoustic Objective |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **1** | High-Pass | **24 Hz** | 0.707 | — | Strips sub-audible rumble wasting 11mm driver excursion under ANC |
| **2** | Low-Shelf | **55 Hz** | 0.710 | +3.6 dB (Adj) | Restores clean sub-bass punch and physical slam |
| **3** | Peaking EQ | **145 Hz** | 1.800 | -5.2 dB | Surgically cuts 11mm mid-bass bloat & 50dB ANC feedback boost |
| **4** | Peaking EQ | **320 Hz** | 1.200 | -2.4 dB | Clears lower-midrange boxiness and vocal masking |
| **5a**| Peaking EQ | **1650 Hz**| 1.400 | +2.8 dB (Adj) | Pulls recessed lead vocals forward out of the V-shape pocket |
| **5b**| Peaking EQ | **3100 Hz**| 1.800 | +3.2 dB (Adj) | Restores anatomical pinna ear-gain |
| **6** | Peaking EQ | **6600 Hz**| 2.500 | -3.8 dB | Notches out metallic AAC treble harshness and cymbal sizzle |
| **7** | High-Shelf | **11500 Hz**| 0.710 | +2.5 dB | Opens up top-octave air, instrument separation, and soundstage |

### Step 3: Android 12 Audio Pipeline & Multi-Band Compression
Hooks into global audio session 0 and per-player sessions via `android.media.audiofx.DynamicsProcessing`:
- **The 64-Band Split**: Splits the 64 logarithmic bands across **32 Pre-EQ bands** ($20\text{Hz} - 632\text{Hz}$) and **32 Post-EQ bands** ($694\text{Hz} - 20\text{kHz}$) to stay 100% compliant with hardware effect limits.
- **Automatic Preamp Headroom**: The Rust core calculates the peak boost ($G_{\max}$) across the spectrum and injects an exact negative gain ($-G_{\max} - 0.2\text{dB}$) at index 128 of the JNI buffer, guaranteeing zero digital clipping.
- **11mm Driver Transient Damper (MBC)**: A 4-band Multi-Band Compressor places a fast **3.5ms attack** compressor specifically on Band 1 (70Hz–250Hz) to damp the physical diaphragm's decay time on rapid bass transients.
- **Inter-Sample Peak Limiter**: Threshold at `-0.2dBFS` prevents DAC inter-sample distortion.

### Step 4: The 120Hz Obsidian Minimalist UI
The Jetpack Compose interface ("Obsidian Acoustic") avoids cluttered multi-band slider strips:
- **`Modifier.drawWithCache` Bode Plot**: Renders the live 64-band frequency response spline at a smooth 120Hz without triggering Compose recomposition during touch drags.
- **Interactive Touch Scrubber**: Dragging across the plot displays a glowing cyan crosshair, exact frequency and dB readout, and real-time acoustic context tag.
- **Tactile Cards**: Glassmorphic cards for toggling the 50dB ANC Anti-Mud compensation, fine-tuning Sub-Bass Weight, adjusting Vocal Pinna Gain, monitoring the 3.5ms Transient Damper, and reviewing the NVRAM checklist.

---

## 4. Build & Deployment

### 1. Compile the Rust Library for arm64-v8a
```bash
cd rust-dsp
cargo ndk -t arm64-v8a -o ../app/src/main/jniLibs build --release
cd ..
```
*(Alternatively, run `./build-rust.sh` which executes the test suite and handles compilation)*

### 2. Run Rust Unit Tests
```bash
cd rust-dsp
cargo test
```
All 10 unit tests verify biquad transfer functions, Nyquist safety, preamp headroom, and C/JNI buffer alignment.

### 3. Build & Install the Android APK
```bash
./gradlew assembleRelease
# Or install directly to connected Android 12+ device:
./gradlew installRelease
```

### 4. Deploy via Antigravity CLI (`agy`)
```bash
agy run "Assemble and install release APK on connected Android 12 device"
```

Once installed:
1. Connect your boAt Nirvana Zenith.
2. Play any track (Spotify, Apple Music, YouTube Music).
3. Toggle the **OBSIDIAN ACOUSTIC ENGINE** on.
4. The veil over the vocals will vanish, and bass transients will tighten instantly.
