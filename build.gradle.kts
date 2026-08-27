// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    configurations.classpath {
        resolutionStrategy {
            force(
                "org.apache.commons:commons-lang3:3.20.0",
                "org.apache.httpcomponents:httpclient:4.5.14",
                "org.bitbucket.b_c:jose4j:0.9.6",
                "org.bouncycastle:bcpkix-jdk18on:1.85",
                "org.bouncycastle:bcprov-jdk18on:1.85.2",
                "org.bouncycastle:bcutil-jdk18on:1.85",
                "org.jdom:jdom2:2.0.6.1"
            )
            eachDependency {
                val requestedNettyPatch = requested.version
                    ?.takeIf { it.startsWith("4.1.") }
                    ?.removePrefix("4.1.")
                    ?.substringBefore(".")
                    ?.toIntOrNull()
                if (requested.group == "io.netty" && requested.version?.startsWith("4.1.") == true && (requestedNettyPatch == null || requestedNettyPatch < 137)) {
                    useVersion("4.1.137.Final")
                    because("Versions before 4.1.137.Final contain multiple security vulnerabilities")
                }
            }
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.hilt) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.ksp) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.auto.license).version(libs.versions.autoLicense) apply false
    kotlin(libs.plugins.kotlin.serialization.get().pluginId).version(libs.versions.kotlin).apply(false)
}

allprojects {
    configurations.configureEach {
        resolutionStrategy {
            force(
                "org.apache.commons:commons-lang3:3.20.0",
                "org.apache.httpcomponents:httpclient:4.5.14",
                "org.bitbucket.b_c:jose4j:0.9.6",
                "org.bouncycastle:bcpkix-jdk18on:1.85",
                "org.bouncycastle:bcprov-jdk18on:1.85.2",
                "org.bouncycastle:bcutil-jdk18on:1.85",
                "org.jdom:jdom2:2.0.6.1"
            )
            eachDependency {
                val requestedNettyPatch = requested.version
                    ?.takeIf { it.startsWith("4.1.") }
                    ?.removePrefix("4.1.")
                    ?.substringBefore(".")
                    ?.toIntOrNull()
                if (requested.group == "io.netty" && requested.version?.startsWith("4.1.") == true && (requestedNettyPatch == null || requestedNettyPatch < 137)) {
                    useVersion("4.1.137.Final")
                    because("Versions before 4.1.137.Final contain multiple security vulnerabilities")
                }
            }
        }
    }
}
