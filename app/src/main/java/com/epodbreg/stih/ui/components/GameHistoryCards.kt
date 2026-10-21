package com.epodbreg.stih.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.epodbreg.stih.data.SessionManager
import com.epodbreg.stih.model.GameEntry
import com.epodbreg.stih.model.HandBonus
import com.epodbreg.stih.model.Player
import com.epodbreg.stih.model.TarokContract
import com.epodbreg.stih.ui.theme.StihBlue
import com.epodbreg.stih.ui.theme.StihGreen
import com.epodbreg.stih.ui.theme.StihRed

/**
 * History card for regular (non-klop) games. Port of GameHistoryCard.swift.
 * Tap to edit, long-press for delete handled by the caller via onLongPress.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GameHistoryCard(
    game: GameEntry,
    onTap: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .let { m ->
                if (onTap != null || onLongPress != null) {
                    m.combinedClickable(onClick = { onTap?.invoke() }, onLongClick = onLongPress)
                } else m
            },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header: icon + name + kontra badge + points badge
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    game.contract.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    contractCardName(game.contract),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))

                KontraStyle.forLevel(game.pointsBreakdown.kontraLevel)?.let { style ->
                    Surface(shape = CircleShape, color = style.color) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                        ) {
                            Icon(style.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
                            Text(
                                "${style.name} ×${1 shl game.pointsBreakdown.kontraLevel}",
                                color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                val positive = game.totalPoints > 0
                Surface(
                    shape = CircleShape,
                    color = (if (positive) StihGreen else StihRed).copy(alpha = 0.15f),
                ) {
                    Text(
                        if (positive) "+${game.totalPoints}" else "${game.totalPoints}",
                        color = if (positive) StihGreen else StihRed,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            // Details
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                contractCardLabel(game.contract)?.let { label ->
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Igralec_ka:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(game.declarerName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                }
                partnerDisplayText(game)?.let { partner ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Soigralec_ka:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(partner, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    }
                }
                // Who called the kontra (if recorded), in the kontra level's colour
                val kontraStyle = KontraStyle.forLevel(game.pointsBreakdown.kontraLevel)
                if (game.kontraCallerName != null && kontraStyle != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Kontro dal_a:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            game.kontraCallerName,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            // Mort is near-black: use the text colour so it stays readable in dark mode
                            color = if (game.pointsBreakdown.kontraLevel >= 4) MaterialTheme.colorScheme.onSurface else kontraStyle.color,
                        )
                    }
                }
                if (game.bonuses.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        game.bonuses.forEach { bonus -> BonusBadge(bonus) }
                    }
                }
            }

            // Footer: outcome dot + timestamp
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (game.outcome.isWin) StihGreen else StihRed)
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Filled.Schedule, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    SessionManager.formatTime(game.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BonusBadge(bonus: HandBonus) {
    val isLost = !bonus.won
    val badgeColor = if (isLost) StihRed else bonus.type.displayColor
    Surface(shape = CircleShape, color = badgeColor) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Icon(
                bonus.type.icon, contentDescription = null,
                tint = Color.White.copy(alpha = if (isLost) 0.7f else 1f),
                modifier = Modifier.size(11.dp),
            )
            Text(
                bonus.type.shortName,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                textDecoration = if (isLost) TextDecoration.LineThrough else TextDecoration.None,
            )
            if (bonus.kontraLevel > 0) {
                Text(
                    "×${1 shl bonus.kontraLevel}",
                    color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

/** Klop history card with per-player scores. Port of KlopGameCard.swift. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KlopGameCard(
    game: GameEntry,
    players: List<Player>,
    onTap: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .let { m ->
                if (onTap != null || onLongPress != null) {
                    m.combinedClickable(onClick = { onTap?.invoke() }, onLongClick = onLongPress)
                } else m
            },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    TarokContract.KLOP.icon, contentDescription = null,
                    tint = StihBlue, modifier = Modifier.size(20.dp),
                )
                Text("Klop", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                players.forEach { player ->
                    val score = game.klopScores?.get(player.name) ?: 0
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            player.name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        when (score) {
                            70 -> KlopSpecialBadge("Prazen_a", StihGreen)
                            -70 -> KlopSpecialBadge("Poln_a", StihRed)
                            0 -> {}
                            else -> {
                                Surface(
                                    shape = CircleShape,
                                    color = (if (score > 0) StihGreen else StihRed).copy(alpha = 0.15f),
                                ) {
                                    Text(
                                        if (score > 0) "+$score" else "$score",
                                        color = if (score > 0) StihGreen else StihRed,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Schedule, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    SessionManager.formatTime(game.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun KlopSpecialBadge(text: String, color: Color) {
    Surface(shape = CircleShape, color = color) {
        Text(
            text,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

private fun contractCardName(contract: TarokContract): String = when (contract) {
    TarokContract.PENALTY_MOND -> "Mond"
    TarokContract.PENALTY_RENONS -> "Renons"
    TarokContract.PAGAT_BONUS -> "Pagat Ultimo"
    else -> contract.displayName
}

private fun contractCardLabel(contract: TarokContract): String? = when (contract) {
    TarokContract.PENALTY_MOND, TarokContract.PENALTY_RENONS -> "Kazen"
    TarokContract.PAGAT_BONUS -> "Nagrada"
    else -> null
}

private fun partnerDisplayText(game: GameEntry): String? = when (game.contract) {
    TarokContract.KLOP, TarokContract.SOLO_TRI, TarokContract.SOLO_DVE, TarokContract.SOLO_ENA,
    TarokContract.SOLO_BREZ, TarokContract.ODPRTI_BERAC,
    TarokContract.PENALTY_MOND, TarokContract.PENALTY_RENONS, TarokContract.PAGAT_BONUS -> null
    else -> game.partnerName
}
