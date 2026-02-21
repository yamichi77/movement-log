package com.yamichi77.movement_log.ui.permission

import android.app.Application
import com.yamichi77.movement_log.MainDispatcherRule
import com.yamichi77.movement_log.permission.PermissionStatusItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PermissionViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun refreshPermissionState_updatesUiStateFromRepository() {
        val fakeRepository = FakePermissionStateRepository(
            hasRequiredPermissions = false,
            permissionItems = listOf(
                PermissionStatusItem(
                    labelResId = 1,
                    permission = "permission.initial",
                    granted = false,
                ),
            ),
        )
        val viewModel = PermissionViewModel(
            application = Application(),
            permissionStateRepository = fakeRepository,
        )

        assertFalse(viewModel.uiState.value.hasRequiredPermissions)
        assertEquals(1, viewModel.uiState.value.permissionItems.size)

        fakeRepository.hasRequiredPermissions = true
        fakeRepository.permissionItems = listOf(
            PermissionStatusItem(
                labelResId = 2,
                permission = "permission.updated",
                granted = true,
            ),
        )

        viewModel.refreshPermissionState()

        assertTrue(viewModel.uiState.value.hasRequiredPermissions)
        assertEquals("permission.updated", viewModel.uiState.value.permissionItems.first().permission)
    }

    private class FakePermissionStateRepository(
        var hasRequiredPermissions: Boolean,
        var permissionItems: List<PermissionStatusItem>,
    ) : PermissionStateRepository {
        override fun hasRequiredPermissions(context: android.content.Context): Boolean =
            hasRequiredPermissions

        override fun permissionItems(context: android.content.Context): List<PermissionStatusItem> =
            permissionItems
    }
}
