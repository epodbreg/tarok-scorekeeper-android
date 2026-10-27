package com.epodbreg.stih.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.epodbreg.stih.data.BackupError
import com.epodbreg.stih.data.BackupException
import com.epodbreg.stih.data.BackupStore
import com.epodbreg.stih.data.RestorePlan
import com.epodbreg.stih.data.SessionManager
import com.epodbreg.stih.data.SettingsManager
import com.epodbreg.stih.data.StihBackup
import com.epodbreg.stih.model.TarokStrings
import com.epodbreg.stih.ui.theme.StihGreen
import com.epodbreg.stih.ui.theme.StihOrange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private class LoadedBackup(val backup: StihBackup, val plan: RestorePlan, val source: String)

private class RestoreResult(val addedSessions: Int, val restoredGame: Boolean, val restoredSettings: Boolean)

private class CopyRow(val copy: BackupStore.AutomaticCopy, val sessionCount: Int?)

/** Settings → Podatki → Obnovi iz kopije: pick a backup, see what it adds, confirm. Port of RestoreBackupView. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestoreBackupScreen(
    sessionManager: SessionManager,
    settings: SettingsManager,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var copies by remember { mutableStateOf<List<CopyRow>>(emptyList()) }
    var loaded by remember { mutableStateOf<LoadedBackup?>(null) }
    var restoreSettings by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<RestoreResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun load(read: () -> String, source: String) {
        try {
            val backup = StihBackup.read(read())
            loaded = LoadedBackup(backup, sessionManager.restorePlan(backup), source)
            restoreSettings = false
        } catch (e: BackupException) {
            errorMessage = e.error.message
        } catch (_: Exception) {
            errorMessage = BackupError.UNREADABLE.message
        }
    }

    // "application/json" plus "*/*": some apps save JSON without a type
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "Izbrana datoteka"
        load({ context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() } }, name)
    }

    LaunchedEffect(Unit) {
        val found = BackupStore.automaticCopies(sessionManager.backupsDir)
        copies = found.map { CopyRow(it, null) }
        copies = withContext(Dispatchers.IO) {
            found.map { copy -> CopyRow(copy, runCatching { StihBackup.read(copy.file.readText()).sessions.size }.getOrNull()) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Obnovi iz kopije") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = if (result == null) "Prekliči" else "Končano")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            val done = result
            val current = loaded
            when {
                done != null -> DoneSection(done, onClose)
                current != null -> PreviewSection(
                    loaded = current,
                    restoreSettings = restoreSettings,
                    onRestoreSettings = { restoreSettings = it },
                    onApply = {
                        val added = sessionManager.applyRestore(current.plan)
                        val restoredGame = current.plan.activeSession?.let { it.id == sessionManager.activeSession?.id } ?: false
                        val backupSettings = current.backup.settings
                        if (restoreSettings && backupSettings != null) settings.applyBackup(backupSettings)
                        result = RestoreResult(added, restoredGame, restoreSettings && backupSettings != null)
                    },
                    onPickOther = {
                        loaded = null
                        restoreSettings = false
                    },
                )
                else -> SourceSection(
                    copies = copies,
                    onPickFile = { picker.launch(arrayOf("application/json", "*/*")) },
                    onPickCopy = { row -> load({ row.copy.file.readText() }, copyTitle(row.copy)) },
                )
            }
        }
    }

    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("Kopije ni mogoče odpreti") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { errorMessage = null }) { Text("V redu") } },
        )
    }
}

// ---- Step 1: choose a backup ----------------------------------------------------

@Composable
private fun SourceSection(copies: List<CopyRow>, onPickFile: () -> Unit, onPickCopy: (CopyRow) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPickFile)
            .padding(16.dp),
    ) {
        Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text("Izberi datoteko …", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
    }
    Footnote("Kopijo narediš v Nastavitve → Podatki → Varnostna kopija — na kateremkoli telefonu s Štihom, tudi na iPhonu.")
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

    SectionHeader("Samodejne kopije na tem telefonu")
    if (copies.isEmpty()) {
        Text(
            "Na tem telefonu še ni samodejnih kopij.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
    copies.forEach { row ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onPickCopy(row) }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(copyTitle(row.copy), style = MaterialTheme.typography.bodyLarge)
            val kind = if (row.copy.kind == BackupStore.Kind.DAILY) "Dnevna kopija" else "Seje in nastavitve"
            Text(
                row.sessionCount?.let { "$kind · ${TarokStrings.sejaCountText(it)}" } ?: kind,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Footnote(
        "Štih vsak dan, ko shraniš sejo, obdrži kopijo zgodovine (zadnjih ${BackupStore.KEEP_DAILY} dni), " +
            "in eno pred vsakim »Počisti zgodovino«.",
    )
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(16.dp),
    ) {
        Icon(Icons.Filled.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Text(
            "Obnova samo doda seje, ki jih na telefonu še ni. Ničesar ne izbriše in ne spremeni.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---- Step 2: preview ----------------------------------------------------------------

@Composable
private fun PreviewSection(
    loaded: LoadedBackup,
    restoreSettings: Boolean,
    onRestoreSettings: (Boolean) -> Unit,
    onApply: () -> Unit,
    onPickOther: () -> Unit,
) {
    val plan = loaded.plan
    val backup = loaded.backup

    InfoRow("Kopija", loaded.source)
    backup.exportedAt?.let { InfoRow("Narejena", dayTimeFormat().format(Date(it))) }
    backup.app?.let { InfoRow("Iz", "${if (it.platform == "android") "Android" else "iPhone"}, Štih ${it.version}") }
    InfoRow("Seje v kopiji", "${backup.sessions.size}")
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

    SectionHeader("Kaj se bo zgodilo")
    PreviewRow(Icons.Filled.AddCircle, StihGreen, "Nove seje", "${plan.newSessions.size}")
    PreviewRow(Icons.Filled.RemoveCircle, MaterialTheme.colorScheme.onSurfaceVariant, "Že na telefonu — ostanejo, kot so", "${plan.alreadyOnPhone}")
    if (plan.unreadable > 0) PreviewRow(Icons.Filled.Warning, StihOrange, "Poškodovane — preskočene", "${plan.unreadable}")
    when {
        plan.activeSession != null -> PreviewRow(Icons.Filled.PlayCircle, StihGreen, "Nedokončana igra", "bo obnovljena")
        plan.keepsPhoneGame -> PreviewRow(Icons.Filled.PauseCircle, MaterialTheme.colorScheme.onSurfaceVariant, "Nedokončana igra", "ostane tvoja trenutna")
    }

    if (backup.settings != null) {
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
            Text("Obnovi tudi nastavitve", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = restoreSettings, onCheckedChange = onRestoreSettings)
        }
        Footnote("Pravila točkovanja, radlci, obvezni klop in tvoje ime. Če je izklopljeno, ostanejo nastavitve tega telefona.")
    }

    val title = when {
        plan.newSessions.isNotEmpty() -> "Dodaj ${TarokStrings.sejaCountText(plan.newSessions.size)}"
        plan.activeSession != null -> "Obnovi nedokončano igro"
        restoreSettings -> "Obnovi nastavitve"
        else -> "Ni ničesar novega"
    }
    Button(
        onClick = onApply,
        enabled = !plan.addsNothing || restoreSettings,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp),
    ) { Text(title, fontWeight = FontWeight.SemiBold) }
    TextButton(onClick = onPickOther, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text("Izberi drugo kopijo")
    }
}

// ---- Step 3: done -------------------------------------------------------------------

@Composable
private fun DoneSection(result: RestoreResult, onClose: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = StihGreen, modifier = Modifier.size(56.dp))
        Text("Obnovljeno", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val lines = buildList {
                add("Dodanih sej: ${result.addedSessions}")
                if (result.restoredGame) add("Nedokončano igro nadaljuješ na začetnem zaslonu.")
                if (result.restoredSettings) add("Nastavitve so obnovljene.")
            }
            lines.forEach {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
        TextButton(onClick = onClose) { Text("Končano", fontWeight = FontWeight.SemiBold) }
    }
}

// ---- Small pieces --------------------------------------------------------------------

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun Footnote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
    }
}

@Composable
private fun PreviewRow(icon: ImageVector, color: Color, title: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val slovene = Locale("sl", "SI")
private fun dayFormat() = SimpleDateFormat("d. MMMM yyyy", slovene)
private fun dayTimeFormat() = SimpleDateFormat("d. MMMM yyyy 'ob' HH:mm", slovene)

/** "23. oktober 2026", or "Pred brisanjem, 23. oktober 2026 ob 21:47" */
private fun copyTitle(copy: BackupStore.AutomaticCopy): String = when (copy.kind) {
    BackupStore.Kind.DAILY -> dayFormat().format(Date(copy.date))
    BackupStore.Kind.BEFORE_CLEAR -> "Pred brisanjem, " + dayTimeFormat().format(Date(copy.date))
}
