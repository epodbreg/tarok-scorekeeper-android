package com.epodbreg.stih.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import com.epodbreg.stih.ui.theme.StihOrange
import com.epodbreg.stih.ui.theme.StihRed

/**
 * Rename players across all saved sessions, or merge a mistyped name into the
 * right one. Port of PlayerManagementView.swift. (iOS uses swipe-to-merge; here
 * each row has an explicit "Združi" button, which is easier to discover.)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerManagementScreen(
    sessionManager: SessionManager,
    settings: SettingsManager,
    onBack: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val players = sessionManager.allPlayerNames

    var renameSource by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    var mergePickSource by remember { mutableStateOf<String?>(null) }
    var mergeConfirm by remember { mutableStateOf<Pair<String, String>?>(null) }
    var conflictMessage by remember { mutableStateOf<String?>(null) }

    fun apply(source: String, target: String) {
        when (val outcome = sessionManager.renamePlayer(source, target)) {
            is SessionManager.RenameOutcome.Renamed -> {
                if (settings.myPlayerName == source) settings.updateMyPlayerName(target)
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            is SessionManager.RenameOutcome.Conflict -> {
                val dates = outcome.sessionDates.joinToString(", ") { SessionManager.formatDate(it) }
                conflictMessage = "»$source« in »$target« sta igrala_i v isti seji ($dates), zato gre za dve različni osebi."
            }
        }
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
                        .clickable {
                            renameText = player.name
                            renameSource = player.name
                        }
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
                    Icon(Icons.Filled.Edit, contentDescription = "Preimenuj", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    TextButton(onClick = { mergePickSource = player.name }, enabled = players.size > 1) {
                        Text("Združi", color = StihOrange)
                    }
                }
                HorizontalDivider()
            }
            item {
                Text(
                    "Tapni za preimenovanje. »Združi« prenese vse igre in statistiko napačno zapisanega imena na pravo ime.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }

    // Rename
    renameSource?.let { source ->
        AlertDialog(
            onDismissRequest = { renameSource = null },
            title = { Text("Preimenuj igralca_ko") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Ime se spremeni v vseh shranjenih sejah.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true, label = { Text("Ime") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = renameText.trim()
                    renameSource = null
                    if (target.isEmpty() || target == source) return@TextButton
                    // Renaming onto an existing name is a merge — confirm it explicitly
                    if (players.any { it.name == target }) mergeConfirm = source to target else apply(source, target)
                }) { Text("Shrani") }
            },
            dismissButton = { TextButton(onClick = { renameSource = null }) { Text("Prekliči") } },
        )
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
                    apply(source, target)
                }) { Text("Združi", color = StihRed) }
            },
            dismissButton = { TextButton(onClick = { mergeConfirm = null }) { Text("Prekliči") } },
        )
    }

    // Conflict
    conflictMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { conflictMessage = null },
            title = { Text("Združitev ni mogoča") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { conflictMessage = null }) { Text("V redu") } },
        )
    }
}
