// LOCKED API (owner: shell worker): SetupScreen(auth), UnlockScreen(auth).
package com.example.group4app.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.group4app.ui.shell.AppTestTags
import com.legenddrry.core.auth.AuthManager
import com.legenddrry.core.auth.AuthResult
import com.legenddrry.core.transfer.VaultTransferLimits
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* Passwords are kept in plain `remember` state, never `rememberSaveable`: saved instance state can
 * be written to disk. Fields are cleared after every submit and the CharArray handed to
 * AuthManager is wiped afterwards. */

/** First-time master password creation (password + confirmation). Calls auth.setUp. */
@Composable
fun SetupScreen(auth: AuthManager, modifier: Modifier = Modifier) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val minLength = AuthManager.MIN_MASTER_PASSWORD_LENGTH

    fun submit() {
        if (working) return
        val localError = validateNewMasterPassword(password, confirm)
        if (localError != null) {
            error = localError
            return
        }
        val chars = password.toCharArray()
        password = ""
        confirm = ""
        working = true
        error = null
        focusManager.clearFocus()
        scope.launch {
            val result = callAuth(chars) { auth.setUp(it) }
            working = false
            error = setupErrorMessage(result)
        }
    }

    AuthScaffold(modifier.testTag(AppTestTags.SETUP_SCREEN)) {
        AuthHeader(
            icon = Icons.Outlined.Shield,
            title = "Create your master password",
            subtitle = "It unlocks your vault on this device. It can't be recovered or reset, " +
                "so choose something memorable and keep it safe.",
        )
        MasterPasswordField(
            value = password,
            onValueChange = { password = it; error = null },
            label = "Master password",
            tag = AppTestTags.SETUP_PASSWORD,
            visible = showPassword,
            onToggleVisible = { showPassword = !showPassword },
            enabled = !working,
            isError = error != null,
            supportingText = "At least $minLength characters",
            imeAction = ImeAction.Next,
            onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
        )
        MasterPasswordField(
            value = confirm,
            onValueChange = { confirm = it; error = null },
            label = "Confirm master password",
            tag = AppTestTags.SETUP_CONFIRM,
            visible = showPassword,
            onToggleVisible = { showPassword = !showPassword },
            enabled = !working,
            isError = error != null,
            imeAction = ImeAction.Done,
            onImeAction = { submit() },
        )
        ErrorMessage(error, AppTestTags.SETUP_ERROR)
        PillButton(
            text = "Create vault",
            working = working,
            onClick = { submit() },
            tag = AppTestTags.SETUP_SUBMIT,
        )
    }
}

/** Unlock with the master password. Calls auth.unlock; shows an error on failure. */
@Composable
fun UnlockScreen(auth: AuthManager, modifier: Modifier = Modifier) {
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var lockoutSeconds by remember { mutableIntStateOf(safeLockout(auth)) }
    /** Bumped to (re)start the lockout countdown. */
    var lockoutTick by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(lockoutTick) {
        var remaining = safeLockout(auth)
        lockoutSeconds = remaining
        while (remaining > 0) {
            error = lockoutMessage(remaining)
            delay(1_000)
            remaining = safeLockout(auth)
            lockoutSeconds = remaining
        }
        if (error != null && error!!.startsWith(LOCKOUT_PREFIX)) error = null
    }

    fun submit() {
        if (working || lockoutSeconds > 0) return
        if (password.isEmpty()) {
            error = "Enter your master password"
            return
        }
        val chars = password.toCharArray()
        password = ""
        working = true
        error = null
        scope.launch {
            val result = callAuth(chars) { auth.unlock(it) }
            working = false
            when (result) {
                AuthResult.SUCCESS -> Unit
                AuthResult.TOO_MANY_ATTEMPTS -> {
                    error = if (safeLockout(auth) > 0) lockoutMessage(safeLockout(auth))
                    else "Too many attempts. Wait a moment, then try again."
                    lockoutTick++
                }
                else -> {
                    error = unlockErrorMessage(result)
                    // A wrong attempt may itself start a lockout.
                    if (safeLockout(auth) > 0) lockoutTick++
                }
            }
        }
    }

    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    AuthScaffold(modifier.testTag(AppTestTags.UNLOCK_SCREEN)) {
        AuthHeader(
            icon = Icons.Outlined.Lock,
            title = "Unlock LegenDDRRY",
            subtitle = "Enter your master password to open your vault.",
        )
        MasterPasswordField(
            value = password,
            onValueChange = {
                password = it
                if (lockoutSeconds <= 0) error = null
            },
            label = "Master password",
            tag = AppTestTags.UNLOCK_PASSWORD,
            visible = showPassword,
            onToggleVisible = { showPassword = !showPassword },
            enabled = !working,
            isError = error != null,
            imeAction = ImeAction.Done,
            onImeAction = { submit() },
            modifier = Modifier.focusRequester(focusRequester),
        )
        ErrorMessage(error, AppTestTags.UNLOCK_ERROR)
        PillButton(
            text = if (lockoutSeconds > 0) "Try again in ${lockoutSeconds}s" else "Unlock",
            working = working,
            enabled = lockoutSeconds <= 0,
            onClick = { submit() },
            tag = AppTestTags.UNLOCK_SUBMIT,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Messages

internal fun validateNewMasterPassword(password: String, confirm: String): String? = when {
    password.length < AuthManager.MIN_MASTER_PASSWORD_LENGTH ->
        "Use at least ${AuthManager.MIN_MASTER_PASSWORD_LENGTH} characters."
    password.isBlank() -> "Your master password can't be only spaces."
    password.length > VaultTransferLimits.MAX_PASSWORD_CHARS ->
        "Use ${VaultTransferLimits.MAX_PASSWORD_CHARS} characters or fewer."
    password != confirm -> "The passwords don't match."
    else -> null
}

internal fun setupErrorMessage(result: AuthResult): String? = when (result) {
    AuthResult.SUCCESS -> null
    AuthResult.PASSWORD_TOO_SHORT ->
        "Use at least ${AuthManager.MIN_MASTER_PASSWORD_LENGTH} characters."
    AuthResult.ALREADY_SET_UP -> "A master password already exists on this device."
    else -> "Couldn't create your vault. Please try again."
}

internal fun unlockErrorMessage(result: AuthResult): String? = when (result) {
    AuthResult.SUCCESS -> null
    AuthResult.WRONG_PASSWORD, AuthResult.PASSWORD_TOO_SHORT -> "Incorrect master password"
    AuthResult.TOO_MANY_ATTEMPTS -> "Too many attempts. Wait a moment, then try again."
    AuthResult.NOT_SET_UP -> "No vault exists on this device yet."
    else -> "Couldn't unlock your vault. Please try again."
}

private const val LOCKOUT_PREFIX = "Too many attempts"

private fun lockoutMessage(seconds: Int) =
    "$LOCKOUT_PREFIX. Try again in $seconds ${if (seconds == 1) "second" else "seconds"}."

private fun safeLockout(auth: AuthManager): Int =
    runCatching { auth.lockoutRemainingSeconds() }.getOrDefault(0).coerceAtLeast(0)

internal suspend fun callAuth(credential: CharArray, block: suspend (CharArray) -> AuthResult): AuthResult = try {
    block(credential)
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    AuthResult.ERROR
} finally {
    credential.fill('\u0000')
}

// ---------------------------------------------------------------------------------------------
// Sage & Clay building blocks

@Composable
private fun AuthScaffold(modifier: Modifier, content: @Composable () -> Unit) {
    // safeDrawing includes the IME, so the card moves above the keyboard and stays scrollable.
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val viewportHeight = maxHeight
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = viewportHeight),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Card(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 24.dp)
                    .widthIn(max = 440.dp)
                    .fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun AuthHeader(icon: ImageVector, title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MasterPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    tag: String,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    enabled: Boolean,
    isError: Boolean,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .testTag(tag),
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        isError = isError,
        shape = MaterialTheme.shapes.small,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = imeAction,
            autoCorrectEnabled = false,
        ),
        keyboardActions = KeyboardActions(onAny = { onImeAction() }),
        supportingText = supportingText?.let { { Text(it) } },
        trailingIcon = {
            IconButton(onClick = onToggleVisible) {
                Icon(
                    if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (visible) "Hide password" else "Show password",
                )
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
    )
}

@Composable
private fun ErrorMessage(message: String?, tag: String) {
    if (message == null) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(20.dp),
        )
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier
                .testTag(tag)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun PillButton(
    text: String,
    working: Boolean,
    onClick: () -> Unit,
    tag: String,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !working,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag(tag),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        if (working) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.5.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}
