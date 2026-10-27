package com.epodbreg.stih.data

import com.epodbreg.stih.model.BonusType
import com.epodbreg.stih.model.GameEntry
import com.epodbreg.stih.model.GameSession
import com.epodbreg.stih.model.HandBonus
import com.epodbreg.stih.model.HandOutcome
import com.epodbreg.stih.model.Player
import com.epodbreg.stih.model.PointsBreakdown
import com.epodbreg.stih.model.TarokContract
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.UUID

/** Port of BackupTests.swift: the backup file works across iPhone and Android, and restoring only adds. */
class BackupTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Same file as docs/fixtures/backup-v1.json — the iOS tests read it too */
    private fun fixture(): String =
        javaClass.classLoader!!.getResource("backup-v1.json")!!.readText()

    private fun fixtureObject(): JsonObject = json.parseToJsonElement(fixture()).jsonObject

    private fun session(names: List<String>, group: String? = null, rounds: Int = 0) = GameSession(
        players = names.map { Player(name = it) },
        groupName = group,
        games = List(rounds) {
            GameEntry(
                contract = TarokContract.TRI, declarerName = names[0], partnerName = null, outcome = HandOutcome.ZMAGAL,
                pointsBreakdown = PointsBreakdown(gamePoints = 10, kontraLevel = 0), totalPoints = 10,
            )
        },
    )

    private fun <T> withTempDir(block: (File) -> T): T {
        val dir = Files.createTempDirectory("stih").toFile()
        try {
            return block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun assertError(expected: BackupError, text: String) {
        try {
            StihBackup.read(text)
            fail("expected $expected")
        } catch (e: BackupException) {
            assertEquals(expected, e.error)
        }
    }

    // ---- iPhone ↔ Android ----

    @Test
    fun sharedFixtureReadsCompletely() {
        val backup = StihBackup.read(fixture())
        assertEquals(StihBackup.AppInfo("android", "1.8"), backup.app)
        assertEquals(1_792_783_800_000L, backup.exportedAt)
        assertEquals(2, backup.sessions.size)
        assertEquals(0, backup.skippedSessions)

        val tuesday = backup.sessions.first()
        assertEquals(UUID.fromString("6F1C2A9E-3B4D-4C5E-9F60-7A8B9C0D1E2F"), tuesday.id) // either case reads
        assertEquals(1_792_602_000_000L, tuesday.startTime)
        assertEquals("Torek", tuesday.groupName)
        assertEquals(0, tuesday.dealerManualOffset)
        assertEquals(listOf("Elias", "Nina", "Marko A.", "Tjaša"), tuesday.playerNames)
        assertEquals(2, tuesday.games.size)
        assertEquals("Nina", tuesday.games[0].partnerName)
        assertEquals("Marko A.", tuesday.games[0].kontraCallerName)
        assertEquals(BonusType.TRULA, tuesday.games[0].bonuses.first().type)
        assertEquals(70, tuesday.games[1].klopScores?.get("Nina"))
        assertNull(backup.sessions[1].dealerManualOffset)
        assertNull(backup.sessions[1].groupName)
        assertEquals(1, backup.activeSession?.games?.size)

        val settings = backup.settings!!
        assertEquals("Domača", settings.houseScoringRules)
        assertEquals(1000, settings.obvezniKlopStep)
        assertEquals(false, settings.obvezniKlopOnZeroOutcome)
        assertEquals("Elias", settings.myPlayerName)
        assertEquals(30, settings.customRules?.first()?.generalRules?.renonsPenalty)
        assertEquals("Domača", settings.currentRuleSet?.name)
    }

    @Test
    fun datesAreWholeMillisecondsAndDealerFieldUsesTheSharedName() {
        val played = session(listOf("Ana", "Bor", "Cene")).copy(startTime = 1_792_602_000_123L, dealerManualOffset = 2)
        val obj = json.parseToJsonElement(StihBackup(sessions = listOf(played)).encoded("1.8")).jsonObject

        assertEquals("stih-backup", obj["format"]?.jsonPrimitive?.content)
        assertEquals(1, obj["version"]?.jsonPrimitive?.content?.toInt())
        assertEquals(JsonNull, obj["activeSession"]) // written as null, like iOS
        val first = obj["sessions"]!!.jsonArray.first().jsonObject
        val start = first["startTime"]!!.jsonPrimitive
        assertFalse(start.content.contains('.')) // iOS reads dates as whole ms
        assertEquals(1_792_602_000_123L, start.long)
        // iOS's name for the dealer adjustment, not Android's property name
        assertEquals(2, first["dealerOffset"]?.jsonPrimitive?.content?.toInt())
        assertFalse(first.containsKey("dealerManualOffset"))
    }

    @Test
    fun roundTripKeepsSessions() {
        val tuesday = session(listOf("Elias", "Nina", "Marko", "Tjaša"), "Torek").copy(
            startTime = 1_792_602_000_000L,
            endTime = 1_792_617_300_000L,
            dealerManualOffset = 2,
            games = listOf(
                GameEntry(
                    contract = TarokContract.DVE, declarerName = "Elias", partnerName = "Nina", outcome = HandOutcome.ZMAGAL,
                    pointsBreakdown = PointsBreakdown(gamePoints = 20, difference = 15, kontraLevel = 1),
                    bonuses = listOf(HandBonus(BonusType.TRULA, announced = true, won = true)),
                    totalPoints = 90, timestamp = 1_792_603_000_000L, kontraCallerName = "Marko",
                ),
            ),
        )
        val restored = StihBackup.read(StihBackup(sessions = listOf(tuesday)).encoded("1.8"))
        assertEquals(listOf(tuesday), restored.sessions)
        assertNull(restored.activeSession)
        assertEquals("android", restored.app?.platform)
    }

    // ---- Restore only adds ----

    @Test
    fun restoreOnlyAddsWhatIsMissing() {
        val shared = session(listOf("Ana", "Bor", "Cene"))
        val phoneVersion = shared.copy(groupName = "Sreda") // changed on the phone after the backup was made
        val onlyInBackup = session(listOf("Elias", "Nina", "Jure"))
        val onlyOnPhone = session(listOf("Mojca", "Luka", "Urban"))
        val backup = StihBackup(sessions = listOf(shared, onlyInBackup, onlyInBackup), skippedSessions = 1)

        val plan = RestorePlan(backup, saved = listOf(phoneVersion, onlyOnPhone), active = null)
        assertEquals(listOf(onlyInBackup.id), plan.newSessions.map { it.id })
        assertEquals(2, plan.alreadyOnPhone) // the shared one + the duplicate in the file
        assertEquals(1, plan.unreadable)
        assertNull(plan.activeSession)
    }

    @Test
    fun unfinishedGameIsRestoredOnlyWhenThePhoneHasNone() {
        val unfinished = session(listOf("Ana", "Bor", "Cene"), rounds = 2)
        val backup = StihBackup(sessions = emptyList(), activeSession = unfinished)

        assertEquals(unfinished.id, RestorePlan(backup, emptyList(), null).activeSession?.id)

        val busy = RestorePlan(backup, emptyList(), session(listOf("Elias", "Nina", "Jure"), rounds = 1))
        assertNull(busy.activeSession)
        assertTrue(busy.keepsPhoneGame)

        val alreadySaved = RestorePlan(backup, listOf(unfinished), null)
        assertNull(alreadySaved.activeSession)
        assertFalse(alreadySaved.keepsPhoneGame)

        val noRounds = StihBackup(sessions = emptyList(), activeSession = session(listOf("Ana", "Bor", "Cene")))
        assertTrue(RestorePlan(noRounds, emptyList(), null).addsNothing)
    }

    // ---- Reading files ----

    @Test
    fun otherFilesAreRejected() {
        assertError(BackupError.NOT_A_BACKUP, """{"hello": 1}""")
        assertError(BackupError.NOT_A_BACKUP, "hello")
        assertError(BackupError.NEWER_VERSION, """{"format": "stih-backup", "version": 2, "sessions": []}""")
        assertError(BackupError.UNREADABLE, """{"format": "stih-backup", "version": 1, "sessions": 5}""")
    }

    @Test
    fun damagedPartsAreSkippedNotFatal() {
        val obj = fixtureObject()
        val sessions = obj["sessions"]!!.jsonArray.toMutableList()
        sessions[1] = JsonObject(sessions[1].jsonObject + ("players" to JsonPrimitive("nonsense")))
        val settings = JsonObject(mapOf("autoScoring" to JsonPrimitive("yes"), "obvezniKlopStep" to JsonPrimitive(1000)))
        val damaged = JsonObject(obj + ("sessions" to JsonArray(sessions)) + ("settings" to settings))

        val backup = StihBackup.read(damaged.toString())
        assertEquals(1, backup.sessions.size)
        assertEquals(1, backup.skippedSessions)
        assertNull(backup.settings?.autoScoring) // wrong type → left out
        assertEquals(1000, backup.settings?.obvezniKlopStep) // the rest still restores
    }

    /** Android files written before 1.8 use "dealerManualOffset" — still read. */
    @Test
    fun oldDealerFieldNameIsAccepted() {
        val obj = fixtureObject()
        val sessions = obj["sessions"]!!.jsonArray.toMutableList()
        sessions[0] = JsonObject(sessions[0].jsonObject - "dealerOffset" + ("dealerManualOffset" to JsonPrimitive(3)))
        val backup = StihBackup.read(JsonObject(obj + ("sessions" to JsonArray(sessions))).toString())
        assertEquals(3, backup.sessions.first().dealerManualOffset)
    }

    @Test
    fun readsThePhonesOwnHistoryFile() {
        val saved = listOf(session(listOf("Ana", "Bor", "Cene")), session(listOf("Elias", "Nina", "Jure"), rounds = 3))
        val backup = StihBackup.read(json.encodeToString(ListSerializer(GameSession.serializer()), saved))
        assertEquals(saved, backup.sessions)
        assertNull(backup.settings)
    }

    // ---- Automatic copies ----

    @Test
    fun dailyCopyOncePerDayKeepsTheLastSeven() = withTempDir { dir ->
        val history = File(dir, "tarok_sessions.json")
        val backups = File(dir, "Backups")
        val noon = 1_792_576_800_000L
        val day = 86_400_000L

        for (i in 0 until 9) {
            history.writeText("[\"day $i\"]")
            BackupStore.makeDailyCopy(history, backups, now = noon + i * day)
        }
        // A later save on the same day keeps that day's first copy
        history.writeText("[\"later\"]")
        BackupStore.makeDailyCopy(history, backups, now = noon + 8 * day + 3_600_000)

        val copies = BackupStore.automaticCopies(backups)
        assertEquals(7, copies.size)
        assertTrue(copies.all { it.kind == BackupStore.Kind.DAILY })
        assertEquals("[\"day 8\"]", copies.first().file.readText())
        assertEquals("[\"day 2\"]", copies.last().file.readText())
    }

    @Test
    fun copyBeforeClearingIsAFullBackup() = withTempDir { dir ->
        val backup = StihBackup(
            sessions = listOf(session(listOf("Ana", "Bor", "Cene"), rounds = 2)),
            activeSession = session(listOf("Elias", "Nina", "Jure"), rounds = 1),
        )
        val file = BackupStore.writeBeforeClearCopy(backup, dir, "1.8", now = 1_792_602_000_000L)
        val listed = BackupStore.automaticCopies(dir)
        assertEquals(listOf(file), listed.map { it.file })
        assertEquals(BackupStore.Kind.BEFORE_CLEAR, listed.first().kind)
        val reread = StihBackup.read(file.readText())
        assertEquals(1, reread.sessions.size)
        assertNotNull(reread.activeSession)
    }
}
