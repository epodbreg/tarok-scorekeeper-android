package com.epodbreg.stih.logic

import com.epodbreg.stih.model.CustomScoringRules
import com.epodbreg.stih.model.GameEntry
import com.epodbreg.stih.model.GeneralRulesConfig
import com.epodbreg.stih.model.HandOutcome
import com.epodbreg.stih.model.Player
import com.epodbreg.stih.model.PointsBreakdown
import com.epodbreg.stih.model.TarokContract
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port of KlopRulesTests.swift: klop and radlci, and the "prazen klop" switch. */
class KlopRulesTest {

    private fun klop(scores: Map<String, Int>) = GameEntry(
        contract = TarokContract.KLOP, declarerName = "", partnerName = null, outcome = HandOutcome.ZMAGAL,
        pointsBreakdown = PointsBreakdown(gamePoints = 0, kontraLevel = 0), totalPoints = 0,
        klopScores = scores, rawKlopScores = scores,
    )

    /** Ana has one unused radlc and plays prazen; Bor has one unused radlc and loses 20; Cene has none */
    private fun table() = listOf(
        Player(name = "Ana", radlci = listOf(false)),
        Player(name = "Bor", radlci = listOf(false)),
        Player(name = "Cene"),
    )

    private val scores = mapOf("Ana" to 70, "Bor" to -20, "Cene" to -10)

    @Test
    fun praznKlopIsDoubledAndUsesTheRadlc() {
        val result = TarokScorer.processGame(klop(scores), table(), GeneralRulesConfig())

        assertEquals(mapOf("Ana" to 140, "Bor" to -40, "Cene" to -10), result.entry.klopScores)
        assertEquals(listOf(true, false), result.players[0].radlci)   // used up, plus the new one from this klop
        assertEquals(listOf(false, false), result.players[1].radlci)  // a loss never uses a radlc
        assertEquals(listOf(false), result.players[2].radlci)
    }

    @Test
    fun radlcCanLeavePrazenKlopAlone() {
        val rules = GeneralRulesConfig(radlcSkipsPrazenKlop = true)
        val result = TarokScorer.processGame(klop(scores), table(), rules)

        // prazen not doubled, the rest still is
        assertEquals(mapOf("Ana" to 70, "Bor" to -40, "Cene" to -10), result.entry.klopScores)
        assertEquals(listOf(false, false), result.players[0].radlci)  // radlc kept, and klop still gives one
        assertEquals(listOf(false, false), result.players[1].radlci)
    }

    @Test
    fun klopBrezRadlcaNeitherDoublesNorUses() {
        val rules = GeneralRulesConfig(radlcDoublesKlop = false)
        val result = TarokScorer.processGame(klop(scores), table(), rules)

        assertEquals(scores, result.entry.klopScores)
        assertEquals(listOf(false, false), result.players[0].radlci)
    }

    @Test
    fun oldRuleDataKeepsTodaysKlop() {
        val json = Json { ignoreUnknownKeys = true }
        val decoded = json.decodeFromString<GeneralRulesConfig>("""{"radlcDoublesKlop": true, "renonsPenalty": 30}""")
        assertFalse(decoded.radlcSkipsPrazenKlop)
        assertEquals(30, decoded.renonsPenalty)

        val rules = CustomScoringRules(name = "Domača").let {
            it.copy(generalRules = it.generalRules.copy(radlcSkipsPrazenKlop = true))
        }
        val roundTrip = json.decodeFromString<CustomScoringRules>(Json.encodeToString(CustomScoringRules.serializer(), rules))
        assertTrue(roundTrip.generalRules.radlcSkipsPrazenKlop)
    }
}
