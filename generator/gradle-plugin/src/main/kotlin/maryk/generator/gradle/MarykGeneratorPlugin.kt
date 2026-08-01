package maryk.generator.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class MarykGeneratorPlugin : Plugin<Project> {
    override fun apply(project: Project) = with(project) {
        val extension = extensions.create("marykGenerator", MarykGeneratorExtension::class.java)
        val generate = tasks.register("marykGenerateModels", MarykGenerateModelsTask::class.java) { task ->
            task.group = "maryk"
            task.description = "Generates Kotlin models from Maryk schemas."
            task.schemas.from(extension.schemas)
            task.packageName.set(extension.packageName)
            task.generatorVersion.set(provider { project.version.toString() })
            task.outputDirectory.set(extension.outputDirectory)
        }
        tasks.register("marykCheckSchemaCompatibility", MarykCheckSchemaCompatibilityTask::class.java) { task ->
            task.group = "verification"
            task.description = "Checks current Maryk schemas against the configured baseline."
            task.schemas.from(extension.schemas)
            task.baselines.from(extension.baselineDirectory)
            task.allowRemovedModels.set(extension.allowRemovedModels)
            task.compatibilityFormatVersion.set(1)
            task.reportFile.set(layout.buildDirectory.file("reports/maryk/schema-compatibility.txt"))
        }
        tasks.register("marykUpdateSchemaBaseline", MarykUpdateSchemaBaselineTask::class.java) { task ->
            task.group = "maryk"
            task.description = "Explicitly replaces the Maryk schema baseline with current schemas."
            task.schemas.from(extension.schemas)
            task.baselineDirectory.set(extension.baselineDirectory)
        }

        pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
            extensions.configure(KotlinJvmProjectExtension::class.java) { kotlinExtension ->
                kotlinExtension.sourceSets.named("main") { sourceSet ->
                    sourceSet.kotlin.srcDir(generate.flatMap { it.outputDirectory })
                }
            }
        }
        pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            extensions.configure(KotlinMultiplatformExtension::class.java) { kotlinExtension ->
                kotlinExtension.sourceSets.named("commonMain") { sourceSet ->
                    sourceSet.kotlin.srcDir(generate.flatMap { it.outputDirectory })
                }
            }
        }
        listOf("com.android.application", "com.android.library").forEach { pluginId ->
            pluginManager.withPlugin(pluginId) {
                afterEvaluate {
                    wireAndroidSourceDirectory(
                        extensions.getByName("android"),
                        generate.get().outputDirectory.get().asFile,
                    )
                }
                tasks.matching {
                    it.name.startsWith("compile") && it.name.contains("Kotlin")
                }.configureEach { task ->
                    task.dependsOn(generate)
                }
            }
        }
    }
}

private fun wireAndroidSourceDirectory(android: Any, sourceDirectory: Any) {
    val sourceSets = android.javaClass.methods
        .first { it.name == "getSourceSets" && it.parameterCount == 0 }
        .invoke(android)
    val main = sourceSets.javaClass.methods
        .first { it.name == "getByName" && it.parameterTypes.contentEquals(arrayOf(String::class.java)) }
        .invoke(sourceSets, "main")
    val kotlin = main.javaClass.methods
        .first { it.name == "getKotlin" && it.parameterCount == 0 }
        .invoke(main)
    kotlin.javaClass.methods
        .first { it.name == "srcDir" && it.parameterCount == 1 }
        .invoke(kotlin, sourceDirectory)
}
