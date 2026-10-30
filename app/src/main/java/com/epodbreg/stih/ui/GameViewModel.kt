package com.epodbreg.stih.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.epodbreg.stih.data.SessionManager
import com.epodbreg.stih.data.SettingsManager
import com.epodbreg.stih.logic.Standings
import com.epodbreg.stih.logic.TarokScorer
import com.epodbreg.stih.model.GameEntry
import com.epodbreg.stih.model.GameSession
import com.epodbreg.stih.model.Player
import com.epodbreg.stih.model.TarokContract
import java.util.UUID

/**
 * Live game state. Port of GameViewModel.swift.
 */
class GameViewModel(
    val sessionManager: SessionManager,
    val settings: SettingsManager,
    initialPlayers: List<Player>,
) : ViewModel() {

    var players: List<Player> by mutableStateOf(initialPlayers)
    var gameHistory: List<GameEntry> by mutableStateOf(emptyList())
    var sessionStartTime: Long by mutableStateOf(System.currentTimeMillis())
    var obvezniKlopTrigger: String? by mutableStateOf(null)

    /**
     * Manual dealer adjustment. The dealer is DERIVED, not stored:
     * (dealerManualOffset + count of dealer-advancing games) mod playerCount,
     * so delete, re-add, edit, and undo all stay correct automatically.
     */
    var dealerManualOffset: Int by mutableStateOf(0)
        private set

    private var originalPlayerOrder: List<Player> = initialPlayers

    /** Played rounds — side entries (penalties/awards) excluded. */
    val roundCount: Int
        get() = gameHistory.count { !it.contract.isSideEntry }

    /** Only real rounds rotate the deal. */
    private val dealerAdvancingCount: Int
        get() = roundCount

    val currentDealerIndex: Int
        get() {
            val n = originalPlayerOrder.size
            if (n == 0) return 0
            return Math.floorMod(dealerManualOffset + dealerAdvancingCount, n)
        }

    fun restoreSessionIfNeeded() {
        var existing = sessionManager.activeSession ?: return
        if (existing.players.isEmpty()) return
        // A game from before 1.9 has no rules of its own: it keeps today's from now on
        if (existing.rules == null) {
            existing = existing.copy(rules = settings.currentRuleSet)
            sessionManager.updateActiveSession(existing)
        }

        players = existing.players
        originalPlayerOrder = existing.players
        gameHistory = existing.games
        sessionStartTime = existing.startTime
        // Migration: old sessions stored only the dealer index — back-solve the
        // offset so the dealer doesn't jump.
        val n = existing.players.size
        val advancing = existing.roundCount
        dealerManualOffset = existing.dealerManualOffset
            ?: Math.floorMod(existing.currentDealerIndex - advancing, n)
        recalculate(save = false)
    }

    private fun saveCurrentState() {
        val session = sessionManager.activeSession ?: return
        sessionManager.updateActiveSession(
            session.copy(
                players = players,
                games = gameHistory,
                startTime = sessionStartTime,
                currentDealerIndex = currentDealerIndex,
                dealerManualOffset = dealerManualOffset,
            )
        )
    }

    /**
     * Single source-of-truth recalculation. Resets all scores and radlci,
     * reprocesses every game chronologically, rebuilds gameHistory.
     */
    fun recalculate(save: Boolean = true) {
        var currentPlayers = players.map { it.copy(score = 0, radlci = emptyList()) }

        val newEntries = mutableListOf<GameEntry>()
        for (oldGame in gameHistory.sortedBy { it.timestamp }) {
            // Use raw (pre-radlc-doubling) values so TarokScorer can re-apply
            // doubling against the current radlci state.
            val baseGame = oldGame.copy(
                totalPoints = oldGame.rawTotalPoints,
                klopScores = oldGame.rawKlopScores,
                partnerPoints = null,
            )
            val result = TarokScorer.processGame(baseGame, currentPlayers, settings.rules.generalRules)
            currentPlayers = applyGameScore(result.entry, result.players, add = true)
            newEntries.add(result.entry)
        }
        players = currentPlayers
        gameHistory = newEntries

        if (save) saveCurrentState()
    }

    fun toggleRadlc(playerId: UUID, index: Int) {
        players = players.map { player ->
            if (player.id == playerId && index in player.radlci.indices) {
                player.copy(radlci = player.radlci.toMutableList().also { it[index] = !it[index] })
            } else player
        }
        saveCurrentState()
    }

    /** Add a game entry that has already gone through TarokScorer. */
    fun addProcessedGameEntry(
        entry: GameEntry,
        updatedPlayers: List<Player>,
        checkObvezniKlop: Boolean = true,
    ) {
        // RAW scores before this game: the trigger follows the big displayed
        // number, not the radlc-adjusted one
        val previousScores = if (checkObvezniKlop) captureScores() else null

        gameHistory = gameHistory + entry
        players = applyGameScore(entry, updatedPlayers, add = true)
        saveCurrentState() // dealer is derived from history — no explicit advance

        previousScores?.let { checkObvezniKlop(it, entry) }
    }

    /** Restore a previously deleted game entry (undo). Radlci must be restored by the caller first. */
    fun addRawGameEntry(entry: GameEntry) {
        gameHistory = gameHistory + entry
        players = applyGameScore(entry, players, add = true)
        saveCurrentState()
    }

    fun setPlayersDirect(newPlayers: List<Player>) {
        players = newPlayers
    }

    fun setGameHistoryDirect(games: List<GameEntry>) {
        gameHistory = games
    }

    /** Display-only: raw score minus the end-of-session penalty for unused radlci. */
    fun liveNetScore(player: Player): Int {
        val rules = settings.rules.generalRules
        if (!rules.radlcPenaltyEnabled) return player.score
        return player.score - player.unusedRadlciCount * rules.radlcPenaltyPerUnused
    }

    private fun checkObvezniKlop(previousScores: Map<String, Int>, game: GameEntry) {
        val rules = settings.rules.generalRules
        if (!rules.obvezniKlopEnabled) return

        var triggerPlayer: Player? = null

        // Trigger 1: any player's RAW score changed to a non-negative
        // multiple of the configured step (zero counts)
        val step = rules.klopStep
        if (rules.obvezniKlopOnRoundScore) {
            triggerPlayer = players.firstOrNull { player ->
                val prev = previousScores[player.name] ?: player.score
                val curr = player.score
                curr != prev && curr >= 0 && curr % step == 0
            }
        }

        // Trigger 2: the game itself totalled exactly 0 (klop and side entries excluded)
        if (triggerPlayer == null && rules.obvezniKlopOnZeroOutcome) {
            val isRealGame = game.contract != TarokContract.KLOP && !game.contract.isSideEntry
            if (isRealGame && game.totalPoints == 0) {
                triggerPlayer = players.firstOrNull { it.name == game.declarerName }
            }
        }

        triggerPlayer?.let { obvezniKlopTrigger = it.name }
    }

    fun clearObvezniKlopTrigger() {
        obvezniKlopTrigger = null
    }

    fun removeGameEntry(id: UUID) {
        gameHistory = gameHistory.filterNot { it.id == id }
        recalculate()
    }

    fun updateGameEntry(updated: GameEntry) {
        val idx = gameHistory.indexOfFirst { it.id == updated.id }
        if (idx >= 0) {
            val originalTimestamp = gameHistory[idx].timestamp
            gameHistory = gameHistory.toMutableList().also {
                it[idx] = updated.copy(timestamp = originalTimestamp)
            }
        }
        recalculate()
    }

    private fun applyGameScore(entry: GameEntry, basePlayers: List<Player>, add: Boolean): List<Player> {
        val multiplier = if (add) 1 else -1

        if (entry.contract == TarokContract.KLOP) {
            val scores = entry.klopScores ?: return basePlayers
            return basePlayers.map { player ->
                val delta = scores[player.name] ?: 0
                player.copy(score = player.score + delta * multiplier)
            }
        }

        // The partner's points can differ from the declarer's (radlc doubling only the declarer)
        return basePlayers.map { player ->
            val isDeclarer = player.name == entry.declarerName
            val isPartner = player.name == entry.actualPartner
            if (isDeclarer || isPartner) {
                player.copy(score = player.score + entry.pointsFor(player.name) * multiplier)
            } else player
        }
    }

    /** The game as it was before [endSession], so leaving the summary can reopen it */
    private var sessionBeforeEnd: GameSession? = null

    /** Back from the summary: the game continues as if it had never been ended (like iOS) */
    fun reopenEndedSession(): Boolean {
        val session = sessionBeforeEnd ?: return false
        sessionBeforeEnd = null
        sessionManager.setActiveSessionDirect(session)
        restoreSessionIfNeeded()
        return true
    }

    fun endSession(): GameSession? {
        sessionBeforeEnd = sessionManager.activeSession
        val session = sessionManager.endCurrentSession() ?: return null

        // Preserve original radlci for session history
        val originalRadlci = players.map { it.radlci }

        // The game's own rules: ending it has already cleared the rules in play
        val rules = (session.rules ?: settings.currentRuleSet).generalRules
        val endedPlayers = players.map { player ->
            val penalty = if (rules.radlcPenaltyEnabled) {
                player.unusedRadlciCount * rules.radlcPenaltyPerUnused
            } else 0
            player.copy(score = player.score - penalty, radlci = emptyList())
        }

        val sorted = endedPlayers.sortedByDescending { it.score }
        val finalScores = sorted.mapIndexed { idx, player ->
            com.epodbreg.stih.model.PlayerScore(player.name, player.score, idx + 1)
        }

        val completedSession = session.copy(
            finalScores = finalScores,
            games = gameHistory,
            startTime = sessionStartTime,
            endTime = System.currentTimeMillis(),
            // Keep radlci for history but use final (penalised) scores
            players = players.mapIndexed { idx, player ->
                player.copy(radlci = originalRadlci[idx], score = endedPlayers[idx].score)
            },
        )

        players = endedPlayers
        return completedSession
    }

    fun advanceDealer() {
        val n = originalPlayerOrder.size
        if (n == 0) return
        dealerManualOffset = Math.floorMod(dealerManualOffset + 1, n)
        saveCurrentState()
    }

    fun reverseDealer() {
        val n = originalPlayerOrder.size
        if (n == 0) return
        dealerManualOffset = Math.floorMod(dealerManualOffset - 1, n)
        saveCurrentState()
    }

    fun setDealer(index: Int) {
        val n = originalPlayerOrder.size
        if (index !in 0 until n) return
        dealerManualOffset = Math.floorMod(index - dealerAdvancingCount, n)
        saveCurrentState()
    }

    fun getCurrentDealerName(): String {
        if (originalPlayerOrder.isEmpty() || currentDealerIndex !in originalPlayerOrder.indices) return "?"
        return originalPlayerOrder[currentDealerIndex].name
    }

    /** Best score first, ties in seat order; equal scores share a place (0, 0, 2) */
    val standings: List<Pair<Player, Int>>
        get() = Standings.of(players)

    val slepaci: Set<String>
        get() = GameSession.computeSlepaci(gameHistory)

    fun captureScores(): Map<String, Int> = players.associate { it.name to it.score }
}
