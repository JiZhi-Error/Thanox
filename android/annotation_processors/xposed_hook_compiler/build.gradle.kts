plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":annotation_processors:xposed_hook_annotation"))
    implementation("com.google.guava:guava:23.0")
    implementation("com.squareup:javapoet:1.13.0")
}

