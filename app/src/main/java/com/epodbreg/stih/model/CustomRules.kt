package com.epodbreg.stih.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull

@Serializable
data class ContractScoreConfig(
    val threePlayerScore: Int,
    val fourPlusPlayerScore: Int,
    val usesRazlika: Boolean = false,
    val hasFixedScoring: Boolean = false,
)

@Serializable
data class BonusScoreConfig(
    val trulaBase: Int = 10,
    val trulaAnnounced: Int = 20,
    val kraljBase: Int = 10,
    val kraljAnnounced: Int = 20,
    val kraljUltimoBase: Int = 10,
    val kraljUltimoAnnounced: Int = 20,
    val pagatUltimoBase: Int = 25,
    val pagatUltimoAnnounced: Int = 50,
    val mondKazBase: Int = 20,
    val tihiValat: Int = 250,
    val napovedanValat: Int = 500,
)

/**
 * Switches saved with each rule set: they depend on the table. Every field has a default, so
 * old rule sets and backups read with today's behaviour.
 */
@Serializable
data class GeneralRulesConfig(
    /**
     * Unused: never read by scoring, and its stored value (true) doesn't describe what the app
     * did. Kept so old data and iOS round-trip. The real rule is [radlcDoublesPartner].
     */
    val radlcDoublingDeclarerOnly: Boolean = true,
    val radlcPenaltyPerUnused: Int = 100,
    /** Whether unused radlci give an end-of-session penalty */
    val radlcPenaltyEnabled: Boolean = true,
    /**
     * Whether an unused radlc doubles klop scores (and a prazen klop uses it up).
     * Klop always *grants* radlci regardless.
     */
    val radlcDoublesKlop: Boolean = true,
    /** Whether an unused radlc doubles the pagat quick-award. */
    val radlcDoublesPagat: Boolean = true,
    /** Whether the declarer's radlc also doubles the partner's points */
    val radlcDoublesPartner: Boolean = true,
    /** "Na prazen klop ne vpliva radlc": a prazen klop isn't doubled and keeps the radlc */
    val radlcSkipsPrazenKlop: Boolean = false,
    /** Points deducted for a renons (always a penalty). */
    val renonsPenalty: Int = 20,
    val enableAutoScoring: Boolean = true,
    val valatWithGamePoints: Boolean = false,
    // Obvezni klop: one app-wide setting until 1.9 (SettingsManager copies it in once)
    val obvezniKlopEnabled: Boolean = false,
    /** Fire when any player's score lands on a non-negative multiple of [obvezniKlopStep] */
    val obvezniKlopOnRoundScore: Boolean = true,
    /** Fire when a played game itself totals exactly 0 points */
    val obvezniKlopOnZeroOutcome: Boolean = true,
    val obvezniKlopStep: Int = 100,
) {
    /** [obvezniKlopStep], or 100 when a stored value is unusable */
    val klopStep: Int get() = if (obvezniKlopStep > 0) obvezniKlopStep else 100
}

/** When the game screen uses half-width player cards */
enum class CompactCardsMode(val rawValue: String, val label: String) {
    AUTO("auto", "Samodejno"),
    ALWAYS("always", "Vedno"),
    NEVER("never", "Nikoli");

    fun isCompact(playerCount: Int): Boolean = when (this) {
        AUTO -> playerCount >= AUTO_FROM_PLAYERS
        ALWAYS -> true
        NEVER -> false
    }

    companion object {
        /** "Samodejno" switches at this many players */
        const val AUTO_FROM_PLAYERS = 5

        fun fromRaw(value: String?): CompactCardsMode? = entries.firstOrNull { it.rawValue == value }
    }
}

@Serializable
data class CustomScoringRules(
    val name: String = "Po meri",
    val contractScores: Map<String, ContractScoreConfig> = defaultContractScores,
    val bonusScores: BonusScoreConfig = BonusScoreConfig(),
    val generalRules: GeneralRulesConfig = GeneralRulesConfig(),
) {
    companion object {
        /** Standard contract scores used to pre-fill new or incomplete rule sets. */
        val defaultContractScores: Map<String, ContractScoreConfig> = mapOf(
            TarokContract.TRI.rawValue to ContractScoreConfig(10, 10, usesRazlika = true),
            TarokContract.DVE.rawValue to ContractScoreConfig(20, 20, usesRazlika = true),
            TarokContract.ENA.rawValue to ContractScoreConfig(30, 30, usesRazlika = true),
            TarokContract.SOLO_TRI.rawValue to ContractScoreConfig(0, 40, usesRazlika = true),
            TarokContract.SOLO_DVE.rawValue to ContractScoreConfig(0, 50, usesRazlika = true),
            TarokContract.SOLO_ENA.rawValue to ContractScoreConfig(0, 60, usesRazlika = true),
            TarokContract.BERAC.rawValue to ContractScoreConfig(70, 70, usesRazlika = false, hasFixedScoring = true),
            TarokContract.SOLO_BREZ.rawValue to ContractScoreConfig(80, 80, usesRazlika = true),
            TarokContract.ODPRTI_BERAC.rawValue to ContractScoreConfig(90, 90, usesRazlika = false, hasFixedScoring = true),
            TarokContract.BARVNI_VALAT.rawValue to ContractScoreConfig(125, 125, usesRazlika = false, hasFixedScoring = true),
            TarokContract.VALAT.rawValue to ContractScoreConfig(500, 500, usesRazlika = false, hasFixedScoring = true),
            TarokContract.PENALTY_MOND.rawValue to ContractScoreConfig(20, 20, hasFixedScoring = true),
            TarokContract.PENALTY_RENONS.rawValue to ContractScoreConfig(20, 20, hasFixedScoring = true),
        )

        fun standard(): CustomScoringRules = CustomScoringRules(
            name = "Standard",
            contractScores = defaultContractScores,
            bonusScores = BonusScoreConfig(),
            generalRules = GeneralRulesConfig(),
        )
    }
}

/**
 * Reads a game's saved rules without ever failing the game: a missing, null or damaged value
 * comes back as null (the game then uses today's rules).
 */
object LenientRulesSerializer : KSerializer<CustomScoringRules?> {
    private val inner = CustomScoringRules.serializer()
    override val descriptor: SerialDescriptor = inner.nullable.descriptor

    override fun serialize(encoder: Encoder, value: CustomScoringRules?) {
        if (value == null) encoder.encodeNull() else encoder.encodeSerializableValue(inner, value)
    }

    override fun deserialize(decoder: Decoder): CustomScoringRules? {
        val json = decoder as? JsonDecoder ?: return decoder.decodeSerializableValue(inner.nullable)
        val element = json.decodeJsonElement()
        if (element is JsonNull) return null
        return try {
            json.json.decodeFromJsonElement(inner, element)
        } catch (_: Exception) {
            null
        }
    }
}
