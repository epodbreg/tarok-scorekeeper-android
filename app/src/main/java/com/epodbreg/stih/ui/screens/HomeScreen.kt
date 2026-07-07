package com.epodbreg.stih.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.epodbreg.stih.data.SessionManager
import com.epodbreg.stih.model.GameSession
import com.epodbreg.stih.ui.theme.StihGreen
import com.epodbreg.stih.ui.theme.StihOrange
import com.epodbreg.stih.ui.theme.StihRed

/** Home screen. Port of ContentView.swift. */
@Composable
fun HomeScreen(
    activeSession: GameSession?,
    onStartNew: () -> Unit,
    onResume: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onCredits: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Spacer(Modifier.height(48.dp))
        Text("Štih", fontSize = 40.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        HomeButton(
            text = "Začni novo igro",
            icon = Icons.Filled.PlayArrow,
            background = MaterialTheme.colorScheme.primary,
            foreground = Color.White,
            onClick = onStartNew,
        )

        if (activeSession != null && activeSession.games.isNotEmpty()) {
            HomeButton(
                text = "Nadaljuj zadnjo igro",
                icon = Icons.Filled.Refresh,
                background = StihGreen,
                foreground = Color.White,
                onClick = onResume,
            )
        }

        HomeButton(
            text = "Zgodovina sej",
            icon = Icons.Filled.History,
            background = MaterialTheme.colorScheme.surfaceVariant,
            foreground = MaterialTheme.colorScheme.primary,
            onClick = onHistory,
        )

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            HomeButton(
                text = "Nastavitve",
                icon = Icons.Filled.Settings,
                background = MaterialTheme.colorScheme.surfaceVariant,
                foreground = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = onSettings,
            )
            HomeButton(
                text = "O aplikaciji",
                icon = Icons.Filled.Info,
                background = MaterialTheme.colorScheme.surfaceVariant,
                foreground = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = onCredits,
            )
        }
    }
}

@Composable
private fun HomeButton(
    text: String,
    icon: ImageVector,
    background: Color,
    foreground: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(20.dp))
            Text(text, color = foreground, fontSize = 18.sp)
        }
    }
}

/** Resume-session prompt. Port of ResumeSessionSheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResumeSessionSheet(
    session: GameSession?,
    onResume: () -> Unit,
    onStartNew: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Icon(
                Icons.Filled.History,
                contentDescription = null,
                tint = StihOrange,
                modifier = Modifier.size(44.dp),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Nedokončana igra", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (session != null) {
                    Text(
                        "Našli smo nedokončano igro z dne ${SessionManager.formatSessionDate(session.startTime)} " +
                            "z igralci ${session.playerNames.joinToString(", ")}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                Text(
                    "Ali jo želite nadaljevati?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SheetButton("Nadaljuj igro", StihGreen, onResume)
                SheetButton("Začni novo igro", MaterialTheme.colorScheme.primary, onStartNew)
                SheetButton("Ne shrani igre", StihRed, onDiscard)
            }
        }
    }
}

@Composable
private fun SheetButton(text: String, background: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
    }
}
