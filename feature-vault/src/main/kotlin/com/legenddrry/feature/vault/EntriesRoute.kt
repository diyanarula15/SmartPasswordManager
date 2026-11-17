// OWNER: worker "entries-ui". FROZEN PUBLIC SIGNATURE — do not change. (Skeleton stub; replace body.)
package com.legenddrry.feature.vault

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.legenddrry.core.VaultRepository
import com.legenddrry.feature.vault.state.VaultViewModel
import com.legenddrry.feature.vault.ui.common.VaultTestTags

@Composable
fun EntriesRoute(repository: VaultRepository) {
    val vm: VaultViewModel = viewModel(
        key = "legenddrry-vault-entries",
        factory = viewModelFactory { initializer { VaultViewModel(repository) } },
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    Column(Modifier.testTag(VaultTestTags.ENTRIES_SCREEN)) {
        state.visibleEntries.forEach { Text(it.title) }
    }
}
