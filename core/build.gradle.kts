plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.test {
    useJUnit()
}

dependencies {
    implementation(libs.gdx)
    implementation(libs.gdx.freetype)
    implementation(libs.gdx.freetype.platform)
    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}