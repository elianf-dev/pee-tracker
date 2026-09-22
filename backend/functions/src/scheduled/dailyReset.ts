import * as admin from "firebase-admin";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { paths } from "../lib/firestorePaths";
import { formatDateKeyUTC } from "../lib/dateKeys";
import { evaluateDailyBadges } from "../lib/badges";
import { BadgeDoc, LeaderboardDoc } from "../types/schema";

// v1 simplification: runs on a fixed UTC day boundary rather than each group's local timezone
// (see SCHEMA.md).
export const dailyReset = onSchedule(
  { schedule: "0 0 * * *", timeZone: "UTC" },
  async () => {
    const db = admin.firestore();
    const yesterday = new Date();
    yesterday.setUTCDate(yesterday.getUTCDate() - 1);
    const dateKey = formatDateKeyUTC(yesterday);
    const now = admin.firestore.FieldValue.serverTimestamp();

    const groupsSnap = await db.collection("groups").get();

    await Promise.all(
      groupsSnap.docs.map(async (groupDoc) => {
        const groupId = groupDoc.id;
        const ref = db.doc(paths.leaderboardDaily(groupId, dateKey));
        const snap = await ref.get();
        if (!snap.exists) return;
        const data = snap.data() as LeaderboardDoc;
        if (data.finalized) return;

        await ref.update({ finalized: true, finalizedAt: now });

        const awards = evaluateDailyBadges(data.entries);
        await Promise.all(
          awards.map((award) => {
            const badgeId = `${dateKey}_${award.type}`;
            const badge: BadgeDoc = {
              type: award.type,
              periodKey: dateKey,
              awardedToUid: award.awardedToUid,
              awardedAt: now,
              meta: award.meta,
            };
            return db.doc(paths.badge(groupId, badgeId)).set(badge);
          })
        );
      })
    );
  }
);
