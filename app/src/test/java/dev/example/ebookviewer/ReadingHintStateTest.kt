package dev.example.ebookviewer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingHintStateTest {
    @Test
    fun successfulOpenOffersOnceAndDismissedHintNeverReturnsInSameSession() {
        val state = ReadingHintState()
        assertFalse(state.visible)
        state.documentOpened()
        assertTrue(state.visible)
        state.documentOpened()
        assertTrue(state.visible)
        state.dismiss()
        repeat(3) { state.documentOpened() }
        assertFalse(state.visible)
    }

    @Test
    fun homeInteractionBeforeSuccessfulOpenDoesNotConsumeTheHint() {
        val state = ReadingHintState()
        state.dismiss()
        state.documentOpened()
        assertTrue(state.visible)
    }

    @Test
    fun newApplicationSessionCanOfferAgainWithoutPersistentStorage() {
        val previous = ReadingHintState()
        previous.documentOpened()
        previous.dismiss()
        val next = ReadingHintState()
        assertFalse(next.visible)
        next.documentOpened()
        assertTrue(next.visible)
    }
}
