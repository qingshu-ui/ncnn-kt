#!/usr/bin/env bash
set -euo pipefail

# Assembles every module, then stages all Maven Central deployments.
sh ./gradlew assemble \
    :ncnn-core:publishAllPublicationsToMavenCentralRepository \
    :ncnn-binding:publishAllPublicationsToMavenCentralRepository \
    :ncnn-jvm-jni:publishAllPublicationsToMavenCentralRepository \
    :ncnn-android-jni:publishAllPublicationsToMavenCentralRepository \
    "$@"
