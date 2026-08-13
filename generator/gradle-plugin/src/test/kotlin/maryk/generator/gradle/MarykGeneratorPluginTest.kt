package maryk.generator.gradle

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome

class MarykGeneratorPluginTest {
    @Test
    fun registersTasksAndWiresGeneratedSourcesIntoJvmMain() {
        val project = fixture()
        project.resolve("build.gradle.kts").writeText(
            """
            plugins {
                kotlin("jvm") version "2.4.0"
                id("io.maryk.generator")
            }

            marykGenerator {
                packageName.set("example.generated")
            }

            tasks.register("printMainSources") {
                doLast {
                    println(kotlin.sourceSets.getByName("main").kotlin.srcDirs.joinToString())
                }
            }
            """.trimIndent(),
        )

        val result = runner(project, "tasks", "--all").build()
        assertTrue(result.output.contains("marykGenerateModels"))
        assertTrue(result.output.contains("marykCheckSchemaCompatibility"))
        assertTrue(result.output.contains("marykUpdateSchemaBaseline"))

        val sources = runner(project, "printMainSources").build()
        assertTrue(sources.output.contains("build/generated/maryk"))
    }

    @Test
    fun wiresGeneratedSourcesIntoKotlinMultiplatformCommonMain() {
        val project = fixture()
        project.resolve("build.gradle.kts").writeText(
            """
            plugins {
                kotlin("multiplatform") version "2.4.0"
                id("io.maryk.generator")
            }

            kotlin {
                jvm()
            }

            marykGenerator {
                packageName.set("example.generated")
            }

            tasks.register("printCommonSources") {
                doLast {
                    println(kotlin.sourceSets.getByName("commonMain").kotlin.srcDirs.joinToString())
                }
            }
            """.trimIndent(),
        )

        val result = runner(project, "printCommonSources").build()

        assertTrue(result.output.contains("build/generated/maryk"))
    }

    @Test
    fun wiresGeneratedSourcesIntoAndroidMain() {
        val project = fixture()
        project.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("com.android.library") version "9.2.1"
                id("io.maryk.generator")
            }

            android {
                namespace = "example.fixture"
                compileSdk = 36
            }

            marykGenerator {
                packageName.set("example.generated")
            }

            tasks.register("printAndroidSources") {
                doLast {
                    val sourceSets = android.javaClass.methods
                        .first { it.name == "getSourceSets" && it.parameterCount == 0 }
                        .invoke(android)
                    val main = sourceSets.javaClass.methods
                        .first { it.name == "getByName" && it.parameterCount == 1 }
                        .invoke(sourceSets, "main")
                    val kotlinSources = main.javaClass.methods
                        .first { it.name == "getKotlin" && it.parameterCount == 0 }
                        .invoke(main)
                    val directories = kotlinSources.javaClass.methods
                        .first { it.name == "getDirectories" && it.parameterCount == 0 }
                        .invoke(kotlinSources)
                    println(directories)
                }
            }
            """.trimIndent(),
        )

        val result = runner(project, "printAndroidSources").build()

        assertTrue(result.output.contains("build/generated/maryk"))
    }

    @Test
    fun refusesConfiguredAndroidKotlinSourceDirectoryAsOutput() {
        val project = fixture()
        project.resolve("src/main/maryk").createDirectories()
            .resolve("person.yaml").writeText(schema("Person"))
        project.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("com.android.library") version "9.2.1"
                id("io.maryk.generator")
            }

            android {
                namespace = "example.fixture"
                compileSdk = 36
            }

            val androidSourceSets = extensions.getByName("android").javaClass.methods
                .first { it.name == "getSourceSets" && it.parameterCount == 0 }
                .invoke(extensions.getByName("android"))
            val androidMain = androidSourceSets.javaClass.methods
                .first { it.name == "getByName" && it.parameterCount == 1 }
                .invoke(androidSourceSets, "main")
            val androidKotlin = androidMain.javaClass.methods
                .first { it.name == "getKotlin" && it.parameterCount == 0 }
                .invoke(androidMain)
            androidKotlin.javaClass.methods
                .first { it.name == "srcDir" && it.parameterCount == 1 }
                .invoke(androidKotlin, "custom/models")

            marykGenerator {
                packageName.set("example.generated")
                outputDirectory.set(layout.projectDirectory.dir("custom/models"))
            }
            """.trimIndent(),
        )
        assertFalse(project.resolve("custom/models").exists())

        val result = runner(project, "marykGenerateModels").buildAndFail()

        assertTrue(result.output.contains("source directory"))
        assertFalse(project.resolve("custom/models/Person.kt").exists())
    }

    @Test
    fun generationIsIncrementalAndRemovesStaleManagedFiles() {
        val project = fixture()
        project.resolve("src/main/maryk").createDirectories()
            .resolve("person.yaml").writeText(schema("Person"))
        val stale = project.resolve("build/generated/maryk/Stale.kt")
        stale.parent.createDirectories()
        stale.writeText("stale")
        stale.parent.resolve(".maryk-generator-output").writeText("Managed by the Maryk generator.")

        val first = runner(project, "marykGenerateModels").build()
        assertEquals(TaskOutcome.SUCCESS, first.task(":marykGenerateModels")?.outcome)
        assertFalse(stale.exists())
        assertTrue(project.resolve("build/generated/maryk/Person.kt").readText().contains("object Person"))

        val second = runner(project, "marykGenerateModels").build()
        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":marykGenerateModels")?.outcome)
    }

    @Test
    fun generationSupportsConfigurationCache() {
        val project = fixture()
        project.resolve("src/main/maryk").createDirectories()
            .resolve("person.yaml").writeText(schema("Person"))

        val first = runner(project, "marykGenerateModels", "--configuration-cache").build()
        assertEquals(TaskOutcome.SUCCESS, first.task(":marykGenerateModels")?.outcome)

        val second = runner(project, "marykGenerateModels", "--configuration-cache").build()
        assertTrue(second.output.contains("Reusing configuration cache."))
    }

    @Test
    fun refusesConfiguredKotlinSourceDirectoryAsOutput() {
        val project = fixture()
        project.resolve("src/main/maryk").createDirectories()
            .resolve("person.yaml").writeText(schema("Person"))
        project.resolve("build.gradle.kts").writeText(
            """
            plugins {
                kotlin("jvm") version "2.4.0"
                id("io.maryk.generator")
            }

            kotlin {
                sourceSets.named("main") {
                    kotlin.srcDir("custom/models")
                }
            }

            marykGenerator {
                packageName.set("example.generated")
                outputDirectory.set(layout.projectDirectory.dir("custom/models"))
            }
            """.trimIndent(),
        )
        assertFalse(project.resolve("custom/models").exists())

        val result = runner(project, "marykGenerateModels").buildAndFail()

        assertTrue(result.output.contains("source directory"))
        assertFalse(project.resolve("custom/models/Person.kt").exists())
    }

    @Test
    fun generationReportsMissingAndMalformedSchemas() {
        val project = fixture()

        val missing = runner(project, "marykGenerateModels").buildAndFail()
        assertTrue(missing.output.contains("No Maryk schemas found"))

        project.resolve("src/main/maryk").createDirectories()
            .resolve("broken.yaml").writeText("not: [valid")
        val malformed = runner(project, "marykGenerateModels").buildAndFail()
        assertTrue(malformed.output.contains("broken.yaml"))
        assertTrue(malformed.output.contains("Could not parse Maryk schema"))
    }

    @Test
    fun compatibilityFailsWithoutMutatingBaselineAndExplicitUpdateMakesItPass() {
        val project = fixture()
        project.resolve("src/main/maryk").createDirectories()
            .resolve("person.yaml").writeText(schema("Person", required = true))
        val baseline = project.resolve("schemas/baseline").createDirectories()
            .resolve("person.yaml")
        baseline.writeText(schema("Person", required = false))
        val baselineBefore = baseline.readText()

        val failure = runner(project, "marykCheckSchemaCompatibility").buildAndFail()
        assertTrue(failure.output.contains("Person:"))
        assertEquals(baselineBefore, baseline.readText())

        val update = runner(project, "marykUpdateSchemaBaseline").build()
        assertEquals(TaskOutcome.SUCCESS, update.task(":marykUpdateSchemaBaseline")?.outcome)
        assertEquals(schema("Person", required = true), baseline.readText())

        val check = runner(project, "marykCheckSchemaCompatibility").build()
        assertEquals(TaskOutcome.SUCCESS, check.task(":marykCheckSchemaCompatibility")?.outcome)
    }

    @Test
    fun baselineUpdatePreservesNestedSchemaPaths() {
        val project = fixture()
        val schemas = project.resolve("schemas/current")
        schemas.resolve("alpha/model.yaml").also {
            it.parent.createDirectories()
            it.writeText(schema("Alpha"))
        }
        schemas.resolve("beta/model.yaml").also {
            it.parent.createDirectories()
            it.writeText(schema("Beta"))
        }
        project.configureSchemas("schemas/current")

        val update = runner(project, "marykUpdateSchemaBaseline").build()

        assertEquals(TaskOutcome.SUCCESS, update.task(":marykUpdateSchemaBaseline")?.outcome)
        assertEquals(schema("Alpha"), project.resolve("schemas/baseline/alpha/model.yaml").readText())
        assertEquals(schema("Beta"), project.resolve("schemas/baseline/beta/model.yaml").readText())
        val check = runner(project, "marykCheckSchemaCompatibility").build()
        assertEquals(TaskOutcome.SUCCESS, check.task(":marykCheckSchemaCompatibility")?.outcome)
    }

    @Test
    fun baselineUpdatePreservesExistingBaselineWhenSchemaRootsConflict() {
        val project = fixture()
        project.resolve("schemas/first/model.yaml").also {
            it.parent.createDirectories()
            it.writeText(schema("First"))
        }
        project.resolve("schemas/second/model.yaml").also {
            it.parent.createDirectories()
            it.writeText(schema("Second"))
        }
        val baseline = project.resolve("schemas/baseline/existing.yaml")
        baseline.parent.createDirectories()
        baseline.writeText(schema("Existing"))
        project.configureSchemas("schemas/first", "schemas/second")

        val failure = runner(project, "marykUpdateSchemaBaseline").buildAndFail()

        assertTrue(failure.output.contains("conflicting relative path"))
        assertEquals(schema("Existing"), baseline.readText())
        assertFalse(project.resolve("schemas/baseline/model.yaml").exists())
    }

    @Test
    fun compatibilityAcceptsMissingDefaultBaselineForNewModels() {
        val project = fixture()
        project.resolve("src/main/maryk").createDirectories()
            .resolve("person.yaml").writeText(schema("Person"))

        val check = runner(project, "marykCheckSchemaCompatibility").build()

        assertEquals(TaskOutcome.SUCCESS, check.task(":marykCheckSchemaCompatibility")?.outcome)
        assertEquals(
            "Maryk schemas are compatible\n",
            project.resolve("build/reports/maryk/schema-compatibility.txt").readText(),
        )
    }

    private fun fixture(): Path {
        val project = createTempDirectory()
        project.resolve("settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories {
                    mavenCentral()
                    google()
                    gradlePluginPortal()
                }
            }
            rootProject.name = "fixture"
            """.trimIndent(),
        )
        project.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("io.maryk.generator")
            }

            marykGenerator {
                packageName.set("example.generated")
            }
            """.trimIndent(),
        )
        return project
    }

    private fun Path.configureSchemas(vararg roots: String) {
        resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("io.maryk.generator")
            }

            marykGenerator {
                packageName.set("example.generated")
                schemas.setFrom(${roots.joinToString { "\"$it\"" }})
            }
            """.trimIndent(),
        )
    }

    private fun runner(project: Path, vararg arguments: String) = GradleRunner.create()
        .withProjectDir(project.toFile())
        .withArguments(*arguments, "--stacktrace")
        .withPluginClasspath()
        .forwardOutput()

    private fun schema(name: String, required: Boolean = true) = """
        name: $name
        ? 1: value
        : !String
          required: $required
    """.trimIndent()
}
