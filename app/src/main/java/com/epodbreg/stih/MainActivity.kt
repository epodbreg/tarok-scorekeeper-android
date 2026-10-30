package com.epodbreg.stih

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.epodbreg.stih.data.SessionManager
import com.epodbreg.stih.data.SettingsManager
import com.epodbreg.stih.model.GameSession
import com.epodbreg.stih.model.Player
import com.epodbreg.stih.ui.GameViewModel
import com.epodbreg.stih.ui.screens.CreditsScreen
import com.epodbreg.stih.ui.screens.HomeScreen
import com.epodbreg.stih.ui.screens.MainGameScreen
import com.epodbreg.stih.ui.screens.PlayerSetupScreen
import com.epodbreg.stih.ui.screens.ResumeSessionSheet
import com.epodbreg.stih.ui.screens.SessionHistoryScreen
import com.epodbreg.stih.ui.screens.SessionSummaryScreen
import com.epodbreg.stih.ui.screens.SettingsScreen
import com.epodbreg.stih.ui.theme.StihTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StihTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    StihApp()
                }
            }
        }
    }
}

private sealed interface Screen {
    data object Home : Screen
    data object PlayerSetup : Screen
    data object Game : Screen
    data object History : Screen
    data object Settings : Screen
    data object Credits : Screen
    data class Summary(val session: GameSession) : Screen
}

@Composable
private fun StihApp() {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager.get(context) }
    val settings = remember { SettingsManager.get(context) }

    var screen: Screen by remember { mutableStateOf(Screen.Home) }
    var setupPrefills by remember { mutableStateOf(listOf<String>()) }
    /** Group the next new session goes into ("Torek"); null = none */
    var setupGroup by remember { mutableStateOf<String?>(null) }
    var gameViewModel by remember { mutableStateOf<GameViewModel?>(null) }
    var showResumePrompt by remember {
        mutableStateOf(sessionManager.activeSession?.games?.isNotEmpty() == true)
    }

    fun startGame(players: List<Player>, group: String?, ruleSet: String) {
        if (sessionManager.activeSession == null) {
            // The picked rules become the selected set, and the game keeps a copy
            if (ruleSet != settings.houseScoringRules && ruleSet in settings.getAllRuleSetNames()) {
                settings.setHouseRules(ruleSet)
            }
            sessionManager.startNewSession(
                GameSession(players = players, groupName = group, rules = settings.currentRuleSet)
            )
        }
        gameViewModel = GameViewModel(sessionManager, settings, players)
        screen = Screen.Game
    }

    fun resumeActiveSession() {
        val session = sessionManager.activeSession ?: return
        gameViewModel = GameViewModel(sessionManager, settings, session.players)
        screen = Screen.Game
    }

    // System back from a simple top-level screen returns home instead of closing the app
    // (the game and end screens handle back themselves)
    BackHandler(enabled = screen in listOf(Screen.PlayerSetup, Screen.History, Screen.Settings, Screen.Credits)) {
        screen = Screen.Home
    }

    when (val s = screen) {
        Screen.Home -> {
            HomeScreen(
                activeSession = sessionManager.activeSession,
                onStartNew = {
                    setupPrefills = emptyList()
                    setupGroup = null
                    screen = Screen.PlayerSetup
                },
                onResume = { resumeActiveSession() },
                onHistory = { screen = Screen.History },
                onSettings = { screen = Screen.Settings },
                onCredits = { screen = Screen.Credits },
            )
            if (showResumePrompt) {
                ResumeSessionSheet(
                    session = sessionManager.activeSession,
                    onResume = {
                        showResumePrompt = false
                        resumeActiveSession()
                    },
                    onStartNew = {
                        showResumePrompt = false
                        sessionManager.clearActiveSession()
                        setupPrefills = emptyList()
                        setupGroup = null
                        screen = Screen.PlayerSetup
                    },
                    onDiscard = {
                        showResumePrompt = false
                        sessionManager.clearActiveSession()
                    },
                    onDismiss = { showResumePrompt = false },
                )
            }
        }

        Screen.PlayerSetup -> PlayerSetupScreen(
            prefills = setupPrefills,
            initialGroup = setupGroup,
            knownPlayers = sessionManager.frequentPlayerNames,
            savedSessions = sessionManager.savedSessions,
            ruleSetNames = settings.getAllRuleSetNames(),
            initialRuleSet = settings.houseScoringRules,
            onStart = { players, group, ruleSet ->
                sessionManager.clearActiveSession()
                startGame(players, group, ruleSet)
            },
            onCancel = { screen = Screen.Home },
        )

        Screen.Game -> {
            val vm = gameViewModel
            if (vm != null) {
                MainGameScreen(
                    viewModel = vm,
                    onSessionCompleted = { completed -> screen = Screen.Summary(completed) },
                    // The game stays saved; the unfinished-game sheet only shows when the app opens
                    onLeave = { screen = Screen.Home },
                )
            } else {
                screen = Screen.Home
            }
        }

        is Screen.Summary -> {
            // Back from the summary goes back into the game (it's only saved or discarded
            // from the buttons); before, back closed the app and the game was lost
            BackHandler {
                if (gameViewModel?.reopenEndedSession() == true) screen = Screen.Game
            }
            SessionSummaryScreen(
                session = s.session,
                onSave = {
                    sessionManager.saveSession(s.session)
                    gameViewModel = null
                    screen = Screen.Home
                },
                onNewWithSamePlayers = {
                    sessionManager.saveSession(s.session)
                    gameViewModel = null
                    setupPrefills = s.session.playerNames
                    setupGroup = s.session.groupName
                    screen = Screen.PlayerSetup
                },
                onDiscard = {
                    sessionManager.clearActiveSession()
                    gameViewModel = null
                    screen = Screen.Home
                },
            )
        }

        Screen.History -> SessionHistoryScreen(
            sessionManager = sessionManager,
            settings = settings,
            onBack = { screen = Screen.Home },
            onResumeSession = { session ->
                sessionManager.setActiveSessionDirect(session.copy(endTime = null))
                resumeActiveSession()
            },
            onNewWithPlayers = { players, group ->
                setupPrefills = players.map { it.name }
                setupGroup = group
                screen = Screen.PlayerSetup
            },
        )

        Screen.Settings -> SettingsScreen(
            settings = settings,
            sessionManager = sessionManager,
            onBack = { screen = Screen.Home },
        )

        Screen.Credits -> CreditsScreen(onBack = { screen = Screen.Home })
    }
}
