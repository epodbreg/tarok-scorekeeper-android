package com.epodbreg.stih.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.epodbreg.stih.model.Player
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.epodbreg.stih.model.GameSession
import com.epodbreg.stih.model.groupNames
import com.epodbreg.stih.model.isPossiblyDifferentPerson
import com.epodbreg.stih.model.normalizedGroup
import com.epodbreg.stih.ui.theme.StihOrange

private const val MAX_QUICK_PICKS = 12

/** Player setup. Port of PlayerSetupView.swift. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlayerSetupScreen(
    prefills: List<String> = emptyList(),
    /** Group the new session goes into ("Torek"); null = none */
    initialGroup: String? = null,
    /** Names from saved sessions, most frequent first */
    knownPlayers: List<String> = emptyList(),
    /** For group names and the "different person with the same name?" warning */
    savedSessions: List<GameSession> = emptyList(),
    onStart: (players: List<Player>, group: String?) -> Unit,
    onCancel: () -> Unit,
) {
    var group by remember { mutableStateOf(initialGroup) }
    var playerCount by remember { mutableStateOf(maxOf(3, prefills.size)) }
    val playerNames = remember {
        (prefills + List(5) { "" }).take(5).toMutableStateList()
    }
    val haptics = LocalHapticFeedback.current

    fun seatOf(name: String) = (0 until playerCount).firstOrNull { playerNames[it].trim() == name }

    /**
     * Tap a saved name: fills the first empty seat (growing the table up to 5),
     * or clears it again if it's already seated.
     */
    fun togglePick(name: String) {
        val seat = seatOf(name)
        val empty = (0 until playerCount).firstOrNull { playerNames[it].isBlank() }
        when {
            seat != null -> playerNames[seat] = ""
            empty != null -> playerNames[empty] = name
            playerCount < 5 -> {
                playerNames[playerCount] = name
                playerCount++
            }
            else -> {
                // Table is full — warn instead of silently ignoring the tap
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                return
            }
        }
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nastavi igralce") },
                actions = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Close, contentDescription = "Zapri")
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // 3 / 4 / 5 selector
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                (3..5).forEach { n ->
                    val selected = playerCount == n
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .border(
                                2.dp,
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                CircleShape,
                            )
                            .clickable { playerCount = n },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "$n",
                            fontSize = 20.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            GroupPickerRow(
                group = group,
                groups = savedSessions.groupNames,
                onGroup = { group = it },
            )

            if (knownPlayers.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Shranjeni igralci",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        knownPlayers.take(MAX_QUICK_PICKS).forEach { name ->
                            val picked = seatOf(name) != null
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (picked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.dp, if (picked) Color.Transparent else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                    .clickable { togglePick(name) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Icon(
                                    if (picked) Icons.Filled.CheckCircle else Icons.Outlined.AddCircleOutline,
                                    contentDescription = null,
                                    tint = if (picked) Color.White else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(15.dp),
                                )
                                Text(
                                    name,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (picked) Color.White else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(playerCount) { idx ->
                    OutlinedTextField(
                        value = playerNames[idx],
                        onValueChange = { playerNames[idx] = it },
                        placeholder = { Text("Igralec_ka ${idx + 1}") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // "Is this the same Jure?" — a saved name seated somewhere it has no history
                    val name = playerNames[idx].trim()
                    val others = (0 until playerCount).filter { it != idx }.map { playerNames[it].trim() }
                    if (name.isNotEmpty() && savedSessions.isPossiblyDifferentPerson(name, others, group)) {
                        val context = group?.let { "še ni igral_a v skupini $it" } ?: "še ni igral_a z nikomer od teh"
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 6.dp),
                        ) {
                            Icon(Icons.Filled.PersonSearch, contentDescription = null, tint = StihOrange, modifier = Modifier.size(16.dp))
                            Text(
                                "$name $context. Druga oseba? Dodaj začetnico, npr. »$name K.«",
                                style = MaterialTheme.typography.bodySmall,
                                color = StihOrange,
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable {
                        val players = (0 until playerCount).map { idx ->
                            Player(name = playerNames[idx].trim().ifEmpty { "Igralec_ka ${idx + 1}" })
                        }
                        onStart(players, group)
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Začni igro", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** "Skupina" picker: Brez skupine / existing groups / Nova skupina… */
@Composable
private fun GroupPickerRow(group: String?, groups: List<String>, onGroup: (String?) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    var newGroupDialog by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Outlined.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Text(
            "Skupina",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp).weight(1f),
        )
        Box {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { menuOpen = true }) {
                Text(group ?: "Brez skupine", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Brez skupine") },
                    trailingIcon = { if (group == null) Icon(Icons.Filled.Check, contentDescription = null) },
                    onClick = { onGroup(null); menuOpen = false },
                )
                groups.forEach { name ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        trailingIcon = { if (group == name) Icon(Icons.Filled.Check, contentDescription = null) },
                        onClick = { onGroup(name); menuOpen = false },
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Nova skupina…") },
                    leadingIcon = { Icon(Icons.Filled.CreateNewFolder, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        newGroupName = ""
                        newGroupDialog = true
                    },
                )
            }
        }
    }

    if (newGroupDialog) {
        AlertDialog(
            onDismissRequest = { newGroupDialog = false },
            title = { Text("Nova skupina") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Seje v isti skupini lahko v statistiki gledaš ločeno.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = newGroupName,
                        onValueChange = { newGroupName = it },
                        placeholder = { Text("npr. Torek") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    normalizedGroup(newGroupName)?.let(onGroup)
                    newGroupDialog = false
                }) { Text("Dodaj") }
            },
            dismissButton = { TextButton(onClick = { newGroupDialog = false }) { Text("Prekliči") } },
        )
    }
}
