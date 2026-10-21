package com.epodbreg.stih.model

import kotlinx.serialization.Serializable

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

@Serializable
data class GeneralRulesConfig(
    val radlcDoublingDeclarerOnly: Boolean = true,
    val radlcPenaltyPerUnused: Int = 100,
    /** Whether unused radlci give an end-of-session penalty */
    val radlcPenaltyEnabled: Boolean = true,
    /**
     * "Klop brez radlca": whether an unused radlc doubles klop scores (and a
     * prazen klop consumes it). Klop always *grants* radlci regardless.
     */
    val radlcDoublesKlop: Boolean = true,
    /** Whether an unused radlc doubles the pagat quick-award. */
    val radlcDoublesPagat: Boolean = true,
    /** Points deducted for a renons (always a penalty). */
    val renonsPenalty: Int = 20,
    val enableAutoScoring: Boolean = true,
    val valatWithGamePoints: Boolean = false,
)

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
