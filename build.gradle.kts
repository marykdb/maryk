import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

repositories {
    mavenCentral()
    google()
}

plugins {
    id("com.android.library") apply false
}

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
    }
}
allprojects {
    // TODO update to use convention plugins when https://github.com/marykdb/maryk/pull/2 is merged
    tasks.withType<KotlinCompilationTask<*>>().configureEach {
        compilerOptions {
            languageVersion.set(KotlinVersion.KOTLIN_1_9)
            apiVersion.set(languageVersion)
            freeCompilerArgs.addAll(
                "-progressive",
                "-Xallocator=custom", // https://kotlinlang.org/docs/whatsnew19.html#preview-of-custom-memory-allocator
                "-opt-in=kotlinx.cinterop.ExperimentalForeignApi"
            )
//            allWarningsAsErrors.set(true)
            if (this is KotlinJvmCompilerOptions) {
                jvmTarget.set(JvmTarget.JVM_1_8)
            }
        }
    }
    repositories {
        mavenCentral()
        google()
        mavenLocal()
    }
}
