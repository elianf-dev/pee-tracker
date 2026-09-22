import { evaluateDailyBadges, evaluateWeeklyBadges } from "../src/lib/badges";
import { LeaderboardEntry } from "../src/types/schema";

function entry(count: number, totalDurationSeconds = count * 30): LeaderboardEntry {
  return { count, totalDurationSeconds, displayName: "x" };
}

describe("evaluateDailyBadges", () => {
  it("awards camel_of_day to the lowest active count", () => {
    const awards = evaluateDailyBadges({
      alice: entry(5),
      bob: entry(2),
      carol: entry(3),
    });
    expect(awards).toEqual([{ type: "camel_of_day", awardedToUid: "bob", meta: { count: 2 } }]);
  });

  it("ignores members with zero logs", () => {
    const awards = evaluateDailyBadges({
      alice: entry(5),
      bob: entry(0),
    });
    expect(awards).toEqual([{ type: "camel_of_day", awardedToUid: "alice", meta: { count: 5 } }]);
  });

  it("returns no awards when nobody logged", () => {
    expect(evaluateDailyBadges({ alice: entry(0) })).toEqual([]);
    expect(evaluateDailyBadges({})).toEqual([]);
  });
});

describe("evaluateWeeklyBadges", () => {
  const dateKeys = ["2026-09-07", "2026-09-08", "2026-09-09", "2026-09-10", "2026-09-11", "2026-09-12", "2026-09-13"];

  it("awards camel_of_week to the lowest weekly count", () => {
    const awards = evaluateWeeklyBadges(
      { alice: entry(20), bob: entry(5) },
      Object.fromEntries(dateKeys.map((d) => [d, {}]))
    );
    expect(awards).toContainEqual({ type: "camel_of_week", awardedToUid: "bob", meta: { count: 5 } });
  });

  it("awards most_regular only to a user active every single day, picking the lowest variance", () => {
    const dailyEntriesByDate: Record<string, Record<string, LeaderboardEntry>> = {};
    for (const d of dateKeys) {
      dailyEntriesByDate[d] = {
        steady: entry(2), // logs exactly 2/day, every day -> stddev 0
        sporadic: entry(0),
      };
    }
    // sporadic skips most days but logs a burst on one day
    dailyEntriesByDate[dateKeys[0]].sporadic = entry(10);

    const awards = evaluateWeeklyBadges(
      { steady: entry(14), sporadic: entry(10) },
      dailyEntriesByDate
    );
    const mostRegular = awards.find((a) => a.type === "most_regular");
    expect(mostRegular?.awardedToUid).toBe("steady");
  });

  it("excludes most_regular candidates who missed any day", () => {
    const dailyEntriesByDate: Record<string, Record<string, LeaderboardEntry>> = {};
    dateKeys.forEach((d, i) => {
      dailyEntriesByDate[d] = i === 0 ? { alice: entry(1) } : { alice: entry(0) };
    });
    const awards = evaluateWeeklyBadges({ alice: entry(1) }, dailyEntriesByDate);
    expect(awards.find((a) => a.type === "most_regular")).toBeUndefined();
  });

  it("returns no awards for a fully empty week", () => {
    const dailyEntriesByDate = Object.fromEntries(dateKeys.map((d) => [d, {}]));
    expect(evaluateWeeklyBadges({}, dailyEntriesByDate)).toEqual([]);
  });
});
