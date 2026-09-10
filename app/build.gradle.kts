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
    kotlin(libs.plugins.kotlin.serialization.get().pluginId).version(libs.versions.kotlin)
}

extensions.configure<ApplicationExtension> {
    namespace = "dev.chungjungsoo.gptmobile"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.chungjungsoo.gptmobile"
        minSdk = 28
        targetSdk = 37
        versionCode = 24
        versionName = "0.9.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Hugging Face OAuth. Replace these after registering an HF OAuth app;
        // the gallery credentials cannot be reused.
        manifestPlaceholders["appAuthRedirectScheme"] =
            "REPLACE_WITH_YOUR_REDIRECT_SCHEME_IN_HUGGINGFACE_APP"
        buildConfigField(
            "String",
            "HF_OAUTH_CLIENT_ID",
            "\"REPLACE_WITH_YOUR_CLIENT_ID_IN_HUGGINGFACE_APP\""
        )
        buildConfigField(
            "String",
            "HF_OAUTH_REDIRECT_URI",
            "\"REPLACE_WITH_YOUR_REDIRECT_URI_IN_HUGGINGFACE_APP\""
        )
    }

    androidResources {
        generateLocaleConfig = true
    }

    lint {
        disable += "MissingTranslation"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            vcsInfo.include = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
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
    implementation(libs.mcp.kotlin.sdk.client) {
        // The SDK bytecode targets Kotlin 2.1, but its 2.4 stdlib confuses Hilt's 2.3 metadata reader.
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    }

    // OAuth browser flow
    implementation(libs.androidx.browser)
    implementation(libs.openid.appauth)

    // On-device LiteRT-LM serving
    implementation(libs.litertlm)

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
