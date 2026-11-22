# RECALL Android prototype (0.1.0)

This is an initial source prototype for Android, intended for a controlled test on the owner's device.

## What it is intended to do
- Start audio capture only after the user taps **Start recording** and grants microphone permission.
- Show an ongoing Android foreground-service notification while capturing.
- Keep up to five minutes of mono 16 kHz PCM audio in a circular file in app-private storage.
- Let the user choose a duration from 1 to 300 seconds and export the most recent segment as a WAV file in app-specific external storage.
- Play the exported WAV inside the app.

## Important limitations
- This source has not yet been compiled or tested on a physical device.
- It is a technical prototype, not a release-ready app. Background execution, Samsung battery management, Android 14/15/16 permission behavior, audio focus, storage, and buffer concurrency all require device testing and fixes.
- Recording must be used transparently and only where legally permitted. The persistent notification and Android microphone privacy indicator are intentional; this is not a covert recorder.
- Audio is stored locally by this prototype; no network or account functionality is included.
- The current UI is deliberately minimal. Wallet, payments, pricing, accounts, cloud sync, and production privacy controls are not implemented.

## Build
Open this folder in Android Studio with Android SDK Platform 35 installed, or build with Gradle 8.11+ and a compatible JDK. The Android Gradle Plugin is 8.9.1. The environment used to prepare this project did not contain Android SDK or Gradle, so no APK is included yet.
