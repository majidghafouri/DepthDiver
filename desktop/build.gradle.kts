import org.gradle.internal.os.OperatingSystem
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    id("application")
}

dependencies {
    implementation(project(":core"))
    implementation(libs.gdx)
    implementation(libs.gdx.backend.lwjgl3)
    implementation("com.badlogicgames.gdx:gdx-platform:${libs.versions.gdx.get()}:natives-desktop")
    implementation("com.badlogicgames.gdx:gdx-freetype-platform:${libs.versions.gdx.get()}:natives-desktop")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

val distJar = tasks.register<Jar>("distJar") {
    group = "distribution"
    description = "Builds a self-contained runnable fat jar (game + LWJGL3/gdx natives merged)."
    from(sourceSets["main"].output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().map {
            if (it.isDirectory) it else zipTree(it)
        }
    }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
        exclude("META-INF/INDEX.LIST")
    }

    archiveBaseName.set("depthdiver-desktop")
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes["Main-Class"] = "com.depthdiver.desktop.DesktopLauncherKt"
        attributes["Implementation-Version"] = project.version
    }
}

tasks.named("build") {
    dependsOn(distJar)
}

val dist = tasks.register("dist") {
    group = "distribution"
    description = "Alias for distJar (builds the self-contained runnable desktop jar)."
    dependsOn(distJar)
}

application {
    mainClass.set("com.depthdiver.desktop.DesktopLauncherKt")
}

tasks.named<JavaExec>("run") {
    if (OperatingSystem.current().isMacOsX) {
        jvmArgs("-XstartOnFirstThread")
    }
}
