package com.ega.terminallauncher.alias

sealed interface AliasResult {
    data class Success(val message: String) : AliasResult
    data class Error(val message: String) : AliasResult
}

class AliasManager(private val aliasRepo: AliasRepository) {

    companion object {
        val RESERVED_WORDS = setOf(
            "clear", "cls",
            "apps", "list",
            "battery", "bat",
            "flash",
            "vol", "volume",
            "calc",
            "alias", "unalias",
            "reset",
            "help",
            "open",
            "theme"
        )

        private val ALIAS_NAME_REGEX = Regex("^[a-z][a-z0-9_-]*$")
    }

    fun handleAliasCommand(args: String): AliasResult {
        val trimmed = args.trim()
        if (trimmed.isEmpty()) {
            return AliasResult.Success(listAliases())
        }

        val equalsIndex = trimmed.indexOf('=')
        if (equalsIndex == -1) {
            return AliasResult.Error("[Usage: alias <name>=<cmd>]")
        }

        val name = trimmed.substring(0, equalsIndex).trim()
        val rawTarget = trimmed.substring(equalsIndex + 1).trim()

        if (name.isEmpty()) {
            return AliasResult.Error("[Usage: alias <name>=<cmd>]")
        }

        if (rawTarget.isEmpty()) {
            return AliasResult.Error("[Error: Alias target cannot be empty]")
        }

        val target = stripQuotes(rawTarget)
        if (target.isEmpty()) {
            return AliasResult.Error("[Error: Alias target cannot be empty]")
        }

        return registerAlias(name, target)
    }

    fun registerAlias(name: String, target: String): AliasResult {
        val cleanName = name.trim().lowercase()
        val cleanTarget = stripQuotes(target.trim())

        if (cleanName.isEmpty() || cleanTarget.isEmpty()) {
            return AliasResult.Error("[Usage: alias <name>=<cmd>]")
        }

        if (cleanName[0].isDigit()) {
            return AliasResult.Error("[Error: Alias name cannot be purely numeric or start with a digit]")
        }

        if (!ALIAS_NAME_REGEX.matches(cleanName)) {
            return AliasResult.Error("[Error: Invalid alias name. Must start with a letter and contain only alphanumeric, '-', or '_']")
        }

        if (RESERVED_WORDS.contains(cleanName)) {
            return AliasResult.Error("[Error: '$cleanName' is a reserved command name]")
        }

        // 2-Way Nested Alias Cycle Prevention
        val targetFirstToken = cleanTarget.split(Regex("\\s+"))[0].lowercase()

        // Direction 1: Target cannot be an existing alias
        if (aliasRepo.get(targetFirstToken) != null) {
            return AliasResult.Error("[Error: Nested alias is not supported]")
        }

        // Direction 2: New alias name cannot be an existing alias's target
        val allAliases = aliasRepo.getAll()
        for ((_, existingTarget) in allAliases) {
            val existingFirstToken = existingTarget.trim().split(Regex("\\s+"))[0].lowercase()
            if (existingFirstToken == cleanName) {
                return AliasResult.Error("[Error: Nested alias is not supported]")
            }
        }

        aliasRepo.put(cleanName, cleanTarget)
        return AliasResult.Success("[Alias registered: $cleanName -> $cleanTarget]")
    }

    fun handleUnaliasCommand(name: String): AliasResult {
        val cleanName = name.trim().lowercase()
        if (cleanName.isEmpty()) {
            return AliasResult.Error("[Usage: unalias <name>]")
        }

        val removed = aliasRepo.remove(cleanName)
        return if (removed) {
            AliasResult.Success("[Alias removed: $cleanName]")
        } else {
            AliasResult.Error("[Error: Alias '$cleanName' not found]")
        }
    }

    fun listAliases(): String {
        val all = aliasRepo.getAll()
        if (all.isEmpty()) {
            return "[No aliases defined]"
        }

        val sorted = all.entries.sortedBy { it.key }
        val sb = StringBuilder("Aliases:\n")
        sorted.forEachIndexed { index, entry ->
            sb.append("  ${entry.key} = ${entry.value}")
            if (index < sorted.size - 1) sb.append("\n")
        }
        return sb.toString()
    }

    fun expandAlias(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return input

        val parts = trimmed.split(Regex("\\s+"), limit = 2)
        val firstToken = parts[0].lowercase()
        val target = aliasRepo.get(firstToken) ?: return input

        return if (parts.size > 1) {
            "$target ${parts[1]}"
        } else {
            target
        }
    }

    private fun stripQuotes(str: String): String {
        val trimmed = str.trim()
        if (trimmed.length >= 2) {
            if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
                (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
                return trimmed.substring(1, trimmed.length - 1).trim()
            }
        }
        return trimmed
    }
}
