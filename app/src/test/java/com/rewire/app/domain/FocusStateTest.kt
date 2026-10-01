package com.rewire.app.domain

import com.rewire.app.domain.focus.FocusConfig
import com.rewire.app.domain.focus.FocusConfigError
import com.rewire.app.domain.focus.FocusSessionStatus.BREAK
import com.rewire.app.domain.focus.FocusSessionStatus.CANCELLED
import com.rewire.app.domain.focus.FocusSessionStatus.COMPLETED
import com.rewire.app.domain.focus.FocusSessionStatus.FOCUSING
import com.rewire.app.domain.focus.FocusSessionStatus.PAUSED
import com.rewire.app.domain.focus.FocusState
import com.rewire.app.domain.focus.FocusState.Companion.MINUTE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FocusStateTest {

    @Test fun breakLongerThanFocusFails() =
        assertEquals(FocusConfigError.BREAK_LONGER_THAN_FOCUS, FocusConfig(20, 30, 1).validate())

    @Test fun equalBreakAndFocusIsValid() = assertNull(FocusConfig(25, 25, 2).validate())

    @Test(expected = IllegalArgumentException::class)
    fun startRejectsInvalidConfig() { FocusState().start(FocusConfig(20, 30, 1), 0) }

    @Test fun fullCycleRunsFocusBreakFocusComplete() {
        val s0 = FocusState().start(FocusConfig(50, 10, 2), 0)
        assertEquals(FOCUSING, s0.status)
        val s1 = s0.advance(50 * MINUTE)
        assertEquals(BREAK, s1.status)
        val s2 = s1.advance(60 * MINUTE)
        assertEquals(FOCUSING, s2.status); assertEquals(2, s2.cycle)
        val s3 = s2.advance(110 * MINUTE)
        assertEquals(COMPLETED, s3.status)
        assertEquals(110 * MINUTE, s3.completedAt)
    }

    @Test fun lateTickSkipsMultiplePhasesWithoutDrift() {
        val s = FocusState().start(FocusConfig(10, 5, 3), 0).advance(16 * MINUTE)
        assertEquals(FOCUSING, s.status); assertEquals(2, s.cycle)
        assertEquals(25 * MINUTE, s.phaseEndsAt)
    }

    @Test fun pauseFreezesRemainingAndResumeContinues() {
        val p = FocusState().start(FocusConfig(25, 5, 1), 0).pause(10 * MINUTE)
        assertEquals(PAUSED, p.status)
        assertEquals(15 * MINUTE, p.remaining(999 * MINUTE))
        val r = p.resume(100 * MINUTE)
        assertEquals(FOCUSING, r.status)
        assertEquals(115 * MINUTE, r.phaseEndsAt)
    }

    @Test fun skipBreakStartsNextFocus() {
        val b = FocusState().start(FocusConfig(10, 5, 2), 0).advance(10 * MINUTE)
        val f = b.skipBreak(11 * MINUTE)
        assertEquals(FOCUSING, f.status); assertEquals(2, f.cycle); assertEquals(21 * MINUTE, f.phaseEndsAt)
    }

    @Test fun zeroBreakGoesStraightToNextFocus() {
        val s = FocusState().start(FocusConfig(10, 0, 2), 0).advance(10 * MINUTE)
        assertEquals(FOCUSING, s.status); assertEquals(2, s.cycle)
    }

    @Test fun cancelEndsActiveSession() =
        assertEquals(CANCELLED, FocusState().start(FocusConfig(10, 5, 1), 0).cancel(1).status)
}
