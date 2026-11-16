package com.ega.terminallauncher.autocomplete

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AutoCompleteTest {

    private lateinit var engine: DefaultAutoCompleteEngine
    private val installedApps = listOf("WhatsApp", "YouTube", "Chrome", "Camera")
    private val aliases = setOf("gp", "ll")

    @Before
    fun setUp() {
        engine = DefaultAutoCompleteEngine(
            builtins = setOf("clear", "cls", "apps", "list", "battery", "calc", "theme", "help")
        )
    }

    @Test
    fun testEmptyInputReturnsAvailableCommands() {
        val result = engine.complete("", installedApps, aliases)
        assertNull(result.completedText)
        assertTrue(result.suggestions.isNotEmpty())
        assertNotNull(result.message)
        assertTrue(result.message!!.contains("theme"))
    }

    @Test
    fun testUniquePrefixCompletesWithTrailingSpace() {
        val result = engine.complete("the", installedApps, aliases)
        assertEquals("theme ", result.completedText)
        assertEquals(listOf("theme"), result.suggestions)
        assertNull(result.message)
    }

    @Test
    fun testUniquePrefixCaseInsensitive() {
        val result = engine.complete("THE", installedApps, aliases)
        assertEquals("theme ", result.completedText)
    }

    @Test
    fun testAmbiguousPrefixReturnsSuggestionsAndCommonPrefix() {
        val result = engine.complete("cl", installedApps, aliases)
        assertEquals(listOf("clear", "cls"), result.suggestions)
        assertNotNull(result.message)
        assertTrue(result.message!!.contains("clear, cls"))
    }

    @Test
    fun testAppAndAliasCompletion() {
        val resultApp = engine.complete("What", installedApps, aliases)
        assertEquals("WhatsApp ", resultApp.completedText)

        val resultAlias = engine.complete("g", installedApps, aliases)
        assertEquals("gp ", resultAlias.completedText)
    }

    @Test
    fun testNoMatchReturnsNoCompletion() {
        val result = engine.complete("nonexistent", installedApps, aliases)
        assertNull(result.completedText)
        assertTrue(result.suggestions.isEmpty())
        assertNotNull(result.message)
        assertTrue(result.message!!.contains("No match for 'nonexistent'"))
    }
}
