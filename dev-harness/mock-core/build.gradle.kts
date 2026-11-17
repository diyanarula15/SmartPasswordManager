// DEV SCAFFOLDING ONLY — mirrors Developer 1's frozen `com.legenddrry.core` contract.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.legenddrry.core"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
}
