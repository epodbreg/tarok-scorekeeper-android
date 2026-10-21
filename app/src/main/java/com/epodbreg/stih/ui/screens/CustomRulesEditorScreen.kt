package com.epodbreg.stih.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.epodbreg.stih.model.BonusScoreConfig
import com.epodbreg.stih.model.BonusType
import com.epodbreg.stih.model.ContractScoreConfig
import com.epodbreg.stih.model.CustomScoringRules
import com.epodbreg.stih.model.TarokContract
import com.epodbreg.stih.ui.components.displayColor
import com.epodbreg.stih.ui.components.icon
import com.epodbreg.stih.ui.theme.StihRed

private enum class RulesSection(val title: String, val icon: ImageVector) {
    BASIC("Osnovno", Icons.Filled.Info),
    GAME_POINTS("Točke iger", Icons.Filled.Numbers),
    BONUSES("Bonusi", Icons.Filled.Star),
}

/** Custom rules editor. Port of CustomRulesEditorView from SettingsView.swift. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomRulesEditorScreen(
    initialRules: CustomScoringRules,
    isExisting: Boolean,
    onSave: (CustomScoringRules) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    var rules by remember { mutableStateOf(initialRules) }
    var selectedSection by remember { mutableStateOf(RulesSection.BASIC) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Uredi pravila") },
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                RulesSection.entries.forEach { section ->
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
            HorizontalDivider()

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
                        showDelete = isExisting,
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
            text = { Text("Ali ste prepričani, da želite izbrisati ta pravila? Te akcije ni mogoče razveljaviti.") },
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

@Composable
private fun BasicSection(
    rules: CustomScoringRules,
    onRules: (CustomScoringRules) -> Unit,
    showDelete: Boolean,
    onDeleteRequest: () -> Unit,
) {
    Text("Osnovno", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

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

    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Podvojitev samo za glavnega igralca", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        "Ko je omogočeno, samo glavni igralec_ka podvoji s radlcem — soigralec_ka ne more",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = rules.generalRules.radlcDoublingDeclarerOnly,
                    onCheckedChange = {
                        onRules(rules.copy(generalRules = rules.generalRules.copy(radlcDoublingDeclarerOnly = it)))
                    },
                )
            }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Radlc podvoji pagat ultimo", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        "Ko je izklopljeno, radlci ne podvojijo pagat ultimo nagrade",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = rules.generalRules.radlcDoublesPagat,
                    onCheckedChange = {
                        onRules(rules.copy(generalRules = rules.generalRules.copy(radlcDoublesPagat = it)))
                    },
                )
            }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Kazen za neuporabljene radlce",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = rules.generalRules.radlcPenaltyEnabled,
                    onCheckedChange = {
                        onRules(rules.copy(generalRules = rules.generalRules.copy(radlcPenaltyEnabled = it)))
                    },
                )
            }
            if (rules.generalRules.radlcPenaltyEnabled) {
                NumberFieldRow(
                    label = "Kazen na radlc:",
                    value = rules.generalRules.radlcPenaltyPerUnused,
                    onValue = {
                        onRules(rules.copy(generalRules = rules.generalRules.copy(radlcPenaltyPerUnused = it)))
                    },
                )
            }
        }
    }

    // Renons penalty
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Kazen za renons", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            NumberFieldRow(
                label = "Kazen:",
                value = rules.generalRules.renonsPenalty,
                onValue = { onRules(rules.copy(generalRules = rules.generalRules.copy(renonsPenalty = it))) },
            )
            Text(
                "Odšteje se igralcu_ki, ki naredi renons (vedno kazen)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showDelete) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
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

@Composable
private fun NumberFieldRow(label: String, value: Int, onValue: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf("$value") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp),
        )
        OutlinedTextField(
            value = text,
            onValueChange = { new ->
                text = new
                new.toIntOrNull()?.let(onValue)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(90.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            ),
        )
        Text("točk", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
