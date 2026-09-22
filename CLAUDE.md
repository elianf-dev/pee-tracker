# Pee Tracker

Competitive bathroom-trip tracker shared with friends. Three trees, one Firebase project
(`streamline-ddf51`): `android/PeeTracker` (Compose), `backend/` (Firestore rules + an unused
Cloud Functions tree), `ios/PeeTracker` (SwiftUI).

## Read this first: Spark mode

The Firebase project is on the **free Spark plan, so no Cloud Functions are deployed and none
can be.** Everything a function used to do — invite codes, group create/join/leave, leaderboard
aggregation, streaks, badge awarding — now happens **on-device**, and `backend/firestore.rules`
is the *only* server-side validation that exists. There is no trusted server double-checking
anything.

Consequences that trip people up:

- `backend/functions/` is **dead code**, kept only as an upgrade path if this ever moves to
  Blaze. Do not add logic there expecting it to run. It still typechecks and is unit-tested.
- **Push notifications do not work.** `notifications/` on Android is wired up, but sending FCM
  requires server credentials that don't exist here.
- **iOS is broken on Spark**, worse than `README.md` implies. `GroupsViewModel.swift` still
  calls `httpsCallable("createGroup")`/`"joinGroup"`/`"leaveGroup"`, which don't exist, so
  groups can't be created or joined on iOS at all. It also has never been compiled — this is a
  Windows machine with no Xcode. iOS is deprioritized; don't assume Swift changes are verifiable.
- Some integrity is unenforceable without a server and is accepted deliberately: leaderboard
  counts are self-reported, period badges are self-awardable, and a user can corrupt their own
  `streak`. `backend/docs/SCHEMA.md` is honest about each — read it before "fixing" one.

`backend/docs/SCHEMA.md` is the **authoritative data contract**. Update it in the same commit as
`firestore.rules`, the Kotlin models, and the Swift models.

## Commands

Android (`android/PeeTracker/`) — needs JDK 17 + Android SDK, both installed locally:

```bash
./gradlew assembleDebug testDebugUnitTest
```

Backend (`backend/`) — rules tests run against the Firestore emulator, no cloud access needed:

```bash
npm run test:rules        # emulators:exec + jest, currently 46 tests
npm run emulators         # interactive emulator
./node_modules/.bin/firebase deploy --only firestore --project streamline-ddf51
```

The `firebase` CLI is **local to `backend/node_modules`**, not on PATH.

Functions typecheck (still worth keeping green even though it's dead code):

```bash
cd backend/functions && ../node_modules/.bin/tsc --noEmit -p tsconfig.json
```

## Rules changes

`firestore.rules` is the security boundary. Every change needs a matching test in
`backend/test/firestore.rules.test.ts` and a `SCHEMA.md` update, then a deploy — editing the
file alone changes nothing in production.

Two constraints that are easy to break by accident:

- **Join and leave must stay batched.** The `memberCount` rule requires the requester's own
  membership doc to cross the matching edge in the *same write* (`+1` = `!exists` before and
  `existsAfter`; `-1` = the reverse). `GroupRepository.joinGroup`/`leaveGroup` already commit the
  count change and the membership write in one batch. Splitting them will start failing.
- **`logs.createdAt` must be `serverTimestamp()`.** Rules require `createdAt == request.time` on
  create, because the 5-minute edit window is measured from that field.

## Local config (gitignored, not in the repo)

- `android/PeeTracker/app/google-services.json` — real Firebase config
- `android/PeeTracker/local.properties` — SDK path

## Conventions

Source is LF; git converts on checkout. Comments in this repo explain *why* a constraint exists,
often with the attack or failure mode spelled out — match that when touching rules or
security-relevant code rather than restating what the line already does.
