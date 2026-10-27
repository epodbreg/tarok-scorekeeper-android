package com.epodbreg.stih.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.epodbreg.stih.data.SessionManager
import com.epodbreg.stih.data.SettingsManager
import com.epodbreg.stih.model.TarokStrings
import com.epodbreg.stih.ui.components.GroupBadge
import com.epodbreg.stih.ui.theme.StihOrange
import com.epodbreg.stih.ui.theme.StihRed
import java.util.UUID

/**
 * Rename players across saved sessions, merge a mistyped name into the right one,
 * or split one name into two people. Port of PlayerManagementView.swift. (iOS merges
 * by swiping; here each row has an explicit "Združi" button, which is easier to discover.)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerManagementScreen(
    sessionManager: SessionManager,
    settings: SettingsManager,
    onBack: () -> Unit,
) {
    var openPlayer by remember { mutableStateOf<String?>(null) }
    var mergePickSource by remember { mutableStateOf<String?>(null) }
    var mergeConfirm by remember { mutableStateOf<Pair<String, String>?>(null) }
    var conflictMessage by remember { mutableStateOf<String?>(null) }
    val haptics = LocalHapticFeedback.current
    val players = sessionManager.allPlayerNames

    openPlayer?.let { name ->
        BackHandler { openPlayer = null }
        PlayerSessionsScreen(sessionManager, settings, name, onBack = { openPlayer = null })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Igralci") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Nazaj") }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(players, key = { it.name }) { player ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { openPlayer = player.name }
                        .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                ) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(player.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            TarokStrings.sejaCountText(player.sessionCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { mergePickSource = player.name }, enabled = players.size > 1) {
                        Text("Združi", color = StihOrange)
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
            }
            item {
                Text(
                    "Tapni igralca_ko za preimenovanje ali razdelitev, če sta pod istim imenom shranjeni dve osebi. " +
                        "»Združi« prenese vse igre napačno zapisanega imena na pravo ime.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }

    // Merge: pick target
    mergePickSource?.let { source ->
        AlertDialog(
            onDismissRequest = { mergePickSource = null },
            title = { Text("Združi »$source« z …") },
            text = {
                LazyColumn {
                    items(players.map { it.name }.filter { it != source }) { name ->
                        Text(
                            name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    mergePickSource = null
                                    mergeConfirm = source to name
                                }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { mergePickSource = null }) { Text("Prekliči") } },
        )
    }

    // Merge: confirm
    mergeConfirm?.let { (source, target) ->
        AlertDialog(
            onDismissRequest = { mergeConfirm = null },
            title = { Text("Združi igralca_ki?") },
            text = { Text("Vse igre igralca_ke »$source« bodo prenesene na »$target«. Tega ni mogoče razveljaviti.") },
            confirmButton = {
                TextButton(onClick = {
                    mergeConfirm = null
                    conflictMessage = applyRename(sessionManager, settings, source, target, sessionIds = null)
                    if (conflictMessage == null) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }) { Text("Združi", color = StihRed) }
            },
            dismissButton = { TextButton(onClick = { mergeConfirm = null }) { Text("Prekliči") } },
        )
    }

    ConflictDialog(conflictMessage) { conflictMessage = null }
}

/** One player's sessions: rename everywhere, or split some sessions off to a new name. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerSessionsScreen(
    sessionManager: SessionManager,
    settings: SettingsManager,
    name: String,
    onBack: () -> Unit,
) {
    var isSplitting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<UUID>()) }
    // null = no prompt; empty = rename everywhere; non-empty = only these sessions (split)
    var renameScope by remember { mutableStateOf<Set<UUID>?>(null) }
    var renameText by remember { mutableStateOf("") }
    var pendingMerge by remember { mutableStateOf<Pair<String, Set<UUID>>?>(null) }
    var conflictMessage by remember { mutableStateOf<String?>(null) }

    val sessions = sessionManager.savedSessions
        .filter { name in it.playerNames }
        .sortedByDescending { it.startTime }

    fun apply(target: String, scope: Set<UUID>) {
        conflictMessage = applyRename(sessionManager, settings, name, target, sessionIds = scope.ifEmpty { null })
        if (conflictMessage != null) return
        isSplitting = false
        selected = emptySet()
        // Name no longer exists anywhere → nothing left to show here
        if (sessionManager.savedSessions.none { name in it.playerNames }) onBack()
    }

    if (isSplitting) BackHandler { isSplitting = false; selected = emptySet() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(name) },
                navigationIcon = {
                    IconButton(onClick = { if (isSplitting) { isSplitting = false; selected = emptySet() } else onBack() }) {
                        Icon(
                            if (isSplitting) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (isSplitting) "Prekliči" else "Nazaj",
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (isSplitting) {
                Surface(tonalElevation = 3.dp) {
                    TextButton(
                        onClick = {
                            renameText = ""
                            renameScope = selected
                        },
                        enabled = selected.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(8.dp),
                    ) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Novo ime za ${TarokStrings.sejaCountText(selected.size)}", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!isSplitting) {
                item {
                    ActionRow(Icons.Filled.Edit, "Preimenuj povsod", enabled = true) {
                        renameText = name
                        renameScope = emptySet()
                    }
                    ActionRow(Icons.AutoMirrored.Filled.CallSplit, "Razdeli na dve osebi…", enabled = sessions.size >= 2) {
                        isSplitting = true
                        selected = emptySet()
                    }
                    Text(
                        "Razdeli, če si pod imenom »$name« shranil_a dve različni osebi — označiš seje druge osebe in jim daš novo ime.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    HorizontalDivider()
                }
            }
            item {
                Text(
                    if (isSplitting) "Označi seje druge osebe" else TarokStrings.sejaCountText(sessions.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                )
            }
            items(sessions, key = { it.id }) { session ->
                val isSelected = session.id in selected
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .let { m ->
                            if (isSplitting) {
                                m.clickable { selected = if (isSelected) selected - session.id else selected + session.id }
                            } else m
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    if (isSplitting) {
                        Icon(
                            if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(SessionManager.formatDate(session.startTime), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            session.groupName?.let { GroupBadge(it) }
                        }
                        Text(
                            "z: " + session.playerNames.filter { it != name }.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }

    // Rename everywhere (empty scope) or split (non-empty scope)
    renameScope?.let { scope ->
        val isSplit = scope.isNotEmpty()
        AlertDialog(
            onDismissRequest = { renameScope = null },
            title = { Text(if (isSplit) "Novo ime za izbrane seje" else "Preimenuj igralca_ko") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (isSplit) "»$name« v ostalih sejah ostane nespremenjen_a." else "Ime se spremeni v vseh shranjenih sejah.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        singleLine = true,
                        placeholder = { Text(if (isSplit) "npr. $name K." else "Ime") },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    renameScope = null
                    val target = renameText.trim()
                    if (target.isEmpty() || target == name) return@TextButton
                    // Renaming onto an existing name is a merge for those sessions — confirm it
                    if (sessionManager.allPlayerNames.any { it.name == target }) {
                        pendingMerge = target to scope
                    } else {
                        apply(target, scope)
                    }
                }) { Text("Shrani") }
            },
            dismissButton = { TextButton(onClick = { renameScope = null }) { Text("Prekliči") } },
        )
    }

    pendingMerge?.let { (target, scope) ->
        AlertDialog(
            onDismissRequest = { pendingMerge = null },
            title = { Text("Združi z obstoječim igralcem_ko?") },
            text = { Text("»$target« že obstaja — igre bodo štete kot njegove_njene.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingMerge = null
                    apply(target, scope)
                }) { Text("Združi", color = StihRed) }
            },
            dismissButton = { TextButton(onClick = { pendingMerge = null }) { Text("Prekliči") } },
        )
    }

    ConflictDialog(conflictMessage) { conflictMessage = null }
}

@Composable
private fun ActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, enabled: Boolean, onClick: () -> Unit) {
    val color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(text, color = color, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ConflictDialog(message: String?, onDismiss: () -> Unit) {
    message ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ni mogoče") },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("V redu") } },
    )
}

/**
 * Runs a rename/merge/split and returns a user-facing message if it was refused.
 * A split never moves "my player" — that stays on the name picked in stats.
 */
private fun applyRename(
    sessionManager: SessionManager,
    settings: SettingsManager,
    source: String,
    target: String,
    sessionIds: Set<UUID>?,
): String? = when (val outcome = sessionManager.renamePlayer(source, target, sessionIds)) {
    is SessionManager.RenameOutcome.Renamed -> {
        if (sessionIds == null && settings.myPlayerName == source) settings.updateMyPlayerName(target)
        null
    }
    is SessionManager.RenameOutcome.Conflict -> {
        val dates = outcome.sessionDates.joinToString(", ") { SessionManager.formatDate(it) }
        "»$source« in »$target« sta igrala_i v isti seji ($dates), zato gre za dve različni osebi."
    }
}
