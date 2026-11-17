package com.legenddrry.feature.tools.approvals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.legenddrry.feature.tools.components.EmptyToolState
import com.legenddrry.feature.tools.components.ToolCard
import com.legenddrry.feature.tools.components.ToolPage
import com.legenddrry.feature.tools.components.formatToolTimestamp
import com.legenddrry.feature.tools.contracts.ApprovalStatus
import com.legenddrry.feature.tools.contracts.ApprovalsRepository
import com.legenddrry.feature.tools.contracts.LoginRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun ApprovalFeature(repository: ApprovalsRepository) {
    val scope = rememberCoroutineScope()
    val state = remember(repository, scope) { ApprovalStateHolder(repository, scope) }
    val requests by state.requests.collectAsStateWithLifecycle()
    val busy by state.busy.collectAsStateWithLifecycle()
    val feedback by state.feedback.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val pending = requests.filter { it.status == ApprovalStatus.PENDING }
    val history = requests.filter { it.status != ApprovalStatus.PENDING }
        .sortedByDescending { it.resolvedAtEpochMs ?: it.requestedAtEpochMs }

    LaunchedEffect(state) {
        while (isActive) {
            state.refreshExpirations()
            delay(1_000)
        }
    }

    ToolPage("Login Approvals", "Review requests and your recent decisions.") {
        ToolCard {
            Text("Local demonstration", style = MaterialTheme.typography.titleMedium)
            Text("Demo requests are simulated on this device. Approving one does not authorize a website or another device. Locations are sample data.")
            Button(
                onClick = state::createDemoRequest,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().testTag("create_demo_request"),
            ) { Text("Create Demo Login Request") }
        }

        feedback?.let { message ->
            ToolCard {
                Text(message, Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("approval_feedback"))
                TextButton(onClick = state::dismissFeedback) { Text("Dismiss") }
            }
        }

        Text("Pending requests (${pending.size})", style = MaterialTheme.typography.titleLarge)
        if (pending.isEmpty()) {
            EmptyToolState("No pending requests", "Create a demo login request to try approving or denying it.")
        } else {
            pending.forEach { request ->
                RequestCard(request, busy, { selectedId = request.id },
                    { state.approve(request.id) }, { state.deny(request.id) })
            }
        }

        Text("Recent history (${history.size})", style = MaterialTheme.typography.titleLarge)
        if (history.isEmpty()) {
            EmptyToolState("No decisions yet", "Approved, denied, and expired requests will appear here.")
        } else {
            history.forEach { request ->
                RequestCard(request, busy, { selectedId = request.id },
                    { state.approve(request.id) }, { state.deny(request.id) })
            }
        }
    }

    requests.firstOrNull { it.id == selectedId }?.let { request ->
        AlertDialog(
            onDismissRequest = { selectedId = null },
            title = { Text("Login request details") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(request.deviceName, style = MaterialTheme.typography.titleMedium)
                    Text(request.approximateLocation)
                    Text("Requested: ${formatToolTimestamp(request.requestedAtEpochMs)}")
                    Text("Expires: ${formatToolTimestamp(request.expiresAtEpochMs)}")
                    Text("Status: ${request.status.label()}")
                    request.resolvedAtEpochMs?.let { Text("Updated: ${formatToolTimestamp(it)}") }
                    Text("Request ID: ${request.id}")
                    Text(if (request.isDemo) "Local simulation — no external authorization." else "Supplied by your connected login request provider.")
                }
            },
            confirmButton = { TextButton(onClick = { selectedId = null }) { Text("Close") } },
        )
    }
}

@Composable
private fun RequestCard(
    request: LoginRequest,
    busy: Boolean,
    onDetails: () -> Unit,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
) {
    ToolCard {
        Text(request.deviceName, style = MaterialTheme.typography.titleMedium)
        Text(if (request.isDemo) "Demo request • ${request.status.label()}" else request.status.label(),
            style = MaterialTheme.typography.labelLarge)
        Text(request.approximateLocation)
        Text(formatToolTimestamp(request.requestedAtEpochMs), style = MaterialTheme.typography.bodySmall)
        if (request.status == ApprovalStatus.PENDING) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onApprove, enabled = !busy,
                    modifier = Modifier.weight(1f).testTag("approve_${request.id}")) { Text("Approve") }
                OutlinedButton(onClick = onDeny, enabled = !busy,
                    modifier = Modifier.weight(1f).testTag("deny_${request.id}")) { Text("Deny") }
            }
        }
        TextButton(onClick = onDetails, modifier = Modifier.testTag("request_details_${request.id}")) { Text("View details") }
    }
}

private fun ApprovalStatus.label(): String = name.lowercase().replaceFirstChar { it.uppercaseChar() }
