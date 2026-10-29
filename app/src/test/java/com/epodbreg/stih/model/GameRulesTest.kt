package com.epodbreg.stih.model

import com.epodbreg.stih.data.MemoryStore
import com.epodbreg.stih.data.SettingsManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Port of GameRulesTests.swift. Since 1.9 a game keeps a copy of the rules it started with, and
 * a group remembers the rules of its last game. Settings changes mid-game don't touch it.
 */
class GameRulesTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun domaca(tri: Int = 99): CustomScoringRules {
        val rules = CustomScoringRules(name = "Domača")
        val key = TarokContract.TRI.rawValue
        return rules.copy(contractScores = rules.contractScores + (key to rules.contractScores.getValue(key).copy(fourPlusPlayerScore = tri)))
    }

    private fun session(group: String?, rules: CustomScoringRules?, startedDaysAgo: Double) = GameSession(
        players = listOf(Player(name = "Ana"), Player(name = "Bor"), Player(name = "Cene")),
        groupName = group,
        rules = rules,
        startTime = System.currentTimeMillis() - (startedDaysAgo * 86_400_000).toLong(),
    )

    private fun encoded(session: GameSession): JsonObject =
        json.encodeToJsonElement(GameSession.serializer(), session).jsonObject

    // Saved with the game

    @Test
    fun rulesTravelWithTheGame() {
        val original = session(group = "Torek", rules = domaca(), startedDaysAgo = 0.0)
        val decoded = json.decodeFromString(GameSession.serializer(), json.encodeToString(GameSession.serializer(), original))
        assertEquals(domaca(), decoded.rules)
    }

    @Test
    fun gamesFromBefore19HaveNoRules() {
        val withoutRules = JsonObject(encoded(session(group = null, rules = domaca(), startedDaysAgo = 0.0)) - "rules")
        val decoded = json.decodeFromJsonElement(GameSession.serializer(), withoutRules)
        assertNull(decoded.rules)
        assertEquals(3, decoded.players.size)
    }

    @Test
    fun damagedRulesDontLoseTheGame() {
        val damaged = JsonObject(encoded(session(group = null, rules = domaca(), startedDaysAgo = 0.0)) + ("rules" to JsonPrimitive("nonsense")))
        val decoded = json.decodeFromJsonElement(GameSession.serializer(), damaged)
        assertNull(decoded.rules)
        assertEquals(listOf("Ana", "Bor", "Cene"), decoded.players.map { it.name })
    }

    // A group remembers

    @Test
    fun groupUsesTheRulesOfItsLatestGame() {
        val standard = CustomScoringRules(name = "Standard")
        val sessions = listOf(
            session(group = "Torek", rules = standard, startedDaysAgo = 14.0),
            session(group = "Torek", rules = domaca(), startedDaysAgo = 7.0),
            session(group = "Torek", rules = null, startedDaysAgo = 1.0),   // before 1.9: skipped
            session(group = "Petek", rules = standard, startedDaysAgo = 0.0),
        )
        assertEquals("Domača", sessions.lastRuleSetName(inGroup = "Torek"))
        assertEquals("Standard", sessions.lastRuleSetName(inGroup = "Petek"))
        assertNull(sessions.lastRuleSetName(inGroup = "Nedelja"))
    }

    // Settings mid-game

    @Test
    fun scoringUsesTheGamesRulesNotTheSelectedOnes() {
        val settings = SettingsManager(MemoryStore())
        assertEquals(10, settings.getContractBaseScore(TarokContract.TRI, 4))   // Standard, no game open

        settings.playingRules = domaca(tri = 99)
        assertEquals("Domača", settings.rules.name)
        assertEquals(99, settings.getContractBaseScore(TarokContract.TRI, 4))

        // Editing the selected set in Settings leaves the running game alone
        val current = settings.currentRuleSet
        val key = TarokContract.TRI.rawValue
        settings.updateCurrentRuleSet(
            current.copy(contractScores = current.contractScores + (key to current.contractScores.getValue(key).copy(fourPlusPlayerScore = 15)))
        )
        assertEquals(99, settings.getContractBaseScore(TarokContract.TRI, 4))

        settings.playingRules = null
        assertEquals(15, settings.getContractBaseScore(TarokContract.TRI, 4))
    }
}
