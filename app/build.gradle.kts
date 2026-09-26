import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
}

val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(key: String, env: String): String? =
    (keystoreProperties.getProperty(key) ?: System.getenv(env))?.takeIf { it.isNotBlank() }

val releaseStorePath = signingValue("storeFile", "DEPTHDIVER_STORE_FILE")
    ?.let { rootProject.file(it) }
    ?.takeIf { it.exists() }
val releaseStorePassword = signingValue("storePassword", "DEPTHDIVER_STORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "DEPTHDIVER_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "DEPTHDIVER_KEY_PASSWORD")
val hasReleaseSigning = releaseStorePath != null && releaseStorePassword != null &&
    releaseKeyAlias != null && releaseKeyPassword != null

android {
    namespace = "app.depthdiver"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "app.depthdiver"
        minSdk = 24
        targetSdk = 37
        versionCode = 2
        versionName = "1.1.0"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseStorePath
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            optimization {
                enable = true
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
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
    lint {
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = false
        htmlReport = true
        xmlReport = true
        disable += setOf("GradleDependency", "OldTargetApi", "UnusedResources")
    }
}

val gdxVersion = libs.versions.gdx.get()

val natives = configurations.create("natives")

dependencies {
    implementation(project(":core"))
    implementation(libs.gdx)
    implementation(libs.gdx.backend.android)
    implementation(libs.material)
    // TODO: Add Google Play Games Services for online leaderboards
    // implementation("com.google.android.gms:play-services-games:23.1.0")
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
