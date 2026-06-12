import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * Compiled documentation examples — NOT published.
 *
 * Every code block on the docs site is pulled from this module via pymdownx.snippets
 * named markers (`// --8<-- [start:name]`), so examples can never silently drift from
 * the library API: if the API changes, this module stops compiling and CI fails
 * (see .github/workflows/checks.yml).
 *
 * JVM + Android targets only — enough to prove the wiring compiles without stressing
 * the build (full multiplatform builds OOM locally at -Xmx4G).
 */
plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
}

kotlin {
    androidTarget {
        compilations.all {
            compileTaskProvider {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_1_8)
                }
            }
        }
    }

    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":library"))
            // :library declares Compose as compileOnly — consumers (and these examples)
            // supply the Compose runtime themselves, like the library's own tests do.
            implementation(libs.jetbrains.compose.runtime)
            implementation(libs.jetbrains.compose.foundation)
            implementation(libs.jetbrains.compose.material3)
            implementation(libs.jetbrains.compose.components.resources)
            // Phone() recipe — the library declares libphonenumber compileOnly,
            // so apps using the Phone rule must add it (see docs/recipes/phone.md).
            implementation(libs.libphonenumber.kotlin)
            // ViewModel pattern (KMP artifact: desktop + android)
            implementation(libs.jetbrains.lifecycle.viewmodel.compose)
            // Circuit pattern
            implementation(libs.circuit.foundation)
        }
    }
}

android {
    namespace = "com.chrisjenx.yakcov.docs"
    compileSdk = 37
    defaultConfig {
        minSdk = 23
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}
