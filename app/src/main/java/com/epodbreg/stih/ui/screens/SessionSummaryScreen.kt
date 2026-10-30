package com.epodbreg.stih.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.epodbreg.stih.data.SessionManager
import com.epodbreg.stih.model.GameSession
import com.epodbreg.stih.model.Player
import com.epodbreg.stih.model.PlayerScore
import com.epodbreg.stih.model.TarokContract
import com.epodbreg.stih.model.TarokStrings
import com.epodbreg.stih.ui.components.GameHistoryCard
import com.epodbreg.stih.ui.components.KlopGameCard
import com.epodbreg.stih.ui.components.RadlcRow
import com.epodbreg.stih.ui.theme.StihGreen
import com.epodbreg.stih.ui.theme.StihOrange
import com.epodbreg.stih.ui.theme.StihRed

/** Session summary shown when a game ends. Port of SessionSummaryView.swift. */
@Composable
fun SessionSummaryScreen(
    session: GameSession,
    onSave: () -> Unit,
    onNewWithSamePlayers: () -> Unit,
    onDiscard: () -> Unit,
) {
    var showDiscardConfirmation by remember { mutableStateOf(false) }

    // Edge to edge: keep clear of the status bar and the navigation buttons
    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        // Header
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 16.dp),
        ) {
            Text("Povzetek seje", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                session.playerNames.joinToString(", "),
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    Icons.Filled.CalendarMonth, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp),
                )
                Text(
                    SessionManager.formatDate(session.startTime),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (session.roundCount > 0) {
                    Icon(
                        Icons.Filled.SportsEsports, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp),
                    )
                    Text(
                        TarokStrings.rundaCountText(session.roundCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        HorizontalDivider()

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Končni rezultati", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            val scores = session.finalScores?.sortedBy { it.position }
            if (scores != null) {
                items(scores) { score ->
                    FinalScoreRow(
                        score = score,
                        rank = score.position - 1,
                        player = session.players.firstOrNull { it.name == score.name },
                        isSlepar = score.name in session.slepaci,
                    )
                }
            } else {
                item {
                    Text(
                        "Končni rezultati niso na voljo",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    )
                }
            }

            if (session.roundCount > 0) {
                item { EveningStatsSection(session) }
            }

            if (session.games.isNotEmpty()) {
                item {
                    Text(
                        "Zgodovina iger",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(session.games.sortedByDescending { it.timestamp }) { game ->
                    if (game.contract == TarokContract.KLOP) {
                        KlopGameCard(game = game, players = session.players)
                    } else {
                        GameHistoryCard(game = game)
                    }
                }
            }
        }

        // Radlci penalty clarifier
        val penaltyPlayers = session.players
            .map { it.name to it.unusedRadlciCount }
            .filter { it.second > 0 }
        if (penaltyPlayers.isNotEmpty()) {
            Surface(color = StihOrange.copy(alpha = 0.1f), shape = RoundedCornerShape(10.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(10.dp),
                ) {
                    Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = StihOrange, modifier = Modifier.size(18.dp))
                    Column {
                        Text(
                            "Neuporabljeni radlci so vplivali na rezultate",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            penaltyPlayers.joinToString(", ") { "${it.first}: −${it.second} radlc" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Bottom buttons
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            BottomActionButton(
                text = "Shrani sejo",
                icon = Icons.Filled.CheckCircle,
                background = MaterialTheme.colorScheme.primary,
                foreground = Color.White,
                onClick = onSave,
            )
            BottomActionButton(
                text = "Nova igra z istimi igralci",
                icon = Icons.Filled.Group,
                background = MaterialTheme.colorScheme.surfaceVariant,
                foreground = MaterialTheme.colorScheme.primary,
                onClick = onNewWithSamePlayers,
            )
            BottomActionButton(
                text = "Ne shrani",
                icon = Icons.Filled.Close,
                background = MaterialTheme.colorScheme.surfaceVariant,
                foreground = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = { showDiscardConfirmation = true },
            )
        }
    }

    if (showDiscardConfirmation) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirmation = false },
            title = { Text("Ne shrani seje?") },
            text = { Text("Seja se ne bo shranila v zgodovino.") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardConfirmation = false
                    onDiscard()
                }) { Text("Ne shrani", color = StihRed) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirmation = false }) { Text("Prekliči") }
            },
        )
    }
}

@Composable
fun FinalScoreRow(
    score: PlayerScore,
    rank: Int,
    player: Player?,
    isSlepar: Boolean = false,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = standingBackground(rank),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            // Standing badge
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(standingBadgeColor(rank)),
                contentAlignment = Alignment.Center,
            ) {
                Text("${rank + 1}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(score.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    if (isSlepar) {
                        Icon(
                            Icons.Filled.LocalShipping,
                            contentDescription = "Šleper",
                            tint = com.epodbreg.stih.ui.theme.StihBrown,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                if (player != null && player.radlci.isNotEmpty()) {
                    RadlcRow(radlci = player.radlci, maxPips = 8, size = 16.dp)
                }
            }
            Text("${score.score}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun BottomActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    background: Color,
    foreground: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(18.dp))
            Text(text, color = foreground, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun standingBackground(rank: Int): Color {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    return when (rank) {
        0 -> StihGreen.copy(alpha = if (dark) 0.33f else 0.16f)
        1 -> Color(0xFF007AFF).copy(alpha = if (dark) 0.30f else 0.13f)
        2 -> Color(0xFFAF52DE).copy(alpha = if (dark) 0.24f else 0.13f)
        3 -> Color.Gray.copy(alpha = if (dark) 0.21f else 0.13f)
        4 -> Color(0xFF30B0C7).copy(alpha = if (dark) 0.19f else 0.10f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
}

private fun standingBadgeColor(rank: Int): Color = when (rank) {
    0 -> Color(0xFFFFCC00)
    1 -> Color.Gray
    2 -> Color(0xFFA2845E)
    3 -> Color(0xFFAF52DE)
    else -> Color(0xFF5856D6)
}
