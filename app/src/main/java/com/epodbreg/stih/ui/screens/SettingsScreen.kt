package com.epodbreg.stih.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import com.epodbreg.stih.data.BackupStore
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.epodbreg.stih.data.SessionManager
import com.epodbreg.stih.data.SettingsManager
import com.epodbreg.stih.model.BonusScoreConfig
import com.epodbreg.stih.model.CompactCardsMode
import com.epodbreg.stih.model.CustomScoringRules
import com.epodbreg.stih.model.GameSession
import com.epodbreg.stih.model.GeneralRulesConfig
import com.epodbreg.stih.model.TarokContract
import com.epodbreg.stih.ui.theme.StihOrange
import com.epodbreg.stih.ui.theme.StihPurple
import com.epodbreg.stih.ui.theme.StihRed
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Settings screen. Port of SettingsView.swift. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: SettingsManager,
    sessionManager: SessionManager,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var showClearConfirmation by remember { mutableStateOf(false) }
    var showRestore by remember { mutableStateOf(false) }
    // Remembered before the sub-screens below, so returning from them keeps the scroll position
    val scrollState = rememberScrollState()
    var clearError by remember { mutableStateOf<String?>(null) }
    /** The set the editor was opened with (Standard, a saved one, or a new one) */
    var editingRules by remember { mutableStateOf<CustomScoringRules?>(null) }
    var rulesMenuOpen by remember { mutableStateOf(false) }
    var showApplyToRunningGame by remember { mutableStateOf(false) }

    if (showRestore) {
        BackHandler { showRestore = false }
        RestoreBackupScreen(sessionManager, settings, onClose = { showRestore = false })
        return
    }

    val currentEditing = editingRules
    if (currentEditing != null) {
        BackHandler { editingRules = null }
        CustomRulesEditorScreen(
            initialRules = currentEditing,
            isPredefined = settings.isPredefined(currentEditing.name),
            canDelete = settings.customRules.any { it.name == currentEditing.name },
            onSave = { updated ->
                settings.saveEditedRules(currentEditing, updated)
                editingRules = null
            },
            onDelete = {
                settings.deleteCustomRuleSet(currentEditing.name)
                editingRules = null
            },
            onCancel = { editingRules = null },
        )
        return
    }

    /** Writes the full backup to the cache and opens the share sheet. */
    fun shareBackup() {
        val backup = sessionManager.makeBackup(settings.toBackupSettings())
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportDir, BackupStore.exportFileName())
        file.writeText(backup.encoded(sessionManager.appVersion))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Varnostna kopija"))
    }

    fun exportAllSessions() {
        val file = File(File(context.cacheDir, "exports").apply { mkdirs() },
            "stih-vse-seje-${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())}.csv")
        file.writeText(allSessionsCsv(sessionManager.savedSessions))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Izvozi vse seje"))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nastavitve") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Nazaj")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Pravila: what depends on the table lives in the rule set (Uredi → Osnovno)
            SettingsSection(title = "Pravila", icon = Icons.AutoMirrored.Filled.MenuBook, iconColor = StihOrange) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Hišna pravila", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable { rulesMenuOpen = true }
                                    .padding(vertical = 4.dp),
                            ) {
                                Text(settings.houseScoringRules, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                            DropdownMenu(expanded = rulesMenuOpen, onDismissRequest = { rulesMenuOpen = false }) {
                                settings.getAllRuleSetNames().forEach { name ->
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = {
                                            settings.setHouseRules(name)
                                            rulesMenuOpen = false
                                        },
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { editingRules = settings.currentRuleSet }) { Text("Uredi") }
                    }
                    RunningGameNote(
                        session = sessionManager.activeSession,
                        selected = settings.currentRuleSet,
                        onApply = { showApplyToRunningGame = true },
                    )
                    Text(
                        "Radlci, klop in točkovanje so v »Uredi« pod Osnovno — vsaka pravila (miza) imajo svoje.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.clickable {
                            editingRules = CustomScoringRules(
                                name = settings.uniqueRuleSetName("Nova pravila"),
                                generalRules = settings.currentRuleSet.generalRules,
                            )
                        },
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Text("Dodaj nova pravila", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                    }
                    val differences = rulesDifferences(settings.currentRuleSet)
                    if (settings.houseScoringRules != SettingsManager.STANDARD || differences.isNotEmpty()) {
                        HorizontalDivider()
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                if (differences.isEmpty()) "Enako kot standard" else "Spremembe od standarda:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            differences.forEach { line ->
                                Text(line, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            // Aplikacija: how the app behaves, the same at every table
            SettingsSection(title = "Aplikacija", icon = Icons.Filled.PhoneAndroid, iconColor = Color.Gray) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsToggleRow(
                        title = "Samodejno štetje točk",
                        subtitle = "Izračuna točke kontrakta po hišnih pravilih",
                        checked = settings.autoScoring,
                        onCheckedChange = { settings.setAutoScoringEnabled(it) },
                    )
                    HorizontalDivider()
                    SettingsToggleRow(
                        title = "Samodejni radlci",
                        subtitle = "Radlci se dodajajo in prečrtajo sami; ročno urejanje je izklopljeno",
                        checked = settings.autoRadlci,
                        onCheckedChange = { settings.updateAutoRadlci(it) },
                    )
                    HorizontalDivider()
                    SettingsToggleRow(
                        title = "Zvočni efekti",
                        subtitle = "Zvok ob dobljeni igri",
                        checked = settings.soundEffects,
                        onCheckedChange = { settings.setSoundEffectsEnabled(it) },
                    )
                    HorizontalDivider()
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Kompaktne kartice", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            "Vodilni_a čez celo širino, ostali po dva v vrsti. Samodejno: od " +
                                "${CompactCardsMode.AUTO_FROM_PLAYERS} igralcev naprej.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            CompactCardsMode.entries.forEachIndexed { index, mode ->
                                SegmentedButton(
                                    selected = settings.compactCards == mode,
                                    onClick = { settings.updateCompactCards(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(index, CompactCardsMode.entries.size),
                                    colors = stihSegmentColors(),
                                    icon = {},
                                ) { Text(mode.label) }
                            }
                        }
                    }
                }
            }

            // Data
            SettingsSection(title = "Podatki", icon = Icons.Filled.Cloud, iconColor = StihPurple) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsActionRow(
                        title = "Varnostna kopija",
                        subtitle = "Vse seje in nastavitve v eni datoteki — za nov telefon ali iPhone",
                        action = "Shrani",
                        actionColor = MaterialTheme.colorScheme.primary,
                        onClick = { shareBackup() },
                    )
                    HorizontalDivider()
                    SettingsActionRow(
                        title = "Obnovi iz kopije",
                        subtitle = "Doda seje iz kopije; obstoječih ne spremeni",
                        action = "Obnovi",
                        actionColor = MaterialTheme.colorScheme.primary,
                        onClick = { showRestore = true },
                    )
                    HorizontalDivider()
                    SettingsActionRow(
                        title = "Izvozi v CSV",
                        subtitle = "Tabela vseh iger za Excel ali Google Sheets",
                        action = "Izvozi",
                        actionColor = MaterialTheme.colorScheme.primary,
                        onClick = { exportAllSessions() },
                    )
                    HorizontalDivider()
                    SettingsActionRow(
                        title = "Počisti zgodovino",
                        subtitle = "Briše vse shranjene seje (prej shrani kopijo na telefon)",
                        action = "Počisti",
                        actionColor = StihRed,
                        enabled = sessionManager.savedSessions.isNotEmpty(),
                        onClick = { showClearConfirmation = true },
                    )
                }
            }
        }
    }

    if (showApplyToRunningGame) {
        AlertDialog(
            onDismissRequest = { showApplyToRunningGame = false },
            title = { Text("Uporabim pravila za nedokončano igro?") },
            text = {
                Text(
                    "Nedokončana igra bo naprej štela po »${settings.currentRuleSet.name}«. Pravila za radlce " +
                        "in klop veljajo za vse odigrane runde, točke že vpisanih iger pa ostanejo, kot so bile.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showApplyToRunningGame = false
                    // Re-scored when the game opens again
                    sessionManager.activeSession?.let {
                        sessionManager.updateActiveSession(it.copy(rules = settings.currentRuleSet))
                    }
                }) { Text("Uporabi") }
            },
            dismissButton = {
                TextButton(onClick = { showApplyToRunningGame = false }) { Text("Prekliči") }
            },
        )
    }

    clearError?.let { message ->
        AlertDialog(
            onDismissRequest = { clearError = null },
            title = { Text("Počisti zgodovino") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { clearError = null }) { Text("V redu") } },
        )
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("Počisti zgodovino") },
            text = {
                Text(
                    "Izbrisane bodo vse shranjene seje (${sessionManager.savedSessions.size}). Prej se na telefon " +
                        "shrani kopija, ki jo lahko vrneš v »Obnovi iz kopije«.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirmation = false
                    try {
                        sessionManager.clearHistory(settings.toBackupSettings())
                    } catch (_: Exception) {
                        // No copy, no delete
                        clearError = "Zgodovina ni izbrisana: kopije pred brisanjem ni bilo mogoče shraniti."
                    }
                }) { Text("Počisti", color = StihRed) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) { Text("Prekliči") }
            },
        )
    }
}

/**
 * An unfinished game keeps the rules it started with. When they differ from the ones selected
 * here, say so and offer to switch the game over.
 */
@Composable
private fun RunningGameNote(session: GameSession?, selected: CustomScoringRules, onApply: () -> Unit) {
    val gameRules = session?.takeIf { it.games.isNotEmpty() }?.rules ?: return
    if (gameRules == selected) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                Icons.Outlined.Info, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp),
            )
            Text(
                "Nedokončana igra šteje po pravilih »${gameRules.name}«, kot so bila ob njenem začetku. " +
                    "Spremembe tukaj veljajo za nove igre.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "Uporabi tudi za nedokončano igro",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onApply).padding(vertical = 4.dp),
        )
    }
}

/** What [rules] do differently from Standard: points, then the table's switches */
private fun rulesDifferences(rules: CustomScoringRules): List<String> {
    val defaults = CustomScoringRules.defaultContractScores
    val defaultBonus = BonusScoreConfig()
    val lines = mutableListOf<String>()
    val contracts = listOf(
        TarokContract.TRI to "Tri", TarokContract.DVE to "Dve", TarokContract.ENA to "Ena",
        TarokContract.SOLO_TRI to "Solo tri", TarokContract.SOLO_DVE to "Solo dve", TarokContract.SOLO_ENA to "Solo ena",
        TarokContract.SOLO_BREZ to "Solo brez", TarokContract.BERAC to "Berač", TarokContract.ODPRTI_BERAC to "Odprti berač",
        TarokContract.BARVNI_VALAT to "Barvni valat",
    )
    for ((contract, name) in contracts) {
        val current = rules.contractScores[contract.rawValue]
        val standard = defaults[contract.rawValue]
        val cur3 = current?.threePlayerScore ?: 0
        val cur4 = current?.fourPlusPlayerScore ?: 0
        if (cur3 != (standard?.threePlayerScore ?: 0) || cur4 != (standard?.fourPlusPlayerScore ?: 0)) {
            lines += "$name: $cur3 / $cur4 točk"
        }
    }
    val bonus = rules.bonusScores
    if (bonus.tihiValat != defaultBonus.tihiValat) lines += "Tihi valat: ${bonus.tihiValat} točk"
    if (bonus.napovedanValat != defaultBonus.napovedanValat) lines += "Napovedan valat: ${bonus.napovedanValat} točk"
    if (bonus.trulaBase != defaultBonus.trulaBase) lines += "Trula: ${bonus.trulaBase} / ${bonus.trulaAnnounced} točk"
    if (bonus.pagatUltimoBase != defaultBonus.pagatUltimoBase) {
        lines += "Pagat ultimo: ${bonus.pagatUltimoBase} / ${bonus.pagatUltimoAnnounced} točk"
    }

    // The switches in Uredi → Osnovno, so the table's rules show without opening it
    val g = rules.generalRules
    val standard = GeneralRulesConfig()
    if (!g.radlcDoublesPartner) lines += "Radlc podvoji samo glavnega igralca_ko"
    if (!g.radlcDoublesPagat) lines += "Radlc ne podvoji pagat ultimo"
    if (!g.radlcPenaltyEnabled) {
        lines += "Brez kazni za neuporabljene radlce"
    } else if (g.radlcPenaltyPerUnused != standard.radlcPenaltyPerUnused) {
        lines += "Kazen na radlc: ${g.radlcPenaltyPerUnused} točk"
    }
    if (!g.radlcDoublesKlop) {
        lines += "Radlc ne podvoji klopa"
    } else if (g.radlcSkipsPrazenKlop) {
        lines += "Na prazen klop ne vpliva radlc"
    }
    if (g.obvezniKlopEnabled) {
        val whenText = buildList {
            if (g.obvezniKlopOnRoundScore) add("okrogle točke (${g.klopStep})")
            if (g.obvezniKlopOnZeroOutcome) add("izid 0")
        }
        lines += if (whenText.isEmpty()) "Obvezni klop" else "Obvezni klop: " + whenText.joinToString(", ")
    }
    if (g.valatWithGamePoints) lines += "Valat s točkami igre"
    if (g.renonsPenalty != standard.renonsPenalty) lines += "Kazen za renons: ${g.renonsPenalty} točk"
    return lines
}

/** Every game of every saved session; every field quoted, quotes doubled */
private fun allSessionsCsv(sessions: List<GameSession>): String {
    val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    fun q(value: String) = "\"" + value.replace("\"", "\"\"") + "\""
    val lines = mutableListOf("Datum,Čas,Igralci,Kontrakt,Glavni igralec,Partner,Rezultat,Točke,Točke partnerja,Bonusi")
    for (session in sessions) {
        val sessionDate = dateFormatter.format(Date(session.startTime))
        val players = session.playerNames.joinToString(";")
        for (game in session.games) {
            // Can differ from the declarer's when the radlc doubles only the declarer
            val partnerPoints = game.actualPartner?.let { "${game.pointsFor(it)}" } ?: ""
            val row = listOf(
                sessionDate,
                timeFormatter.format(Date(game.timestamp)),
                players,
                game.contract.rawValue,
                game.declarerName,
                game.partnerName ?: "",
                game.outcome.rawValue,
                "${game.totalPoints}",
                partnerPoints,
                game.bonuses.joinToString(";") { it.type.rawValue },
            )
            lines += row.joinToString(",") { q(it) }
        }
    }
    return lines.joinToString("\n")
}

/** Segmented picker in the app's blue instead of the default purple */
@Composable
fun stihSegmentColors() = SegmentedButtonDefaults.colors(
    activeContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
    activeContentColor = MaterialTheme.colorScheme.primary,
    activeBorderColor = MaterialTheme.colorScheme.outline,
)

@Composable
fun SettingsSection(
    title: String,
    icon: ImageVector,
    iconColor: Color,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}

@Composable
fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    subtitle: String,
    action: String,
    actionColor: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onClick, enabled = enabled) {
            Text(action, color = if (enabled) actionColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
        }
    }
}
