package dev.example.ebookviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageTextLimitsTest {
    @Test
    fun lowMemoryAndThresholdBlockBeforeNativeWork() {
        val floor = PageTextLimits.MIN_AVAILABLE_BYTES
        assertFalse(PageTextLimits.memoryAllowed(floor - 1, 0, false))
        assertFalse(PageTextLimits.memoryAllowed(floor, 0, true))
        assertFalse(PageTextLimits.memoryAllowed(floor, floor, false))
        assertTrue(PageTextLimits.memoryAllowed(floor, floor / 2, false))
    }

    @Test
    fun textBudgetRejectsAnOversizedSinglePartAndManyParts() {
        assertNull(PageTextLimits.bounded(sequenceOf("x".repeat(16_001))))
        assertNull(PageTextLimits.bounded(generateSequence { "xx" }.take(8_001)))
        assertEquals(16_000, PageTextLimits.bounded(sequenceOf("x".repeat(16_000)))?.length)
    }

    @Test
    fun textPartsKeepReadingBreaksAndEmptyScanStaysEmpty() {
        assertEquals("first\nsecond", PageTextLimits.bounded(sequenceOf("first", "second")))
        assertEquals("", PageTextLimits.bounded(emptySequence()))
    }
}
