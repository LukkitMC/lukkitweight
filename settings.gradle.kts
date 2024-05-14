plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "leavesweight"

include("paperweight-lib", "leavesweight-patcher")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
