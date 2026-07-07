package com.epodbreg.stih.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.epodbreg.stih.model.GameSession
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Session persistence. Port of SessionManager.swift — JSON files in filesDir.
 */
class SessionManager(context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val sessionsFile = File(context.filesDir, "tarok_sessions.json")
    private val activeSessionFile = File(context.filesDir, "active_session.json")

    var activeSession: GameSession? by mutableStateOf(null)
        private set
    var savedSessions: List<GameSession> by mutableStateOf(emptyList())
        private set

    init {
        loadSavedSessionsSafely()
        loadActiveSessionSafely()
    }

    private fun loadSavedSessionsSafely() {
        savedSessions = try {
            if (sessionsFile.exists()) json.decodeFromString(sessionsFile.readText()) else emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun loadActiveSessionSafely() {
        activeSession = try {
            if (activeSessionFile.exists()) {
                val session: GameSession = json.decodeFromString(activeSessionFile.readText())
                if (session.players.isEmpty()) {
                    activeSessionFile.delete()
                    null
                } else session
            } else null
        } catch (_: Exception) {
            activeSessionFile.delete()
            null
        }
    }

    fun startNewSession(session: GameSession) {
        activeSession = session
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

    private fun persistSessions() {
        try {
            sessionsFile.writeText(json.encodeToString(savedSessions))
        } catch (_: Exception) {
        }
    }

    private fun persistActiveSession() {
        try {
            val session = activeSession
            if (session != null) {
                activeSessionFile.writeText(json.encodeToString(session))
            } else {
                activeSessionFile.delete()
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        @Volatile
        private var instance: SessionManager? = null

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
