import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.time.Instant
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/** Bump these together; the release manifest task refuses to go backwards. */
val releaseVersionCode = 11
val releaseVersionName = "1.1.6"

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

/**
 * SHA-256 of the release signing certificate, injected into BuildConfig so the
 * app can refuse to stay activated when repackaged with another key. Empty when
 * the keystore is unavailable, which makes the check a no-op.
 */
fun releaseCertificateSha256(): String {
    if (!keystorePropertiesFile.exists()) return ""
    return runCatching {
        val storePath = rootProject.file(keystoreProperties.getProperty("storeFile"))
        if (!storePath.exists()) return ""
        val password = keystoreProperties.getProperty("storePassword").toCharArray()
        val certificate = loadKeyStore(storePath, password)
            ?.getCertificate(keystoreProperties.getProperty("keyAlias"))
            ?: return ""
        MessageDigest.getInstance("SHA-256")
            .digest(certificate.encoded)
            .joinToString("") { "%02x".format(it) }
    }.getOrDefault("")
}

fun loadKeyStore(path: File, password: CharArray): KeyStore? {
    for (type in listOf("PKCS12", "JKS")) {
        val loaded = runCatching {
            KeyStore.getInstance(type).apply {
                path.inputStream().use { stream -> load(stream, password) }
            }
        }.getOrNull()
        if (loaded != null) return loaded
    }
    return null
}

android {
    namespace = "com.textpic.studio"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.textpic.studio"
        minSdk = 26
        targetSdk = 35
        versionCode = releaseVersionCode
        versionName = releaseVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        // Address of the owner's license server (Cloudflare Worker).
        // Set it in gradle.properties or with -Ptextpic.licenseServerUrl=...
        val licenseServerUrl = (project.findProperty("textpic.licenseServerUrl") as String?)
            ?.trim()
            ?.takeIf { it.startsWith("https://") }
            ?: ""
        buildConfigField("String", "LICENSE_SERVER_URL", "\"$licenseServerUrl\"")
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            buildConfigField("String", "LICENSE_CERT_SHA256", "\"\"")
        }
        release {
            buildConfigField("String", "LICENSE_CERT_SHA256", "\"${releaseCertificateSha256()}\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (keystorePropertiesFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                null
            }
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

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

/**
 * Writes `dist/release-manifest.json` for the signed release APK, which the owner
 * console checks before publishing. Refuses a versionCode that is not higher than
 * the last manifest in `dist/`.
 */
tasks.register("writeReleaseManifest") {
    group = "release"
    description = "Writes dist/release-manifest.json for the release APK."
    doLast {
        val apk = layout.buildDirectory.file("outputs/apk/release/app-release.apk").get().asFile
        require(apk.isFile) { "Build the release APK first: ./gradlew assembleRelease" }
        val out = rootProject.file("dist/release-manifest.json")
        if (out.isFile) {
            val previous = Regex("\"versionCode\"\\s*:\\s*(\\d+)")
                .find(out.readText())?.groupValues?.get(1)?.toIntOrNull()
            require(previous == null || releaseVersionCode > previous) {
                "versionCode $releaseVersionCode must be higher than the last release ($previous)."
            }
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(apk.readBytes())
            .joinToString("") { "%02x".format(it) }
        val builtAt = Instant.now().toString()
        val body = """
            {
              "packageName": "com.textpic.studio",
              "versionCode": $releaseVersionCode,
              "versionName": "$releaseVersionName",
              "sha256": "$digest",
              "sizeBytes": ${apk.length()},
              "minSdk": 26,
              "builtAt": "$builtAt"
            }
        """.trimIndent() + "\n"
        out.parentFile.mkdirs()
        out.writeText(body)
        logger.lifecycle("Wrote ${out.path}")
    }
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    finalizedBy("writeReleaseManifest")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":license"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.vico.compose.m3)
    implementation(libs.androidx.emoji2)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.truth)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
}
