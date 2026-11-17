package com.legenddrry.feature.tools.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legenddrry.feature.tools.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ToolPage(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxSize().background(colors.background)) {
        val margin = if (maxWidth >= 600.dp) 24.dp else 16.dp
        CompositionLocalProvider(LocalContentColor provides colors.onBackground) {
            ProvideTextStyle(ToolsType.body) {
                Column(
                    Modifier.align(Alignment.TopCenter).widthIn(max = 720.dp).fillMaxHeight().fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(horizontal = margin).padding(top = 24.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("LEGENDDRRY / TOOLS", style = ToolsType.overline, color = colors.onSurfaceVariant)
                        Text(title, style = ToolsType.headline, modifier = Modifier.semantics { heading() })
                        Text(subtitle, style = ToolsType.body, color = colors.onSurfaceVariant)
                    }
                    content()
                }
            }
        }
    }
}

@Composable
fun ToolCard(content: @Composable ColumnScope.() -> Unit) = ToolCardSurface(null, content)

@Composable
fun ToolCard(onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) = ToolCardSurface(onClick, content)

@Composable
private fun ToolCardSurface(onClick: (() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface)
    val body: @Composable ColumnScope.() -> Unit = {
        ProvideTextStyle(ToolsType.body) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
    if (onClick == null) {
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = colors,
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp), content = body)
    } else {
        Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = colors,
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp), content = body)
    }
}

@Composable
fun EmptyToolState(title: String, description: String) {
    ToolCard {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ToolIconTile("shield")
            Text(title, style = ToolsType.title, color = MaterialTheme.colorScheme.onSurface)
            Text(description, style = ToolsType.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val iconCodePoints = mapOf(
    "shield" to 0xE8E8, "devices" to 0xE1B1, "laptop" to 0xE30C, "phone" to 0xE32C,
    "location" to 0xE55F, "schedule" to 0xE8B5, "check" to 0xE5CA, "close" to 0xE5CD,
    "history" to 0xE889, "mail" to 0xE0BE, "copy" to 0xE14D, "delete" to 0xE872,
    "add" to 0xE145, "search" to 0xE8B6, "info" to 0xE88E, "lock" to 0xE897,
    "visibility" to 0xE8F4, "settings" to 0xE8B8, "light" to 0xE518, "dark" to 0xE51C,
    "system" to 0xE1AB, "chevron" to 0xE5CC, "arrow" to 0xE5C8, "warning" to 0xE002,
    "error" to 0xE000, "refresh" to 0xE5D5, "tune" to 0xE429, "key" to 0xE73C,
    "help" to 0xE887, "pause" to 0xE034, "check_circle" to 0xE86C, "logout" to 0xE9BA,
)

/** Genuine, bundled Material Symbols Rounded. Decorative unless a description is supplied. */
@OptIn(ExperimentalTextApi::class)
@Composable
fun ToolIcon(
    name: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    size: Dp = 24.dp,
    description: String? = null,
    filled: Boolean = false,
) {
    val family = remember(filled) {
        FontFamily(Font(R.font.tools_material_symbols_rounded,
            variationSettings = FontVariation.Settings(FontVariation.Setting("FILL", if (filled) 1f else 0f), FontVariation.Setting("wght", 400f), FontVariation.Setting("opsz", 24f))))
    }
    val iconTextSize = with(LocalDensity.current) { size.toSp() }
    Box(modifier.size(size).clearAndSetSemantics {
        if (description != null) { contentDescription = description; role = Role.Image }
    }, contentAlignment = Alignment.Center) {
        Text(String(Character.toChars(iconCodePoints[name] ?: iconCodePoints.getValue("info"))),
            fontFamily = family, fontSize = iconTextSize, lineHeight = iconTextSize, color = tint)
    }
}

@Composable
fun ToolIconTile(name: String) {
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
        ToolIcon(name, tint = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
fun ToolSectionTitle(title: String, supporting: String? = null) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = ToolsType.titleLarge, modifier = Modifier.semantics { heading() })
        if (supporting != null) Text(supporting, style = ToolsType.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ToolLabel(text: String, accent: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(8.dp), color = if (accent) colors.secondaryContainer else colors.primaryContainer,
        contentColor = if (accent) colors.onSecondaryContainer else colors.onPrimaryContainer) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = ToolsType.caption)
    }
}

@Composable
fun ToolNotice(title: String, message: String, icon: String = "info") {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ToolIcon(icon, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = ToolsType.label)
                Text(message, style = ToolsType.body)
            }
        }
    }
}

@Composable
fun ToolFeedback(message: String, isError: Boolean = false, onDismiss: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    Surface(
        Modifier.fillMaxWidth().animateContentSize(tween(150)).semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(14.dp),
        color = if (isError) colors.errorContainer else colors.primaryContainer,
        contentColor = if (isError) colors.onErrorContainer else colors.onPrimaryContainer,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ToolIcon(if (isError) "error" else "check_circle", tint = if (isError) colors.onErrorContainer else colors.onPrimaryContainer)
                Text(message, Modifier.weight(1f), style = ToolsType.body)
            }
            if (onDismiss != null) ToolActionButton("Dismiss", onDismiss, kind = ToolButtonKind.TEXT)
        }
    }
}

enum class ToolButtonKind { PRIMARY, TONAL, OUTLINED, TEXT }

@Composable
fun ToolActionButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, icon: String? = null, kind: ToolButtonKind = ToolButtonKind.PRIMARY,
) {
    val colors = MaterialTheme.colorScheme
    val body: @Composable RowScope.() -> Unit = {
        if (icon != null) { ToolIcon(icon, tint = LocalContentColor.current, size = 18.dp); Spacer(Modifier.width(8.dp)) }
        Text(text, style = ToolsType.label)
    }
    val target = modifier.heightIn(min = 52.dp)
    val padding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
    when (kind) {
        ToolButtonKind.PRIMARY -> Button(onClick, target, enabled, shape = CircleShape, colors = ButtonDefaults.buttonColors(disabledContainerColor = colors.surfaceVariant, disabledContentColor = colors.onSurfaceVariant), contentPadding = padding, content = body)
        ToolButtonKind.TONAL -> FilledTonalButton(onClick, target, enabled, shape = CircleShape, colors = ButtonDefaults.filledTonalButtonColors(containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer, disabledContainerColor = colors.surfaceVariant, disabledContentColor = colors.onSurfaceVariant), contentPadding = padding, content = body)
        ToolButtonKind.OUTLINED -> OutlinedButton(onClick, target, enabled, shape = CircleShape, colors = ButtonDefaults.outlinedButtonColors(disabledContainerColor = colors.surfaceVariant, disabledContentColor = colors.onSurfaceVariant), contentPadding = padding, content = body)
        ToolButtonKind.TEXT -> TextButton(onClick, target, enabled, shape = CircleShape, colors = ButtonDefaults.textButtonColors(disabledContentColor = colors.onSurfaceVariant), contentPadding = padding, content = body)
    }
}

@Composable
fun ToolTextField(
    value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier,
    supportingText: String? = null, isError: Boolean = false, enabled: Boolean = true,
    leadingIcon: String? = null, search: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    var focused by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val shape = if (search) CircleShape else RoundedCornerShape(14.dp)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = ToolsType.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))
        OutlinedTextField(
            value, onValueChange,
            modifier.fillMaxWidth().heightIn(min = 56.dp).onFocusChanged { focused = it.isFocused }.semantics { contentDescription = label },
            enabled = enabled, singleLine = true, isError = isError, shape = shape,
            textStyle = ToolsType.bodyLarge,
            keyboardOptions = KeyboardOptions(imeAction = if (search) ImeAction.Search else ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }, onSearch = { keyboard?.hide() }),
            leadingIcon = if (leadingIcon != null) ({ ToolIcon(leadingIcon, tint = if (focused) colors.primary else colors.onSurfaceVariant) }) else null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.surface, unfocusedContainerColor = colors.surfaceVariant,
                disabledContainerColor = colors.surfaceVariant, errorContainerColor = colors.surfaceVariant,
                unfocusedBorderColor = Color.Transparent, focusedBorderColor = colors.primary,
                errorBorderColor = colors.error, disabledBorderColor = Color.Transparent,
                focusedTextColor = colors.onSurface, unfocusedTextColor = colors.onSurface,
            ),
        )
        if (supportingText != null) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (isError) ToolIcon("error", tint = colors.error, size = 18.dp)
            Text(supportingText, style = ToolsType.caption, color = if (isError) colors.error else colors.onSurfaceVariant)
        }
    }
}

@Composable
fun ToolDialog(
    title: String, onDismiss: () -> Unit, confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit = {}, content: @Composable ColumnScope.() -> Unit,
) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val alpha by animateFloatAsState(if (entered) 1f else 0f, tween(275), label = "dialogContent")
    AlertDialog(
        onDismissRequest = onDismiss, shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text(title, style = ToolsType.titleLarge) },
        text = {
            ProvideTextStyle(ToolsType.body) {
                Column(Modifier.verticalScroll(rememberScrollState()).graphicsLayer { this.alpha = alpha },
                    verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
            }
        },
        confirmButton = confirmButton, dismissButton = dismissButton,
    )
}

fun formatToolTimestamp(epochMs: Long): String = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")
    .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(epochMs))
