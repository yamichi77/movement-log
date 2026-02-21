package com.yamichi77.movement_log.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.yamichi77.movement_log.R
import com.yamichi77.movement_log.ui.history.HistoryMapUiPoint
import com.yamichi77.movement_log.ui.history.HistoryMapUiState
import com.yamichi77.movement_log.ui.history.HistoryMapViewModel
import com.yamichi77.movement_log.ui.theme.MovementlogTheme
import com.yamichi77.movement_log.ui.theme.Spacing

@Composable
fun HistoryMapScreen(
    viewModel: HistoryMapViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryMapScreenContent(uiState = uiState)
}

@Composable
private fun HistoryMapScreenContent(
    uiState: HistoryMapUiState,
) {
    val mapPoints = remember(uiState.points) {
        uiState.points.map { point ->
            LatLng(point.latitude, point.longitude)
        }
    }
    val lastPoint = remember(uiState.lastLatitude, uiState.lastLongitude) {
        if (uiState.lastLatitude == null || uiState.lastLongitude == null) {
            null
        } else {
            LatLng(uiState.lastLatitude, uiState.lastLongitude)
        }
    }
    val cameraPositionState = rememberCameraPositionState()

    LaunchedEffect(lastPoint, uiState.logCount) {
        if (lastPoint != null) {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(lastPoint, 15f)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = stringResource(R.string.history_map_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.history_map_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                if (!uiState.isMapEnabled) {
                    MapPlaceholder(
                        message = stringResource(R.string.history_map_map_unavailable),
                        detail = stringResource(R.string.history_map_api_key_hint),
                    )
                } else if (mapPoints.isEmpty()) {
                    MapPlaceholder(
                        message = stringResource(R.string.history_map_empty_title),
                        detail = stringResource(R.string.history_map_empty_detail),
                    )
                } else {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
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
            }
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(Spacing.md)) {
                Text(
                    text = stringResource(R.string.history_map_stats_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(R.string.history_map_last_point, uiState.lastPointText),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.history_map_log_count, uiState.logCount),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.history_map_last_updated, uiState.lastUpdatedText),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MapPlaceholder(
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
private fun HistoryMapScreenPreview() {
    MovementlogTheme {
        HistoryMapScreenContent(
            uiState = HistoryMapUiState(
                points = listOf(
                    HistoryMapUiPoint(latitude = 35.6812, longitude = 139.7671),
                    HistoryMapUiPoint(latitude = 35.6814, longitude = 139.7674),
                    HistoryMapUiPoint(latitude = 35.6818, longitude = 139.7678),
                ),
                logCount = 3,
                lastLatitude = 35.6818,
                lastLongitude = 139.7678,
                lastPointText = "35.6818, 139.7678",
                lastUpdatedText = "2026-02-15 14:32:10",
                isMapEnabled = true,
            ),
        )
    }
}
