# Privacy Policy

**Effective Date:** 24 November 2026
**Last Updated:** 24 November 2026
**App:** RECALL
**Developer:** RECALL Audio
**Contact:** recallaudio.support@gmail.com

This Privacy Policy explains what information RECALL collects, how it is used, and the choices you have.

## 1. Summary (plain English)

- **RECALL does not collect or transmit your audio.** All audio recorded by the app stays on your device.
- **Nothing is uploaded to any server owned or operated by RECALL Audio.** The app has no "cloud" component and no login.
- **You control what leaves your phone.** If you choose to save a recovered clip, you decide where it is shared and with whom.
- **Uninstalling the app deletes every recording it produced.**

## 2. Information We Access on Your Device

### 2.1 Microphone Audio
RECALL accesses the microphone continuously while the "Always Listening" feature is enabled. Captured audio is encoded as Opus (24 kbps mono) and written to a rolling buffer stored as time-stamped OGG chunks in the app's private storage directory (`/data/data/com.recall.app/files/chunks/`), which is not accessible to other apps on your device. The default buffer window is 24 hours; users can extend this up to 7 days through optional buffer-tier upgrades.

- **Purpose:** to allow you to recover the most recent audio when you did not record it manually.
- **Retention on device:** 24 hours by default (extendable up to 7 days by the user). Chunks older than the active buffer window are automatically deleted in a first-in/first-out schedule.
- **Transmission:** never. The app does not request the INTERNET permission and cannot send network traffic.
- **Third-party access:** none. The audio buffer is sandboxed to RECALL by the Android operating system.

### 2.2 Saved Recoveries (optional)
When you tap "Save to Device" on a recovered clip, the clip is written as a WAV file to your public `Music/RECALL/` directory using Android's MediaStore API. Once saved, the file is subject to normal device storage and whatever sharing choices you make. RECALL does not continue to track or access these files.

### 2.3 In-App Purchases
When you buy a minute pack through Google Play, your purchase is processed by Google Play Billing. Google provides RECALL with a purchase token confirming the transaction, which is used solely to credit the correct number of minutes to your account on your device. RECALL does not receive your name, payment-method details, or billing address from Google.

Google's handling of your payment information is governed by the [Google Play Terms](https://play.google.com/intl/en_us/about/play-terms/) and [Google Privacy Policy](https://policies.google.com/privacy).

### 2.4 Diagnostic / Crash Data
If you opt in to share crash reports, RECALL may collect anonymized technical diagnostic data (device model, Android version, stack trace) through Google Firebase Crashlytics to help us fix bugs. This data never includes audio content and is not linked to your identity. You can disable this at any time in the app settings.

## 3. Information We Do NOT Collect

- Audio content, in any form, on any server
- Transcripts or analyses of your conversations
- Your name, email address, phone number, or Google account identity
- Your location (precise or approximate)
- Your contacts, calendar, photos, or other personal data
- Any advertising identifiers

## 4. Permissions Requested

| Permission | Why it is needed |
|---|---|
| `RECORD_AUDIO` | To capture the rolling audio buffer that powers recovery |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MICROPHONE` | To keep listening while the app is in the background, as required by Android 14+ |
| `POST_NOTIFICATIONS` | To show the minimal "RECALL is listening" notification required by Android for any foreground service |
| `RECEIVE_BOOT_COMPLETED` | To resume listening automatically after a device reboot (only if you had the feature enabled) |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | To ask the system not to kill RECALL while idle, so recovery remains available at all times |
| `WRITE_EXTERNAL_STORAGE` (Android 9 and older only) | To write saved recoveries to the public Music/RECALL folder on older devices; replaced on Android 10+ by scoped MediaStore access |

The app does **not** request `INTERNET`, `ACCESS_FINE_LOCATION`, `READ_CONTACTS`, `READ_SMS`, `CAMERA`, or any other sensitive permission.

## 5. Children

RECALL is not directed to children under 13 and we do not knowingly collect any information from children. The app is age-rated Teen (13+) on Google Play.

## 6. Your Choices and Rights

- **Stop recording:** Toggle the "Always listening" switch inside the app to OFF.
- **Delete everything:** Uninstall the app. Android removes the entire buffer along with the app.
- **Keep a saved recording:** Any WAV in `Music/RECALL/` is yours. Move it, share it, or delete it from your file manager.
- **Buy minutes refund:** Minute packs are consumable digital goods and are generally non-refundable once consumed, except as required by Google Play's refund policy or applicable consumer-protection law. To request a refund within Google's eligibility window, use Google Play's built-in refund flow or contact support.

If you reside in the European Economic Area, the United Kingdom, Switzerland, California, or another jurisdiction that grants data-subject rights, those rights apply to any information we do hold (which, for most users of RECALL, is none at all because the app does not phone home).

## 7. Security

Because audio is stored only in the app's private sandbox directory and is never transmitted off the device, the primary security control is the Android operating system's app-isolation model. For maximum protection, we recommend:

- Keeping Android up to date
- Using a strong screen lock (PIN, password, biometric)
- Not sideloading untrusted apps that could attempt to read protected storage

## 8. Changes to This Policy

If we update this policy, the "Last Updated" date above will change. Material changes will be announced in the app on next launch.

## 9. Contact

Questions, concerns, or data requests:

**Email:** recallaudio.support@gmail.com
**Developer:** RECALL Audio
