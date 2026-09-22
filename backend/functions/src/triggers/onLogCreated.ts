import * as admin from "firebase-admin";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { applyLogDelta, topEntryUid } from "../lib/leaderboard";
import { computeStreakUpdate, streakMilestoneBadge } from "../lib/streaks";
import { BADGE_COPY } from "../lib/badges";
import { sendToTopic, sendToUser } from "../lib/notify";
import { paths } from "../lib/firestorePaths";
import { BadgeDoc, LeaderboardDoc, LogDoc, Streak, UserDoc } from "../types/schema";

export const onLogCreated = onDocumentCreated(
  "groups/{groupId}/logs/{logId}",
  async (event) => {
    const snap = event.data;
    if (!snap) return;
    const log = snap.data() as LogDoc;
    const groupId = event.params.groupId;
    const db = admin.firestore();

    const dailyRef = db.doc(paths.leaderboardDaily(groupId, log.dateKeyLocal));
    const beforeDailySnap = await dailyRef.get();
    const previousLeader = topEntryUid((beforeDailySnap.data() as LeaderboardDoc | undefined)?.entries);

    await applyLogDelta(groupId, log, 1);

    const afterDailySnap = await dailyRef.get();
    const newLeader = topEntryUid((afterDailySnap.data() as LeaderboardDoc | undefined)?.entries);
    if (newLeader === log.uid && newLeader !== previousLeader) {
      await sendToTopic(groupId, {
        title: "New leader! 🚽",
        body: `${log.displayName} just took the lead for today.`,
      });
    }

    await updateStreakAndAwardMilestone(groupId, log);
  }
);

async function updateStreakAndAwardMilestone(groupId: string, log: LogDoc): Promise<void> {
  const db = admin.firestore();
  const userRef = db.doc(paths.user(log.uid));

  const newStreak = await db.runTransaction(async (txn) => {
    const userSnap = await txn.get(userRef);
    if (!userSnap.exists) return null;
    const user = userSnap.data() as UserDoc;

    const updatedStreak: Streak = computeStreakUpdate(user.streak, log.dateKeyLocal);
    if (updatedStreak === user.streak) {
      return null; // same-day log, no change, nothing to award
    }
    txn.update(userRef, { streak: updatedStreak });

    const milestone = streakMilestoneBadge(updatedStreak.current);
    if (milestone) {
      const badgeId = `${log.uid}_${milestone}`;
      const badge: BadgeDoc = {
        type: milestone,
        periodKey: log.dateKeyLocal,
        awardedToUid: log.uid,
        awardedAt: admin.firestore.FieldValue.serverTimestamp(),
        meta: { streakCurrent: updatedStreak.current },
      };
      txn.set(db.doc(paths.badge(groupId, badgeId)), badge);
    }

    return { streak: updatedStreak, milestone };
  });

  if (newStreak?.milestone) {
    const copy = BADGE_COPY[newStreak.milestone];
    await sendToUser(log.uid, {
      title: `${copy.emoji} ${copy.title}!`,
      body: copy.description,
    });
  }
}
