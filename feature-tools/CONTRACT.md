# Locked integration contract

Shared types are in `com.legenddrry.feature.tools.contracts.ToolContracts.kt`.
Do not change these signatures. Report a proposed change to the orchestrator.

Root owns contracts, components, build, dependency injection, theme wrapper and the
four public routes. Feature owners must only edit their assigned package and tests.

Each feature exposes an internal composable taking its repository explicitly:
- approvals: `ApprovalFeature(repository: ApprovalsRepository)`
- aliases: `AliasesFeature(repository: AliasesRepository)`
- security: `SecurityFeature(provider: SecuritySummaryProvider)`
- settings: `SettingsFeature(repository: SettingsRepository)`

Default implementations (public, for host integration and tests):
- approvals.InMemoryApprovalsRepository
- aliases.InMemoryAliasesRepository
- security.InMemorySecuritySummaryProvider
- settings.InMemorySettingsRepository

All constructors must support no arguments. Approvals should also accept a clock
and seed records for deterministic expiration tests. In-memory mutations should be
atomic/thread-safe. Use immutable lists and StateFlow, UUID IDs, no network.
Settings/security counts can be fake only when clearly labeled demo.

Root supplies `ToolsDependencies`, `ProvideTools` and zero-argument public routes.
The application owns MaterialTheme. The legacy `LegenDDRRYToolsTheme` signature remains
as a deprecated pass-through; palette/theme application is isolated to test/preview code. Feature UI uses MaterialTheme and shared ToolPage,
ToolCard, EmptyToolState, formatToolTimestamp. No global navigation in features.

Use Compose runtime StateFlow collection (`collectAsStateWithLifecycle`),
rememberCoroutineScope or a state holder. Catch/report mutation errors. Avoid
lifecycle-viewmodel-compose (not a dependency). JUnit 4 + runBlocking for repo tests.
Use `Dispatchers.Unconfined` if a state holder test needs a scope without Android.
