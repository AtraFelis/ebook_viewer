package dev.example.ebookviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageScrubStateTest {
    @Test
    fun previewClampsAndMapsWholeDocumentWithoutChangingCurrentPosition() {
        val state = PageScrubState()
        state.update(127, 320, true)
        assertTrue(state.begin(-1.0))
        assertEquals(0, state.target)
        state.move(239.0 / 319)
        assertEquals(239, state.target)
        assertEquals(127, state.current)
        state.move(2.0)
        assertEquals(319, state.target)
        assertEquals(319, state.commit())
        assertNull(state.commit())
        state.update(0, Int.MAX_VALUE, true)
        state.begin(1.0)
        assertEquals(Int.MAX_VALUE - 1, state.commit())
    }

    @Test
    fun cancelAndSamePageNeverCommitEvenWithLateMoveAndRelease() {
        val state = PageScrubState()
        state.update(10, 100, true)
        state.begin(0.8)
        assertTrue(state.cancel())
        state.move(1.0)
        assertNull(state.commit())
        assertFalse(state.cancel())
        state.begin(10.0 / 99)
        assertNull(state.commit())
    }

    @Test
    fun unavailableSinglePageAndInvalidPositionsCannotBegin() {
        val state = PageScrubState()
        for ((page, count) in listOf(null to 0, 0 to 1, -1 to 100, 100 to 100)) {
            state.update(page, count, true)
            assertFalse(state.begin(1.0))
            assertNull(state.commit())
        }
        state.update(0, 100, false)
        assertFalse(state.begin(1.0))
    }

    @Test
    fun rangePositionOrAvailabilityChangeInvalidatesAnUnconfirmedTarget() {
        val state = PageScrubState()
        state.update(0, 100, true)
        state.begin(0.8)
        state.update(0, 100, true)
        assertEquals(79, state.target)
        state.update(0, 90, true)
        assertNull(state.commit())
        state.begin(0.8)
        state.update(1, 90, true)
        assertNull(state.commit())
        state.begin(0.8)
        state.update(1, 90, false)
        assertNull(state.commit())
    }
}
