package com.epodbreg.stih.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TripOrigin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.epodbreg.stih.model.BonusScoreConfig
import com.epodbreg.stih.model.BonusType
import com.epodbreg.stih.model.ContractScoreConfig
import com.epodbreg.stih.model.CustomScoringRules
import com.epodbreg.stih.model.GeneralRulesConfig
import com.epodbreg.stih.model.TarokContract
import com.epodbreg.stih.ui.components.displayColor
import com.epodbreg.stih.ui.components.icon
import com.epodbreg.stih.ui.theme.StihOrange
import com.epodbreg.stih.ui.theme.StihRed
import kotlinx.coroutines.delay

private enum class RulesSection(val title: String, val icon: ImageVector) {
    BASIC("Osnovno", Icons.Filled.Info),
    GAME_POINTS("Točke iger", Icons.Filled.Numbers),
    BONUSES("Bonusi", Icons.Filled.Star),
}

/**
 * Rules editor. Port of CustomRulesEditorView from SettingsView.swift. Standard
 * ([isPredefined]) shows only Osnovno: its switches can change, its points are fixed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomRulesEditorScreen(
    initialRules: CustomScoringRules,
    isPredefined: Boolean,
    /** Only saved custom rule sets can be deleted */
    canDelete: Boolean,
    onSave: (CustomScoringRules) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    var rules by remember { mutableStateOf(initialRules) }
    var selectedSection by remember { mutableStateOf(RulesSection.BASIC) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val sections = if (isPredefined) listOf(RulesSection.BASIC) else RulesSection.entries

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isPredefined) "Pravila: ${initialRules.name}" else "Uredi pravila") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Close, contentDescription = "Prekliči")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { onSave(rules) },
                        enabled = rules.name.isNotBlank(),
                    ) { Text("Shrani", fontWeight = FontWeight.SemiBold) }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Section tabs
            if (sections.size > 1) Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                sections.forEach { section ->
                    val selected = selectedSection == section
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { selectedSection = section }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                section.icon, contentDescription = null,
                                tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                section.title,
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
            if (sections.size > 1) HorizontalDivider()

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (selectedSection) {
                    RulesSection.BASIC -> BasicSection(
                        rules = rules,
                        onRules = { rules = it },
                        isPredefined = isPredefined,
                        showDelete = canDelete,
                        onDeleteRequest = { showDeleteConfirmation = true },
                    )
                    RulesSection.GAME_POINTS -> GamePointsSection(rules = rules, onRules = { rules = it })
                    RulesSection.BONUSES -> BonusesSection(rules = rules, onRules = { rules = it })
                }
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Izbriši pravila") },
            text = { Text("Pravila »${initialRules.name}« bodo izbrisana. Tega ni mogoče razveljaviti.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirmation = false
                    onDelete()
                }) { Text("Izbriši", color = StihRed) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("Prekliči") }
            },
        )
    }
}

/** Osnovno: the table's switches, in three groups */
@Composable
private fun BasicSection(
    rules: CustomScoringRules,
    onRules: (CustomScoringRules) -> Unit,
    isPredefined: Boolean,
    showDelete: Boolean,
    onDeleteRequest: () -> Unit,
) {
    val general = rules.generalRules
    fun update(change: (GeneralRulesConfig) -> GeneralRulesConfig) = onRules(rules.copy(generalRules = change(general)))

    if (isPredefined) {
        Text(
            "Točke Standard pravil so fiksne. Za drugačne točke dodaj nova pravila.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Ime pravil", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            OutlinedTextField(
                value = rules.name,
                onValueChange = { onRules(rules.copy(name = it)) },
                placeholder = { Text("Ime pravil") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    RuleGroup("Radlci", Icons.Filled.TripOrigin, Color(0xFF5856D6)) {
        RuleToggle(
            "Radlc podvoji tudi soigralcu_ki",
            "Ko je izklopljeno, se podvojijo samo točke glavnega igralca_ke",
            general.radlcDoublesPartner,
        ) { on -> update { it.copy(radlcDoublesPartner = on) } }
        HorizontalDivider()
        RuleToggle(
            "Radlc podvoji pagat ultimo",
            "Ko je izklopljeno, radlci ne podvojijo pagat ultimo nagrade",
            general.radlcDoublesPagat,
        ) { on -> update { it.copy(radlcDoublesPagat = on) } }
        HorizontalDivider()
        RuleToggle(
            "Kazen za neuporabljene radlce",
            "Ob koncu seje se odšteje za vsak neuporabljen radlc",
            general.radlcPenaltyEnabled,
        ) { on -> update { it.copy(radlcPenaltyEnabled = on) } }
        AnimatedVisibility(general.radlcPenaltyEnabled) {
            NumberFieldRow(label = "Kazen na radlc", value = general.radlcPenaltyPerUnused) { value ->
                update { it.copy(radlcPenaltyPerUnused = value) }
            }
        }
    }

    RuleGroup("Klop", Icons.Filled.BugReport, StihOrange) {
        RuleToggle(
            "Radlc podvoji klop",
            "Igralec_ka z neuporabljenim radlcem dobi dvojne točke klopa",
            general.radlcDoublesKlop,
        ) { on -> update { it.copy(radlcDoublesKlop = on) } }
        AnimatedVisibility(general.radlcDoublesKlop) {
            Box(Modifier.padding(start = 16.dp)) {
                RuleToggle(
                    "Na prazen klop ne vpliva radlc",
                    "Prazen klop se ne podvoji in ne porabi radlca",
                    general.radlcSkipsPrazenKlop,
                ) { on -> update { it.copy(radlcSkipsPrazenKlop = on) } }
            }
        }
        HorizontalDivider()
        RuleToggle(
            "Obvezni klop",
            "Po določenih pogojih zahteva klop ali solo brez",
            general.obvezniKlopEnabled,
        ) { on -> update { it.copy(obvezniKlopEnabled = on) } }
        AnimatedVisibility(general.obvezniKlopEnabled) {
            Column(
                modifier = Modifier.padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SmallToggle("Ko ima igralec_ka okrogle točke", general.obvezniKlopOnRoundScore) { on ->
                    update { it.copy(obvezniKlopOnRoundScore = on) }
                }
                if (general.obvezniKlopOnRoundScore) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Večkratnik",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.width(200.dp)) {
                            val steps = listOf(100, 500, 1000)
                            steps.forEachIndexed { index, step ->
                                SegmentedButton(
                                    selected = general.klopStep == step,
                                    onClick = { update { it.copy(obvezniKlopStep = step) } },
                                    shape = SegmentedButtonDefaults.itemShape(index, steps.size),
                                    colors = stihSegmentColors(),
                                    icon = {},
                                ) { Text("$step", fontSize = 13.sp) }
                            }
                        }
                    }
                }
                SmallToggle("Ko je izid igre enak 0", general.obvezniKlopOnZeroOutcome) { on ->
                    update { it.copy(obvezniKlopOnZeroOutcome = on) }
                }
            }
        }
    }

    RuleGroup("Točkovanje", Icons.Filled.Functions, Color(0xFF007AFF)) {
        RuleToggle(
            "Valat s točkami igre",
            "Pri valatu se prištejejo tudi točke osnovne igre",
            general.valatWithGamePoints,
        ) { on -> update { it.copy(valatWithGamePoints = on) } }
        HorizontalDivider()
        NumberFieldRow(label = "Kazen za renons", value = general.renonsPenalty) { value ->
            update { it.copy(renonsPenalty = value) }
        }
        Text(
            "Odšteje se igralcu_ki, ki naredi renons (vedno kazen)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showDelete) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .clip(CircleShape)
                .background(StihRed.copy(alpha = 0.1f))
                .clickable(onClick = onDeleteRequest)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Delete, contentDescription = null, tint = StihRed, modifier = Modifier.size(16.dp))
                Text("Izbriši ta pravila", color = StihRed, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun RuleGroup(title: String, icon: ImageVector, color: Color, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}

@Composable
private fun RuleToggle(title: String, caption: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SmallToggle(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun GamePointsSection(
    rules: CustomScoringRules,
    onRules: (CustomScoringRules) -> Unit,
) {
    Text("Točke iger", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

    val editableContracts = listOf(
        TarokContract.TRI to "Tri", TarokContract.DVE to "Dve", TarokContract.ENA to "Ena",
        TarokContract.SOLO_TRI to "Solo tri", TarokContract.SOLO_DVE to "Solo dve", TarokContract.SOLO_ENA to "Solo ena",
        TarokContract.SOLO_BREZ to "Solo brez", TarokContract.BERAC to "Berač", TarokContract.ODPRTI_BERAC to "Odprti berač",
        TarokContract.BARVNI_VALAT to "Barvni valat",
    )

    fun updateScore(contract: TarokContract, threePlayer: Int?, fourPlus: Int?) {
        val fixed = contract.hasFixedScoring
        val usesRazlika = !fixed
        val existing = rules.contractScores[contract.rawValue]
            ?: ContractScoreConfig(0, 0, usesRazlika = usesRazlika, hasFixedScoring = fixed)
        val updated = existing.copy(
            threePlayerScore = threePlayer ?: existing.threePlayerScore,
            fourPlusPlayerScore = fourPlus ?: existing.fourPlusPlayerScore,
        )
        onRules(rules.copy(contractScores = rules.contractScores + (contract.rawValue to updated)))
    }

    editableContracts.forEach { (contract, name) ->
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(contract.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
                NumberFieldRow(
                    label = "3 igralci:",
                    value = rules.contractScores[contract.rawValue]?.threePlayerScore ?: 0,
                    onValue = { updateScore(contract, threePlayer = it, fourPlus = null) },
                )
                NumberFieldRow(
                    label = "4+ igralci:",
                    value = rules.contractScores[contract.rawValue]?.fourPlusPlayerScore ?: 0,
                    onValue = { updateScore(contract, threePlayer = null, fourPlus = it) },
                )
                val config = rules.contractScores[contract.rawValue]
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (config?.usesRazlika != false && !contract.hasFixedScoring) {
                        Text("± Uporablja razliko", style = MaterialTheme.typography.labelSmall, color = Color(0xFF007AFF))
                    }
                    if (config?.hasFixedScoring == true || contract.hasFixedScoring) {
                        Text("🔒 Fiksno točkovanje", style = MaterialTheme.typography.labelSmall, color = com.epodbreg.stih.ui.theme.StihOrange)
                    }
                }
            }
        }
    }

    // Valat — tihi/napovedan live in bonusScores
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(TarokContract.VALAT.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text("Valat", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
            NumberFieldRow(
                label = "Tihi valat:",
                value = rules.bonusScores.tihiValat,
                onValue = { onRules(rules.copy(bonusScores = rules.bonusScores.copy(tihiValat = it))) },
            )
            NumberFieldRow(
                label = "Napovedan:",
                value = rules.bonusScores.napovedanValat,
                onValue = { onRules(rules.copy(bonusScores = rules.bonusScores.copy(napovedanValat = it))) },
            )
        }
    }
}

@Composable
private fun BonusesSection(
    rules: CustomScoringRules,
    onRules: (CustomScoringRules) -> Unit,
) {
    Text("Bonus točke", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

    BonusEditor(
        title = "Trula", type = BonusType.TRULA,
        base = rules.bonusScores.trulaBase, announced = rules.bonusScores.trulaAnnounced,
        onBase = { onRules(rules.copy(bonusScores = rules.bonusScores.copy(trulaBase = it))) },
        onAnnounced = { onRules(rules.copy(bonusScores = rules.bonusScores.copy(trulaAnnounced = it))) },
    )
    BonusEditor(
        title = "Kralji", type = BonusType.KRALJI,
        base = rules.bonusScores.kraljBase, announced = rules.bonusScores.kraljAnnounced,
        onBase = { onRules(rules.copy(bonusScores = rules.bonusScores.copy(kraljBase = it))) },
        onAnnounced = { onRules(rules.copy(bonusScores = rules.bonusScores.copy(kraljAnnounced = it))) },
    )
    BonusEditor(
        title = "Kralj Ultimo", type = BonusType.KRALJ_ULTIMO,
        base = rules.bonusScores.kraljUltimoBase, announced = rules.bonusScores.kraljUltimoAnnounced,
        onBase = { onRules(rules.copy(bonusScores = rules.bonusScores.copy(kraljUltimoBase = it))) },
        onAnnounced = { onRules(rules.copy(bonusScores = rules.bonusScores.copy(kraljUltimoAnnounced = it))) },
    )
    BonusEditor(
        title = "Pagat Ultimo", type = BonusType.PAGAT_ULTIMO,
        base = rules.bonusScores.pagatUltimoBase, announced = rules.bonusScores.pagatUltimoAnnounced,
        onBase = { onRules(rules.copy(bonusScores = rules.bonusScores.copy(pagatUltimoBase = it))) },
        onAnnounced = { onRules(rules.copy(bonusScores = rules.bonusScores.copy(pagatUltimoAnnounced = it))) },
    )

    // Mond kaz
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    BonusType.MOND_KAZ.icon, contentDescription = null,
                    tint = BonusType.MOND_KAZ.displayColor, modifier = Modifier.size(18.dp),
                )
                Text("Kazen za izgubljenega monda", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
            NumberFieldRow(
                label = "Kazen (vedno):",
                value = rules.bonusScores.mondKazBase,
                onValue = { onRules(rules.copy(bonusScores = rules.bonusScores.copy(mondKazBase = it))) },
            )
        }
    }
}

@Composable
private fun BonusEditor(
    title: String,
    type: BonusType,
    base: Int,
    announced: Int,
    onBase: (Int) -> Unit,
    onAnnounced: (Int) -> Unit,
) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(type.icon, contentDescription = null, tint = type.displayColor, modifier = Modifier.size(18.dp))
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
            NumberFieldRow(label = "Nenapovedano:", value = base, onValue = onBase)
            NumberFieldRow(label = "Napovedano:", value = announced, onValue = onAnnounced)
        }
    }
}

/**
 * A typed number of points. Digits only (max 5); the value updates on every keystroke, so
 * "Shrani" never misses the last digit, and tapping the field selects it so typing replaces it.
 */
@Composable
private fun NumberFieldRow(label: String, value: Int, onValue: (Int) -> Unit) {
    var field by remember { mutableStateOf(TextFieldValue("$value")) }
    // Follow outside changes (e.g. another tab), but not while the user types "" → 0
    LaunchedEffect(value) {
        if (field.text.toIntOrNull() != value && field.text.isNotEmpty()) field = TextFieldValue("$value")
    }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    LaunchedEffect(focused) {
        if (focused) {
            delay(30) // after the tap has placed the cursor
            field = field.copy(selection = TextRange(0, field.text.length))
        }
    }
    val focusManager = LocalFocusManager.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = field,
            onValueChange = { new ->
                val digits = new.text.filter(Char::isDigit).take(5)
                field = new.copy(text = digits, selection = if (digits == new.text) new.selection else TextRange(digits.length))
                onValue(digits.toIntOrNull() ?: 0)
            },
            singleLine = true,
            interactionSource = interaction,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            modifier = Modifier.width(90.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            ),
        )
        Text("točk", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
