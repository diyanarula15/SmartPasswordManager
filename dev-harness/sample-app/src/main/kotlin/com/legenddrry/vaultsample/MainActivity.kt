// DEV SCAFFOLDING ONLY. Developer 1 owns the real app shell and navigation.
package com.legenddrry.vaultsample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.legenddrry.feature.vault.EntriesRoute
import com.legenddrry.feature.vault.GenerateRoute

class MainActivity : ComponentActivity() {
    private val repository by lazy { InMemoryVaultRepository.demo() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    var tab by rememberSaveable { mutableIntStateOf(0) }
                    val repo = remember { repository }
                    Column(Modifier.fillMaxSize().statusBarsPadding()) {
                        TabRow(selectedTabIndex = tab) {
                            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Vault") }, modifier = Modifier.testTag("harness_tab_vault"))
                            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Generate") }, modifier = Modifier.testTag("harness_tab_generate"))
                        }
                        Box(Modifier.weight(1f)) {
                            if (tab == 0) EntriesRoute(repo) else GenerateRoute(repo)
                        }
                    }
                }
            }
        }
    }
}
