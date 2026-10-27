package com.epodbreg.stih.model

/**
 * Session groups ("Torek") for filtering stats, plus player-identity helpers.
 * Pure functions over saved sessions — port of SessionGroups.swift.
 */
sealed interface SessionFilter {
    data object All : SessionFilter
    data class Group(val name: String) : SessionFilter
    data object Ungrouped : SessionFilter
}

/** Blank or whitespace-only group names mean "no group". */
fun normalizedGroup(group: String?): String? = group?.trim()?.ifEmpty { null }

/** Group names in use, most sessions first (ties by name). */
val List<GameSession>.groupNames: List<String>
    get() = mapNotNull { it.groupName }
        .groupingBy { it }.eachCount()
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .map { it.key }

fun List<GameSession>.filtered(by: SessionFilter): List<GameSession> = when (by) {
    SessionFilter.All -> this
    is SessionFilter.Group -> filter { it.groupName == by.name }
    SessionFilter.Ungrouped -> filter { it.groupName == null }
}

/** Sessions with exactly the same set of players — seat order doesn't matter. */
fun List<GameSession>.withSamePlayers(session: GameSession): List<GameSession> {
    val table = session.playerNames.toSet()
    return filter { it.playerNames.toSet() == table }
}

/**
 * Heuristic for "the other Jure": a saved name seated somewhere it has no history.
 * - With a group picked: the name has never played in that group.
 * - Without one: the table has known players, but none of them ever played with this name.
 * Brand-new names and brand-new groups are never flagged — there's nothing to compare with.
 */
fun List<GameSession>.isPossiblyDifferentPerson(name: String, seatedWith: List<String>, group: String?): Boolean {
    val sessionsWithName = filter { name in it.playerNames }
    if (sessionsWithName.isEmpty()) return false

    if (group != null) {
        val groupSessions = filter { it.groupName == group }
        if (groupSessions.isEmpty()) return false
        return groupSessions.none { name in it.playerNames }
    }

    val knownNames = flatMap { it.playerNames }.toSet()
    val knownOthers = (seatedWith.toSet() - name).intersect(knownNames)
    if (knownOthers.isEmpty()) return false
    val everPlayedWith = sessionsWithName.flatMap { it.playerNames }.toSet()
    return everPlayedWith.intersect(knownOthers).isEmpty()
}
