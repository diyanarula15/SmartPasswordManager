package com.legenddrry.feature.tools

import androidx.compose.runtime.Composable
import com.legenddrry.feature.tools.approvals.ApprovalFeature
import com.legenddrry.feature.tools.aliases.AliasesFeature
import com.legenddrry.feature.tools.security.SecurityFeature
import com.legenddrry.feature.tools.settings.SettingsFeature

@Composable
fun ApprovalsRoute() = ApprovalFeature(LocalToolsDependencies.current.approvals)

@Composable
fun AliasesRoute() = AliasesFeature(LocalToolsDependencies.current.aliases)

@Composable
fun SecurityRoute() = SecurityFeature(LocalToolsDependencies.current.security)

@Composable
fun SettingsRoute() = SettingsFeature(LocalToolsDependencies.current.settings)
