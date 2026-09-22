# PeeTracker (Android)

Kotlin/Compose app covering all four phases: Firebase Auth, invite-code groups, live
leaderboards, and streaks/badges/notifications. See `backend/docs/SCHEMA.md` for the full
data contract.

## Verified building on this machine

A JDK, the Android SDK, and Gradle were installed on this dev machine and a full build was
run successfully:

- JDK 17 (Microsoft Build of OpenJDK) at `C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot`
- Android SDK (platform 35, build-tools 35.0.0, platform-tools) at
  `C:\Users\elian\AppData\Local\Android\sdk`
- Gradle 8.9 at `C:\Users\elian\AppData\Local\Gradle\gradle-8.9` (used once to generate
  `gradle/wrapper/gradle-wrapper.jar`; `./gradlew` is self-sufficient after that)
- `local.properties` (gitignored) points `sdk.dir` at the SDK above

`./gradlew assembleDebug testDebugUnitTest` succeeds: produces
`app/build/outputs/apk/debug/app-debug.apk` and all unit tests pass (`StopwatchStateTest`: 5/5).

**This build used a placeholder `app/google-services.json`** (fake project id, fake API key, a
placeholder OAuth web client just so `R.string.default_web_client_id` exists) purely so the
Google Services Gradle plugin and Kotlin compiler could be exercised end-to-end without a real
Firebase project. It is gitignored and **must be replaced with the real file from your Firebase
console before running the app against real data** — the placeholder will build but cannot
authenticate or reach Firestore.

## Prerequisites (for a different machine)

1. Install **JDK 17+**.
2. Install the **Android SDK** — either via Android Studio (simplest, prompts for everything),
   or standalone `cmdline-tools` + `sdkmanager` as done here (see above for the exact packages:
   `platform-tools`, `platforms;android-35`, `build-tools;35.0.0`).
3. If `gradle/wrapper/gradle-wrapper.jar` is missing, generate it once with any local Gradle
   install: `gradle wrapper --gradle-version 8.9` (or let Android Studio regenerate it on sync).
4. From the [Firebase console](https://console.firebase.google.com/), add an Android app with
   package name `com.peetracker.app`, download the real `google-services.json`, and place it at
   `app/google-services.json`, replacing the placeholder.
   - Enable **Google** and **Email/Password** sign-in providers in Firebase Authentication.
   - Enable Cloud Firestore, Cloud Functions, and Cloud Messaging.
5. Deploy `backend/firestore.rules` and `backend/functions` to the same Firebase project.

## Build

```
./gradlew assembleDebug
```

(`gradlew.bat assembleDebug` on a plain Windows shell instead of Git Bash/WSL.)

## Project layout

- `data/model/` — Firestore-mirrored data classes (`UserProfile`, `LogEntry`, `Group`,
  `GroupMember`, `LeaderboardEntry`, `Badge`), `@PropertyName`-annotated to match
  `backend/docs/SCHEMA.md` field names exactly.
- `data/remote/` — repositories: `AuthRepository` (Firebase Auth only — never creates
  `users/{uid}`, which the `onUserCreate` Cloud Function owns), `GroupRepository` (wraps the
  `createGroup`/`joinGroup`/`leaveGroup` callables), `LogRepository`, `LeaderboardRepository`,
  `BadgeRepository`, plus the shared `DateKeys` object both logging and leaderboards depend on.
- `notifications/` — FCM token registration/refresh, group-topic subscribe/unsubscribe, the
  `FirebaseMessagingService` subclass for foreground notification display.
- `ui/` — Compose screens and Hilt view models: `auth`, `groups` (onboarding/invite-share),
  `logging`, `leaderboard`, `streaks` (streak counter + badge grid + celebration overlay).
- `app/src/test/kotlin/com/peetracker/app/StopwatchStateTest.kt` — JUnit test for stopwatch
  start/stop/reset logic; runs with `./gradlew testDebugUnitTest` (no emulator required).

## Known gaps

- Dependency versions (Firebase BoM, Compose BOM, AGP, Kotlin, Hilt, Credential Manager,
  `googleid`, `material-icons-extended`) resolved and compiled successfully as pinned, but
  weren't stress-tested against a real Firebase backend or a physical/emulated device.
- Launcher icon and the FCM notification's small icon are minimal placeholders, not designed
  assets.
- No instrumented (on-device) tests yet — only JVM unit tests.
