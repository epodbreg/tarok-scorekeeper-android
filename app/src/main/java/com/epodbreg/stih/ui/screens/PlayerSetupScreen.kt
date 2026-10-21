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

private const val MAX_QUICK_PICKS = 12

/** Player setup. Port of PlayerSetupView.swift. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlayerSetupScreen(
    prefills: List<String> = emptyList(),
    /** Names from saved sessions, most frequent first */
    knownPlayers: List<String> = emptyList(),
    onStart: (List<Player>) -> Unit,
    onCancel: () -> Unit,
) {
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
                        onStart(players)
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Začni igro", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
