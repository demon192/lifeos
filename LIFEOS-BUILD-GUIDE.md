# LifeOS — Build Guide & Session Handoff

> **Purpose of this file:** Continuity doc for LifeOS, a solo hackathon project for **iQOO Hackathon 2026**.
> Read this FIRST in any new session. It captures every locked decision so we don't re-litigate them.
> The full product spec lives in `C:\Users\I769271\Downloads\LIFEOS — SENIOR ENGINEER MASTER PRODUCT BRIEF.md`.

---

## 0. READ THIS BEFORE ANYTHING

- **The builder is solo and fairly new to coding.** Scaffold heavily. Explain in plain language. Keep steps small, explicit, copy-paste-ready. Do NOT dump senior-engineer architecture and walk away.
- **Scope discipline is the #1 rule.** The product brief is a big vision; a beginner solo cannot build all of it. Follow the brief's own §24: build the smallest vertical slice, and **never break a working slice.** Every milestone must leave a demoable app.
- **Ignore `C:\Users\I769271\Downloads\CLAUDE.md`.** It auto-loads from Downloads and is about an unrelated SAP invoice project. Nothing to do with LifeOS.
- **Do not pull unrelated memories into this project.** The user asked for this project to stand on its own.

---

## 1. WHAT LIFEOS IS (one-liner)

"An AI layer between you and the physical world." Point the phone at a document → it understands it, remembers it, reasons about consequences, and turns it into action.

**The differentiator is NOT OCR. It is:**
`INFORMATION → CONTEXT → MEMORY → REASONING → ACTION`

The magic moment for judges: *"I showed my phone something once, and it remembered why it mattered"* — a **proactive** action the user didn't explicitly ask for.

---

## 2. EVENT CONTEXT (drives several decisions)

- **iQOO Hackathon 2026**, organized by iQOO + Reskilll. Phone-first hackathon. ₹40L prize pool.
- **Track chosen: Open Innovation / Productivity.** Open Innovation explicitly rewards *"a local or open-source model at the core"* — this is why we go fully on-device.
- **30-hour event.** Sat 10:00 clock start → Sun awards. Two judged checkpoints (Eval R1 Sat 19:00, Eval R2 Sun 09:00), Top 10 → final pitches (3–5 min) → Top 6 advance to Grand Finale (Bengaluru, 9–11 Oct 2026).
- **Loaner iQOO phone provided** at check-in. **Demo runs ON the loaner phone**, not the user's own. The FAQ says on-device inference *"targets the Snapdragon NPU (Sarvam, Gemma, Phi class models)"* → **the loaner is Snapdragon.**
- **Office Kit** (phone↔laptop bridge: screen mirror, shared clipboard, file transfer, remote control) is provided, pre-paired on the loaner. **Office Kit usage = 10% of the rubric** (measured by telemetry — counts + durations, not self-reported) + a **"Most iQOO Usage" award.**
- **Green Light** = laptop + phone both usable. **Red Light** = laptop closed as build machine, reachable only *through* Office Kit.
- **Free AI credits** offered → most teams will use cloud APIs. Our fully-on-device approach is a **differentiator**: works in airplane mode, no wifi dependency, no credits needed, privacy by default. **Say this in the pitch.**
- **Pre-building:** No rule against it was found; FAQ is written for teams arriving with a working app. **Decision: build the full MVP now, before the event.** (Re-verify with organizers if possible.)

---

## 3. LOCKED TECHNICAL DECISIONS

| Decision | Choice | Why |
|---|---|---|
| **Stack** | **Native Android — Kotlin + Jetpack Compose** | Best on-device AI, camera, notifications. Brief forbids "web app that runs on a phone." |
| **AI mode** | **Fully on-device** | Open Innovation bonus + airplane-mode demo. |
| **OCR / "seeing"** | **ML Kit Text Recognition** (on-device) | Fast, tiny, reliable. Does NOT use a vision-LLM to read images. |
| **Reasoning / extraction** | **Gemma 3 1B (INT4) via MediaPipe LLM Inference** | ~700MB, fits 8GB, runs GPU (dev phone) or NPU (loaner) automatically. Open-weights = the "local model" requirement. |
| **Later differentiator** | **Sarvam** (Indian-language / Hindi voice) if time allows | Indian open-source model, fits "how India lives" angle. |
| **Storage / memory** | **Room (SQLite)** for structured LifeEvents + lite Life Graph | Brief §16: structured facts in relational storage. Keep it simple. |
| **Reminders / actions** | **WorkManager** for scheduled notifications | Native, reliable. |
| **Voice** | Android **SpeechRecognizer + TTS** (on-device) | First-class interface per brief §8. Add after core works. |

### The frozen pipeline
```
iQOO Phone
├── CameraX          ← capture
├── ML Kit OCR       ← text extraction (on-device, instant)
├── Gemma 3 1B       ← LifeEvent JSON + reasoning (MediaPipe, GPU/NPU auto)
├── Room/SQLite      ← memory + lite Life Graph
├── WorkManager      ← scheduled notifications (reminder engine)
└── Jetpack Compose  ← UI (Life Brief, scan result, memory query)

         ↕ Office Kit (clipboard + file)  ← 10% of score, build a real moment

MacBook
└── Simple web page/script rendering exported LifeEvent data
    → "Weekly Expense Report" — visible in demo
```

---

## 4. DEVICES

| Role | Device | Chipset | Notes |
|---|---|---|---|
| **Dev / test** | User's **iQOO Z7 Pro 5G** | **MediaTek Dimensity 7200**, 8GB (+8 virtual, ignore for models), Android 15 / Funtouch 15, 256GB | Runs Gemma on **GPU (Mali-G610)**. NPU (APU) not practically reachable → **don't claim NPU on this device.** |
| **Demo** | **Loaner iQOO** (Snapdragon, unknown model until check-in) | Snapdragon + NPU | Build for the Z7 Pro as the **floor** so it runs everywhere; loaner just runs faster. |

**Honest framing for the pitch:** market **"local open-source model, fully on-device"** (always true). Only mention NPU if the loaner confirms it.

---

## 5. WORKFLOW

- **Claude Code runs on the user's Windows machine** (`C:\Users\I769271`). Code is written here.
- **Push to GitHub** (`https://github.com/demon192/lifeos.git`) → presenter **pulls on MacBook Pro** → builds in **Android Studio** → **sideloads APK to phone** → tests → reports back (screenshots, error text, logcat).
- **Claude cannot build/run the app on Windows** (no Android SDK/device here). **The presenter's test run is the first real execution.** Clear, detailed feedback from the presenter is the lifeline.

---

## 6. MILESTONE ROADMAP (never break a working slice)

- [x] **M1 — Vertical slice plumbing.** CameraX opens → point at text → ML Kit OCR → raw text on screen. No AI, no storage. *Proves the hard plumbing.* **✅ VERIFIED ON DEVICE (2026-09-14)** — live OCR reading text on the iQOO Z7 Pro. See §8 below.
- [x] **M2 — Understanding.** OCR text → Gemma 3 1B → `{amount, due_date}` LifeEvent JSON → display nicely. **✅ PIPELINE VERIFIED ON DEVICE (2026-09-14)** — first-run model download + on-device Gemma inference + result card all working on the iQOO Z7 Pro. **Capture UX (2026-09-14):** Capture takes a *still photo* → user confirms (**Retake / Use & understand**) → only on confirm does the app OCR the sharp still (`StillImageOcr`) and run Gemma. This replaced live-frame OCR for extraction because a steady, full-res still OCRs far better — accurate OCR text is the #1 driver of extraction quality. Extraction *quality* still being tuned (a 1B model; dense Indian electricity bills and photos-of-a-screen are hard — moiré/glare wreck OCR). Prompt hardened: field-location hints ("Total Payable"/"Bill Due Date"), and examples marked FORMAT-only so the model stops copying example values (it once echoed the example amount `1240`). **Demo target = a clean, well-lit English electricity bill (real paper beats a photo of a monitor).** Next quality lever if needed: low-temperature session API for determinism. **Crop + crash fix (2026-09-14):** added optional drag-to-crop on the frozen photo (dependency-free, clamped) so the user can isolate the bill; and capped OCR text fed to Gemma at `MAX_OCR_CHARS` (2000) — a dense still OCRs to thousands of chars and overflowing `MAX_TOKENS` was crashing MediaPipe *natively* (the "app closes on Use & understand" bug).
- [ ] **M3 — Action (COMPLETE DEMOABLE PRODUCT).** "Remind me 2 days before" button → WorkManager schedules a real notification.
- [ ] **M4 — Memory + query.** Save LifeEvents to Room → NL query "what do I owe this week?" answered from stored data.
- [ ] **M5 — Voice.** SpeechRecognizer input for capture + queries.
- [ ] **M6 — Office Kit moment.** Push LifeEvent data phone→laptop via clipboard/file → laptop "Weekly Expense Report" view (scores the 10%).
- [ ] **Polish** — Daily Life Brief screen ("3 things need your attention"), rehearse the 5-document demo flow (electricity bill, warranty, water notice, restaurant receipt, travel ticket).

**First document type = electricity bill.** Generalize to receipt/warranty/notice/ticket only after the slice works.

---

## 7. OPEN ITEMS — NEEDED TO START CODING

1. **GitLab repo URL** (or a chosen name so structure can be laid out and connected). *(RESOLVED: repo is on **GitHub** → `https://github.com/demon192/lifeos.git`. M1 pushed to `main` on 2026-09-14.)*
2. **Mac readiness:** presenter has **Android Studio + JDK 17** installed, and can sideload an APK? *(Answered: not confirmed → see `MAC-SETUP-FOR-PRESENTER.md`, send it to the presenter.)*
3. (Nice to have) Re-confirm pre-building is allowed with organizers.

**Next concrete action once above are answered:** scaffold the Kotlin/Compose project (`build.gradle.kts`, permissions, dependencies) and write **Milestone 1** code — copy-paste ready. *(DONE — see §8.)*

---

## 8. M1 SCAFFOLD — WHAT EXISTS & HOW TO BUILD IT

**App identity (locked):** package/applicationId `com.lifeos.app`, app name "LifeOS".

**What M1 does:** opens the back camera, runs ML Kit text recognition on the live feed
on-device, and shows the recognized text in a panel at the bottom. No AI, no storage — just
the hard plumbing (camera + OCR), exactly per the brief's smallest-slice rule.

**Files created:**
```
settings.gradle.kts, build.gradle.kts, gradle.properties, .gitignore
gradle/libs.versions.toml                       ← all library versions live here
gradle/wrapper/gradle-wrapper.properties        ← pins Gradle 8.9
app/build.gradle.kts, app/proguard-rules.pro
app/src/main/AndroidManifest.xml                ← camera permission
app/src/main/res/…                              ← strings, theme, adaptive launcher icon (XML, no PNGs)
app/src/main/java/com/lifeos/app/
  MainActivity.kt        ← app entry, sets up Compose
  CameraScreen.kt        ← permission gate + CameraX preview + OCR overlay
  TextAnalyzer.kt        ← ML Kit OCR on each camera frame
  ui/theme/Theme.kt      ← Compose theme
```

**⚠️ One gap to close on the Mac before first build — the Gradle wrapper JAR.**
The binary `gradle/wrapper/gradle-wrapper.jar` (and the `gradlew` / `gradlew.bat` scripts) can't
be generated on the Windows side. Android Studio regenerates them automatically when it opens the
project. If it doesn't, from the `LifeOS` folder run: `gradle wrapper --gradle-version 8.9`
(needs a system Gradle: `brew install gradle`). Commit the generated wrapper files afterward.

**How to build (short version — full setup in `MAC-SETUP-FOR-PRESENTER.md`):**
open the `LifeOS` folder in Android Studio → let Gradle sync → plug in the iQOO → press ▶ Run →
tap **Allow** on the camera prompt → point at any printed text.

**M1 is "passing" when:** printed text you point at appears in the bottom panel within a second or
two, updating live as you move the phone. Send back a screenshot + any Logcat `FATAL` block if not.

---

## 9. GUARDRAILS FROM THE BRIEF (don't violate)

- High-consequence/financial actions require **explicit user confirmation**; never auto-execute.
- Distinguish **extracted fact** vs **AI inference**; show source; degrade gracefully ("Possible due date: Sept 7. Please verify.").
- No medical diagnosis, no payment execution, no silent data sharing.
- Minimize cognitive load: `POINT → UNDERSTAND → CONFIRM → DONE`. No dashboards/long forms/notification spam.
- Non-goals for MVP: payments, banking, WhatsApp/email integration, smart-home, multi-agent, 3D UI, Kubernetes, multi-user infra.

---

## 10. GETTING THE GEMMA MODEL (one-time human step for M2)

The app runs **Gemma 3 1B (INT4)** on-device via MediaPipe. The model file is ~555 MB — too big to
put in the APK or in git — so it must get onto the phone one of two ways. **Do this once.**

**Model file:** `gemma3-1b-it-int4.task` (555 MB), Gemma 3 1B INT4, MediaPipe `.task` format.

### Option A — Normal path: download-on-first-run (what end users/judges get) ✅ WIRED UP
`MODEL_URL` in [`ModelManager.kt`](app/src/main/java/com/lifeos/app/ModelManager.kt) is already set to a
**public, ungated** re-host (verified 2026-09-14, ~555 MB, no login):
`https://huggingface.co/AfiOne/gemma3-1b-it-int4.task/resolve/main/gemma3-1b-it-int4.task`
So on first launch the app downloads the model (progress bar), then works offline forever.
Nothing to set up — just build & run.

> **⚠️ HuggingFace URL gotcha (this bit people):** a download URL must use **`/resolve/`**, not
> **`/blob/`**. A `/blob/` link returns the *web page* for the file, so the app saves a few-KB HTML
> file instead of the 555 MB model — you'll see a "file too small / corrupt" failure. Always
> `/resolve/main/<filename>`.

If you'd rather host your own copy (e.g. the original **gated** `litert-community/Gemma3-1B-IT`):
accept the Gemma license on HF while logged in, download the 555 MB `gemma3-1b-it-int4.task`, upload
it as a **GitHub Release asset** on `github.com/demon192/lifeos` (Releases → Draft → drag the file →
Publish; up to 2 GB, public, no auth), then paste its `…/releases/download/<tag>/…` URL into `MODEL_URL`.

### Option B — Dev shortcut: adb push (fastest for testing, no upload needed)
Skip the URL entirely — push the file straight to the phone once:
```bash
adb shell mkdir -p /data/local/tmp/llm
adb push gemma3-1b-it-int4.task /data/local/tmp/llm/gemma3-1b-it-int4.task
```
The app checks that path first and uses it automatically. **Note:** you must redo this on the
**loaner phone** at the event. For the actual demo, Option A is safer (nothing to set up on stage).

### If it doesn't load
- App says "no model / no URL": you skipped both options, or `MODEL_URL` is still blank.
- Crash in `createFromOptions` mentioning tokens/context: tweak `MAX_TOKENS` in
  [`GemmaEngine.kt`](app/src/main/java/com/lifeos/app/GemmaEngine.kt) (see the comment there).
- Send back the Logcat `FATAL` block.
