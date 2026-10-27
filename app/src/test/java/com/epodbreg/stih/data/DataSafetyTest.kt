package com.epodbreg.stih.data

import com.epodbreg.stih.model.CustomScoringRules
import com.epodbreg.stih.model.GameSession
import com.epodbreg.stih.model.Player
import com.epodbreg.stih.model.TarokContract
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Port of DataSafetyTests.swift: loading never throws history away. */
class DataSafetyTest {

    private val json = Json { encodeDefaults = true }

    private fun sessionJson(names: List<String>): JsonElement =
        json.encodeToJsonElement(GameSession.serializer(), GameSession(players = names.map { Player(name = it) }))

    @Test
    fun oneBrokenSessionDoesNotLoseTheOthers() {
        val broken = buildJsonObject {
            put("id", JsonPrimitive("not-a-uuid"))
            put("players", JsonPrimitive("nonsense"))
        }
        val text = buildJsonArray {
            add(sessionJson(listOf("Ana", "Bor", "Cene")))
            add(broken)
            add(sessionJson(listOf("Elias", "Nina", "Marko", "Tjaša")))
        }.toString()

        val result = SessionManager.decodeSessions(text)
        assertEquals(2, result.sessions.size)
        assertEquals(1, result.failedCount)
        assertEquals(listOf(3, 4), result.sessions.map { it.playerNames.size })
    }

    @Test
    fun garbageFileThrowsInsteadOfLoadingEmpty() {
        try {
            SessionManager.decodeSessions("{ this is not json")
            fail("expected an exception")
        } catch (_: Exception) {
        }
    }

    @Test
    fun unreadableFileIsKeptAsACopy() {
        val dir = Files.createTempDirectory("stih").toFile()
        try {
            val file = File(dir, "tarok_sessions.json")
            val original = "[{\"half a session\""
            file.writeText(original)

            val backup = SessionManager.backUpUnreadableFile(file)
            assertNotNull(backup)
            assertTrue(backup!!.name.startsWith("tarok_sessions-unreadable-"))
            assertEquals(original, backup.readText())
            assertTrue(file.exists()) // original untouched too
        } finally {
            dir.deleteRecursively()
        }
    }

    /**
     * Saved contract points come from the rule set. Standard rules must give exactly the
     * points of the old hardcoded table, or every Standard game would change.
     */
    @Test
    fun standardRulesMatchTheOldHardcodedScores() {
        val rules = CustomScoringRules.standard()
        val old = listOf(
            Triple(TarokContract.TRI, 10, 10), Triple(TarokContract.DVE, 20, 20), Triple(TarokContract.ENA, 30, 30),
            Triple(TarokContract.SOLO_TRI, 0, 40), Triple(TarokContract.SOLO_DVE, 0, 50), Triple(TarokContract.SOLO_ENA, 0, 60),
            Triple(TarokContract.BERAC, 70, 70), Triple(TarokContract.SOLO_BREZ, 80, 80), Triple(TarokContract.ODPRTI_BERAC, 90, 90),
            Triple(TarokContract.BARVNI_VALAT, 125, 125),
        )
        for ((contract, three, fourPlus) in old) {
            val config = rules.contractScores[contract.rawValue] ?: error("$contract missing")
            assertEquals("$contract 3 players", three, config.threePlayerScore)
            assertEquals("$contract 4+ players", fourPlus, config.fourPlusPlayerScore)
        }
        // Valat is scored from tihi/napovedan values
        assertEquals(250, rules.bonusScores.tihiValat)
    }
}
