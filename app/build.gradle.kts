import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.moodtunes.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.moodtunes.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 16
        versionName = "1.9.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // ── Release signing ──────────────────────────────────────────────────────
    // Credentials are supplied via environment variables (CI) or a local,
    // git-ignored `keystore.properties` file. There is deliberately NO
    // hard-coded fallback: a missing configuration produces an unsigned
    // release build instead of silently reusing a published key.
    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties().apply {
        if (keystorePropertiesFile.exists()) {
            keystorePropertiesFile.inputStream().use { load(it) }
        }
    }

    fun signingValue(envName: String, propertyName: String): String? =
        System.getenv(envName)?.trim()?.takeIf { it.isNotEmpty() }
            ?: keystoreProperties.getProperty(propertyName)?.trim()?.takeIf { it.isNotEmpty() }

    val storePath = signingValue("KEYSTORE_FILE", "storeFile")
    val storePasswordValue = signingValue("KEYSTORE_PASSWORD", "storePassword")
    val keyAliasValue = signingValue("KEY_ALIAS", "keyAlias")
    val keyPasswordValue = signingValue("KEY_PASSWORD", "keyPassword")
    val releaseKeystore = storePath?.let { rootProject.file(it) }?.takeIf { it.exists() }
    val hasReleaseSigning = releaseKeystore != null &&
        storePasswordValue != null && keyAliasValue != null && keyPasswordValue != null

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = storePasswordValue
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.findByName("release")
            if (!hasReleaseSigning) {
                logger.warn(
                    "MoodTunes: release signing is not configured. Set KEYSTORE_FILE/" +
                        "KEYSTORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD or create keystore.properties. " +
                        "The release APK will be UNSIGNED."
                )
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // When the release key is available locally, debug builds share its
            // signature so debug/release APKs can be swapped in place. Otherwise
            // the standard debug key is used.
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/*.kotlin_module",
                "mozilla/public-suffix-list.txt"
            )
        }
    }

    testOptions {
        unitTests {
            // Allows JVM unit tests to run against android.jar stubs (Uri.parse etc.)
            isReturnDefaultValues = true
        }
    }

    applicationVariants.all {
        outputs.all {
            (this as? com.android.build.gradle.internal.api.BaseVariantOutputImpl)?.outputFileName =
                "MoodTunes-v${defaultConfig.versionName}.apk"
        }
    }
}

// Room: export the schema so migrations are reviewable and testable.
// Committed JSON files live in app/schemas/.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    // Compose (BOM controls versions of all compose libs below)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Media3 / ExoPlayer
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.cast)
    implementation(libs.androidx.media3.datasource)
    implementation(libs.androidx.media3.database)

    // Glance (home screen widget)
    implementation(libs.androidx.glance.appwidget)

    // MediaRouter (Chromecast route picker)
    implementation(libs.androidx.mediarouter)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Coil (image loading for album art)
    implementation(libs.coil.compose)

    // Networking & YouTube Streaming Engine
    implementation(libs.okhttp)
    implementation(libs.androidx.javascriptengine)
    implementation(libs.newpipe.extractor)

    // Lottie (animations)
    implementation(libs.lottie.compose)

    // Accompanist (permission handling)
    implementation(libs.accompanist.permissions)

    // Timber (logging)
    implementation(libs.timber)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.org.json)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
