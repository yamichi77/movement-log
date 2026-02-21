package com.yamichi77.movement_log

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Browser
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.yamichi77.movement_log.data.auth.AuthNavigationEvent
import com.yamichi77.movement_log.data.auth.AuthNavigationEventBus
import com.yamichi77.movement_log.data.auth.AuthSessionStatusRepositoryProvider
import com.yamichi77.movement_log.data.auth.AuthSessionStoreProvider
import com.yamichi77.movement_log.data.repository.ConnectionSettingsRepositoryProvider
import com.yamichi77.movement_log.data.settings.ConnectionSettings
import com.yamichi77.movement_log.data.sync.AuthKeepAliveSchedulerProvider
import com.yamichi77.movement_log.navigation.AppRoute
import com.yamichi77.movement_log.navigation.TopLevelDestination
import com.yamichi77.movement_log.permission.PermissionUtils
import com.yamichi77.movement_log.ui.permission.PermissionViewModel
import com.yamichi77.movement_log.ui.screen.ConnectionSettingsScreen
import com.yamichi77.movement_log.ui.screen.HistoryMapScreen
import com.yamichi77.movement_log.ui.screen.HomeScreen
import com.yamichi77.movement_log.ui.screen.LogTableScreen
import com.yamichi77.movement_log.ui.screen.PermissionScreen
import com.yamichi77.movement_log.ui.theme.MovementlogTheme
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleAuthCallback(intent)
        enableEdgeToEdge()
        setContent { MovementlogTheme { MovementlogApp() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthCallback(intent)
    }

    private fun handleAuthCallback(intent: Intent?) {
        val callbackUri = intent?.data ?: return
        Log.d("MainActivity", "handleAuthCallback uri=$callbackUri")
        val accessToken = callbackUri.accessTokenFromCallback() ?: return
        Log.d("MainActivity", "access token received from callback")
        AuthSessionStoreProvider.get().setAccessToken(accessToken)
        AuthKeepAliveSchedulerProvider.get(applicationContext).start()
        lifecycleScope.launch {
            AuthSessionStatusRepositoryProvider.get(applicationContext)
                .markSessionEstablished()
        }
        AuthNavigationEventBus.clear()
    }
}

@Composable
fun MovementlogApp() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = currentBackStackEntry?.destination
    val permissionViewModel: PermissionViewModel = viewModel()
    val permissionUiState by permissionViewModel.uiState.collectAsStateWithLifecycle()
    val hasRequiredPermissions = permissionUiState.hasRequiredPermissions

    val connectionSettingsRepository = remember(context) {
        ConnectionSettingsRepositoryProvider.get(context.applicationContext)
    }
    val connectionSettings by connectionSettingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = ConnectionSettings.Default,
    )
    val authNavigationEvent by AuthNavigationEventBus.event.collectAsStateWithLifecycle()
    val suppressAuthBrowserLaunch = remember { isRunningInstrumentedTest() }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionViewModel.refreshPermissionState()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionViewModel.refreshPermissionState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(hasRequiredPermissions, currentDestination) {
        val isPermissionRoute = currentDestination?.hasRoute(AppRoute.Permission::class) == true
        if (hasRequiredPermissions && isPermissionRoute) {
            navController.navigate(AppRoute.Home) {
                launchSingleTop = true
                popUpTo(AppRoute.Permission) { inclusive = true }
            }
        } else if (!hasRequiredPermissions && !isPermissionRoute) {
            navController.navigate(AppRoute.Permission) {
                launchSingleTop = true
                popUpTo(AppRoute.Home) { inclusive = true }
            }
        }
    }

    LaunchedEffect(authNavigationEvent, connectionSettings.baseUrl) {
        val event = authNavigationEvent as? AuthNavigationEvent.RequireLogin ?: return@LaunchedEffect
        if (suppressAuthBrowserLaunch) {
            Log.d("MainActivity", "skip auth browser launch during instrumented tests")
            AuthNavigationEventBus.clear()
            return@LaunchedEffect
        }
        val loginBaseUrl = event.baseUrl?.takeIf { it.isNotBlank() } ?: connectionSettings.baseUrl
        val loginUri = buildLoginUri(loginBaseUrl)
        if (loginUri == null) {
            Log.w("MainActivity", "loginUri is null. baseUrl=$loginBaseUrl")
            AuthNavigationEventBus.clear()
            return@LaunchedEffect
        }
        Log.d("MainActivity", "launch login uri=$loginUri")
        launchLoginBrowser(context, loginUri)
        AuthNavigationEventBus.clear()
    }

    val shouldShowTopLevelNavigation = hasRequiredPermissions &&
        (currentDestination?.hasRoute(AppRoute.Permission::class) != true)

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            if (shouldShowTopLevelNavigation) {
                TopLevelDestination.entries.forEach { destination ->
                    val selected = currentDestination?.hasRoute(destination.route::class) == true
                    item(
                        icon = {
                            Icon(
                                destination.icon,
                                contentDescription = stringResource(destination.labelResId),
                            )
                        },
                        label = { Text(stringResource(destination.labelResId)) },
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                navController.navigate(destination.route) {
                                    launchSingleTop = true
                                    restoreState = true
                                    popUpTo(AppRoute.Home) { saveState = true }
                                }
                            }
                        },
                    )
                }
            }
        },
    ) {
        Box(modifier = Modifier.statusBarsPadding()) {
            NavHost(
                navController = navController,
                startDestination = if (hasRequiredPermissions) AppRoute.Home else AppRoute.Permission,
            ) {
                composable<AppRoute.Permission> {
                    PermissionScreen(
                        permissionItems = permissionUiState.permissionItems,
                        onRequestPermissions = {
                            permissionLauncher.launch(PermissionUtils.requiredPermissions())
                        },
                    )
                }
                composable<AppRoute.Home> {
                    HomeScreen(
                        onOpenHistoryMap = {
                            navController.navigate(AppRoute.HistoryMap) {
                                launchSingleTop = true
                                restoreState = true
                                popUpTo(AppRoute.Home) { saveState = true }
                            }
                        },
                    )
                }
                composable<AppRoute.HistoryMap> { HistoryMapScreen() }
                composable<AppRoute.LogTable> { LogTableScreen() }
                composable<AppRoute.ConnectionSettings> { ConnectionSettingsScreen() }
            }
        }
    }
}

private fun buildLoginUri(baseUrl: String): Uri? {
    val normalized = baseUrl.trim()
        .ifBlank { return null }
        .let { raw ->
            if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"
        }
    val parsedBase = normalized.toHttpUrlOrNull() ?: return null
    val loginUrl = parsedBase.newBuilder()
        .addEncodedPathSegments("api/auth/login")
        .addQueryParameter("redirect_uri", AuthCallbackUri)
        .build()
    return Uri.parse(loginUrl.toString())
}

private fun Uri.accessTokenFromCallback(): String? {
    TokenParamCandidates.forEach { key ->
        getQueryParameter(key)?.takeIf { it.isNotBlank() }?.let { return it }
    }

    val fragmentValue = fragment ?: return null
    val fragmentPairs = fragmentValue.split("&")
        .asSequence()
        .mapNotNull { part ->
            val separatorIndex = part.indexOf("=")
            if (separatorIndex <= 0) return@mapNotNull null
            val key = part.substring(0, separatorIndex)
            val value = part.substring(separatorIndex + 1)
            key to Uri.decode(value)
        }
    TokenParamCandidates.forEach { tokenKey ->
        fragmentPairs.firstOrNull { (key, value) -> key == tokenKey && value.isNotBlank() }
            ?.second
            ?.let { return it }
    }

    return fragmentValue
        .takeIf { value -> value.isNotBlank() && !value.contains("=") && looksLikeToken(value) }
}

private fun looksLikeToken(value: String): Boolean {
    val trimmed = value.trim()
    if (trimmed.length >= 20) return true
    return trimmed.count { it == '.' } >= 2
}

private fun launchLoginBrowser(context: android.content.Context, loginUri: Uri) {
    val headers = Bundle().apply {
        putString("Referer", AuthCallbackUri)
    }
    runCatching {
        CustomTabsIntent.Builder()
            .build()
            .also { it.intent.putExtra(Browser.EXTRA_HEADERS, headers) }
            .launchUrl(context, loginUri)
    }.onFailure {
        Log.w("MainActivity", "CustomTabs launch failed, fallback to ACTION_VIEW", it)
        val intent = Intent(Intent.ACTION_VIEW, loginUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(Browser.EXTRA_HEADERS, headers)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { fallbackError ->
                Log.e("MainActivity", "Browser launch failed", fallbackError)
            }
    }
}

private fun isRunningInstrumentedTest(): Boolean = runCatching {
    val clazz = Class.forName("androidx.test.platform.app.InstrumentationRegistry")
    clazz.getMethod("getInstrumentation").invoke(null)
    true
}.getOrDefault(false)

private const val AuthCallbackUri = "movementlog://auth/callback"
private val TokenParamCandidates = listOf(
    "access_token",
    "token",
    "id_token",
    "jwt",
)
