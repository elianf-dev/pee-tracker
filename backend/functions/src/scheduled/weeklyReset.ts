import * as admin from "firebase-admin";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { paths } from "../lib/firestorePaths";
import { datesInIsoWeek, formatWeekKeyUTC } from "../lib/dateKeys";
import { BADGE_COPY, evaluateWeeklyBadges } from "../lib/badges";
import { sendToTopic } from "../lib/notify";
import { BadgeDoc, LeaderboardDoc, LeaderboardEntry } from "../types/schema";

// v1 simplification: runs on a fixed UTC week boundary rather than each group's local timezone
// (see SCHEMA.md).
export const weeklyReset = onSchedule(
  { schedule: "0 0 * * 1", timeZone: "UTC" },
  async () => {
    const db = admin.firestore();
    const lastWeek = new Date();
    lastWeek.setUTCDate(lastWeek.getUTCDate() - 7);
    const weekKey = formatWeekKeyUTC(lastWeek);
    const now = admin.firestore.FieldValue.serverTimestamp();

    const groupsSnap = await db.collection("groups").get();

    await Promise.all(
      groupsSnap.docs.map(async (groupDoc) => {
        const groupId = groupDoc.id;
        const ref = db.doc(paths.leaderboardWeekly(groupId, weekKey));
        const snap = await ref.get();
        if (!snap.exists) return;
        const data = snap.data() as LeaderboardDoc;
        if (data.finalized) return;

        await ref.update({ finalized: true, finalizedAt: now });

        const dateKeys = datesInIsoWeek(weekKey);
        const dailySnaps = await Promise.all(
          dateKeys.map((dateKey) => db.doc(paths.leaderboardDaily(groupId, dateKey)).get())
        );
        const dailyEntriesByDate: Record<string, Record<string, LeaderboardEntry>> = {};
        dateKeys.forEach((dateKey, i) => {
          const dailyData = dailySnaps[i].data() as LeaderboardDoc | undefined;
          dailyEntriesByDate[dateKey] = dailyData?.entries ?? {};
        });

        const awards = evaluateWeeklyBadges(data.entries, dailyEntriesByDate);
        await Promise.all(
          awards.map((award) => {
            const badgeId = `${weekKey}_${award.type}`;
            const badge: BadgeDoc = {
              type: award.type,
              periodKey: weekKey,
              awardedToUid: award.awardedToUid,
              awardedAt: now,
              meta: award.meta,
            };
            return db.doc(paths.badge(groupId, badgeId)).set(badge);
          })
        );

        if (awards.length > 0) {
          const summary = awards
            .map((a) => `${BADGE_COPY[a.type].emoji} ${BADGE_COPY[a.type].title}`)
            .join(", ");
          await sendToTopic(groupId, {
            title: "Weekly results are in! 🏆",
            body: summary,
          });
        }
      })
    );
  }
);
