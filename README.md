# Štih — Tarok Scorekeeper (Android)

Zapisovalnik točk za slovenski tarok. Android port of the iOS app **Štih** (SwiftUI → Kotlin + Jetpack Compose).

## Features

- 3–5 player sessions with dealer rotation
- All contracts: klop, tri/dve/ena, solo variants, berač, solo brez, odprti berač, barvni valat, valat
- Bonuses (trula, kralji, kralj ultimo, pagat ultimo, mond kaz) with announced/kontra states
- Kontra escalation on game and bonuses (kontra ×2 → re ×4 → sub ×8 → mort ×16)
- Radlci: earned on klop/berač/solo brez/odprti berač/valat, doubling, end-of-session penalty
- Obvezni klop triggers (round score / zero outcome)
- Custom house rules (contract scores, bonus scores, radlc rules)
- Session history with resume, duplicate, and CSV export
- Undo for deleted games, edit any past game with full recalculation

## Build

Requires JDK 17+ and the Android SDK (compileSdk 35).

```sh
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`.

## Architecture

- `model/` — data classes (kotlinx.serialization), JSON-compatible with the iOS app's Codable format
- `logic/` — `TarokScorer` (radlc doubling + klop processing), CSV export
- `data/` — `SessionManager` (JSON file persistence), `SettingsManager` (SharedPreferences + house-rule scoring)
- `ui/` — Jetpack Compose screens and components, `GameViewModel` as the single source of truth with full-history recalculation
