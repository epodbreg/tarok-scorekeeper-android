package com.epodbreg.stih.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.epodbreg.stih.data.SessionManager
import com.epodbreg.stih.data.SettingsManager
import com.epodbreg.stih.logic.TarokScorer
import com.epodbreg.stih.model.GameEntry
import com.epodbreg.stih.model.GameSession
import com.epodbreg.stih.model.Player
import com.epodbreg.stih.model.TarokContract
import com.epodbreg.stih.model.TarokStrings
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
    var currentDealerIndex: Int by mutableStateOf(0)
    var sessionStartTime: Long by mutableStateOf(System.currentTimeMillis())
    var obvezniKlopTrigger: String? by mutableStateOf(null)

    private var originalPlayerOrder: List<Player> = initialPlayers

    fun restoreSessionIfNeeded() {
        val existing = sessionManager.activeSession ?: return
        if (existing.players.isEmpty()) return

        players = existing.players
        originalPlayerOrder = existing.players
        gameHistory = existing.games
        sessionStartTime = existing.startTime
        currentDealerIndex = existing.currentDealerIndex
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
            )
            val result = TarokScorer.processGame(baseGame, currentPlayers)
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
        advancesDealer: Boolean = true,
    ) {
        val prevLiveNets = if (checkObvezniKlop) {
            players.associate { it.name to liveNetScore(it) }
        } else null

        gameHistory = gameHistory + entry
        players = applyGameScore(entry, updatedPlayers, add = true)
        if (advancesDealer) advanceDealer() else saveCurrentState()

        prevLiveNets?.let { checkObvezniKlop(it, entry) }
    }

    /** Restore a previously deleted game entry (undo). Radlci must be restored by the caller first. */
    fun addRawGameEntry(entry: GameEntry) {
        gameHistory = gameHistory + entry
        players = applyGameScore(entry, players, add = true)
        advanceDealer()
    }

    fun setPlayersDirect(newPlayers: List<Player>) {
        players = newPlayers
    }

    fun setGameHistoryDirect(games: List<GameEntry>) {
        gameHistory = games
    }

    /** Live net score = raw score minus penalty for unused radlci. */
    fun liveNetScore(player: Player): Int {
        val rules = settings.currentRuleSet.generalRules
        if (!rules.radlcPenaltyEnabled) return player.score
        return player.score - player.unusedRadlciCount * rules.radlcPenaltyPerUnused
    }

    private fun checkObvezniKlop(previousLiveNetScores: Map<String, Int>, game: GameEntry) {
        if (!settings.obvezniKlopEnabled) return

        var triggerPlayer: Player? = null

        // Trigger 1: any player's live net score changed to a non-negative multiple of 100
        if (settings.obvezniKlopOnRoundScore) {
            triggerPlayer = players.firstOrNull { player ->
                val prev = previousLiveNetScores[player.name] ?: player.score
                val curr = liveNetScore(player)
                curr != prev && curr >= 0 && curr % 100 == 0
            }
        }

        // Trigger 2: the game itself totalled exactly 0 (exclude klop and penalty games)
        if (triggerPlayer == null && settings.obvezniKlopOnZeroOutcome) {
            val isRealGame = game.contract !in setOf(
                TarokContract.KLOP, TarokContract.PENALTY_MOND,
                TarokContract.PENALTY_RENONS, TarokContract.PAGAT_BONUS,
            )
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

        return basePlayers.map { player ->
            val isDeclarer = player.name == entry.declarerName
            val isPartner = entry.partnerName != null &&
                entry.partnerName != TarokStrings.ZARUFAL &&
                player.name == entry.partnerName &&
                entry.partnerName != entry.declarerName
            if (isDeclarer || isPartner) {
                player.copy(score = player.score + entry.totalPoints * multiplier)
            } else player
        }
    }

    fun endSession(): GameSession? {
        val session = sessionManager.endCurrentSession() ?: return null

        // Preserve original radlci for session history
        val originalRadlci = players.map { it.radlci }

        val rules = settings.currentRuleSet.generalRules
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
        if (originalPlayerOrder.isEmpty()) {
            currentDealerIndex = 0
        } else {
            currentDealerIndex = (currentDealerIndex + 1) % originalPlayerOrder.size
        }
        saveCurrentState()
    }

    fun reverseDealer() {
        if (originalPlayerOrder.isEmpty()) return
        currentDealerIndex =
            (currentDealerIndex - 1 + originalPlayerOrder.size) % originalPlayerOrder.size
        saveCurrentState()
    }

    fun setDealer(index: Int) {
        if (index !in originalPlayerOrder.indices) return
        currentDealerIndex = index
        saveCurrentState()
    }

    fun getCurrentDealerName(): String {
        if (originalPlayerOrder.isEmpty() || currentDealerIndex !in originalPlayerOrder.indices) return "?"
        return originalPlayerOrder[currentDealerIndex].name
    }

    val standings: List<Pair<Player, Int>>
        get() = players.sortedByDescending { it.score }.mapIndexed { idx, p -> p to idx }

    val slepaci: Set<String>
        get() = GameSession.computeSlepaci(gameHistory)

    fun captureScores(): Map<String, Int> = players.associate { it.name to it.score }
}
