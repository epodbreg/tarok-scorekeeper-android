package com.epodbreg.stih.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port of SessionGroupsTests.swift: groups and the "different person with the same name" heuristic. */
class SessionGroupsTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun session(names: List<String>, group: String? = null) =
        GameSession(players = names.map { Player(name = it) }, groupName = group)

    private val tuesday = listOf("Elias", "Nina", "Marko", "Tjaša")

    @Test
    fun groupNamesAndFilters() {
        val sessions = listOf(
            session(tuesday, "Torek"),
            session(tuesday, "Torek"),
            session(listOf("Elias", "Jure", "Ana"), "Vikend"),
            session(listOf("Elias", "Bor", "Cene")),
        )
        assertEquals(listOf("Torek", "Vikend"), sessions.groupNames) // most sessions first
        assertEquals(4, sessions.filtered(SessionFilter.All).size)
        assertEquals(2, sessions.filtered(SessionFilter.Group("Torek")).size)
        assertEquals(1, sessions.filtered(SessionFilter.Ungrouped).size)
    }

    @Test
    fun samePlayerSetIgnoresSeatingOrder() {
        val first = session(tuesday)
        val sessions = listOf(
            first,
            session(tuesday.reversed()), // same table, different seats
            session(listOf("Elias", "Nina", "Marko")), // one missing
            session(tuesday + "Jure"), // one extra
        )
        assertEquals(2, sessions.withSamePlayers(first).size)
    }

    @Test
    fun differentPersonWithGroup() {
        val sessions = listOf(
            session(tuesday, "Torek"),
            session(listOf("Elias", "Jure", "Ana")), // Jure #1 — never on Tuesday
        )
        // A known name that has never played in the picked group → flag it
        assertTrue(sessions.isPossiblyDifferentPerson("Jure", listOf("Elias", "Nina"), "Torek"))
        // A Tuesday regular → fine
        assertFalse(sessions.isPossiblyDifferentPerson("Nina", listOf("Elias", "Jure"), "Torek"))
        // Brand-new name or brand-new group → nothing to compare with
        assertFalse(sessions.isPossiblyDifferentPerson("Zala", listOf("Elias"), "Torek"))
        assertFalse(sessions.isPossiblyDifferentPerson("Jure", listOf("Elias"), "Petek"))
    }

    @Test
    fun differentPersonWithoutGroup() {
        val sessions = listOf(
            session(tuesday),
            session(listOf("Jure", "Ana", "Bor")), // Jure #1's crew
        )
        // Jure seated only with known people he never played with → flag
        assertTrue(sessions.isPossiblyDifferentPerson("Jure", listOf("Nina", "Marko"), null))
        // One of them did play with him → same Jure
        assertFalse(sessions.isPossiblyDifferentPerson("Jure", listOf("Nina", "Ana"), null))
        // Only unknown people at the table → no evidence either way
        assertFalse(sessions.isPossiblyDifferentPerson("Jure", listOf("Zala", "Rok"), null))
        // Nobody else seated yet → no warning
        assertFalse(sessions.isPossiblyDifferentPerson("Jure", listOf("", ""), null))
    }

    @Test
    fun groupSurvivesSaveAndOldFilesStillLoad() {
        val tagged = session(tuesday, "Torek")
        val roundTrip = json.decodeFromString(GameSession.serializer(), json.encodeToString(GameSession.serializer(), tagged))
        assertEquals("Torek", roundTrip.groupName)

        // A session saved by an older app version has no groupName key
        val withoutGroup = JsonObject(json.encodeToJsonElement(GameSession.serializer(), tagged).jsonObject - "groupName")
        val old = json.decodeFromJsonElement(GameSession.serializer(), withoutGroup)
        assertNull(old.groupName)
    }

    @Test
    fun splitRenamesOnlyChosenSessions() {
        val jure1 = session(listOf("Elias", "Jure", "Ana"))
        val jure2 = session(listOf("Elias", "Jure", "Bor"))
        // The split is a per-session rename; the untouched session keeps the old name
        val split = listOf(jure1, jure2).map { if (it.id == jure2.id) it.renamingPlayer("Jure", "Jure S.") else it }
        assertTrue("Jure" in split[0].playerNames)
        assertTrue("Jure S." in split[1].playerNames)
        assertFalse("Jure" in split[1].playerNames)
    }
}
