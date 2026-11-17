package com.example.group4app.ui.shell

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.group4app.navigation.MoreSubScreen

/** The More hub: Security dashboard, Settings, About and "Lock now". */
@Composable
internal fun MoreScreen(onOpen: (MoreSubScreen) -> Unit, onLockNow: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag(AppTestTags.MORE_SCREEN)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "More",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .semantics { heading() },
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = MaterialTheme.shapes.medium,
        ) {
            MoreRow(
                icon = Icons.Outlined.Shield,
                title = "Security dashboard",
                subtitle = "Weak and reused passwords",
                tag = AppTestTags.MORE_SECURITY,
                onClick = { onOpen(MoreSubScreen.SECURITY) },
            )
            HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
            MoreRow(
                icon = Icons.Outlined.Settings,
                title = "Settings",
                subtitle = "Theme, auto-lock, clipboard",
                tag = AppTestTags.MORE_SETTINGS,
                onClick = { onOpen(MoreSubScreen.SETTINGS) },
            )
            HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
            MoreRow(
                icon = Icons.Outlined.Info,
                title = "About",
                subtitle = "What is stored and where",
                tag = AppTestTags.MORE_ABOUT,
                onClick = { onOpen(MoreSubScreen.ABOUT) },
            )
        }
        Spacer(Modifier.height(4.dp))
        FilledTonalButton(
            onClick = onLockNow,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag(AppTestTags.MORE_LOCK),
            shape = CircleShape,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Lock now", style = MaterialTheme.typography.labelLarge)
        }
        Text(
            "Locking removes the vault key from memory. You'll need your master password to get back in.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun MoreRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(tag)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun IconBadge(
    icon: ImageVector,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    content: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
    }
}

/** About: what the app is, what is stored where, and which features are demonstrations. */
@Composable
internal fun AboutScreen() {
    val context = LocalContext.current
    val version = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag(AppTestTags.ABOUT_SCREEN)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Outlined.Shield)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("LegenDDRRY", style = MaterialTheme.typography.headlineMedium)
                Text(
                    listOfNotNull("Password manager", version?.let { "version $it" }).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        AboutCard(
            icon = Icons.Outlined.Lock,
            title = "Your vault stays on this device",
            body = "Entries are encrypted on the device with AES-256-GCM. The encryption key is " +
                "derived from your master password and protected with the Android Keystore. " +
                "Nothing is uploaded, and the master password can't be recovered if you forget it.",
        )
        AboutCard(
            icon = Icons.Outlined.Science,
            title = "Demonstration features",
            body = "Login approvals are a demonstration: there is no real login service behind " +
                "them. Email aliases are generated for show only; they don't receive or forward email.",
            badgeContainer = MaterialTheme.colorScheme.secondaryContainer,
            badgeContent = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        AboutCard(
            icon = Icons.Outlined.Timer,
            title = "Protection the app enforces",
            body = "Auto-lock after the time you choose in Settings, and copied passwords are " +
                "cleared from the clipboard after the delay you choose. Screenshots and screen " +
                "recording of the app are blocked.",
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun AboutCard(
    icon: ImageVector,
    title: String,
    body: String,
    badgeContainer: Color = MaterialTheme.colorScheme.primaryContainer,
    badgeContent: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(Modifier.padding(16.dp)) {
            IconBadge(icon, badgeContainer, badgeContent)
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
