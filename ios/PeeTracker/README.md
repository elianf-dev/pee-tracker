# PeeTracker (iOS)

> ## ⚠️ This target does not currently work
>
> The Swift sources cover all four phases — auth, groups, leaderboards, streaks and badges —
> but `Groups/GroupsViewModel.swift` still calls `httpsCallable("createGroup")`,
> `"joinGroup"` and `"leaveGroup"`. Those Cloud Functions are **not deployed and cannot be**:
> the Firebase project is on the free Spark plan. Android was ported to write Firestore
> directly; iOS never was. Groups therefore cannot be created or joined here at all, which
> makes most of the rest unreachable.
>
> Nothing in this tree has ever been compiled either — it was authored on Windows, with no
> Xcode. Treat the setup below as necessary but not sufficient: you'd also need to port the
> group/leaderboard/streak/badge writes to direct Firestore calls, mirroring
> `android/PeeTracker/app/src/main/kotlin/com/peetracker/app/data/remote/`, and match
> `backend/firestore.rules`. iOS is currently deprioritized.

Firebase Auth (email/password, Sign in with Apple, Sign in with Google) plus stopwatch-timed
logging, groups, leaderboards, streaks and badges — see `backend/docs/SCHEMA.md` for the data
contract and its "Spark Mode" section for what the client is responsible for without a server.

This project was authored on Windows, where Xcode doesn't run. There is
intentionally no `.xcodeproj` checked in — it's a generated, fragile bundle format
that shouldn't be hand-edited or hand-written. Instead the project is described
declaratively in `project.yml` for [XcodeGen](https://github.com/yonaskolb/XcodeGen),
which produces the `.xcodeproj` on demand.

## One-time setup (on a Mac)

1. Install XcodeGen: `brew install xcodegen`
2. Add your Firebase config file: place `GoogleService-Info.plist` (downloaded from
   the Firebase console) at `PeeTracker/PeeTracker/GoogleService-Info.plist`. It is
   deliberately not checked into this repo. `backend/.firebaserc` points at the real
   project, `streamline-ddf51`.
3. From `ios/PeeTracker/`, run:

   ```
   xcodegen generate
   ```

4. Open `PeeTracker.xcodeproj` in Xcode.
5. Drag `GoogleService-Info.plist` into the Xcode project navigator (under the
   `PeeTracker` group) if it isn't already picked up as a folder reference, and make
   sure it's added to the `PeeTracker` target.

## Manual configuration XcodeGen can't do for you

- **Sign in with Apple**: `project.yml` already requests the
  `com.apple.developer.applesignin` entitlement, but the corresponding capability
  must also be enabled on your App ID in the
  [Apple Developer portal](https://developer.apple.com/account/resources/identifiers/list)
  (and, if you use one, on your provisioning profile) before it will work on a
  device or in TestFlight. The simulator works without this once the entitlement
  is present, as long as you're signed into an Apple ID on the simulator.
- **Sign in with Google**: after step 2 above, open `GoogleService-Info.plist` and
  copy the `REVERSED_CLIENT_ID` value. In Xcode, add it as a URL scheme under
  Target > Info > URL Types (or add a `CFBundleURLTypes` entry to the `info.properties`
  block in `project.yml` and re-run `xcodegen generate`). Also add a `GIDClientID`
  key to Info.plist with the plist's `CLIENT_ID` value. Without this, the Google
  sign-in flow won't be able to hand control back to the app.
- **Bundle identifier**: `project.yml` sets `com.peetracker.app`. Change it to match
  whatever App ID you actually registered in the Apple Developer portal and in your
  Firebase iOS app config, then re-run `xcodegen generate`.

## Re-running XcodeGen

Any time `project.yml` changes, or a Swift file is added/removed/moved, re-run
`xcodegen generate` from `ios/PeeTracker/` to regenerate the `.xcodeproj`. It's safe
to run repeatedly — it's fully derived from `project.yml` plus what's on disk under
`PeeTracker/` and `PeeTrackerTests/`.

## Dependencies

Declared as Swift Package Manager dependencies in `project.yml`:

- `firebase-ios-sdk` (`from: 11.0.0`) — `FirebaseCore`, `FirebaseAuth`,
  `FirebaseFirestore`, `FirebaseMessaging`. Firestore's `Codable` support
  (`addDocument(from:)`, `@ServerTimestamp`) ships inside `FirebaseFirestore` itself
  as of SDK 11 — no separate `FirebaseFirestoreSwift` package needed.
- `GoogleSignIn-iOS` (`from: 8.0.0`) — `GoogleSignIn`.

Xcode/SwiftPM will resolve exact pinned versions on first `xcodegen generate` +
build; this repo doesn't (and can't, without Xcode/SwiftPM available) commit a
`Package.resolved`. If a newer major version of either SDK has shipped by the time
you build this, bump the `from:` versions in `project.yml` after checking each
SDK's release notes for breaking changes.

## Tests

`PeeTrackerTests/StopwatchViewModelTests.swift` uses Swift Testing (`@Test`/`#expect`),
not XCTest. Run with `xcodebuild test` or Xcode's Test navigator (⌘U).
