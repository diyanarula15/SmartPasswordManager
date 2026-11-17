package com.legenddrry.feature.tools.approvals

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.legenddrry.feature.tools.components.EmptyToolState
import com.legenddrry.feature.tools.components.ToolActionButton
import com.legenddrry.feature.tools.components.ToolButtonKind
import com.legenddrry.feature.tools.components.ToolCard
import com.legenddrry.feature.tools.components.ToolDialog
import com.legenddrry.feature.tools.components.ToolFeedback
import com.legenddrry.feature.tools.components.ToolIcon
import com.legenddrry.feature.tools.components.ToolIconTile
import com.legenddrry.feature.tools.components.ToolLabel
import com.legenddrry.feature.tools.components.ToolPage
import com.legenddrry.feature.tools.components.ToolSectionTitle
import com.legenddrry.feature.tools.components.ToolsType
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
    val feedbackIsError by state.feedbackIsError.collectAsStateWithLifecycle()
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

    ToolPage("Login approvals", "Review sign-ins and your recent decisions.") {
        ToolCard {
            // The screen's single warm accent belongs to the demo disclosure.
            ToolLabel("Local demonstration", accent = true)
            Text(
                "Local demo only. Approvals do not sign in to a website or another device. Locations are sample data.",
                style = ToolsType.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ToolActionButton(
                text = "Create demo login request",
                onClick = state::createDemoRequest,
                enabled = !busy,
                icon = "add",
                kind = ToolButtonKind.TONAL,
                modifier = Modifier.fillMaxWidth().testTag("create_demo_request"),
            )
        }

        feedback?.let { message ->
            Box(Modifier.testTag("approval_feedback")) {
                ToolFeedback(message, feedbackIsError, state::dismissFeedback)
            }
        }

        ToolSectionTitle("Pending requests (${pending.size})", "Only approve a sign-in you recognise.")
        if (pending.isEmpty()) {
            EmptyToolState("No pending requests", "Create a demo login request to try approving or denying it.")
        } else {
            pending.forEach { request ->
                RequestCard(request, busy, { selectedId = request.id },
                    { state.approve(request.id) }, { state.deny(request.id) })
            }
        }

        ToolSectionTitle("Recent history (${history.size})", "A record of completed and expired requests.")
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
        ToolDialog(
            title = "Login request details",
            onDismiss = { selectedId = null },
            confirmButton = {
                ToolActionButton("Close", { selectedId = null }, kind = ToolButtonKind.TEXT)
            },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                ToolIconTile(deviceIcon(request.deviceName))
                Text(request.deviceName, style = ToolsType.title, color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f))
            }
            RequestStatus(request)
            RequestDetail("Approximate location", request.approximateLocation, "location")
            RequestDetail("Requested", formatToolTimestamp(request.requestedAtEpochMs), "schedule")
            RequestDetail("Expires", formatToolTimestamp(request.expiresAtEpochMs), "schedule")
            // Preserve this explicit text for screen readers and existing host test selectors.
            Text("Status: ${request.status.label()}", style = ToolsType.body)
            request.resolvedAtEpochMs?.let { RequestDetail("Updated", formatToolTimestamp(it), "history") }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Request ID", style = ToolsType.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(request.id, style = ToolsType.caption, color = MaterialTheme.colorScheme.onSurface)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolIcon("info", size = 18.dp)
                Text(
                    if (request.isDemo) "Local simulation — no external authorization."
                    else "Supplied by your connected login request provider.",
                    style = ToolsType.body,
                )
            }
        }
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
    ToolCard(onClick = onDetails) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ToolIconTile(deviceIcon(request.deviceName))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(request.deviceName, style = ToolsType.title, color = MaterialTheme.colorScheme.onSurface)
                Text(formatToolTimestamp(request.requestedAtEpochMs), style = ToolsType.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        RequestStatus(request)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ToolIcon("location", size = 18.dp)
            Text(request.approximateLocation, style = ToolsType.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (request.status == ApprovalStatus.PENDING) {
            ApprovalActions(request.id, busy, onApprove, onDeny)
        }
        ToolActionButton("View details", onDetails, kind = ToolButtonKind.TEXT, icon = "arrow",
            modifier = Modifier.testTag("request_details_${request.id}"))
    }
}

@Composable
private fun ApprovalActions(id: String, busy: Boolean, onApprove: () -> Unit, onDeny: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 280.dp || LocalDensity.current.fontScale > 1.2f) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolActionButton("Approve", onApprove, enabled = !busy, icon = "check",
                    modifier = Modifier.fillMaxWidth().testTag("approve_$id"))
                ToolActionButton("Deny", onDeny, enabled = !busy, icon = "close", kind = ToolButtonKind.OUTLINED,
                    modifier = Modifier.fillMaxWidth().testTag("deny_$id"))
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ToolActionButton("Approve", onApprove, enabled = !busy, icon = "check",
                    modifier = Modifier.weight(1f).testTag("approve_$id"))
                ToolActionButton("Deny", onDeny, enabled = !busy, icon = "close", kind = ToolButtonKind.OUTLINED,
                    modifier = Modifier.weight(1f).testTag("deny_$id"))
            }
        }
    }
}

/** Status has both words and an icon; clay is never used to communicate a problem. */
@Composable
private fun RequestStatus(request: LoginRequest) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(
        when (request.status) {
            ApprovalStatus.APPROVED -> colors.primaryContainer
            ApprovalStatus.DENIED -> colors.errorContainer
            ApprovalStatus.PENDING, ApprovalStatus.EXPIRED -> colors.surfaceVariant
        }, tween(150), label = "approvalStatusContainer",
    )
    val foreground by animateColorAsState(
        when (request.status) {
            ApprovalStatus.APPROVED -> colors.onPrimaryContainer
            ApprovalStatus.DENIED -> colors.onErrorContainer
            ApprovalStatus.PENDING, ApprovalStatus.EXPIRED -> colors.onSurfaceVariant
        }, tween(150), label = "approvalStatusForeground",
    )
    val icon = when (request.status) {
        ApprovalStatus.PENDING -> "schedule"
        ApprovalStatus.APPROVED -> "check_circle"
        ApprovalStatus.DENIED -> "close"
        ApprovalStatus.EXPIRED -> "history"
    }
    Surface(shape = RoundedCornerShape(8.dp), color = container, contentColor = foreground) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            ToolIcon(icon, tint = foreground, size = 18.dp, filled = request.status == ApprovalStatus.APPROVED)
            Text(if (request.isDemo) "Demo request • ${request.status.label()}" else request.status.label(), style = ToolsType.caption)
        }
    }
}

@Composable
private fun RequestDetail(label: String, value: String, icon: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ToolIcon(icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = ToolsType.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = ToolsType.body, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

private fun deviceIcon(name: String): String = if (
    name.contains("phone", ignoreCase = true) || name.contains("mobile", ignoreCase = true)
) "phone" else "laptop"

private fun ApprovalStatus.label(): String = name.lowercase().replaceFirstChar { it.uppercaseChar() }
