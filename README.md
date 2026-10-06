# NEXUS — Android Native Multimedia AI Hub

> **"Every model. One canvas."**

NEXUS is a native Android AI multimedia workstation built with Jetpack Compose, Material 3, and Kotlin Coroutines. It delivers a unified interface for state-of-the-art generative video, image, audio, and reasoning models, abstracting complex backend infrastructure through a decoupled **Provider Architecture**.

---

## Features

- **Provider-Based Architecture:** Seamlessly abstracts Atlas Cloud and future AI inference APIs.
- **Dynamic Form Engine:** Automatically adapts input controls (aspect ratios, video durations, CFG scales, seed, steps) to each model's native capabilities.
- **Native Multimedia Rendering:**
  - **Image:** Fullscreen zoomable and pan-enabled canvas.
  - **Video:** Seamless Media3 ExoPlayer with timeline scrubber, play/pause controls, and looping.
  - **Audio:** Native Media3 playback with waveform slider and duration tracking.
- **Background Generation & Resilience:** WorkManager guarantees that generations finish reliably even if the app is backgrounded or minimized, firing a native Android completion notification with direct deep-linking.
- **Hardware-Backed Security:** API credentials are protected via `EncryptedSharedPreferences` backed by the **Android Keystore (AES256-GCM)**. Zero keys are ever hardcoded or printed in logs.
- **Android Intent Integration:** Deeply integrated with the Android ecosystem, featuring Photo Picker / SAF and incoming `ACTION_SEND` Share Intents.
- **Agentic Nexus Bridge:** Designed from the ground up for Model Context Protocol (MCP) agents to automate multimedia creation safely.

---

## Architecture Overview

```
com.vidadomu.nexus
├── core
│   ├── database (SQLite persistent store & Flow streams)
│   ├── model (Domain entities: Provider, Model, Request, Job, Output)
│   ├── network (OkHttp, redacting log interceptor, error translator)
│   ├── security (Keystore-backed storage, key masking, log sanitization)
│   └── work (WorkManager background tracker & notification dispatcher)
├── data
│   └── repository (Generation, Model Catalog, and Settings repositories)
├── provider
│   ├── api (NexusProvider interface)
│   ├── atlas (Atlas Cloud real API adapter)
│   ├── mock (Offline test provider for zero-cost QA)
│   └── registry (Dynamic provider resolution)
└── ui
    ├── create (Canvas & Dynamic Form Engine)
    ├── generations (Real-time generation tracker & diagnostics)
    ├── library (Media vault organized by modality)
    ├── models (Curated model registry & capabilities)
    ├── navigation (Bottom navigation & route controller)
    ├── output (Zoomable image & Media3 ExoPlayer video/audio viewers)
    ├── settings (Provider configuration & developer options)
    └── theme (Nexus Obsidian & Neon Aura Design System)
```

---

## Building and Testing

### Prerequisites
- JDK 17
- Android SDK 35/36
- Gradle 8.9+

### Run Automated Unit Tests
```bash
./gradlew test
```

### Build Debug APK
```bash
./gradlew assembleDebug
```

### Install onto Device
```bash
adb -s <DEVICE_SERIAL> install -r app/build/outputs/apk/debug/app-debug.apk
```
