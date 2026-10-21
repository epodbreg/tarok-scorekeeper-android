package com.epodbreg.stih.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.epodbreg.stih.logic.SessionFunFacts
import com.epodbreg.stih.logic.TarokStats
import com.epodbreg.stih.model.GameSession
import com.epodbreg.stih.model.TarokContract
import com.epodbreg.stih.model.TarokStrings
import com.epodbreg.stih.ui.components.StatChip
import com.epodbreg.stih.ui.components.StatChipGrid
import com.epodbreg.stih.ui.components.icon
import com.epodbreg.stih.ui.theme.StihBlue
import com.epodbreg.stih.ui.theme.StihBrown
import com.epodbreg.stih.ui.theme.StihGreen
import com.epodbreg.stih.ui.theme.StihOrange
import com.epodbreg.stih.ui.theme.StihTeal
import com.epodbreg.stih.ui.theme.StihYellow
import kotlin.math.abs

/** "Statistika večera" — end-screen fun facts. Each chip only appears when it has data. */
@Composable
fun EveningStatsSection(session: GameSession) {
    val facts = remember(session) { TarokStats.funFacts(session) }

    val chips = buildList<@Composable (Modifier) -> Unit> {
        facts.mostPlayedContract?.let { (contract, count) ->
            add { m -> StatChip(contract.icon, StihBlue, "Najbolj igrana igra", contract.displayName, "$count× igrana", m) }
        }
        facts.roundOfTheNight?.let { hero ->
            val detail = if (hero.contract == TarokContract.KLOP) {
                "klop za ${hero.klopScores?.values?.maxOfOrNull { abs(it) } ?: 0} točk"
            } else {
                "${hero.declarerName} · ${if (hero.totalPoints > 0) "+" else ""}${hero.totalPoints}"
            }
            add { m -> StatChip(Icons.Filled.LocalFireDepartment, StihOrange, "Igra večera", hero.contract.displayName, detail, m) }
        }
        facts.bestSingleRound?.let { best ->
            add { m ->
                StatChip(Icons.Filled.WorkspacePremium, StihYellow, "Največ točk od igre", best.name,
                    "+${best.points} (${best.contract.displayName})", m)
            }
        }
        facts.mostSlepi?.let { s ->
            add { m -> StatChip(Icons.Filled.LocalShipping, StihBrown, "Največ šlepov", s.name, TarokStrings.slepCountText(s.count), m) }
        }
        facts.biggestSlep?.let { s ->
            add { m ->
                StatChip(Icons.Filled.LocalShipping, StihBrown, "Največji šlep", s.name,
                    "+${s.points} (${s.contract.displayName})", m)
            }
        }
        facts.unluckiest?.let { u ->
            add { m -> StatChip(Icons.Filled.Cloud, Color.Gray, "Smola večera", u.name, "${u.count}× zarufal_a", m) }
        }
        facts.avgRoundLengthMs?.let { avg ->
            add { m -> StatChip(Icons.Filled.Timer, StihTeal, "Povprečna runda", TarokStats.formatDuration(avg), null, m) }
        }
        facts.longestStreak?.let { s ->
            add { m ->
                StatChip(Icons.AutoMirrored.Filled.TrendingUp, StihGreen, "Niz zmag", s.name, "${s.count}× zapored v plusu", m)
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
        Text("Statistika večera", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.Style, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Text(headline(facts), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        StatChipGrid(chips)
    }
}

/** "Igrali ste 27 rund taroka · 2 h 14 min" — accusative "1 rundo" for one round. */
private fun headline(facts: SessionFunFacts): String {
    val rounds = if (facts.roundCount == 1) "1 rundo" else TarokStrings.rundaCountText(facts.roundCount)
    val duration = facts.durationMs?.takeIf { it > 60_000 }?.let { " · ${TarokStats.formatDuration(it.toDouble())}" } ?: ""
    return "Igrali ste $rounds taroka$duration"
}
