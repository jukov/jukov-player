import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import com.github.triplet.gradle.androidpublisher.ReleaseStatus
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.googleServices)
    alias(libs.plugins.firebaseCrashlytics)
    alias(libs.plugins.gradlePlayPublisher)
    alias(libs.plugins.screenshot)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment)
    implementation(libs.compose.uiToolingPreview)
    implementation(libs.androidx.room3.runtime)
    implementation(libs.androidx.work.runtime)
    implementation(libs.ktor.client.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.multiplatform.settings.no.arg)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    debugImplementation(libs.compose.uiTooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(libs.kotlin.testJunit)
    androidTestImplementation(libs.androidx.testExt.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.ktor.client.mock)
    androidTestImplementation(libs.multiplatform.settings.test)
    screenshotTestImplementation(libs.screenshot.validation.api)
    screenshotTestImplementation(libs.compose.uiTooling)
    screenshotTestImplementation(libs.compose.foundation)
    screenshotTestImplementation(libs.compose.material3)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.isFile) {
        keystorePropertiesFile.inputStream().use(::load)
    }
}
fun signingValue(propertyName: String, environmentName: String): String? =
    keystoreProperties.getProperty(propertyName)?.takeIf { it.isNotBlank() }
        ?: System.getenv(environmentName)?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("storeFile", "JUKOV_RELEASE_STORE_FILE")
val releaseStorePassword = signingValue("storePassword", "JUKOV_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "JUKOV_RELEASE_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "JUKOV_RELEASE_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { it != null }

val releaseVersionCode = providers.environmentVariable("JUKOV_VERSION_CODE")
    .orElse("1")
    .map { value ->
        value.toIntOrNull()?.takeIf { it > 0 }
            ?: error("JUKOV_VERSION_CODE must be a positive integer, got '$value'.")
    }
val releaseVersionName = providers.environmentVariable("JUKOV_VERSION_NAME")
    .orElse("1.0")
val androidTestBuildType = providers.environmentVariable("JUKOV_ANDROID_TEST_BUILD_TYPE")
    .orElse("debug")

android {
    experimentalProperties["android.experimental.enableScreenshotTest"] = true
    namespace = "info.jukov.player"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    installation {
        // Android Studio can report INSTALL_BASELINE_PROFILE_FAILED on newer ART
        // versions even though the APK itself was installed successfully.
        enableBaselineProfile = false
    }

    defaultConfig {
        applicationId = "info.jukov.player"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = releaseVersionCode.get()
        versionName = releaseVersionName.get()
        testInstrumentationRunner = "info.jukov.player.JukovTestRunner"
        if (androidTestBuildType.get() == "releaseSmoke") {
            testInstrumentationRunnerArguments["class"] =
                "info.jukov.player.ReleaseR8SmokeTest"
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(requireNotNull(releaseStoreFile))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            ndk {
                // Native symbols are packaged into the AAB and uploaded with it.
                debugSymbolLevel = "FULL"
            }
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("releaseSmoke") {
            initWith(getByName("release"))
            matchingFallbacks += "release"
            proguardFile("proguard-release-smoke-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        testBuildType = androidTestBuildType.get()
        managedDevices {
            localDevices {
                create("pixel2Api28") {
                    device = "Pixel 2"
                    apiLevel = 28
                    systemImageSource = "aosp"
                }
                create("pixel2Api36") {
                    device = "Pixel 2"
                    apiLevel = 36
                    systemImageSource = "aosp"
                }
            }
            groups {
                create("androidSmoke") {
                    targetDevices.add(localDevices["pixel2Api28"])
                    targetDevices.add(localDevices["pixel2Api36"])
                }
            }
        }
    }
}

play {
    defaultToAppBundles.set(true)
    track.set("internal")
    releaseStatus.set(ReleaseStatus.COMPLETED)
}

val validateReleaseSigning by tasks.registering(ValidateReleaseSigningTask::class) {
    signingConfigured.set(hasReleaseSigning)
}

tasks.configureEach {
    if (name == "preReleaseBuild") {
        dependsOn(validateReleaseSigning)
    }
}
