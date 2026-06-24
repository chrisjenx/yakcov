import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

// Android JVM bytecode target — per-channel via compose-releases.toml (see libs.versions.toml).
val jvmTargetVersion = libs.versions.jvmTarget.get()

android {
    namespace = "com.chrisjenx.yakcov.sample"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.chrisjenx.yakcov.sample"
        minSdk = 23
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(jvmTargetVersion)
        targetCompatibility = JavaVersion.toVersion(jvmTargetVersion)
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(jvmTargetVersion))
        jvmDefault.set(JvmDefaultMode.ENABLE)
    }
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation(project(":library"))
//    implementation("com.chrisjenx.yakcov:library:+")
    // yakcov declares libphonenumber-kotlin as compileOnly; the sample uses the Phone() rule (and
    // the library's androidx-startup PhoneNumberUtilInitializer loads it at launch), so the sample
    // must supply it at runtime — without it the app crashes on startup with NoClassDefFoundError.
    implementation(libs.libphonenumber.kotlin)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activityCompose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    // material-icons-core is no longer pulled transitively by material3 in recent Compose BOMs
    implementation(libs.androidx.material.icons.core)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.testManifest)
}
