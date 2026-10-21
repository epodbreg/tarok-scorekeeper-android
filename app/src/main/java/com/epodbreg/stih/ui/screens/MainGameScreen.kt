package com.epodbreg.stih.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.epodbreg.stih.data.SessionManager
import com.epodbreg.stih.logic.TarokScorer
import com.epodbreg.stih.model.GameEntry
import com.epodbreg.stih.model.GameSession
import com.epodbreg.stih.model.HandOutcome
import com.epodbreg.stih.model.Player
import com.epodbreg.stih.model.PointsBreakdown
import com.epodbreg.stih.model.TarokContract
import com.epodbreg.stih.ui.GameViewModel
import com.epodbreg.stih.ui.components.GameHistoryCard
import com.epodbreg.stih.ui.components.GameStepperSheet
import com.epodbreg.stih.ui.components.KlopGameCard
import com.epodbreg.stih.ui.components.PlayerScoreCard
import com.epodbreg.stih.ui.theme.StihGreen
import com.epodbreg.stih.ui.theme.StihOrange
import com.epodbreg.stih.ui.theme.StihPurple
import com.epodbreg.stih.ui.theme.StihRed
import kotlinx.coroutines.delay
import java.util.UUID

private sealed interface EditState {
    data object Idle : EditState
    data object Adding : EditState
    data class Editing(val game: GameEntry) : EditState
}

/** Main game screen. Port of MainGameView.swift. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainGameScreen(
    viewModel: GameViewModel,
    onSessionCompleted: (GameSession) -> Unit,
    onExitWithoutSaving: () -> Unit,
) {
    var editState: EditState by remember { mutableStateOf(EditState.Idle) }
    var expandedPlayerIds by remember { mutableStateOf(setOf<UUID>()) }
    var showBackWarning by remember { mutableStateOf(false) }
    var gameToDelete by remember { mutableStateOf<GameEntry?>(null) }
    var showDealerMenu by remember { mutableStateOf(false) }
    var penaltyPlayerId by remember { mutableStateOf<UUID?>(null) }
    var isHistoryExpanded by remember { mutableStateOf(false) }
    var obvezniKlopPreselected by remember { mutableStateOf<TarokContract?>(null) }
    var obvezniKlopPlayer by remember { mutableStateOf<String?>(null) }

    // Consume the trigger immediately so every trigger is a fresh transition —
    // a missed popup can never permanently swallow future triggers.
    LaunchedEffect(viewModel.obvezniKlopTrigger) {
        viewModel.obvezniKlopTrigger?.let { name ->
            obvezniKlopPlayer = name
            viewModel.clearObvezniKlopTrigger()
        }
    }

    // Undo snackbar state for deleted games
    var recentlyDeleted by remember { mutableStateOf<GameEntry?>(null) }
    var deletedRadlcSnapshot by remember { mutableStateOf<Map<String, List<Boolean>>?>(null) }
    var undoTick by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) { viewModel.restoreSessionIfNeeded() }

    LaunchedEffect(recentlyDeleted, undoTick) {
        if (recentlyDeleted != null) {
            delay(5000)
            recentlyDeleted = null
            deletedRadlcSnapshot = null
        }
    }

    val contractsWithPartner: Set<TarokContract> =
        if (viewModel.players.size == 3) emptySet()
        else setOf(
            TarokContract.TRI, TarokContract.DVE, TarokContract.ENA,
            TarokContract.VALAT, TarokContract.BARVNI_VALAT,
        )

    BackHandler { showBackWarning = true }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { showBackWarning = true }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Nazaj")
                    }
                },
                title = {
                    Column {
                        Text("Tarok igra", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            SessionManager.formatSessionDate(viewModel.sessionStartTime),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header: dealer pill + end session
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(StihOrange)
                            .clickable { showDealerMenu = true }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Text("Deli", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(viewModel.getCurrentDealerName(), color = Color.White, fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(StihRed)
                            .clickable {
                                viewModel.endSession()?.let { onSessionCompleted(it) }
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text("Zaključi sejo", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                HorizontalDivider()

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Player cards, sorted by standing
                    viewModel.standings.forEach { (player, rank) ->
                        PlayerScoreCard(
                            player = player,
                            standing = rank,
                            isExpanded = player.id in expandedPlayerIds,
                            isDealer = player.name == viewModel.getCurrentDealerName(),
                            isSlepar = player.name in viewModel.slepaci,
                            liveNetScore = viewModel.liveNetScore(player),
                            showNetScore = viewModel.settings.currentRuleSet.generalRules.radlcPenaltyEnabled &&
                                player.hasUnusedRadlci,
                            gameHistory = viewModel.gameHistory,
                            onExpandToggle = {
                                expandedPlayerIds = if (player.id in expandedPlayerIds) {
                                    expandedPlayerIds - player.id
                                } else {
                                    expandedPlayerIds + player.id
                                }
                            },
                            onPenalty = { penaltyPlayerId = player.id },
                            // Auto-radlci lock: pips are read-only status unless disabled
                            onRadlcTap = if (viewModel.settings.autoRadlci) null
                            else { index -> viewModel.toggleRadlc(player.id, index) },
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }

                    // Add game button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable { editState = EditState.Adding }
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Text("Dodaj igro", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                        }
                    }

                    // Game history section
                    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isHistoryExpanded = !isHistoryExpanded },
                        ) {
                            Text("Zgodovina iger", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            if (viewModel.roundCount > 0) {
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                ) {
                                    Text(
                                        "${viewModel.roundCount}",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            Icon(
                                if (isHistoryExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        AnimatedVisibility(visible = isHistoryExpanded) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (viewModel.gameHistory.isEmpty()) {
                                    Text(
                                        "Ni zabeleženih iger.\nDodajte prvo igro za začetek",
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 24.dp),
                                    )
                                } else {
                                    viewModel.gameHistory.sortedByDescending { it.timestamp }.forEach { game ->
                                        if (game.contract == TarokContract.KLOP) {
                                            KlopGameCard(
                                                game = game,
                                                players = viewModel.players,
                                                onTap = { editState = EditState.Editing(game) },
                                                onLongPress = { gameToDelete = game },
                                            )
                                        } else {
                                            GameHistoryCard(
                                                game = game,
                                                onTap = { editState = EditState.Editing(game) },
                                                onLongPress = { gameToDelete = game },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(64.dp))
                }
            }

            // Undo snackbar
            AnimatedVisibility(
                visible = recentlyDeleted != null,
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                recentlyDeleted?.let { deleted ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.inverseSurface,
                        shadowElevation = 6.dp,
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                        ) {
                            Text(
                                "Igra izbrisana: ${deleted.contract.displayName}",
                                color = MaterialTheme.colorScheme.inverseOnSurface,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.width(16.dp))
                            TextButton(onClick = {
                                deletedRadlcSnapshot?.let { snapshot ->
                                    viewModel.setPlayersDirect(
                                        viewModel.players.map { p ->
                                            snapshot[p.name]?.let { p.copy(radlci = it) } ?: p
                                        }
                                    )
                                }
                                viewModel.addRawGameEntry(deleted)
                                recentlyDeleted = null
                                deletedRadlcSnapshot = null
                            }) {
                                Text("Razveljavi", color = StihOrange, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Obvezni klop overlay — non-dismissible until a choice is made
            obvezniKlopPlayer?.let { name ->
                ObvezniKlopOverlay(
                    playerName = name,
                    onKlop = {
                        obvezniKlopPlayer = null
                        obvezniKlopPreselected = TarokContract.KLOP
                        editState = EditState.Adding
                    },
                    onSoloBrez = {
                        obvezniKlopPlayer = null
                        obvezniKlopPreselected = TarokContract.SOLO_BREZ
                        editState = EditState.Adding
                    },
                )
            }
        }
    }

    // Add / edit steppers
    when (val state = editState) {
        is EditState.Adding -> GameStepperSheet(
            players = viewModel.players,
            contractsWithPartner = contractsWithPartner,
            settings = viewModel.settings,
            initialContract = obvezniKlopPreselected,
            onSave = { entry ->
                val result = TarokScorer.processGame(
                    entry, viewModel.players, viewModel.settings.currentRuleSet.generalRules,
                )
                viewModel.addProcessedGameEntry(result.entry, result.players)
                editState = EditState.Idle
                obvezniKlopPreselected = null
            },
            onCancel = {
                editState = EditState.Idle
                obvezniKlopPreselected = null
            },
        )
        is EditState.Editing -> GameStepperSheet(
            players = viewModel.players,
            contractsWithPartner = contractsWithPartner,
            settings = viewModel.settings,
            initialGame = state.game,
            onSave = { entry ->
                viewModel.updateGameEntry(entry.copy(id = state.game.id))
                editState = EditState.Idle
            },
            onDelete = {
                viewModel.removeGameEntry(state.game.id)
                editState = EditState.Idle
            },
            onCancel = { editState = EditState.Idle },
        )
        EditState.Idle -> {}
    }

    // Dealer menu
    if (showDealerMenu) {
        DealerMenuSheet(
            players = viewModel.players,
            currentDealerName = viewModel.getCurrentDealerName(),
            onAdvance = { viewModel.advanceDealer(); showDealerMenu = false },
            onReverse = { viewModel.reverseDealer(); showDealerMenu = false },
            onSelect = { idx -> viewModel.setDealer(idx); showDealerMenu = false },
            onDismiss = { showDealerMenu = false },
        )
    }

    // Penalty / bonus dialog
    penaltyPlayerId?.let { playerId ->
        val player = viewModel.players.firstOrNull { it.id == playerId }
        if (player != null) {
            val rules = viewModel.settings.currentRuleSet
            PenaltyDialog(
                player = player,
                pagatPoints = kotlin.math.abs(rules.bonusScores.pagatUltimoBase),
                mondPenalty = kotlin.math.abs(rules.bonusScores.mondKazBase),
                renonsPenalty = kotlin.math.abs(rules.generalRules.renonsPenalty),
                onAction = { contract, points, outcome ->
                    val entry = GameEntry(
                        contract = contract,
                        declarerName = player.name,
                        partnerName = null,
                        outcome = outcome,
                        pointsBreakdown = PointsBreakdown(gamePoints = points, kontraLevel = 0),
                        bonuses = emptyList(),
                        totalPoints = points,
                    )
                    // Through the scorer: pagat quick-award gets radlc doubling
                    // (without consuming); mond/renons pass through untouched.
                    val result = TarokScorer.processGame(
                        entry, viewModel.players, viewModel.settings.currentRuleSet.generalRules,
                    )
                    viewModel.addProcessedGameEntry(result.entry, result.players)
                    penaltyPlayerId = null
                },
                onDismiss = { penaltyPlayerId = null },
            )
        } else {
            penaltyPlayerId = null
        }
    }

    // Delete confirmation
    gameToDelete?.let { game ->
        AlertDialog(
            onDismissRequest = { gameToDelete = null },
            title = { Text("Izbriši igro?") },
            text = { Text("Ali želite izbrisati ${game.contract.displayName} igro?") },
            confirmButton = {
                TextButton(onClick = {
                    deletedRadlcSnapshot = viewModel.players.associate { it.name to it.radlci }
                    viewModel.removeGameEntry(game.id)
                    recentlyDeleted = game
                    undoTick++
                    gameToDelete = null
                }) { Text("Izbriši", color = StihRed) }
            },
            dismissButton = {
                TextButton(onClick = { gameToDelete = null }) { Text("Prekliči") }
            },
        )
    }

    // Back warning
    if (showBackWarning) {
        AlertDialog(
            onDismissRequest = { showBackWarning = false },
            title = { Text("Zapusti igro?") },
            text = { Text("Ste prepričani, da želite zapustiti igro, brez da shranite?") },
            confirmButton = {
                TextButton(onClick = {
                    showBackWarning = false
                    onExitWithoutSaving()
                }) { Text("Ja", color = StihRed) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBackWarning = false
                    viewModel.endSession()?.let { onSessionCompleted(it) }
                }) { Text("Ne, shrani") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DealerMenuSheet(
    players: List<Player>,
    currentDealerName: String,
    onAdvance: () -> Unit,
    onReverse: () -> Unit,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                "Delilec_ka",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            HorizontalDivider()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onAdvance)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Naprej", fontWeight = FontWeight.Medium)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onReverse)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Nazaj", fontWeight = FontWeight.Medium)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            players.forEachIndexed { idx, player ->
                val isCurrent = player.name == currentDealerName
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(idx) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(
                        player.name,
                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isCurrent) StihOrange else MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.weight(1f))
                    if (isCurrent) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = StihOrange, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PenaltyDialog(
    player: Player,
    pagatPoints: Int,
    mondPenalty: Int,
    renonsPenalty: Int,
    onAction: (TarokContract, Int, HandOutcome) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dodaj penal / bonus") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(player.name, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                PenaltyOption("Pagat ultimo +$pagatPoints", StihGreen) {
                    onAction(TarokContract.PAGAT_BONUS, pagatPoints, HandOutcome.ZMAGAL)
                }
                PenaltyOption("Izgubljen pagat −$pagatPoints", StihRed) {
                    onAction(TarokContract.PAGAT_BONUS, -pagatPoints, HandOutcome.IZGUBIL)
                }
                PenaltyOption("Mond −$mondPenalty", StihRed) {
                    onAction(TarokContract.PENALTY_MOND, -mondPenalty, HandOutcome.IZGUBIL)
                }
                PenaltyOption("Renons −$renonsPenalty", StihRed) {
                    onAction(TarokContract.PENALTY_RENONS, -renonsPenalty, HandOutcome.IZGUBIL)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Prekliči") }
        },
    )
}

@Composable
private fun PenaltyOption(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text,
        color = color,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
    )
}

/** Obvezni klop overlay. Non-dismissible — user must pick klop or solo brez. */
@Composable
private fun ObvezniKlopOverlay(
    playerName: String,
    onKlop: () -> Unit,
    onSoloBrez: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Text("🃏", fontSize = 64.sp)
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Obvezni klop!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "$playerName je na okrogli številki",
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "Izberi igro za naslednji krog",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    OverlayChoiceButton(
                        text = "Klop",
                        icon = Icons.Filled.BugReport,
                        background = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        modifier = Modifier.weight(1f),
                        onClick = onKlop,
                    )
                    OverlayChoiceButton(
                        text = "Solo Brez",
                        icon = Icons.Filled.Layers,
                        background = StihPurple.copy(alpha = 0.85f),
                        modifier = Modifier.weight(1f),
                        onClick = onSoloBrez,
                    )
                }
            }
        }
    }
}

@Composable
private fun OverlayChoiceButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    background: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}
