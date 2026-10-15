# Haptic Habits

A minimal, open-source Android app for breaking subconscious habits. Every time I catch myself doing the habit, I tap a button and a running total goes up. Seeing the number climb is the negative feedback. No ads, no accounts, no bloat.

The app is sideloaded as an APK onto my own phone, not distributed through Google Play (for now).

## v1 (done)

- One screen: the total, shown large, and a single "+1" button. Nothing else.
- The total must survive closing the app, swiping it away, and restarting the phone.

Next up is roadmap step 1.

## Roadmap (in order; do not build ahead of the current step)

1. Undo / delete individual taps.
2. Multiple configurable buttons, each with a weight (larger weights for more destructive habits, negative weights for positive habits).
3. Trends over time (per day / week, per button).

## Technical decisions

- Kotlin, Jetpack Compose, Material 3, single activity.
- Persistence: Room. Store each tap as an event row (`id`, `timestamp`, `weight`) and compute the total as `SUM(weight)`, observed as a Flow. This is deliberate: undo, weighted buttons, and trends all fall out of an event log with no data migration, whereas a single stored counter can't be turned into history later.
- Export Room schemas and write real migrations for every schema change. Never use destructive migrations; the tap history is the user's data. Schemas are exported by the Room Gradle plugin (`room { schemaDirectory(...) }` in `app/build.gradle.kts`) to `app/schemas/`; commit the generated JSON with every schema change.
- Data layer lives in `data/` (`TapEvent`, `TapEventDao`, `HabitDatabase`). The UI is `MainActivity` + `CounterViewModel`, which talks to the DAO directly (no repository yet).
- No INTERNET permission, no analytics, no ads, no Google Play Services. Keep dependencies to AndroidX/Jetpack essentials.
- Debug builds use `applicationIdSuffix ".debug"` (and a distinct app label) so a debug install from Android Studio can sit next to the sideloaded release build without a signature conflict.

## Environment

- Windows 11, Android Studio. Build from the terminal with `gradlew.bat` (e.g. `.\gradlew.bat assembleDebug`, `.\gradlew.bat assembleRelease`).
- `JAVA_HOME` should point at Android Studio's bundled JDK (typically `C:\Program Files\Android\Android Studio\jbr`). The SDK path comes from `local.properties`, which Android Studio generates and which must stay gitignored.
- Release APKs are signed with a keystore kept outside the repo. The signing config reads from a gitignored `keystore.properties`. Never commit the keystore, its passwords, or `local.properties`. (Not set up yet; until it is, only debug builds are installable.)
- The Gradle daemon and test JVM run on JDK 25. Robolectric needs the `--add-opens=java.base/jdk.internal.access=ALL-UNNAMED` test JVM arg already set in `app/build.gradle.kts`; keep it.
- After changing Gradle files from outside Android Studio, the user must Gradle-sync before Run is enabled; mention this when build files change.
- Debug (`.debug`) and release builds are separate apps with separate databases; taps recorded in one never appear in the other.

## Working conventions

- Small, focused commits with clear messages; one roadmap step per branch.
- Unit-test the data layer (DAO / repository) and run tests before committing. DAO tests are JVM tests using Robolectric and an in-memory Room database (`app/src/test/`); run with `.\gradlew.bat testDebugUnitTest`.
- Keep code simple and readable; prefer plain ViewModel + StateFlow over extra architecture until the roadmap actually needs it.
- When finishing a change, briefly explain what changed and how to verify it on the phone.
