package com.epodbreg.stih.logic

import com.epodbreg.stih.model.GameEntry
import com.epodbreg.stih.model.GeneralRulesConfig
import com.epodbreg.stih.model.HandOutcome
import com.epodbreg.stih.model.Player
import com.epodbreg.stih.model.PointsBreakdown
import com.epodbreg.stih.model.TableSize
import com.epodbreg.stih.model.TarokContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Port of MorePlayersTests.swift. Tables of 6 to 10: some sit a round out, the app doesn't
 * track who. In klop an empty row means "didn't play" and scores 0; everyone still gets radlci.
 */
class MorePlayersTest {

    private fun table(count: Int, radlci: List<Boolean> = emptyList()) =
        (1..count).map { Player(name = "P$it", radlci = radlci) }

    private fun klop(scores: Map<String, Int>) = GameEntry(
        contract = TarokContract.KLOP, declarerName = "", partnerName = null, outcome = HandOutcome.ZMAGAL,
        pointsBreakdown = PointsBreakdown(gamePoints = 0, kontraLevel = 0), totalPoints = 0,
        klopScores = scores, rawKlopScores = scores,
    )

    private fun klopLeavesThoseWhoSatOutAtZero(count: Int) {
        val players = table(count, radlci = listOf(false))
        // Four played; the stepper saves an empty row as 0
        val scores = players.associate { it.name to 0 } + mapOf("P1" to 70, "P2" to -30, "P3" to -25, "P4" to -15)

        val result = TarokScorer.processGame(klop(scores), players, GeneralRulesConfig())

        assertEquals(140, result.entry.klopScores?.get("P1"))
        assertEquals(-60, result.entry.klopScores?.get("P2"))
        result.players.drop(4).forEach { sittingOut ->
            assertEquals(0, result.entry.klopScores?.get(sittingOut.name))
            assertEquals(listOf(false, false), sittingOut.radlci)   // radlc untouched, and klop still gives one
        }
        assertEquals(listOf(true, false), result.players[0].radlci)  // the prazen klop used its radlc
    }

    @Test fun klopWithSix() = klopLeavesThoseWhoSatOutAtZero(6)

    @Test fun klopWithTen() = klopLeavesThoseWhoSatOutAtZero(10)

    @Test
    fun aRadlcGameGivesAllTenARadlc() {
        val valat = GameEntry(
            contract = TarokContract.VALAT, declarerName = "P1", partnerName = "P2", outcome = HandOutcome.ZMAGAL,
            pointsBreakdown = PointsBreakdown(gamePoints = 0, kontraLevel = 0), totalPoints = 0,
        )
        val result = TarokScorer.processGame(valat, table(10), GeneralRulesConfig())
        assertTrue(result.players.all { it.radlci == listOf(false) })
    }

    @Test
    fun sevenNamesSeatSeven() {
        val names = (1..7).map { "P$it" }
        val (seats, count) = TableSize.seating(names)
        assertEquals(7, count)
        assertEquals(names, seats.take(7))
        assertEquals(TableSize.MAX, seats.size)
    }

    @Test
    fun seatingStaysBetweenThreeAndTen() {
        assertEquals(3, TableSize.seating(listOf("A")).second)
        val (seats, count) = TableSize.seating((1..12).map { "P$it" })
        assertEquals(10, count)
        assertEquals("P10", seats.last())
    }
}
