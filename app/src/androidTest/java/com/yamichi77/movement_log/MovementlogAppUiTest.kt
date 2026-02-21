package com.yamichi77.movement_log

import android.Manifest
import androidx.annotation.StringRes
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.yamichi77.movement_log.data.repository.ConnectionSettingsRepository
import com.yamichi77.movement_log.data.repository.ConnectionSettingsRepositoryProvider
import com.yamichi77.movement_log.data.repository.ConnectivityTestResult
import com.yamichi77.movement_log.data.settings.ConnectionSettings
import com.yamichi77.movement_log.data.tracking.TrackingStateStore
import com.yamichi77.movement_log.ui.screen.CONNECTION_SETTINGS_BASE_URL_FIELD_TAG
import com.yamichi77.movement_log.ui.screen.CONNECTION_SETTINGS_BICYCLE_INTERVAL_FIELD_TAG
import com.yamichi77.movement_log.ui.screen.CONNECTION_SETTINGS_RUNNING_INTERVAL_FIELD_TAG
import com.yamichi77.movement_log.ui.screen.CONNECTION_SETTINGS_STILL_INTERVAL_FIELD_TAG
import com.yamichi77.movement_log.ui.screen.CONNECTION_SETTINGS_UPLOAD_PATH_FIELD_TAG
import com.yamichi77.movement_log.ui.screen.CONNECTION_SETTINGS_VEHICLE_INTERVAL_FIELD_TAG
import com.yamichi77.movement_log.ui.screen.CONNECTION_SETTINGS_WALKING_INTERVAL_FIELD_TAG
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MovementlogAppUiTest {
    private val fakeConnectionSettingsRepository = UiTestConnectionSettingsRepository()

    init {
        ConnectionSettingsRepositoryProvider.setForTesting(fakeConnectionSettingsRepository)
    }

    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.ACTIVITY_RECOGNITION,
    )

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @After
    fun tearDown() {
        ConnectionSettingsRepositoryProvider.setForTesting(null)
    }

    @Test
    fun topLevelNavigation_movesAcrossAllScreens() {
        waitForText(R.string.home_latest_location)

        navigateToMenuAndAssertScreen(
            menuResId = R.string.menu_history_map,
            screenMarkerResId = R.string.history_map_stats_title,
        )
        navigateToMenuAndAssertScreen(
            menuResId = R.string.menu_log_table,
            screenMarkerResId = R.string.log_table_title,
        )
        navigateToMenuAndAssertScreen(
            menuResId = R.string.menu_connection_settings,
            screenMarkerResId = R.string.connection_settings_title,
        )
        navigateToMenuAndAssertScreen(
            menuResId = R.string.menu_home,
            screenMarkerResId = R.string.home_latest_location,
        )
    }

    @Test
    fun primaryOperations_homeToggleAndConnectionSave() {
        waitForText(R.string.home_start_collecting)
        waitForText(R.string.home_stop_collecting)

        composeRule.runOnIdle {
            TrackingStateStore.setCollecting(false)
        }

        waitForEnabledTextButton(R.string.home_start_collecting)
        composeRule.onNodeWithText(string(R.string.home_start_collecting)).performClick()

        waitForEnabledTextButton(R.string.home_stop_collecting)
        composeRule.onNodeWithText(string(R.string.home_stop_collecting)).performClick()

        waitForEnabledTextButton(R.string.home_start_collecting)

        navigateToMenuAndAssertScreen(
            menuResId = R.string.menu_connection_settings,
            screenMarkerResId = R.string.connection_settings_title,
        )
        ensureConnectionSettingsSectionExpanded()
        ensureTrackingFrequencySectionExpanded()

        composeRule.onNodeWithTag(CONNECTION_SETTINGS_BASE_URL_FIELD_TAG).performTextReplacement("https://10.0.2.2")
        composeRule.onNodeWithTag(CONNECTION_SETTINGS_UPLOAD_PATH_FIELD_TAG).performTextReplacement("/api/movelog")
        composeRule.onNodeWithTag(CONNECTION_SETTINGS_WALKING_INTERVAL_FIELD_TAG).performTextReplacement("31")
        composeRule.onNodeWithTag(CONNECTION_SETTINGS_RUNNING_INTERVAL_FIELD_TAG).performTextReplacement("26")
        composeRule.onNodeWithTag(CONNECTION_SETTINGS_BICYCLE_INTERVAL_FIELD_TAG).performTextReplacement("21")
        composeRule.onNodeWithTag(CONNECTION_SETTINGS_VEHICLE_INTERVAL_FIELD_TAG).performTextReplacement("16")
        composeRule.onNodeWithTag(CONNECTION_SETTINGS_STILL_INTERVAL_FIELD_TAG).performTextReplacement("901")

        composeRule.onNodeWithText(string(R.string.connection_settings_save)).performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun stateRestoration_keepsConnectionSettingsAfterActivityRecreate() {
        waitForText(R.string.home_latest_location)

        navigateToMenuAndAssertScreen(
            menuResId = R.string.menu_connection_settings,
            screenMarkerResId = R.string.connection_settings_title,
        )
        ensureConnectionSettingsSectionExpanded()
        ensureTrackingFrequencySectionExpanded()

        val expectedBaseUrl = "https://10.0.2.15"
        val expectedUploadPath = "/api/restore-test"
        val expectedWalkingInterval = "33"
        val expectedStillInterval = "903"

        composeRule.onNodeWithTag(CONNECTION_SETTINGS_BASE_URL_FIELD_TAG).performTextReplacement(expectedBaseUrl)
        composeRule.onNodeWithTag(CONNECTION_SETTINGS_UPLOAD_PATH_FIELD_TAG).performTextReplacement(expectedUploadPath)
        composeRule.onNodeWithTag(CONNECTION_SETTINGS_WALKING_INTERVAL_FIELD_TAG)
            .performTextReplacement(expectedWalkingInterval)
        composeRule.onNodeWithTag(CONNECTION_SETTINGS_STILL_INTERVAL_FIELD_TAG)
            .performTextReplacement(expectedStillInterval)

        composeRule.onNodeWithText(string(R.string.connection_settings_save)).performClick()
        composeRule.waitForIdle()

        composeRule.activityRule.scenario.recreate()

        waitForText(R.string.connection_settings_title)
        ensureConnectionSettingsSectionExpanded()
        ensureTrackingFrequencySectionExpanded()
        composeRule.onNodeWithTag(CONNECTION_SETTINGS_BASE_URL_FIELD_TAG)
            .assertTextContains(expectedBaseUrl)
        composeRule.onNodeWithTag(CONNECTION_SETTINGS_UPLOAD_PATH_FIELD_TAG)
            .assertTextContains(expectedUploadPath)
        composeRule.onNodeWithTag(CONNECTION_SETTINGS_WALKING_INTERVAL_FIELD_TAG)
            .assertTextContains(expectedWalkingInterval)
        composeRule.onNodeWithTag(CONNECTION_SETTINGS_STILL_INTERVAL_FIELD_TAG)
            .assertTextContains(expectedStillInterval)
    }

    @Test
    fun connectionSettings_connectivityValidation_showsInputErrors() {
        waitForText(R.string.home_latest_location)

        navigateToMenuAndAssertScreen(
            menuResId = R.string.menu_connection_settings,
            screenMarkerResId = R.string.connection_settings_title,
        )
        ensureConnectionSettingsSectionExpanded()

        composeRule.onNodeWithTag(CONNECTION_SETTINGS_BASE_URL_FIELD_TAG).performTextClearance()
        composeRule.onNodeWithTag(CONNECTION_SETTINGS_UPLOAD_PATH_FIELD_TAG).performTextReplacement("invalid-path")

        composeRule.onNodeWithText(string(R.string.connection_settings_connectivity_test)).performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun connectionSettings_connectivityTest_showsSuccessMessage() {
        waitForText(R.string.home_latest_location)
        navigateToMenuAndAssertScreen(
            menuResId = R.string.menu_connection_settings,
            screenMarkerResId = R.string.connection_settings_title,
        )
        ensureConnectionSettingsSectionExpanded()

        composeRule.onNodeWithTag(CONNECTION_SETTINGS_BASE_URL_FIELD_TAG)
            .performTextReplacement("https://portal.yamichi.test")
        composeRule.onNodeWithTag(CONNECTION_SETTINGS_UPLOAD_PATH_FIELD_TAG)
            .performTextReplacement("/api/movelog")

        composeRule.onNodeWithText(string(R.string.connection_settings_connectivity_test))
            .performClick()
        composeRule.waitForIdle()
        waitForText(R.string.connection_settings_title)
    }

    @Test
    fun homeMapPreview_tapNavigatesToHistoryMap() {
        waitForText(R.string.home_latest_location)

        composeRule.onNodeWithTag("home_map_preview_card", useUnmergedTree = true).performClick()

        waitForText(R.string.history_map_stats_title)
    }

    private fun navigateToMenuAndAssertScreen(
        @StringRes menuResId: Int,
        @StringRes screenMarkerResId: Int,
    ) {
        val menuLabel = string(menuResId)
        clickTopLevelMenu(menuLabel)
        waitForText(screenMarkerResId)
    }

    private fun clickTopLevelMenu(menuLabel: String) {
        val candidates = listOf(
            hasAnyDescendant(hasContentDescription(menuLabel)) and hasClickAction(),
            hasAnyDescendant(hasText(menuLabel)) and hasClickAction(),
            hasContentDescription(menuLabel) and hasClickAction(),
            hasText(menuLabel) and hasClickAction(),
            hasContentDescription(menuLabel),
            hasText(menuLabel),
        )

        for (matcher in candidates) {
            if (clickFirstNodeIfExists(matcher, useUnmergedTree = true)) return
            if (clickFirstNodeIfExists(matcher, useUnmergedTree = false)) return
        }

        throw AssertionError("Top level menu not found: $menuLabel")
    }

    private fun clickFirstNodeIfExists(
        matcher: SemanticsMatcher,
        useUnmergedTree: Boolean,
    ): Boolean {
        if (!hasNode(matcher, useUnmergedTree = useUnmergedTree)) {
            return false
        }
        return runCatching {
            composeRule.onAllNodes(matcher, useUnmergedTree = useUnmergedTree).onFirst().performClick()
            true
        }.getOrDefault(false)
    }

    private fun waitForEnabledTextButton(@StringRes resId: Int) {
        val targetText = string(resId)
        val matcher = hasText(targetText) and hasClickAction() and isEnabled()
        waitForNode(matcher)
        composeRule.waitUntil(7_000) {
            runCatching {
                composeRule.onAllNodesWithText(targetText).fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
    }

    private fun waitForText(@StringRes resId: Int) {
        val targetText = string(resId)
        waitForNode(hasText(targetText))
        composeRule.waitUntil(7_000) {
            runCatching {
                composeRule.onAllNodesWithText(targetText).fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
    }

    private fun ensureConnectionSettingsSectionExpanded() {
        ensureSectionExpanded(
            sectionTitle = string(R.string.connection_settings_title),
            requiredTag = CONNECTION_SETTINGS_BASE_URL_FIELD_TAG,
        )
    }

    private fun ensureTrackingFrequencySectionExpanded() {
        ensureSectionExpanded(
            sectionTitle = string(R.string.tracking_frequency_section_title),
            requiredTag = CONNECTION_SETTINGS_WALKING_INTERVAL_FIELD_TAG,
        )
    }

    private fun ensureSectionExpanded(
        sectionTitle: String,
        requiredTag: String,
    ) {
        if (hasNodeWithTag(requiredTag, useUnmergedTree = true) ||
            hasNodeWithTag(requiredTag, useUnmergedTree = false)
        ) {
            return
        }

        val sectionMatcher = hasAnyDescendant(hasText(sectionTitle)) and hasClickAction()
        val clicked = clickFirstNodeIfExists(sectionMatcher, useUnmergedTree = true) ||
            clickFirstNodeIfExists(sectionMatcher, useUnmergedTree = false)
        if (!clicked) {
            throw AssertionError("Expandable section not found: $sectionTitle")
        }

        composeRule.waitUntil(7_000) {
            hasNodeWithTag(requiredTag, useUnmergedTree = true) ||
                hasNodeWithTag(requiredTag, useUnmergedTree = false)
        }
    }

    private fun waitForNode(
        matcher: SemanticsMatcher,
        timeoutMillis: Long = 7_000,
        useUnmergedTree: Boolean = false,
    ) {
        composeRule.waitUntil(timeoutMillis) {
            hasNode(matcher, useUnmergedTree = useUnmergedTree)
        }
    }

    private fun hasNode(
        matcher: SemanticsMatcher,
        useUnmergedTree: Boolean,
    ): Boolean = runCatching {
        composeRule.onAllNodes(matcher, useUnmergedTree = useUnmergedTree)
            .fetchSemanticsNodes()
            .isNotEmpty()
    }.getOrDefault(false)

    private fun hasNodeWithTag(
        tag: String,
        useUnmergedTree: Boolean,
    ): Boolean = runCatching {
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = useUnmergedTree)
            .fetchSemanticsNodes()
            .isNotEmpty()
    }.getOrDefault(false)

    private fun string(@StringRes resId: Int): String = composeRule.activity.getString(resId)

    private class UiTestConnectionSettingsRepository : ConnectionSettingsRepository {
        val settingsState = MutableStateFlow(ConnectionSettings.Default)
        private val sendStatusState = MutableStateFlow("")

        override val settings: Flow<ConnectionSettings> = settingsState
        override val sendStatusText: Flow<String> = sendStatusState

        override suspend fun save(settings: ConnectionSettings) {
            settingsState.value = settings
        }

        override suspend fun saveSendStatusText(text: String) {
            sendStatusState.value = text
        }

        override suspend fun testConnectivity(settings: ConnectionSettings): ConnectivityTestResult {
            settingsState.value = settings
            return ConnectivityTestResult(sessionRotated = true)
        }
    }
}
