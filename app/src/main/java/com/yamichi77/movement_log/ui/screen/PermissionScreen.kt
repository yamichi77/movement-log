package com.yamichi77.movement_log.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.yamichi77.movement_log.R
import com.yamichi77.movement_log.permission.PermissionStatusItem
import com.yamichi77.movement_log.ui.theme.MovementlogTheme
import com.yamichi77.movement_log.ui.theme.Spacing

@Composable
fun PermissionScreen(
    permissionItems: List<PermissionStatusItem>,
    onRequestPermissions: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(
            imageVector = Icons.Default.Security,
            contentDescription = null,
            modifier = Modifier.size(Spacing.iconLarge),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(Spacing.md))
        Text(
            text = stringResource(R.string.permission_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.permission_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(vertical = Spacing.xs)) {
                permissionItems.forEach { item ->
                    ListItem(
                        modifier = Modifier.fillMaxWidth(),
                        headlineContent = { Text(stringResource(item.labelResId)) },
                        supportingContent = { Text(item.permission) },
                        trailingContent = {
                            if (item.granted) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = stringResource(R.string.permission_granted),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Cancel,
                                    contentDescription = stringResource(R.string.permission_not_granted),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.xs))
        Button(
            onClick = onRequestPermissions,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.permission_request))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PermissionScreenPreview() {
    MovementlogTheme {
        PermissionScreen(
            permissionItems = listOf(
                PermissionStatusItem(
                    labelResId = R.string.permission_fine_location,
                    permission = "android.permission.ACCESS_FINE_LOCATION",
                    granted = true,
                ),
                PermissionStatusItem(
                    labelResId = R.string.permission_coarse_location,
                    permission = "android.permission.ACCESS_COARSE_LOCATION",
                    granted = false,
                ),
                PermissionStatusItem(
                    labelResId = R.string.permission_notifications,
                    permission = "android.permission.POST_NOTIFICATIONS",
                    granted = false,
                ),
                PermissionStatusItem(
                    labelResId = R.string.permission_activity_recognition,
                    permission = "android.permission.ACTIVITY_RECOGNITION",
                    granted = false,
                ),
            ),
            onRequestPermissions = {},
        )
    }
}
