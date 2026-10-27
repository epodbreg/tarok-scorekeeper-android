package com.epodbreg.stih.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.epodbreg.stih.data.SessionManager
import com.epodbreg.stih.model.TarokStrings
import com.epodbreg.stih.model.normalizedGroup
import com.epodbreg.stih.model.withSamePlayers
import com.epodbreg.stih.ui.theme.StihRed
import java.util.UUID

/** Small "📁 Torek" capsule shown on session rows. */
@Composable
fun GroupBadge(name: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
            Text(name, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * Put sessions into a group: pick one, type a new one, remove from group, or delete a
 * group (long-press; sessions stay). Port of GroupAssignView.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupAssignSheet(
    sessionManager: SessionManager,
    sessionIds: Set<UUID>,
    onDone: () -> Unit,
) {
    val sessions = sessionManager.savedSessions.filter { it.id in sessionIds }
    var newGroupName by remember { mutableStateOf("") }
    var includeSameTable by remember { mutableStateOf(false) }
    var groupToDelete by remember { mutableStateOf<String?>(null) }

    // When every picked session has the same players, offer the rest of that table's sessions too
    val first = sessions.firstOrNull()
    val sameTableExtras: Set<UUID> =
        if (first != null && sessions.all { it.playerNames.toSet() == first.playerNames.toSet() }) {
            sessionManager.savedSessions.withSamePlayers(first).map { it.id }.toSet() - sessionIds
        } else emptySet()
    val targetIds = if (includeSameTable) sessionIds + sameTableExtras else sessionIds
    // Group shared by all picked sessions, for the checkmark
    val currentGroup = sessions.map { it.groupName }.toSet().singleOrNull()

    fun assign(group: String?) {
        sessionManager.setGroup(group, targetIds)
        onDone()
    }

    ModalBottomSheet(onDismissRequest = onDone, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Text(
                if (sessionIds.size == 1) "Skupina seje" else "Skupina za ${TarokStrings.sejaCountText(sessionIds.size)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (sameTableExtras.isNotEmpty() && first != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Še ${TarokStrings.sejaCountText(sameTableExtras.size)} z istimi igralci", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            first.playerNames.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = includeSameTable, onCheckedChange = { includeSameTable = it })
                }
                HorizontalDivider()
            }

            Text(
                "Skupine",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
            )
            sessionManager.allGroupNames.forEach { group ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { assign(group) }
                        .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                ) {
                    Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(group, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    if (group == currentGroup) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    IconButton(onClick = { groupToDelete = group }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Izbriši skupino", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                OutlinedTextField(
                    value = newGroupName,
                    onValueChange = { newGroupName = it },
                    placeholder = { Text("Nova skupina (npr. Torek)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { normalizedGroup(newGroupName)?.let(::assign) }),
                    modifier = Modifier.weight(1f),
                )
                if (normalizedGroup(newGroupName) != null) {
                    TextButton(onClick = { assign(newGroupName) }) { Text("Dodaj", fontWeight = FontWeight.SemiBold) }
                }
            }

            if (sessions.any { it.groupName != null }) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { assign(null) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Icon(Icons.Filled.FolderOff, contentDescription = null, tint = StihRed)
                    Text("Odstrani iz skupine", color = StihRed, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    groupToDelete?.let { group ->
        AlertDialog(
            onDismissRequest = { groupToDelete = null },
            title = { Text("Izbriši skupino?") },
            text = { Text("Seje ostanejo shranjene, samo niso več v skupini »$group«.") },
            confirmButton = {
                TextButton(onClick = {
                    sessionManager.deleteGroup(group)
                    groupToDelete = null
                }) { Text("Izbriši", color = StihRed) }
            },
            dismissButton = { TextButton(onClick = { groupToDelete = null }) { Text("Prekliči") } },
        )
    }
}
