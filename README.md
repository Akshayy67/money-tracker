# AI Money Tracker (Android)

An offline-first, privacy-focused, AI-assisted expense tracker for India. It auto-captures
transactions from bank/UPI SMS and app notifications, categorizes them (learning from your
corrections), tracks people/splits/budgets/goals/subscriptions, and does **rigorous, deterministic
cash-flow forecasting** plus scheduled digest recaps. All money math is computed in Kotlin — the
Claude API is used only to phrase insights, write digest copy, categorize genuinely unknown
merchants, and power the chat assistant over data the app computes.

## Tech stack

- **Kotlin**, **Jetpack Compose** (Material 3, dynamic color, dark mode), single-activity +
  navigation-compose.
- **Clean architecture + MVVM**: `data` (Room, parsers, repositories, capture, remote),
  `domain` (engines, use cases, models), `ui` (Compose screens + ViewModels).
- **Hilt** DI, **Room** (encrypted via SQLCipher), **Coroutines/Flow**, **WorkManager**,
  **Paging 3**, **DataStore**, **OkHttp + kotlinx.serialization**.
- Charts are self-contained Compose `Canvas` composables (no external chart dependency to break).

## Project layout

```
com.aimoneytracker
├── data
│   ├── local        # Room: entities, DAOs, AppDatabase, converters
│   ├── parser       # Parser registry, bank rules, field extractors, sender filter, normalizer
│   ├── processor    # TransactionProcessor: the end-to-end ingestion pipeline
│   ├── repository   # Repositories (single source of truth wrappers + filters)
│   ├── capture      # SmsReceiver, NotificationListener
│   ├── remote       # AnthropicAiService (Claude API)
│   ├── backup       # Encrypted backup + CSV/JSON/PDF export
│   ├── preferences  # SettingsRepository (DataStore)
│   └── security     # DatabaseKeyProvider (SQLCipher key + PIN)
├── domain
│   ├── model        # Enums + shared models
│   ├── categorize   # CategoryCatalog + CategorizationEngine
│   ├── process      # Duplicate / Transfer / Refund / Recurring detectors
│   ├── forecast     # ForecastEngine + calibrator (§15)
│   ├── digest       # DigestEngine (§16)
│   ├── insights     # InsightsEngine (§14)
│   ├── ai           # AiService boundary
│   └── usecase      # GenerateForecast / GenerateDigest / AnswerChat
├── ui               # Compose screens + ViewModels, theme, navigation, components
├── work             # WorkManager workers + scheduler + boot receiver
└── widget           # Home-screen quick-add widget
```

## Setup

1. **Open in Android Studio** (Giraffe+/Koala recommended), JDK 17. Let Gradle sync.
   - If `./gradlew` is missing the wrapper jar, run `gradle wrapper` once, or just build from the IDE.
2. **Anthropic API key** (optional — the app runs fully without it via Local-only fallbacks):
   put it in one of:
   - `local.properties`: `ANTHROPIC_API_KEY=sk-ant-...`
   - root `gradle.properties`: `ANTHROPIC_API_KEY=sk-ant-...`
   - env var `ANTHROPIC_API_KEY`
   It is injected into `BuildConfig.ANTHROPIC_API_KEY`. The model is set in `app/build.gradle.kts`
   (`ANTHROPIC_MODEL`, default `claude-sonnet-4-6`).
3. **Run** on a device/emulator (minSdk 26 / Android 8.0).

## Permissions (granted at runtime)

- `RECEIVE_SMS` / `READ_SMS` — auto-capture + 12-month history backfill.
- Notification access (system settings) — capture bank/UPI app notifications via
  `TransactionNotificationListener`.
- `POST_NOTIFICATIONS` — review prompts, alerts, digests.
- `READ_CONTACTS` (optional) — name suggestions for people.
- Biometric — app lock.

Grant SMS + notification access on first run, then trigger **Settings → Import last 12 months of
SMS** to populate history.

## Key engines (all deterministic)

- **Parsing** (`data/parser`): a pluggable `ParserRegistry` of `RegexBankParser`s (SBI, HDFC, ICICI,
  Axis, Kotak, PNB, generic UPI/GPay/PhonePe/Paytm). Each result carries a 0–1 confidence; raw
  messages are stored for reprocessing.
- **Processing** (`data/processor`): dedup + merge, transfer detection (own-account pairs excluded
  from income/expense), refund/reversal linking, failed-txn handling, confidence-based review queue.
- **Categorization** (`domain/categorize`): rule → learned merchant-map → keyword heuristics → AI
  fallback. Every user correction creates/strengthens a rule and recategorizes past similar txns.
- **Forecasting** (`domain/forecast`): layered — deterministic scheduled events, a recency-weighted,
  day-of-week/day-of-month-seasonal, outlier-winsorized variable-spend model, MTD-vs-history blend,
  ±1σ confidence band, safe-to-spend, run-low date, and self-calibration from stored snapshots.
- **Digests** (`domain/digest`): weekly/monthly recaps with comparisons, anomaly flags, upcoming
  bills, goal progress — posted as rich notifications and stored for history.

## Adding a new bank/UPI format

1. Open `data/parser/BankParsers.kt`.
2. Add one instance, declaring the sender tokens (and optional body markers) that identify it:
   ```kotlin
   val YesBankParser = RegexBankParser(
       bankName = "Yes Bank",
       senderTokens = listOf("YESBNK", "YESBK"),
       bodyMarkers = listOf("yes bank"),
   )
   ```
3. Register it in `allBankParsers` **before** the generic fallbacks.
4. If its wording is unusual, subclass `RegexBankParser` and override `parse(...)`, reusing
   `FieldExtractors`. Add a sample to `app/src/test/.../ParserTest.kt`.

No other code changes are needed — the registry routes by confidence automatically. Run **Settings →
Reprocess stored messages** to re-parse history with the new rule.

## Tests

`app/src/test` covers the parser, dedup, transfer detection, forecast math, and digest generation:

```
./gradlew testDebugUnitTest
```

## Privacy & security

- All financial data is stored in an **encrypted SQLCipher** database (with a graceful unencrypted
  fallback if the native lib is unavailable on a device).
- **Local-only mode** (Settings) disables every network/AI call.
- App lock (PIN/biometric), `FLAG_SECURE` to blur amounts in the app switcher, and an edit/audit log.
- Encrypted backups (AES-256-GCM) to app storage; CSV/JSON/PDF export via FileProvider.

## Notes / extension points

- DB is at schema **version 1**; add `Migration` objects to `AppDatabase` when bumping the version.
- Screenshot/voice OCR capture is wired as an explicit extension point in `MainActivity`
  (`handleShareIntent`) and `TransactionSource.OCR/VOICE`.
- The chat assistant resolves a deterministic answer first and only asks Claude to phrase it.
