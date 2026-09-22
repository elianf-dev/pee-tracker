import { BadgeType, LeaderboardEntry } from "../types/schema";

export interface BadgeAward {
  type: BadgeType;
  awardedToUid: string;
  meta: Record<string, unknown>;
}

export const BADGE_COPY: Record<BadgeType, { title: string; description: string; emoji: string }> = {
  camel_of_day: {
    title: "Camel of the Day",
    description: "Fewest trips logged today among active members.",
    emoji: "🐫",
  },
  camel_of_week: {
    title: "Camel of the Week",
    description: "Fewest trips logged this week among active members.",
    emoji: "🐫",
  },
  most_regular: {
    title: "Most Regular",
    description: "Logged every day this week with the most consistent pace.",
    emoji: "⏰",
  },
  streak_7: {
    title: "Week Streak",
    description: "7 days in a row logging at least one trip.",
    emoji: "🔥",
  },
  streak_30: {
    title: "Month Streak",
    description: "30 days in a row logging at least one trip.",
    emoji: "🏆",
  },
};

function stddev(values: number[]): number {
  if (values.length === 0) return Infinity;
  const mean = values.reduce((a, b) => a + b, 0) / values.length;
  const variance = values.reduce((a, b) => a + (b - mean) ** 2, 0) / values.length;
  return Math.sqrt(variance);
}

export function evaluateDailyBadges(entries: Record<string, LeaderboardEntry>): BadgeAward[] {
  const active = Object.entries(entries).filter(([, e]) => e.count > 0);
  if (active.length === 0) return [];

  const [camelUid] = active.reduce((min, curr) => (curr[1].count < min[1].count ? curr : min));
  return [{ type: "camel_of_day", awardedToUid: camelUid, meta: { count: entries[camelUid].count } }];
}

export function evaluateWeeklyBadges(
  weeklyEntries: Record<string, LeaderboardEntry>,
  dailyEntriesByDate: Record<string, Record<string, LeaderboardEntry>>
): BadgeAward[] {
  const awards: BadgeAward[] = [];

  const activeWeekly = Object.entries(weeklyEntries).filter(([, e]) => e.count > 0);
  if (activeWeekly.length > 0) {
    const [camelUid] = activeWeekly.reduce((min, curr) => (curr[1].count < min[1].count ? curr : min));
    awards.push({
      type: "camel_of_week",
      awardedToUid: camelUid,
      meta: { count: weeklyEntries[camelUid].count },
    });
  }

  const dateKeys = Object.keys(dailyEntriesByDate);
  const candidateUids = Object.keys(weeklyEntries).filter((uid) =>
    dateKeys.every((dateKey) => (dailyEntriesByDate[dateKey][uid]?.count ?? 0) > 0)
  );

  if (candidateUids.length > 0) {
    let mostRegularUid: string | null = null;
    let lowestStddev = Infinity;
    for (const uid of candidateUids) {
      const dailyCounts = dateKeys.map((dateKey) => dailyEntriesByDate[dateKey][uid]?.count ?? 0);
      const sd = stddev(dailyCounts);
      if (sd < lowestStddev) {
        lowestStddev = sd;
        mostRegularUid = uid;
      }
    }
    if (mostRegularUid) {
      awards.push({
        type: "most_regular",
        awardedToUid: mostRegularUid,
        meta: { stddev: lowestStddev },
      });
    }
  }

  return awards;
}
