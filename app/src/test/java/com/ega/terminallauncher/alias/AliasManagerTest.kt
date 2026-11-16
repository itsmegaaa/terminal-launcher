package com.ega.terminallauncher.alias

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AliasManagerTest {

    private lateinit var repo: InMemoryAliasRepository
    private lateinit var manager: AliasManager

    @Before
    fun setUp() {
        repo = InMemoryAliasRepository()
        manager = AliasManager(repo)
    }

    @Test
    fun testEmptyAliasesList() {
        assertEquals("[No aliases defined]", manager.listAliases())
    }

    @Test
    fun testRegisterAndListAlias() {
        val r1 = manager.handleAliasCommand("gs=git status")
        assertTrue(r1 is AliasResult.Success)
        assertEquals("[Alias registered: gs -> git status]", (r1 as AliasResult.Success).message)

        val list = manager.listAliases()
        assertTrue(list.contains("gs = git status"))
    }

    @Test
    fun testQuoteStripping() {
        val r1 = manager.handleAliasCommand("gs=\"git status\"")
        assertTrue(r1 is AliasResult.Success)
        assertEquals("git status", repo.get("gs"))

        val r2 = manager.handleAliasCommand("gp='git push origin main'")
        assertTrue(r2 is AliasResult.Success)
        assertEquals("git push origin main", repo.get("gp"))

        // Inner quotes preserved
        val r3 = manager.handleAliasCommand("say='echo \"hello world\"'")
        assertTrue(r3 is AliasResult.Success)
        assertEquals("echo \"hello world\"", repo.get("say"))
    }

    @Test
    fun testRejectsNumericAndInvalidNames() {
        val r1 = manager.handleAliasCommand("1=whatsapp")
        assertTrue(r1 is AliasResult.Error)
        assertEquals("[Error: Alias name cannot be purely numeric or start with a digit]", (r1 as AliasResult.Error).message)

        val r2 = manager.handleAliasCommand("123=calc")
        assertTrue(r2 is AliasResult.Error)
        assertEquals("[Error: Alias name cannot be purely numeric or start with a digit]", (r2 as AliasResult.Error).message)

        val r3 = manager.handleAliasCommand("my alias=test")
        assertTrue(r3 is AliasResult.Error)
    }

    @Test
    fun testRejectsReservedWords() {
        for (word in AliasManager.RESERVED_WORDS) {
            val res = manager.handleAliasCommand("$word=something")
            assertTrue("Should reject reserved word: $word", res is AliasResult.Error)
            assertEquals("[Error: '$word' is a reserved command name]", (res as AliasResult.Error).message)
        }
    }

    @Test
    fun testRejectsEmptyTargetOrMissingEquals() {
        val r1 = manager.handleAliasCommand("gs=")
        assertTrue(r1 is AliasResult.Error)
        assertEquals("[Error: Alias target cannot be empty]", (r1 as AliasResult.Error).message)

        val r2 = manager.handleAliasCommand("gs")
        assertTrue(r2 is AliasResult.Error)
        assertEquals("[Usage: alias <name>=<cmd>]", (r2 as AliasResult.Error).message)
    }

    @Test
    fun testNestedAliasPreventionDirection1() {
        // Direction 1: target's first token is an existing alias
        manager.handleAliasCommand("b=echo hi")

        val r = manager.handleAliasCommand("a=b")
        assertTrue(r is AliasResult.Error)
        assertEquals("[Error: Nested alias is not supported]", (r as AliasResult.Error).message)

        val rWithArgs = manager.handleAliasCommand("a=\"b -l\"")
        assertTrue(rWithArgs is AliasResult.Error)
        assertEquals("[Error: Nested alias is not supported]", (rWithArgs as AliasResult.Error).message)
    }

    @Test
    fun testNestedAliasPreventionDirection2() {
        // Direction 2: new alias name is already the target of an existing alias
        manager.handleAliasCommand("a=b")

        val r = manager.handleAliasCommand("b=echo world")
        assertTrue(r is AliasResult.Error)
        assertEquals("[Error: Nested alias is not supported]", (r as AliasResult.Error).message)
    }

    @Test
    fun testExpandAlias() {
        manager.handleAliasCommand("gs=\$git status")

        assertEquals("\$git status", manager.expandAlias("gs"))
        assertEquals("\$git status -s", manager.expandAlias("gs -s"))
        assertEquals("unknown command", manager.expandAlias("unknown command"))
    }

    @Test
    fun testUnalias() {
        manager.handleAliasCommand("gs=git status")
        val r1 = manager.handleUnaliasCommand("gs")
        assertTrue(r1 is AliasResult.Success)
        assertEquals("[Alias removed: gs]", (r1 as AliasResult.Success).message)

        val r2 = manager.handleUnaliasCommand("gs")
        assertTrue(r2 is AliasResult.Error)
        assertEquals("[Error: Alias 'gs' not found]", (r2 as AliasResult.Error).message)
    }
}
