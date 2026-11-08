plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "io.github.qingshu.ncnn.testapp"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.qingshu.ncnn.testapp"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":ncnn"))
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
