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
}

kotlin {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    applyDefaultHierarchyTemplate()
    androidTarget {
        compilations.all {
            compileTaskProvider {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_1_8)
                    freeCompilerArgs.add("-Xjdk-release=${JavaVersion.VERSION_1_8}")
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
    }


    listOf(
        iosX64(),
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
    compileSdk = 36

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
        targetSdk = 36
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
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
    // If gradle property release true remove sha from version
    version = if (
        providers.systemProperty("release").isPresent || providers.gradleProperty("release").isPresent
    ) {
        gitCurrentTag.get()
    } else {
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
