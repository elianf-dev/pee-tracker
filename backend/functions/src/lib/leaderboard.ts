import * as admin from "firebase-admin";
import { paths } from "./firestorePaths";
import { LeaderboardDoc, LeaderboardEntry, LogDoc } from "../types/schema";

// Best-effort, non-atomic: used only to decide whether to fire a "lead change" push
// notification, not for the leaderboard's actual counts (those are updated transactionally
// in applyLogDelta below). A rare race under simultaneous logs might miss/duplicate a
// notification — acceptable for a fun, non-critical feature.
export function topEntryUid(entries: Record<string, LeaderboardEntry> | undefined): string | null {
  if (!entries) return null;
  let best: { uid: string; count: number } | null = null;
  for (const [uid, entry] of Object.entries(entries)) {
    if (!best || entry.count > best.count) {
      best = { uid, count: entry.count };
    }
  }
  return best?.uid ?? null;
}

type LogForLeaderboard = Pick<
  LogDoc,
  "uid" | "displayName" | "durationSeconds" | "dateKeyLocal" | "weekKeyLocal"
>;

export async function applyLogDelta(
  groupId: string,
  log: LogForLeaderboard,
  countDelta: 1 | -1
): Promise<void> {
  const db = admin.firestore();
  const durationDelta = countDelta * log.durationSeconds;

  await Promise.all([
    applyToDoc(db, paths.leaderboardDaily(groupId, log.dateKeyLocal), log, countDelta, durationDelta),
    applyToDoc(db, paths.leaderboardWeekly(groupId, log.weekKeyLocal), log, countDelta, durationDelta),
  ]);
}

async function applyToDoc(
  db: FirebaseFirestore.Firestore,
  path: string,
  log: Pick<LogForLeaderboard, "uid" | "displayName">,
  countDelta: number,
  durationDelta: number
): Promise<void> {
  const ref = db.doc(path);
  await db.runTransaction(async (txn) => {
    const snap = await txn.get(ref);
    const now = admin.firestore.FieldValue.serverTimestamp();

    if (!snap.exists) {
      const doc: LeaderboardDoc = {
        entries: {
          [log.uid]: {
            count: Math.max(countDelta, 0),
            totalDurationSeconds: Math.max(durationDelta, 0),
            displayName: log.displayName,
          },
        },
        updatedAt: now,
      };
      txn.set(ref, doc);
      return;
    }

    const data = snap.data() as LeaderboardDoc;
    const existing = data.entries?.[log.uid];
    txn.update(ref, {
      [`entries.${log.uid}`]: {
        count: Math.max((existing?.count ?? 0) + countDelta, 0),
        totalDurationSeconds: Math.max((existing?.totalDurationSeconds ?? 0) + durationDelta, 0),
        displayName: log.displayName,
      },
      updatedAt: now,
    });
  });
}
