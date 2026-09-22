import { computeStreakUpdate, isStreakBroken, streakMilestoneBadge } from "../src/lib/streaks";

describe("computeStreakUpdate", () => {
  it("starts a streak at 1 on the very first log", () => {
    const result = computeStreakUpdate({ current: 0, longest: 0, lastLogDateKey: "" }, "2026-09-07");
    expect(result).toEqual({ current: 1, longest: 1, lastLogDateKey: "2026-09-07" });
  });

  it("returns the same object unchanged for a second log on the same day", () => {
    const current = { current: 3, longest: 5, lastLogDateKey: "2026-09-07" };
    const result = computeStreakUpdate(current, "2026-09-07");
    expect(result).toBe(current);
  });

  it("increments on a consecutive day and raises longest if needed", () => {
    const result = computeStreakUpdate({ current: 3, longest: 3, lastLogDateKey: "2026-09-07" }, "2026-09-08");
    expect(result).toEqual({ current: 4, longest: 4, lastLogDateKey: "2026-09-08" });
  });

  it("does not raise longest below its current max", () => {
    const result = computeStreakUpdate({ current: 3, longest: 10, lastLogDateKey: "2026-09-07" }, "2026-09-08");
    expect(result).toEqual({ current: 4, longest: 10, lastLogDateKey: "2026-09-08" });
  });

  it("resets to 1 after a gap of more than one day", () => {
    const result = computeStreakUpdate({ current: 5, longest: 5, lastLogDateKey: "2026-09-01" }, "2026-09-07");
    expect(result).toEqual({ current: 1, longest: 5, lastLogDateKey: "2026-09-07" });
  });
});

describe("streakMilestoneBadge", () => {
  it("returns streak_7 at exactly 7", () => {
    expect(streakMilestoneBadge(7)).toBe("streak_7");
  });
  it("returns streak_30 at exactly 30", () => {
    expect(streakMilestoneBadge(30)).toBe("streak_30");
  });
  it("returns null for any other value", () => {
    expect(streakMilestoneBadge(6)).toBeNull();
    expect(streakMilestoneBadge(8)).toBeNull();
    expect(streakMilestoneBadge(29)).toBeNull();
    expect(streakMilestoneBadge(31)).toBeNull();
  });
});

describe("isStreakBroken", () => {
  it("is false when there is no prior log", () => {
    expect(isStreakBroken("", "2026-09-07")).toBe(false);
  });
  it("is false when the last log was today or yesterday", () => {
    expect(isStreakBroken("2026-09-07", "2026-09-07")).toBe(false);
    expect(isStreakBroken("2026-09-06", "2026-09-07")).toBe(false);
  });
  it("is true when at least one full day was missed", () => {
    expect(isStreakBroken("2026-09-05", "2026-09-07")).toBe(true);
  });
});
