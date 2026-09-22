import { datesInIsoWeek, formatDateKeyUTC, formatWeekKeyUTC } from "../src/lib/dateKeys";

describe("formatDateKeyUTC / formatWeekKeyUTC", () => {
  it("formats a date key as YYYY-MM-DD", () => {
    expect(formatDateKeyUTC(new Date(Date.UTC(2026, 8, 7)))).toBe("2026-09-07");
  });

  it("formats an ISO week key", () => {
    // 2026-09-07 is a Monday; confirm it round-trips through datesInIsoWeek below too.
    expect(formatWeekKeyUTC(new Date(Date.UTC(2026, 8, 7)))).toMatch(/^2026-W\d{2}$/);
  });
});

describe("datesInIsoWeek", () => {
  it("is the exact inverse of formatWeekKeyUTC for every day it produces", () => {
    const monday = new Date(Date.UTC(2026, 8, 7));
    const weekKey = formatWeekKeyUTC(monday);
    const dates = datesInIsoWeek(weekKey);

    expect(dates).toHaveLength(7);
    for (const d of dates) {
      const [y, m, day] = d.split("-").map(Number);
      expect(formatWeekKeyUTC(new Date(Date.UTC(y, m - 1, day)))).toBe(weekKey);
    }
  });

  it("returns consecutive calendar days", () => {
    const dates = datesInIsoWeek("2026-W37");
    for (let i = 1; i < dates.length; i++) {
      const prev = Date.parse(`${dates[i - 1]}T00:00:00Z`);
      const curr = Date.parse(`${dates[i]}T00:00:00Z`);
      expect(curr - prev).toBe(24 * 3600 * 1000);
    }
  });

  it("returns an empty array for a malformed key", () => {
    expect(datesInIsoWeek("not-a-week")).toEqual([]);
  });
});
