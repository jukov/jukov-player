rootProject.name = "Jukovplayer"

pluginManagement {
    buildscript {
        repositories {
            maven("https://storage.googleapis.com/r8-releases/raw")
        }
        dependencies {
            // AGP 9.0.1 bundles an R8 version that cannot parse Kotlin 2.4 metadata.
            classpath("com.android.tools:r8:9.1.29")
        }
    }
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        maven("https://packages.jetbrains.team/maven/p/cmp/dev")
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        maven("https://packages.jetbrains.team/maven/p/cmp/dev")
    }
}

include(":androidApp")
include(":shared")
