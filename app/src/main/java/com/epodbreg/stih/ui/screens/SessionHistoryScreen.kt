package com.epodbreg.stih.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.width
import com.epodbreg.stih.ui.components.GroupAssignSheet
import com.epodbreg.stih.ui.components.GroupBadge
import java.util.UUID
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.People
import com.epodbreg.stih.data.SettingsManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.epodbreg.stih.data.SessionManager
import com.epodbreg.stih.logic.csvExport
import com.epodbreg.stih.model.GameSession
import com.epodbreg.stih.model.Player
import com.epodbreg.stih.model.TarokContract
import com.epodbreg.stih.model.TarokStrings
import com.epodbreg.stih.ui.components.GameHistoryCard
import com.epodbreg.stih.ui.components.KlopGameCard
import com.epodbreg.stih.ui.theme.StihGreen
import com.epodbreg.stih.ui.theme.StihOrange
import com.epodbreg.stih.ui.theme.StihRed
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Saved sessions list. Port of SessionHistoryView.swift. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SessionHistoryScreen(
    sessionManager: SessionManager,
    settings: SettingsManager,
    onBack: () -> Unit,
    onResumeSession: (GameSession) -> Unit,
    /** New game with this table's players and group */
    onNewWithPlayers: (players: List<Player>, group: String?) -> Unit,
) {
    var selectedSession by remember { mutableStateOf<GameSession?>(null) }
    var sessionToDelete by remember { mutableStateOf<GameSession?>(null) }
    var showStats by remember { mutableStateOf(false) }
    var showPlayers by remember { mutableStateOf(false) }
    // Multi-select → put sessions into a group
    var isSelecting by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf(setOf<UUID>()) }
    var groupSheetSessions by remember { mutableStateOf<Set<UUID>?>(null) }

    // Player management sits on top of stats when opened from its picker
    if (showPlayers) {
        BackHandler { showPlayers = false }
        PlayerManagementScreen(sessionManager, settings, onBack = { showPlayers = false })
        return
    }
    if (showStats) {
        BackHandler { showStats = false }
        StatsScreen(
            sessionManager = sessionManager,
            settings = settings,
            onBack = { showStats = false },
            onManagePlayers = { showPlayers = true },
        )
        return
    }

    val detail = selectedSession
    if (detail != null) {
        SessionDetailScreen(
            session = detail,
            onBack = { selectedSession = null },
            onResume = { onResumeSession(detail) },
            onNewWithPlayers = { onNewWithPlayers(detail.players, detail.groupName) },
        )
        return
    }

    if (isSelecting) {
        BackHandler { isSelecting = false; selection = emptySet() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isSelecting) "Izberi seje" else "Zgodovina sej") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSelecting) { isSelecting = false; selection = emptySet() } else onBack()
                    }) {
                        Icon(
                            if (isSelecting) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (isSelecting) "Končano" else "Nazaj",
                        )
                    }
                },
                actions = {
                    val hasSessions = sessionManager.savedSessions.isNotEmpty()
                    if (isSelecting) return@TopAppBar
                    IconButton(onClick = { isSelecting = true }, enabled = hasSessions) {
                        Icon(Icons.Filled.Checklist, contentDescription = "Izberi seje")
                    }
                    IconButton(onClick = { showPlayers = true }, enabled = hasSessions) {
                        Icon(Icons.Filled.People, contentDescription = "Igralci")
                    }
                    IconButton(onClick = { showStats = true }, enabled = hasSessions) {
                        Icon(Icons.Filled.BarChart, contentDescription = "Tarok statistika")
                    }
                },
            )
        },
        bottomBar = {
            if (isSelecting) {
                Surface(tonalElevation = 3.dp) {
                    TextButton(
                        onClick = { groupSheetSessions = selection },
                        enabled = selection.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(8.dp),
                    ) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Dodaj v skupino (${selection.size})", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
    ) { padding ->
        if (sessionManager.savedSessions.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Filled.History, contentDescription = null,
                    tint = Color.Gray, modifier = Modifier.size(60.dp),
                )
                Text("Ni shranjenih sej", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
                Text(
                    "Ko zaključiš igro, se bo pojavila tukaj",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(sessionManager.savedSessions.sortedByDescending { it.startTime }, key = { it.id }) { session ->
                    val isSelected = session.id in selection
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (isSelecting) {
                                            selection = if (isSelected) selection - session.id else selection + session.id
                                        } else {
                                            selectedSession = session
                                        }
                                    },
                                    // Long-press → "Skupina…" for this one session
                                    onLongClick = { if (!isSelecting) groupSheetSessions = setOf(session.id) },
                                )
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                        ) {
                            if (isSelecting) {
                                Icon(
                                    if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    SessionManager.getSessionName(session),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        TarokStrings.rundaCountText(session.roundCount),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    session.groupName?.let { GroupBadge(it) }
                                }
                            }
                            if (!isSelecting) {
                                IconButton(onClick = { onResumeSession(session) }) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = "Nadaljuj", tint = StihGreen)
                                }
                                IconButton(onClick = { sessionToDelete = session }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Izbriši", tint = StihRed)
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    groupSheetSessions?.let { ids ->
        GroupAssignSheet(
            sessionManager = sessionManager,
            sessionIds = ids,
            onDone = {
                groupSheetSessions = null
                isSelecting = false
                selection = emptySet()
            },
        )
    }

    sessionToDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = { Text("Izbriši sejo?") },
            text = { Text("Seja ${SessionManager.getSessionName(session)} bo izbrisana iz zgodovine.") },
            confirmButton = {
                TextButton(onClick = {
                    sessionManager.deleteSession(session.id)
                    sessionToDelete = null
                }) { Text("Izbriši", color = StihRed) }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDelete = null }) { Text("Prekliči") }
            },
        )
    }
}

/** Session detail with final scores, history, resume/new/export. Port of SessionDetailView.swift. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    session: GameSession,
    onBack: () -> Unit,
    onResume: () -> Unit,
    onNewWithPlayers: () -> Unit,
) {
    val context = LocalContext.current

    fun exportCsv() {
        val csv = session.csvExport()
        val dateStamp = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(session.startTime))
        val playerSlug = session.players.take(2).joinToString("-") { it.name }
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportDir, "stih-$playerSlug-$dateStamp.csv")
        file.writeText(csv)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Izvozi sejo"))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Povzetek seje") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Nazaj")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 16.dp),
            ) {
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

                item {
                    Text(
                        "Zgodovina iger",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (session.games.isEmpty()) {
                    item {
                        Text(
                            "V tej seji ni bilo zabeleženih iger",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        )
                    }
                } else {
                    items(session.games.sortedByDescending { it.timestamp }) { game ->
                        if (game.contract == TarokContract.KLOP) {
                            KlopGameCard(game = game, players = session.players)
                        } else {
                            GameHistoryCard(game = game)
                        }
                    }
                }
            }

            // Bottom buttons
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(16.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onResume)
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Text("Nadaljuj igro", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(onClick = onNewWithPlayers)
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.Group, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Text("Nova igra", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .background(StihOrange.copy(alpha = 0.15f))
                            .clickable { exportCsv() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.Share, contentDescription = null, tint = StihOrange, modifier = Modifier.size(16.dp))
                            Text("Izvozi", color = StihOrange, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }
}
