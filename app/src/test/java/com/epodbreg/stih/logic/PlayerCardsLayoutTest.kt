package com.epodbreg.stih.logic

import com.epodbreg.stih.data.BackupSettings
import com.epodbreg.stih.model.CompactCardsMode
import com.epodbreg.stih.model.Player
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port of PlayerCardsLayoutTests.swift + CompactCardsTests.swift: how the game screen groups cards. */
class PlayerCardsLayoutTest {

    private fun rows(fullWidth: List<Boolean>) = PlayerCardsLayout.rows(fullWidth).map { it.indices }

    @Test
    fun leaderAloneThenPairs() {
        assertEquals(listOf(listOf(0), listOf(1, 2), listOf(3, 4)), rows(listOf(true, false, false, false, false)))
        assertEquals(listOf(listOf(0), listOf(1, 2), listOf(3)), rows(listOf(true, false, false, false)))
    }

    @Test
    fun anOpenedCardBreaksThePairs() {
        // 5 players, the 4th (index 3) opened
        assertEquals(listOf(listOf(0), listOf(1, 2), listOf(3), listOf(4)), rows(listOf(true, false, false, true, false)))
        // the 3rd opened: the 2nd waits alone, pairing continues after
        assertEquals(listOf(listOf(0), listOf(1), listOf(2), listOf(3, 4)), rows(listOf(true, false, true, false, false)))
    }

    // Compact screen slots

    private fun describe(slots: List<PlayerCardSlot<String>>) = slots.map { slot ->
        when (val kind = slot.kind) {
            is PlayerCardSlot.Kind.Card -> if (kind.fullWidth) "${slot.player}!" else slot.player
            is PlayerCardSlot.Kind.Details -> "${slot.player}>" + if (kind.underLeftCard) "L" else "R"
        }
    }

    @Test
    fun soleLeaderFullThenPairs() {
        val slots = PlayerCardsLayout.compactSlots(listOf("A", "B", "C", "D", "E"), soleLeader = "A", opened = emptySet())
        assertEquals(listOf("A!", "B", "C", "D", "E"), describe(slots))
        assertEquals(listOf(listOf(0), listOf(1, 2), listOf(3, 4)), rows(slots.map { it.isFullWidth }))
    }

    @Test
    fun tiedTopHasNoFullCard() {
        val slots = PlayerCardsLayout.compactSlots(listOf("A", "B", "C", "D"), soleLeader = null, opened = emptySet())
        assertEquals(listOf("A", "B", "C", "D"), describe(slots))
    }

    @Test
    fun detailsOpenUnderTheRowAndKeepThePair() {
        // The 5th player opened: the 4th keeps its partner, the panel points right
        val right = PlayerCardsLayout.compactSlots(listOf("A", "B", "C", "D", "E"), soleLeader = "A", opened = setOf("E"))
        assertEquals(listOf("A!", "B", "C", "D", "E", "E>R"), describe(right))
        // A left card opened: its panel still comes after the whole row
        val left = PlayerCardsLayout.compactSlots(listOf("A", "B", "C", "D", "E"), soleLeader = "A", opened = setOf("B"))
        assertEquals(listOf("A!", "B", "C", "B>L", "D", "E"), describe(left))
        // The opened leader expands in place, no panel
        val leader = PlayerCardsLayout.compactSlots(listOf("A", "B", "C"), soleLeader = "A", opened = setOf("A"))
        assertEquals(listOf("A!", "B", "C"), describe(leader))
    }

    @Test
    fun aCardKeepsItsIdWhenItChangesWidth() {
        val half = PlayerCardSlot("A", PlayerCardSlot.Kind.Card(fullWidth = false))
        val full = PlayerCardSlot("A", PlayerCardSlot.Kind.Card(fullWidth = true))
        val panel = PlayerCardSlot("A", PlayerCardSlot.Kind.Details(underLeftCard = true))
        assertEquals(half.id, full.id)
        assertNotEquals(half.id, panel.id)
    }

    // Standings

    private fun players(scores: List<Int>) = scores.mapIndexed { seat, score -> Player(name = "P$seat", score = score) }

    @Test
    fun tiesSharePlaceAndKeepSeatOrder() {
        val standings = Standings.of(players(listOf(10, 30, 30, 0, 10)))
        assertEquals(listOf("P1", "P2", "P0", "P4", "P3"), standings.map { it.first.name })
        assertEquals(listOf(0, 0, 2, 2, 4), standings.map { it.second })
        assertNull(Standings.soleLeader(standings))
    }

    @Test
    fun soleLeaderOnlyWhenAlone() {
        assertNull(Standings.soleLeader(Standings.of(players(listOf(0, 0, 0, 0)))))
        assertEquals("P1", Standings.soleLeader(Standings.of(players(listOf(0, 25, 0))))?.name)
    }

    @Test
    fun allFullIsOnePerRow() {
        assertEquals(listOf(listOf(0), listOf(1), listOf(2)), rows(listOf(true, true, true)))
        assertFalse(PlayerCardsLayout.rows(listOf(true, false)).last().isFull)
    }

    // Compact cards setting (CompactCardsTests.swift)

    @Test
    fun autoSwitchesAtFivePlayers() {
        assertFalse(CompactCardsMode.AUTO.isCompact(3))
        assertFalse(CompactCardsMode.AUTO.isCompact(4))
        assertTrue(CompactCardsMode.AUTO.isCompact(5))
        assertTrue(CompactCardsMode.AUTO.isCompact(8))
        assertTrue(CompactCardsMode.ALWAYS.isCompact(3))
        assertFalse(CompactCardsMode.NEVER.isCompact(10))
    }

    @Test
    fun settingTravelsInBackups() {
        val settings = BackupSettings.fromJson(Json.parseToJsonElement("""{"compactCards": "always", "autoRadlci": false}""").jsonObject)
        assertEquals("always", settings.compactCards)
        assertEquals(CompactCardsMode.ALWAYS, CompactCardsMode.fromRaw(settings.compactCards))

        // Unknown values from a newer app are ignored, not fatal
        val future = BackupSettings.fromJson(Json.parseToJsonElement("""{"compactCards": "sometimes"}""").jsonObject)
        assertNull(CompactCardsMode.fromRaw(future.compactCards))
    }
}
