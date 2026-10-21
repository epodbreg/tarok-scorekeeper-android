package com.epodbreg.stih.logic

import com.epodbreg.stih.model.BonusType
import com.epodbreg.stih.model.GameEntry
import com.epodbreg.stih.model.GameSession
import com.epodbreg.stih.model.HandBonus
import com.epodbreg.stih.model.HandOutcome
import com.epodbreg.stih.model.Player
import com.epodbreg.stih.model.PlayerScore
import com.epodbreg.stih.model.PointsBreakdown
import com.epodbreg.stih.model.TarokContract
import com.epodbreg.stih.model.TarokStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port of TarokStatsTests.swift — these pin the stats rules shared with iOS. */
class TarokStatsTest {

    private val players = listOf("Ana", "Bor", "Cene", "Duša")

    private fun game(
        contract: TarokContract,
        declarer: String,
        partner: String? = null,
        outcome: HandOutcome = HandOutcome.ZMAGAL,
        points: Int,
        bonuses: List<HandBonus> = emptyList(),
        kontra: Int = 0,
        klopScores: Map<String, Int>? = null,
        atSeconds: Long = 0,
    ) = GameEntry(
        contract = contract,
        declarerName = declarer,
        partnerName = partner,
        outcome = outcome,
        pointsBreakdown = PointsBreakdown(gamePoints = points, kontraLevel = kontra),
        bonuses = bonuses,
        totalPoints = points,
        timestamp = (1_000_000 + atSeconds) * 1000,
        klopScores = klopScores,
    )

    private fun session(names: List<String> = players) = GameSession(players = names.map { Player(name = it) })

    @Test
    fun declaredWinsAndWinRate() {
        val games = listOf(
            game(TarokContract.DVE, "Ana", points = 30),
            game(TarokContract.DVE, "Ana", outcome = HandOutcome.IZGUBIL, points = -25),
            game(TarokContract.ENA, "Ana", points = 40),
        )
        val stats = TarokStats.compute(games, players)
        val ana = stats.getValue("Ana")
        assertEquals(3, ana.declared)
        assertEquals(2, ana.declaredWon)
        assertEquals(2.0 / 3.0, ana.winRate, 1e-9)
        assertEquals(45, ana.pointsEarned)
        assertEquals(40, ana.biggestWin?.points)
        assertEquals(-25, ana.worstLoss?.points)
        assertEquals(TarokContract.DVE, ana.favoriteContract)
        assertEquals(3, ana.roundsPlayed)
        assertEquals(3, stats.getValue("Bor").roundsPlayed) // present at the table
        assertEquals(0, stats.getValue("Bor").declared)
    }

    @Test
    fun partnerSlepStats() {
        val games = listOf(
            game(TarokContract.TRI, "Ana", partner = "Bor", points = 20),
            game(TarokContract.DVE, "Cene", partner = "Bor", points = 55),
            game(TarokContract.ENA, "Ana", partner = "Bor", outcome = HandOutcome.IZGUBIL, points = -40),
        )
        val bor = TarokStats.compute(games, players).getValue("Bor")
        assertEquals(3, bor.partnerRounds)
        assertEquals(2, bor.partnerWins)
        assertEquals(55, bor.biggestSlep)
        assertEquals(20 + 55 - 40, bor.pointsEarned)

        // Zarufal partner is NOT a šlep
        val zarufalGame = game(TarokContract.DVE, "Ana", partner = TarokStrings.ZARUFAL, points = 20)
        val stats2 = TarokStats.compute(listOf(zarufalGame), players)
        assertEquals(1, stats2.getValue("Ana").zarufalCount)
        assertTrue(stats2.values.all { it.partnerRounds == 0 })
    }

    @Test
    fun sideEntriesAreNotRounds() {
        val games = listOf(
            game(TarokContract.DVE, "Ana", points = 30),
            game(TarokContract.PENALTY_MOND, "Bor", outcome = HandOutcome.IZGUBIL, points = -20),
            game(TarokContract.PAGAT_BONUS, "Cene", points = 25),
            game(TarokContract.PENALTY_RENONS, "Duša", outcome = HandOutcome.IZGUBIL, points = -20),
        )
        val stats = TarokStats.compute(games, players)
        assertEquals(1, stats.getValue("Ana").roundsPlayed) // only the real round counts
        assertEquals(1, stats.getValue("Bor").mondKazCount)
        assertEquals(0, stats.getValue("Bor").declared) // a penalty is not a declared round
        assertEquals(1, stats.getValue("Cene").pagatAwards)
        assertEquals(1, stats.getValue("Duša").renonsCount)
        assertEquals(-20, stats.getValue("Bor").pointsEarned) // but points still count
    }

    @Test
    fun klopPrazenPolnFromRawScores() {
        val klop = GameEntry(
            contract = TarokContract.KLOP, declarerName = "-", partnerName = null,
            outcome = HandOutcome.IZGUBIL,
            pointsBreakdown = PointsBreakdown(gamePoints = 0, kontraLevel = 0),
            totalPoints = 0,
            klopScores = mapOf("Ana" to 140, "Bor" to -70, "Cene" to -20, "Duša" to 0), // Ana radlc-doubled
            rawKlopScores = mapOf("Ana" to 70, "Bor" to -70, "Cene" to -20, "Duša" to 0),
        )
        val stats = TarokStats.compute(listOf(klop), players)
        assertEquals(1, stats.getValue("Ana").klopPrazen) // raw +70, despite doubled final 140
        assertEquals(1, stats.getValue("Bor").klopPoln)
        assertEquals(0, stats.getValue("Cene").klopPoln)
        assertEquals(140, stats.getValue("Ana").pointsEarned) // points use the final (doubled) score
    }

    @Test
    fun bonusAndKontraTracking() {
        val bonuses = listOf(
            HandBonus(BonusType.TRULA, announced = true, won = true),
            HandBonus(BonusType.PAGAT_ULTIMO, announced = false, won = false),
        )
        val games = listOf(
            game(TarokContract.ENA, "Ana", partner = "Bor", points = 60, bonuses = bonuses, kontra = 2),
            game(TarokContract.VALAT, "Cene", points = 500),
        )
        val stats = TarokStats.compute(games, players)
        assertEquals(1, stats.getValue("Ana").bonusesWon[BonusType.TRULA])
        assertEquals(1, stats.getValue("Ana").bonusesLost[BonusType.PAGAT_ULTIMO])
        assertEquals(1, stats.getValue("Bor").bonusesWon[BonusType.TRULA]) // partner shares the bonus
        assertEquals(1, stats.getValue("Ana").kontraRounds)
        assertEquals(1, stats.getValue("Cene").valati)
    }

    @Test
    fun sessionAggregationAndFunFacts() {
        val base = session()
        val s1 = base.copy(
            games = listOf(
                game(TarokContract.DVE, "Ana", points = 30, atSeconds = 300),
                game(TarokContract.DVE, "Bor", outcome = HandOutcome.IZGUBIL, points = -25, atSeconds = 900),
                game(TarokContract.VALAT, "Ana", points = 500, atSeconds = 1500),
            ),
            endTime = base.startTime + 1_800_000,
            finalScores = listOf(
                PlayerScore("Ana", 530, 1),
                PlayerScore("Bor", -25, 4),
                PlayerScore("Cene", 0, 2),
                PlayerScore("Duša", 0, 3),
            ),
        )
        val s2 = session().copy(
            games = listOf(game(TarokContract.ENA, "Ana", points = 40)),
            finalScores = listOf(
                PlayerScore("Ana", 40, 1),
                PlayerScore("Bor", 0, 2),
                PlayerScore("Cene", 0, 3),
                PlayerScore("Duša", 0, 4),
            ),
        )

        val agg = TarokStats.compute(listOf(s1, s2))
        assertEquals(2, agg.getValue("Ana").sessionsPlayed)
        assertEquals(2, agg.getValue("Ana").sessionsWon)
        assertEquals(3, agg.getValue("Ana").declared)
        assertEquals(1, agg.getValue("Bor").podiums) // position 2 in s2
        assertEquals(500, agg.getValue("Ana").biggestWin?.points)

        val facts = TarokStats.funFacts(s1)
        assertEquals(3, facts.roundCount)
        assertEquals(TarokContract.DVE, facts.mostPlayedContract?.first)
        assertEquals(TarokContract.VALAT, facts.roundOfTheNight?.contract)
        assertEquals("Ana", facts.bestSingleRound?.name)
        assertEquals(500, facts.bestSingleRound?.points)
        assertEquals(TarokContract.VALAT, facts.bestSingleRound?.contract)
        assertEquals(1_800_000L, facts.durationMs)
    }

    @Test
    fun kontraCallerAndLostSlepi() {
        fun kontraGame(declarer: String, partner: String?, caller: String, won: Boolean) = GameEntry(
            contract = TarokContract.DVE, declarerName = declarer, partnerName = partner,
            outcome = if (won) HandOutcome.ZMAGAL else HandOutcome.IZGUBIL,
            pointsBreakdown = PointsBreakdown(gamePoints = if (won) 40 else -40, kontraLevel = 1),
            totalPoints = if (won) 40 else -40,
            kontraCallerName = caller,
        )
        val games = listOf(
            kontraGame("Ana", "Bor", "Cene", won = false), // Cene's kontra paid off
            kontraGame("Ana", "Bor", "Cene", won = true), // backfired
            kontraGame("Duša", null, "Ana", won = false),
        )
        val stats = TarokStats.compute(games, players)
        assertEquals(2, stats.getValue("Cene").kontrasCalled)
        assertEquals(1, stats.getValue("Cene").kontrasWon)
        assertEquals(1, stats.getValue("Ana").kontrasCalled)
        assertEquals(1, stats.getValue("Ana").kontrasWon)
        assertEquals(2, stats.getValue("Ana").kontraRounds) // kontra'd against her as declarer
        assertEquals(2, stats.getValue("Bor").partnerRounds)
        assertEquals(1, stats.getValue("Bor").partnerLosses) // izgubljeni šlep
    }

    @Test
    fun renameAndMergeAcrossSession() {
        val s = session(listOf("Elias", "Nina", "Elisa")).copy(
            games = listOf(
                game(TarokContract.DVE, "Elisa", partner = "Nina", points = 30),
                GameEntry(
                    contract = TarokContract.KLOP, declarerName = "-", partnerName = null,
                    outcome = HandOutcome.IZGUBIL,
                    pointsBreakdown = PointsBreakdown(gamePoints = 0, kontraLevel = 0),
                    totalPoints = 0,
                    klopScores = mapOf("Elisa" to 70, "Nina" to -20, "Elias" to -10),
                ),
            ),
            finalScores = listOf(PlayerScore("Elisa", 100, 1)),
        )

        // "Elisa" → "Ela": plain rename, no conflict
        assertFalse(s.hasConflict("Elisa", "Ela"))
        val renamed = s.renamingPlayer("Elisa", "Ela")
        assertTrue("Ela" in renamed.playerNames)
        assertFalse("Elisa" in renamed.playerNames)
        assertEquals("Ela", renamed.games[0].declarerName)
        assertEquals(70, renamed.games[1].klopScores?.get("Ela"))
        assertEquals(70, renamed.games[1].rawKlopScores?.get("Ela"))
        assertEquals("Ela", renamed.finalScores?.first()?.name)
        assertEquals(s.games[0].id, renamed.games[0].id) // identity preserved

        // "Elisa" → "Elias" while both sat at this table: must be refused
        assertTrue(s.hasConflict("Elisa", "Elias"))
    }

    /** Game timestamps relative to session start → verify round-length math. */
    @Test
    fun roundLengths() {
        val base = session()
        fun entry(contract: TarokContract, minutes: Long) = GameEntry(
            contract = contract, declarerName = "Ana", partnerName = null, outcome = HandOutcome.ZMAGAL,
            pointsBreakdown = PointsBreakdown(gamePoints = 10, kontraLevel = 0),
            totalPoints = 10,
            timestamp = base.startTime + minutes * 60_000,
        )
        val s = base.copy(
            games = listOf(entry(TarokContract.TRI, 4), entry(TarokContract.DVE, 10), entry(TarokContract.ENA, 25)),
        )
        val facts = TarokStats.funFacts(s)
        // Rounds of 4, 6 and 15 min → average 25/3 min
        assertNotNull(facts.avgRoundLengthMs)
        assertEquals(25.0 * 60_000 / 3, facts.avgRoundLengthMs!!, 0.01)
    }

    @Test
    fun winningStreak() {
        fun at(g: GameEntry, i: Long) = g.copy(timestamp = i * 1000)
        val games = listOf(
            at(game(TarokContract.TRI, "Ana", points = 20), 1),
            at(game(TarokContract.DVE, "Bor", points = 30), 2), // Ana sat out — streak survives
            at(game(TarokContract.ENA, "Cene", partner = "Ana", points = 40), 3),
            at(
                GameEntry(
                    contract = TarokContract.KLOP, declarerName = "-", partnerName = null,
                    outcome = HandOutcome.IZGUBIL,
                    pointsBreakdown = PointsBreakdown(gamePoints = 0, kontraLevel = 0),
                    totalPoints = 0,
                    klopScores = mapOf("Ana" to 70, "Bor" to -20, "Cene" to -30, "Duša" to 0),
                ),
                4,
            ),
            at(game(TarokContract.PENALTY_MOND, "Ana", outcome = HandOutcome.IZGUBIL, points = -20), 5), // side entry: ignored
            at(game(TarokContract.DVE, "Ana", outcome = HandOutcome.IZGUBIL, points = -30), 6), // minus breaks it
            at(game(TarokContract.TRI, "Ana", points = 20), 7),
        )
        val s = session().copy(games = games)
        val facts = TarokStats.funFacts(s)
        assertEquals("Ana", facts.longestStreak?.name)
        assertEquals(3, facts.longestStreak?.count) // +20, +40 (šlep), +70 (klop)

        // Only 2 in a row → below the threshold, no chip
        assertNull(TarokStats.funFacts(s.copy(games = games.take(3))).longestStreak)
    }
}
