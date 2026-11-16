package com.ega.terminallauncher

import com.ega.terminallauncher.matcher.AppMatcher
import com.ega.terminallauncher.matcher.MatchResult
import com.ega.terminallauncher.model.AppEntry
import com.ega.terminallauncher.mru.InMemoryMruRepository
import com.ega.terminallauncher.state.CommandAction
import com.ega.terminallauncher.state.LauncherState
import com.ega.terminallauncher.state.LauncherStateMachine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzyMatcherTest {

    private val sampleApps = listOf(
        AppEntry("Chrome", "com.android.chrome"),
        AppEntry("Camera", "com.android.camera"),
        AppEntry("Calculator", "com.android.calculator2"),
        AppEntry("WhatsApp", "com.whatsapp"),
        AppEntry("WhatsApp Business", "com.whatsapp.w4b"),
        AppEntry("YouTube", "com.google.android.youtube"),
        AppEntry("YouTube Studio", "com.google.android.apps.youtube.creator"),
        AppEntry("Spotify", "com.spotify.music"),
        AppEntry("Mobile Legends", "com.mobile.legends"),
        AppEntry("Modern Life", "com.modern.life"),
        AppEntry("F-Droid", "org.fdroid.fdroid"),
        AppEntry("Slack", "com.Slack"),
        AppEntry("Stack", "com.stack.overflow"),
        AppEntry("Files", "com.google.android.apps.nbu.files"),
        AppEntry("Finds", "com.example.finds")
    )

    // ==========================================
    // Tier 1: Exact & Prefix Match Tests
    // ==========================================

    @Test
    fun testTier1_exactMatch_shortCircuits() {
        val apps = listOf(
            AppEntry("wa", "com.wa.short"),
            AppEntry("WhatsApp", "com.whatsapp"),
            AppEntry("WhatsApp Business", "com.whatsapp.w4b")
        )
        val result = AppMatcher.match("wa", apps)
        assertTrue(result is MatchResult.Exact)
        assertEquals("com.wa.short", (result as MatchResult.Exact).app.packageName)
    }

    @Test
    fun testTier1_caseInsensitiveExactMatch() {
        val result = AppMatcher.match("CHROME", sampleApps)
        assertTrue(result is MatchResult.Exact)
        assertEquals("Chrome", (result as MatchResult.Exact).app.label)
    }

    @Test
    fun testTier1_singlePrefixMatch() {
        val result = AppMatcher.match("spot", sampleApps)
        assertTrue(result is MatchResult.Single)
        val single = result as MatchResult.Single
        assertEquals(1, single.tier)
        assertEquals("Spotify", single.app.label)
    }

    @Test
    fun testTier1_prefixAmbiguity_rankedByRatio() {
        // Query "wh": matches "WhatsApp" (len 8, ratio 2/8 = 0.25) and "WhatsApp Business" (len 17, ratio 2/17 = 0.1176)
        val result = AppMatcher.match("wh", sampleApps)
        assertTrue(result is MatchResult.Ambiguous)
        val ambiguous = result as MatchResult.Ambiguous
        assertEquals(1, ambiguous.tier)
        assertEquals(2, ambiguous.candidates.size)
        // Highest ratio should be ranked first
        assertEquals("WhatsApp", ambiguous.candidates[0].label)
        assertEquals("WhatsApp Business", ambiguous.candidates[1].label)
    }

    @Test
    fun testTier1_prefixEqualRatios_stableAlphabeticalSort() {
        val result = AppMatcher.match("fi", sampleApps)
        assertTrue(result is MatchResult.Ambiguous)
        val ambiguous = result as MatchResult.Ambiguous
        assertEquals(1, ambiguous.tier)
        assertEquals(2, ambiguous.candidates.size)
        // Both len 5, sorted alphabetically: Files, Finds
        assertEquals("Files", ambiguous.candidates[0].label)
        assertEquals("Finds", ambiguous.candidates[1].label)
    }

    // ==========================================
    // Tier 2: Initial & Subsequence Match Tests
    // ==========================================

    @Test
    fun testTier2_wordExtraction() {
        assertEquals(listOf("You", "Tube"), AppMatcher.extractWords("YouTube"))
        assertEquals(listOf("F", "Droid"), AppMatcher.extractWords("F-Droid"))
        assertEquals(listOf("Mobile", "Legends"), AppMatcher.extractWords("Mobile Legends"))
        assertEquals(listOf("Clash", "of", "Clans"), AppMatcher.extractWords("Clash of Clans"))
    }

    @Test
    fun testTier2_initialMatch_densityCalculation() {
        // Query "yt" matches "YouTube" (words: You, Tube -> initials y, t -> matched: 2, total: 2 -> density 1.0)
        val apps = listOf(AppEntry("YouTube", "com.google.android.youtube"))
        val result = AppMatcher.match("yt", apps)
        assertTrue(result is MatchResult.Single)
        val single = result as MatchResult.Single
        assertEquals(2, single.tier)
        assertEquals("YouTube", single.app.label)
    }

    @Test
    fun testTier2_hyphenatedPunctuationWordMatch() {
        // Query "fd" matches "F-Droid"
        val apps = listOf(AppEntry("F-Droid", "org.fdroid.fdroid"))
        val result = AppMatcher.match("fd", apps)
        assertTrue(result is MatchResult.Single)
        val single = result as MatchResult.Single
        assertEquals(2, single.tier)
        assertEquals("F-Droid", single.app.label)
    }

    @Test
    fun testTier2_singleWinner_whenDeltaAboveThreshold() {
        // Query "yt" with "YouTube" (density = 1.0) and "YouTube Studio" (density = 2/3 = 0.667)
        // Delta = 1.0 - 0.667 = 0.333 >= 0.10 -> Single winner
        val apps = listOf(
            AppEntry("YouTube Studio", "com.google.android.apps.youtube.creator"),
            AppEntry("YouTube", "com.google.android.youtube")
        )
        val result = AppMatcher.match("yt", apps)
        assertTrue(result is MatchResult.Single)
        val single = result as MatchResult.Single
        assertEquals(2, single.tier)
        assertEquals("YouTube", single.app.label)
    }

    @Test
    fun testTier2_ambiguity_whenDeltaBelowThreshold() {
        // Query "ml" with "Mobile Legends" (density 1.0) and "Modern Life" (density 1.0)
        // Delta = 0.0 < 0.10 -> Ambiguous
        val apps = listOf(
            AppEntry("Modern Life", "com.modern.life"),
            AppEntry("Mobile Legends", "com.mobile.legends")
        )
        val result = AppMatcher.match("ml", apps)
        assertTrue(result is MatchResult.Ambiguous)
        val ambiguous = result as MatchResult.Ambiguous
        assertEquals(2, ambiguous.tier)
        assertEquals(2, ambiguous.candidates.size)
    }

    @Test
    fun testTier2_outOfOrderInitials_failsTier2() {
        val apps = listOf(AppEntry("Call of Duty", "com.activision.callofduty.shooter"))
        // Query "doc" has initials d, o, c which are out-of-order for Call of Duty (c, o, d)
        val result = AppMatcher.match("doc", apps)
        // Should not match Tier 2, falls through to Tier 3 and fails ratio threshold
        assertTrue(result is MatchResult.NoMatch)
    }

    // ==========================================
    // Tier 3: Fuzzy Levenshtein Tests
    // ==========================================

    @Test
    fun testTier3_levenshteinRatioFormula() {
        // "chrme" vs "chrome": dist = 1, maxLen = 6 -> ratio = 1 - 1/6 = 0.8333...
        val ratio = AppMatcher.levenshteinRatio("chrme", "chrome")
        assertTrue(ratio >= 0.83 && ratio <= 0.84)
    }

    @Test
    fun testTier3_singleWinner_aboveThreshold() {
        val apps = listOf(
            AppEntry("Chrome", "com.android.chrome"),
            AppEntry("Camera", "com.android.camera")
        )
        val result = AppMatcher.match("chrme", apps)
        assertTrue(result is MatchResult.Single)
        val single = result as MatchResult.Single
        assertEquals(3, single.tier)
        assertEquals("Chrome", single.app.label)
    }

    @Test
    fun testTier3_thresholdRejection_belowPoint75() {
        // "w" vs "WhatsApp" (dist = 7, max = 8 -> ratio = 0.125 < 0.75)
        val apps = listOf(AppEntry("WhatsApp", "com.whatsapp"))
        val result = AppMatcher.match("w", apps)
        // Neither Tier 1 prefix (query "w" matches WhatsApp as single prefix in Tier 1 though!).
        // Let's test with a non-prefix query: "xyz" vs "WhatsApp"
        val nonPrefixResult = AppMatcher.match("xyz", apps)
        assertTrue(nonPrefixResult is MatchResult.NoMatch)
    }

    @Test
    fun testTier3_ambiguity_whenDeltaBelowThreshold() {
        // Query "smack" with "Slack" (dist 1, ratio 0.80) and "Stack" (dist 1, ratio 0.80)
        val apps = listOf(
            AppEntry("Stack", "com.stack.overflow"),
            AppEntry("Slack", "com.Slack")
        )
        val result = AppMatcher.match("smack", apps)
        assertTrue(result is MatchResult.Ambiguous)
        val ambiguous = result as MatchResult.Ambiguous
        assertEquals(3, ambiguous.tier)
        assertEquals(2, ambiguous.candidates.size)
    }

    // ==========================================
    // MRU & State Machine Tests
    // ==========================================

    @Test
    fun testMru_saveAndAutoLaunch() {
        val mru = InMemoryMruRepository()
        val stateMachine = LauncherStateMachine(mruRepo = mru)

        // 1. First query "wh" is ambiguous -> enters AwaitingSelection
        val action1 = stateMachine.onInput("wh", sampleApps)
        assertTrue(action1 is CommandAction.ShowChoices)
        assertTrue(stateMachine.state is LauncherState.AwaitingSelection)

        // 2. Select option 1 ("WhatsApp")
        val action2 = stateMachine.onInput("1", sampleApps)
        assertTrue(action2 is CommandAction.Launch)
        val launchAction = action2 as CommandAction.Launch
        assertEquals("WhatsApp", launchAction.app.label)
        assertEquals("selection", launchAction.source)
        assertEquals(LauncherState.Normal, stateMachine.state)

        // 3. Verify MRU stored
        assertEquals("com.whatsapp", mru.get("wh"))

        // 4. Repeat query "wh" -> auto-launches remembered app directly without choices
        val action3 = stateMachine.onInput("wh", sampleApps)
        assertTrue(action3 is CommandAction.Launch)
        val mruLaunch = action3 as CommandAction.Launch
        assertEquals("WhatsApp", mruLaunch.app.label)
        assertEquals("mru", mruLaunch.source)
        assertEquals(LauncherState.Normal, stateMachine.state)
    }

    @Test
    fun testMru_suffixQuestionMark_forcesSelectionList() {
        val mru = InMemoryMruRepository()
        mru.put("wh", "com.whatsapp")
        val stateMachine = LauncherStateMachine(mruRepo = mru)

        // Query "wh?" should bypass MRU and force numbered selection list
        val action = stateMachine.onInput("wh?", sampleApps)
        assertTrue(action is CommandAction.ShowChoices)
        assertTrue(stateMachine.state is LauncherState.AwaitingSelection)
    }

    @Test
    fun testMru_resetCommand_clearsMru() {
        val mru = InMemoryMruRepository()
        mru.put("wh", "com.whatsapp")
        val stateMachine = LauncherStateMachine(mruRepo = mru)

        val action = stateMachine.onInput("reset mru", sampleApps)
        assertTrue(action is CommandAction.Message)
        assertEquals("[MRU cache cleared]", (action as CommandAction.Message).text)
        assertNull(mru.get("wh"))

        // Next query "wh" should now produce ambiguity choices since MRU is cleared
        val nextAction = stateMachine.onInput("wh", sampleApps)
        assertTrue(nextAction is CommandAction.ShowChoices)
    }

    @Test
    fun testMru_packageUninstall_removesRecord() {
        val mru = InMemoryMruRepository()
        mru.put("wa", "com.whatsapp")
        mru.put("w4b", "com.whatsapp.w4b")
        mru.put("yt", "com.google.android.youtube")

        mru.removePackage("com.whatsapp")
        assertNull(mru.get("wa"))
        assertEquals("com.whatsapp.w4b", mru.get("w4b"))
        assertEquals("com.google.android.youtube", mru.get("yt"))
    }

    @Test
    fun testStateMachine_awaitingSelection_numericSelection() {
        val mru = InMemoryMruRepository()
        val stateMachine = LauncherStateMachine(mruRepo = mru)

        stateMachine.onInput("wh", sampleApps)
        assertTrue(stateMachine.state is LauncherState.AwaitingSelection)

        // Select option 2 ("WhatsApp Business")
        val action = stateMachine.onInput("2", sampleApps)
        assertTrue(action is CommandAction.Launch)
        assertEquals("WhatsApp Business", (action as CommandAction.Launch).app.label)
        assertEquals(LauncherState.Normal, stateMachine.state)
        assertEquals("com.whatsapp.w4b", mru.get("wh"))
    }

    @Test
    fun testStateMachine_awaitingSelection_nonNumericInput_cancelsAndExecutes() {
        val mru = InMemoryMruRepository()
        val stateMachine = LauncherStateMachine(mruRepo = mru)

        stateMachine.onInput("wh", sampleApps)
        assertTrue(stateMachine.state is LauncherState.AwaitingSelection)

        // Typing a non-numeric command like "chrome" should cancel selection and launch Chrome directly
        val action = stateMachine.onInput("chrome", sampleApps)
        assertTrue(action is CommandAction.Launch)
        assertEquals("Chrome", (action as CommandAction.Launch).app.label)
        assertEquals(LauncherState.Normal, stateMachine.state)
    }

    @Test
    fun testStateMachine_backButton_cancelsAwaitingSelection() {
        val mru = InMemoryMruRepository()
        val stateMachine = LauncherStateMachine(mruRepo = mru)

        stateMachine.onInput("wh", sampleApps)
        assertTrue(stateMachine.state is LauncherState.AwaitingSelection)

        val handled = stateMachine.onBack()
        assertTrue(handled)
        assertEquals(LauncherState.Normal, stateMachine.state)

        // Second back press when already Normal returns false
        assertFalse(stateMachine.onBack())
    }

    @Test
    fun testStateMachine_onNewIntent_resetsToNormal() {
        val mru = InMemoryMruRepository()
        val stateMachine = LauncherStateMachine(mruRepo = mru)

        stateMachine.onInput("wh", sampleApps)
        assertTrue(stateMachine.state is LauncherState.AwaitingSelection)

        stateMachine.onNewIntent()
        assertEquals(LauncherState.Normal, stateMachine.state)
    }

    @Test
    fun testStateMachine_expiration_resetsToNormal() {
        val mru = InMemoryMruRepository()
        val stateMachine = LauncherStateMachine(mruRepo = mru)

        stateMachine.onInput("wh", sampleApps)
        assertTrue(stateMachine.state is LauncherState.AwaitingSelection)

        stateMachine.expire()
        assertEquals(LauncherState.Normal, stateMachine.state)
    }

    @Test
    fun testStateMachine_formatChoices() {
        val candidates = listOf(
            AppEntry("WhatsApp", "com.whatsapp"),
            AppEntry("WhatsApp Business", "com.whatsapp.w4b")
        )
        val formatted = LauncherStateMachine.formatChoices(candidates)
        assertEquals("[1] WhatsApp  [2] WhatsApp Business", formatted)
    }
}
