package com.epodbreg.stih.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

/** Serializes java.util.UUID as its string form (matches iOS Codable UUID). */
object UUIDSerializer : KSerializer<UUID> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: UUID) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): UUID = UUID.fromString(decoder.decodeString())
}

@Serializable
enum class TarokContract {
    @SerialName("klop") KLOP,
    @SerialName("tri") TRI,
    @SerialName("dve") DVE,
    @SerialName("ena") ENA,
    @SerialName("soloTri") SOLO_TRI,
    @SerialName("soloDve") SOLO_DVE,
    @SerialName("soloEna") SOLO_ENA,
    @SerialName("berac") BERAC,
    @SerialName("soloBrez") SOLO_BREZ,
    @SerialName("odprtiBerac") ODPRTI_BERAC,
    @SerialName("barvniValat") BARVNI_VALAT,
    @SerialName("valat") VALAT,
    @SerialName("penaltyRenons") PENALTY_RENONS,
    @SerialName("penaltyMond") PENALTY_MOND,

    /** Pagat ultimo won by non-declarer (reward outside game flow) */
    @SerialName("pagatBonus") PAGAT_BONUS;

    val rawValue: String
        get() = when (this) {
            KLOP -> "klop"; TRI -> "tri"; DVE -> "dve"; ENA -> "ena"
            SOLO_TRI -> "soloTri"; SOLO_DVE -> "soloDve"; SOLO_ENA -> "soloEna"
            BERAC -> "berac"; SOLO_BREZ -> "soloBrez"; ODPRTI_BERAC -> "odprtiBerac"
            BARVNI_VALAT -> "barvniValat"; VALAT -> "valat"
            PENALTY_RENONS -> "penaltyRenons"; PENALTY_MOND -> "penaltyMond"
            PAGAT_BONUS -> "pagatBonus"
        }

    val displayName: String
        get() = when (this) {
            KLOP -> "Klop"; TRI -> "Tri"; DVE -> "Dve"; ENA -> "Ena"
            SOLO_TRI -> "Solo tri"; SOLO_DVE -> "Solo dve"; SOLO_ENA -> "Solo ena"
            BERAC -> "Berač"; SOLO_BREZ -> "Solo brez"; ODPRTI_BERAC -> "Odprti berač"
            BARVNI_VALAT -> "Barvni valat"; VALAT -> "Valat"
            PENALTY_MOND -> "Mond"; PENALTY_RENONS -> "Renons"; PAGAT_BONUS -> "Pagat bonus"
        }

    val isPenalty: Boolean
        get() = this == PENALTY_MOND || this == PENALTY_RENONS

    /**
     * Side entries (penalties/awards) are bookkeeping, not played rounds:
     * they don't advance the dealer, don't count as games, and aren't stats rounds.
     */
    val isSideEntry: Boolean
        get() = this == PENALTY_MOND || this == PENALTY_RENONS || this == PAGAT_BONUS

    val hasFixedScoring: Boolean
        get() = this in setOf(BERAC, ODPRTI_BERAC, BARVNI_VALAT, VALAT, PENALTY_MOND, PENALTY_RENONS)

    val isValatType: Boolean
        get() = this == VALAT || this == BARVNI_VALAT

    val givesRadlci: Boolean
        get() = this in setOf(KLOP, BERAC, SOLO_BREZ, ODPRTI_BERAC, BARVNI_VALAT, VALAT)
}

@Serializable
enum class HandOutcome {
    @SerialName("zmagal") ZMAGAL,
    @SerialName("zmagalSlepal") ZMAGAL_SLEPAL,
    @SerialName("izgubil") IZGUBIL,
    @SerialName("izgubilSlepal") IZGUBIL_SLEPAL;

    val isWin: Boolean get() = this == ZMAGAL || this == ZMAGAL_SLEPAL
    val isLoss: Boolean get() = !isWin

    val rawValue: String
        get() = when (this) {
            ZMAGAL -> "zmagal"; ZMAGAL_SLEPAL -> "zmagalSlepal"
            IZGUBIL -> "izgubil"; IZGUBIL_SLEPAL -> "izgubilSlepal"
        }

    val displayName: String
        get() = when (this) {
            ZMAGAL -> "Zmagal_a"
            ZMAGAL_SLEPAL -> "Zmagal_a (šlepal_a)"
            IZGUBIL -> "Izgubil_a"
            IZGUBIL_SLEPAL -> "Izgubil_a (šlepal_a)"
        }
}

@Serializable
enum class BonusType {
    @SerialName("trula") TRULA,
    @SerialName("kralji") KRALJI,
    @SerialName("kraljUltimo") KRALJ_ULTIMO,
    @SerialName("pagatUltimo") PAGAT_ULTIMO,
    @SerialName("mondKaz") MOND_KAZ;

    val rawValue: String
        get() = when (this) {
            TRULA -> "trula"; KRALJI -> "kralji"; KRALJ_ULTIMO -> "kraljUltimo"
            PAGAT_ULTIMO -> "pagatUltimo"; MOND_KAZ -> "mondKaz"
        }

    val displayName: String
        get() = when (this) {
            TRULA -> "Trula"; KRALJI -> "Kralji"; KRALJ_ULTIMO -> "Kralj Ultimo"
            PAGAT_ULTIMO -> "Pagat Ultimo"; MOND_KAZ -> "Kaznovan mond"
        }

    val shortName: String
        get() = when (this) {
            TRULA -> "Trula"; KRALJI -> "Kralji"; KRALJ_ULTIMO -> "Kralj U."
            PAGAT_ULTIMO -> "Pagat"; MOND_KAZ -> "Mond K."
        }

    val abbreviation: String
        get() = when (this) {
            TRULA -> "TR"; KRALJI -> "KR"; KRALJ_ULTIMO -> "KU"
            PAGAT_ULTIMO -> "PU"; MOND_KAZ -> "MO"
        }
}

/**
 * Migration serializer for HandBonus: legacy iOS entries encoded won/lost
 * inside kontraLevel (-1 = lost, >= 0 = won) and may be missing `won`.
 */
object HandBonusMigrationSerializer : JsonTransformingSerializer<HandBonus>(HandBonus.serializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        val obj = element as? JsonObject ?: return element
        val rawKontra = obj["kontraLevel"]?.jsonPrimitive?.intOrNull ?: 0
        val won = obj["won"]?.jsonPrimitive?.booleanOrNull ?: (rawKontra >= 0)
        return buildJsonObject {
            obj.forEach { (k, v) -> if (k != "won" && k != "kontraLevel") put(k, v) }
            put("won", JsonPrimitive(won))
            put("kontraLevel", JsonPrimitive(maxOf(0, rawKontra)))
        }
    }
}

@Serializable
data class HandBonus(
    val type: BonusType,
    val announced: Boolean,
    val won: Boolean = true,
    /** 0 = none, 1 = kontra x2, 2 = re x4, 3 = sub x8, 4 = mort x16 */
    val kontraLevel: Int = 0,
) {
    val kontraMultiplier: Int get() = maxOf(1, 1 shl kontraLevel)
    val hasKontra: Boolean get() = kontraLevel > 0
}

@Serializable
data class PointsBreakdown(
    val gamePoints: Int,
    val difference: Int? = null,
    val trula: Int? = null,
    val kralji: Int? = null,
    val kraljUltimo: Int? = null,
    val pagatUltimo: Int? = null,
    val valat: Int? = null,
    val mondKaz: Int? = null,
    val kontraLevel: Int = 0,
)

@Serializable
data class GameEntry(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID = UUID.randomUUID(),
    val contract: TarokContract,
    val declarerName: String,
    val partnerName: String? = null,
    val outcome: HandOutcome,
    val pointsBreakdown: PointsBreakdown,
    val bonuses: List<@Serializable(with = HandBonusMigrationSerializer::class) HandBonus> = emptyList(),
    val totalPoints: Int,
    /** Epoch milliseconds */
    val timestamp: Long = System.currentTimeMillis(),
    val klopScores: Map<String, Int>? = null,
    /** Original klop scores before radlc doubling */
    val rawKlopScores: Map<String, Int>? = klopScores,
    /** Original points before radlc doubling */
    val rawTotalPoints: Int = totalPoints,
    /** For valat with game points */
    val underlyingGame: TarokContract? = null,
    /** Whether valat was announced (napovedan) vs silent (tihi) */
    val isValatAnnounced: Boolean? = null,
    /** Opponent who called the game-level kontra (optional; null for older entries) */
    val kontraCallerName: String? = null,
) {
    /**
     * Copy with every reference to [oldName] replaced by [newName]
     * (declarer, partner, kontra caller, klop score keys). Identity is preserved.
     */
    fun renamingPlayer(oldName: String, newName: String): GameEntry {
        fun swap(name: String) = if (name == oldName) newName else name
        fun swapKeys(scores: Map<String, Int>?): Map<String, Int>? = scores?.let { map ->
            val result = mutableMapOf<String, Int>()
            map.forEach { (k, v) -> result.merge(swap(k), v, Int::plus) }
            result
        }
        return copy(
            declarerName = swap(declarerName),
            partnerName = partnerName?.let(::swap),
            klopScores = swapKeys(klopScores),
            rawKlopScores = swapKeys(rawKlopScores),
            kontraCallerName = kontraCallerName?.let(::swap),
        )
    }
}

@Serializable
data class Player(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val score: Int = 0,
    /** true = used, false = unused */
    val radlci: List<Boolean> = emptyList(),
) {
    val usedRadlciCount: Int get() = radlci.count { it }
    val unusedRadlciCount: Int get() = radlci.count { !it }
    val hasUnusedRadlci: Boolean get() = radlci.contains(false)
}

@Serializable
data class PlayerScore(
    val name: String,
    val score: Int,
    val position: Int,
)

@Serializable
data class GameSession(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID = UUID.randomUUID(),
    val players: List<Player>,
    /** Epoch milliseconds */
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val games: List<GameEntry> = emptyList(),
    val finalScores: List<PlayerScore>? = null,
    /** Derived dealer index persisted for backwards compatibility and display. */
    val currentDealerIndex: Int = 0,
    /**
     * Manual dealer adjustment. The effective dealer is
     * (dealerManualOffset + count of dealer-advancing games) mod playerCount.
     * Null on sessions saved before this field existed — back-solved on load.
     */
    val dealerManualOffset: Int? = null,
) {
    val playerNames: List<String> get() = players.map { it.name }
    val isActive: Boolean get() = endTime == null

    /** Played rounds — side entries (mond kazen, renons, pagat award) excluded */
    val roundCount: Int get() = games.count { !it.contract.isSideEntry }

    /** Both names sat at this table — merging them would fuse two real people. */
    fun hasConflict(oldName: String, newName: String): Boolean =
        oldName != newName && oldName in playerNames && newName in playerNames

    /** Copy with [oldName] replaced everywhere. Caller must check [hasConflict] first. */
    fun renamingPlayer(oldName: String, newName: String): GameSession {
        if (oldName !in playerNames) return this
        return copy(
            players = players.map { if (it.name == oldName) it.copy(name = newName) else it },
            games = games.map { it.renamingPlayer(oldName, newName) },
            finalScores = finalScores?.map { if (it.name == oldName) it.copy(name = newName) else it },
        )
    }

    /**
     * Players who have been soigralec/ka the most times (only 1/2/3/V/BV contracts).
     * Returns a set so ties are handled. Empty if nobody has been partner yet.
     */
    val slepaci: Set<String>
        get() = computeSlepaci(games)

    companion object {
        val PARTNER_ELIGIBLE = setOf(
            TarokContract.TRI, TarokContract.DVE, TarokContract.ENA,
            TarokContract.VALAT, TarokContract.BARVNI_VALAT
        )

        fun computeSlepaci(games: List<GameEntry>): Set<String> {
            val counts = mutableMapOf<String, Int>()
            for (game in games) {
                val partner = game.partnerName ?: continue
                if (game.contract !in PARTNER_ELIGIBLE) continue
                if (partner.isEmpty() || partner == TarokStrings.ZARUFAL) continue
                counts[partner] = (counts[partner] ?: 0) + 1
            }
            val max = counts.values.maxOrNull() ?: return emptySet()
            if (max <= 0) return emptySet()
            return counts.filterValues { it == max }.keys
        }
    }
}

/** Shared string constants (ported from TarokConstants.Strings). */
object TarokStrings {
    const val ZARUFAL = "Zarufal_a"

    /** Placeholder seat names ("Igralec_ka 3") are hidden from pickers and stats. */
    private val PLACEHOLDER = Regex("""^Igralec_ka \d+$""")
    fun isPlaceholderName(name: String) = PLACEHOLDER.matches(name)

    // Slovene plurals: the 1 / 2 / 3–4 / 5+ forms

    fun rundaCountText(count: Int): String = when (count) {
        1 -> "1 runda"
        2 -> "2 rundi"
        3, 4 -> "$count runde"
        else -> "$count rund"
    }

    fun sejaCountText(count: Int): String = when (count) {
        1 -> "1 seja"
        2 -> "2 seji"
        3, 4 -> "$count seje"
        else -> "$count sej"
    }

    fun uspesnihText(count: Int): String = when (count) {
        1 -> "1 uspešna"
        2 -> "2 uspešni"
        3, 4 -> "$count uspešne"
        else -> "$count uspešnih"
    }

    fun slepCountText(count: Int): String = when (count) {
        1 -> "1 šlep"
        2 -> "2 šlepa"
        3, 4 -> "$count šlepi"
        else -> "$count šlepov"
    }
}
