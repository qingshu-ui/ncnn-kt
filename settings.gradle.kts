@file:Suppress("UnstableApiUsage")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
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
    }
}

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "ncnn-kt"

val enableAndroid = providers.gradleProperty("ncnn.android").orNull?.toBoolean()
    ?: System.getenv("NCNN_ANDROID")?.toBoolean()
    ?: true

include("app")
include("ncnn")
if (enableAndroid) {
    include("ncnn-android-native")
    include("android-test-app")
}
