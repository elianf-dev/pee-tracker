export function formatDateKeyUTC(date: Date): string {
  const y = date.getUTCFullYear();
  const m = String(date.getUTCMonth() + 1).padStart(2, "0");
  const d = String(date.getUTCDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

// ISO-8601 week key ("YYYY-Www"), computed in UTC to match the client's dateKeyLocal/
// weekKeyLocal format without needing per-group timezone data (see SCHEMA.md's Phase 3
// "v1 simplification" note on dailyReset/weeklyReset using a fixed UTC boundary).
export function formatWeekKeyUTC(date: Date): string {
  const d = new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate()));
  const dayNum = (d.getUTCDay() + 6) % 7; // Mon=0..Sun=6
  d.setUTCDate(d.getUTCDate() - dayNum + 3); // Thursday of this ISO week

  const firstThursday = new Date(Date.UTC(d.getUTCFullYear(), 0, 4));
  const firstDayNum = (firstThursday.getUTCDay() + 6) % 7;
  firstThursday.setUTCDate(firstThursday.getUTCDate() - firstDayNum + 3);

  const weekNum = 1 + Math.round((d.getTime() - firstThursday.getTime()) / (7 * 24 * 3600 * 1000));
  return `${d.getUTCFullYear()}-W${String(weekNum).padStart(2, "0")}`;
}

// Inverse of formatWeekKeyUTC: given "YYYY-Www", returns the 7 UTC dateKeys (Mon..Sun) in
// that ISO week, so weeklyReset can pull each day's leaderboardDaily doc for badge evaluation.
export function datesInIsoWeek(weekKey: string): string[] {
  const match = /^(\d{4})-W(\d{2})$/.exec(weekKey);
  if (!match) return [];
  const isoYear = Number(match[1]);
  const isoWeek = Number(match[2]);

  const jan4 = new Date(Date.UTC(isoYear, 0, 4));
  const jan4DayNum = (jan4.getUTCDay() + 6) % 7;
  const week1Monday = new Date(jan4.getTime());
  week1Monday.setUTCDate(jan4.getUTCDate() - jan4DayNum);

  const monday = new Date(week1Monday.getTime());
  monday.setUTCDate(week1Monday.getUTCDate() + (isoWeek - 1) * 7);

  const dateKeys: string[] = [];
  for (let i = 0; i < 7; i++) {
    const d = new Date(monday.getTime());
    d.setUTCDate(monday.getUTCDate() + i);
    dateKeys.push(formatDateKeyUTC(d));
  }
  return dateKeys;
}
