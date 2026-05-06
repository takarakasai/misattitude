import org.gradle.process.ExecOperations
import javax.inject.Inject

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.takarakasai.misattitude"
    compileSdk = 34

    defaultConfig {
        // applicationId は Google Play 公開時に固定される一意の ID。
        // 一度公開すると変更不可なので慎重に。
        applicationId = "io.github.takarakasai.misattitude"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // ---------------------------------------------------------------------------
    // リリース署名設定
    //
    // 鍵情報は ~/.gradle/gradle.properties に下記キーで保存する想定（リポジトリ外）:
    //   MISATTITUDE_STORE_FILE=C:/path/to/release.jks
    //   MISATTITUDE_STORE_PASSWORD=...
    //   MISATTITUDE_KEY_ALIAS=misattitude
    //   MISATTITUDE_KEY_PASSWORD=...
    // 設定が無い環境（CI 検証ビルド等）では release ビルドはデバッグ署名で
    // 続行できるよう signingConfig は条件付きで割り当てる。
    // ---------------------------------------------------------------------------
    val storeFilePath: String? = (project.findProperty("MISATTITUDE_STORE_FILE") as String?)
        ?: System.getenv("MISATTITUDE_STORE_FILE")
    val storePass: String? = (project.findProperty("MISATTITUDE_STORE_PASSWORD") as String?)
        ?: System.getenv("MISATTITUDE_STORE_PASSWORD")
    val releaseKeyAlias: String? = (project.findProperty("MISATTITUDE_KEY_ALIAS") as String?)
        ?: System.getenv("MISATTITUDE_KEY_ALIAS")
    val releaseKeyPass: String? = (project.findProperty("MISATTITUDE_KEY_PASSWORD") as String?)
        ?: System.getenv("MISATTITUDE_KEY_PASSWORD")
    val hasReleaseKeystore = storeFilePath != null && file(storeFilePath).exists()

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(storeFilePath!!)
                storePassword = storePass
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                logger.lifecycle(
                    "[Misattitude] release keystore not found; release build will use debug signing. " +
                        "Set MISATTITUDE_STORE_FILE in ~/.gradle/gradle.properties before publishing."
                )
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        // BuildConfig.VERSION_NAME / VERSION_CODE を実行時に参照したいので有効化。
        // AGP 8.x では既定でオフなので明示的に true にする必要がある。
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)

    implementation(libs.filament.android)
    implementation(libs.filament.utils.android)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}

// ---------------------------------------------------------------------------
// Filament material compilation
//
// Filament `.mat` source must be compiled to `.filamat` binaries with `matc`,
// shipped in the Filament release tarball (https://github.com/google/filament/releases).
// Set MATC_PATH (env var) or matcPath (gradle property) to the matc executable.
// Recommended (persistent across PowerShell sessions): add to ~/.gradle/gradle.properties:
//   matcPath=C:/tools/filament-1.54.5/bin/matc.exe
//
// Then compiled `.filamat` files are written into src/main/assets/.
//
// Implementation notes:
//   * Configuration-Cache safe: uses an injected `ExecOperations` instead of
//     `project.exec`, and exposes inputs/outputs as task properties.
//   * `@SkipWhenEmpty` on `matSources` means: if no `.mat` files exist under
//     `src/main/materials/`, this task is automatically skipped, and `matc`
//     is NOT required. This matches Misattitude's current setup (no
//     precompiled materials) — the build proceeds with MATC_PATH unset.
//   * If `.mat` files do exist but `matcPath` is blank, the task fails with
//     a clear message at execution time.
// ---------------------------------------------------------------------------
abstract class CompileFilamentMaterials : DefaultTask() {
    @get:InputFiles
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val matSources: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    abstract val matcPath: Property<String>

    @get:Inject
    abstract val execOps: ExecOperations

    @TaskAction
    fun compile() {
        val matc = matcPath.get()
        if (matc.isBlank()) {
            throw GradleException(
                "matc not found but .mat sources exist. " +
                    "Set MATC_PATH env var or matcPath gradle property. " +
                    "See app/build.gradle.kts for details."
            )
        }
        val outDir = outputDir.get().asFile
        outDir.mkdirs()
        matSources.forEach { mat ->
            val out = outDir.resolve(mat.nameWithoutExtension + ".filamat")
            execOps.exec {
                commandLine(matc, "-p", "mobile", "-a", "opengl", "-o", out.absolutePath, mat.absolutePath)
            }
        }
    }
}

val matcPathProperty: String? = (project.findProperty("matcPath") as String?)
    ?: System.getenv("MATC_PATH")

val compileMaterials = tasks.register<CompileFilamentMaterials>("compileMaterials") {
    group = "filament"
    description = "Compile Filament .mat sources to .filamat binaries with matc"
    matSources.from(
        fileTree(layout.projectDirectory.dir("src/main/materials")) { include("*.mat") }
    )
    outputDir.set(layout.projectDirectory.dir("src/main/assets"))
    matcPath.set(matcPathProperty ?: "")
}

tasks.named("preBuild") { dependsOn(compileMaterials) }
