package com.epodbreg.stih.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.epodbreg.stih.data.SessionManager
import com.epodbreg.stih.data.SettingsManager
import com.epodbreg.stih.logic.PlayerStats
import com.epodbreg.stih.logic.TarokStats
import com.epodbreg.stih.model.BonusType
import com.epodbreg.stih.model.TarokStrings
import com.epodbreg.stih.ui.components.StatChip
import com.epodbreg.stih.ui.components.StatChipGrid
import com.epodbreg.stih.ui.components.StatRow
import com.epodbreg.stih.ui.components.StatSection
import com.epodbreg.stih.ui.components.WinRateBar
import com.epodbreg.stih.ui.components.icon
import com.epodbreg.stih.ui.theme.StihBlue
import com.epodbreg.stih.ui.theme.StihBrown
import com.epodbreg.stih.ui.theme.StihGreen
import com.epodbreg.stih.ui.theme.StihOrange
import com.epodbreg.stih.ui.theme.StihRed
import com.epodbreg.stih.ui.theme.StihYellow
import java.util.UUID

/** "Tarok statistika" — combined stats across selected saved sessions. Port of StatsView.swift. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    sessionManager: SessionManager,
    settings: SettingsManager,
    onBack: () -> Unit,
    onManagePlayers: () -> Unit,
) {
    val sessions = sessionManager.savedSessions
    var selectedIds by remember { mutableStateOf(sessions.map { it.id }.toSet()) }
    var showFilter by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }

    val selected = sessions.filter { it.id in selectedIds }
    // Placeholder seat names are hidden unless nothing else exists
    val allNames = selected.flatMap { it.playerNames }.toSet()
    val realNames = allNames.filterNot { TarokStrings.isPlaceholderName(it) }
    val playerNames = (realNames.ifEmpty { allNames }).sorted()
    val currentPlayer = settings.myPlayerName.takeIf { it in playerNames } ?: playerNames.firstOrNull()
    val stats = remember(selected, currentPlayer) {
        currentPlayer?.let { TarokStats.compute(selected)[it] }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tarok statistika") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Nazaj") }
                },
                actions = {
                    TextButton(onClick = { showFilter = true }) {
                        Icon(Icons.Filled.FilterList, contentDescription = "Izberi seje", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("${selectedIds.size}/${sessions.size}")
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Player picker
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Box {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { pickerOpen = true },
                    ) {
                        Text(currentPlayer ?: "—", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = "Izberi igralca_ko")
                    }
                    DropdownMenu(expanded = pickerOpen, onDismissRequest = { pickerOpen = false }) {
                        playerNames.forEach { name ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                trailingIcon = { if (name == currentPlayer) Icon(Icons.Filled.Check, contentDescription = null) },
                                onClick = {
                                    settings.updateMyPlayerName(name)
                                    pickerOpen = false
                                },
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Uredi igralce…") },
                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                            onClick = {
                                pickerOpen = false
                                onManagePlayers()
                            },
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                stats?.let {
                    Text(
                        TarokStrings.rundaCountText(it.roundsPlayed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (stats == null) {
                EmptyStats()
            } else {
                OverviewChips(stats)
                GamesSection(stats)
                SlepSection(stats)
                SpecialsSection(stats)
                BonusesSection(stats)
                RecordsSection(stats)
            }
        }
    }

    if (showFilter) {
        SessionFilterSheet(
            sessionManager = sessionManager,
            selectedIds = selectedIds,
            onChange = { selectedIds = it },
            onDismiss = { showFilter = false },
        )
    }
}

@Composable
private fun OverviewChips(s: PlayerStats) {
    StatChipGrid(
        listOf(
            { m ->
                StatChip(Icons.Filled.CalendarMonth, StihBlue, "Seje", "${s.sessionsPlayed}",
                    "${s.sessionsWon}× zmaga · ${s.podiums}× stopničke", m)
            },
            { m ->
                StatChip(Icons.Filled.Functions, if (s.pointsEarned >= 0) StihGreen else StihRed, "Skupaj točk",
                    "${if (s.pointsEarned > 0) "+" else ""}${s.pointsEarned}",
                    TarokStrings.rundaCountText(s.roundsPlayed), m)
            },
        ),
    )
}

@Composable
private fun GamesSection(s: PlayerStats) {
    StatSection("Igre", Icons.Filled.SportsEsports, StihGreen) {
        if (s.declared == 0) {
            Text("Ni odigranih iger", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@StatSection
        }
        WinRateBar(won = s.declaredWon, total = s.declared)
        s.favoriteContract?.let { fav ->
            StatRow("Najljubša igra", "${fav.displayName} (${s.contractCounts[fav] ?: 0}×)")
        }
        // Per-contract breakdown, most played first
        s.contractCounts.entries.sortedWith(PlayerStats.byCountThenRawValue).forEach { (contract, count) ->
            val wins = s.contractWins[contract] ?: 0
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(contract.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Text(contract.displayName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text("$wins/$count", fontWeight = FontWeight.SemiBold, color = if (wins * 2 >= count) StihGreen else StihOrange)
            }
        }
    }
}

@Composable
private fun SlepSection(s: PlayerStats) {
    // Same pickup-truck icon the in-game cards use for the šlepar
    StatSection("Šlepi", Icons.Filled.LocalShipping, StihBrown) {
        StatRow("Rund kot soigralec_ka", "${s.partnerRounds}")
        StatRow("Dobljeni šlepi", "${s.partnerWins}", if (s.partnerWins > 0) StihGreen else Color.Unspecified)
        StatRow("Izgubljeni šlepi", "${s.partnerLosses}", if (s.partnerLosses > 0) StihRed else Color.Unspecified)
        if (s.biggestSlep > 0) StatRow("Največji šlep", "+${s.biggestSlep}", StihGreen)
    }
}

@Composable
private fun SpecialsSection(s: PlayerStats) {
    StatSection("Posebnosti", Icons.Filled.AutoAwesome, StihYellow) {
        StatRow("Valati", "${s.valati}")
        StatRow("Prazni klopi", "${s.klopPrazen}", if (s.klopPrazen > 0) StihGreen else Color.Unspecified)
        StatRow("Polni klopi", "${s.klopPoln}", if (s.klopPoln > 0) StihRed else Color.Unspecified)
        StatRow("Kontrirane igre", "${s.kontraRounds}")
        StatRow(
            "Dane kontre",
            if (s.kontrasCalled > 0) "${s.kontrasCalled} (${TarokStrings.uspesnihText(s.kontrasWon)})" else "0",
            if (s.kontrasCalled > 0 && s.kontrasWon * 2 >= s.kontrasCalled) StihGreen else Color.Unspecified,
        )
        StatRow("Zarufal_a", "${s.zarufalCount}×")
    }
}

/** Napovedi/bonusi (won ✓ / lost ✗) plus side entries — kazni and pagat awards. */
@Composable
private fun BonusesSection(s: PlayerStats) {
    val shown = BonusType.entries.filter { (s.bonusesWon[it] ?: 0) + (s.bonusesLost[it] ?: 0) > 0 }
    val hasSideEntries = s.mondKazCount + s.renonsCount + s.pagatAwards + s.pagatLosses > 0
    if (shown.isEmpty() && !hasSideEntries) return

    StatSection("Napovedi, bonusi in minusi", Icons.Filled.Star, StihOrange) {
        shown.forEach { type ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(type.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Text(type.displayName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text("${s.bonusesWon[type] ?: 0} ✓ · ${s.bonusesLost[type] ?: 0} ✗", fontWeight = FontWeight.SemiBold)
            }
        }
        if (shown.isNotEmpty() && hasSideEntries) HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
        if (s.pagatAwards > 0) StatRow("Pagat ultimo nagrade", "${s.pagatAwards}×", StihGreen)
        if (s.pagatLosses > 0) StatRow("Pagat ultimo izgube", "${s.pagatLosses}×", StihRed)
        if (s.mondKazCount > 0) StatRow("Mond kazen", "${s.mondKazCount}×", StihRed)
        if (s.renonsCount > 0) StatRow("Renons", "${s.renonsCount}×", StihRed)
    }
}

@Composable
private fun RecordsSection(s: PlayerStats) {
    StatSection("Rekordi", Icons.Filled.EmojiEvents, StihYellow) {
        s.biggestWin?.let { StatRow("Največja zmaga", "+${it.points} (${it.contract.displayName})", StihGreen) }
        s.worstLoss?.let { StatRow("Najhujši poraz", "${it.points} (${it.contract.displayName})", StihRed) }
        if (s.biggestWin == null && s.worstLoss == null) {
            Text("Še brez rekordov — igraj!", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyStats() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 60.dp),
    ) {
        Icon(Icons.Filled.BarChart, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
        Text("Ni podatkov", style = MaterialTheme.typography.titleMedium)
        Text("Izberi vsaj eno sejo s shranjenimi igrami.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionFilterSheet(
    sessionManager: SessionManager,
    selectedIds: Set<UUID>,
    onChange: (Set<UUID>) -> Unit,
    onDismiss: () -> Unit,
) {
    val sessions = sessionManager.savedSessions.sortedByDescending { it.startTime }
    val allSelected = selectedIds.size == sessions.size
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
            Text("Izberi seje", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = { onChange(if (allSelected) emptySet() else sessions.map { it.id }.toSet()) }) {
                Text(if (allSelected) "Odznači vse" else "Izberi vse")
            }
        }
        LazyColumn(modifier = Modifier.padding(bottom = 24.dp)) {
            items(sessions, key = { it.id }) { session ->
                val checked = session.id in selectedIds
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onChange(if (checked) selectedIds - session.id else selectedIds + session.id) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(SessionManager.getSessionName(session), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            "${SessionManager.formatDate(session.startTime)} · ${TarokStrings.rundaCountText(session.roundCount)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        if (checked) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
