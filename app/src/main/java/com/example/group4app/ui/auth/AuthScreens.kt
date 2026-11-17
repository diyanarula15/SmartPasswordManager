// LOCKED API (owner: shell worker). SKELETON STUB.
package com.example.group4app.ui.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.group4app.ui.shell.AppTestTags
import com.legenddrry.core.auth.AuthManager
import kotlinx.coroutines.launch

/** First-time master password creation (password + confirmation). Calls auth.setUp. */
@Composable
fun SetupScreen(auth: AuthManager) {
    var pw by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Column(Modifier.testTag(AppTestTags.SETUP_SCREEN)) {
        OutlinedTextField(pw, { pw = it }, Modifier.testTag(AppTestTags.SETUP_PASSWORD))
        Button({ scope.launch { auth.setUp(pw.toCharArray()) } }, Modifier.testTag(AppTestTags.SETUP_SUBMIT)) { Text("Create vault") }
    }
}

/** Unlock with the master password. Calls auth.unlock; shows an error on failure. */
@Composable
fun UnlockScreen(auth: AuthManager) {
    var pw by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Column(Modifier.testTag(AppTestTags.UNLOCK_SCREEN)) {
        OutlinedTextField(pw, { pw = it }, Modifier.testTag(AppTestTags.UNLOCK_PASSWORD))
        Button({ scope.launch { auth.unlock(pw.toCharArray()) } }, Modifier.testTag(AppTestTags.UNLOCK_SUBMIT)) { Text("Unlock") }
    }
}
