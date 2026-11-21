package com.focus.prototype

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundTransitionTest {
    @Test fun aNewHomeEventRestartsChecksAfterAnOverlayExitSequence() {
        val timeline = TransitionCheckTimeline(longArrayOf(220L, 760L, 1400L))

        assertEquals(10220L, timeline.restart(10000L)) // overlay dismissed
        assertArrayEquals(longArrayOf(10220L, 10760L, 11400L), timeline.deadlines())

        // The subsequent HOME event arrives before the first sequence's 1400 ms window ends.
        assertEquals(10470L, timeline.restart(10250L))
        assertArrayEquals(longArrayOf(10470L, 11010L, 11650L), timeline.deadlines())
        assertEquals(11650L, timeline.deadline(2))
    }
}
