package com.yamichi77.movement_log.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yamichi77.movement_log.R
import com.yamichi77.movement_log.ui.connection.ConnectionSettingsUiState
import com.yamichi77.movement_log.ui.connection.ConnectionSettingsViewModel
import com.yamichi77.movement_log.ui.theme.MovementlogTheme
import com.yamichi77.movement_log.ui.theme.Spacing

@Composable
fun ConnectionSettingsScreen(
    viewModel: ConnectionSettingsViewModel = viewModel(),
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()
    ConnectionSettingsScreenContent(
        uiState = uiState.value,
        onBaseUrlChanged = viewModel::onBaseUrlChanged,
        onUploadPathChanged = viewModel::onUploadPathChanged,
        onWalkingIntervalChanged = viewModel::onWalkingIntervalChanged,
        onRunningIntervalChanged = viewModel::onRunningIntervalChanged,
        onBicycleIntervalChanged = viewModel::onBicycleIntervalChanged,
        onVehicleIntervalChanged = viewModel::onVehicleIntervalChanged,
        onStillIntervalChanged = viewModel::onStillIntervalChanged,
        onConnectivityTest = viewModel::onConnectivityTestClick,
        onSave = viewModel::onSaveClick,
    )
}

@Composable
private fun ConnectionSettingsScreenContent(
    uiState: ConnectionSettingsUiState,
    onBaseUrlChanged: (String) -> Unit,
    onUploadPathChanged: (String) -> Unit,
    onWalkingIntervalChanged: (String) -> Unit,
    onRunningIntervalChanged: (String) -> Unit,
    onBicycleIntervalChanged: (String) -> Unit,
    onVehicleIntervalChanged: (String) -> Unit,
    onStillIntervalChanged: (String) -> Unit,
    onConnectivityTest: () -> Unit,
    onSave: () -> Unit,
) {
    var isConnectionExpanded by rememberSaveable { mutableStateOf(false) }
    var isTrackingFrequencyExpanded by rememberSaveable { mutableStateOf(false) }

    val sendStatusText = if (uiState.sendStatusText.isBlank()) {
        stringResource(R.string.connection_settings_send_status_initial)
    } else {
        uiState.sendStatusText
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        ExpandableSettingsSection(
            title = stringResource(R.string.connection_settings_title),
            description = stringResource(R.string.connection_settings_description),
            expanded = isConnectionExpanded,
            onExpandedChange = { isConnectionExpanded = it },
        ) {
            OutlinedTextField(
                value = uiState.baseUrl,
                onValueChange = onBaseUrlChanged,
                label = { Text(stringResource(R.string.connection_settings_base_url)) },
                isError = uiState.baseUrlError != null,
                supportingText = { uiState.baseUrlError?.let { Text(text = it) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(CONNECTION_SETTINGS_BASE_URL_FIELD_TAG),
                singleLine = true,
            )
            OutlinedTextField(
                value = uiState.uploadPath,
                onValueChange = onUploadPathChanged,
                label = { Text(stringResource(R.string.connection_settings_upload_path)) },
                isError = uiState.uploadPathError != null,
                supportingText = { uiState.uploadPathError?.let { Text(text = it) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(CONNECTION_SETTINGS_UPLOAD_PATH_FIELD_TAG),
                singleLine = true,
            )
            OutlinedButton(
                onClick = onConnectivityTest,
                enabled = !uiState.isTestingConnectivity && !uiState.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(imageVector = Icons.Default.NetworkCheck, contentDescription = null)
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text(
                    if (uiState.isTestingConnectivity) {
                        stringResource(R.string.connection_settings_connectivity_testing)
                    } else {
                        stringResource(R.string.connection_settings_connectivity_test)
                    },
                )
            }

            if (uiState.connectivityResult == ConnectionSettingsUiState.ConnectivityResult.Success) {
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            uiState.connectivityMessage
                                ?: stringResource(R.string.connection_settings_connectivity_success),
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.NetworkCheck,
                            contentDescription = null,
                        )
                    },
                )
            }

            if (uiState.connectivityResult == ConnectionSettingsUiState.ConnectivityResult.Error) {
                Text(
                    text = uiState.connectivityMessage ?: stringResource(
                        R.string.connection_settings_connectivity_failed,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        ExpandableSettingsSection(
            title = stringResource(R.string.tracking_frequency_section_title),
            description = stringResource(R.string.tracking_frequency_section_description),
            expanded = isTrackingFrequencyExpanded,
            onExpandedChange = { isTrackingFrequencyExpanded = it },
        ) {
            IntervalInputField(
                labelResId = R.string.tracking_frequency_walking_label,
                testTag = CONNECTION_SETTINGS_WALKING_INTERVAL_FIELD_TAG,
                value = uiState.walkingIntervalInput,
                error = uiState.walkingIntervalError,
                onValueChange = onWalkingIntervalChanged,
            )
            IntervalInputField(
                labelResId = R.string.tracking_frequency_running_label,
                testTag = CONNECTION_SETTINGS_RUNNING_INTERVAL_FIELD_TAG,
                value = uiState.runningIntervalInput,
                error = uiState.runningIntervalError,
                onValueChange = onRunningIntervalChanged,
            )
            IntervalInputField(
                labelResId = R.string.tracking_frequency_bicycle_label,
                testTag = CONNECTION_SETTINGS_BICYCLE_INTERVAL_FIELD_TAG,
                value = uiState.bicycleIntervalInput,
                error = uiState.bicycleIntervalError,
                onValueChange = onBicycleIntervalChanged,
            )
            IntervalInputField(
                labelResId = R.string.tracking_frequency_vehicle_label,
                testTag = CONNECTION_SETTINGS_VEHICLE_INTERVAL_FIELD_TAG,
                value = uiState.vehicleIntervalInput,
                error = uiState.vehicleIntervalError,
                onValueChange = onVehicleIntervalChanged,
            )
            IntervalInputField(
                labelResId = R.string.tracking_frequency_still_label,
                testTag = CONNECTION_SETTINGS_STILL_INTERVAL_FIELD_TAG,
                value = uiState.stillIntervalInput,
                error = uiState.stillIntervalError,
                onValueChange = onStillIntervalChanged,
            )
        }

        HorizontalDivider()

        Button(
            onClick = onSave,
            enabled = !uiState.isSaving && !uiState.isTestingConnectivity,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(imageVector = Icons.Default.Save, contentDescription = null)
            Spacer(modifier = Modifier.width(Spacing.xs))
            Text(stringResource(R.string.connection_settings_save))
        }

        when (uiState.saveResult) {
            ConnectionSettingsUiState.SaveResult.None -> Unit
            ConnectionSettingsUiState.SaveResult.Success -> {
                Text(
                    text = stringResource(R.string.connection_settings_save_success),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            ConnectionSettingsUiState.SaveResult.Error -> {
                Text(
                    text = stringResource(R.string.connection_settings_save_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        HorizontalDivider()

        Text(
            text = stringResource(R.string.connection_settings_send_status_title),
            style = MaterialTheme.typography.titleSmall,
        )
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(Spacing.md)) {
                Text(
                    text = sendStatusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ExpandableSettingsSection(
    title: String,
    description: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandedChange(!expanded) }
                    .padding(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = if (expanded) {
                        Icons.Default.KeyboardArrowUp
                    } else {
                        Icons.Default.KeyboardArrowDown
                    },
                    contentDescription = null,
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    content = content,
                )
            }
        }
    }
}

@Composable
private fun IntervalInputField(
    labelResId: Int,
    testTag: String,
    value: String,
    error: String?,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(
                stringResource(
                    R.string.tracking_frequency_label_with_unit,
                    stringResource(labelResId),
                    stringResource(R.string.tracking_frequency_unit_seconds),
                ),
            )
        },
        isError = error != null,
        supportingText = { error?.let { Text(it) } },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

@Preview(showBackground = true)
@Composable
private fun ConnectionSettingsScreenPreview() {
    MovementlogTheme {
        ConnectionSettingsScreenContent(
            uiState = ConnectionSettingsUiState(
                baseUrl = "https://portal.yamichi.com",
                uploadPath = "/api/movelog",
                walkingIntervalInput = "30",
                runningIntervalInput = "25",
                bicycleIntervalInput = "20",
                vehicleIntervalInput = "15",
                stillIntervalInput = "900",
                sendStatusText = stringResource(R.string.connection_settings_send_status_initial),
            ),
            onBaseUrlChanged = {},
            onUploadPathChanged = {},
            onWalkingIntervalChanged = {},
            onRunningIntervalChanged = {},
            onBicycleIntervalChanged = {},
            onVehicleIntervalChanged = {},
            onStillIntervalChanged = {},
            onConnectivityTest = {},
            onSave = {},
        )
    }
}

const val CONNECTION_SETTINGS_BASE_URL_FIELD_TAG = "connection_settings_base_url_field"
const val CONNECTION_SETTINGS_UPLOAD_PATH_FIELD_TAG = "connection_settings_upload_path_field"
const val CONNECTION_SETTINGS_WALKING_INTERVAL_FIELD_TAG = "connection_settings_walking_interval_field"
const val CONNECTION_SETTINGS_RUNNING_INTERVAL_FIELD_TAG = "connection_settings_running_interval_field"
const val CONNECTION_SETTINGS_BICYCLE_INTERVAL_FIELD_TAG = "connection_settings_bicycle_interval_field"
const val CONNECTION_SETTINGS_VEHICLE_INTERVAL_FIELD_TAG = "connection_settings_vehicle_interval_field"
const val CONNECTION_SETTINGS_STILL_INTERVAL_FIELD_TAG = "connection_settings_still_interval_field"
