package com.epodbreg.stih.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.Exposure
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.epodbreg.stih.model.GameEntry
import com.epodbreg.stih.model.HandOutcome
import com.epodbreg.stih.model.Player
import com.epodbreg.stih.model.TarokContract
import com.epodbreg.stih.model.TarokStrings
import com.epodbreg.stih.ui.theme.StihBrown
import com.epodbreg.stih.ui.theme.StihGreen
import com.epodbreg.stih.ui.theme.StihOrange
import com.epodbreg.stih.ui.theme.StihRed
import com.epodbreg.stih.ui.theme.StihYellow
import com.epodbreg.stih.ui.theme.scoreColor

/**
 * Main-game player card. Port of PlayerScoreCard.swift (standard layout):
 * crown for 1st place, dealer chip, big raw score with net score below,
 * tappable radlci, expandable per-player game history, penalty button.
 */
@Composable
fun PlayerScoreCard(
    player: Player,
    standing: Int,
    isExpanded: Boolean,
    isDealer: Boolean,
    isSlepar: Boolean,
    liveNetScore: Int,
    showNetScore: Boolean,
    gameHistory: List<GameEntry>,
    onExpandToggle: () -> Unit,
    onPenalty: () -> Unit,
    onRadlcTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = when {
        player.score > 0 -> StihGreen
        player.score < 0 -> StihRed
        else -> Color.Gray
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(2.dp, tint.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Line 1: crown + name + score
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (standing == 0) {
                    Icon(
                        Icons.Filled.WorkspacePremium,
                        contentDescription = "Prvo mesto",
                        tint = StihYellow,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            player.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                        if (isSlepar) {
                            Spacer(Modifier.width(5.dp))
                            Icon(
                                Icons.Filled.LocalShipping,
                                contentDescription = "Šleper",
                                tint = StihBrown,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                    if (isDealer) {
                        Spacer(Modifier.padding(top = 2.dp))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(StihOrange)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                "Deli",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${player.score}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = scoreColor(player.score),
                    )
                    if (showNetScore) {
                        Text(
                            "$liveNetScore",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Radlci
            if (player.radlci.isNotEmpty()) {
                Spacer(Modifier.padding(top = 8.dp))
                RadlcRow(radlci = player.radlci, onTap = onRadlcTap)
            }

            // Line 2: expand (left) + penalty (right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onExpandToggle, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (isExpanded) "Skrči" else "Razširi",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(onClick = onPenalty, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Outlined.Exposure,
                        contentDescription = "Dodaj penal ali bonus",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // Expanded per-player history
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .padding(top = 2.dp, bottom = 8.dp),
                ) {
                    playerGames(gameHistory, player).forEach { game ->
                        PlayerHistoryRow(game = game, player = player)
                    }
                }
            }
        }
    }
}

private fun playerGames(gameHistory: List<GameEntry>, player: Player): List<GameEntry> =
    gameHistory.filter { game ->
        game.declarerName == player.name ||
            (game.partnerName != null &&
                game.partnerName != TarokStrings.ZARUFAL &&
                game.partnerName == player.name) ||
            game.contract == TarokContract.KLOP
    }

@Composable
private fun PlayerHistoryRow(game: GameEntry, player: Player) {
    val isLoss = when (game.contract) {
        TarokContract.PENALTY_RENONS, TarokContract.PENALTY_MOND -> true
        TarokContract.KLOP -> (game.klopScores?.get(player.name) ?: 0) < 0
        else -> game.outcome.isLoss
    }
    val isPartner = game.partnerName == player.name

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isLoss) StihRed.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
        ) {
            Icon(
                game.contract.icon,
                contentDescription = null,
                tint = if (isLoss) StihRed else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )

            when (game.contract) {
                TarokContract.KLOP -> {
                    Text("Klop", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.weight(1f))
                    val v = game.klopScores?.get(player.name) ?: 0
                    Text(
                        if (v == 0) "0" else if (v > 0) "+$v" else "$v",
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            v < 0 -> StihRed
                            v > 0 -> StihGreen
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                TarokContract.PENALTY_RENONS -> {
                    Text("Renons", style = MaterialTheme.typography.bodySmall, color = StihRed)
                    Spacer(Modifier.weight(1f))
                }
                TarokContract.PENALTY_MOND -> {
                    Text("Izgubljen mond", style = MaterialTheme.typography.bodySmall, color = StihRed)
                    Spacer(Modifier.weight(1f))
                }
                else -> {
                    Text(game.contract.displayName, style = MaterialTheme.typography.bodySmall)
                    if (game.declarerName == player.name) {
                        Icon(
                            Icons.Filled.Person, contentDescription = "Igralec",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(11.dp),
                        )
                    } else if (isPartner) {
                        Icon(
                            Icons.Filled.LocalShipping, contentDescription = "Soigralec",
                            tint = StihBrown, modifier = Modifier.size(11.dp),
                        )
                    }
                    game.bonuses.forEach { bonus ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Icon(
                                bonus.type.icon, contentDescription = null,
                                tint = bonus.type.displayColor,
                                modifier = Modifier.size(10.dp),
                            )
                            Text(
                                bonus.type.abbreviation,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = bonus.type.displayColor,
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    val isWin = game.outcome == HandOutcome.ZMAGAL || game.outcome == HandOutcome.ZMAGAL_SLEPAL
                    Icon(
                        if (isWin) Icons.Filled.Check else Icons.Filled.Close,
                        contentDescription = null,
                        tint = if (isWin) StihGreen else StihRed,
                        modifier = Modifier.size(11.dp),
                    )
                    Text(
                        if (game.totalPoints > 0) "+${game.totalPoints}" else "${game.totalPoints}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isWin) StihGreen else StihRed,
                    )
                }
            }
        }
    }
}
