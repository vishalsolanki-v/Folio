import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.aboutlibraries)
    alias(libs.plugins.licensee)
}

/**
 * Removes mammoth's SimpleSax from its jar so our Android-compatible copy
 * (src/main/java/org/zwobble/mammoth/internal/xml/parsing/SimpleSax.java) is the only one on the classpath.
 * Every other jar passes through untouched.
 */
abstract class StripMammothSimpleSax : TransformAction<TransformParameters.None> {
    @get:InputArtifact
    abstract val input: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val jar = input.get().asFile
        if (!jar.name.startsWith("mammoth-")) {
            outputs.file(input)
            return
        }
        val stripped = "org/zwobble/mammoth/internal/xml/parsing/SimpleSax"
        ZipInputStream(jar.inputStream()).use { zin ->
            ZipOutputStream(outputs.file("folio-${jar.name}").outputStream()).use { zout ->
                generateSequence { zin.nextEntry }
                    .filterNot { it.name == "$stripped.class" || it.name.startsWith("$stripped$") }
                    .forEach { entry ->
                        zout.putNextEntry(ZipEntry(entry.name))
                        zin.copyTo(zout)
                        zout.closeEntry()
                    }
            }
        }
    }
}

val mammothPatched = Attribute.of("folio.mammothPatched", Boolean::class.javaObjectType)

dependencies {
    attributesSchema { attribute(mammothPatched) }
    artifactTypes.getByName("jar") { attributes.attribute(mammothPatched, false) }
    registerTransform(StripMammothSimpleSax::class) {
        from.attribute(mammothPatched, false).attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar")
        to.attribute(mammothPatched, true).attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar")
    }
}

configurations.configureEach {
    if (isCanBeResolved && name.endsWith("RuntimeClasspath")) {
        attributes.attribute(mammothPatched, true)
    }
}

android {
    namespace = "com.folio.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.folio.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

licensee {
    allow("Apache-2.0")
    allow("MIT")
    allow("BSD-2-Clause")
    allow("BSD-3-Clause")
    allow("Unlicense")
    // tm4e (used by Sora Editor's TextMate module, added in M1): weak per-file copyleft, used unmodified.
    allow("EPL-2.0")
    // Sora Editor is LGPL-2.1: used unmodified, credited, and kept un-obfuscated (see docs/plan.md §9).
    allowDependency("io.github.Rosemoe.sora-editor", "editor", "0.23.6")
    allowDependency("io.github.Rosemoe.sora-editor", "language-textmate", "0.23.6")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.aboutlibraries.compose.m3)
    implementation(libs.pdfium)
    implementation(libs.mammoth)
    implementation(platform(libs.sora.bom))
    implementation(libs.sora.editor)
    implementation(libs.sora.textmate)
    implementation(libs.markdown.renderer.m3)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.coil.svg)
    implementation(libs.telephoto.zoomable.image.coil3)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}
