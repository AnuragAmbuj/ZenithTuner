# ZenithTuner: Precision Acoustic Optimization & Customization Suite for boAt Nirvana Zenith

[![Rust](https://img.shields.io/badge/Rust-1.75%2B-orange.svg)](https://www.rust-lang.org/)
[![Android](https://img.shields.io/badge/Android-12%2B%20(API%2031%2B)-green.svg)](https://developer.android.com/)
[![Architecture](https://img.shields.io/badge/Arch-arm64--v8a%20%7C%20Zero--GC-blue.svg)]()
[![UI](https://img.shields.io/badge/Jetpack%20Compose-120Hz%20Obsidian-cyan.svg)](https://developer.android.com/jetpack/compose)

A production-grade, zero-latency, zero-garbage-collection acoustic correction pipeline and 120Hz Obsidian interface with **full options for tuning and customizing audio** specifically engineered for the **boAt Nirvana Zenith (Mystique Black)** dual 11mm titanium-coated dynamic driver earbuds.

---

## 1. The Engineering Diagnosis (Why They Sound Muddy)

While the dual 11mm titanium-coated dynamic drivers are physically capable, stock firmware and consumer tuning degrade acoustic output through three distinct software layers:

1. **The V-Shaped Frequency Curve**: Stock tuning artificially bloats the mid-bass around **145Hz** to sound "punchy" and spikes the treble around **6.6kHz** to fake "detail," resulting in muffled vocals and listening fatigue.
2. **The 50dB ANC Feedback Loop**: When active ANC is engaged, the internal error-microphone forces a heavy low-frequency boost into the **100Hz–200Hz** range, causing the 11mm diaphragms to physically ring and muddy up fast transients.
3. **AAC Codec Psychoacoustics**: Over Bluetooth AAC at 44.1kHz, high-frequency transients get smeared by aggressive psychoacoustic compression, creating a metallic sizzle on cymbals and sibilant 'S' sounds.

ZenithTuner hooks directly into **Android 12 DynamicsProcessing** and is powered by a **panic-safe Rust f64 DSP core** (`libzenith_dsp.so`).

---

## 2. Complete Tuning & Customization Options

ZenithTuner provides four specialized studios for tailoring the acoustic response to any listening preference:

### Studio 1: Express ("Obsidian Express")
Designed for fast, intuitive acoustic adjustments with zero clutter:
- **Master Engine Toggle**: Real-time engagement of the hardware `DynamicsProcessing` engine with active cyan pulse dot.
- **Preset Quick-Selector**: One-tap switching between reference targets.
- **50dB ANC Anti-Mud Switch**: Surgically cuts the 145Hz feedback resonance and eliminates sub-audible flutter under active ANC.
- **Sub-Bass Slam Slider (55Hz)**: Fine-tune 11mm driver physical slam from linear (0dB) to heavy punch (+7.2dB) without muddying vocals.
- **Vocal Pinna Gain Slider (1.65k & 3.1k)**: Pulls lead vocals forward out of the V-shape depression.
- **Binaural Crossfeed & Soundstage**: Simulates natural interaural acoustic delay and head shadow (0%–100%) to eliminate in-the-head fatigue and expand spatial width.
- **Preamp Headroom Selector**: Switch between auto zero-clipping guard and manual fixed preamp (-12dB to +3dB).
- **Firmware Neutralization Checklist**: Built-in interactive guide to clear boAt Hearables NVRAM state.

### Studio 2: Parametric Lab ("Precision Lab")
Full parametric inspection and control over all 8 Robert Bristow-Johnson biquad stages in double precision (`f64`):
1. **Stage 0: HPF Sub-Rumble Strip** — High-pass filter ($15\text{Hz} - 50\text{Hz}$, $Q: 0.1 - 2.0$)
2. **Stage 1: Sub-Bass Slam** — Low-shelf filter ($30\text{Hz} - 90\text{Hz}$, gain: $-12\text{dB} \dots +12\text{dB}$, $Q: 0.1 - 3.0$)
3. **Stage 2: 50dB ANC Anti-Mud** — Peaking EQ ($100\text{Hz} - 200\text{Hz}$, gain: $-16\text{dB} \dots 0\text{dB}$, $Q: 0.5 - 5.0$)
4. **Stage 3: Lower-Mid Boxiness Cut** — Peaking EQ ($220\text{Hz} - 480\text{Hz}$, gain: $-8\text{dB} \dots +4\text{dB}$, $Q: 0.5 - 3.0$)
5. **Stage 4: Vocal Pinna Gain 1** — Peaking EQ ($1200\text{Hz} - 2200\text{Hz}$, gain: $-6\text{dB} \dots +8\text{dB}$, $Q: 0.5 - 3.0$)
6. **Stage 5: Pinna Ear-Gain 2** — Peaking EQ ($2400\text{Hz} - 4000\text{Hz}$, gain: $-6\text{dB} \dots +8\text{dB}$, $Q: 0.5 - 3.0$)
7. **Stage 6: AAC Treble De-Sizzle** — Peaking EQ ($5500\text{Hz} - 7800\text{Hz}$, gain: $-12\text{dB} \dots 0\text{dB}$, $Q: 0.5 - 6.0$)
8. **Stage 7: Top-Octave Air & Soundstage** — High-shelf filter ($8000\text{Hz} - 16000\text{Hz}$, gain: $-8\text{dB} \dots +8\text{dB}$, $Q: 0.1 - 2.0$)

*Every slider adjustment renders live on the 120Hz Bode plot with zero Compose recomposition overhead.*

### Studio 3: MBC Dynamics ("11mm Transient Damper")
Direct access to Android's 4-band hardware Multi-Band Compressor:
- **Band 1 (70Hz - 250Hz) Diaphragm Damper**:
  - **Attack Time ($1.0\text{ms} - 15.0\text{ms}$)**: Default fast **3.5ms attack** clamps titanium diaphragm ringing under 50dB ANC.
  - **Threshold ($-28\text{dB} \dots -6\text{dB}$)**
  - **Compression Ratio ($1.5:1 \dots 5.0:1$)**
- **Hardware Output Limiter**:
  - **Threshold ($-1.0\text{dBFS} \dots 0.0\text{dBFS}$)**: Guarantees true-peak inter-sample clipping protection on Bluetooth AAC.

### Studio 4: Acoustic Presets Vault ("Target Vault")
Switch between scientifically engineered reference targets or store custom curves:
- **Zenith Reference**: The linearized engineering baseline curing mid-bass bloat, ANC resonance, and AAC sizzle.
- **Harman IE 2019 Target**: Scientifically validated consumer curve with +5.5dB deep sub-bass shelf and smooth 8dB pinna rise.
- **Vocal & Acoustic Focus**: Forward intimate vocals (+4.5dB across 1.6k–3.5kHz) and trimmed mid-bass for jazz, podcasts, and acoustic music.
- **Basshead & EDM Slam**: Powerful 50Hz sub-bass (+7.5dB) paired with aggressive 145Hz notch (-6.0dB) for clean, un-distorted excursion.
- **Treble Sparkle & Classical**: Airy top-octave ($+5.0\text{dB}$ at 12kHz) and 35% spatial crossfeed for wide orchestral separation.
- **Flat Studio**: Clinical monitor response across all 64 bands.
- **Custom User**: Retains your exact custom parametric filter settings.

---

## 3. Architecture Overview

```
ZenithTuner/
├── rust-dsp/                          # Panic-safe Rust f64 DSP core (libzenith_dsp.so)
│   ├── Cargo.toml                     # Crate definition (cdylib, rlib, opt-level=3)
│   └── src/
│       └── lib.rs                     # RBJ Biquads, Custom Parametric Spec, Preamp Headroom, JNI
├── app/
│   ├── build.gradle.kts               # Android 12+ (API 31-35), Compose BOM, NDK arm64-v8a
│   └── src/main/
│       ├── AndroidManifest.xml        # Foreground service (mediaPlayback), session receiver
│       └── java/com/zenith/tuner/
│           ├── ZenithApplication.kt   # Low-importance notification channel configuration
│           ├── MainActivity.kt        # Edge-to-edge Compose container & permission lifecycle
│           ├── dsp/
│           │   ├── ZenithNativeDsp.kt # JNI bridge to libzenith_dsp.so + pure Kotlin fallback
│           │   ├── DynamicsProcessingManager.kt # 32 Pre-EQ + 32 Post-EQ + 4-Band MBC Damper
│           │   └── AcousticModel.kt   # State models (Presets, Parametric Stages, MBC, Tabs)
│           ├── service/
│           │   ├── ZenithDspService.kt# Foreground service managing global session 0 & players
│           │   └── AudioSessionReceiver.kt # Broadcast receiver for player session IDs
│           └── ui/
│               ├── theme/             # Obsidian Dark palette (#08080A, Titanium, Electric Cyan)
│               ├── components/
│               │   ├── BodePlotCanvas.kt     # 120Hz drawWithCache interactive frequency spline
│               │   ├── TactileCards.kt       # Express controls (Anti-Mud, Slam, Crossfeed)
│               │   ├── ParametricLabCards.kt # 8-stage parametric biquad customizer cards
│               │   ├── MbcDynamicsCards.kt   # 11mm Transient Damper & Limiter controls
│               │   └── GlassCard.kt          # Reusable obsidian glassmorphic card container
│               └── ZenithTunerScreen.kt      # Main 4-tab studio interface
└── build-rust.sh                      # Automation script to compile & package Rust binaries
```

---

## 4. Build & Deployment

### 1. Compile the Rust Library for arm64-v8a
```bash
cd rust-dsp
cargo ndk -t arm64-v8a -o ../app/src/main/jniLibs build --release
cd ..
```
*(Or execute `./build-rust.sh` to run the test suite and verify host/target compilation)*

### 2. Run Rust Unit Tests
```bash
cd rust-dsp && cargo test
```
All 10 unit tests verify biquad formulas, Nyquist safety, custom parametric tuning, preamp headroom, and C/JNI buffer alignment.

### 3. Build & Install Release APK on Connected Device
```bash
./gradlew installRelease
```
Or via Antigravity CLI:
```bash
agy run "Assemble and install release APK on connected Android 12 device"
```
