package dev.example.ebookviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveSettingTest {
    @Test
    fun failedDraftKeepsInputAndRetriesWithoutChangingConfirmation() {
        var finish: (Boolean) -> Unit = {}
        val state = LiveSetting(false, { _, done -> finish = done }, {})
        state.choose(true)
        finish(false)
        assertTrue(state.value)
        assertFalse(state.confirmed)
        assertTrue(state.failed)
        state.save()
        finish(true)
        assertTrue(state.confirmed)
        assertFalse(state.failed)
    }

    @Test
    fun inFlightAndOldDuplicateCallbacksCannotReplaceNewSelection() {
        val callbacks = mutableListOf<(Boolean) -> Unit>()
        val state = LiveSetting(10, { _, done -> callbacks.add(done) }, {})
        state.choose(20)
        state.choose(30)
        assertEquals(1, callbacks.size)
        assertEquals(20, state.value)
        callbacks[0](true)
        state.choose(40)
        callbacks[0](false)
        assertTrue(state.busy)
        callbacks[1](true)
        assertEquals(40, state.confirmed)
        assertFalse(state.failed)
    }

    @Test
    fun submissionExceptionKeepsDraftAndDoesNotAffectAnotherFile() {
        var reject = true
        val first = LiveSetting(1, { _, done ->
            if (reject) error("Synthetic rejected write") else done(true)
        }, {})
        val other = LiveSetting(2, { _, done -> done(true) }, {})
        first.choose(3)
        other.choose(4)
        assertTrue(first.failed)
        assertEquals(1, first.confirmed)
        assertEquals(3, first.value)
        assertEquals(4, other.confirmed)
        reject = false
        first.save()
        assertEquals(3, first.confirmed)
    }

    @Test
    fun recreatedUnconfirmedValueRequiresRetryButCompletedValueDoesNot() {
        val state = LiveSetting(25, { _, done -> done(true) }, {})
        state.restore(75)
        assertTrue(state.failed)
        assertEquals(25, state.confirmed)
        state.save()
        val restored = LiveSetting(75, { _, done -> done(true) }, {})
        restored.restore(75)
        assertFalse(restored.failed)
    }
}
