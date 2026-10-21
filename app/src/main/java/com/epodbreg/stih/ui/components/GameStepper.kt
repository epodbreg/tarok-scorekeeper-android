package com.epodbreg.stih.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.epodbreg.stih.data.SettingsManager
import com.epodbreg.stih.model.BonusType
import com.epodbreg.stih.model.GameEntry
import com.epodbreg.stih.model.HandBonus
import com.epodbreg.stih.model.HandOutcome
import com.epodbreg.stih.model.Player
import com.epodbreg.stih.model.PointsBreakdown
import com.epodbreg.stih.model.TarokContract
import com.epodbreg.stih.model.TarokStrings
import com.epodbreg.stih.ui.theme.StihGreen
import com.epodbreg.stih.ui.theme.StihOrange
import com.epodbreg.stih.ui.theme.StihPurple
import com.epodbreg.stih.ui.theme.StihRed
import com.epodbreg.stih.ui.theme.StihYellow

enum class AddGameStep { PLAYER, CONTRACT, PARTNER, OUTCOME, KLOP, UNDERLYING_GAME }

enum class KlopResultState { NONE, PRAZEN, POLN;
    fun next(): KlopResultState = when (this) {
        NONE -> PRAZEN; PRAZEN -> POLN; POLN -> NONE
    }
}

enum class BonusCycleState { NONE, WON, WON_ANNOUNCED, LOST, LOST_ANNOUNCED }

/**
 * Game entry wizard as a modal bottom sheet. Port of GameStepper.swift.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameStepperSheet(
    players: List<Player>,
    contractsWithPartner: Set<TarokContract>,
    settings: SettingsManager,
    initialGame: GameEntry? = null,
    initialContract: TarokContract? = null,
    onSave: (GameEntry) -> Unit,
    onDelete: (() -> Unit)? = null,
    onCancel: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var step by remember {
        mutableStateOf(
            when {
                initialGame != null -> AddGameStep.CONTRACT
                initialContract == TarokContract.KLOP -> AddGameStep.KLOP
                initialContract != null -> AddGameStep.PLAYER
                else -> AddGameStep.PLAYER
            }
        )
    }
    var selectedPlayer by remember {
        mutableStateOf(initialGame?.let { g -> players.indexOfFirst { it.name == g.declarerName }.takeIf { it >= 0 } })
    }
    var selectedContract by remember { mutableStateOf(initialGame?.contract ?: initialContract) }
    var selectedPartner by remember {
        mutableStateOf(
            initialGame?.partnerName
                ?.takeUnless { it == TarokStrings.ZARUFAL }
                ?.let { name -> players.indexOfFirst { it.name == name }.takeIf { it >= 0 } }
        )
    }
    var outcome by remember { mutableStateOf(initialGame?.outcome ?: HandOutcome.ZMAGAL) }
    var points by remember {
        mutableStateOf(initialGame?.pointsBreakdown?.difference?.toString() ?: "")
    }
    val klopPoints = remember {
        mutableStateMapOf<String, String>().apply {
            initialGame?.klopScores?.forEach { (k, v) ->
                if (v != 70 && v != -70) put(k, "$v")
            }
        }
    }
    val klopResults = remember {
        mutableStateMapOf<String, KlopResultState>().apply {
            initialGame?.klopScores?.forEach { (k, v) ->
                put(k, when (v) { 70 -> KlopResultState.PRAZEN; -70 -> KlopResultState.POLN; else -> KlopResultState.NONE })
            }
        }
    }
    val bonusStates = remember {
        mutableStateMapOf<BonusType, BonusCycleState>().apply {
            initialGame?.bonuses?.forEach { bonus ->
                put(
                    bonus.type,
                    if (bonus.won) {
                        if (bonus.announced) BonusCycleState.WON_ANNOUNCED else BonusCycleState.WON
                    } else {
                        if (bonus.announced) BonusCycleState.LOST_ANNOUNCED else BonusCycleState.LOST
                    }
                )
            }
        }
    }
    val bonusKontras = remember {
        mutableStateMapOf<BonusType, Int>().apply {
            initialGame?.bonuses?.filter { it.kontraLevel > 0 }?.forEach { put(it.type, it.kontraLevel) }
        }
    }
    var gameKontra by remember { mutableStateOf(initialGame?.pointsBreakdown?.kontraLevel ?: 0) }
    var kontraCaller by remember { mutableStateOf(initialGame?.kontraCallerName) }

    /** Opponents of the declarer side — the only players who can call kontra. */
    fun kontraCandidates(): List<String> {
        val declarer = selectedPlayer?.let { players[it].name }
        val partner = selectedPartner?.let { players[it].name }
        return players.map { it.name }.filter { it != declarer && it != partner }
    }

    /** The caller only counts while kontra is active and they're still an opponent (partner may change). */
    fun resolvedKontraCaller(): String? =
        kontraCaller?.takeIf { gameKontra > 0 && it in kontraCandidates() }
    val expandedBonuses = remember { mutableStateMapOf<BonusType, Boolean>() }
    var selectedUnderlyingGame by remember { mutableStateOf(initialGame?.underlyingGame) }
    var isValatAnnounced by remember { mutableStateOf(initialGame?.isValatAnnounced ?: false) }
    var isValatSolo by remember {
        mutableStateOf(
            initialGame != null && initialGame.contract == TarokContract.VALAT &&
                initialGame.partnerName == null && players.size > 3
        )
    }

    fun currentBonuses(): List<HandBonus> = bonusStates.mapNotNull { (bonus, state) ->
        when (state) {
            BonusCycleState.WON -> HandBonus(bonus, announced = false, won = true)
            BonusCycleState.WON_ANNOUNCED -> HandBonus(bonus, announced = true, won = true, kontraLevel = bonusKontras[bonus] ?: 0)
            BonusCycleState.LOST -> HandBonus(bonus, announced = false, won = false)
            BonusCycleState.LOST_ANNOUNCED -> HandBonus(bonus, announced = true, won = false, kontraLevel = bonusKontras[bonus] ?: 0)
            BonusCycleState.NONE -> null
        }
    }

    fun contractIsValat(c: TarokContract) = c.isValatType
    fun contractHasFixed(c: TarokContract) = c.hasFixedScoring

    val canAdvance = when (step) {
        AddGameStep.PLAYER -> selectedPlayer != null
        AddGameStep.CONTRACT -> selectedContract != null
        AddGameStep.UNDERLYING_GAME -> selectedUnderlyingGame != null
        AddGameStep.PARTNER -> true
        AddGameStep.OUTCOME -> {
            val c = selectedContract
            (c != null && (contractIsValat(c) || contractHasFixed(c))) || points.isNotEmpty()
        }
        AddGameStep.KLOP -> true
    }

    fun previousStep(): AddGameStep = when (step) {
        AddGameStep.PLAYER -> AddGameStep.PLAYER
        AddGameStep.CONTRACT -> AddGameStep.PLAYER
        AddGameStep.UNDERLYING_GAME -> AddGameStep.CONTRACT
        AddGameStep.PARTNER -> {
            val c = selectedContract
            if (c != null && settings.contractIsValatWithGamePoints(c)) AddGameStep.UNDERLYING_GAME
            else AddGameStep.CONTRACT
        }
        AddGameStep.OUTCOME -> {
            val c = selectedContract
            when {
                c != null && settings.contractIsValatWithGamePoints(c) -> {
                    val ug = selectedUnderlyingGame
                    if (ug != null && ug in contractsWithPartner) AddGameStep.PARTNER
                    else AddGameStep.UNDERLYING_GAME
                }
                c != null && c in contractsWithPartner -> AddGameStep.PARTNER
                else -> AddGameStep.CONTRACT
            }
        }
        AddGameStep.KLOP -> if (selectedPlayer == null) AddGameStep.PLAYER else AddGameStep.CONTRACT
    }

    fun stepAfterContract(c: TarokContract): AddGameStep = when {
        c == TarokContract.KLOP -> AddGameStep.KLOP
        c == TarokContract.VALAT && settings.currentRuleSet.generalRules.valatWithGamePoints -> {
            selectedPartner = null
            AddGameStep.UNDERLYING_GAME
        }
        c in contractsWithPartner || (c == TarokContract.VALAT && players.size > 3) -> AddGameStep.PARTNER
        else -> {
            selectedPartner = null
            AddGameStep.OUTCOME
        }
    }

    fun finishRegularContract() {
        val pIdx = selectedPlayer ?: return
        val contract = selectedContract ?: return

        val partner: String? = if (contract in contractsWithPartner) {
            selectedPartner?.let { players[it].name } ?: TarokStrings.ZARUFAL
        } else null

        val selectedBonuses = currentBonuses()
        val razlika = points.toIntOrNull() ?: 0

        // Game score: apply win/loss to base + razlika only
        var gameScore = settings.getContractBaseScore(contract, players.size) + razlika
        if (outcome.isLoss) gameScore = -kotlin.math.abs(gameScore)
        // Kontra multiplies only the game score, not the bonuses
        gameScore *= (1 shl gameKontra)

        // Bonuses are independent of the game outcome
        val bonusTotal = selectedBonuses.sumOf { settings.getBonusPoints(it) }
        val totalPoints = gameScore + bonusTotal

        onSave(
            GameEntry(
                contract = contract,
                declarerName = players[pIdx].name,
                partnerName = partner,
                outcome = outcome,
                pointsBreakdown = PointsBreakdown(gamePoints = totalPoints, difference = razlika, kontraLevel = gameKontra),
                bonuses = selectedBonuses,
                totalPoints = totalPoints,
                rawTotalPoints = totalPoints,
                kontraCallerName = resolvedKontraCaller(),
            )
        )
    }

    fun finishBarvniValat() {
        val pIdx = selectedPlayer ?: return
        val contract = selectedContract ?: return
        val partner: String? = when {
            isValatSolo -> null
            selectedPartner != null -> players[selectedPartner!!].name
            else -> TarokStrings.ZARUFAL
        }
        var totalPoints = settings.getBarvniValatScore()
        if (outcome == HandOutcome.IZGUBIL) totalPoints = -totalPoints
        totalPoints *= (1 shl gameKontra)

        onSave(
            GameEntry(
                contract = contract,
                declarerName = players[pIdx].name,
                partnerName = partner,
                outcome = outcome,
                pointsBreakdown = PointsBreakdown(gamePoints = totalPoints, kontraLevel = gameKontra),
                bonuses = emptyList(),
                totalPoints = totalPoints,
                kontraCallerName = resolvedKontraCaller(),
            )
        )
    }

    fun finishValat() {
        val pIdx = selectedPlayer ?: return
        val contract = selectedContract ?: return
        val partner: String? = when {
            isValatSolo -> null
            selectedPartner != null -> players[selectedPartner!!].name
            else -> TarokStrings.ZARUFAL
        }
        var totalPoints = settings.getValatBaseScore(isValatAnnounced)
        if (settings.currentRuleSet.generalRules.valatWithGamePoints) {
            selectedUnderlyingGame?.let { totalPoints += settings.getContractBaseScore(it, players.size) }
        }
        if (outcome == HandOutcome.IZGUBIL) totalPoints = -totalPoints
        totalPoints *= (1 shl gameKontra)

        onSave(
            GameEntry(
                contract = contract,
                declarerName = players[pIdx].name,
                partnerName = partner,
                outcome = outcome,
                pointsBreakdown = PointsBreakdown(gamePoints = totalPoints, kontraLevel = gameKontra),
                bonuses = emptyList(),
                totalPoints = totalPoints,
                underlyingGame = if (settings.currentRuleSet.generalRules.valatWithGamePoints) selectedUnderlyingGame else null,
                isValatAnnounced = isValatAnnounced,
                kontraCallerName = resolvedKontraCaller(),
            )
        )
    }

    fun finishKlop() {
        val scores = mutableMapOf<String, Int>()
        players.forEach { player ->
            when (klopResults[player.name] ?: KlopResultState.NONE) {
                KlopResultState.PRAZEN -> scores[player.name] = 70
                KlopResultState.POLN -> scores[player.name] = -70
                KlopResultState.NONE -> {
                    val input = klopPoints[player.name]?.toIntOrNull() ?: 0
                    scores[player.name] = -kotlin.math.abs(input)
                }
            }
        }
        onSave(
            GameEntry(
                contract = TarokContract.KLOP,
                declarerName = "-",
                partnerName = null,
                outcome = HandOutcome.IZGUBIL,
                pointsBreakdown = PointsBreakdown(gamePoints = 0, kontraLevel = 0),
                bonuses = emptyList(),
                totalPoints = 0,
                klopScores = scores,
            )
        )
    }

    fun advance() {
        when (step) {
            AddGameStep.PLAYER -> step = AddGameStep.CONTRACT
            AddGameStep.CONTRACT -> selectedContract?.let { step = stepAfterContract(it) }
            AddGameStep.UNDERLYING_GAME -> {
                val ug = selectedUnderlyingGame
                step = if (ug != null && ug in contractsWithPartner) AddGameStep.PARTNER else AddGameStep.OUTCOME
            }
            AddGameStep.PARTNER -> {
                step = AddGameStep.OUTCOME
                expandedBonuses.clear()
            }
            AddGameStep.OUTCOME -> when (selectedContract) {
                TarokContract.VALAT -> finishValat()
                TarokContract.BARVNI_VALAT -> finishBarvniValat()
                else -> finishRegularContract()
            }
            AddGameStep.KLOP -> finishKlop()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
        ) {
            // Header: breadcrumb chips
            StepperHeader(
                step = step,
                players = players,
                selectedPlayer = selectedPlayer,
                selectedContract = selectedContract,
                selectedPartner = selectedPartner,
                isValatSolo = isValatSolo,
                contractsWithPartner = contractsWithPartner,
                isEditing = initialGame != null,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            when (step) {
                AddGameStep.PLAYER -> PlayerStep(
                    players = players,
                    selectedPlayer = selectedPlayer,
                    onSelect = { selectedPlayer = it; step = AddGameStep.CONTRACT },
                    onKlop = { selectedContract = TarokContract.KLOP; step = AddGameStep.KLOP },
                )
                AddGameStep.CONTRACT -> ContractStep(
                    contracts = availableContracts(players.size),
                    selected = selectedContract,
                    onSelect = { c ->
                        selectedContract = c
                        step = stepAfterContract(c)
                    },
                )
                AddGameStep.UNDERLYING_GAME -> UnderlyingGameStep(
                    contracts = availableUnderlyingGames(players.size),
                    selected = selectedUnderlyingGame,
                    baseScore = { settings.getContractBaseScore(it, players.size) },
                    onSelect = { c ->
                        selectedUnderlyingGame = c
                        step = if (c in contractsWithPartner) AddGameStep.PARTNER else AddGameStep.OUTCOME
                    },
                )
                AddGameStep.PARTNER -> PartnerStep(
                    players = players,
                    declarerIndex = selectedPlayer,
                    selectedPartner = selectedPartner,
                    isValatSolo = isValatSolo,
                    showSolo = selectedContract == TarokContract.VALAT || selectedContract == TarokContract.BARVNI_VALAT,
                    onSelect = { idx ->
                        selectedPartner = idx
                        isValatSolo = false
                        step = AddGameStep.OUTCOME
                    },
                    onZarufal = {
                        selectedPartner = null
                        isValatSolo = false
                        step = AddGameStep.OUTCOME
                    },
                    onSolo = {
                        selectedPartner = null
                        isValatSolo = true
                        step = AddGameStep.OUTCOME
                    },
                )
                AddGameStep.OUTCOME -> OutcomeStep(
                    settings = settings,
                    contract = selectedContract,
                    playerCount = players.size,
                    outcome = outcome,
                    onOutcome = { outcome = it },
                    points = points,
                    onPoints = { points = it },
                    gameKontra = gameKontra,
                    onGameKontra = { gameKontra = (gameKontra + 1) % 5 },
                    kontraCandidates = kontraCandidates(),
                    kontraCaller = kontraCaller,
                    onKontraCaller = { name -> kontraCaller = if (kontraCaller == name) null else name },
                    bonusStates = bonusStates,
                    bonusKontras = bonusKontras,
                    expandedBonuses = expandedBonuses,
                    isValatAnnounced = isValatAnnounced,
                    onValatAnnounced = { isValatAnnounced = it },
                    underlyingGame = selectedUnderlyingGame,
                    currentBonuses = ::currentBonuses,
                )
                AddGameStep.KLOP -> KlopStep(
                    players = players,
                    klopPoints = klopPoints,
                    klopResults = klopResults,
                )
            }

            if (initialGame != null && step == AddGameStep.KLOP && onDelete != null) {
                TextButton(
                    onClick = { onDelete(); onCancel() },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text("Izbriši roko", color = StihRed)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Bottom buttons: cancel / back / next
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CapsuleButton(
                    text = "Prekliči",
                    background = MaterialTheme.colorScheme.surfaceVariant,
                    foreground = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    onClick = onCancel,
                )
                if (step != AddGameStep.PLAYER) {
                    CapsuleButton(
                        text = "Nazaj",
                        background = StihOrange.copy(alpha = 0.2f),
                        foreground = StihOrange,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (step == AddGameStep.OUTCOME) expandedBonuses.clear()
                            step = previousStep()
                        },
                    )
                }
                CapsuleButton(
                    text = if (step == AddGameStep.KLOP || step == AddGameStep.OUTCOME) {
                        if (step == AddGameStep.KLOP) (if (initialGame == null) "Dodaj" else "Shrani") else "Naprej"
                    } else "Naprej",
                    background = if (canAdvance) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    foreground = Color.White,
                    enabled = canAdvance,
                    modifier = Modifier.weight(1f),
                    onClick = { advance() },
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ---- Sub-views --------------------------------------------------------------

@Composable
private fun StepperHeader(
    step: AddGameStep,
    players: List<Player>,
    selectedPlayer: Int?,
    selectedContract: TarokContract?,
    selectedPartner: Int?,
    isValatSolo: Boolean,
    contractsWithPartner: Set<TarokContract>,
    isEditing: Boolean,
) {
    val isKlop = selectedContract == TarokContract.KLOP || step == AddGameStep.KLOP
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            isKlop -> StepperChip(TarokContract.KLOP.icon, StihOrange, "Klop")
            selectedPlayer == null -> Text(
                if (isEditing) "Uredi igro" else "Dodaj igro",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            else -> {
                StepperChip(Icons.Filled.Person, MaterialTheme.colorScheme.primary, players[selectedPlayer].name)
                val showContract = selectedContract != null && step != AddGameStep.PLAYER && step != AddGameStep.CONTRACT
                if (showContract && selectedContract != null) {
                    Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    StepperChip(selectedContract.icon, MaterialTheme.colorScheme.onSurfaceVariant, selectedContract.displayName)
                }
                if (step == AddGameStep.OUTCOME && selectedContract != null && selectedContract in contractsWithPartner) {
                    Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    when {
                        selectedPartner != null -> StepperChip(Icons.Filled.LocalShipping, com.epodbreg.stih.ui.theme.StihBrown, players[selectedPartner].name)
                        isValatSolo -> StepperChip(Icons.Filled.Person, StihPurple, "Solo")
                        else -> StepperChip(Icons.Filled.WorkspacePremium, StihRed, TarokStrings.ZARUFAL)
                    }
                }
            }
        }
    }
}

@Composable
private fun StepperChip(icon: androidx.compose.ui.graphics.vector.ImageVector, iconColor: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(14.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun CapsuleButton(
    text: String,
    background: Color,
    foreground: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(background)
            .let { if (enabled) it.clickable(onClick = onClick) else it }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = foreground, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun PlayerStep(
    players: List<Player>,
    selectedPlayer: Int?,
    onSelect: (Int) -> Unit,
    onKlop: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Izberi igralca_ko",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.height(((players.size + 1) / 2 * 60).dp),
            userScrollEnabled = false,
        ) {
            items(players.indices.toList()) { idx ->
                val selected = selectedPlayer == idx
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onSelect(idx) }
                        .padding(vertical = 12.dp, horizontal = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        players[idx].name,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
        }
        // Klop shortcut — full width
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(StihOrange)
                .clickable(onClick = onKlop)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(TarokContract.KLOP.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Text("Klop", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun availableContracts(playerCount: Int): List<TarokContract> = buildList {
    add(TarokContract.TRI); add(TarokContract.DVE); add(TarokContract.ENA)
    if (playerCount != 3) {
        add(TarokContract.SOLO_TRI); add(TarokContract.SOLO_DVE); add(TarokContract.SOLO_ENA)
    }
    add(TarokContract.BERAC); add(TarokContract.SOLO_BREZ); add(TarokContract.ODPRTI_BERAC)
    add(TarokContract.BARVNI_VALAT); add(TarokContract.VALAT); add(TarokContract.KLOP)
}

private fun availableUnderlyingGames(playerCount: Int): List<TarokContract> = buildList {
    add(TarokContract.TRI); add(TarokContract.DVE); add(TarokContract.ENA)
    if (playerCount != 3) {
        add(TarokContract.SOLO_TRI); add(TarokContract.SOLO_DVE); add(TarokContract.SOLO_ENA)
    }
    add(TarokContract.BERAC); add(TarokContract.SOLO_BREZ); add(TarokContract.ODPRTI_BERAC)
}

@Composable
private fun ContractStep(
    contracts: List<TarokContract>,
    selected: TarokContract?,
    onSelect: (TarokContract) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            "Izberi kontrakt",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.height(((contracts.size + 2) / 3 * 92).dp),
            userScrollEnabled = false,
        ) {
            items(contracts) { contract ->
                val isSelected = selected == contract
                Column(
                    modifier = Modifier
                        .height(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .clickable { onSelect(contract) }
                        .padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        contract.icon,
                        contentDescription = null,
                        tint = if (isSelected) Color.White else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        contract.displayName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun UnderlyingGameStep(
    contracts: List<TarokContract>,
    selected: TarokContract?,
    baseScore: (TarokContract) -> Int,
    onSelect: (TarokContract) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Osnovna igra za valat",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Text(
            "Izberi igro, ki se je igrala z valatom",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.height(((contracts.size + 2) / 3 * 88).dp),
            userScrollEnabled = false,
        ) {
            items(contracts) { contract ->
                val isSelected = selected == contract
                Column(
                    modifier = Modifier
                        .height(75.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(10.dp),
                        )
                        .clickable { onSelect(contract) }
                        .padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(contract.icon, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(
                        contract.displayName,
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center, maxLines = 2,
                    )
                    Text(
                        "+${baseScore(contract)}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PartnerStep(
    players: List<Player>,
    declarerIndex: Int?,
    selectedPartner: Int?,
    isValatSolo: Boolean,
    showSolo: Boolean,
    onSelect: (Int) -> Unit,
    onZarufal: () -> Unit,
    onSolo: () -> Unit,
) {
    val available = players.indices.filter { it != declarerIndex }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Izberi soigralca_ko",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.height((((available.size + 1 + (if (showSolo) 1 else 0)) + 1) / 2 * 60).dp),
            userScrollEnabled = false,
        ) {
            items(available) { idx ->
                val isSelected = selectedPartner == idx
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onSelect(idx) }
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        players[idx].name,
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
            item {
                val active = selectedPartner == null && !isValatSolo
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (active) StihRed.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(onClick = onZarufal)
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = StihRed, modifier = Modifier.size(16.dp))
                        Text(TarokStrings.ZARUFAL, color = StihRed, fontWeight = FontWeight.SemiBold, maxLines = 1, fontSize = 14.sp)
                    }
                }
            }
            if (showSolo) {
                item {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isValatSolo) StihPurple.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(onClick = onSolo)
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.Person, contentDescription = null, tint = StihPurple, modifier = Modifier.size(16.dp))
                            Text("Solo", color = StihPurple, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OutcomeStep(
    settings: SettingsManager,
    contract: TarokContract?,
    playerCount: Int,
    outcome: HandOutcome,
    onOutcome: (HandOutcome) -> Unit,
    points: String,
    onPoints: (String) -> Unit,
    gameKontra: Int,
    onGameKontra: () -> Unit,
    kontraCandidates: List<String>,
    kontraCaller: String?,
    onKontraCaller: (String) -> Unit,
    bonusStates: MutableMap<BonusType, BonusCycleState>,
    bonusKontras: MutableMap<BonusType, Int>,
    expandedBonuses: MutableMap<BonusType, Boolean>,
    isValatAnnounced: Boolean,
    onValatAnnounced: (Boolean) -> Unit,
    underlyingGame: TarokContract?,
    currentBonuses: () -> List<HandBonus>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Rezultat",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        // Win/loss + kontra pill row
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(HandOutcome.ZMAGAL, HandOutcome.IZGUBIL).forEach { o ->
                val selected = outcome == o
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .background(
                            if (selected) (if (o == HandOutcome.ZMAGAL) StihGreen else StihRed)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { onOutcome(o) }
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(
                            if (o == HandOutcome.ZMAGAL) Icons.Filled.ThumbUp else Icons.Filled.ThumbDown,
                            contentDescription = null,
                            tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            if (o == HandOutcome.ZMAGAL) "Zmagal_a" else "Izgubil_a",
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            maxLines = 1,
                        )
                    }
                }
            }

            // Game kontra pill
            val style = KontraStyle.forLevel(gameKontra)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(style?.color ?: MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onGameKontra)
                    .padding(vertical = 10.dp, horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(
                        style?.icon ?: Icons.Filled.Bolt,
                        contentDescription = null,
                        tint = if (style != null) Color.White else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        style?.let { "${it.name} ×${1 shl gameKontra}" } ?: "Kontra",
                        color = if (style != null) Color.White else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        maxLines = 1,
                    )
                }
            }
        }

        // "Kontro dal_a:" — optional, records who called the game kontra (for stats)
        if (gameKontra > 0) {
            val color = KontraStyle.forLevel(gameKontra)?.color ?: StihOrange
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Kontro dal_a:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    kontraCandidates.forEach { name ->
                        val selected = kontraCaller == name
                        Text(
                            name,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (selected) color else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onKontraCaller(name) }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }

        when {
            contract == TarokContract.VALAT -> ValatTypeSelection(
                settings = settings,
                playerCount = playerCount,
                isValatAnnounced = isValatAnnounced,
                onValatAnnounced = onValatAnnounced,
                underlyingGame = underlyingGame,
            )
            contract == TarokContract.BARVNI_VALAT -> ScoreSummaryBox(
                rows = listOf("Barvni valat" to "${settings.getBarvniValatScore()} točk"),
            )
            contract != null && settings.contractHasFixedScoring(contract) -> {
                val base = settings.getContractBaseScore(contract, playerCount)
                val finalScore = if (outcome == HandOutcome.ZMAGAL) base else -base
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScoreSummaryBox(
                        rows = listOf(contract.displayName to "${if (finalScore > 0) "+" else ""}$finalScore točk"),
                    )
                    Text(
                        "Berač in Odprti berač imata fiksno število točk brez razlike.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            contract != null -> {
                // Razlika input + live total
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Column {
                            Text(
                                if (settings.contractUsesRazlika(contract, playerCount)) "Razlika" else "Točke",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedTextField(
                                value = points,
                                onValueChange = { new ->
                                    if (new.isEmpty() || new == "-" || new.toIntOrNull() != null) onPoints(new)
                                },
                                placeholder = { Text("0") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                modifier = Modifier.width(100.dp),
                                textStyle = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                                ),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        if (settings.autoScoring) {
                            val score = settings.calculateGameAndBonuses(
                                contract = contract,
                                playerCount = playerCount,
                                outcome = outcome,
                                razlika = points.toIntOrNull() ?: 0,
                                bonuses = currentBonuses(),
                                kontraLevel = gameKontra,
                            )
                            if (score != null) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        "Skupaj",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        if (score > 0) "+$score" else "$score",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = if (score > 0) StihGreen else StihRed,
                                    )
                                }
                            }
                        }
                    }
                }

                // 2x2 bonus grid
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        BonusCard(BonusType.TRULA, bonusStates, bonusKontras, expandedBonuses)
                        BonusCard(BonusType.PAGAT_ULTIMO, bonusStates, bonusKontras, expandedBonuses)
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        BonusCard(BonusType.KRALJI, bonusStates, bonusKontras, expandedBonuses)
                        BonusCard(BonusType.KRALJ_ULTIMO, bonusStates, bonusKontras, expandedBonuses)
                    }
                }
            }
        }
    }
}

@Composable
private fun ValatTypeSelection(
    settings: SettingsManager,
    playerCount: Int,
    isValatAnnounced: Boolean,
    onValatAnnounced: (Boolean) -> Unit,
    underlyingGame: TarokContract?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Tip valata",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            ValatTypeButton(
                text = "Tihi valat",
                icon = Icons.Filled.VisibilityOff,
                selected = !isValatAnnounced,
                selectedColor = com.epodbreg.stih.ui.theme.StihBlue,
                onClick = { onValatAnnounced(false) },
            )
            ValatTypeButton(
                text = "Napovedan",
                icon = Icons.Filled.Campaign,
                selected = isValatAnnounced,
                selectedColor = StihPurple,
                onClick = { onValatAnnounced(true) },
            )
        }

        val base = settings.getValatBaseScore(isValatAnnounced)
        val rows = buildList {
            add((if (isValatAnnounced) "Napovedan valat" else "Tihi valat") to "$base točk")
            if (underlyingGame != null && settings.currentRuleSet.generalRules.valatWithGamePoints) {
                val ugScore = settings.getContractBaseScore(underlyingGame, playerCount)
                add(underlyingGame.displayName to "+$ugScore točk")
                add("Skupaj" to "${base + ugScore} točk")
            }
        }
        ScoreSummaryBox(rows = rows)
    }
}

@Composable
private fun ValatTypeButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) selectedColor else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                icon, contentDescription = null,
                tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun ScoreSummaryBox(rows: List<Pair<String, String>>) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Povzetek točk:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            rows.forEachIndexed { i, (label, value) ->
                val isTotal = label == "Skupaj"
                if (isTotal) HorizontalDivider()
                Row {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isTotal || rows.size == 1) FontWeight.Bold else FontWeight.Normal,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        value,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isTotal || rows.size == 1) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun BonusCard(
    bonus: BonusType,
    bonusStates: MutableMap<BonusType, BonusCycleState>,
    bonusKontras: MutableMap<BonusType, Int>,
    expandedBonuses: MutableMap<BonusType, Boolean>,
) {
    val state = bonusStates[bonus] ?: BonusCycleState.NONE
    val isActive = state != BonusCycleState.NONE
    val isWon = state == BonusCycleState.WON || state == BonusCycleState.WON_ANNOUNCED
    val isLost = state == BonusCycleState.LOST || state == BonusCycleState.LOST_ANNOUNCED
    val announced = state == BonusCycleState.WON_ANNOUNCED || state == BonusCycleState.LOST_ANNOUNCED
    val isExpanded = expandedBonuses[bonus] == true

    fun tapWinLoss() {
        val wasAnnounced = announced
        when (state) {
            BonusCycleState.NONE -> bonusStates[bonus] = BonusCycleState.WON
            BonusCycleState.WON, BonusCycleState.WON_ANNOUNCED ->
                bonusStates[bonus] = if (wasAnnounced) BonusCycleState.LOST_ANNOUNCED else BonusCycleState.LOST
            BonusCycleState.LOST, BonusCycleState.LOST_ANNOUNCED -> {
                bonusStates[bonus] = BonusCycleState.NONE
                bonusKontras[bonus] = 0
            }
        }
    }

    fun tapAnnounced() {
        when (state) {
            BonusCycleState.WON -> bonusStates[bonus] = BonusCycleState.WON_ANNOUNCED
            BonusCycleState.WON_ANNOUNCED -> bonusStates[bonus] = BonusCycleState.WON
            BonusCycleState.LOST -> bonusStates[bonus] = BonusCycleState.LOST_ANNOUNCED
            BonusCycleState.LOST_ANNOUNCED -> bonusStates[bonus] = BonusCycleState.LOST
            else -> {}
        }
        val nowState = bonusStates[bonus]
        val nowAnnounced = nowState == BonusCycleState.WON_ANNOUNCED || nowState == BonusCycleState.LOST_ANNOUNCED
        if (!nowAnnounced) bonusKontras[bonus] = 0
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isExpanded) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else Color.Transparent)
            .padding(6.dp),
    ) {
        // Header pill — tap to expand/collapse
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    1.5.dp,
                    if (isActive) bonus.displayColor.copy(alpha = 0.6f) else Color.Transparent,
                    CircleShape,
                )
                .clickable { expandedBonuses[bonus] = !isExpanded }
                .padding(vertical = 9.dp, horizontal = 10.dp),
        ) {
            Icon(
                bonus.icon, contentDescription = null,
                tint = if (isActive) bonus.displayColor else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(14.dp),
            )
            Text(bonus.shortName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Spacer(Modifier.weight(1f))
            if (isActive && !isExpanded) {
                Icon(
                    if (isWon) Icons.Filled.Check else Icons.Filled.Close,
                    contentDescription = null,
                    tint = if (isWon) StihGreen else StihRed,
                    modifier = Modifier.size(12.dp),
                )
            }
            if (announced && !isExpanded) {
                Icon(Icons.Filled.Star, contentDescription = "Napovedano", tint = StihYellow, modifier = Modifier.size(11.dp))
            }
            Icon(
                if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp),
            )
        }

        if (isExpanded) {
            // Win/loss
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        1.5.dp,
                        when {
                            isWon -> StihGreen.copy(alpha = 0.6f)
                            isLost -> StihRed.copy(alpha = 0.6f)
                            else -> Color.Transparent
                        },
                        CircleShape,
                    )
                    .clickable { tapWinLoss() }
                    .padding(vertical = 9.dp),
            ) {
                Spacer(Modifier.weight(1f))
                when {
                    isWon -> {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = StihGreen, modifier = Modifier.size(12.dp))
                        Text("Zmagal_a", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = StihGreen)
                    }
                    isLost -> {
                        Icon(Icons.Filled.Close, contentDescription = null, tint = StihRed, modifier = Modifier.size(12.dp))
                        Text("Izgubil_a", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = StihRed)
                    }
                    else -> {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(11.dp))
                        Text("/", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(Icons.Filled.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(11.dp))
                    }
                }
                Spacer(Modifier.weight(1f))
            }

            // Announced
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        1.5.dp,
                        if (announced) StihYellow.copy(alpha = 0.7f) else Color.Transparent,
                        CircleShape,
                    )
                    .let { if (isActive) it.clickable { tapAnnounced() } else it }
                    .padding(vertical = 9.dp),
            ) {
                Spacer(Modifier.weight(1f))
                Icon(
                    if (announced) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = null,
                    tint = if (announced) StihYellow else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    "Napovedano",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isActive) 1f else 0.45f),
                    maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
            }

            // Kontra — announced bonuses only
            val kontra = bonusKontras[bonus] ?: 0
            val kontraStyle = KontraStyle.forLevel(kontra)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(kontraStyle?.color ?: MaterialTheme.colorScheme.surfaceVariant)
                    .let { m ->
                        if (announced) m.clickable {
                            bonusKontras[bonus] = ((bonusKontras[bonus] ?: 0) + 1) % 5
                        } else m
                    }
                    .padding(vertical = 9.dp),
            ) {
                Spacer(Modifier.weight(1f))
                Icon(
                    kontraStyle?.icon ?: Icons.Filled.Bolt,
                    contentDescription = null,
                    tint = if (kontraStyle != null) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    kontraStyle?.let { "${it.name} ×${1 shl kontra}" } ?: "Kontra",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (kontraStyle != null) Color.White
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = if (announced) 1f else 0.45f),
                    maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun KlopStep(
    players: List<Player>,
    klopPoints: MutableMap<String, String>,
    klopResults: MutableMap<String, KlopResultState>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Klop rezultat (za vse)", style = MaterialTheme.typography.titleLarge)

        players.forEach { player ->
            val result = klopResults[player.name] ?: KlopResultState.NONE
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    player.name,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(100.dp),
                    maxLines = 1,
                )
                OutlinedTextField(
                    value = klopPoints[player.name] ?: "",
                    onValueChange = { new ->
                        if (new.isEmpty() || new == "-" || new.toIntOrNull() != null) klopPoints[player.name] = new
                    },
                    placeholder = { Text("Točke", fontSize = 13.sp) },
                    enabled = result == KlopResultState.NONE,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.width(90.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center),
                )
                val (bg, fg) = when (result) {
                    KlopResultState.NONE -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurface
                    KlopResultState.PRAZEN -> StihGreen.copy(alpha = 0.16f) to StihGreen
                    KlopResultState.POLN -> StihRed.copy(alpha = 0.18f) to StihRed
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .background(bg)
                        .clickable {
                            val next = result.next()
                            klopResults[player.name] = next
                            if (next != KlopResultState.NONE) klopPoints[player.name] = ""
                        }
                        .padding(vertical = 8.dp, horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        when (result) {
                            KlopResultState.NONE -> "—"
                            KlopResultState.PRAZEN -> "Prazen_a (+70)"
                            KlopResultState.POLN -> "Poln_a (−70)"
                        },
                        color = fg,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        maxLines = 1,
                    )
                }
            }
        }

        Text(
            "Za Prazen/Poln klikni gumb, drugače napiši minus točke.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
