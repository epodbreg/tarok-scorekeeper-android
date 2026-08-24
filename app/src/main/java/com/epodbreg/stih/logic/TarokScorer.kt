package com.epodbreg.stih.logic

import com.epodbreg.stih.model.GameEntry
import com.epodbreg.stih.model.GeneralRulesConfig
import com.epodbreg.stih.model.HandOutcome
import com.epodbreg.stih.model.Player
import com.epodbreg.stih.model.PointsBreakdown
import com.epodbreg.stih.model.TarokContract

/**
 * Handles radlc doubling and radlc granting when a game is scored.
 * Direct port of TarokScorer.swift: the entry comes in with its total already
 * calculated by the stepper; this only applies radlc doubling and grants new radlci.
 */
object TarokScorer {

    val radlcGivingContracts: Set<TarokContract> = setOf(
        TarokContract.KLOP, TarokContract.BERAC, TarokContract.SOLO_BREZ,
        TarokContract.ODPRTI_BERAC, TarokContract.BARVNI_VALAT, TarokContract.VALAT,
    )

    /** Result of processing: the final entry plus the updated player list. */
    data class Result(val entry: GameEntry, val players: List<Player>)

    fun processGame(
        baseEntry: GameEntry,
        players: List<Player>,
        rules: GeneralRulesConfig = GeneralRulesConfig(),
    ): Result {
        if (baseEntry.contract == TarokContract.KLOP) {
            return processKlopGame(baseEntry, players, rules)
        }

        // Mond kazen and renons: never doubled, never give or consume radlci
        if (baseEntry.contract == TarokContract.PENALTY_MOND ||
            baseEntry.contract == TarokContract.PENALTY_RENONS
        ) {
            return Result(baseEntry, players)
        }

        // Pagat quick-award: an unused radlc doubles it but is NEVER consumed —
        // a radlc is only used up by the declarer of a real played game.
        if (baseEntry.contract == TarokContract.PAGAT_BONUS) {
            val declarer = players.firstOrNull { it.name == baseEntry.declarerName }
            if (declarer == null || !declarer.hasUnusedRadlci) {
                return Result(baseEntry, players)
            }
            val doubled = baseEntry.totalPoints * 2
            return Result(
                baseEntry.copy(
                    totalPoints = doubled,
                    rawTotalPoints = baseEntry.totalPoints,
                    pointsBreakdown = baseEntry.pointsBreakdown.copy(gamePoints = doubled),
                ),
                players,
            )
        }

        val mutablePlayers = players.toMutableList()
        val givesRadlc = baseEntry.contract in radlcGivingContracts
        var finalPoints = baseEntry.totalPoints

        // Radlc logic: double with an existing radlc before granting new ones.
        // Any non-penalty game can be doubled — givesRadlc only controls granting.
        val decIdx = mutablePlayers.indexOfFirst { it.name == baseEntry.declarerName }
        if (decIdx >= 0) {
            val declarer = mutablePlayers[decIdx]
            val radlcIdx = declarer.radlci.indexOfFirst { !it }
            if (declarer.radlci.isNotEmpty() && radlcIdx >= 0) {
                finalPoints = baseEntry.totalPoints * 2
                // Only consume the radlc on a win
                if (baseEntry.outcome == HandOutcome.ZMAGAL || baseEntry.outcome == HandOutcome.ZMAGAL_SLEPAL) {
                    val updated = declarer.radlci.toMutableList().also { it[radlcIdx] = true }
                    mutablePlayers[decIdx] = declarer.copy(radlci = updated)
                }
            }
        }

        // Grant new radlci after scoring
        if (givesRadlc) {
            for (idx in mutablePlayers.indices) {
                mutablePlayers[idx] = mutablePlayers[idx].copy(
                    radlci = mutablePlayers[idx].radlci + false
                )
            }
        }

        val entry = GameEntry(
            id = baseEntry.id,
            contract = baseEntry.contract,
            declarerName = baseEntry.declarerName,
            partnerName = baseEntry.partnerName,
            outcome = baseEntry.outcome,
            pointsBreakdown = PointsBreakdown(
                gamePoints = finalPoints,
                difference = baseEntry.pointsBreakdown.difference,
                kontraLevel = baseEntry.pointsBreakdown.kontraLevel,
            ),
            bonuses = baseEntry.bonuses,
            totalPoints = finalPoints,
            timestamp = baseEntry.timestamp,
            klopScores = null,
            rawKlopScores = baseEntry.rawKlopScores,
            rawTotalPoints = baseEntry.totalPoints,
            underlyingGame = baseEntry.underlyingGame,
            isValatAnnounced = baseEntry.isValatAnnounced,
        )
        return Result(entry, mutablePlayers)
    }

    private fun processKlopGame(
        baseEntry: GameEntry,
        players: List<Player>,
        rules: GeneralRulesConfig,
    ): Result {
        val rawKlopScores = baseEntry.rawKlopScores ?: baseEntry.klopScores
            ?: return Result(baseEntry, players)

        val mutablePlayers = players.toMutableList()
        val finalScores = mutableMapOf<String, Int>()

        // Score first, using existing (old) radlci. "Klop brez radlca":
        // when radlcDoublesKlop is off, radlci neither double nor get consumed.
        for (idx in mutablePlayers.indices) {
            val player = mutablePlayers[idx]
            val rawScore = rawKlopScores[player.name] ?: 0
            var adjustedScore = rawScore

            val radlcIdx = player.radlci.indexOfFirst { !it }
            if (rules.radlcDoublesKlop && rawScore != 0 && player.radlci.isNotEmpty() && radlcIdx >= 0) {
                adjustedScore = rawScore * 2
                // Only cancel the radlc on a win (prazen klop)
                if (rawScore == 70) {
                    val updated = player.radlci.toMutableList().also { it[radlcIdx] = true }
                    mutablePlayers[idx] = player.copy(radlci = updated)
                }
            }
            finalScores[player.name] = adjustedScore
        }

        // Grant new radlci after scoring (for future games)
        for (idx in mutablePlayers.indices) {
            mutablePlayers[idx] = mutablePlayers[idx].copy(
                radlci = mutablePlayers[idx].radlci + false
            )
        }

        val entry = GameEntry(
            id = baseEntry.id,
            contract = TarokContract.KLOP,
            declarerName = "-",
            partnerName = null,
            outcome = HandOutcome.IZGUBIL,
            pointsBreakdown = baseEntry.pointsBreakdown,
            bonuses = emptyList(),
            totalPoints = 0,
            timestamp = baseEntry.timestamp,
            klopScores = finalScores,
            rawKlopScores = baseEntry.rawKlopScores,
            rawTotalPoints = baseEntry.rawTotalPoints,
        )
        return Result(entry, mutablePlayers)
    }
}
