package com.epodbreg.stih.logic

import com.epodbreg.stih.model.GameEntry
import com.epodbreg.stih.model.GeneralRulesConfig
import com.epodbreg.stih.model.HandOutcome
import com.epodbreg.stih.model.Player
import com.epodbreg.stih.model.PointsBreakdown
import com.epodbreg.stih.model.TarokContract
import com.epodbreg.stih.model.TarokStrings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port of PartnerRadlcTests.swift: "Radlc podvoji tudi soigralcu_ki". */
class PartnerRadlcTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun dve(partner: String?) = GameEntry(
        contract = TarokContract.DVE, declarerName = "Ana", partnerName = partner, outcome = HandOutcome.ZMAGAL,
        pointsBreakdown = PointsBreakdown(gamePoints = 20, difference = 15, kontraLevel = 0),
        totalPoints = 35,
    )

    private fun table(anaRadlci: List<Boolean> = listOf(false)) = listOf(
        Player(name = "Ana", radlci = anaRadlci), Player(name = "Bor"), Player(name = "Cene"), Player(name = "Dan"),
    )

    @Test
    fun byDefaultThePartnerIsDoubledToo() {
        val game = TarokScorer.processGame(dve(partner = "Bor"), table(), GeneralRulesConfig()).entry

        assertEquals(70, game.totalPoints)
        assertNull(game.partnerPoints)
        assertEquals(70, game.pointsFor("Ana"))
        assertEquals(70, game.pointsFor("Bor"))
        assertEquals(0, game.pointsFor("Cene"))
    }

    @Test
    fun declarerOnlyLeavesThePartnerUndoubled() {
        val rules = GeneralRulesConfig(radlcDoublesPartner = false)
        val result = TarokScorer.processGame(dve(partner = "Bor"), table(), rules)
        val game = result.entry

        assertEquals(70, game.totalPoints)
        assertEquals(35, game.partnerPoints)
        assertEquals(70, game.pointsFor("Ana"))
        assertEquals(35, game.pointsFor("Bor"))
        assertEquals(listOf(true), result.players[0].radlci)  // the declarer's radlc is used as before
    }

    @Test
    fun nothingChangesWithoutARadlcOrAPartner() {
        val rules = GeneralRulesConfig(radlcDoublesPartner = false)

        val plain = TarokScorer.processGame(dve(partner = "Bor"), table(anaRadlci = emptyList()), rules).entry
        assertNull(plain.partnerPoints)
        assertEquals(35, plain.pointsFor("Bor"))

        val alone = TarokScorer.processGame(dve(partner = TarokStrings.ZARUFAL), table(), rules).entry
        assertNull(alone.partnerPoints)
        assertNull(alone.actualPartner)
        assertEquals(0, alone.pointsFor(TarokStrings.ZARUFAL))
    }

    @Test
    fun oldDataReadsWithTodaysBehaviour() {
        val rules = json.decodeFromString<GeneralRulesConfig>("{}")
        assertTrue(rules.radlcDoublesPartner)
        assertFalse(rules.radlcSkipsPrazenKlop)
        assertFalse(rules.obvezniKlopEnabled)
        assertTrue(rules.obvezniKlopOnRoundScore && rules.obvezniKlopOnZeroOutcome)
        assertEquals(100, rules.obvezniKlopStep)

        // A game saved before partnerPoints existed: the partner got the declarer's points
        val encoded = json.encodeToJsonElement(GameEntry.serializer(), dve(partner = "Bor")).jsonObject
        val withoutField = JsonObject(encoded - "partnerPoints")
        val old = json.decodeFromJsonElement(GameEntry.serializer(), withoutField)
        assertNull(old.partnerPoints)
        assertEquals(35, old.pointsFor("Bor"))

        val split = TarokScorer.processGame(
            dve(partner = "Bor"), table(), GeneralRulesConfig(radlcDoublesPartner = false),
        ).entry
        val roundTrip = json.decodeFromString(GameEntry.serializer(), json.encodeToString(GameEntry.serializer(), split))
        assertEquals(35, roundTrip.partnerPoints)
    }
}
