package com.epodbreg.stih.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.epodbreg.stih.model.BonusType
import com.epodbreg.stih.model.CustomScoringRules
import com.epodbreg.stih.model.HandBonus
import com.epodbreg.stih.model.HandOutcome
import com.epodbreg.stih.model.TarokContract
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * App settings + house-rule scoring. Port of SettingsManager.swift,
 * backed by SharedPreferences instead of UserDefaults.
 */
class SettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("stih_settings", Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    var autoScoring: Boolean by mutableStateOf(prefs.getBoolean("autoScoring", true))
        private set
    var houseScoringRules: String by mutableStateOf(prefs.getString("houseScoringRules", "Standard") ?: "Standard")
        private set
    var showAnimations: Boolean by mutableStateOf(prefs.getBoolean("showAnimations", true))
        private set
    var soundEffects: Boolean by mutableStateOf(prefs.getBoolean("soundEffects", false))
        private set
    var obvezniKlopEnabled: Boolean by mutableStateOf(prefs.getBoolean("obvezniKlopEnabled", false))
        private set

    /** Fire when any player's live net score hits a non-negative multiple of 100 */
    var obvezniKlopOnRoundScore: Boolean by mutableStateOf(prefs.getBoolean("obvezniKlopOnRoundScore", true))
        private set

    /** Fire when the scored game itself totals exactly 0 points */
    var obvezniKlopOnZeroOutcome: Boolean by mutableStateOf(prefs.getBoolean("obvezniKlopOnZeroOutcome", true))
        private set

    /** Round-score trigger step: fire when a live net score hits a non-negative multiple of this. */
    var obvezniKlopStep: Int by mutableStateOf(prefs.getInt("obvezniKlopStep", 100))
        private set

    /** When on, radlc pips are read-only status — no manual tap-to-toggle. */
    var autoRadlci: Boolean by mutableStateOf(prefs.getBoolean("autoRadlci", true))
        private set

    var customRules: List<CustomScoringRules> by mutableStateOf(loadCustomRules())
        private set
    var currentRuleSet: CustomScoringRules by mutableStateOf(CustomScoringRules.standard())
        private set

    private val predefinedRules: Map<String, CustomScoringRules> =
        mapOf("Standard" to CustomScoringRules.standard())

    init {
        reloadCurrentRuleSet()
    }

    // ---- Setters -----------------------------------------------------------

    fun setAutoScoringEnabled(value: Boolean) {
        autoScoring = value
        prefs.edit().putBoolean("autoScoring", value).apply()
    }

    fun setHouseRules(name: String) {
        houseScoringRules = name
        prefs.edit().putString("houseScoringRules", name).apply()
        reloadCurrentRuleSet()
    }

    fun setShowAnimationsEnabled(value: Boolean) {
        showAnimations = value
        prefs.edit().putBoolean("showAnimations", value).apply()
    }

    fun setSoundEffectsEnabled(value: Boolean) {
        soundEffects = value
        prefs.edit().putBoolean("soundEffects", value).apply()
    }

    fun setObvezniKlop(value: Boolean) {
        obvezniKlopEnabled = value
        prefs.edit().putBoolean("obvezniKlopEnabled", value).apply()
    }

    fun setObvezniKlopRoundScore(value: Boolean) {
        obvezniKlopOnRoundScore = value
        prefs.edit().putBoolean("obvezniKlopOnRoundScore", value).apply()
    }

    fun setObvezniKlopZeroOutcome(value: Boolean) {
        obvezniKlopOnZeroOutcome = value
        prefs.edit().putBoolean("obvezniKlopOnZeroOutcome", value).apply()
    }

    fun updateObvezniKlopStep(value: Int) {
        obvezniKlopStep = value
        prefs.edit().putInt("obvezniKlopStep", value).apply()
    }

    fun updateAutoRadlci(value: Boolean) {
        autoRadlci = value
        prefs.edit().putBoolean("autoRadlci", value).apply()
    }

    /**
     * Update the active rule set in place (e.g. toggles bound to generalRules).
     * Always persisted as an override so quick-toggles survive relaunch even
     * for the predefined "Standard" rule set.
     */
    fun updateCurrentRuleSet(rules: CustomScoringRules) {
        currentRuleSet = rules
        saveCurrentRuleSetOverride()
        if (houseScoringRules != "Standard") {
            val idx = customRules.indexOfFirst { it.name == rules.name }
            if (idx >= 0) {
                customRules = customRules.toMutableList().also { it[idx] = rules }
                saveCustomRules()
            }
        }
    }

    private fun saveCurrentRuleSetOverride() {
        prefs.edit().putString("currentRuleSetOverride", json.encodeToString(currentRuleSet)).apply()
    }

    private fun loadCurrentRuleSetOverride(): CustomScoringRules? {
        val raw = prefs.getString("currentRuleSetOverride", null) ?: return null
        return try {
            json.decodeFromString<CustomScoringRules>(raw)
        } catch (_: Exception) {
            null
        }
    }

    // ---- Custom rules management -------------------------------------------

    private fun loadCustomRules(): List<CustomScoringRules> {
        val raw = prefs.getString("customScoringRules", null) ?: return emptyList()
        return try {
            json.decodeFromString(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveCustomRules() {
        prefs.edit().putString("customScoringRules", json.encodeToString(customRules)).apply()
    }

    private fun reloadCurrentRuleSet() {
        // Prefer the saved override while it still belongs to the selected rule
        // set; switching rule sets loads the base and re-saves it as the override.
        val override = loadCurrentRuleSetOverride()
        currentRuleSet = if (override != null && override.name == houseScoringRules) {
            override
        } else {
            val base = predefinedRules[houseScoringRules]
                ?: customRules.firstOrNull { it.name == houseScoringRules }
                ?: CustomScoringRules.standard()
            base.also { currentRuleSet = it; saveCurrentRuleSetOverride() }
        }
    }

    fun addOrUpdateCustomRuleSet(rules: CustomScoringRules, originalName: String? = null) {
        val list = customRules.toMutableList()
        val idx = list.indexOfFirst { it.name == (originalName ?: rules.name) }
        if (idx >= 0) list[idx] = rules else list.add(rules)
        customRules = list
        saveCustomRules()
        setHouseRules(rules.name)
    }

    fun deleteCustomRuleSet(name: String) {
        customRules = customRules.filterNot { it.name == name }
        saveCustomRules()
        if (houseScoringRules == name) setHouseRules("Standard")
    }

    fun getAllRuleSetNames(): List<String> =
        predefinedRules.keys.sorted() + customRules.map { it.name }

    // ---- Scoring helpers (port of SettingsManager.swift) --------------------

    fun contractUsesRazlika(contract: TarokContract, playerCount: Int): Boolean {
        val config = currentRuleSet.contractScores[contract.rawValue]
            ?: return if (playerCount == 3) {
                contract in setOf(TarokContract.TRI, TarokContract.DVE, TarokContract.ENA, TarokContract.SOLO_BREZ)
            } else {
                contract in setOf(
                    TarokContract.TRI, TarokContract.DVE, TarokContract.ENA,
                    TarokContract.SOLO_TRI, TarokContract.SOLO_DVE, TarokContract.SOLO_ENA,
                    TarokContract.SOLO_BREZ,
                )
            }
        return config.usesRazlika
    }

    fun contractHasFixedScoring(contract: TarokContract): Boolean =
        contract in setOf(
            TarokContract.BERAC, TarokContract.ODPRTI_BERAC,
            TarokContract.BARVNI_VALAT, TarokContract.VALAT,
            TarokContract.PENALTY_MOND, TarokContract.PENALTY_RENONS,
        )

    fun contractIsValatWithGamePoints(contract: TarokContract): Boolean =
        contract == TarokContract.VALAT && currentRuleSet.generalRules.valatWithGamePoints

    fun getBarvniValatScore(): Int =
        currentRuleSet.contractScores[TarokContract.BARVNI_VALAT.rawValue]?.fourPlusPlayerScore ?: 125

    fun getValatBaseScore(isAnnounced: Boolean): Int =
        if (isAnnounced) currentRuleSet.bonusScores.napovedanValat else currentRuleSet.bonusScores.tihiValat

    fun getContractBaseScore(contract: TarokContract, playerCount: Int): Int {
        val config = currentRuleSet.contractScores[contract.rawValue]
        if (config != null) {
            return if (playerCount == 3) config.threePlayerScore else config.fourPlusPlayerScore
        }
        return fallbackBaseScore(contract, playerCount)
    }

    private fun fallbackBaseScore(contract: TarokContract, playerCount: Int): Int = when (contract) {
        TarokContract.TRI -> 10
        TarokContract.DVE -> 20
        TarokContract.ENA -> 30
        TarokContract.SOLO_TRI -> if (playerCount == 3) 0 else 40
        TarokContract.SOLO_DVE -> if (playerCount == 3) 0 else 50
        TarokContract.SOLO_ENA -> if (playerCount == 3) 0 else 60
        TarokContract.BERAC -> 70
        TarokContract.SOLO_BREZ -> 80
        TarokContract.ODPRTI_BERAC -> 90
        TarokContract.BARVNI_VALAT -> 125
        TarokContract.VALAT -> 250
        TarokContract.PENALTY_MOND, TarokContract.PENALTY_RENONS -> 20
        TarokContract.PAGAT_BONUS -> 25
        TarokContract.KLOP -> 0
    }

    /**
     * Full game + bonuses total. Win/loss applies only to the game score;
     * bonuses are independent of the game outcome. Kontra multiplies only
     * the game score; per-bonus kontra is inside getBonusPoints.
     */
    fun calculateGameAndBonuses(
        contract: TarokContract,
        playerCount: Int,
        outcome: HandOutcome,
        razlika: Int = 0,
        bonuses: List<HandBonus> = emptyList(),
        kontraLevel: Int = 0,
    ): Int? {
        if (!autoScoring) return null

        var gameScore = getContractBaseScore(contract, playerCount)
        if (contractUsesRazlika(contract, playerCount)) gameScore += razlika

        if (outcome.isLoss) gameScore = -kotlin.math.abs(gameScore)
        gameScore *= (1 shl maxOf(0, kontraLevel))

        var bonusTotal = 0
        if (!contractHasFixedScoring(contract)) {
            for (bonus in bonuses) bonusTotal += getBonusPoints(bonus)
        }
        return gameScore + bonusTotal
    }

    fun getBonusPoints(bonus: HandBonus): Int {
        val cfg = currentRuleSet.bonusScores
        val basePoints = when (bonus.type) {
            BonusType.TRULA -> if (bonus.announced) cfg.trulaAnnounced else cfg.trulaBase
            BonusType.KRALJI -> if (bonus.announced) cfg.kraljAnnounced else cfg.kraljBase
            BonusType.KRALJ_ULTIMO -> if (bonus.announced) cfg.kraljUltimoAnnounced else cfg.kraljUltimoBase
            BonusType.PAGAT_ULTIMO -> if (bonus.announced) cfg.pagatUltimoAnnounced else cfg.pagatUltimoBase
            BonusType.MOND_KAZ -> return -cfg.mondKazBase // always a penalty
        }
        val multiplier = 1 shl bonus.kontraLevel
        return (if (bonus.won) kotlin.math.abs(basePoints) else -kotlin.math.abs(basePoints)) * multiplier
    }

    companion object {
        @Volatile
        private var instance: SettingsManager? = null

        fun get(context: Context): SettingsManager =
            instance ?: synchronized(this) {
                instance ?: SettingsManager(context.applicationContext).also { instance = it }
            }
    }
}
