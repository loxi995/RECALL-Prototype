# Google Play Store Listing — RECALL

Everything needed to fill out the Play Console store listing form, the Data Safety questionnaire, and the manual-review declarations for sensitive permissions.

---

## App name
```
RECALL — Recover the audio you missed
```
(30 characters — Play's hard limit)

## Short description (80 characters max)
```
Didn't hit record? Recover the last 12 hours of audio from your phone.
```

## Full description (up to 4000 characters)

```
Ever finished a conversation, a lecture, a meeting, or a brilliant idea and
only then realized you forgot to press record? RECALL fixes that.

RECALL keeps a rolling 12-hour buffer of ambient audio on your phone. When
you need it, you choose how far back to go — 30 seconds or 12 hours — and
RECALL recovers that audio as a file you can play, save, and share. No more
"I wish I'd recorded that."

HOW IT WORKS
• RECALL listens in the background with a minimal always-on service.
• Audio is written to a private rolling buffer on your device — the newest
  12 hours are always there, older audio is automatically overwritten.
• Open RECALL, choose how many minutes to recover, tap Recover Audio.
• Play it back inside RECALL or save it to your Music folder as a WAV file.
• Share, trim, or import into any audio app you already use.

YOUR AUDIO STAYS ON YOUR PHONE
• No cloud. No server. No login. No tracking.
• The app does not have internet permission and cannot send anything off
  your device.
• Uninstalling RECALL deletes every byte of the buffer instantly.

WHAT'S FREE
• 60 seconds of recovery on your first use — try the full flow with zero
  commitment.

MINUTE PACKS (one-time purchases)
• 10 min — $0.99
• 30 min — $2.49
• 60 min — $3.99  (Best Value)
• 180 min — $8.99
• 600 min — $19.99
Minutes never expire.

GOOD TO KNOW
• Works with the phone locked, in your pocket, while other apps run.
• Battery impact is modest — the mic uses less than ~5% per day on most
  modern phones.
• Samsung devices may try to kill background apps; RECALL walks you
  through the one-tap battery-optimization exemption on first launch.

YOUR RESPONSIBILITY
In many places it is illegal to record a conversation without everyone's
consent. You are responsible for complying with the audio-recording laws
in your region. RECALL is a personal memory-aid tool, not a surveillance
tool — please use it ethically.

Questions or feedback: recallaudio.support@gmail.com
```

(Trim or expand freely. ~1900 characters, well under the 4000 limit, leaves room for a quick "What's new" paragraph per release.)

---

## Category + tags

- **Category:** Productivity (primary). Secondary: Tools.
- **Tags (if asked):** voice recorder, audio recovery, meeting assistant, dictation, memory aid

---

## Target audience

- **Target age range:** 13+ (Teen).
- **"Does your app appeal to children?"** → No.

---

## Content rating questionnaire — expected answers

| Question | Answer |
|---|---|
| Violence of any kind | No |
| Sexual content | No |
| Profanity / crude humor | No |
| Drugs / alcohol / tobacco | No |
| User-generated content shared publicly | No |
| In-app purchases | Yes — digital goods (minute packs) |
| Unrestricted web browsing | No |
| Shares user location | No |
| Does app collect personal information | No |

Expected rating: **Everyone** or **Everyone 10+**.
Recommend self-selecting **Teen** anyway because of the recording subject matter — it signals "this needs a grown-up decision" to reviewers and parents, and heads off complaints.

---

## Data Safety form — exact answers

Google asks a lot of questions in this section. All answers assume the current on-device-only build. Revisit and update the moment you add any cloud feature.

### Does your app collect or share any of the required user data types?
**Yes** — the app collects "Audio" on the device (microphone) and "Purchase history" through Google Play Billing. Everything else: **No**.

### Is all of the user data collected by your app encrypted in transit?
**Not applicable** — no user data is transmitted off the device. (Google still requires you to confirm the transit question; the answer is that nothing transits.)

### Do you provide a way for users to request that their data be deleted?
**Yes** — users delete all stored audio by uninstalling the app or toggling "Always Listening" off. There is no server-side data to delete.

### For each data type collected:

#### Microphone — Audio recordings
- **Collected?** Yes
- **Shared?** No
- **Processed ephemerally?** No (it is stored in a rolling 12-hour buffer)
- **Optional or required?** Required for the core feature; users may disable listening at any time.
- **Purposes:** App functionality
- **Collected for ads?** No
- **Collected for analytics?** No

#### Purchases — Purchase history (via Google Play Billing)
- **Collected?** Yes
- **Shared?** No
- **Optional or required?** Required if the user buys a minute pack
- **Purposes:** App functionality (credit the correct number of minutes)
- **Collected for ads?** No

All other data types: **No**.

---

## Sensitive-permission declarations

Google will ask you to justify these in the **App content → Sensitive permissions** section. Paste the exact text below.

### RECORD_AUDIO justification
```
RECALL's core feature is a rolling 12-hour buffer of ambient audio that the
user can retrieve on demand (e.g. for conversations or lectures they forgot
to record manually). Audio is stored only in the app's private sandbox on
the user's device, is never transmitted off the device, and is automatically
overwritten within 12 hours. Users can disable listening at any time via a
prominent switch inside the app.
```

### Foreground Service — Microphone type justification
```
Android 14 (API 34) requires the "microphone" foreground service type for any
app that uses the microphone while in the background. RECALL's rolling-buffer
functionality requires continuous microphone access, which is only possible
through a foreground service with this type. The service displays an ongoing
notification at all times and can be stopped by the user.
```

### POST_NOTIFICATIONS
```
RECALL displays the minimal ongoing notification required by Android for any
foreground service. The notification channel is set to IMPORTANCE_MIN so it
is invisible in the status bar and only appears in the shade.
```

### RECEIVE_BOOT_COMPLETED
```
The foreground listening service resumes automatically after a reboot only
if the user had the feature enabled before the reboot. This prevents the
rolling buffer from gapping every time the phone restarts.
```

### REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
```
Users are prompted (never forced) to grant a battery-optimization exemption
on first launch so Samsung and other aggressive OEMs do not silently kill
the listening service. The prompt is dismissible and the app continues to
function without it (on compatible devices).
```

---

## Release notes — v1.0.0 (first public release)

```
First public release of RECALL:
• 12-hour always-on audio buffer, fully on-device
• Recover any slice from 30 seconds up to 12 hours
• Save recoveries as WAV to your Music folder
• 60 seconds of free recovery for every new user
• Minute packs available (10 / 30 / 60 / 180 / 600 min)
```

---

## Screenshots to take on your phone

Use your Samsung S21 Ultra for all phone screenshots. Resolution will be ~1440×3200. Google accepts PNG or JPEG, 320–3840 px on either side. Take these:

1. **Hero shot:** Main screen with listening ON (green dot pulsing), balance showing "1 min 00 sec — Free trial · 60 sec included."
2. **Duration picker:** Open the wheel picker on "2 min 30 sec" so a scaleable number set is visible.
3. **Buy Minutes dialog:** Open the pack picker — shows pricing clearly.
4. **After recovery:** State right after a recovery with the RECOVERED toast visible.
5. **Playback:** Media player open with the saved WAV playing.
6. **Balance low:** The balance card showing "No minutes left" and the BUY MINUTES button highlighted.

Save them in `store/screenshots/` before uploading.

## Feature graphic + icon

- `store/feature-graphic-1024x500.png` — generated by this repo
- `store/app-icon-512.png` — generated by this repo

---

## Pre-launch checklist

- [ ] Google Play Console account created, identity verified ($25, 1–7 days)
- [ ] Privacy policy published and reachable
- [ ] Terms of Service published and reachable
- [ ] Support email `recallaudio.support@gmail.com` created and monitored
- [ ] Internal test track uploaded with v1.0.0 AAB
- [ ] Closed test track seeded with 12+ real Google-account testers for 14 days
- [ ] Content rating questionnaire completed
- [ ] Data Safety form completed with the answers above
- [ ] All sensitive-permission justifications pasted and saved
- [ ] Screenshots uploaded
- [ ] Feature graphic and icon uploaded
- [ ] Minute-pack SKUs created in Play Console (match the productIds in `Billing.kt`)
- [ ] Target countries selected (worldwide, minus any you want to exclude)
- [ ] Pricing reviewed per country (Google auto-converts the USD prices)
- [ ] Submit for production review
