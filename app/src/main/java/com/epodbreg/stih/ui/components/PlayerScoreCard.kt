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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
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

/** How a card is drawn on the game screen */
enum class PlayerCardStyle {
    /** Full width: crown, name, score, radlci, buttons; opens in place */
    STANDARD,
    /** Half width, two per row (compact screen); tap to open its details panel */
    HALF,
    /** Full-width panel under an opened half card: radlci, ±, the player's games */
    DETAILS,
}

/**
 * Main-game player card. Port of PlayerScoreCard.swift: crown for a player alone in first
 * place, dealer chip, big raw score with the radlc-adjusted score below, tappable radlci,
 * the player's games when opened, penalty button.
 */
@Composable
fun PlayerScoreCard(
    player: Player,
    /** Place, from 0; tied players share one */
    standing: Int,
    /** Alone in first place: gets the crown (nobody does while the top is tied) */
    isLeader: Boolean,
    isExpanded: Boolean,
    isDealer: Boolean,
    isSlepar: Boolean,
    liveNetScore: Int,
    showNetScore: Boolean,
    gameHistory: List<GameEntry>,
    onExpandToggle: () -> Unit,
    onPenalty: () -> Unit,
    /** Null = auto-radlci lock: pips are read-only status. */
    onRadlcTap: ((Int) -> Unit)?,
    modifier: Modifier = Modifier,
    style: PlayerCardStyle = PlayerCardStyle.STANDARD,
) {
    val tint = when {
        player.score > 0 -> StihGreen
        player.score < 0 -> StihRed
        else -> Color.Gray
    }
    // An opened half card and its panel share an outline, so they read as one
    val outlined = (style == PlayerCardStyle.HALF && isExpanded) || style == PlayerCardStyle.DETAILS
    val shape = RoundedCornerShape(12.dp)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(2.dp, if (outlined) MaterialTheme.colorScheme.primary else tint.copy(alpha = 0.3f), shape),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
    ) {
        when (style) {
            PlayerCardStyle.STANDARD -> Column(modifier = Modifier.padding(16.dp)) {
                StandardContent(
                    player, isLeader, isExpanded, isDealer, isSlepar, liveNetScore, showNetScore,
                    onExpandToggle, onPenalty, onRadlcTap,
                )
                AnimatedVisibility(visible = isExpanded) {
                    PlayerGames(gameHistory, player, isOpen = isExpanded)
                }
            }
            PlayerCardStyle.HALF -> HalfContent(
                player, standing, isExpanded, isDealer, isSlepar, liveNetScore, showNetScore, onExpandToggle,
            )
            PlayerCardStyle.DETAILS -> Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        player.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onPenalty, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Outlined.Exposure,
                            contentDescription = "Dodaj penal ali bonus",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(onClick = onExpandToggle, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.ExpandLess,
                            contentDescription = "Zapri podrobnosti",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                if (player.radlci.isNotEmpty()) {
                    RadlcRow(radlci = player.radlci, onTap = onRadlcTap)
                }
                if (playerGames(gameHistory, player).isEmpty()) {
                    Text(
                        "Še brez iger.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    PlayerGames(gameHistory, player, isOpen = true)
                }
            }
        }
    }
}

@Composable
private fun StandardContent(
    player: Player,
    isLeader: Boolean,
    isExpanded: Boolean,
    isDealer: Boolean,
    isSlepar: Boolean,
    liveNetScore: Int,
    showNetScore: Boolean,
    onExpandToggle: () -> Unit,
    onPenalty: () -> Unit,
    onRadlcTap: ((Int) -> Unit)?,
) {
    // Line 1: crown + name + score
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (isLeader) {
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
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isSlepar) {
                    Spacer(Modifier.width(5.dp))
                    SleparIcon(14.dp)
                }
            }
            if (isDealer) {
                Spacer(Modifier.padding(top = 2.dp))
                DealerBadge(fontSize = 11, horizontal = 8.dp, vertical = 3.dp)
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
}

/**
 * Two lines: place · name … score, then Deli + radlci … radlc-adjusted score. The score and
 * the Deli badge never shrink; the name and radlci give way instead. The whole card opens it.
 */
@Composable
private fun HalfContent(
    player: Player,
    standing: Int,
    isExpanded: Boolean,
    isDealer: Boolean,
    isSlepar: Boolean,
    liveNetScore: Int,
    showNetScore: Boolean,
    onExpandToggle: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                onClickLabel = if (isExpanded) "Zapre podrobnosti" else "Odpre podrobnosti",
                onClick = onExpandToggle,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "${standing + 1}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Name and šlepar mark take what's left; the score never shrinks
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    player.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isSlepar) SleparIcon(12.dp)
            }
            Text(
                "${player.score}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = scoreColor(player.score),
                maxLines = 1,
                softWrap = false,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.heightIn(min = 14.dp),
        ) {
            if (isDealer) DealerBadge(fontSize = 10, horizontal = 6.dp, vertical = 2.dp)
            Box(modifier = Modifier.weight(1f)) {
                if (player.radlci.isNotEmpty()) {
                    // The most detailed version that fits next to the badge and the score
                    FirstThatFits(
                        { RadlcRow(player.radlci, maxPips = 5, size = 11.dp, pipsPerGroup = 2) },
                        { RadlcRow(player.radlci, maxPips = 3, size = 11.dp, pipsPerGroup = 1) },
                        { RadlcRow(player.radlci, maxPips = 2, size = 9.dp, pipsPerGroup = 1) },
                    )
                }
            }
            if (showNetScore) {
                Text(
                    "$liveNetScore",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/**
 * Shows the first of [options] that fits the width (the last one if none does). A plain
 * layout, not a subcompose one, so the card around it can still be asked for its height.
 */
@Composable
private fun FirstThatFits(vararg options: @Composable () -> Unit) {
    Layout(content = { options.forEach { it() } }) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity)
        val placeables = measurables.map { it.measure(loose) }
        val chosen = placeables.firstOrNull { it.width <= constraints.maxWidth } ?: placeables.lastOrNull()
            ?: return@Layout layout(0, 0) {}
        layout(minOf(chosen.width, constraints.maxWidth), chosen.height) { chosen.place(0, 0) }
    }
}

@Composable
private fun DealerBadge(fontSize: Int, horizontal: Dp, vertical: Dp) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(StihOrange)
            .padding(horizontal = horizontal, vertical = vertical),
    ) {
        Text("Deli", color = Color.White, fontSize = fontSize.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun SleparIcon(size: Dp) {
    Icon(
        Icons.Filled.LocalShipping,
        contentDescription = "Šleper",
        tint = StihBrown,
        modifier = Modifier.size(size),
    )
}

/** The player's games, aligned with the card: the latest few, older ones on request */
@Composable
private fun PlayerGames(gameHistory: List<GameEntry>, player: Player, isOpen: Boolean) {
    val games = playerGames(gameHistory, player)
    var showAll by remember { mutableStateOf(false) }
    LaunchedEffect(isOpen) { if (!isOpen) showAll = false }
    val hidden = if (showAll) 0 else maxOf(0, games.size - RECENT_GAMES_SHOWN)
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
    ) {
        if (hidden > 0) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clickable { showAll = true }
                    .padding(vertical = 2.dp),
            ) {
                Icon(
                    Icons.Filled.ExpandLess, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp),
                )
                Text(
                    "Prejšnje igre ($hidden)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        games.drop(hidden).forEach { game -> PlayerHistoryRow(game = game, player = player) }
    }
}

private const val RECENT_GAMES_SHOWN = 8

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
                    val points = game.pointsFor(player.name)
                    Text(
                        if (points > 0) "+$points" else "$points",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isWin) StihGreen else StihRed,
                    )
                }
            }
        }
    }
}
