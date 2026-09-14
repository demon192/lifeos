# Mac Setup — Build & Run LifeOS on a Phone

> **Who this is for:** the person building LifeOS on the MacBook. You do **not** need Android
> experience. Follow this once, top to bottom. It takes ~45–60 min, mostly downloads.
> When you're done you'll be able to: pull the code, open it, build it, and put the app on a phone.

The plan: **pull code from GitLab → open in Android Studio → plug in the phone → press Run.**
The app installs straight onto the phone over the USB cable. No app store, no APK juggling for normal testing.

---

## Part A — Install the tools (do this once)

### 1. Install JDK 17 (Java)
Android Studio ships its own Java, but having JDK 17 on the system avoids surprises.

- Easiest way, if you have Homebrew:
  ```bash
  brew install --cask temurin@17
  ```
- No Homebrew? Download "Temurin 17 (LTS)" for macOS from https://adoptium.net → run the installer.
- Check it worked:
  ```bash
  java -version
  ```
  You want a line that says `17.x.x`. (If it says a different number, that's usually fine —
  Android Studio uses its own bundled Java for building. This is just a safety net.)

### 2. Install Android Studio
- Download from https://developer.android.com/studio → open the `.dmg` → drag **Android Studio**
  into **Applications**.
- Launch it. On first run it opens a **Setup Wizard** — click through with the **Standard**
  option. It will download the Android SDK, a platform, and build tools automatically.
  **Let it finish** (this is the big download). Requires internet.
- If it asks about "Android Virtual Device" / emulator, you can skip it — we test on a real phone.

### 3. Confirm the SDK installed
- In Android Studio: **Settings** (⌘,) → **Languages & Frameworks → Android SDK**.
- Under **SDK Platforms**, make sure **Android 15 (API 35)** is checked. If not, check it and hit Apply.
- Under **SDK Tools**, make sure **Android SDK Build-Tools** and **Android SDK Platform-Tools**
  are checked. Apply.

---

## Part B — Get the phone ready (do this once per phone)

We have two phones. **Same steps for both.**
- **Dev/test phone:** iQOO Z7 Pro 5G (this is what we build against day-to-day).
- **Demo phone:** the loaner iQOO you get at check-in.

### 1. Turn on Developer Options
On the phone: **Settings → About phone → Software version** (Funtouch OS puts the build number here)
→ tap **Build number** 7 times → it says "You are now a developer."

### 2. Turn on USB debugging
**Settings → System → Developer options** → turn **ON**:
- **USB debugging**
- **Install via USB** (Funtouch may call it this; turn on anything about installing apps over USB)

### 3. Plug the phone into the Mac
- Use a **data** USB-C cable (some cables are charge-only — if the phone doesn't show up, try another cable).
- The phone pops up **"Allow USB debugging?"** → check **Always allow from this computer** → **Allow**.
- Verify the Mac sees it. In Android Studio's top toolbar, the device dropdown should now show
  your phone's name (e.g. "iQOO Z7 Pro"). If you prefer the terminal:
  ```bash
  ~/Library/Android/sdk/platform-tools/adb devices
  ```
  You want a line ending in `device` (not `unauthorized` — if unauthorized, re-check the Allow popup).

---

## Part C — Pull, build, run (the loop you'll repeat all hackathon)

### 1. Get the code
```bash
git clone <GITLAB_REPO_URL>
cd LifeOS
```
(The repo URL is still TBD — see the build guide's Open Items. Once it exists, paste it here.)

### 2. Open it
- Android Studio → **Open** → pick the `LifeOS` folder (the one with `settings.gradle.kts` inside).
- It will say **"Gradle sync"** and grind for a few minutes the first time (downloading libraries).
  Let it finish. Green "sync successful" at the bottom = good.
- If it complains it can't find a JDK, go **Settings → Build → Build Tools → Gradle** and set
  **Gradle JDK** to the bundled **jbr-17** (or the Temurin 17 you installed).

### 3. Run it on the phone
- Pick your phone in the device dropdown (top toolbar).
- Press the green **▶ Run** button (or ⌃R).
- First build takes a few minutes; later builds are seconds. The app launches on the phone by itself.
- The phone will ask for **Camera permission** the first time — tap **Allow**.

### 4. To pull new code later
```bash
git pull
```
Then press **▶ Run** again. That's the whole loop.

---

## If something breaks — what to send back

Claude is writing this code on a Windows machine and **cannot run it** — your test run is the
first real execution. When something fails, the more detail you send, the faster it's fixed:

1. **Where it failed:** did Gradle sync fail? did the build fail? did the app crash on the phone?
2. **The red error text.** Copy the actual message, not "it didn't work."
   - Build errors show in the **Build** tab at the bottom.
   - Crashes show in **Logcat** (bottom toolbar). Filter Logcat by the app or search for the word
     `FATAL`. Copy the block starting at `FATAL EXCEPTION` down through the lines that say
     `at com.lifeos...`.
3. **A screenshot** of the phone screen if it looks wrong but didn't crash.

Paste that back and the next fix comes as copy-paste-ready code.

---

## Quick reference

| Thing | Where |
|---|---|
| SDK location | `~/Library/Android/sdk` |
| `adb` (device tool) | `~/Library/Android/sdk/platform-tools/adb` |
| List connected phones | `adb devices` |
| Open project | Android Studio → Open → the `LifeOS` folder |
| Build + install to phone | green ▶ Run button |
| See crash logs | Logcat tab, search `FATAL` |
