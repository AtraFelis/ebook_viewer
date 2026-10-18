package dev.example.ebookviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class RenderPageSessionTest {
    private class Page(val index: Int, val events: MutableList<String>) : AutoCloseable {
        override fun close() {
            events.add("close$index")
        }
    }

    @Test
    fun samePageTilesShareOneOpenAndCloseAtBatchEnd() {
        val events = ArrayList<String>()
        RenderPageSession { index ->
            events.add("open$index")
            Page(index, events)
        }.use {
            val first = it.page(0)
            repeat(4) { _ -> assertSame(first, it.page(0)) }
            assertEquals(listOf("open0"), events)
        }
        assertEquals(listOf("open0", "close0"), events)
    }

    @Test
    fun switchingPagesClosesBeforeOpeningAnother() {
        val events = ArrayList<String>()
        RenderPageSession { index ->
            events.add("open$index")
            Page(index, events)
        }.use {
            it.page(0)
            it.page(1)
            it.page(0)
        }
        assertEquals(listOf("open0", "close0", "open1", "close1", "open0", "close0"), events)
    }

    @Test
    fun renderExceptionStillClosesCurrentPage() {
        val events = ArrayList<String>()
        assertThrows(IllegalStateException::class.java) {
            RenderPageSession { index -> Page(index, events) }.use {
                it.page(2)
                error("synthetic render failure")
            }
        }
        assertEquals(listOf("close2"), events)
    }

    @Test
    fun failedOpenDoesNotKeepThePreviousPageAndCloseIsIdempotent() {
        val events = ArrayList<String>()
        val session = RenderPageSession { index ->
            if (index == 1) error("synthetic open failure")
            Page(index, events)
        }
        session.page(0)
        assertThrows(IllegalStateException::class.java) { session.page(1) }
        session.close()
        session.close()
        assertEquals(listOf("close0"), events)
        assertThrows(IllegalStateException::class.java) { session.page(2) }
    }
}
