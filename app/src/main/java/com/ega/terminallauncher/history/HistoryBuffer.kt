package com.ega.terminallauncher.history

interface HistoryBuffer {
    val size: Int
    fun add(command: String)
    fun getPrevious(currentDraft: String): String?
    fun getNext(): String?
    fun resetNavigation()
    fun getAll(): List<String>
    fun clear()
}

class InMemoryHistoryBuffer(private val capacity: Int = 500) : HistoryBuffer {
    private val entries = mutableListOf<String>()
    private var navIndex = -1
    private var savedDraft: String = ""

    override val size: Int
        get() = entries.size

    override fun add(command: String) {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return

        if (entries.isNotEmpty() && entries.last() == trimmed) {
            resetNavigation()
            return
        }

        if (entries.size >= capacity) {
            entries.removeAt(0)
        }

        entries.add(trimmed)
        resetNavigation()
    }

    override fun getPrevious(currentDraft: String): String? {
        if (entries.isEmpty()) return null

        if (navIndex == -1) {
            savedDraft = currentDraft
            navIndex = entries.size - 1
            return entries[navIndex]
        }

        if (navIndex > 0) {
            navIndex--
            return entries[navIndex]
        }

        return entries[0]
    }

    override fun getNext(): String? {
        if (entries.isEmpty() || navIndex == -1) return null

        if (navIndex < entries.size - 1) {
            navIndex++
            return entries[navIndex]
        }

        navIndex = -1
        val draft = savedDraft
        savedDraft = ""
        return draft
    }

    override fun resetNavigation() {
        navIndex = -1
        savedDraft = ""
    }

    override fun getAll(): List<String> = entries.toList()

    override fun clear() {
        entries.clear()
        resetNavigation()
    }
}
