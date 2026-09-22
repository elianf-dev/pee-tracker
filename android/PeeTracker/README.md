# PeeTracker (Android)

Kotlin/Compose app covering all four phases: Firebase Auth, invite-code groups, live
leaderboards, and streaks/badges. See `backend/docs/SCHEMA.md` for the full data contract.

**This app runs in Spark mode** — the Firebase project is on the free plan, so there are no
Cloud Functions and none can be deployed. Group creation/joining, leaderboard aggregation,
streaks and badge awarding all happen on-device here, validated only by
`backend/firestore.rules`. Push notifications do not work at all: sending FCM requires server
credentials that don't exist without a backend. The root `README.md` and `SCHEMA.md`'s "Spark
Mode" section have the full reasoning.

## Verified building on this machine

- JDK 17 (Microsoft Build of OpenJDK) at `C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot`
- Android SDK (platform 35, build-tools 35.0.0, platform-tools) at
  `C:\Users\elian\AppData\Local\Android\sdk`
- Gradle 8.9 at `C:\Users\elian\AppData\Local\Gradle\gradle-8.9` (used once to generate
  `gradle/wrapper/gradle-wrapper.jar`; `./gradlew` is self-sufficient after that)
- `local.properties` (gitignored) points `sdk.dir` at the SDK above

`./gradlew assembleDebug testDebugUnitTest` succeeds: produces
`app/build/outputs/apk/debug/app-debug.apk` and 16 unit tests pass (`StopwatchStateTest` 7,
`StreakLogicTest` 9).

The real `app/google-services.json` from the Firebase console is in place on this machine. It
is gitignored, so a fresh clone needs it downloaded again before the app can authenticate or
reach Firestore — the build will otherwise succeed and then fail at runtime.

## Prerequisites (for a different machine)

1. Install **JDK 17+**.
2. Install the **Android SDK** — either via Android Studio (simplest, prompts for everything),
   or standalone `cmdline-tools` + `sdkmanager` as done here (see above for the exact packages:
   `platform-tools`, `platforms;android-35`, `build-tools;35.0.0`).
3. If `gradle/wrapper/gradle-wrapper.jar` is missing, generate it once with any local Gradle
   install: `gradle wrapper --gradle-version 8.9` (or let Android Studio regenerate it on sync).
4. From the [Firebase console](https://console.firebase.google.com/), add an Android app with
   package name `com.peetracker.app`, download `google-services.json`, and place it at
   `app/google-services.json`.
   - Enable **Google** and **Email/Password** sign-in providers in Firebase Authentication.
   - Enable **Cloud Firestore**. Cloud Functions and Cloud Messaging are not used — see the
     Spark mode note above.
5. Deploy `backend/firestore.rules` to the same Firebase project. There is no functions deploy
   step. From `backend/`:

   ```
   ./node_modules/.bin/firebase deploy --only firestore --project streamline-ddf51
   ```

## Build

```
./gradlew assembleDebug
```

(`gradlew.bat assembleDebug` on a plain Windows shell instead of Git Bash/WSL.)

## Project layout

- `data/model/` — Firestore-mirrored data classes (`UserProfile`, `LogEntry`, `Group`,
  `GroupMember`, `LeaderboardEntry`, `Badge`), `@PropertyName`-annotated to match
  `backend/docs/SCHEMA.md` field names exactly.
- `data/remote/` — repositories, all talking to Firestore directly since there are no callables
  to wrap:
  - `AuthRepository` — Firebase Auth only.
  - `UserRepository` — owns `users/{uid}`, including `createProfileIfMissing`, which the client
    calls after sign-up because there is no `onUserCreate` trigger in Spark mode.
  - `GroupRepository` — create/join/leave written client-side. Join and leave **must** keep the
    `memberCount` change and the membership doc write in one batch: the rules require the
    membership doc to cross the matching edge in the same write. `memberCount` uses
    `FieldValue.increment` so two simultaneous joiners don't collide.
  - `LogRepository` — one transaction covering the log, both leaderboard deltas, the streak, and
    any milestone badge. Badges are write-once in the rules, so the transaction checks whether a
    milestone badge already exists before awarding it.
  - `LeaderboardRepository`, `BadgeRepository`, `PeriodBadgeEvaluator` (opportunistic period
    close, since there's no cron), `StreakLogic`, and the shared `DateKeys`.
- `notifications/` — FCM token registration/refresh, topic subscribe/unsubscribe, and the
  `FirebaseMessagingService` subclass. **Wired up but inert**: nothing can send to it in Spark
  mode.
- `ui/` — Compose screens and Hilt view models: `auth`, `groups` (onboarding/invite-share),
  `logging`, `leaderboard`, `streaks` (streak counter + badge grid + celebration overlay),
  `session`, `settings`.
- `util/` — `runCatchingCancellable`, which is `runCatching` that rethrows
  `CancellationException` instead of swallowing it. Use it rather than `runCatching` around any
  suspending call.
- `app/src/test/kotlin/com/peetracker/app/` — JVM unit tests, no emulator required:
  `StopwatchStateTest` (start/stop/reset, and that a wall-clock jump can't corrupt a duration)
  and `StreakLogicTest` (streak arithmetic and milestone re-earning). Run with
  `./gradlew testDebugUnitTest`.

## Known gaps

- **Never run against live Firebase.** The build is verified and the unit tests pass, but no
  end-to-end pass has been done on a device or emulator: sign in, create a group, join by code,
  log a trip, watch the leaderboard and streak update. This is the biggest open risk.
- Dependency versions (Firebase BoM, Compose BOM, AGP, Kotlin, Hilt, Credential Manager,
  `googleid`, `material-icons-extended`) resolved and compiled successfully as pinned, but
  weren't stress-tested against a real backend or device.
- Launcher icon and the notification small icon are minimal placeholders, not designed assets.
- No instrumented (on-device) tests yet — only JVM unit tests. The view models have no test
  coverage; `LogEntryViewModel` is the natural first one.
- `runCatching` still wraps suspending calls in several view models, where it swallows
  cancellation. The two sites where that caused work to continue after teardown are fixed; the
  rest are a pending cleanup.
- `DateKeys.weekKey` is untested at ISO year boundaries (a date in late December can belong to
  week 1 of the next ISO year), which is where this kind of date math usually breaks.
