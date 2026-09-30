@file:Suppress("UnstableApiUsage")

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.kotlin.dsl.aboutLibraries
import org.gradle.kotlin.dsl.configure

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.android.hilt)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.auto.license)
    jacoco
    kotlin(libs.plugins.kotlin.serialization.get().pluginId).version(libs.versions.kotlin)
}

extensions.configure<ApplicationExtension> {
    namespace = "dev.chungjungsoo.gptmobile"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.melo.gptmobile.improved"
        minSdk = 31
        targetSdk = 36
        versionCode = 91
        versionName = "0.9.22.0" // delegation, tool consent and portable backups

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Public OAuth client configuration; never put client secrets in an APK.
        val hfClientId = providers.gradleProperty("HF_OAUTH_CLIENT_ID").orElse(providers.environmentVariable("HF_OAUTH_CLIENT_ID")).getOrElse("")
        val hfRedirect = providers.gradleProperty("HF_OAUTH_REDIRECT_URI").orElse(providers.environmentVariable("HF_OAUTH_REDIRECT_URI")).getOrElse("")
        require(hfClientId.all { it.isLetterOrDigit() || it in "-_" }) { "Invalid HF OAuth client ID" }
        require(hfRedirect.isEmpty() || Regex("[a-z][a-z0-9+.-]*://[A-Za-z0-9/_.-]+").matches(hfRedirect)) { "Invalid HF OAuth redirect URI" }
        manifestPlaceholders["appAuthRedirectScheme"] = hfRedirect.substringBefore(":").ifEmpty { "gptmobile-hf-unconfigured" }
        buildConfigField("String", "LITERT_LM_VERSION", "\"${libs.versions.litertlm.get()}\"")
        buildConfigField("String", "QAIRT_VERSION", "\"${libs.versions.qnn.get()}\"")
        buildConfigField("String", "HF_OAUTH_CLIENT_ID", "\"$hfClientId\"")
        buildConfigField("String", "HF_OAUTH_REDIRECT_URI", "\"$hfRedirect\"")
        // App owner confirmed LLM7 integration approval on 2026-09-26.
        // A build can explicitly disable the integration if approval changes.
        buildConfigField("boolean", "FREE_LLM7_APPROVED", providers.gradleProperty("freeLlm7Approved").map { (it == "true").toString() }.getOrElse("true"))

        ndk {
            // Target 64-bit modern high-performance ABIs (eliminates 32-bit legacy overhead)
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    splits {
        abi {
            isEnable = providers.gradleProperty("enableAbiSplits").orNull != "false"
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }

    androidResources {
        generateLocaleConfig = true
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        // Existing lint debt is recorded after review; new errors fail this gate.
        warning += "MissingTranslation"
    }

    buildTypes {
        debug {
            enableUnitTestCoverage = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            vcsInfo.include = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.testLogging {
                    events("passed", "skipped", "failed", "standardError")
                }
            }
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
        }
        jniLibs {
            // Extract native libraries to nativeLibraryDir on installation so Qualcomm FastRPC cDSP can load libQnnHtpV79Skel.so directly from the filesystem
            useLegacyPackaging = true
            // Keep pre-stripped native libraries without triggering stripping warnings
            keepDebugSymbols += setOf(
                "**/libLiteRt.so",
                "**/libLiteRtClGlAccelerator.so",
                "**/libLiteRtDispatch_Qualcomm.so",
                "**/libLiteRtCompilerPlugin_Qualcomm.so",
                "**/liblitertlm_jni.so",
                "**/liblitertlm_apple_framework.so",
                "**/libYnnpack*.so",
                "**/libdatastore_shared_counter.so",
                "**/libandroidx.graphics.path.so",
                "**/libQnn*.so"
            )
            // LLM packages are AOT compiled. Keep one QAIRT version from qnn-runtime;
            // do not mix it with checked-in HTP stubs/skeletons via pickFirsts.
            // libQnnHtpPrepare is part of Qualcomm's documented LiteRT dispatch
            // runtime, including for precompiled contexts, and must be packaged.
            excludes += setOf("**/libQnnDsp*.so", "**/libQnnGpu.so")
        }
    }
}

extensions.configure<ApplicationAndroidComponentsExtension> {
    onVariants { variant ->
        variant.androidTest?.sources?.assets?.addStaticSourceDirectory("$projectDir/schemas")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")
    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    val fileFilter = setOf(
        "**/R.class",
        "**/R$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "android/**/*.*",
        "**/*_Hilt*.class",
        "**/Hilt_*.class",
        "**/*_Factory.class",
        "**/*_MembersInjector.class"
    )

    val debugTree = fileTree("$buildDir/tmp/kotlin-classes/debug") {
        exclude(fileFilter)
    }
    val mainSources = files(
        "$projectDir/src/main/java",
        "$projectDir/src/main/kotlin"
    )

    sourceDirectories.setFrom(mainSources)
    classDirectories.setFrom(files(debugTree))
    executionData.setFrom(
        fileTree(buildDir) {
            include(
                "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
                "jacoco/testDebugUnitTest.exec"
            )
        }
    )
}

dependencies {
    // Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.viewmodel)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.material.views)

    // Location
    implementation(libs.maplibre)

    // SplashScreen
    implementation(libs.splashscreen)

    // DataStore
    implementation(libs.androidx.datastore)

    // Dependency Injection
    implementation(libs.hilt)
    implementation(libs.androidx.lifecycle.runtime.compose.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.work.runtime.ktx)

    // Ktor
    implementation(libs.ktor.content.negotiation)
    implementation(libs.ktor.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.logging)
    implementation(libs.ktor.serialization)
    implementation(libs.mcp.kotlin.sdk.client)

    // OAuth browser flow
    implementation(libs.androidx.browser)
    implementation(libs.openid.appauth)

    // Document text extraction for cloud and on-device models
    implementation(libs.pdfbox)
    implementation(libs.poi)
    implementation(libs.poi.scratchpad)

    // JSON parsing
    implementation(libs.gson)

    // On-device LiteRT-LM serving
    implementation(libs.litertlm)

    // QAIRT host libraries and matching HTP stubs/skeletons for LiteRT-LM NPU dispatch.
    implementation(libs.qnn.runtime)

    // License page UI
    implementation(libs.auto.license.core)
    implementation(libs.auto.license.ui)

    // Markdown
    implementation(libs.markdown.renderer)
    implementation(libs.markdown.renderer.m3)
    implementation(libs.markdown.renderer.code)

    // Navigation
    implementation(libs.hilt.navigation)
    implementation(libs.androidx.navigation)

    // Room
    implementation(libs.room)
    ksp(libs.room.compiler)
    implementation(libs.room.ktx)

    // Serialization
    implementation(libs.kotlin.serialization)

    // Test
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.mockito.core)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.room.testing)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

aboutLibraries {
    // Remove the "generated" timestamp to allow for reproducible builds
    export {
        excludeFields.add("generated")
    }
}
