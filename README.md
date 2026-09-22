# Pee Tracker

A fun, competitive bathroom-trip tracker you share with friends. Two native apps (iOS/SwiftUI,
Android/Compose) share one Firebase backend.

See `.claude/plans` (in the Claude Code session that built this) for the full multi-phase plan.
This repo currently implements:
- **Phase 1: Auth + basic logging**
- **Phase 2: invite-code groups** — create/join a group via a `PEE-XXXX` code, share it with
  friends, log bathroom trips into your real group instead of a test placeholder.
- **Phase 3: leaderboards + resets** — live daily/weekly leaderboards per group, maintained
  server-side by Cloud Functions triggers on every log create/delete, plus scheduled functions
  that finalize each period.
- **Phase 4: streaks, badges, notifications, polish** — daily streaks with milestone badges,
  period badges (Camel of the Day/Week, Most Regular), push notifications (new leader, new
  member, weekly results, streak milestones) over FCM, and a light haptics/animation pass on
  the core logging interaction.

This completes the plan's four phases. Natural next steps beyond it: multi-group switching,
group settings/leave-group UI, richer notification copy, and a real design pass beyond the
"light polish" done here.

**Running on Firebase's free Spark plan, no Cloud Functions.** Project `streamline-ddf51` stays
off the paid Blaze plan (no credit card), which means Cloud Functions aren't available at all.
Everything that used to be a Cloud Function — invite codes, group create/join/leave, leaderboard
aggregation, streaks, badge awarding — now happens directly on-device, governed by
`firestore.rules`. Push notifications are the one casualty: sending FCM messages requires server
credentials that don't exist without a backend, so there simply are none in this mode. Full
reasoning and the exact trust trade-offs are in `backend/docs/SCHEMA.md`'s "Spark Mode" section.
The `functions/` code is kept in the repo, fully unit-tested, as an opt-in upgrade path if this
project ever moves to Blaze.

## Build status

**Android**: verified building for real on this machine (JDK 17 + Android SDK + Gradle 8.9
installed locally — see `android/PeeTracker/README.md` for exact versions/paths).
`./gradlew assembleDebug testDebugUnitTest` succeeds — produces a real debug APK and all unit
tests pass. This used a gitignored placeholder `google-services.json`; swap in the real one from
your Firebase console before running against actual data.

**iOS**: still unbuilt — this is a Windows machine with no Xcode available. The `project.yml`
(XcodeGen) and all Swift sources are believed correct by careful manual review, but have never
been compiled. This is the biggest remaining risk in the codebase — budget time for a real build
pass on a Mac before trusting the iOS side.

## Layout

- `backend/` — Firebase project: Firestore rules/indexes, Cloud Functions, rules unit tests.
  See `backend/docs/SCHEMA.md` for the authoritative data contract both apps must match.
- `ios/PeeTracker/` — SwiftUI app. Requires a Mac + Xcode; see its README for setup
  (this repo was built on Windows, so the `.xcodeproj` is generated via XcodeGen, not committed).
- `android/PeeTracker/` — Compose app. Requires JDK 17+ and the Android SDK; see its README.

## Getting started

1. **Firebase project**: create one at https://console.firebase.google.com (this repo is wired
   to `streamline-ddf51`), enable Authentication (Google, Apple, Email/Password providers) and
   Cloud Firestore. Cloud Functions and Cloud Messaging are **not** needed in Spark mode — see
   above.
2. `backend/.firebaserc` already points at the project ID above.
3. `cd backend && npm install`
4. `firebase deploy --only firestore` to deploy the rules + indexes for real, or
   `npm run emulators` to run everything locally first.
5. Follow `ios/PeeTracker/README.md` and `android/PeeTracker/README.md` to set up each client.
   Both need the real `google-services.json`/`GoogleService-Info.plist` from the Firebase
   console — no Cloud Functions deploy step is required for either.

## Development environment note

This project was scaffolded on Windows. The Android app can be built here once JDK 17+ and the
Android SDK are installed. The iOS app requires a Mac with Xcode — its project is defined via an
XcodeGen `project.yml` rather than a committed `.xcodeproj`, so `xcodegen generate` produces a
working Xcode project in one step once you're on a Mac.
