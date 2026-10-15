import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing comes from the git-ignored keystore.properties; see CONTRIBUTING.md.
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.ndelic.kadar"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ndelic.kadar"
        // Android 10+: MediaStore RELATIVE_PATH and IS_PENDING need it.
        minSdk = 29
        targetSdk = 35
        versionCode = 14
        versionName = "0.8.0"
        // Shown next to the version on the home screen: alpha, beta, or "" once stable.
        buildConfigField("String", "STAGE", "\"alpha\"")
    }

    signingConfigs {
        if (!keystoreProps.isEmpty) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        // A separate "Kadar Dev" app, so testing a build never touches the real one and its projects.
        debug {
            applicationIdSuffix = ".debug"
            manifestPlaceholders["appName"] = "Kadar Dev"
        }
        release {
            manifestPlaceholders["appName"] = "Kadar"
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // The open-source license texts ship inside the app (Settings > Open-source licenses).
    sourceSets {
        getByName("main") { assets.srcDir(rootProject.file("third_party")) }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.media3.transformer)
    implementation(libs.media3.effect)
    implementation(libs.mlkit.subject.segmentation)
    testImplementation(libs.junit)
}
