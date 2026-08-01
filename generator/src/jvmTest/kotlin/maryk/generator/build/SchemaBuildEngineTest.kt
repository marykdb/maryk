package maryk.generator.build

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SchemaBuildEngineTest {
    @Test
    fun discoversYamlAndJsonSchemasInPathOrder() {
        val directory = createTempDirectory()
        directory.resolve("z.yml").writeText(schema("Zed"))
        directory.resolve("a.yaml").writeText(schema("Alpha"))
        directory.resolve("nested").createDirectories().resolve("m.json").writeText(jsonSchema("Middle"))
        directory.resolve("ignored.txt").writeText("ignored")

        assertEquals(
            listOf("a.yaml", "nested/m.json", "z.yml"),
            SchemaBuildEngine.discoverSchemas(listOf(directory))
                .map(directory::relativize)
                .map { it.toString() },
        )
    }

    @Test
    fun generatesDeterministicFilesAndRemovesStaleOutput() {
        val schemas = createTempDirectory()
        schemas.resolve("b.yaml").writeText(schema("Beta"))
        schemas.resolve("a.json").writeText(jsonSchema("Alpha"))
        val output = createTempDirectory()
        output.resolve("Stale.kt").writeText("stale")

        val result = SchemaBuildEngine.generate(
            schemaFiles = SchemaBuildEngine.discoverSchemas(listOf(schemas)),
            packageName = "example.generated",
            outputDirectory = output,
        )

        assertEquals(listOf("Alpha.kt", "Beta.kt"), result.files.map { it.fileName.toString() })
        assertFalse(Files.exists(output.resolve("Stale.kt")))
        assertTrue(output.resolve("Alpha.kt").readText().startsWith("package example.generated"))
        val first = result.files.associate { it.fileName.toString() to it.readText() }

        val second = SchemaBuildEngine.generate(
            schemaFiles = SchemaBuildEngine.discoverSchemas(listOf(schemas)),
            packageName = "example.generated",
            outputDirectory = output,
        )
        assertEquals(first, second.files.associate { it.fileName.toString() to it.readText() })
    }

    @Test
    fun rejectsDuplicateModelNamesAndMalformedSchemas() {
        val directory = createTempDirectory()
        directory.resolve("one.yaml").writeText(schema("Duplicate"))
        directory.resolve("two.yaml").writeText(schema("Duplicate"))

        val duplicate = assertFailsWith<SchemaBuildException> {
            SchemaBuildEngine.load(SchemaBuildEngine.discoverSchemas(listOf(directory)))
        }
        assertTrue(duplicate.message.orEmpty().contains("Duplicate model name Duplicate"))

        directory.resolve("two.yaml").writeText("not: [valid")
        val malformed = assertFailsWith<SchemaBuildException> {
            SchemaBuildEngine.load(SchemaBuildEngine.discoverSchemas(listOf(directory)))
        }
        assertTrue(malformed.message.orEmpty().contains("two.yaml"))
    }

    @Test
    fun reportsRemovedModelsAndPropertyIncompatibilitiesDeterministically() {
        val current = createTempDirectory()
        val baseline = createTempDirectory()
        current.resolve("person.yaml").writeText(schema("Person", required = true))
        baseline.resolve("person.yaml").writeText(schema("Person", required = false))
        baseline.resolve("removed.yaml").writeText(schema("Removed"))

        val report = SchemaBuildEngine.checkCompatibility(
            currentSchemaFiles = SchemaBuildEngine.discoverSchemas(listOf(current)),
            baselineSchemaFiles = SchemaBuildEngine.discoverSchemas(listOf(baseline)),
        )

        assertFalse(report.isCompatible)
        assertTrue(report.reasons.first().startsWith("Person:"))
        assertEquals("Removed: baseline model is missing from current schemas", report.reasons.last())

        val allowed = SchemaBuildEngine.checkCompatibility(
            currentSchemaFiles = SchemaBuildEngine.discoverSchemas(listOf(current)),
            baselineSchemaFiles = SchemaBuildEngine.discoverSchemas(listOf(baseline)),
            allowRemovedModels = true,
        )
        assertTrue(allowed.reasons.none { it.startsWith("Removed:") })
    }

    private fun schema(name: String, required: Boolean = true) = """
        name: $name
        ? 1: value
        : !String
          required: $required
    """.trimIndent()

    private fun jsonSchema(name: String) = """
        {
          "name": "$name",
          "properties": [{
            "index": 1,
            "name": "value",
            "definition": ["String", {
              "required": true,
              "final": false,
              "unique": false
            }]
          }]
        }
    """.trimIndent()
}
