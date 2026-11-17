package com.example.group4app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.group4app.navigation.LegenDDRRYApp
import com.example.group4app.security.ScreenSecurity
import com.example.group4app.security.SecurityServices

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Before any content exists: no screenshots, screen recording or recents thumbnails.
        ScreenSecurity.apply(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as LegenDDRRYApplication).container
        val security = SecurityServices.get(this, container)
        // Auto-lock checks on every onStart/onStop of this activity.
        lifecycle.addObserver(security.autoLock)
        // Clipboard clearing is process-wide; start() is idempotent.
        security.clipboardGuard.start()

        // LegenDDRRYApp applies the theme (Settings → Theme) and system-bar icon contrast itself.
        setContent { LegenDDRRYApp(container) }
    }
}
