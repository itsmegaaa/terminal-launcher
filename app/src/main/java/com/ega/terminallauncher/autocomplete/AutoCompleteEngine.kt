package com.ega.terminallauncher.autocomplete

import com.ega.terminallauncher.alias.AliasManager

data class AutoCompleteResult(
    val completedText: String?,
    val suggestions: List<String> = emptyList(),
    val message: String? = null
)

interface AutoCompleteEngine {
    fun complete(input: String, installedApps: List<String>, aliases: Set<String>): AutoCompleteResult
}

class DefaultAutoCompleteEngine(
    private val builtins: Set<String> = AliasManager.RESERVED_WORDS
) : AutoCompleteEngine {

    override fun complete(
        input: String,
        installedApps: List<String>,
        aliases: Set<String>
    ): AutoCompleteResult {
        val trimmed = input.trim()
        val allCandidates = (builtins + aliases + installedApps).distinct()

        if (trimmed.isEmpty()) {
            val common = builtins.sorted()
            return AutoCompleteResult(
                completedText = null,
                suggestions = common,
                message = "[Available commands: ${common.joinToString(", ")}]"
            )
        }

        val matches = allCandidates.filter { candidate ->
            candidate.startsWith(trimmed, ignoreCase = true)
        }.distinct()

        if (matches.isEmpty()) {
            return AutoCompleteResult(
                completedText = null,
                suggestions = emptyList(),
                message = "[No match for '$trimmed']"
            )
        }

        if (matches.size == 1) {
            val single = matches.first()
            return AutoCompleteResult(
                completedText = "$single ",
                suggestions = matches,
                message = null
            )
        }

        val lcp = findLongestCommonPrefix(matches)
        val completed = if (lcp.length > trimmed.length) lcp else null
        val sorted = matches.sortedBy { it.lowercase() }
        val message = "[Suggestions: ${sorted.joinToString(", ")}]"

        return AutoCompleteResult(
            completedText = completed,
            suggestions = sorted,
            message = message
        )
    }

    private fun findLongestCommonPrefix(strings: List<String>): String {
        if (strings.isEmpty()) return ""
        var prefix = strings[0]
        for (i in 1 until strings.size) {
            while (!strings[i].startsWith(prefix, ignoreCase = true)) {
                prefix = prefix.substring(0, prefix.length - 1)
                if (prefix.isEmpty()) return ""
            }
        }
        return prefix
    }
}
