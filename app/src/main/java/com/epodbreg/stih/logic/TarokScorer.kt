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

        // Pagat quick-award: an unused radlc doubles it (when the rule set allows)
        // but is NEVER consumed — only the declarer of a real played game uses one up.
        if (baseEntry.contract == TarokContract.PAGAT_BONUS) {
            val declarer = players.firstOrNull { it.name == baseEntry.declarerName }
            if (!rules.radlcDoublesPagat || declarer == null || !declarer.hasUnusedRadlci) {
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
        var doubled = false
        val decIdx = mutablePlayers.indexOfFirst { it.name == baseEntry.declarerName }
        if (decIdx >= 0) {
            val declarer = mutablePlayers[decIdx]
            val radlcIdx = declarer.radlci.indexOfFirst { !it }
            if (declarer.radlci.isNotEmpty() && radlcIdx >= 0) {
                finalPoints = baseEntry.totalPoints * 2
                doubled = true
                // Only consume the radlc on a win
                if (baseEntry.outcome == HandOutcome.ZMAGAL || baseEntry.outcome == HandOutcome.ZMAGAL_SLEPAL) {
                    val updated = declarer.radlci.toMutableList().also { it[radlcIdx] = true }
                    mutablePlayers[decIdx] = declarer.copy(radlci = updated)
                }
            }
        }

        // The declarer's radlc doubles the partner too — unless the table plays it for the declarer only
        val partnerPoints = if (doubled && !rules.radlcDoublesPartner && baseEntry.actualPartner != null) {
            baseEntry.totalPoints
        } else null

        // Grant new radlci after scoring
        if (givesRadlc) {
            for (idx in mutablePlayers.indices) {
                mutablePlayers[idx] = mutablePlayers[idx].copy(
                    radlci = mutablePlayers[idx].radlci + false
                )
            }
        }

        // copy() so every other field (e.g. kontraCallerName) survives scoring
        val entry = baseEntry.copy(
            pointsBreakdown = PointsBreakdown(
                gamePoints = finalPoints,
                difference = baseEntry.pointsBreakdown.difference,
                kontraLevel = baseEntry.pointsBreakdown.kontraLevel,
            ),
            totalPoints = finalPoints,
            klopScores = null,
            rawTotalPoints = baseEntry.totalPoints,
            partnerPoints = partnerPoints,
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

        // Score first, using existing (old) radlci. When radlcDoublesKlop is off, radlci
        // neither double nor get used; radlcSkipsPrazenKlop leaves only the prazen alone.
        for (idx in mutablePlayers.indices) {
            val player = mutablePlayers[idx]
            val rawScore = rawKlopScores[player.name] ?: 0
            var adjustedScore = rawScore

            val isPrazen = rawScore == 70
            val radlcIdx = player.radlci.indexOfFirst { !it }
            if (rules.radlcDoublesKlop && rawScore != 0 && !(isPrazen && rules.radlcSkipsPrazenKlop) &&
                player.radlci.isNotEmpty() && radlcIdx >= 0
            ) {
                adjustedScore = rawScore * 2
                // A prazen klop (the win) uses the radlc up
                if (isPrazen) {
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
