// OWNER: worker "generator-ui". FROZEN PUBLIC SIGNATURE — do not change. (Skeleton stub; replace body.)
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
import com.legenddrry.feature.vault.state.GeneratorViewModel
import com.legenddrry.feature.vault.ui.common.VaultTestTags

@Composable
fun GenerateRoute(repository: VaultRepository) {
    val vm: GeneratorViewModel = viewModel(
        key = "legenddrry-vault-generator",
        factory = viewModelFactory { initializer { GeneratorViewModel(repository) } },
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    Column(Modifier.testTag(VaultTestTags.GENERATOR_SCREEN)) {
        Text(state.password, Modifier.testTag(VaultTestTags.GENERATOR_PASSWORD))
    }
}
