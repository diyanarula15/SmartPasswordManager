// Owner: shell worker. SKELETON STUB.
package com.example.group4app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.group4app.navigation.LegenDDRRYApp
import com.example.group4app.ui.theme.SmartPasswordManagerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as LegenDDRRYApplication).container
        setContent {
            SmartPasswordManagerTheme {
                LegenDDRRYApp(container)
            }
        }
    }
}
