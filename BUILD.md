# How to build & install AI Money Tracker

You have **three** ways to turn this source into an installable APK. Pick one.

> ⚠️ **Revoke the OpenAI key you pasted in chat.** It is compromised. Generate a new one at
> platform.openai.com → API keys and use that. Never commit `local.properties` (it's git-ignored).

---

## The one thing every method needs: the Gradle wrapper jar

This project ships the wrapper **scripts** (`gradlew`, `gradlew.bat`) and config, but **not** the
binary `gradle/wrapper/gradle-wrapper.jar` (binaries can't be authored as text). You get it by ONE of:

- Opening the project once in **Android Studio** (it generates it automatically), **or**
- Running `gradle wrapper --gradle-version 8.11.1` once (needs Gradle installed), **or**
- Letting **GitHub Actions** generate it (Method C — nothing to install locally).

---

## Method A — Android Studio (easiest, most reliable)

1. Install **Android Studio** (bundles JDK + Android SDK): https://developer.android.com/studio
2. *File → Open* → select the `expenseTrack` folder → wait for Gradle sync (first run needs internet).
3. Put your key in `local.properties`: `VITE_OPENAI_API_KEY=sk-proj-your-new-key`
4. *Build → Build APK(s)*, or press ▶ with your phone plugged in (USB debugging on) to install directly.
5. APK output: `app/build/outputs/apk/debug/app-debug.apk`

---

## Method B — VS Code / command line

1. Install **JDK 17** (https://adoptium.net/temurin/releases/?version=17) and the **Android SDK**
   (via Android Studio once, or the standalone command-line tools).
2. In `local.properties` set the SDK path and your key:
   ```properties
   sdk.dir=C:\\Users\\Akshay\\AppData\\Local\\Android\\Sdk
   VITE_OPENAI_API_KEY=sk-proj-your-new-key
   ```
3. Generate the wrapper jar once (see section above): `gradle wrapper --gradle-version 8.11.1`
4. Build:
   - Windows: `.\gradlew.bat assembleDebug`
   - macOS/Linux: `./gradlew assembleDebug`
5. Install on a connected phone: `adb install app/build/outputs/apk/debug/app-debug.apk`
   (or copy the APK to the phone and tap it; allow "install unknown apps").

---

## Method C — GitHub Actions (no local toolchain; build in the cloud)

Best if you only have a phone.

1. Create a **GitHub repo** and push this project to it (the included `.gitignore` keeps your key out).
2. In the repo: *Settings → Secrets and variables → Actions → New repository secret*
   - Name: `OPENAI_API_KEY`  Value: your **new** key.
3. The workflow `.github/workflows/build.yml` runs on every push (and via *Actions → Build APK →
   Run workflow*). It generates the wrapper, builds, and uploads the APK.
4. Open the finished run → **Artifacts → `app-debug-apk`** → download → install on your phone.

---

## After installing (any method)

1. Launch → the **first-run "Analyze past transactions"** screen appears.
2. Grant **SMS** permission, pick a range (30 days … 1 year), and let it scan your history.
3. Grant **Notification access** (Settings) so live bank/UPI app notifications are captured too.
4. Optional: turn on **Local-only mode** (Settings) to disable all network/AI.

## Security note about the key

A `BuildConfig` key is compiled **into the APK**. Fine for a personal build on your own phone — but
do **not** share that APK publicly, since the key can be extracted. For a public release, route AI
calls through your own small backend instead of shipping the key in the app.
