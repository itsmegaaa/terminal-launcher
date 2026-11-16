package com.ega.terminallauncher.matcher

import com.ega.terminallauncher.model.AppEntry

sealed interface MatchResult {
    data class Exact(val app: AppEntry) : MatchResult
    data class Single(val app: AppEntry, val tier: Int) : MatchResult
    data class Ambiguous(val candidates: List<AppEntry>, val tier: Int) : MatchResult
    object NoMatch : MatchResult
}

object AppMatcher {

    fun match(query: String, apps: List<AppEntry>): MatchResult {
        val cleanQuery = query.trim().removeSuffix("?").lowercase()
        if (cleanQuery.isEmpty() || apps.isEmpty()) {
            return MatchResult.NoMatch
        }

        // Tier 1: Exact & Prefix Match
        val tier1Result = matchTier1(cleanQuery, apps)
        if (tier1Result != null) {
            return tier1Result
        }

        // Tier 2: Initial & Subsequence Match
        val tier2Result = matchTier2(cleanQuery, apps)
        if (tier2Result != null) {
            return tier2Result
        }

        // Tier 3: Fuzzy Levenshtein Similarity
        return matchTier3(cleanQuery, apps)
    }

    private fun matchTier1(cleanQuery: String, apps: List<AppEntry>): MatchResult? {
        val exact = apps.firstOrNull { it.label.equals(cleanQuery, ignoreCase = true) }
        if (exact != null) {
            return MatchResult.Exact(exact)
        }

        val prefixes = apps.filter { it.label.startsWith(cleanQuery, ignoreCase = true) }
        if (prefixes.isEmpty()) {
            return null
        }

        val ranked = prefixes.sortedWith(
            compareByDescending<AppEntry> { cleanQuery.length.toDouble() / it.label.length.toDouble() }
                .thenBy { it.label }
        )

        return if (ranked.size == 1) {
            MatchResult.Single(ranked[0], tier = 1)
        } else {
            MatchResult.Ambiguous(ranked, tier = 1)
        }
    }

    internal fun extractWords(name: String): List<String> {
        return name.split(Regex("(?<=[a-z0-9])(?=[A-Z])|[\\s_\\-.:,/]+"))
            .filter { it.isNotBlank() }
    }

    private fun matchTier2(cleanQuery: String, apps: List<AppEntry>): MatchResult? {
        data class Candidate(val app: AppEntry, val density: Double)
        val candidates = mutableListOf<Candidate>()

        for (app in apps) {
            val words = extractWords(app.label)
            if (words.isEmpty()) continue
            val initials = words.map { it.first().lowercaseChar() }

            var qIdx = 0
            for (initial in initials) {
                if (qIdx < cleanQuery.length && initial == cleanQuery[qIdx]) {
                    qIdx++
                }
            }

            if (qIdx == cleanQuery.length) {
                val density = cleanQuery.length.toDouble() / words.size.toDouble()
                candidates.add(Candidate(app, density))
            }
        }

        if (candidates.isEmpty()) {
            return null
        }

        candidates.sortWith(
            compareByDescending<Candidate> { it.density }
                .thenBy { it.app.label }
        )

        if (candidates.size == 1) {
            return MatchResult.Single(candidates[0].app, tier = 2)
        }

        val delta = candidates[0].density - candidates[1].density
        return if (delta < 0.10) {
            MatchResult.Ambiguous(candidates.map { it.app }, tier = 2)
        } else {
            MatchResult.Single(candidates[0].app, tier = 2)
        }
    }

    internal fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        var dp = IntArray(n + 1) { it }
        for (i in 1..m) {
            val newDp = IntArray(n + 1)
            newDp[0] = i
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                newDp[j] = minOf(newDp[j - 1] + 1, dp[j] + 1, dp[j - 1] + cost)
            }
            dp = newDp
        }
        return dp[n]
    }

    internal fun levenshteinRatio(s1: String, s2: String): Double {
        val maxLen = maxOf(s1.length, s2.length)
        if (maxLen == 0) return 1.0
        val dist = levenshteinDistance(s1, s2)
        return 1.0 - (dist.toDouble() / maxLen.toDouble())
    }

    private fun matchTier3(cleanQuery: String, apps: List<AppEntry>): MatchResult {
        data class Candidate(val app: AppEntry, val ratio: Double)

        val candidates = apps.map { app ->
            Candidate(app, levenshteinRatio(cleanQuery, app.label.lowercase()))
        }.filter { it.ratio >= 0.75 }
            .toMutableList()

        if (candidates.isEmpty()) {
            return MatchResult.NoMatch
        }

        candidates.sortWith(
            compareByDescending<Candidate> { it.ratio }
                .thenBy { it.app.label }
        )

        if (candidates.size == 1) {
            return MatchResult.Single(candidates[0].app, tier = 3)
        }

        val delta = candidates[0].ratio - candidates[1].ratio
        return if (delta < 0.10) {
            MatchResult.Ambiguous(candidates.map { it.app }, tier = 3)
        } else {
            MatchResult.Single(candidates[0].app, tier = 3)
        }
    }
}
