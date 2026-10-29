package com.epodbreg.stih.data

import com.epodbreg.stih.model.CustomScoringRules
import com.epodbreg.stih.model.GameSession
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * The backup file shared with the iOS app ("stih-backup", spec in docs/BACKUP-FORMAT.md),
 * plus the automatic copies the phone keeps by itself. Port of Backup.swift.
 */

private val backupJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
}

/** A full backup: saved sessions, the unfinished game and settings. Dates are epoch ms. */
data class StihBackup(
    val sessions: List<GameSession>,
    val activeSession: GameSession? = null,
    val settings: BackupSettings? = null,
    val exportedAt: Long? = null,
    val app: AppInfo? = null,
    /** Sessions in the file that couldn't be read (set when reading, never written) */
    val skippedSessions: Int = 0,
) {
    @Serializable
    data class AppInfo(val platform: String, val version: String)

    fun encoded(appVersion: String = app?.version ?: "?"): String {
        val obj = buildJsonObject {
            put("format", FORMAT_NAME)
            put("version", CURRENT_VERSION)
            put("exportedAt", exportedAt ?: System.currentTimeMillis())
            put("app", backupJson.encodeToJsonElement(AppInfo.serializer(), app ?: AppInfo("android", appVersion)))
            put("sessions", backupJson.encodeToJsonElement(ListSerializer(GameSession.serializer()), sessions))
            // null when there's no unfinished game, like iOS
            put("activeSession", activeSession?.let { backupJson.encodeToJsonElement(GameSession.serializer(), it) } ?: JsonNull)
            settings?.let { put("settings", it.toJson()) }
        }
        return backupJson.encodeToString(JsonObject.serializer(), obj)
    }

    companion object {
        const val FORMAT_NAME = "stih-backup"
        const val CURRENT_VERSION = 1

        /**
         * Reads a backup file — or a bare history array as the phone stores it
         * (the automatic daily copies and the "-unreadable-" copies use that format).
         */
        fun read(text: String): StihBackup {
            val first = text.firstOrNull { !it.isWhitespace() && it != '﻿' }
            return when (first) {
                '[' -> {
                    val result = try {
                        SessionManager.decodeSessions(text)
                    } catch (_: Exception) {
                        throw BackupException(BackupError.UNREADABLE)
                    }
                    StihBackup(sessions = result.sessions, skippedSessions = result.failedCount)
                }
                '{' -> {
                    val obj = try {
                        backupJson.parseToJsonElement(text) as JsonObject
                    } catch (_: Exception) {
                        throw BackupException(BackupError.UNREADABLE)
                    }
                    fromJson(obj)
                }
                else -> throw BackupException(BackupError.NOT_A_BACKUP)
            }
        }

        private fun fromJson(obj: JsonObject): StihBackup {
            val format = (obj["format"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            val version = (obj["version"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
            if (format != FORMAT_NAME || version == null || version < 1) throw BackupException(BackupError.NOT_A_BACKUP)
            if (version > CURRENT_VERSION) throw BackupException(BackupError.NEWER_VERSION)

            // One by one, so a damaged session doesn't take the rest down
            val sessionElements = obj["sessions"] as? JsonArray ?: throw BackupException(BackupError.UNREADABLE)
            val sessions = sessionElements.mapNotNull(::decodeSession)
            var skipped = sessionElements.size - sessions.size

            val activeElement = obj["activeSession"]
            val active = activeElement?.takeIf { it !is JsonNull }?.let(::decodeSession)
            if (activeElement != null && activeElement !is JsonNull && active == null) skipped++

            return StihBackup(
                sessions = sessions,
                activeSession = active,
                settings = (obj["settings"] as? JsonObject)?.let(BackupSettings::fromJson),
                exportedAt = (obj["exportedAt"] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull,
                app = obj["app"]?.let { runCatching { backupJson.decodeFromJsonElement(AppInfo.serializer(), it) }.getOrNull() },
                skippedSessions = skipped,
            )
        }

        private fun decodeSession(element: JsonElement): GameSession? =
            runCatching { backupJson.decodeFromJsonElement(GameSession.serializer(), element) }.getOrNull()
    }
}

enum class BackupError(val message: String) {
    NOT_A_BACKUP("Ta datoteka ni Štihova varnostna kopija."),
    NEWER_VERSION("Kopijo je naredila novejša različica Štiha. Posodobi aplikacijo in poskusi znova."),
    UNREADABLE("Datoteke ni mogoče prebrati — morda je poškodovana."),
}

class BackupException(val error: BackupError) : Exception(error.message)

/**
 * Settings as stored in a backup — same keys as both apps' preferences. Every field is
 * optional, so a backup from an older or newer app restores whatever it has.
 */
data class BackupSettings(
    val autoScoring: Boolean? = null,
    val houseScoringRules: String? = null,
    val showAnimations: Boolean? = null,
    val soundEffects: Boolean? = null,
    val obvezniKlopEnabled: Boolean? = null,
    val obvezniKlopOnRoundScore: Boolean? = null,
    val obvezniKlopOnZeroOutcome: Boolean? = null,
    val obvezniKlopStep: Int? = null,
    val autoRadlci: Boolean? = null,
    /** CompactCardsMode raw value ("auto", "always", "never"); unknown values are ignored */
    val compactCards: String? = null,
    val myPlayerName: String? = null,
    val customRules: List<CustomScoringRules>? = null,
    val currentRuleSet: CustomScoringRules? = null,
) {
    fun toJson(): JsonObject = buildJsonObject {
        autoScoring?.let { put("autoScoring", it) }
        houseScoringRules?.let { put("houseScoringRules", it) }
        showAnimations?.let { put("showAnimations", it) }
        soundEffects?.let { put("soundEffects", it) }
        obvezniKlopEnabled?.let { put("obvezniKlopEnabled", it) }
        obvezniKlopOnRoundScore?.let { put("obvezniKlopOnRoundScore", it) }
        obvezniKlopOnZeroOutcome?.let { put("obvezniKlopOnZeroOutcome", it) }
        obvezniKlopStep?.let { put("obvezniKlopStep", it) }
        autoRadlci?.let { put("autoRadlci", it) }
        compactCards?.let { put("compactCards", it) }
        myPlayerName?.let { put("myPlayerName", it) }
        customRules?.let { put("customRules", backupJson.encodeToJsonElement(ListSerializer(CustomScoringRules.serializer()), it)) }
        currentRuleSet?.let { put("currentRuleSet", backupJson.encodeToJsonElement(CustomScoringRules.serializer(), it)) }
    }

    companion object {
        /** A field with the wrong type is skipped instead of failing the whole settings block */
        fun fromJson(obj: JsonObject): BackupSettings {
            fun bool(key: String) = (obj[key] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull
            fun int(key: String) = (obj[key] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
            fun string(key: String) = (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
            fun rules(element: JsonElement?) = element?.let {
                runCatching { backupJson.decodeFromJsonElement(CustomScoringRules.serializer(), it) }.getOrNull()
            }
            return BackupSettings(
                autoScoring = bool("autoScoring"),
                houseScoringRules = string("houseScoringRules"),
                showAnimations = bool("showAnimations"),
                soundEffects = bool("soundEffects"),
                obvezniKlopEnabled = bool("obvezniKlopEnabled"),
                obvezniKlopOnRoundScore = bool("obvezniKlopOnRoundScore"),
                obvezniKlopOnZeroOutcome = bool("obvezniKlopOnZeroOutcome"),
                obvezniKlopStep = int("obvezniKlopStep"),
                autoRadlci = bool("autoRadlci"),
                compactCards = string("compactCards"),
                myPlayerName = string("myPlayerName"),
                customRules = (obj["customRules"] as? JsonArray)?.mapNotNull(::rules),
                currentRuleSet = rules(obj["currentRuleSet"]),
            )
        }
    }
}

/**
 * What restoring a backup will do. Restoring only ever adds: a session that's already on
 * the phone (same id) stays exactly as it is on the phone.
 */
class RestorePlan(backup: StihBackup, saved: List<GameSession>, active: GameSession?) {
    val newSessions: List<GameSession>
    val alreadyOnPhone: Int
    val unreadable: Int = backup.skippedSessions
    /** The backup's unfinished game, to become the current game (null if none, or the phone has its own) */
    val activeSession: GameSession?
    /** The backup had an unfinished game, but the phone has its own — the phone's stays */
    val keepsPhoneGame: Boolean

    val addsNothing: Boolean get() = newSessions.isEmpty() && activeSession == null

    init {
        val known = saved.map { it.id }.toMutableSet()
        active?.let { known += it.id }
        val added = mutableListOf<GameSession>()
        var duplicates = 0
        for (session in backup.sessions) {
            if (known.add(session.id)) added += session else duplicates++
        }
        newSessions = added
        alreadyOnPhone = duplicates

        val unfinished = backup.activeSession
            ?.takeIf { it.players.isNotEmpty() && it.games.isNotEmpty() && it.id !in known }
        activeSession = unfinished?.takeIf { active == null }
        keepsPhoneGame = unfinished != null && active != null
    }
}

/**
 * Backup files: the one you share, and the automatic copies in filesDir/Backups —
 * - "samodejna-2026-10-23.json": the history as it was before the first save of that day (last 7)
 * - "pred-brisanjem-2026-10-23-2130.json": a full backup made before "Počisti zgodovino" (last 5)
 * Dates come from the file names.
 */
object BackupStore {
    const val DAILY_PREFIX = "samodejna-"
    const val BEFORE_CLEAR_PREFIX = "pred-brisanjem-"
    const val KEEP_DAILY = 7
    const val KEEP_BEFORE_CLEAR = 5

    private fun dayFormat() = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private fun minuteFormat() = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US)

    fun exportFileName(now: Long = System.currentTimeMillis()) =
        "stih-varnostna-kopija-${dayFormat().format(Date(now))}.json"

    /** Keeps today's copy of the history file, taken before today's first save overwrites it. */
    fun makeDailyCopy(historyFile: File, folder: File, now: Long = System.currentTimeMillis()) {
        if (!historyFile.exists()) return
        val copy = File(folder, "$DAILY_PREFIX${dayFormat().format(Date(now))}.json")
        if (copy.exists()) return
        try {
            folder.mkdirs()
            historyFile.copyTo(copy)
            prune(DAILY_PREFIX, KEEP_DAILY, folder)
        } catch (_: Exception) {
        }
    }

    /** Full backup written before the history is cleared, so clearing can be undone. Throws on failure. */
    fun writeBeforeClearCopy(backup: StihBackup, folder: File, appVersion: String, now: Long = System.currentTimeMillis()): File {
        if (!folder.exists() && !folder.mkdirs()) throw java.io.IOException("Can't create ${folder.path}")
        val file = File(folder, "$BEFORE_CLEAR_PREFIX${minuteFormat().format(Date(now))}.json")
        SessionManager.writeAtomically(file, backup.encoded(appVersion))
        prune(BEFORE_CLEAR_PREFIX, KEEP_BEFORE_CLEAR, folder)
        return file
    }

    enum class Kind { DAILY, BEFORE_CLEAR }

    data class AutomaticCopy(val file: File, val date: Long, val kind: Kind)

    /** Automatic copies on this phone, newest first */
    fun automaticCopies(folder: File): List<AutomaticCopy> =
        (folder.listFiles() ?: emptyArray())
            .filter { it.name.endsWith(".json") }
            .mapNotNull { file ->
                val stem = file.name.removeSuffix(".json")
                when {
                    stem.startsWith(DAILY_PREFIX) ->
                        parse(dayFormat(), stem.removePrefix(DAILY_PREFIX))?.let { AutomaticCopy(file, it, Kind.DAILY) }
                    stem.startsWith(BEFORE_CLEAR_PREFIX) ->
                        parse(minuteFormat(), stem.removePrefix(BEFORE_CLEAR_PREFIX))?.let { AutomaticCopy(file, it, Kind.BEFORE_CLEAR) }
                    else -> null
                }
            }
            .sortedByDescending { it.date }

    private fun parse(format: SimpleDateFormat, text: String): Long? =
        runCatching { format.apply { isLenient = false }.parse(text)?.time }.getOrNull()

    /** Deletes the oldest files with [prefix] beyond the newest [keep] (names sort by date). */
    fun prune(prefix: String, keep: Int, folder: File) {
        (folder.listFiles() ?: return)
            .filter { it.name.startsWith(prefix) && it.name.endsWith(".json") }
            .sortedByDescending { it.name }
            .drop(keep)
            .forEach { it.delete() }
    }
}
