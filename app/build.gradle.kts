import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}
val googleWebClientId = localProperties.getProperty("GOOGLE_WEB_CLIENT_ID")
    ?.takeIf { it.isNotBlank() }
    ?: providers.environmentVariable("GOOGLE_WEB_CLIENT_ID").orNull.orEmpty()

// Release builds are intentionally strict: no debug/default keystore fallback is permitted.
val releaseKeystorePath = providers.gradleProperty("WORD_BATTLE_KEYSTORE_PATH")
    .orElse(providers.environmentVariable("WORD_BATTLE_KEYSTORE_PATH"))
    .orNull
val releaseStorePassword = providers.gradleProperty("WORD_BATTLE_STORE_PASSWORD")
    .orElse(providers.environmentVariable("WORD_BATTLE_STORE_PASSWORD"))
    .orElse(providers.environmentVariable("STORE_PASSWORD"))
    .orNull
val releaseKeyAlias = providers.gradleProperty("WORD_BATTLE_KEY_ALIAS")
    .orElse(providers.environmentVariable("WORD_BATTLE_KEY_ALIAS"))
    .orElse(providers.environmentVariable("KEY_ALIAS"))
    .orNull
val releaseKeyPassword = providers.gradleProperty("WORD_BATTLE_KEY_PASSWORD")
    .orElse(providers.environmentVariable("WORD_BATTLE_KEY_PASSWORD"))
    .orElse(providers.environmentVariable("KEY_PASSWORD"))
    .orNull

android {
    namespace = "com.wordbattle.com"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.wordbattle.com"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    packaging.resources.excludes += setOf(
        "/META-INF/{AL2.0,LGPL2.1}",
        "/META-INF/INDEX.LIST",
        "/META-INF/DEPENDENCIES"
    )

    signingConfigs {
        create("wordBattleRelease") {
            storeFile = releaseKeystorePath?.let { file(it) }
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("wordBattleRelease")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    testOptions.unitTests.isReturnDefaultValues = true
}

val verifyReleaseSigningInputs = tasks.register("verifyReleaseSigningInputs") {
    group = "verification"
    description = "Fails release packaging unless the owner-supplied keystore and credentials are present."
    doLast {
        check(!releaseKeystorePath.isNullOrBlank()) {
            "WORD_BATTLE_KEYSTORE_PATH is required. Release APKs never use a debug/default keystore."
        }
        check(file(requireNotNull(releaseKeystorePath)).isFile) {
            "The configured Word Battle release keystore does not exist: $releaseKeystorePath"
        }
        check(!releaseStorePassword.isNullOrBlank()) { "STORE_PASSWORD is required for release signing." }
        check(!releaseKeyAlias.isNullOrBlank()) { "KEY_ALIAS is required for release signing." }
        check(!releaseKeyPassword.isNullOrBlank()) { "KEY_PASSWORD is required for release signing." }
    }
}

tasks.configureEach {
    if (name in setOf("validateSigningRelease", "packageRelease", "assembleRelease", "bundleRelease")) {
        dependsOn(verifyReleaseSigningInputs)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.19.0")
    // AppCompat powers per-app language selection (AppCompatDelegate.setApplicationLocales).
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.2.0")

    implementation(platform("io.github.jan-tennert.supabase:bom:3.7.0"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")
    implementation("io.ktor:ktor-client-android:3.5.1")
    implementation("io.coil-kt.coil3:coil-compose:3.5.0")
    implementation("io.coil-kt.coil3:coil-network-ktor3:3.5.0")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
