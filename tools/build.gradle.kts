plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    // The tool validates with the same loader the game uses, rather than a second
    // implementation that would agree with the game right up until it didn't.
    implementation(project(":core"))
    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}

application {
    mainClass.set("com.depthdiver.tools.ContentToolKt")
}
