package com.yamichi77.movement_log.ui.screen

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.yamichi77.movement_log.R
import com.yamichi77.movement_log.ui.home.HomeUiState
import com.yamichi77.movement_log.ui.home.HomeViewModel
import com.yamichi77.movement_log.ui.theme.MovementlogTheme
import com.yamichi77.movement_log.ui.theme.Spacing

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onOpenHistoryMap: () -> Unit = {},
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreenContent(
        uiState = uiState.value,
        onOpenHistoryMap = onOpenHistoryMap,
        onStartCollecting = viewModel::onStartCollectingClick,
        onStopCollecting = viewModel::onStopCollectingClick,
    )
}

@Composable
private fun HomeScreenContent(
    uiState: HomeUiState,
    onOpenHistoryMap: () -> Unit,
    onStartCollecting: () -> Unit,
    onStopCollecting: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = stringResource(R.string.menu_home),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.home_overview_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(Spacing.md)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Icon(
                        imageVector = Icons.Default.FiberManualRecord,
                        contentDescription = null,
                        modifier = Modifier.size(Spacing.iconSmall),
                        tint = if (uiState.isCollecting) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    )
                    Text(
                        text = if (uiState.isCollecting) {
                            stringResource(R.string.home_status_collecting)
                        } else {
                            stringResource(R.string.home_status_stopped)
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(R.string.home_activity_status, uiState.activityStatus),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.home_last_updated, uiState.lastUpdatedText),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(Spacing.md)) {
                Text(
                    text = stringResource(R.string.home_latest_location),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(R.string.home_latitude, uiState.latitudeText),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.home_longitude, uiState.longitudeText),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(R.string.home_log_count, uiState.logCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        HomeMapPreviewCard(
            uiState = uiState,
            onOpenHistoryMap = onOpenHistoryMap,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Button(
                onClick = onStartCollecting,
                enabled = !uiState.isCollecting,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.home_start_collecting))
            }
            FilledTonalButton(
                onClick = onStopCollecting,
                enabled = uiState.isCollecting,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.home_stop_collecting))
            }
        }
    }
}

@Composable
private fun HomeMapPreviewCard(
    uiState: HomeUiState,
    onOpenHistoryMap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mapPoints = remember(uiState.mapPreviewPoints) {
        uiState.mapPreviewPoints.map { point ->
            LatLng(point.latitude, point.longitude)
        }
    }
    val lastPoint = remember(uiState.lastPreviewLatitude, uiState.lastPreviewLongitude) {
        if (uiState.lastPreviewLatitude == null || uiState.lastPreviewLongitude == null) {
            null
        } else {
            LatLng(uiState.lastPreviewLatitude, uiState.lastPreviewLongitude)
        }
    }
    val cameraPositionState = rememberCameraPositionState()
    val mapUiSettings = remember {
        MapUiSettings(
            zoomControlsEnabled = false,
            scrollGesturesEnabled = false,
            zoomGesturesEnabled = false,
            tiltGesturesEnabled = false,
            rotationGesturesEnabled = false,
            mapToolbarEnabled = false,
            myLocationButtonEnabled = false,
        )
    }

    LaunchedEffect(lastPoint, mapPoints.size) {
        if (lastPoint != null) {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(lastPoint, 15f)
        }
    }

    OutlinedCard(modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (!uiState.isMapEnabled) {
                HomeMapPlaceholder(
                    message = stringResource(R.string.history_map_map_unavailable),
                    detail = stringResource(R.string.history_map_api_key_hint),
                )
            } else if (mapPoints.isEmpty()) {
                HomeMapPlaceholder(
                    message = stringResource(R.string.history_map_empty_title),
                    detail = stringResource(R.string.history_map_empty_detail),
                )
            } else {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    uiSettings = mapUiSettings,
                ) {
                    if (mapPoints.size >= 2) {
                        Polyline(
                            points = mapPoints,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (lastPoint != null) {
                        Marker(
                            state = MarkerState(position = lastPoint),
                            title = stringResource(R.string.history_map_last_point_label),
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(HOME_MAP_PREVIEW_TEST_TAG)
                    .clickable(onClick = onOpenHistoryMap),
            )
        }
    }
}

@Composable
private fun HomeMapPlaceholder(
    message: String,
    detail: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.Map,
            contentDescription = null,
            modifier = Modifier.size(Spacing.iconLarge),
            tint = MaterialTheme.colorScheme.outline,
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    MovementlogTheme {
        HomeScreenContent(
            uiState = HomeUiState(
                isCollecting = true,
                activityStatus = stringResource(R.string.activity_status_walking),
                latitudeText = "35.6812",
                longitudeText = "139.7671",
                lastUpdatedText = "2026-02-15 18:00:00",
                logCount = 128,
            ),
            onOpenHistoryMap = {},
            onStartCollecting = {},
            onStopCollecting = {},
        )
    }
}

const val HOME_MAP_PREVIEW_TEST_TAG: String = "home_map_preview_card"
