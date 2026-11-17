package com.opdownloader.app.ui

import android.app.Activity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.opdownloader.app.data.Store
import com.opdownloader.app.data.StarPrompt
import com.opdownloader.app.ui.screens.APP_GITHUB_REPO
import com.opdownloader.app.ui.screens.AboutScreen
import com.opdownloader.app.ui.screens.ActiveDownloadsScreen
import com.opdownloader.app.ui.screens.AddDownloadScreen
import com.opdownloader.app.ui.screens.BrowserScreen
import com.opdownloader.app.ui.screens.DownloadDetailsScreen
import com.opdownloader.app.ui.screens.EngineSetupScreen
import com.opdownloader.app.ui.screens.FileManagerScreen
import com.opdownloader.app.ui.screens.HomeScreen
import com.opdownloader.app.ui.screens.OnboardingScreen
import com.opdownloader.app.ui.screens.QualityScreen
import com.opdownloader.app.ui.screens.SettingsScreen
import com.opdownloader.app.ui.screens.SplashScreen
import com.opdownloader.app.ui.screens.SupportScreen
import com.opdownloader.app.ui.screens.UrlAnalyzerScreen
import com.opdownloader.app.ui.screens.openLink
import com.opdownloader.app.ui.theme.LocalAppColors
import com.opdownloader.app.ui.theme.PremiumTheme
import com.opdownloader.app.ui.theme.resolveDark

private data class TabSpec(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabSpec("home", "Home", Icons.Rounded.Home),
    TabSpec("active", "Active", Icons.Rounded.FileDownload),
    TabSpec("files", "Files", Icons.Rounded.FolderOpen),
    TabSpec("browser", "Browser", Icons.Rounded.TravelExplore),
    TabSpec("settings", "Settings", Icons.Rounded.Settings),
)

@Composable
fun PremiumApp() {
    val settings by Store.settings.collectAsState()
    val dark = resolveDark(settings.themeMode)

    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    PremiumTheme(darkTheme = dark) {
        val colors = LocalAppColors.current
        val context = LocalContext.current
        val nav = rememberNavController()
        val backStack by nav.currentBackStackEntryAsState()
        val route = backStack?.destination?.route
        // Routes with query args (e.g. "browser?url={url}") still belong to a tab;
        // compare only the path so the navbar stays visible on the Browser tab.
        val activeRoute = route?.substringBefore("?")
        var browserFullscreen by remember { mutableStateOf(false) }
        LaunchedEffect(activeRoute) {
            if (activeRoute != "browser") browserFullscreen = false
        }
        // Fullscreen browsing hides the floating tab bar so the page gets the
        // whole screen. The Browser screen shows an exit button in that mode.
        val showTabs = tabs.any { it.route == activeRoute } &&
            !(activeRoute == "browser" && browserFullscreen)
        var showStarPrompt by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            StarPrompt.ensureInstalled(context)
        }

        // Ask for a GitHub star only after three days of use, and only on the
        // main tabs so it never interrupts splash, onboarding or engine setup.
        LaunchedEffect(route, showTabs) {
            if (showTabs && !showStarPrompt && StarPrompt.isDue(context)) {
                StarPrompt.markShown(context)
                showStarPrompt = true
            }
        }

        LaunchedEffect(route, ShareInbox.pending) {
            val shared = ShareInbox.pending
            if (shared != null && route != null && route != "splash" && route != "onboarding" && route != "setup") {
                ShareInbox.clear()
                nav.navigate("analyzer/${android.net.Uri.encode(shared)}")
            }
        }

        Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
            NavHost(
                navController = nav,
                startDestination = "splash",
                modifier = Modifier.fillMaxSize(),
            ) {
                composable("splash") {
                    SplashScreen(onFinished = {
                        val settings = Store.settings.value
                        val target = when {
                            !settings.onboarded -> "onboarding"
                            !settings.engineSetupDone -> "setup"
                            else -> "home"
                        }
                        nav.navigate(target) {
                            popUpTo("splash") { inclusive = true }
                        }
                    })
                }
                composable("onboarding") {
                    OnboardingScreen(onFinished = {
                        Store.updateSettings { it.copy(onboarded = true) }
                        val target = if (Store.settings.value.engineSetupDone) "home" else "setup"
                        nav.navigate(target) {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    })
                }
                composable("setup") {
                    EngineSetupScreen(onFinished = {
                        nav.navigate("home") {
                            popUpTo("setup") { inclusive = true }
                        }
                    })
                }
                composable("home") {
                    HomeScreen(nav = nav)
                }
                composable("active") {
                    ActiveDownloadsScreen(nav = nav)
                }
                composable("files") {
                    FileManagerScreen(nav = nav)
                }
                composable(
                    route = "browser?url={url}",
                    arguments = listOf(navArgument("url") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    })
                ) { entry ->
                    val url = entry.arguments?.getString("url")
                    BrowserScreen(
                        nav = nav,
                        initialUrl = url,
                        onFullscreenChange = { browserFullscreen = it },
                    )
                }
                composable("browser") {
                    BrowserScreen(
                        nav = nav,
                        onFullscreenChange = { browserFullscreen = it },
                    )
                }
                composable("settings") {
                    SettingsScreen(nav = nav)
                }
                composable("add") {
                    AddDownloadScreen(nav = nav)
                }
                composable("analyzer/{url}") { entry ->
                    val url = entry.arguments?.getString("url").orEmpty()
                    UrlAnalyzerScreen(nav = nav, url = url)
                }
                composable("quality") {
                    QualityScreen(nav = nav)
                }
                composable("details/{id}") { entry ->
                    DownloadDetailsScreen(
                        nav = nav,
                        id = entry.arguments?.getString("id").orEmpty(),
                    )
                }
                composable("about") {
                    AboutScreen(nav = nav)
                }
                composable("support") {
                    SupportScreen(nav = nav)
                }
            }

            if (showTabs) {
                FloatingTabBar(
                    currentRoute = activeRoute,
                    onSelect = { target -> nav.navigateTopLevel(target) },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }

            if (showStarPrompt) {
                val days = remember { StarPrompt.daysInUse(context) }
                AlertDialog(
                    onDismissRequest = { showStarPrompt = false },
                    containerColor = colors.surfaceElevated,
                    titleContentColor = colors.textPrimary,
                    textContentColor = colors.textSecondary,
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(colors.primary.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = null,
                                    tint = colors.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text("Enjoying oP Downloader?", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        }
                    },
                    text = {
                        Column {
                            Text(
                                if (days >= 3) {
                                    "You have been using it for $days days. If it saves you time, a star on GitHub helps other people find the project."
                                } else {
                                    "If oP Downloader saves you time, a star on GitHub helps other people find the project."
                                },
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Don't ask again",
                                color = colors.textMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        StarPrompt.markDone(context)
                                        showStarPrompt = false
                                    }
                                    .padding(horizontal = 2.dp, vertical = 6.dp),
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                StarPrompt.markDone(context)
                                showStarPrompt = false
                                openLink(context, APP_GITHUB_REPO)
                            },
                        ) {
                            Text("Star on GitHub", color = colors.primary, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showStarPrompt = false }) {
                            Text("Later", color = colors.textSecondary)
                        }
                    },
                )
            }
        }
    }
}

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun FloatingTabBar(
    currentRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val selectedIndex = tabs.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    val fabSize = 52.dp

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 14.dp, top = 16.dp),
    ) {
        val slotWidth = maxWidth / tabs.size
        val fabX by animateDpAsState(
            targetValue = slotWidth * selectedIndex + (slotWidth - fabSize) / 2,
            animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
            label = "tabFabX",
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(colors.primaryDeep),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                val selected = currentRoute == tab.route
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSelect(tab.route) }
                        .padding(top = 4.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(modifier = Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                        if (!selected) {
                            Icon(
                                tab.icon,
                                contentDescription = tab.label,
                                tint = colors.onPrimaryDeep.copy(alpha = 0.82f),
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        tab.label,
                        color = if (selected) colors.onPrimaryDeep else colors.onPrimaryDeep.copy(alpha = 0.82f),
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .offset(x = fabX, y = (-5).dp)
                .size(fabSize)
                .shadow(12.dp, CircleShape)
                .clip(CircleShape)
                .background(colors.onPrimaryDeep),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = selectedIndex,
                transitionSpec = {
                    (fadeIn(tween(180)) + scaleIn(initialScale = 0.72f, animationSpec = tween(220)))
                        .togetherWith(fadeOut(tween(110)) + scaleOut(targetScale = 0.72f, animationSpec = tween(110)))
                },
                contentAlignment = Alignment.Center,
                label = "tabFabIcon",
            ) { index ->
                Icon(
                    tabs[index].icon,
                    contentDescription = tabs[index].label,
                    tint = colors.primaryDeep,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}
