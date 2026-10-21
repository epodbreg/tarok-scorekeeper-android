package com.epodbreg.stih.logic

import com.epodbreg.stih.model.BonusType
import com.epodbreg.stih.model.GameEntry
import com.epodbreg.stih.model.GameSession
import com.epodbreg.stih.model.HandOutcome
import com.epodbreg.stih.model.TarokContract
import com.epodbreg.stih.model.TarokStrings
import kotlin.math.abs

/** A score paired with the contract it came from (records, biggest šlep). */
data class ContractPoints(val points: Int, val contract: TarokContract)

/** Per-player statistics. Port of PlayerStats in TarokStats.swift. */
data class PlayerStats(
    // Rounds (side entries excluded)
    val roundsPlayed: Int = 0,
    val declared: Int = 0,
    val declaredWon: Int = 0,

    // Šlepi (rounds as partner)
    val partnerRounds: Int = 0,
    val partnerWins: Int = 0,
    val biggestSlep: Int = 0,

    val zarufalCount: Int = 0,

    // Points
    val pointsEarned: Int = 0,
    val biggestWin: ContractPoints? = null,
    val worstLoss: ContractPoints? = null,

    // Contracts (as declarer)
    val contractCounts: Map<TarokContract, Int> = emptyMap(),
    val contractWins: Map<TarokContract, Int> = emptyMap(),

    // Specials
    val valati: Int = 0,
    val klopPrazen: Int = 0,
    val klopPoln: Int = 0,
    val kontraRounds: Int = 0,
    val kontrasCalled: Int = 0,
    val kontrasWon: Int = 0,
    val bonusesWon: Map<BonusType, Int> = emptyMap(),
    val bonusesLost: Map<BonusType, Int> = emptyMap(),

    // Side entries (tracked, never counted as rounds)
    val mondKazCount: Int = 0,
    val renonsCount: Int = 0,
    val pagatAwards: Int = 0,
    val pagatLosses: Int = 0,

    // Sessions (filled only when computing from sessions)
    val sessionsPlayed: Int = 0,
    val sessionsWon: Int = 0,
    val podiums: Int = 0,
) {
    val winRate: Double get() = if (declared > 0) declaredWon.toDouble() / declared else 0.0

    val partnerLosses: Int get() = partnerRounds - partnerWins

    /** Most-declared contract; deterministic tie-break by raw value. */
    val favoriteContract: TarokContract?
        get() = contractCounts.entries.sortedWith(byCountThenRawValue).firstOrNull()?.key

    fun merge(other: PlayerStats): PlayerStats = PlayerStats(
        roundsPlayed = roundsPlayed + other.roundsPlayed,
        declared = declared + other.declared,
        declaredWon = declaredWon + other.declaredWon,
        partnerRounds = partnerRounds + other.partnerRounds,
        partnerWins = partnerWins + other.partnerWins,
        biggestSlep = maxOf(biggestSlep, other.biggestSlep),
        zarufalCount = zarufalCount + other.zarufalCount,
        pointsEarned = pointsEarned + other.pointsEarned,
        biggestWin = listOfNotNull(biggestWin, other.biggestWin).maxByOrNull { it.points },
        worstLoss = listOfNotNull(worstLoss, other.worstLoss).minByOrNull { it.points },
        contractCounts = contractCounts.plusCounts(other.contractCounts),
        contractWins = contractWins.plusCounts(other.contractWins),
        valati = valati + other.valati,
        klopPrazen = klopPrazen + other.klopPrazen,
        klopPoln = klopPoln + other.klopPoln,
        kontraRounds = kontraRounds + other.kontraRounds,
        kontrasCalled = kontrasCalled + other.kontrasCalled,
        kontrasWon = kontrasWon + other.kontrasWon,
        bonusesWon = bonusesWon.plusCounts(other.bonusesWon),
        bonusesLost = bonusesLost.plusCounts(other.bonusesLost),
        mondKazCount = mondKazCount + other.mondKazCount,
        renonsCount = renonsCount + other.renonsCount,
        pagatAwards = pagatAwards + other.pagatAwards,
        pagatLosses = pagatLosses + other.pagatLosses,
        sessionsPlayed = sessionsPlayed + other.sessionsPlayed,
        sessionsWon = sessionsWon + other.sessionsWon,
        podiums = podiums + other.podiums,
    )

    companion object {
        /** Highest count first; ties broken by contract raw value (matches iOS). */
        val byCountThenRawValue: Comparator<Map.Entry<TarokContract, Int>> =
            compareByDescending<Map.Entry<TarokContract, Int>> { it.value }.thenBy { it.key.rawValue }
    }
}

private fun <K> Map<K, Int>.plusCounts(other: Map<K, Int>): Map<K, Int> =
    (keys + other.keys).associateWith { (this[it] ?: 0) + (other[it] ?: 0) }

private fun <K> MutableMap<K, Int>.bump(key: K) {
    this[key] = (this[key] ?: 0) + 1
}

data class NamedCount(val name: String, val count: Int)
data class NamedPoints(val name: String, val points: Int, val contract: TarokContract)

/** End-screen fun facts for one session. */
data class SessionFunFacts(
    val roundCount: Int = 0,
    /** Milliseconds */
    val durationMs: Long? = null,
    val mostPlayedContract: Pair<TarokContract, Int>? = null,
    /** Biggest |points| real round */
    val roundOfTheNight: GameEntry? = null,
    /** Most points a player got from one round */
    val bestSingleRound: NamedPoints? = null,
    val mostSlepi: NamedCount? = null,
    val biggestSlep: NamedPoints? = null,
    /** Most zarufal */
    val unluckiest: NamedCount? = null,
    /** Milliseconds */
    val avgRoundLengthMs: Double? = null,
    /** Only set when at least [TarokStats.MIN_STREAK_LENGTH] */
    val longestStreak: NamedCount? = null,
)

/**
 * Pure statistics engine — port of TarokStats.swift. Everything is derived
 * from saved games/sessions; aggregates by player name.
 */
object TarokStats {

    /** A winning streak shorter than this isn't worth a chip on the end screen. */
    const val MIN_STREAK_LENGTH = 3

    private const val ZARUFAL = TarokStrings.ZARUFAL

    /** Mutable working copy so the per-game loop stays readable. */
    private class Acc {
        var roundsPlayed = 0
        var declared = 0
        var declaredWon = 0
        var partnerRounds = 0
        var partnerWins = 0
        var biggestSlep = 0
        var zarufalCount = 0
        var pointsEarned = 0
        var biggestWin: ContractPoints? = null
        var worstLoss: ContractPoints? = null
        val contractCounts = mutableMapOf<TarokContract, Int>()
        val contractWins = mutableMapOf<TarokContract, Int>()
        var valati = 0
        var klopPrazen = 0
        var klopPoln = 0
        var kontraRounds = 0
        var kontrasCalled = 0
        var kontrasWon = 0
        val bonusesWon = mutableMapOf<BonusType, Int>()
        val bonusesLost = mutableMapOf<BonusType, Int>()
        var mondKazCount = 0
        var renonsCount = 0
        var pagatAwards = 0
        var pagatLosses = 0

        fun freeze() = PlayerStats(
            roundsPlayed = roundsPlayed, declared = declared, declaredWon = declaredWon,
            partnerRounds = partnerRounds, partnerWins = partnerWins, biggestSlep = biggestSlep,
            zarufalCount = zarufalCount, pointsEarned = pointsEarned,
            biggestWin = biggestWin, worstLoss = worstLoss,
            contractCounts = contractCounts.toMap(), contractWins = contractWins.toMap(),
            valati = valati, klopPrazen = klopPrazen, klopPoln = klopPoln,
            kontraRounds = kontraRounds, kontrasCalled = kontrasCalled, kontrasWon = kontrasWon,
            bonusesWon = bonusesWon.toMap(), bonusesLost = bonusesLost.toMap(),
            mondKazCount = mondKazCount, renonsCount = renonsCount,
            pagatAwards = pagatAwards, pagatLosses = pagatLosses,
        )
    }

    /**
     * Per-player stats from one list of games. [playerNames] defines the table:
     * every listed player is considered present for every real round. Names not
     * at the table are ignored.
     */
    fun compute(games: List<GameEntry>, playerNames: List<String>): Map<String, PlayerStats> {
        val acc = playerNames.associateWith { Acc() }
        val realRoundCount = games.count { !it.contract.isSideEntry }
        acc.values.forEach { it.roundsPlayed = realRoundCount }

        for (game in games) {
            // Side entries: tally separately, never as rounds — but points count
            if (game.contract.isSideEntry) {
                val a = acc[game.declarerName] ?: continue
                when (game.contract) {
                    TarokContract.PENALTY_MOND -> a.mondKazCount++
                    TarokContract.PENALTY_RENONS -> a.renonsCount++
                    TarokContract.PAGAT_BONUS ->
                        if (game.totalPoints >= 0) a.pagatAwards++ else a.pagatLosses++
                    else -> {}
                }
                a.pointsEarned += game.totalPoints
                continue
            }

            // Klop: prazen/poln from RAW ±70 so radlc doubling doesn't hide them
            if (game.contract == TarokContract.KLOP) {
                val raw = game.rawKlopScores ?: game.klopScores ?: emptyMap()
                raw.forEach { (name, rawScore) ->
                    if (rawScore == 70) acc[name]?.let { it.klopPrazen++ }
                    if (rawScore == -70) acc[name]?.let { it.klopPoln++ }
                }
                game.klopScores?.forEach { (name, score) -> acc[name]?.let { it.pointsEarned += score } }
                continue
            }

            // Real declared round
            val declarer = game.declarerName
            val won = game.outcome == HandOutcome.ZMAGAL || game.outcome == HandOutcome.ZMAGAL_SLEPAL

            acc[declarer]?.let { d ->
                d.declared++
                d.contractCounts.bump(game.contract)
                if (won) {
                    d.declaredWon++
                    d.contractWins.bump(game.contract)
                    if (game.contract.isValatType) d.valati++
                }
                if (game.partnerName == ZARUFAL) d.zarufalCount++
                if (game.pointsBreakdown.kontraLevel > 0) d.kontraRounds++
            }

            // The caller sits on the opposing side: their kontra pays off when the declarer loses
            if (game.pointsBreakdown.kontraLevel > 0) {
                game.kontraCallerName?.let { acc[it] }?.let { caller ->
                    caller.kontrasCalled++
                    if (!won) caller.kontrasWon++
                }
            }

            // Points go to the declarer and partner (same points). Zarufal is never a partner.
            val credited = mutableListOf(declarer)
            val partner = game.partnerName
            if (partner != null && partner != ZARUFAL && partner != declarer && acc[partner] != null) {
                credited += partner
                acc.getValue(partner).let { p ->
                    p.partnerRounds++
                    if (won) {
                        p.partnerWins++
                        p.biggestSlep = maxOf(p.biggestSlep, game.totalPoints)
                    }
                }
            }
            for (name in credited) {
                val a = acc[name] ?: continue
                a.pointsEarned += game.totalPoints
                if (game.totalPoints > 0 && game.totalPoints > (a.biggestWin?.points ?: Int.MIN_VALUE)) {
                    a.biggestWin = ContractPoints(game.totalPoints, game.contract)
                }
                if (game.totalPoints < 0 && game.totalPoints < (a.worstLoss?.points ?: Int.MAX_VALUE)) {
                    a.worstLoss = ContractPoints(game.totalPoints, game.contract)
                }
                // Bonuses belong to the whole declarer side
                for (bonus in game.bonuses) {
                    if (bonus.won) a.bonusesWon.bump(bonus.type) else a.bonusesLost.bump(bonus.type)
                }
            }
        }

        return acc.mapValues { it.value.freeze() }
    }

    /** Cross-session aggregate, merged by player name; adds session results. */
    fun compute(sessions: List<GameSession>): Map<String, PlayerStats> {
        val merged = mutableMapOf<String, PlayerStats>()
        for (session in sessions) {
            for ((name, s) in compute(session.games, session.playerNames)) {
                val mine = session.finalScores?.firstOrNull { it.name == name }
                val withSession = s.copy(
                    sessionsPlayed = 1,
                    sessionsWon = if (mine?.position == 1) 1 else 0,
                    podiums = if (mine != null && mine.position <= 3) 1 else 0,
                )
                merged[name] = merged[name]?.merge(withSession) ?: withSession
            }
        }
        return merged
    }

    /** End-screen fun facts for one session. */
    fun funFacts(session: GameSession): SessionFunFacts {
        val stats = compute(session.games, session.playerNames)
        val realRounds = session.games.filter { !it.contract.isSideEntry }.sortedBy { it.timestamp }

        val contractTally = realRounds.groupingBy { it.contract }.eachCount()
        val mostPlayed = contractTally.entries.sortedWith(PlayerStats.byCountThenRawValue).firstOrNull()

        // Most points from a single round. Declarer is listed before partner and rounds
        // are chronological, so with strict `>` ties go to the declarer / the earlier round.
        var bestSingle: NamedPoints? = null
        for (round in realRounds) {
            for (name in creditedPlayers(round, session.playerNames)) {
                val points = pointsCredited(round, name)
                if (points > 0 && points > (bestSingle?.points ?: 0)) {
                    bestSingle = NamedPoints(name, points, round.contract)
                }
            }
        }

        // Winning streak: rounds where a player's points were positive.
        // Sitting out (0) doesn't break it; any minus does.
        var streak: NamedCount? = null
        for (name in session.playerNames) {
            var current = 0
            var best = 0
            for (round in realRounds) {
                val points = pointsCredited(round, name)
                if (points > 0) {
                    current++
                    best = maxOf(best, current)
                } else if (points < 0) {
                    current = 0
                }
            }
            if (best >= MIN_STREAK_LENGTH && best > (streak?.count ?: 0)) streak = NamedCount(name, best)
        }

        // Player-based facts: highest value first, ties broken by name
        val mostSlepi = stats.entries
            .sortedWith(compareByDescending<Map.Entry<String, PlayerStats>> { it.value.partnerWins }.thenBy { it.key })
            .firstOrNull()?.takeIf { it.value.partnerWins > 0 }
            ?.let { NamedCount(it.key, it.value.partnerWins) }

        val biggestSlep = stats.entries
            .sortedWith(compareByDescending<Map.Entry<String, PlayerStats>> { it.value.biggestSlep }.thenBy { it.key })
            .firstOrNull()?.takeIf { it.value.biggestSlep > 0 }
            ?.let { (name, s) ->
                val contract = realRounds.firstOrNull {
                    it.partnerName == name && it.totalPoints == s.biggestSlep
                }?.contract ?: TarokContract.TRI
                NamedPoints(name, s.biggestSlep, contract)
            }

        val unluckiest = stats.entries
            .sortedWith(compareByDescending<Map.Entry<String, PlayerStats>> { it.value.zarufalCount }.thenBy { it.key })
            .firstOrNull()?.takeIf { it.value.zarufalCount > 0 }
            ?.let { NamedCount(it.key, it.value.zarufalCount) }

        // Average gap between consecutive real rounds; the first is measured from session start
        val lengths = mutableListOf<Long>()
        var prev = session.startTime
        for (round in realRounds) {
            val delta = round.timestamp - prev
            if (delta > 0) lengths += delta
            prev = round.timestamp
        }

        return SessionFunFacts(
            roundCount = realRounds.size,
            durationMs = session.endTime?.let { it - session.startTime },
            mostPlayedContract = mostPlayed?.let { it.key to it.value },
            roundOfTheNight = realRounds.maxByOrNull(::roundMagnitude),
            bestSingleRound = bestSingle,
            mostSlepi = mostSlepi,
            biggestSlep = biggestSlep,
            unluckiest = unluckiest,
            avgRoundLengthMs = if (lengths.isEmpty()) null else lengths.average(),
            longestStreak = streak,
        )
    }

    /** Points a player got from one real round (0 if they weren't on the declarer side). */
    private fun pointsCredited(game: GameEntry, name: String): Int = when {
        game.contract == TarokContract.KLOP -> game.klopScores?.get(name) ?: 0
        game.declarerName == name -> game.totalPoints
        game.partnerName != null && game.partnerName != ZARUFAL && game.partnerName == name -> game.totalPoints
        else -> 0
    }

    /** Players credited in a round, declarer before partner; klop in seat order. */
    private fun creditedPlayers(game: GameEntry, seatOrder: List<String>): List<String> {
        if (game.contract == TarokContract.KLOP) {
            return seatOrder.filter { game.klopScores?.containsKey(it) == true }
        }
        val partner = game.partnerName
        return if (partner != null && partner != ZARUFAL && partner != game.declarerName) {
            listOf(game.declarerName, partner)
        } else {
            listOf(game.declarerName)
        }
    }

    /** Swing magnitude used for "round of the night". */
    fun roundMagnitude(game: GameEntry): Int =
        if (game.contract == TarokContract.KLOP) {
            game.klopScores?.values?.maxOfOrNull { abs(it) } ?: 0
        } else {
            abs(game.totalPoints)
        }

    /** Formats a length like "4 min" / "1 h 12 min". */
    fun formatDuration(millis: Double): String {
        val minutes = (millis / 60_000).toInt()
        return when {
            minutes < 1 -> "< 1 min"
            minutes < 60 -> "$minutes min"
            else -> "${minutes / 60} h ${minutes % 60} min"
        }
    }
}
