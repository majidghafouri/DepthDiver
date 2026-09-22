import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "app.depthdiver"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "app.depthdiver"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    packaging {
        resources {
            pickFirsts += listOf("META-INF/gdx.backend.android.properties")
        }
    }
}

val gdxVersion = libs.versions.gdx.get()

val natives = configurations.create("natives")

dependencies {
    implementation(project(":core"))
    implementation(libs.gdx)
    implementation(libs.gdx.backend.android)
    implementation(libs.material)
    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-armeabi-v7a")
    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-arm64-v8a")
    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-x86")
    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-x86_64")
    natives("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-armeabi-v7a")
    natives("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-arm64-v8a")
    natives("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-x86")
    natives("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-x86_64")
}

abstract class CopyAndroidNatives : DefaultTask() {
    @get:InputFiles
    abstract val nativesFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val out = outputDir.get().asFile
        nativesFiles.forEach { jar ->
            val abi = jar.name.removeSuffix(".jar").substringAfterLast("natives-")
            val abiDir = File(out, abi)
            abiDir.mkdirs()
            ZipFile(jar).use { zip ->
                zip.entries().asSequence()
                    .filter { !it.isDirectory && it.name.endsWith(".so") }
                    .forEach { e ->
                        val target = File(abiDir, e.name.substringAfterLast('/'))
                        zip.getInputStream(e).use { ins ->
                            target.outputStream().use { ous ->
                                ins.copyTo(ous)
                            }
                        }
                    }
            }
        }
    }
}

tasks.register<CopyAndroidNatives>("copyAndroidNatives") {
    nativesFiles.from(natives)
    outputDir.set(layout.projectDirectory.dir("src/main/jniLibs"))
}

tasks.named("preBuild") { dependsOn("copyAndroidNatives") }
