import java.util.Properties

plugins {
    alias(libs.plugins.island.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
    id("kotlin-parcelize")
}

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
if (keystorePropertiesFile.isFile) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

fun Properties.signingProperty(name: String): String? =
    getProperty(name)?.takeIf { it.isNotBlank() }

val localKeystoreFile = keystoreProperties.signingProperty("storeFile")
val localKeystorePassword = keystoreProperties.signingProperty("storePassword")
val localKeyAlias = keystoreProperties.signingProperty("keyAlias")
val localKeyPassword = keystoreProperties.signingProperty("keyPassword")
val hasLocalSigningConfig = listOf(
    localKeystoreFile,
    localKeystorePassword,
    localKeyAlias,
    localKeyPassword,
).all { it != null }

val envKeystoreFile = System.getenv("SIGNING_KEY")
val envKeystorePassword = System.getenv("KEYSTORE_PASSWORD")
val envAlias = System.getenv("ALIAS")
val envKeyPassword = System.getenv("KEY_PASSWORD")
val hasEnvSigningConfig = listOf(
    envKeystoreFile,
    envKeystorePassword,
    envAlias,
    envKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "com.island.recorder"

    defaultConfig {
        applicationId = "com.island.recorder"

        // Version control retrieved from git, with a build-plugin fallback when git is unavailable.
        versionCode = project.getGitCommitCount()
        versionName = project.getBaseVersionName()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions.addAll(listOf("level"))

    productFlavors {
        create("Unstable") {
            dimension = "level"
            isDefault = true
            versionNameSuffix = ".${project.getGitHash()}"
        }

        create("Stable") {
            dimension = "level"
            versionNameSuffix = ".${project.getGitHash()}"
        }
    }

    signingConfigs {
        create("release") {
            if (hasLocalSigningConfig) {
                storeFile = rootProject.file(localKeystoreFile!!)
                storePassword = localKeystorePassword
                keyAlias = localKeyAlias
                keyPassword = localKeyPassword
            } else if (hasEnvSigningConfig) {
                storeFile = file(envKeystoreFile!!)
                storePassword = envKeystorePassword
                keyAlias = envAlias
                keyPassword = envKeyPassword
            }
        }
    }

    buildTypes {
        release {
            if (hasLocalSigningConfig || hasEnvSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt")
            )
            // Generate native debug symbols for crash analysis
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    packaging {
        resources {
            excludes.add("/META-INF/{AL2.0,LGPL2.1}")
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    compileOnly(project(":hidden-api"))

    implementation(libs.androidx.core)
    implementation(libs.androidx.lifecycle)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.splashscreen)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)

    // Miuix UI
    implementation(libs.miuix.core)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.shader)
    implementation(libs.miuix.blur)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.glass)

    // Koin Dependency Injection
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)

    // Shizuku
    implementation(libs.rikka.shizuku.api)
    implementation(libs.rikka.shizuku.provider)

    implementation(project(":app-process"))

    // Timber
    implementation(libs.timber)

    // HiddenApiBypass
    implementation(libs.hiddenapibypass)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)

    // Debug
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}

