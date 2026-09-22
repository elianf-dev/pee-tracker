package com.peetracker.app

import com.peetracker.app.data.model.Streak
import com.peetracker.app.data.remote.StreakLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreakLogicTest {

    @Test
    fun `logging twice on the same day leaves the streak untouched`() {
        val existing = Streak(current = 3, longest = 5, lastLogDateKey = "2026-09-07")
        assertEquals(existing, StreakLogic.computeUpdate(existing, "2026-09-07"))
    }

    @Test
    fun `logging the next day extends the streak`() {
        val existing = Streak(current = 3, longest = 5, lastLogDateKey = "2026-09-07")
        val updated = StreakLogic.computeUpdate(existing, "2026-09-08")

        assertEquals(4, updated.current)
        assertEquals(5, updated.longest)
        assertEquals("2026-09-08", updated.lastLogDateKey)
    }

    @Test
    fun `a gap restarts the streak at one and preserves longest`() {
        val existing = Streak(current = 9, longest = 9, lastLogDateKey = "2026-09-07")
        val updated = StreakLogic.computeUpdate(existing, "2026-09-10")

        assertEquals(1, updated.current)
        assertEquals(9, updated.longest)
    }

    @Test
    fun `the first ever log starts the streak at one`() {
        val updated = StreakLogic.computeUpdate(Streak(), "2026-09-07")

        assertEquals(1, updated.current)
        assertEquals(1, updated.longest)
        assertEquals("2026-09-07", updated.lastLogDateKey)
    }

    @Test
    fun `extending past the previous best raises longest`() {
        val existing = Streak(current = 5, longest = 5, lastLogDateKey = "2026-09-07")
        assertEquals(6, StreakLogic.computeUpdate(existing, "2026-09-08").longest)
    }

    @Test
    fun `milestones are awarded only on the exact milestone days`() {
        assertEquals("streak_7", StreakLogic.milestoneBadgeType(7))
        assertEquals("streak_30", StreakLogic.milestoneBadgeType(30))
        assertNull(StreakLogic.milestoneBadgeType(6))
        assertNull(StreakLogic.milestoneBadgeType(8))
        assertNull(StreakLogic.milestoneBadgeType(31))
    }

    // This is the unit-level shape of a bug that used to break logging outright. Milestones are
    // keyed on the *current* streak only, so a streak that breaks and is climbed again returns
    // the same badge type a second time — and badge docs have stable per-user ids and are
    // write-once in firestore.rules. LogRepository.submitLog therefore has to check whether the
    // badge already exists before writing it; setting it unconditionally made the second award an
    // update of an existing doc, which the rules deny, failing the whole transaction and losing
    // the user's log, leaderboard credit and streak with it.
    @Test
    fun `re-earning a milestone after a reset returns the same badge type again`() {
        val firstTime = StreakLogic.computeUpdate(
            Streak(current = 6, longest = 6, lastLogDateKey = "2026-09-07"),
            "2026-09-08"
        )
        assertEquals(7, firstTime.current)
        assertEquals("streak_7", StreakLogic.milestoneBadgeType(firstTime.current))

        // The streak breaks, current is reset to 0, longest is kept, and the user climbs again.
        val climbingAgain = Streak(current = 6, longest = 7, lastLogDateKey = "2026-10-05")
        val secondTime = StreakLogic.computeUpdate(climbingAgain, "2026-10-06")

        assertEquals(7, secondTime.current)
        assertEquals("streak_7", StreakLogic.milestoneBadgeType(secondTime.current))
    }

    @Test
    fun `a streak with no logs yet is never considered broken`() {
        assertFalse(StreakLogic.isBroken("", "2026-09-10"))
    }

    @Test
    fun `logging yesterday keeps the streak alive but two days breaks it`() {
        assertFalse(StreakLogic.isBroken("2026-09-09", "2026-09-10"))
        assertFalse(StreakLogic.isBroken("2026-09-10", "2026-09-10"))
        assertTrue(StreakLogic.isBroken("2026-09-08", "2026-09-10"))
    }
}
