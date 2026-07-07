package com.epodbreg.stih.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.epodbreg.stih.model.Player

/** Player setup. Port of PlayerSetupView.swift. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSetupScreen(
    prefills: List<String> = emptyList(),
    onStart: (List<Player>) -> Unit,
    onCancel: () -> Unit,
) {
    var playerCount by remember { mutableStateOf(maxOf(3, prefills.size)) }
    val playerNames = remember {
        (prefills + List(5) { "" }).take(5).toMutableStateList()
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
                            Player(name = playerNames[idx].ifBlank { "Igralec_ka ${idx + 1}" })
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
