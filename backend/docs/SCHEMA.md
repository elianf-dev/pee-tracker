# Firestore Schema & Contract (Phase 4 — Spark Mode)

This is the single source of truth for data shapes shared between `ios/` and `android/`.
Update this file in the same PR as `firestore.rules`, `functions/src/types/schema.ts`, the Swift
models, and the Kotlin models. Full multi-phase schema is documented here incrementally —
this revision covers Phase 1 (Auth + basic logging), Phase 2 (invite-code groups), Phase 3
(leaderboards + resets), and Phase 4 (streaks, badges, notifications).

## Spark Mode: no Cloud Functions, no billing account

The deployed backend for this project (`streamline-ddf51`) runs on Firebase's free **Spark**
plan, which does not support Cloud Functions at all (not a quota limit — the feature is entirely
unavailable without upgrading to the pay-as-you-go Blaze plan, which requires a credit card).
By choice, this project stays on Spark, which means **everything that used to be a Cloud
Function now happens directly on the client**, governed by `firestore.rules` instead of trusted
server code. The `functions/` directory and its Cloud Functions still exist in this repo and are
fully unit-tested (see `functions/test/`), but are **not deployed** — `firebase.json` only
deploys `firestore.rules`/`firestore.indexes.json`. If this project ever upgrades to Blaze, the
functions can be deployed as-is and the client-side logic below can be deleted in favor of them
(the two are designed to produce the same Firestore end-state).

**What this costs, concretely:**
- **Push notifications are impossible on Spark**, full stop — sending an FCM message requires
  server credentials that only exist in a trusted backend. The client-side FCM token
  registration/topic-subscribe code is left in place (harmless, ready to work instantly if this
  project ever moves to Blaze) but nothing will ever publish to those topics today. The in-app
  badge celebration overlay still works while the app is open (it's a live Firestore listener,
  not a push), it just won't fire in the background.
- **Weaker write integrity.** A Cloud Function is the only way to make a write "trusted" (the
  server computes it, the client can't lie about it). Without one, invite-code creation, group
  membership, leaderboard counts, streaks, and badge awards are all **written directly by
  clients** and only checked by `firestore.rules`' declarative, non-Turing-complete validation.
  Firestore rules can check shape, ownership, and simple arithmetic invariants, but cannot fully
  verify something like "this really is the minimum count in the group" the way server code
  could. For a friends-only fun app this is an accepted trade-off, not an oversight — documented
  per-collection below wherever it applies.
- **No server cron**, so `dailyReset`/`weeklyReset`/`streakSweep`'s jobs (finalizing a period,
  awarding period badges, sweeping broken streaks) are replaced by **opportunistic client-side
  computation**: whichever device happens to open the Leaderboard/Streaks screen after a period
  has rolled over computes and writes the result itself (idempotent, "first writer wins" via a
  Firestore rule that only allows the write if the target doc doesn't already exist). If nobody
  opens the app in a given window, that window's badge is simply never awarded — acceptable for
  a casual app, not something a real cron job would miss.

## Phase 2 migration note

Phase 1 used a single hardcoded `TEST_GROUP_ID = "test-group-phase1"` group since there was no
group creation flow yet. Phase 2 replaces that entirely: logs are now written under the user's
real, joined group. `TEST_GROUP_ID` no longer exists anywhere in the codebase — do not reintroduce
it. A user with no groups yet (`currentGroupIds == []`) is routed to onboarding
(create-or-join) before they can reach the logging screen.

## Collections

### `users/{uid}`

| Field | Type | Notes |
|---|---|---|
| `displayName` | string | client-writable |
| `photoURL` | string \| null | client-writable |
| `authProviders` | string[] | e.g. `["apple"]`, `["google"]`, `["password"]` |
| `currentGroupIds` | string[] | **Spark mode: client-writable**, owner-only. Updated directly by the client during create/join/leave group (no `onUserCreate`/callables running). Trust model: a user could in principle add an arbitrary groupId here, but that grants no actual access — reading a group still requires a real `members/{uid}` doc, so this is harmless self-corruption at worst. |
| `timezone` | string | IANA tz name, client-writable, e.g. `"America/Chicago"` |
| `streak` | map | `{ current: number, longest: number, lastLogDateKey: string }` — **Spark mode: client-writable**, owner-only. Computed client-side (see `computeStreakUpdate` ported into both apps) every time that device submits a log, and swept client-side on app launch if a day was missed. Trust model: a user could hand-edit their own streak; there is no server to catch it. |
| `fcmTokens` | map | `deviceId -> FCM registration token` — client-writable; harmless to keep even though nothing sends to it in Spark mode (see the Spark Mode section above). |
| `createdAt` | timestamp | client-set `serverTimestamp()`, written once by the client itself right after sign-up (no `onUserCreate` trigger in Spark mode) |

**Spark mode**: created directly by the client right after a successful sign-up (`setDoc` with
`currentGroupIds: []`, `streak: {0,0,""}`, `fcmTokens: {}`), guarded by a rule that only allows
a user to create their own doc with those exact defaults. If the client crashes between auth and
this write, the user simply has no `users/{uid}` doc yet — both apps check for this on launch and
retry the creation rather than assuming it always exists.

### `groups/{groupId}`

| Field | Type | Notes |
|---|---|---|
| `name` | string | set at creation, 1-40 chars, not editable in Phase 2 |
| `inviteCode` | string | denormalized copy of the code, e.g. `"PEE-4X9KQ7MT"`; canonical lookup is `inviteCodes/{code}` |
| `ownerUid` | string | the creator |
| `memberCount` | number | maintained by `createGroup`/`joinGroup`/`leaveGroup` |
| `settings` | map | `{ dailyResetHour: number, weeklyResetWeekday: number }` — unused (no cron in Spark mode), defaults `{0, 0}` |
| `createdAt` | timestamp | client-set `serverTimestamp()` |

**Spark mode**: the creating client writes this doc directly (`ownerUid` must equal their own
uid, `memberCount` must be `1`). After creation, `memberCount` may only be updated by exactly
`+1`/`-1` per write (enforced in rules) — join increments, leave decrements. No other field is
client-updatable after creation (`name`/`settings`/`ownerUid` are create-once).

### `groups/{groupId}/members/{uid}`

Subcollection; document id **is** the member's `uid` so membership can be checked with a single
`exists()` read instead of a query.

| Field | Type | Notes |
|---|---|---|
| `displayName` | string | denormalized snapshot at join time |
| `photoURL` | string \| null | denormalized snapshot at join time |
| `joinedAt` | timestamp | client-set `serverTimestamp()` |
| `role` | string | `"owner" \| "member"` |

**Spark mode**: a user may only create their *own* membership doc (`{uid} == request.auth.uid`),
with `role` matching whether they're also the group's `ownerUid` (owner) or not (member). The
only realistic way to learn a `groupId` to join is via a real invite code lookup or already being
a member — `groupId`s are unguessable Firestore auto-IDs, so this is acceptably secure without a
server gatekeeping the join. Deletable by the member themself (leaving the group).

### `groups/{groupId}/logs/{logId}`

Unchanged shape from Phase 1, now scoped to a real group instead of the hardcoded test group.

| Field | Type | Notes |
|---|---|---|
| `uid` | string | author, must equal `request.auth.uid` |
| `displayName` | string | denormalized snapshot of the author's name at write time |
| `timestamp` | timestamp | client-captured stopwatch **start** time |
| `durationSeconds` | number | client-measured, integer seconds |
| `volume` | string | one of `"low" \| "medium" \| "high"` |
| `dateKeyLocal` | string | `"YYYY-MM-DD"`, computed client-side from device tz at write time |
| `weekKeyLocal` | string | ISO week, `"YYYY-Www"`, computed client-side at write time |
| `createdAt` | timestamp | `serverTimestamp()`, used for ordering |

Create requires group membership (checked via `groups/{groupId}/members/{uid}` existing).
Edit/delete: author-only, within 5 minutes of `createdAt` (enforced in rules).

### `inviteCodes/{code}`

Top-level lookup collection, `code -> groupId`.

| Field | Type | Notes |
|---|---|---|
| `groupId` | string | |
| `createdAt` | timestamp | |

Code format: `"PEE-XXXXXXXX"` where `XXXXXXXX` is 8 random uppercase chars from the charset
`ABCDEFGHJKMNPQRSTUVWXYZ23456789` (excludes `0/O/1/I/L` to avoid visual ambiguity when a friend
reads a code aloud or off a screen). Generated client-side (ported from
`functions/src/lib/inviteCode.ts`'s logic) with a collision retry loop: `get()` the candidate
code doc, regenerate if it already exists. Both generators draw from a CSPRNG (`SecureRandom` on
Android, `crypto.randomInt` in the functions port), because a predictable code is as weak as a
short one.

**Spark mode rules**: `get` (reading one exact code you already know) is allowed for any signed-in
user; `list` (querying/browsing the whole collection) is denied.

⚠️ **Denying `list` is not by itself a defence against brute force**, and an earlier revision of
this doc wrongly claimed it was. Any signed-in user may `get` any code they care to name, one at
a time, and nothing in Firestore rules can rate-limit that. At the original length of 4 the code
space was 31⁴ ≈ 923k, so walking it was entirely practical — and since a code yields a `groupId`,
and `members/{uid}` create asks only that you know the `groupId` and use your own uid, that walk
ended in joining a stranger's group. It also burns the project's Spark read quota, which is the
owner's to pay. The length is now 8 (31⁸ ≈ 8.5e11), which makes the walk infeasible and restores
the "you must have been given the string" assumption the join gate rests on. `create` is allowed if the code doesn't already exist yet (collision-safe) — a client
creates this doc as the second step of `createGroup`, right after the `groups/{groupId}` doc it
points to already exists.

### `groups/{groupId}/leaderboardDaily/{dateKey}` and `leaderboardWeekly/{weekKey}`

One aggregate doc per period (`dateKey` = `"YYYY-MM-DD"`, `weekKey` = ISO week `"YYYY-Www"` —
same format `logs.dateKeyLocal`/`weekKeyLocal` already use).

| Field | Type | Notes |
|---|---|---|
| `entries` | map | `uid -> { count: number, totalDurationSeconds: number, displayName: string }` |
| `updatedAt` | timestamp | client-set `serverTimestamp()`, bumped on every increment |
| `finalized` | boolean | set `true` by whichever client first notices the period has rolled over (opportunistic, see Spark Mode section); absent/`false` while the period is still live |
| `finalizedAt` | timestamp | set alongside `finalized: true` |

Decision: aggregated counters, not a live query over `logs` — Firestore has no native
`GROUP BY`, and a live count-by-user query would cost a read per matching log document every time
the leaderboard renders. A single doc read gives the whole group's ranking. Trade-off: the
`entries` map has a practical ceiling of a few hundred members per group before
document-size/write-contention becomes a concern — acceptable for a friends-group app.

**Spark mode**: the device that just logged a trip writes its own increment directly, in a client
transaction, to both the daily and weekly doc (creating either if this is the first log of that
period), immediately after writing the log itself. Rules constrain an update/create to only
touching the writer's own key under `entries` — nobody can edit another member's count. This is
still not fully tamper-proof (a client could write an inflated `count` for *itself*), which is
the accepted trust trade-off described in the Spark Mode section above.

### `groups/{groupId}/badges/{badgeId}`

Awarded badges. `badgeId` is `${periodKey}_${badgeType}` for period badges (`camel_of_day`,
`camel_of_week`, `most_regular`) or `${uid}_${badgeType}` for personal streak badges
(`streak_7`, `streak_30`) — so re-earning a streak badge overwrites the same doc rather than
creating duplicates. Live-listened for the celebratory "you got a badge" toast.

| Field | Type | Notes |
|---|---|---|
| `type` | string | one of `camel_of_day \| camel_of_week \| most_regular \| streak_7 \| streak_30` |
| `periodKey` | string | the `dateKey`/`weekKey` the badge was earned in (or the log's `dateKeyLocal` for streak badges) |
| `awardedToUid` | string | |
| `awardedAt` | timestamp | |
| `meta` | map | badge-specific extra data, e.g. `{ count: 2 }` or `{ streakCurrent: 7 }` — display-only, not load-bearing |

Badge copy (title/description/emoji) is intentionally **not** stored per-doc — both native apps
and `functions/src/lib/badges.ts` keep a static `type -> copy` table so wording can be tuned
without a data migration:

| Type | Title | Emoji |
|---|---|---|
| `camel_of_day` | Camel of the Day | 🐫 |
| `camel_of_week` | Camel of the Week | 🐫 |
| `most_regular` | Most Regular | ⏰ |
| `streak_7` | Week Streak | 🔥 |
| `streak_30` | Month Streak | 🏆 |

**Spark mode write rules**: `streak_7`/`streak_30` may only be created by the user they're
awarded to (`awardedToUid == request.auth.uid`) — fully self-contained, no other member's data
needed, so this is as trustworthy as any client-side write can be. `camel_of_day`/`camel_of_week`/
`most_regular` may be created by *any* group member, using a `!exists()` check on the badge doc
so it's a "first client to notice computes and writes it" pattern rather than a race — ported
`evaluateDailyBadges`/`evaluateWeeklyBadges` logic runs on-device using the already-readable
leaderboard docs (weekly also reads that week's 7 `leaderboardDaily` docs). This runs
opportunistically whenever the Leaderboard/Streaks screen loads and notices the previous
day/week has no badge yet — there is no cron, so a period nobody opens the app during simply
never gets a period badge, which is an accepted gap for a casual app.

## Push notifications

**Not available in Spark mode — see the "Spark Mode" section at the top of this document.**
Everything below describes the Blaze/Cloud-Functions behavior, preserved here undeployed:

FCM **topics per group** (`group_${groupId}`) for broadcast events — the client subscribes
right after `createGroup`/`joinGroup` succeed and unsubscribes on `leaveGroup`:
- **New leader**: sent from `onLogCreated` when a log causes a new #1 on *today's* leaderboard
  (weekly lead changes are not announced — daily is noisy enough for the fun factor without a
  second stream).
- **New member**: sent from the `joinGroup` callable after a genuinely new member joins.
- **Weekly results**: sent from `weeklyReset` after badges are awarded, summarizing them.

**Personal** notifications go directly to a user's own `fcmTokens`, not a topic:
- **Streak milestone**: sent from `onLogCreated` the moment `streak.current` crosses 7 or 30.

All notification sends are best-effort — a failure never blocks or rolls back the Firestore
write that triggered it (see `functions/src/lib/notify.ts`).

## Volume enum

Exactly three lowercase string values, used verbatim in Firestore and in both native enums:

```
low | medium | high
```

Swift: `enum Volume: String, Codable { case low, medium, high }`
Kotlin: `enum class Volume(val value: String) { LOW("low"), MEDIUM("medium"), HIGH("high") }`

## Cloud Functions (not deployed in Spark mode — kept as a Blaze upgrade path)

The functions below exist in `functions/` and are unit-tested, but `firebase.json` does not
deploy them and this section describes what they'd do if this project ever moves to Blaze. The
client-side ports of this same logic (see the "Spark mode" notes throughout this document) are
what's actually running today.

| Function | Type | Path | Responsibility |
|---|---|---|---|
| `onUserCreate` | Auth `onCreate` trigger | `functions/src/triggers/onUserCreate.ts` | Creates `users/{uid}` with `currentGroupIds: []` and other defaults |
| `createGroup` | callable | `functions/src/callable/createGroup.ts` | Generates a unique invite code, creates `groups/{groupId}` + owner `members/{uid}` doc, adds `groupId` to `users/{uid}.currentGroupIds` |
| `joinGroup` | callable | `functions/src/callable/joinGroup.ts` | Looks up `inviteCodes/{code}`, adds `members/{uid}`, increments `memberCount`, adds `groupId` to `users/{uid}.currentGroupIds` |
| `leaveGroup` | callable | `functions/src/callable/leaveGroup.ts` | Removes `members/{uid}`, decrements `memberCount`, removes `groupId` from `users/{uid}.currentGroupIds` |
| `onLogCreated` | Firestore `onCreate` trigger | `functions/src/triggers/onLogCreated.ts` | Transactionally increments the log author's entry in that group's `leaderboardDaily/{dateKeyLocal}` and `leaderboardWeekly/{weekKeyLocal}` docs (creating either doc/entry if absent); sends a "new leader" topic notification on a daily lead change; updates `users/{uid}.streak` and awards `streak_7`/`streak_30` the moment they're crossed |
| `onLogDeleted` | Firestore `onDelete` trigger | `functions/src/triggers/onLogDeleted.ts` | Transactionally decrements the same two aggregate entries (handles mis-taps/undo within the 5-minute edit window) |
| `dailyReset` | scheduled (`onSchedule`, daily at UTC midnight) | `functions/src/scheduled/dailyReset.ts` | For every group, finalizes yesterday's `leaderboardDaily/{dateKey}` doc and awards `camel_of_day`. **v1 simplification**: fixed UTC day boundary, not each group's local timezone. |
| `weeklyReset` | scheduled (`onSchedule`, weekly, Monday 00:00 UTC) | `functions/src/scheduled/weeklyReset.ts` | Finalizes last week's `leaderboardWeekly/{weekKey}` doc, awards `camel_of_week`/`most_regular` (using the week's 7 `leaderboardDaily` docs), sends the "weekly results" topic notification. |
| `streakSweep` | scheduled (`onSchedule`, daily at 00:05 UTC) | `functions/src/scheduled/streakSweep.ts` | Zeroes `streak.current` for any user whose `lastLogDateKey` is more than one day stale. Runs just after `dailyReset` so a genuinely broken streak is caught quickly without racing it. |

### Callable request/response shapes (Blaze-only reference; not deployed)

```ts
createGroup(data: { name: string })
  -> { groupId: string, inviteCode: string }

joinGroup(data: { code: string })
  -> { groupId: string, name: string }

leaveGroup(data: { groupId: string })
  -> { success: true }
```

All three throw a `functions.https.HttpsError` (`"unauthenticated"`, `"invalid-argument"`,
`"not-found"`, etc.) on failure. The Spark-mode client-side ports of `createGroup`/`joinGroup`/
`leaveGroup` (direct Firestore writes, not callables) use the same request/response shapes as
plain function signatures so the UI code barely changed.

## Firestore Security Rules (Spark mode — what's actually deployed)

See `backend/firestore.rules`. This is the real, live rule set (no Cloud Functions/Admin SDK
backing any of it):

- `users/{uid}`: owner-only read. `create` allowed only for your own uid with the exact defaults
  (`currentGroupIds: []`, `streak: {current:0, longest:0, lastLogDateKey:""}`, `fcmTokens: {}`).
  `update` allowed for your own uid, any fields — `streak`/`currentGroupIds` are no longer
  blocked, since there's no server to manage them (see the Spark Mode trust-model note above).
- `groups/{groupId}`: `get` allowed for any signed-in user (you must already know the exact
  groupId, e.g. from an invite-code lookup — same trust argument as `inviteCodes` below; also
  required so `joinGroup` can read `memberCount`/`name` before the joiner has a `members/{uid}`
  doc yet). `list` denied (no browsing/enumeration). `create` allowed if `ownerUid ==
  request.auth.uid` and `memberCount == 1`. `update`
  allowed only if the only changed field is `memberCount`, and only by exactly `+1` or `-1` (join
  or leave) — `name`/`settings`/`ownerUid` are immutable after creation. Each direction also
  requires the requester's own membership doc to cross the matching edge in the same write
  (`+1` = `!exists` before and `existsAfter`; `-1` = the reverse), so a member can't replay a
  bare increment or decrement and drift the count away from the real roster.
- `groups/{groupId}/members/{memberId}`: read allowed to any existing member of that group.
  `create` allowed only for `memberId == request.auth.uid`, with `role` matching whether they're
  that group's `ownerUid`. `delete` allowed only by the member themself (leaving).
- `groups/{groupId}/logs/{logId}`: unchanged from Phase 3 — create requires
  `request.auth.uid == request.resource.data.uid` and group membership; read allowed to any
  member; update/delete restricted to the author within a 5-minute grace window.
- `groups/{groupId}/leaderboardDaily/{dateKey}` and `leaderboardWeekly/{weekKey}`: read allowed to
  any group member. `create`/`update` allowed to any group member, but constrained to only
  touching their *own* key under `entries` (checked via `request.resource.data.diff(resource.data)`
  / the created doc's `entries` map having exactly one key, their own uid).
- `groups/{groupId}/badges/{badgeId}`: read allowed to any group member. `create` allowed if
  either (a) `type` is `streak_7`/`streak_30` and `awardedToUid == request.auth.uid`, or (b)
  `type` is `camel_of_day`/`camel_of_week`/`most_regular`, `awardedToUid` is a real member of the
  group, and the doc doesn't already exist (first-writer-wins). No `update`/`delete` — badges are
  write-once.
- `inviteCodes/{code}`: `get` allowed for any signed-in user (you must already know the exact
  code string); `list` denied (no browsing/enumeration); `create` allowed if the code doesn't
  already exist yet.
- Everything else: denied by default (`match /{document=**} { allow read, write: if false; }`
  catch-all).
