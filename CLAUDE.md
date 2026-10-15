# Haptic Habits

A minimal, open-source Android app for breaking subconscious habits. Every time I catch myself doing the habit, I tap a button and a running total goes up. Seeing the number climb is the negative feedback. No ads, no accounts, no bloat.

The app is sideloaded as an APK onto my own phone, not distributed through Google Play (for now).

## v1 (done)

- One screen: the total, shown large, and a single "+1" button. Nothing else.
- The total must survive closing the app, swiping it away, and restarting the phone.

## Haptic feedback on +1 (done; added off-roadmap before step 1; replaced in step 2)

- Every +1 tap played a short haptic tick that fired even with the system "touch feedback" setting off. Step 2 replaced it with the full vibration below.

## Step 1: Undo (done)

- An "Undo" button at the bottom center of the home screen, shown only while this session has changes to undo. Each press removes the most recent change from this session. Undo does not vibrate.

## Step 2: Settings screen and full vibration (done)

- A gear icon in the top right of the home screen opens Settings; the top-bar back arrow or system back returns home. The home screen is otherwise unchanged (total, +1, Undo).
- Each press plays a full-phone vibration like an incoming notification: two 200 ms buzzes at full strength, 120 ms apart. It still fires with the system "touch feedback" setting off.
- Settings is a scrolling list with two switches, "Vibrate on positive presses" and "Vibrate on negative presses", both on by default and persisted. A press vibrates when the switch for its value's sign is on (today only +1 exists, so only the positive switch has a visible effect). Undo never vibrates.

Next up is roadmap step 3.

## Roadmap (in order; one step per branch; do not build ahead of the current step)

1. **Undo.** An undo button on the home screen, shown only while there are changes to the sum made this session (since the app was opened). Each press removes the most recent change from this session (a button press or a reset).
2. **Settings screen and full vibration.** A gear icon in the top right of the home screen opens Settings. Replace the short tap haptic with a full-phone vibration like an incoming notification. Settings has two switches, "Vibrate on positive presses" and "Vibrate on negative presses", both on by default.
3. **Configurable buttons.** In Settings, add, rename, change the value of, and delete buttons. Each button has a name and a non-zero integer value, which can be negative (larger values for more destructive habits, negative values for positive habits). The home screen shows all buttons. Existing taps migrate to a default "+1" button with value 1.
4. **Colors and sum flash.** By default, positive-value buttons are green and negative-value buttons are red, and the sum flashes green when it goes up and red when it goes down (including on undo). A Settings switch inverts this (positive red, negative green) for both the button colors and the flash.
5. **History.** Settings → History lists every entry, newest first, with timestamp, button name, value, and the resulting sum after that entry. Any row can be deleted (with a confirmation).
6. **Reset sum to 0.** Settings → Reset (with a confirmation) brings the sum to 0 without deleting anything. The reset appears in History as its own row, can be deleted there to restore the previous sum, and can be undone like any other change this session.
7. **Trends.** Settings → Trends shows: the sum over time; per button, a trend line of presses per day / week / month; and per button, a GitHub-style contribution calendar of which days it was pressed.

Navigation: the home screen has only the gear icon. History, Trends, button setup, the switches, and Reset all live in Settings.

## Technical decisions

- Kotlin, Jetpack Compose, Material 3, single activity.
- Persistence: Room. Store each tap as an event row (`id`, `timestamp`, `weight`) and compute the total as `SUM(weight)`, observed as a Flow. This is deliberate: undo, weighted buttons, and trends all fall out of an event log with no data migration, whereas a single stored counter can't be turned into history later.
- Export Room schemas and write real migrations for every schema change. Never use destructive migrations; the tap history is the user's data. Schemas are exported by the Room Gradle plugin (`room { schemaDirectory(...) }` in `app/build.gradle.kts`) to `app/schemas/`; commit the generated JSON with every schema change.
- Data layer lives in `data/` (`TapEvent`, `TapEventDao`, `HabitDatabase` for Room; `AppSettings`, `SettingsStore` for DataStore). The UI is `MainActivity` (hosts the `NavHost` and the home `CounterScreen`) + `CounterViewModel`, and `SettingsScreen` + `SettingsViewModel`. ViewModels talk to the DAO / `SettingsStore` directly (no repository yet).
- The press vibration lives in `TapVibration` (renamed from `TapHaptics` in step 2): a `VibrationEffect.createWaveform` double pulse at amplitude 255 (constants `TIMINGS_MS` / `AMPLITUDES`), with an on/off `long[]` pattern fallback below API 26. It deliberately uses `Vibrator` rather than Compose/View haptic feedback, because those are silenced by the system touch-feedback setting. The vibration is tagged with media usage (`VibrationAttributes` on API 33+, `AudioAttributes` on 26-32) because Android 13+ can treat an untagged vibration as touch feedback; media (rather than notification) usage is deliberate too, since notification vibrations follow the ringer mode and can be muted in silent mode. Don't "simplify" it to `LocalHapticFeedback`.
- Every press goes through `press(weight)` in `MainActivity`: it plays `TapVibration` if `CounterViewModel.vibratesFor(weight)` and then calls `CounterViewModel.recordTap(weight)`. The sign decision is `AppSettings.vibratesFor(value)` (positive switch for > 0, negative switch for < 0); the ViewModel holds the settings as an eagerly collected `StateFlow` (defaults until DataStore loads) and stays free of Android services. Step 3's buttons should just call `press(button.value)`.
- Undo: `CounterViewModel` keeps the ids of event rows added this session in memory (survives rotation with the ViewModel; a fresh launch or process death starts a new session with nothing to undo). Undo pops the newest id and calls `TapEventDao.deleteById`; if that returns 0 (the row is already gone, e.g. deleted from History in step 5) it skips to the next id. All ViewModel writes go through one `Mutex` so an undo pressed right after +1 removes that tap, not an older one. Step 6 should push a reset row's id onto the same list so resets become undoable for free. Known gap for step 5: if History deletes every remaining session row, the Undo button stays visible until pressed once (the press is then a no-op that hides it); fix by routing History deletes through the same session state or deriving visibility from the DB.
- The Undo button is pinned to the bottom of the screen, outside the centered column, so showing/hiding it never moves the +1 button. Its icon is a vector drawable (`res/drawable/ic_undo.xml`) rather than the material-icons library.
- Planned data model for the roadmap (finalize when building each step, with a real migration each time):
  - Step 3 adds a `buttons` table (`id`, `name`, `weight`, plus an archived flag). Deleting a button archives it rather than removing the row, so History and Trends keep its name. `tap_events` gains a `buttonId`. Each event keeps its own `weight`, so changing a button's value never rewrites past entries.
  - Step 6 stores a reset as its own entry type in `tap_events`, not as deleted rows. The total becomes the sum of weights after the latest reset, so deleting the reset (or undoing it) restores the previous total, and deleting an older row never makes a reset stop landing on 0.
- Settings (vibration switches, color inversion) go in Jetpack DataStore (Preferences), not Room. They are preferences, not history. `SettingsStore` owns the keys (`vibrate_on_positive`, `vibrate_on_negative`; never rename stored key names) and maps them to `AppSettings`, whose constructor defaults are the defaults. The single process-wide DataStore is the `settings` file (`SettingsStore.from(context)`); a corrupt file is replaced with defaults. Add new switches (e.g. step 4's color inversion) as fields on `AppSettings` plus a key and setter on `SettingsStore`.
- Navigation: Navigation Compose with plain string routes (`HOME_ROUTE`, `SETTINGS_ROUTE` in `MainActivity`), so no kotlinx-serialization plugin is needed. `CounterViewModel` is created outside the `NavHost`, so it is activity-scoped: session undo survives a trip to Settings, and Settings rows (e.g. step 6's Reset) can reach it to add undoable changes. Navigation clicks are wrapped in `dropUnlessResumed` so a double tap can't navigate twice or pop the home screen. Settings is a `Column` with `verticalScroll` of `ListItem` rows; later steps add their rows (Buttons, color inversion, History, Reset, Trends) to it, and sub-screens (History, Trends, button setup) as new routes.
- Icons (`ic_undo`, `ic_settings`, `ic_arrow_back`) are vector drawables in `res/drawable/`, not the material-icons library.
- Draw Trends charts and the calendar with Compose `Canvas`; no third-party chart library.
- No INTERNET permission, no analytics, no ads, no Google Play Services. The only permission is `VIBRATE` (install-time, no prompt). Keep dependencies to AndroidX/Jetpack essentials.
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
- Unit-test the data layer (DAO / repository) and run tests before committing. DAO tests are JVM tests using Robolectric and an in-memory Room database (`app/src/test/`); run with `.\gradlew.bat testDebugUnitTest`. `SettingsStoreTest` runs DataStore on the plain JVM against a file in a `TemporaryFolder`. ViewModel tests use fakes (`FakeTapEventDao`, an in-memory `DataStore`) on the plain JVM.
- `NavigationTest` is a Robolectric Compose UI test that launches the real `MainActivity`. Keep it to one test method: the Room database and the settings DataStore are process-wide singletons that would carry over between tests. Compose UI tests on Robolectric need Espresso 3.7+ on the unit-test classpath (older Espresso fails with `InputManager.getInstance()`).
- Keep code simple and readable; prefer plain ViewModel + StateFlow over extra architecture until the roadmap actually needs it.
- When finishing a change, briefly explain what changed and how to verify it on the phone.
