@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.kotlin.serialization)
}

// Android JVM bytecode target — per-channel via compose-releases.toml (see libs.versions.toml).
val jvmTargetVersion = libs.versions.jvmTarget.get()

kotlin {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    // Public API drift gate: `checkKotlinAbi` fails CI when the committed dump under
    // library/api/ doesn't match the current public surface; `updateKotlinAbi` regenerates it.
    //
    // `enabled` must be set REFLECTIVELY, and the two channels are why. Kotlin 2.4 (`[next]`)
    // removed both `enabled` and `klib`; naming either one statically is a deprecation *error*
    // that fails this script's own compilation — breaking not just the continue-on-error `next`
    // CI legs but `release.yml`'s next-channel publish. Kotlin 2.3 (`[stable]`) has the opposite
    // requirement: without `enabled = true` the check task is SKIPPED, so an empty block leaves
    // a gate that always passes and validates nothing. Verified both directions by mutation test
    // (add a public symbol; the check must FAIL) — an empty block passed it vacuously.
    //
    // `klib` is not set at all: klib validation already defaults to on.
    //
    // On 2.4 the reflective lookup finds nothing and no-ops, but the block's presence auto-enables
    // validation there — and the committed dump, generated on stable's compiler, will NOT match it
    // (2.4 stops emitting the synthetic DefaultConstructorMarker bridge constructors). The dump is
    // therefore toolchain-specific by nature and `Checks-Api` (unmatrixed, stable-only) is the sole
    // place it is enforced. `release.yml` excludes this task from its next-channel dry-run for the
    // same reason.
    //
    // Note: the `Checks-Api` job runs on ubuntu-latest, so the Apple entries in
    // library/api/library.klib.api are NOT actually validated — they pass by inference because
    // klib.keepUnsupportedTargets defaults to `true`. Common-API drift is still caught via the
    // js/wasmJs entries, which DO run on Linux.
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation {
        @Suppress("UNCHECKED_CAST")
        val enabledProperty = javaClass.methods
            .firstOrNull { it.name == "getEnabled" && it.parameterCount == 0 }
            ?.invoke(this) as? org.gradle.api.provider.Property<Boolean>
        enabledProperty?.set(true)
    }

    applyDefaultHierarchyTemplate()
    androidTarget {
        compilations.all {
            compileTaskProvider {
                compilerOptions {
                    jvmTarget.set(JvmTarget.fromTarget(jvmTargetVersion))
                    freeCompilerArgs.add("-Xjdk-release=${JavaVersion.toVersion(jvmTargetVersion)}")
                }
            }
        }
    }

    jvm()

    js {
        browser {
            testTask {
                useKarma {
                    //TODO use firefox
                    useChromeHeadless()
                }
            }
        }
        // Compose 1.12+ gates browser Compose UI tests on an executable binary so the
        // Skiko runtime can be bundled by webpack (CMP-4906). Harmless on older Compose.
        binaries.executable()
        useEsModules()
    }

    wasmJs {
        browser {
            testTask {
                useKarma {
                    //TODO use firefox
                    useChromeHeadless()
                }
            }
        }
        binaries.executable()
    }


    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            baseName = "Yakcov"
        }
    }

    sourceSets {
        commonMain.dependencies {
            // Change to compile only to not force consumers to bring in all compose dependencies
            compileOnly(libs.jetbrains.compose.runtime)
            compileOnly(libs.jetbrains.compose.foundation)
            compileOnly(libs.jetbrains.compose.material3)
            compileOnly(libs.jetbrains.compose.components.resources)
            compileOnly(libs.jetbrains.compose.ui.tooling.preview)
            compileOnly(libs.kotlinx.datetime)
            compileOnly(libs.kotlinx.serialization.core)
            compileOnly(libs.jetbrains.compose.runtime.saveable)
            compileOnly(libs.libphonenumber.kotlin)
        }

        commonTest.dependencies {
            // Implement for tests to run
            implementation(libs.jetbrains.compose.runtime)
            implementation(libs.jetbrains.compose.foundation)
            implementation(libs.jetbrains.compose.material3)
            implementation(libs.jetbrains.compose.components.resources)
            implementation(libs.jetbrains.compose.ui.tooling.preview)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.jetbrains.compose.runtime.saveable)
            implementation(libs.libphonenumber.kotlin)
            // Test Dependencies
            implementation(kotlin("test"))
            implementation(libs.jetbrains.compose.ui.test)
        }

        androidMain.dependencies {
            compileOnly(libs.jetbrains.compose.ui.tooling)
            compileOnly(libs.androidx.activityCompose)
            compileOnly(libs.androidx.startup.runtime)
        }

        androidUnitTest.dependencies {
            // Implement for tests to run
            implementation(libs.androidx.activityCompose)
            implementation(libs.androidx.startup.runtime)
            // Should pull down jvm target
            implementation(libs.libphonenumber.kotlin)
        }

        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
        }
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
            // Classpath scanning for RuleRegistryGuardTest (rule-convention discovery guard)
            implementation(libs.classgraph)
        }

        jsMain.dependencies {
            compileOnly(libs.jetbrains.compose.html.core)
        }

        jsTest.dependencies {
            implementation(libs.jetbrains.compose.html.core)
        }

        iosMain.dependencies {
        }


    }


    //https://kotlinlang.org/docs/native-objc-interop.html#export-of-kdoc-comments-to-generated-objective-c-headers
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget> {
        compilations["main"].compileTaskProvider.configure {
            compilerOptions {
                freeCompilerArgs.add("-Xexport-kdoc")
            }
        }
    }
}

compose.resources {
    publicResClass = true
    generateResClass = always
}

android {
    namespace = "com.chrisjenx.yakcov"
    compileSdk = 37

    defaultConfig {
        minSdk = 23
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    sourceSets["main"].apply {
        manifest.srcFile("src/androidMain/AndroidManifest.xml")
        res.srcDirs("src/androidMain/res")
    }
    //https://developer.android.com/studio/test/gradle-managed-devices
    testOptions {
        targetSdk = 37
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(jvmTargetVersion)
        targetCompatibility = JavaVersion.toVersion(jvmTargetVersion)
    }
    buildFeatures {
        //enables a Compose tooling support in the AndroidStudio
        compose = true
    }
}

publishing {
    repositories {
        maven {
            name = "githubPackages"
            url = uri("https://maven.pkg.github.com/chrisjenx/yakcov")
            credentials(PasswordCredentials::class)
            // https://vanniktech.github.io/gradle-maven-publish-plugin/other/#configuring-the-repository
            // username is from: githubPackagesUsername or ORG_GRADLE_PROJECT_githubPackagesUsername
            // password is from: githubPackagesPassword or ORG_GRADLE_PROJECT_githubPackagesPassword
        }
    }
}

private val gitRevListTags = providers.exec {
    commandLine("git", "rev-list", "--tags", "--max-count=1")
}.standardOutput.asText.map { it.trim() }

private val gitCurrentTag = providers.exec {
    commandLine("git", "describe", "--tags", gitRevListTags.get())
}.standardOutput.asText.map { it.trim() }

// get git shortSha for version
private val gitSha = providers.exec { commandLine("git", "rev-parse", "--short", "HEAD") }
    .standardOutput.asText.map { it.trim() }

mavenPublishing {
    // Version priority: explicit publishVersion > git tag (release) > git tag + sha (dev)
    version = when {
        providers.gradleProperty("publishVersion").isPresent ->
            providers.gradleProperty("publishVersion").get()
        providers.systemProperty("release").isPresent || providers.gradleProperty("release").isPresent ->
            gitCurrentTag.get()
        else ->
            "${gitCurrentTag.get()}-${gitSha.get()}"
    }
    coordinates("com.chrisjenx.yakcov", "library", version = version.toString())
    publishToMavenCentral()
    signAllPublications()

    pom {
        name.set("Yakcov")
        description.set("Yet Another Kotlin COmpose Validation library")
        inceptionYear.set("2024")
        url.set("https://github.com/chrisjenx/yakcov/")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("chrisjenx")
                name.set("Chris Jenkins ")
                url.set("https://github.com/chrisjenx/")
            }
        }
        scm {
            url.set("https://github.com/chrisjenx/yakcov/")
            connection.set("scm:git:git://github.com/chrisjenx/yakcov.git")
            developerConnection.set("scm:git:ssh://git@github.com/chrisjenx/yakcov.git")
        }
    }
}
