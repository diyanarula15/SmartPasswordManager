// DEV SCAFFOLDING ONLY — not part of the deliverable. Developer 1's real project replaces this.
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "legenddrry-vault-harness"

// Temporary stand-in for Developer 1's `:core` module (frozen contract only).
include(":core")
project(":core").projectDir = file("mock-core")

// The real deliverable lives outside the harness.
include(":feature-vault")
project(":feature-vault").projectDir = file("../feature-vault")

// Throwaway test application.
include(":sample-app")
