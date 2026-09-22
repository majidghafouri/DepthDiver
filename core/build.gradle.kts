plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation(libs.gdx)
    implementation(libs.gdx.freetype)
    implementation(libs.gdx.freetype.platform)
    testImplementation("org.jetbrains.kotlin:kotlin-test")
}