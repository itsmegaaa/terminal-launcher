package com.ega.terminallauncher.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class HistoryBufferTest {

    private lateinit var history: InMemoryHistoryBuffer

    @Before
    fun setUp() {
        history = InMemoryHistoryBuffer(capacity = 5)
    }

    @Test
    fun testEmptyBufferReturnsNull() {
        assertEquals(0, history.size)
        assertNull(history.getPrevious("draft"))
        assertNull(history.getNext())
    }

    @Test
    fun testAddSingleCommandAndNavigate() {
        history.add("echo hello")
        assertEquals(1, history.size)

        val prev = history.getPrevious("my current draft")
        assertEquals("echo hello", prev)

        val next = history.getNext()
        assertEquals("my current draft", next)
    }

    @Test
    fun testMultipleCommandsNavigationAndDraftRestoration() {
        history.add("cmd1")
        history.add("cmd2")
        history.add("cmd3")
        assertEquals(3, history.size)

        // Navigate up
        assertEquals("cmd3", history.getPrevious("draft"))
        assertEquals("cmd2", history.getPrevious("draft"))
        assertEquals("cmd1", history.getPrevious("draft"))

        // Clamps at oldest
        assertEquals("cmd1", history.getPrevious("draft"))

        // Navigate down
        assertEquals("cmd2", history.getNext())
        assertEquals("cmd3", history.getNext())
        assertEquals("draft", history.getNext())
    }

    @Test
    fun testBlankCommandsIgnored() {
        history.add("")
        history.add("   ")
        history.add("\t\n")
        assertEquals(0, history.size)
    }

    @Test
    fun testConsecutiveDuplicateDeduplication() {
        history.add("ls")
        history.add("ls")
        history.add("ls")
        assertEquals(1, history.size)

        history.add("pwd")
        history.add("ls")
        assertEquals(3, history.size)
        assertEquals(listOf("ls", "pwd", "ls"), history.getAll())
    }

    @Test
    fun testCapacityEviction() {
        for (i in 1..7) {
            history.add("cmd$i")
        }
        assertEquals(5, history.size)
        assertEquals(listOf("cmd3", "cmd4", "cmd5", "cmd6", "cmd7"), history.getAll())
    }

    @Test
    fun testClearEmptiesHistory() {
        history.add("cmd1")
        history.add("cmd2")
        assertEquals(2, history.size)

        history.clear()
        assertEquals(0, history.size)
        assertNull(history.getPrevious("draft"))
    }
}
