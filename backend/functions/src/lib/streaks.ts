import { Streak } from "../types/schema";

function daysBetween(earlierDateKey: string, laterDateKey: string): number {
  const earlier = Date.parse(`${earlierDateKey}T00:00:00Z`);
  const later = Date.parse(`${laterDateKey}T00:00:00Z`);
  return Math.round((later - earlier) / (24 * 3600 * 1000));
}

// dateKeyLocal is computed in each user's own device timezone (see SCHEMA.md), so comparing
// two keys as if they were UTC calendar days is an approximation — same class of v1
// simplification as the UTC-boundary reset jobs. Good enough for a fun streak counter.
export function computeStreakUpdate(current: Streak, newDateKey: string): Streak {
  if (newDateKey === current.lastLogDateKey) {
    return current; // another log on the same day — no streak change
  }

  const isConsecutive =
    current.lastLogDateKey !== "" && daysBetween(current.lastLogDateKey, newDateKey) === 1;
  const newCurrent = isConsecutive ? current.current + 1 : 1;

  return {
    current: newCurrent,
    longest: Math.max(current.longest, newCurrent),
    lastLogDateKey: newDateKey,
  };
}

export function streakMilestoneBadge(streakCurrent: number): "streak_7" | "streak_30" | null {
  if (streakCurrent === 30) return "streak_30";
  if (streakCurrent === 7) return "streak_7";
  return null;
}

export function isStreakBroken(lastLogDateKey: string, todayDateKey: string): boolean {
  if (lastLogDateKey === "") return false;
  const gap = daysBetween(lastLogDateKey, todayDateKey);
  return gap >= 2; // missed at least one full calendar day
}
