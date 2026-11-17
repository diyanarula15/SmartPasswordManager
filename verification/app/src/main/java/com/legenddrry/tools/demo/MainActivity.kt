package com.legenddrry.tools.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.legenddrry.feature.tools.*

class MainActivity : ComponentActivity() {
    private val tools = ToolsDependencies.demo()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ProvideTools(tools) {
                LegenDDRRYToolsTheme {
                    var selected by rememberSaveable { mutableIntStateOf(0) }
                    val sections = listOf("Approvals", "Aliases", "Security", "Settings")
                    Scaffold(
                        bottomBar = {
                            NavigationBar {
                                sections.forEachIndexed { index, title ->
                                    NavigationBarItem(
                                        selected = selected == index,
                                        onClick = { selected = index },
                                        icon = { Text(listOf("A", "@", "S", "⚙")[index]) },
                                        label = { Text(title) },
                                    )
                                }
                            }
                        },
                    ) { padding ->
                        Surface(Modifier.fillMaxSize().padding(padding)) {
                            when (selected) {
                                0 -> ApprovalsRoute()
                                1 -> AliasesRoute()
                                2 -> SecurityRoute()
                                else -> SettingsRoute()
                            }
                        }
                    }
                }
            }
        }
    }
}
