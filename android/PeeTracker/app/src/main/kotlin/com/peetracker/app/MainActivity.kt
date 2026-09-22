package com.peetracker.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.peetracker.app.notifications.NotificationsViewModel
import com.peetracker.app.ui.auth.AuthScreen
import com.peetracker.app.ui.groups.CreateGroupScreen
import com.peetracker.app.ui.groups.CreateOrJoinGroupScreen
import com.peetracker.app.ui.groups.InviteShareScreen
import com.peetracker.app.ui.groups.JoinGroupScreen
import com.peetracker.app.ui.leaderboard.LeaderboardScreen
import com.peetracker.app.ui.logging.LogEntryScreen
import com.peetracker.app.ui.session.SessionState
import com.peetracker.app.ui.session.SessionViewModel
import com.peetracker.app.ui.settings.SettingsScreen
import com.peetracker.app.ui.streaks.StreaksScreen
import dagger.hilt.android.AndroidEntryPoint

private object Destinations {
    const val SPLASH = "splash"
    const val AUTH = "auth"
    const val CREATE_OR_JOIN_GROUP = "create_or_join_group"
    const val CREATE_GROUP = "create_group"
    const val JOIN_GROUP = "join_group"
    const val JOIN_GROUP_CODE_ARG = "code"
    const val INVITE_SHARE = "invite_share"
    const val INVITE_SHARE_CODE_ARG = "inviteCode"
    const val LOG_ENTRY = "log_entry"
    const val LOG_ENTRY_GROUP_ARG = "groupId"
    const val SETTINGS = "settings"
    const val SETTINGS_GROUP_ARG = "groupId"

    fun joinGroupRoute(code: String? = null): String =
        if (code != null) "$JOIN_GROUP?$JOIN_GROUP_CODE_ARG=$code" else JOIN_GROUP

    fun inviteShareRoute(inviteCode: String): String = "$INVITE_SHARE/$inviteCode"

    fun logEntryRoute(groupId: String): String = "$LOG_ENTRY/$groupId"

    fun settingsRoute(groupId: String): String = "$SETTINGS/$groupId"
}

private object GroupTabs {
    const val LOG = "tab_log"
    const val LEADERBOARD = "tab_leaderboard"
    const val STREAKS = "tab_streaks"
    const val GROUP_ARG = "groupId"

    fun logRoute(groupId: String): String = "$LOG/$groupId"
    fun leaderboardRoute(groupId: String): String = "$LEADERBOARD/$groupId"
    fun streaksRoute(groupId: String): String = "$STREAKS/$groupId"
}

private fun parseInviteCodeFromDeepLink(data: Uri?): String? {
    if (data == null || data.scheme != "peetracker" || data.host != "join") return null
    return data.pathSegments.firstOrNull()
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var pendingInviteCode by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingInviteCode = parseInviteCodeFromDeepLink(intent?.data)
        setContent {
            PeeTrackerRoot(
                pendingInviteCode = pendingInviteCode,
                onPendingInviteCodeConsumed = { pendingInviteCode = null }
            )
        }
    }

    // launchMode="singleTop" (see the manifest) routes a deep link received while the app is
    // already running here instead of spawning a second Activity instance.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parseInviteCodeFromDeepLink(intent.data)?.let { pendingInviteCode = it }
    }
}

@Composable
private fun PeeTrackerRoot(
    pendingInviteCode: String?,
    onPendingInviteCodeConsumed: () -> Unit
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val navController = rememberNavController()
            val sessionViewModel: SessionViewModel = hiltViewModel()
            val sessionState by sessionViewModel.sessionState.collectAsState()

            // Keyed only on sessionState (not pendingInviteCode) so that consuming the pending
            // code below — which sets it back to null — can't re-run this branch and clobber the
            // join-screen navigation with a bounce back to CREATE_OR_JOIN_GROUP. The NeedsGroup
            // case only navigates here when there's no code to hand off; the effect below owns
            // that hand-off.
            LaunchedEffect(sessionState) {
                when (val state = sessionState) {
                    SessionState.Loading -> Unit
                    SessionState.SignedOut -> navController.navigate(Destinations.AUTH) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                    SessionState.NeedsGroup -> {
                        if (pendingInviteCode == null) {
                            navController.navigate(Destinations.CREATE_OR_JOIN_GROUP) {
                                popUpTo(navController.graph.id) { inclusive = true }
                            }
                        }
                    }
                    is SessionState.InGroup -> navController.navigate(Destinations.logEntryRoute(state.groupId)) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                }
            }

            // A deep-linked invite code can arrive before the user has signed in (or while
            // they're already sitting on NeedsGroup), so it's stashed on the Activity and applied
            // here, separately from the effect above, once the session lands on NeedsGroup.
            LaunchedEffect(sessionState, pendingInviteCode) {
                if (sessionState == SessionState.NeedsGroup && pendingInviteCode != null) {
                    navController.navigate(Destinations.joinGroupRoute(pendingInviteCode)) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                    onPendingInviteCodeConsumed()
                }
            }

            NavHost(navController = navController, startDestination = Destinations.SPLASH) {
                composable(Destinations.SPLASH) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                composable(Destinations.AUTH) {
                    AuthScreen(onSignedIn = { sessionViewModel.onSignedIn() })
                }

                composable(Destinations.CREATE_OR_JOIN_GROUP) {
                    CreateOrJoinGroupScreen(
                        onCreateGroup = { navController.navigate(Destinations.CREATE_GROUP) },
                        onJoinGroup = { navController.navigate(Destinations.joinGroupRoute()) },
                        onSignOut = { sessionViewModel.signOut() }
                    )
                }

                composable(Destinations.CREATE_GROUP) {
                    CreateGroupScreen(
                        onGroupCreated = { inviteCode ->
                            navController.navigate(Destinations.inviteShareRoute(inviteCode))
                        }
                    )
                }

                composable(
                    route = "${Destinations.JOIN_GROUP}?${Destinations.JOIN_GROUP_CODE_ARG}={${Destinations.JOIN_GROUP_CODE_ARG}}",
                    arguments = listOf(
                        navArgument(Destinations.JOIN_GROUP_CODE_ARG) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) { backStackEntry ->
                    JoinGroupScreen(
                        initialCode = backStackEntry.arguments?.getString(Destinations.JOIN_GROUP_CODE_ARG),
                        onJoined = { sessionViewModel.refreshGroupState() }
                    )
                }

                composable(
                    route = "${Destinations.INVITE_SHARE}/{${Destinations.INVITE_SHARE_CODE_ARG}}",
                    arguments = listOf(navArgument(Destinations.INVITE_SHARE_CODE_ARG) { type = NavType.StringType })
                ) { backStackEntry ->
                    InviteShareScreen(
                        inviteCode = backStackEntry.arguments?.getString(Destinations.INVITE_SHARE_CODE_ARG).orEmpty(),
                        onContinue = { sessionViewModel.refreshGroupState() }
                    )
                }

                composable(
                    route = "${Destinations.LOG_ENTRY}/{${Destinations.LOG_ENTRY_GROUP_ARG}}",
                    arguments = listOf(navArgument(Destinations.LOG_ENTRY_GROUP_ARG) { type = NavType.StringType })
                ) { backStackEntry ->
                    val groupId = backStackEntry.arguments?.getString(Destinations.LOG_ENTRY_GROUP_ARG).orEmpty()
                    GroupHomeScreen(
                        groupId = groupId,
                        onOpenSettings = { navController.navigate(Destinations.settingsRoute(groupId)) }
                    )
                }

                composable(
                    route = "${Destinations.SETTINGS}/{${Destinations.SETTINGS_GROUP_ARG}}",
                    arguments = listOf(navArgument(Destinations.SETTINGS_GROUP_ARG) { type = NavType.StringType })
                ) { backStackEntry ->
                    val groupId = backStackEntry.arguments?.getString(Destinations.SETTINGS_GROUP_ARG).orEmpty()
                    SettingsScreen(
                        groupId = groupId,
                        onLeftGroup = { sessionViewModel.refreshGroupState() },
                        onSignOut = { sessionViewModel.signOut() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupHomeScreen(groupId: String, onOpenSettings: () -> Unit) {
    val tabNavController = rememberNavController()
    val tabBackStackEntry by tabNavController.currentBackStackEntryAsState()
    val currentRoute = tabBackStackEntry?.destination?.route
    val logRoute = "${GroupTabs.LOG}/{${GroupTabs.GROUP_ARG}}"
    val leaderboardRoute = "${GroupTabs.LEADERBOARD}/{${GroupTabs.GROUP_ARG}}"
    val streaksRoute = "${GroupTabs.STREAKS}/{${GroupTabs.GROUP_ARG}}"

    val context = LocalContext.current
    val notificationsViewModel: NotificationsViewModel = hiltViewModel()
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    // Requested here, not during auth/onboarding, because it only makes sense once the user has
    // an active group to receive notifications about — asking earlier would be a cold, contextless
    // permission prompt with a worse grant rate.
    LaunchedEffect(groupId) {
        notificationsViewModel.registerTokenForCurrentUser()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentRoute == logRoute,
                    onClick = {
                        if (currentRoute != logRoute) {
                            tabNavController.navigate(GroupTabs.logRoute(groupId)) {
                                popUpTo(tabNavController.graph.id) { inclusive = true }
                            }
                        }
                    },
                    icon = { Icon(Icons.Filled.WaterDrop, contentDescription = null) },
                    label = { Text("Log") }
                )
                NavigationBarItem(
                    selected = currentRoute == leaderboardRoute,
                    onClick = {
                        if (currentRoute != leaderboardRoute) {
                            tabNavController.navigate(GroupTabs.leaderboardRoute(groupId)) {
                                popUpTo(tabNavController.graph.id) { inclusive = true }
                            }
                        }
                    },
                    icon = { Icon(Icons.Filled.EmojiEvents, contentDescription = null) },
                    label = { Text("Leaderboard") }
                )
                NavigationBarItem(
                    selected = currentRoute == streaksRoute,
                    onClick = {
                        if (currentRoute != streaksRoute) {
                            tabNavController.navigate(GroupTabs.streaksRoute(groupId)) {
                                popUpTo(tabNavController.graph.id) { inclusive = true }
                            }
                        }
                    },
                    icon = { Icon(Icons.Filled.LocalFireDepartment, contentDescription = null) },
                    label = { Text("Streaks") }
                )
            }
        }
    ) { padding ->
        NavHost(
            navController = tabNavController,
            startDestination = GroupTabs.logRoute(groupId),
            modifier = Modifier.padding(padding)
        ) {
            composable(
                route = logRoute,
                arguments = listOf(navArgument(GroupTabs.GROUP_ARG) { type = NavType.StringType })
            ) {
                LogEntryScreen()
            }
            composable(
                route = leaderboardRoute,
                arguments = listOf(navArgument(GroupTabs.GROUP_ARG) { type = NavType.StringType })
            ) {
                LeaderboardScreen()
            }
            composable(
                route = streaksRoute,
                arguments = listOf(navArgument(GroupTabs.GROUP_ARG) { type = NavType.StringType })
            ) {
                StreaksScreen()
            }
        }
    }
}
