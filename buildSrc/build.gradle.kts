plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

repositories {
    mavenCentral()
}

gradlePlugin {
    plugins {
        create("ncnnSdk") {
            id = "ncnn.sdk"
            implementationClass = "ncnn.NcnnSdkPlugin"
        }
    }
}
