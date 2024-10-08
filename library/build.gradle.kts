@file:OptIn(ExperimentalWasmDsl::class)

import com.android.build.api.dsl.ManagedVirtualDevice
import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.compose.ExperimentalComposeLibrary
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
    //alias(libs.plugins.cocoapods)
    alias(libs.plugins.maven.publish)
}

kotlin {
//    cocoapods {
//        version = "1.0.0"
//        summary = "Yet Another Kotlin COmpose Validation library"
//        homepage = "https://github.com/chrisjenx/yakcov"
//        ios.deploymentTarget = "14.1"
//        framework {
//            baseName = "shared"
//            isStatic = true
//            pod("libPhoneNumber-iOS")
////            @OptIn(ExperimentalKotlinGradlePluginApi::class)
////            transitiveExport = true
//        }
//    }

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
        //https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-test.html
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        instrumentedTestVariant {
            sourceSetTree.set(KotlinSourceSetTree.test)
            dependencies {
                debugImplementation(libs.androidx.testManifest)
                implementation(libs.androidx.junit4)
            }
        }
    }

    jvm()

    js {
        browser {
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
        }
    }

    wasmJs {
        browser {
            testTask {
                useKarma {
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
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.kotlinx.datetime)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            @OptIn(ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
        }

        androidMain.dependencies {
            compileOnly(compose.uiTooling)
            implementation(libs.androidx.activityCompose)
            api(libs.libphonenumber.android)
        }

        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.libphonenumber.jvm)
        }

        jsMain.dependencies {
            implementation(compose.html.core)
            implementation(npm("libphonenumber-js", "1.11.10"))
        }

        iosMain.dependencies {
        }


    }

    //https://kotlinlang.org/docs/native-objc-interop.html#export-of-kdoc-comments-to-generated-objective-c-headers
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget> {
        compilations["main"].compilerOptions.options.freeCompilerArgs.add("-Xexport-kdoc")
    }
}

android {
    namespace = "com.chrisjenx.yakcov"
    compileSdk = 34

    defaultConfig {
        minSdk = 23
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    sourceSets["main"].apply {
        manifest.srcFile("src/androidMain/AndroidManifest.xml")
        res.srcDirs("src/androidMain/res")
    }
    //https://developer.android.com/studio/test/gradle-managed-devices
    @Suppress("UnstableApiUsage")
    testOptions {
        targetSdk = 34
        managedDevices.devices {
            maybeCreate<ManagedVirtualDevice>("pixel5").apply {
                device = "Pixel 5"
                apiLevel = 34
                systemImageSource = "aosp"
            }
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

@Suppress("UnstableApiUsage")
private val gitRevListTags = providers.exec {
    commandLine("git", "rev-list", "--tags", "--max-count=1")
}.standardOutput.asText.map { it.trim() }

@Suppress("UnstableApiUsage")
private val gitCurrentTag = providers.exec {
    commandLine("git", "describe", "--tags", gitRevListTags.get())
}.standardOutput.asText.map { it.trim() }

// get git shortSha for version
@Suppress("UnstableApiUsage")
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
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
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
