package com.epodbreg.stih.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.epodbreg.stih.model.BonusType
import com.epodbreg.stih.model.CompactCardsMode
import com.epodbreg.stih.model.CustomScoringRules
import com.epodbreg.stih.model.GeneralRulesConfig
import com.epodbreg.stih.model.HandBonus
import com.epodbreg.stih.model.HandOutcome
import com.epodbreg.stih.model.TarokContract
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Where settings live: SharedPreferences in the app, a plain map in tests */
interface SettingsStore {
    fun contains(key: String): Boolean
    fun getBoolean(key: String, default: Boolean): Boolean
    fun getInt(key: String, default: Int): Int
    fun getString(key: String): String?
    fun putBoolean(key: String, value: Boolean)
    fun putInt(key: String, value: Int)
    fun putString(key: String, value: String)
}

class SharedPrefsStore(private val prefs: SharedPreferences) : SettingsStore {
    override fun contains(key: String) = prefs.contains(key)
    override fun getBoolean(key: String, default: Boolean) = prefs.getBoolean(key, default)
    override fun getInt(key: String, default: Int) = prefs.getInt(key, default)
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun putBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    override fun putInt(key: String, value: Int) = prefs.edit().putInt(key, value).apply()
    override fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
}

/**
 * App settings + house-rule scoring. Port of SettingsManager.swift.
 *
 * Since 1.9 everything that depends on the table (radlci, klop, obvezni klop, renons …) lives in
 * the rule set. [currentRuleSet] is the selected set, which Settings edits; [rules] is what
 * scoring reads: the open game's own copy ([playingRules]) if there is one.
 */
class SettingsManager(private val store: SettingsStore) {

    constructor(context: Context) : this(
        SharedPrefsStore(context.getSharedPreferences("stih_settings", Context.MODE_PRIVATE))
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    var autoScoring: Boolean by mutableStateOf(store.getBoolean("autoScoring", true))
        private set
    var houseScoringRules: String by mutableStateOf(store.getString("houseScoringRules") ?: STANDARD)
        private set
    /** Kept for backups (both apps write it); nothing reads it */
    var showAnimations: Boolean by mutableStateOf(store.getBoolean("showAnimations", true))
        private set
    var soundEffects: Boolean by mutableStateOf(store.getBoolean("soundEffects", false))
        private set

    /** When on, radlc pips are read-only status — no manual tap-to-toggle. */
    var autoRadlci: Boolean by mutableStateOf(store.getBoolean("autoRadlci", true))
        private set

    /** Game screen: leader full width, the others as half cards two per row */
    var compactCards: CompactCardsMode by mutableStateOf(
        CompactCardsMode.fromRaw(store.getString("compactCards")) ?: CompactCardsMode.AUTO
    )
        private set

    /** The player whose statistics the stats screen shows; remembered across launches. */
    var myPlayerName: String by mutableStateOf(store.getString("myPlayerName") ?: "")
        private set

    var customRules: List<CustomScoringRules> by mutableStateOf(loadCustomRules())
        private set
    var currentRuleSet: CustomScoringRules by mutableStateOf(CustomScoringRules.standard())
        private set

    /** The open game's own rules (see GameSession.rules); null when no game is in progress. */
    var playingRules: CustomScoringRules? by mutableStateOf(null)

    /** What scoring reads: the game's own rules, else the selected set. */
    val rules: CustomScoringRules get() = playingRules ?: currentRuleSet

    private val predefinedRules: Map<String, CustomScoringRules> =
        mapOf(STANDARD to CustomScoringRules.standard())

    init {
        reloadCurrentRuleSet()
        migrateObvezniKlopIntoRules()
    }

    // ---- Setters -----------------------------------------------------------

    fun setAutoScoringEnabled(value: Boolean) {
        autoScoring = value
        store.putBoolean("autoScoring", value)
    }

    fun setHouseRules(name: String) {
        houseScoringRules = name
        store.putString("houseScoringRules", name)
        reloadCurrentRuleSet()
    }

    fun setShowAnimationsEnabled(value: Boolean) {
        showAnimations = value
        store.putBoolean("showAnimations", value)
    }

    fun setSoundEffectsEnabled(value: Boolean) {
        soundEffects = value
        store.putBoolean("soundEffects", value)
    }

    fun updateAutoRadlci(value: Boolean) {
        autoRadlci = value
        store.putBoolean("autoRadlci", value)
    }

    fun updateCompactCards(value: CompactCardsMode) {
        compactCards = value
        store.putString("compactCards", value.rawValue)
    }

    fun updateMyPlayerName(value: String) {
        myPlayerName = value
        store.putString("myPlayerName", value)
    }

    /**
     * Changes the selected rule set (the switches edited in Pravila). It's saved as the override,
     * Standard keeps its own copy (so switching sets and back doesn't reset it), and the custom
     * set of the same name stays in step, so the editor and backups see what the game sees.
     */
    fun updateCurrentRuleSet(rules: CustomScoringRules) {
        currentRuleSet = rules
        val encoded = json.encodeToString(rules)
        store.putString(KEY_OVERRIDE, encoded)
        if (rules.name in predefinedRules) store.putString(KEY_STANDARD_SWITCHES, encoded)
        val index = customRules.indexOfFirst { it.name == rules.name }
        if (index >= 0 && customRules[index] != rules) {
            customRules = customRules.toMutableList().also { it[index] = rules }
            saveCustomRules()
        }
    }

    /** Shortcut for editing one switch of the selected set */
    fun updateGeneralRules(change: (GeneralRulesConfig) -> GeneralRulesConfig) {
        updateCurrentRuleSet(currentRuleSet.copy(generalRules = change(currentRuleSet.generalRules)))
    }

    private fun loadOverride(): CustomScoringRules? = decodeRules(store.getString(KEY_OVERRIDE))

    private fun storedStandardRules(): CustomScoringRules? = decodeRules(store.getString(KEY_STANDARD_SWITCHES))

    private fun decodeRules(raw: String?): CustomScoringRules? = raw?.let {
        try {
            json.decodeFromString<CustomScoringRules>(it)
        } catch (_: Exception) {
            null
        }
    }

    private fun reloadCurrentRuleSet() {
        // Prefer the saved override while it still belongs to the selected rule set;
        // switching sets loads the base and re-saves it as the override.
        val override = loadOverride()
        val next = when {
            override != null && override.name == houseScoringRules -> override
            houseScoringRules in predefinedRules ->
                storedStandardRules() ?: predefinedRules.getValue(houseScoringRules)
            else -> customRules.firstOrNull { it.name == houseScoringRules } ?: CustomScoringRules.standard()
        }
        updateCurrentRuleSet(next)
    }

    /**
     * Until 1.9 obvezni klop was one setting for the whole app. Copy whatever was set into every
     * rule set (Standard included) once, so nobody's table changes behaviour after the update.
     */
    private fun migrateObvezniKlopIntoRules() {
        if (store.getBoolean(KEY_OBVEZNI_MIGRATED, false)) return

        fun copyOldValues(rules: GeneralRulesConfig): GeneralRulesConfig {
            var result = rules
            if (store.contains("obvezniKlopEnabled")) {
                result = result.copy(obvezniKlopEnabled = store.getBoolean("obvezniKlopEnabled", false))
            }
            if (store.contains("obvezniKlopOnRoundScore")) {
                result = result.copy(obvezniKlopOnRoundScore = store.getBoolean("obvezniKlopOnRoundScore", true))
            }
            if (store.contains("obvezniKlopOnZeroOutcome")) {
                result = result.copy(obvezniKlopOnZeroOutcome = store.getBoolean("obvezniKlopOnZeroOutcome", true))
            }
            val step = store.getInt("obvezniKlopStep", 0)
            if (step > 0) result = result.copy(obvezniKlopStep = step)
            return result
        }

        val standard = storedStandardRules() ?: CustomScoringRules.standard()
        store.putString(
            KEY_STANDARD_SWITCHES,
            json.encodeToString(standard.copy(generalRules = copyOldValues(standard.generalRules))),
        )
        replaceCustomRules(customRules.map { it.copy(generalRules = copyOldValues(it.generalRules)) })
        updateGeneralRules(::copyOldValues)
        store.putBoolean(KEY_OBVEZNI_MIGRATED, true)
    }

    // ---- Custom rules management -------------------------------------------

    private fun loadCustomRules(): List<CustomScoringRules> {
        val raw = store.getString("customScoringRules") ?: return emptyList()
        return try {
            json.decodeFromString(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveCustomRules() {
        store.putString("customScoringRules", json.encodeToString(customRules))
    }

    /** Replaces the saved custom rule sets (the selected one isn't reloaded) */
    fun replaceCustomRules(rules: List<CustomScoringRules>) {
        customRules = rules
        saveCustomRules()
    }

    fun getAllRuleSetNames(): List<String> =
        predefinedRules.keys.sorted() + customRules.map { it.name }

    /** [base], or "base 2", "base 3" … — whichever isn't taken yet */
    fun uniqueRuleSetName(base: String): String {
        val trimmed = base.trim()
        val taken = getAllRuleSetNames().toSet()
        if (trimmed !in taken) return trimmed
        var number = 2
        while ("$trimmed $number" in taken) number++
        return "$trimmed $number"
    }

    /** Whether [name] is a built-in set (Standard): its points are fixed, only its switches change */
    fun isPredefined(name: String): Boolean = name in predefinedRules && customRules.none { it.name == name }

    /**
     * Saves what the rules editor returns. [original] is the set it was opened with: Standard
     * (switches only), a saved custom set (kept selected if it was, also after a rename), or a
     * new one (added under a free name and selected).
     */
    fun saveEditedRules(original: CustomScoringRules, updated: CustomScoringRules) {
        val savedIndex = customRules.indexOfFirst { it.name == original.name }
        when {
            savedIndex < 0 && original.name in predefinedRules -> updateCurrentRuleSet(updated)
            savedIndex >= 0 -> {
                val wasActive = houseScoringRules == original.name
                // Renamed onto another set's name → number it instead of clashing
                val renamed = if (updated.name.trim() != original.name) {
                    updated.copy(name = uniqueRuleSetName(updated.name))
                } else updated.copy(name = original.name)
                replaceCustomRules(customRules.toMutableList().also { it[savedIndex] = renamed })
                if (wasActive) {
                    houseScoringRules = renamed.name
                    store.putString("houseScoringRules", renamed.name)
                    updateCurrentRuleSet(renamed)
                }
            }
            else -> {
                val added = updated.copy(name = uniqueRuleSetName(updated.name))
                replaceCustomRules(customRules + added)
                setHouseRules(added.name)
            }
        }
    }

    fun deleteCustomRuleSet(name: String) {
        replaceCustomRules(customRules.filterNot { it.name == name })
        if (houseScoringRules == name) setHouseRules(STANDARD)
    }

    // ---- Backup ---------------------------------------------------------------

    /** Current settings for a backup file (same keys as the iOS app). */
    fun toBackupSettings(): BackupSettings {
        // Also inside currentRuleSet since 1.9; kept at the top for older apps
        val general = currentRuleSet.generalRules
        return BackupSettings(
            autoScoring = autoScoring,
            houseScoringRules = houseScoringRules,
            showAnimations = showAnimations,
            soundEffects = soundEffects,
            obvezniKlopEnabled = general.obvezniKlopEnabled,
            obvezniKlopOnRoundScore = general.obvezniKlopOnRoundScore,
            obvezniKlopOnZeroOutcome = general.obvezniKlopOnZeroOutcome,
            obvezniKlopStep = general.klopStep,
            autoRadlci = autoRadlci,
            compactCards = compactCards.rawValue,
            myPlayerName = myPlayerName.ifEmpty { null },
            customRules = customRules,
            currentRuleSet = currentRuleSet,
        )
    }

    /**
     * Restores settings from a backup. Custom rule sets merge by name (the backup's version
     * wins; ones only on this phone stay). The house rules switch only to a rule set that
     * exists, and the active set (with its switches) only if its name matches.
     */
    fun applyBackup(backup: BackupSettings) {
        backup.autoScoring?.let(::setAutoScoringEnabled)
        backup.showAnimations?.let(::setShowAnimationsEnabled)
        backup.soundEffects?.let(::setSoundEffectsEnabled)
        backup.autoRadlci?.let(::updateAutoRadlci)
        CompactCardsMode.fromRaw(backup.compactCards)?.let(::updateCompactCards)
        backup.customRules?.takeIf { it.isNotEmpty() }?.let { incoming ->
            val names = incoming.map { it.name }.toSet()
            replaceCustomRules(customRules.filterNot { it.name in names } + incoming)
            // The selected set was replaced: load the backup's version now, before anything
            // saves the phone's old copy back over it
            incoming.firstOrNull { it.name == houseScoringRules }?.let(::updateCurrentRuleSet)
        }
        backup.houseScoringRules?.takeIf { it in getAllRuleSetNames() }?.let { house ->
            setHouseRules(house)
            backup.currentRuleSet?.takeIf { it.name == house }?.let(::updateCurrentRuleSet)
        }
        // The top-level obvezni klop keys (written by every version) win
        updateGeneralRules { general ->
            var result = general
            backup.obvezniKlopEnabled?.let { result = result.copy(obvezniKlopEnabled = it) }
            backup.obvezniKlopOnRoundScore?.let { result = result.copy(obvezniKlopOnRoundScore = it) }
            backup.obvezniKlopOnZeroOutcome?.let { result = result.copy(obvezniKlopOnZeroOutcome = it) }
            backup.obvezniKlopStep?.takeIf { it > 0 }?.let { result = result.copy(obvezniKlopStep = it) }
            result
        }
        backup.myPlayerName?.takeIf { it.isNotEmpty() }?.let(::updateMyPlayerName)
    }

    // ---- Scoring helpers (port of SettingsManager.swift) — all read [rules] -----

    fun contractUsesRazlika(contract: TarokContract, playerCount: Int): Boolean {
        val config = rules.contractScores[contract.rawValue]
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
        contract == TarokContract.VALAT && rules.generalRules.valatWithGamePoints

    fun getValatBaseScore(isAnnounced: Boolean): Int =
        if (isAnnounced) rules.bonusScores.napovedanValat else rules.bonusScores.tihiValat

    fun getContractBaseScore(contract: TarokContract, playerCount: Int): Int {
        val config = rules.contractScores[contract.rawValue]
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
        val cfg = rules.bonusScores
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
        const val STANDARD = "Standard"
        private const val KEY_OVERRIDE = "currentRuleSetOverride"
        private const val KEY_STANDARD_SWITCHES = "standardRuleSetSwitches"
        private const val KEY_OBVEZNI_MIGRATED = "obvezniKlopMovedIntoRules"

        @Volatile
        private var instance: SettingsManager? = null

        fun get(context: Context): SettingsManager =
            instance ?: synchronized(this) {
                instance ?: SettingsManager(context.applicationContext).also { instance = it }
            }
    }
}
