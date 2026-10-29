package com.epodbreg.stih.data

import com.epodbreg.stih.model.CustomScoringRules
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Port of RuleSetStorageTests.swift. Rule sets are per table since 1.9: each keeps its own
 * switches, the selected one stays in step with its saved copy, and a restore brings the
 * backup's version. Every test uses its own in-memory store.
 */
class RuleSetStorageTest {

    private fun domaca(renons: Int = 20) = CustomScoringRules(name = "Domača").let {
        it.copy(generalRules = it.generalRules.copy(renonsPenalty = renons))
    }

    @Test
    fun oldObvezniKlopMovesIntoEveryRuleSetOnce() {
        // What 1.8 left behind: app-wide obvezni klop, a custom set that is selected
        val store = MemoryStore().apply {
            putBoolean("obvezniKlopEnabled", true)
            putInt("obvezniKlopStep", 1000)
            putString("customScoringRules", Json.encodeToString(listOf(domaca())))
            putString("houseScoringRules", "Domača")
        }
        val settings = SettingsManager(store)
        assertTrue(settings.currentRuleSet.generalRules.obvezniKlopEnabled)
        assertEquals(1000, settings.currentRuleSet.generalRules.obvezniKlopStep)
        assertEquals(true, settings.customRules.firstOrNull()?.generalRules?.obvezniKlopEnabled)

        settings.setHouseRules("Standard")
        assertTrue(settings.currentRuleSet.generalRules.obvezniKlopEnabled)
        assertEquals(1000, settings.currentRuleSet.generalRules.obvezniKlopStep)

        // Once only: turning it off in Standard sticks after a restart
        settings.updateGeneralRules { it.copy(obvezniKlopEnabled = false) }
        val restarted = SettingsManager(store)
        assertFalse(restarted.currentRuleSet.generalRules.obvezniKlopEnabled)
    }

    @Test
    fun standardKeepsItsSwitchesWhenSwitchingSets() {
        val store = MemoryStore()
        val settings = SettingsManager(store)
        settings.updateGeneralRules { it.copy(radlcDoublesPartner = false) }
        settings.replaceCustomRules(settings.customRules + domaca())

        settings.setHouseRules("Domača")
        assertTrue(settings.currentRuleSet.generalRules.radlcDoublesPartner)  // Domača has its own
        settings.setHouseRules("Standard")
        assertFalse(settings.currentRuleSet.generalRules.radlcDoublesPartner)

        assertFalse(SettingsManager(store).currentRuleSet.generalRules.radlcDoublesPartner)
    }

    @Test
    fun editingTheSelectedSetUpdatesItsSavedCopy() {
        val store = MemoryStore()
        val settings = SettingsManager(store)
        settings.replaceCustomRules(settings.customRules + domaca())
        settings.setHouseRules("Domača")
        settings.updateGeneralRules { it.copy(radlcSkipsPrazenKlop = true) }

        assertEquals(true, settings.customRules.firstOrNull()?.generalRules?.radlcSkipsPrazenKlop)
        val restarted = SettingsManager(store)
        assertTrue(restarted.currentRuleSet.generalRules.radlcSkipsPrazenKlop)
        assertEquals(true, restarted.customRules.firstOrNull()?.generalRules?.radlcSkipsPrazenKlop)
    }

    /**
     * The phone has "Domača" selected; the backup has a newer "Domača". The backup's must win —
     * also when the file doesn't say which set was selected, or carries no currentRuleSet.
     */
    @Test
    fun restoreBringsTheBackupsVersionOfTheSelectedSet() {
        for (fileNamesTheSelectedSet in listOf(true, false)) {
            val phone = SettingsManager(MemoryStore())
            phone.replaceCustomRules(listOf(domaca(renons = 20)))
            phone.setHouseRules("Domača")

            val other = SettingsManager(MemoryStore())
            other.replaceCustomRules(listOf(domaca(renons = 50)))
            other.setHouseRules("Domača")
            var backup = other.toBackupSettings().copy(currentRuleSet = null)  // e.g. a damaged field
            if (!fileNamesTheSelectedSet) backup = backup.copy(houseScoringRules = null)

            phone.applyBackup(backup)

            assertEquals("Domača", phone.houseScoringRules)
            assertEquals(50, phone.currentRuleSet.generalRules.renonsPenalty)
            assertEquals(50, phone.customRules.first { it.name == "Domača" }.generalRules.renonsPenalty)
        }
    }

    @Test
    fun newRuleSetNamesNeverClash() {
        val settings = SettingsManager(MemoryStore())
        assertEquals("Nova pravila", settings.uniqueRuleSetName("Nova pravila"))
        settings.replaceCustomRules(settings.customRules + CustomScoringRules(name = "Nova pravila"))
        assertEquals("Nova pravila 2", settings.uniqueRuleSetName("Nova pravila"))
        assertEquals("Standard 2", settings.uniqueRuleSetName("Standard "))
    }
}
