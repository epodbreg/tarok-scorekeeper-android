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
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.epodbreg.stih.model.CustomScoringRules
import com.epodbreg.stih.ui.theme.StihGreen
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
    var clearError by remember { mutableStateOf<String?>(null) }
    var editingRules by remember { mutableStateOf<CustomScoringRules?>(null) }
    var editingOriginalName by remember { mutableStateOf<String?>(null) }
    var rulesMenuOpen by remember { mutableStateOf(false) }

    if (showRestore) {
        BackHandler { showRestore = false }
        RestoreBackupScreen(sessionManager, settings, onClose = { showRestore = false })
        return
    }

    val currentEditing = editingRules
    if (currentEditing != null) {
        CustomRulesEditorScreen(
            initialRules = currentEditing,
            isExisting = editingOriginalName != null,
            onSave = { updated ->
                settings.addOrUpdateCustomRuleSet(updated, originalName = editingOriginalName)
                editingRules = null
                editingOriginalName = null
            },
            onDelete = {
                editingOriginalName?.let { settings.deleteCustomRuleSet(it) }
                editingRules = null
                editingOriginalName = null
            },
            onCancel = {
                editingRules = null
                editingOriginalName = null
            },
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
        val sessions = sessionManager.savedSessions
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        val lines = mutableListOf("Datum,Čas,Igralci,Kontrakt,Glavni igralec,Partner,Rezultat,Točke,Bonusi")
        for (session in sessions) {
            val sessionDate = dateFormatter.format(Date(session.startTime))
            val players = session.playerNames.joinToString(";")
            for (game in session.games) {
                val time = timeFormatter.format(Date(game.timestamp))
                val bonuses = game.bonuses.joinToString(";") { it.type.rawValue }
                lines.add(
                    "\"$sessionDate\",\"$time\",\"$players\",\"${game.contract.rawValue}\"," +
                        "\"${game.declarerName}\",\"${game.partnerName ?: ""}\",\"${game.outcome.rawValue}\"," +
                        "\"${game.totalPoints}\",\"$bonuses\""
                )
            }
        }
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportDir, "stih-vse-seje-${dateFormatter.format(Date())}.csv")
        file.writeText(lines.joinToString("\n"))
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Rules section
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
                        if (settings.houseScoringRules != "Standard") {
                            TextButton(onClick = {
                                settings.customRules.firstOrNull { it.name == settings.houseScoringRules }?.let {
                                    editingRules = it
                                    editingOriginalName = it.name
                                }
                            }) { Text("Uredi") }
                        }
                    }
                    HorizontalDivider()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.clickable {
                            editingRules = CustomScoringRules(name = "Nova pravila")
                            editingOriginalName = null
                        },
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Text("Dodaj nova pravila", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Game section
            SettingsSection(title = "Igra", icon = Icons.Filled.SportsEsports, iconColor = StihGreen) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsToggleRow(
                        title = "Zvočni efekti",
                        subtitle = "Zvoki pri uspešnih akcijah",
                        checked = settings.soundEffects,
                        onCheckedChange = { settings.setSoundEffectsEnabled(it) },
                    )
                    HorizontalDivider()
                    // Obvezni klop
                    SettingsToggleRow(
                        title = "Obvezni klop",
                        subtitle = "Po določenih pogojih zahteva klop ali solo brez",
                        checked = settings.obvezniKlopEnabled,
                        onCheckedChange = { settings.setObvezniKlop(it) },
                    )
                    if (settings.obvezniKlopEnabled) {
                        SettingsSubToggleRow(
                            title = "Ko ima igralec_ka okrogle točke",
                            checked = settings.obvezniKlopOnRoundScore,
                            onCheckedChange = { settings.setObvezniKlopRoundScore(it) },
                        )
                        if (settings.obvezniKlopOnRoundScore) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(start = 16.dp),
                            ) {
                                Text(
                                    "Korak:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                listOf(100, 500, 1000).forEach { step ->
                                    val selected = settings.obvezniKlopStep == step
                                    Box(
                                        modifier = Modifier
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(
                                                if (selected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.surfaceVariant
                                            )
                                            .clickable { settings.updateObvezniKlopStep(step) }
                                            .padding(horizontal = 14.dp, vertical = 6.dp),
                                    ) {
                                        Text(
                                            "$step",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (selected) Color.White
                                            else MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            }
                        }
                        SettingsSubToggleRow(
                            title = "Ko je izid igre enak 0",
                            checked = settings.obvezniKlopOnZeroOutcome,
                            onCheckedChange = { settings.setObvezniKlopZeroOutcome(it) },
                        )
                    }
                    HorizontalDivider()
                    SettingsToggleRow(
                        title = "Samodejni radlci",
                        subtitle = "Radlci se vodijo samodejno — brez ročnega vklapljanja s pritiskom",
                        checked = settings.autoRadlci,
                        onCheckedChange = { settings.updateAutoRadlci(it) },
                    )
                    HorizontalDivider()
                    SettingsToggleRow(
                        title = "Radlc podvoji pagat ultimo",
                        subtitle = "Ko je izklopljeno, radlci ne podvojijo pagat ultimo nagrade",
                        checked = settings.currentRuleSet.generalRules.radlcDoublesPagat,
                        onCheckedChange = {
                            settings.updateCurrentRuleSet(
                                settings.currentRuleSet.copy(
                                    generalRules = settings.currentRuleSet.generalRules.copy(radlcDoublesPagat = it)
                                )
                            )
                        },
                    )
                    HorizontalDivider()
                    SettingsToggleRow(
                        title = "Radlc podvoji klop",
                        subtitle = "Neuporabljen radlc podvoji klop rezultat (prazen klop ga porabi)",
                        checked = settings.currentRuleSet.generalRules.radlcDoublesKlop,
                        onCheckedChange = {
                            settings.updateCurrentRuleSet(
                                settings.currentRuleSet.copy(
                                    generalRules = settings.currentRuleSet.generalRules.copy(radlcDoublesKlop = it)
                                )
                            )
                        },
                    )
                    HorizontalDivider()
                    SettingsToggleRow(
                        title = "Radlci za soigralce",
                        subtitle = "Podvojitev točk z radlci samo za glavne igralce, ne za soigralce",
                        checked = settings.currentRuleSet.generalRules.radlcDoublingDeclarerOnly,
                        onCheckedChange = {
                            settings.updateCurrentRuleSet(
                                settings.currentRuleSet.copy(
                                    generalRules = settings.currentRuleSet.generalRules.copy(radlcDoublingDeclarerOnly = it)
                                )
                            )
                        },
                    )
                    HorizontalDivider()
                    SettingsToggleRow(
                        title = "Valat s točkami igre",
                        subtitle = "Pri valatu se prištejejo tudi točke osnovne igre",
                        checked = settings.currentRuleSet.generalRules.valatWithGamePoints,
                        onCheckedChange = {
                            settings.updateCurrentRuleSet(
                                settings.currentRuleSet.copy(
                                    generalRules = settings.currentRuleSet.generalRules.copy(valatWithGamePoints = it)
                                )
                            )
                        },
                    )
                }
            }

            // Auto scoring
            SettingsSection(title = "Samodejno štetje", icon = Icons.Filled.Functions, iconColor = Color(0xFF007AFF)) {
                SettingsToggleRow(
                    title = "Samodejno štetje točk",
                    subtitle = "Avtomatsko izračuna točke za kontrakte",
                    checked = settings.autoScoring,
                    onCheckedChange = { settings.setAutoScoringEnabled(it) },
                )
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
private fun SettingsSubToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 16.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
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
