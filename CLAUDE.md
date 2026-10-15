# Haptic Habits

A minimal, open-source Android app for breaking subconscious habits. Every time I catch myself doing the habit, I tap a button and a running total goes up. Seeing the number climb is the negative feedback. No ads, no accounts, no bloat.

The app is sideloaded as an APK onto my own phone, not distributed through Google Play (for now).

## Current scope (v1)

- One screen: the total, shown large, and a single "+1" button. Nothing else.
- The total must survive closing the app, swiping it away, and restarting the phone.

## Roadmap (in order; do not build ahead of the current step)

1. Undo / delete individual taps.
2. Multiple configurable buttons, each with a weight (larger weights for more destructive habits, negative weights for positive habits).
3. Trends over time (per day / week, per button).

## Technical decisions

- Kotlin, Jetpack Compose, Material 3, single activity.
- Persistence: Room. Store each tap as an event row (`id`, `timestamp`, `weight`) and compute the total as `SUM(weight)`, observed as a Flow. This is deliberate: undo, weighted buttons, and trends all fall out of an event log with no data migration, whereas a single stored counter can't be turned into history later.
- Export Room schemas (`room.schemaLocation`) and write real migrations for every schema change. Never use destructive migrations; the tap history is the user's data.
- No INTERNET permission, no analytics, no ads, no Google Play Services. Keep dependencies to AndroidX/Jetpack essentials.
- Debug builds use `applicationIdSuffix ".debug"` (and a distinct app label) so a debug install from Android Studio can sit next to the sideloaded release build without a signature conflict.

## Environment

- Windows 11, Android Studio. Build from the terminal with `gradlew.bat` (e.g. `.\gradlew.bat assembleDebug`, `.\gradlew.bat assembleRelease`).
- `JAVA_HOME` should point at Android Studio's bundled JDK (typically `C:\Program Files\Android\Android Studio\jbr`). The SDK path comes from `local.properties`, which Android Studio generates and which must stay gitignored.
- Release APKs are signed with a keystore kept outside the repo. The signing config reads from a gitignored `keystore.properties`. Never commit the keystore, its passwords, or `local.properties`.

## Working conventions

- Small, focused commits with clear messages; one roadmap step per branch.
- Unit-test the data layer (DAO / repository) and run tests before committing.
- Keep code simple and readable; prefer plain ViewModel + StateFlow over extra architecture until the roadmap actually needs it.
- When finishing a change, briefly explain what changed and how to verify it on the phone.
