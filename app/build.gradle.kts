import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.hilt)
    alias(libs.plugins.baselineprofile)
}

// Release signing: env vars (CI) win over gitignored keystore.properties (local); neither = unsigned release.
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

fun signingValue(env: String, property: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: keystoreProps.getProperty(property)?.takeIf { it.isNotBlank() }

val keystoreFile = signingValue("ANDROID_KEYSTORE_FILE", "storeFile")?.let(rootProject::file)

// Version name lives in the root VERSION file, bumped by the release PR.
val appVersionName = rootProject.file("VERSION").readText().trim()

/** Packs X.Y.Z[-alpha|beta.N] into a code that rises with every release: 1.4.2-beta.3 = 10402103, 1.4.2 = 10402200. */
fun versionCodeOf(name: String): Int {
    val match = Regex("""^(\d+)\.(\d+)\.(\d+)(?:-(alpha|beta)\.(\d+))?$""").matchEntire(name)
        ?: error("VERSION '$name' is not X.Y.Z or X.Y.Z-alpha|beta.N")
    val (major, minor, patch, label, number) = match.destructured
    require(minor.toInt() < 100 && patch.toInt() < 100 && (number.toIntOrNull() ?: 0) < 100) {
        "VERSION '$name' overflows a version code slot"
    }
    val stage = when (label) { "alpha" -> 0; "beta" -> 1; else -> 2 }
    return major.toInt() * 10_000_000 + minor.toInt() * 100_000 + patch.toInt() * 1_000 +
        stage * 100 + (number.toIntOrNull() ?: 0)
}

android {
    namespace = "com.rewire.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.rewire.app"
        minSdk = 26
        targetSdk = 35
        versionCode = versionCodeOf(appVersionName)
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // English-only UI: drop library translations from the APK.
    androidResources {
        localeFilters += "en"
    }

    signingConfigs {
        if (keystoreFile?.exists() == true) {
            create("release") {
                storeFile = keystoreFile
                storePassword = signingValue("ANDROID_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("ANDROID_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("ANDROID_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }
    // Baseline-profile / benchmark builds (added by the plugin) must install: debug key when no release keystore.
    buildTypes.configureEach {
        if (name != "release" && name.endsWith("Release") && signingConfig == null) signingConfig = signingConfigs.getByName("debug")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += listOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/*.version",
            "/META-INF/*.kotlin_module",
            "DebugProbesKt.bin",
            "kotlin-tooling-metadata.json",
        )
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// Exported schemas are checked in: they are the baseline for every future migration.
room {
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    compilerOptions {
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "androidx.compose.animation.ExperimentalSharedTransitionApi",
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
