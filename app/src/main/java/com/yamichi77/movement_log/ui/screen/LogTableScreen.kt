package com.yamichi77.movement_log.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TableRows
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yamichi77.movement_log.R
import com.yamichi77.movement_log.ui.logtable.LogTableUiItem
import com.yamichi77.movement_log.ui.logtable.LogTableUiState
import com.yamichi77.movement_log.ui.logtable.LogTableViewModel
import com.yamichi77.movement_log.ui.theme.Spacing

private const val TimeColumnWeight = 1.5f
private const val LatitudeColumnWeight = 1f
private const val LongitudeColumnWeight = 1f
private const val ActivityColumnWeight = 1f

@Composable
fun LogTableScreen(
    viewModel: LogTableViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LogTableScreenContent(uiState = uiState)
}

@Composable
private fun LogTableScreenContent(
    uiState: LogTableUiState,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(
            key = "log_table_overview",
            contentType = "overview",
        ) {
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = stringResource(R.string.log_table_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .padding(horizontal = Spacing.md)
                    .semantics { heading() },
            )
            Spacer(modifier = Modifier.height(Spacing.xxs))
            Text(
                text = stringResource(R.string.log_table_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.md),
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = stringResource(R.string.log_table_display_count, uiState.displayedCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.log_table_total_count, uiState.totalCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(Spacing.xs))
        }

        stickyHeader(
            key = "log_table_header",
            contentType = "header",
        ) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                        .semantics { heading() },
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(
                        text = stringResource(R.string.log_table_header_time),
                        modifier = Modifier.weight(TimeColumnWeight),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.log_table_header_latitude),
                        modifier = Modifier.weight(LatitudeColumnWeight),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.log_table_header_longitude),
                        modifier = Modifier.weight(LongitudeColumnWeight),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.log_table_header_activity),
                        modifier = Modifier.weight(ActivityColumnWeight),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                HorizontalDivider()
            }
        }

        if (uiState.items.isEmpty()) {
            item(
                key = "log_table_empty",
                contentType = "empty",
            ) {
                Spacer(modifier = Modifier.height(Spacing.xl))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Icon(
                        imageVector = Icons.Default.TableRows,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.log_table_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(
                items = uiState.items,
                key = { it.stableId },
                contentType = { "log_item" },
            ) { entry ->
                LogTableRow(entry = entry)
                HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.md))
            }
        }
    }
}

@Composable
private fun LogTableRow(entry: LogTableUiItem) {
    val rowDescription = stringResource(
        R.string.log_table_row_content_description,
        entry.timeText,
        entry.latitudeText,
        entry.longitudeText,
        entry.activityStatusText,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
            .semantics(mergeDescendants = true) {
                contentDescription = rowDescription
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = entry.timeText,
            modifier = Modifier.weight(TimeColumnWeight),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = entry.latitudeText,
            modifier = Modifier.weight(LatitudeColumnWeight),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = entry.longitudeText,
            modifier = Modifier.weight(LongitudeColumnWeight),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = entry.activityStatusText,
            modifier = Modifier.weight(ActivityColumnWeight),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LogTableScreenPreview() {
    LogTableScreenContent(
        uiState = LogTableUiState(
            items = listOf(
                LogTableUiItem(
                    stableId = 1L,
                    timeText = "2026-02-15 14:32:10",
                    latitudeText = "35.6812",
                    longitudeText = "139.7671",
                    activityStatusText = stringResource(R.string.activity_status_walking),
                ),
                LogTableUiItem(
                    stableId = 2L,
                    timeText = "2026-02-15 14:31:40",
                    latitudeText = "35.6810",
                    longitudeText = "139.7669",
                    activityStatusText = stringResource(R.string.activity_status_still),
                ),
            ),
            displayedCount = 2,
            totalCount = 2,
        ),
    )
}
