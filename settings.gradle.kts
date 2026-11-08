@file:Suppress("UnstableApiUsage")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
dependencyResolutionManagement {
    repositories {
        if (System.getenv("GITHUB_ACTIONS") != "true") {
            maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public")
            // maven("https://maven.aliyun.com/repository/central")
            // maven("https://maven.aliyun.com/repository/google")
        }

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
        if (System.getenv("GITHUB_ACTIONS") != "true") {
            maven("https://maven.aliyun.com/repository/central")
            maven("https://maven.aliyun.com/repository/gradle-plugin")
        }

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
