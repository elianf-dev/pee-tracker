import * as admin from "firebase-admin";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { formatDateKeyUTC } from "../lib/dateKeys";
import { isStreakBroken } from "../lib/streaks";
import { UserDoc } from "../types/schema";

// v1 simplification: compares each user's lastLogDateKey (their own local calendar day)
// against today's UTC date, same class of approximation as the other scheduled jobs.
export const streakSweep = onSchedule(
  { schedule: "5 0 * * *", timeZone: "UTC" },
  async () => {
    const db = admin.firestore();
    const todayKey = formatDateKeyUTC(new Date());

    const usersSnap = await db.collection("users").where("streak.current", ">", 0).get();

    await Promise.all(
      usersSnap.docs.map(async (userDoc) => {
        const user = userDoc.data() as UserDoc;
        if (isStreakBroken(user.streak.lastLogDateKey, todayKey)) {
          await userDoc.ref.update({ "streak.current": 0 });
        }
      })
    );
  }
);
