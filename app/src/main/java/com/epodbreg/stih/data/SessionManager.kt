package com.epodbreg.stih.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.epodbreg.stih.model.GameSession
import com.epodbreg.stih.model.TarokStrings
import com.epodbreg.stih.model.groupNames
import com.epodbreg.stih.model.normalizedGroup
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Session persistence. Port of SessionManager.swift — JSON files in filesDir.
 */
class SessionManager(context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val sessionsFile = File(context.filesDir, "tarok_sessions.json")
    private val activeSessionFile = File(context.filesDir, "active_session.json")

    /**
     * Set when the history file exists but could neither be read nor copied aside — then we
     * must not overwrite it, or months of history would be replaced by an empty list.
     */
    private var sessionsFileIsProtected = false

    var activeSession: GameSession? by mutableStateOf(null)
        private set
    var savedSessions: List<GameSession> by mutableStateOf(emptyList())
        private set

    init {
        loadSavedSessionsSafely()
        loadActiveSessionSafely()
    }

    private fun loadSavedSessionsSafely() {
        if (!sessionsFile.exists()) {
            savedSessions = emptyList()
            return
        }
        try {
            val result = decodeSessions(sessionsFile.readText())
            savedSessions = result.sessions
            // Keep the original — the skipped sessions are still in it
            if (result.failedCount > 0) backUpUnreadableFile(sessionsFile)
        } catch (_: Exception) {
            savedSessions = emptyList()
            if (backUpUnreadableFile(sessionsFile) == null) sessionsFileIsProtected = true
        }
    }

    private fun loadActiveSessionSafely() {
        if (!activeSessionFile.exists()) {
            activeSession = null
            return
        }
        activeSession = try {
            val session: GameSession = json.decodeFromString(activeSessionFile.readText())
            if (session.players.isEmpty()) {
                activeSessionFile.delete()
                null
            } else session
        } catch (_: Exception) {
            // Keep a copy first — it may hold tonight's unsaved game
            backUpUnreadableFile(activeSessionFile)
            activeSessionFile.delete()
            null
        }
    }

    fun startNewSession(session: GameSession) {
        activeSession = session.copy(groupName = normalizedGroup(session.groupName))
        persistActiveSession()
    }

    fun endCurrentSession(): GameSession? {
        val session = activeSession ?: return null
        activeSession = null
        activeSessionFile.delete()
        return session.copy(endTime = System.currentTimeMillis())
    }

    fun updateActiveSession(session: GameSession) {
        activeSession = session
        persistActiveSession()
    }

    fun setActiveSessionDirect(session: GameSession?) {
        activeSession = session
        persistActiveSession()
    }

    fun saveSession(session: GameSession) {
        savedSessions = savedSessions.filterNot { it.id == session.id } + session
        persistSessions()
    }

    fun deleteSession(sessionId: java.util.UUID) {
        savedSessions = savedSessions.filterNot { it.id == sessionId }
        persistSessions()
    }

    fun clearAllSessions() {
        savedSessions = emptyList()
        persistSessions()
    }

    fun clearActiveSession() {
        activeSession = null
        activeSessionFile.delete()
    }

    // ---- Player rename / merge ----------------------------------------------

    data class NameCount(val name: String, val sessionCount: Int)

    sealed interface RenameOutcome {
        data class Renamed(val sessionCount: Int) : RenameOutcome
        /** Both names appear in the same session(s) — refusing to fuse two real people */
        data class Conflict(val sessionDates: List<Long>) : RenameOutcome
    }

    /** Every distinct player name across saved sessions, with how many sessions it appears in. */
    val allPlayerNames: List<NameCount>
        get() = savedSessions
            .flatMap { it.playerNames.toSet() }
            .groupingBy { it }.eachCount()
            .map { NameCount(it.key, it.value) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    /** Saved names, most sessions first (then by name), placeholders left out — for quick-pick. */
    val frequentPlayerNames: List<String>
        get() = allPlayerNames
            .filterNot { TarokStrings.isPlaceholderName(it.name) }
            .sortedWith(compareByDescending<NameCount> { it.sessionCount }.thenBy { it.name })
            .map { it.name }

    /**
     * Rename [oldName] to [newName] in all saved sessions and the active one.
     * If [newName] already exists this merges the two — unless they ever played together.
     */
    /**
     * [sessionIds] limits the rename to these saved sessions — a *split*, for when two
     * different people were saved under one name. null = everywhere, including the active session.
     */
    fun renamePlayer(oldName: String, newName: String, sessionIds: Set<UUID>? = null): RenameOutcome {
        val target = newName.trim()
        if (target.isEmpty() || target == oldName) return RenameOutcome.Renamed(0)

        fun inScope(session: GameSession) = sessionIds?.contains(session.id) ?: true
        val activeInScope = if (sessionIds == null) listOfNotNull(activeSession) else emptyList()
        val conflicts = (savedSessions.filter(::inScope) + activeInScope)
            .filter { it.hasConflict(oldName, target) }
        if (conflicts.isNotEmpty()) return RenameOutcome.Conflict(conflicts.map { it.startTime })

        val affected = savedSessions.count { inScope(it) && oldName in it.playerNames }
        savedSessions = savedSessions.map { if (inScope(it)) it.renamingPlayer(oldName, target) else it }
        persistSessions()
        if (sessionIds == null) {
            activeSession?.takeIf { oldName in it.playerNames }?.let {
                activeSession = it.renamingPlayer(oldName, target)
                persistActiveSession()
            }
        }
        return RenameOutcome.Renamed(affected)
    }

    // ---- Session groups -------------------------------------------------------

    /** Group names in use across saved sessions, most sessions first */
    val allGroupNames: List<String> get() = savedSessions.groupNames

    /** Put sessions into a group (null or blank = remove them from any group). */
    fun setGroup(group: String?, sessionIds: Set<UUID>) {
        val normalized = normalizedGroup(group)
        savedSessions = savedSessions.map { if (it.id in sessionIds) it.copy(groupName = normalized) else it }
        persistSessions()
    }

    /** Removes the group from every session (the sessions themselves stay). */
    fun deleteGroup(name: String) {
        setGroup(null, savedSessions.filter { it.groupName == name }.map { it.id }.toSet())
    }

    // All writes and deletes run synchronously on the caller's thread, so they always land
    // in order (iOS 1.7 had to add a serial queue to get this).

    private fun persistSessions() {
        if (sessionsFileIsProtected) return
        try {
            writeAtomically(sessionsFile, json.encodeToString(savedSessions))
        } catch (_: Exception) {
        }
    }

    private fun persistActiveSession() {
        try {
            val session = activeSession
            if (session != null) {
                writeAtomically(activeSessionFile, json.encodeToString(session))
            } else {
                activeSessionFile.delete()
            }
        } catch (_: Exception) {
        }
    }

    data class DecodeResult(val sessions: List<GameSession>, val failedCount: Int)

    companion object {
        @Volatile
        private var instance: SessionManager? = null

        private val lenientJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        /**
         * Decodes saved sessions one by one, so a single broken session can't take the rest
         * down. Throws only when the text isn't a JSON array at all.
         */
        fun decodeSessions(text: String): DecodeResult {
            val elements = lenientJson.parseToJsonElement(text).jsonArray
            val sessions = elements.mapNotNull { element ->
                try {
                    lenientJson.decodeFromJsonElement(GameSession.serializer(), element)
                } catch (_: Exception) {
                    null
                }
            }
            return DecodeResult(sessions, elements.size - sessions.size)
        }

        /**
         * Copies a file that failed to load to "<name>-unreadable-<time>.json" next to it,
         * before any save can overwrite it. Returns the copy, or null if copying failed.
         */
        fun backUpUnreadableFile(file: File): File? {
            val stamp = SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss", Locale.US).format(Date())
            val backup = File(file.parentFile, "${file.nameWithoutExtension}-unreadable-$stamp.json")
            return try {
                file.copyTo(backup, overwrite = true)
            } catch (_: Exception) {
                null
            }
        }

        /** Write to a temp file and rename, so a crash mid-write can't leave a truncated file. */
        fun writeAtomically(file: File, text: String) {
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) {
                file.writeText(text)
                tmp.delete()
            }
        }

        fun get(context: Context): SessionManager =
            instance ?: synchronized(this) {
                instance ?: SessionManager(context.applicationContext).also { instance = it }
            }

        fun formatDate(epochMillis: Long): String =
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(epochMillis))

        fun formatSessionDate(epochMillis: Long): String =
            SimpleDateFormat("d. MMM yyyy", Locale("sl", "SI")).format(Date(epochMillis))

        fun formatTime(epochMillis: Long): String =
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMillis))

        fun getSessionName(session: GameSession): String =
            session.playerNames.joinToString(", ") + " - " + formatDate(session.startTime)
    }
}
