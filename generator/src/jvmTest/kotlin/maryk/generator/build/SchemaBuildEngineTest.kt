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
import kotlin.test.assertSame
import kotlin.test.assertTrue
import maryk.core.properties.definitions.ReferenceDefinition

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
    fun preservesExistingOutputWhenWritingGeneratedFilesFails() {
        val schemas = createTempDirectory()
        schemas.resolve("alpha.yaml").writeText(schema("Alpha"))
        schemas.resolve("broken.yaml").writeText(schema("Broken/Model"))
        val output = createTempDirectory()
        output.resolve("Existing.kt").writeText("existing")

        assertFailsWith<SchemaBuildException> {
            SchemaBuildEngine.generate(
                schemaFiles = SchemaBuildEngine.discoverSchemas(listOf(schemas)),
                packageName = "example.generated",
                outputDirectory = output,
            )
        }

        assertEquals("existing", output.resolve("Existing.kt").readText())
        assertFalse(Files.exists(output.resolve("Alpha.kt")))
    }

    @Test
    fun rejectsModelNamesThatCouldEscapeTheGenerationStagingDirectory() {
        for (unsafeName in listOf(
            "../X",
            "nested/X",
            "nested\\X",
            "C:\\generated\\X",
            "\\\\server\\share\\X",
            "/tmp/maryk-generator-escaped/X",
            "Trailing.",
            "Trailing ",
            "CON",
            "nul",
            "COM1",
            "lpt9",
            "control\u007f",
            "control\u009f",
        )) {
            val schemas = createTempDirectory()
            schemas.resolve("model.json").writeText(jsonSchema(unsafeName))
            val output = createTempDirectory()
            output.resolve("Existing.kt").writeText("existing")

            try {
                val exception = assertFailsWith<SchemaBuildException> {
                    SchemaBuildEngine.generate(
                        schemaFiles = SchemaBuildEngine.discoverSchemas(listOf(schemas)),
                        packageName = "example.generated",
                        outputDirectory = output,
                    )
                }

                assertTrue(exception.message.orEmpty().contains(unsafeName))
                assertEquals("existing", output.resolve("Existing.kt").readText())
            } finally {
                Files.deleteIfExists(output.parent.resolve("X.kt"))
            }
        }
    }

    @Test
    fun resolvesYamlForwardReferenceToJsonModel() {
        val schemas = createTempDirectory()
        schemas.resolve("a.yaml").writeText(referenceSchema("Alpha", "Beta"))
        schemas.resolve("b.json").writeText(jsonSchema("Beta"))

        val loaded = SchemaBuildEngine.load(SchemaBuildEngine.discoverSchemas(listOf(schemas)))
        val byName = loaded.associateBy { it.model.Meta.name }

        assertSame(byName.getValue("Beta").model, byName.getValue("Alpha").referenceDefinition().dataModel)
    }

    @Test
    fun generatesMixedFormatMutualReferences() {
        val schemas = createTempDirectory()
        schemas.resolve("a.json").writeText(jsonReferenceSchema("Alpha", "Beta"))
        schemas.resolve("b.yml").writeText(referenceSchema("Beta", "Alpha"))
        val output = createTempDirectory()

        val loaded = SchemaBuildEngine.load(SchemaBuildEngine.discoverSchemas(listOf(schemas)))
        val byName = loaded.associateBy { it.model.Meta.name }
        assertSame(byName.getValue("Beta").model, byName.getValue("Alpha").referenceDefinition().dataModel)
        assertSame(byName.getValue("Alpha").model, byName.getValue("Beta").referenceDefinition().dataModel)

        val result = SchemaBuildEngine.generate(
            schemaFiles = SchemaBuildEngine.discoverSchemas(listOf(schemas)),
            packageName = "example.generated",
            outputDirectory = output,
        )

        assertEquals(listOf("Alpha.kt", "Beta.kt"), result.files.map { it.fileName.toString() })
        val alphaSource = output.resolve("Alpha.kt").readText()
        val betaSource = output.resolve("Beta.kt").readText()
        assertTrue(alphaSource.contains("import maryk.core.properties.types.Key"))
        assertTrue(alphaSource.contains("dataModel = { Beta }"))
        assertTrue(betaSource.contains("import maryk.core.properties.types.Key"))
        assertTrue(betaSource.contains("dataModel = { Alpha }"))
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

    @Test
    fun acceptsEmptyCompatibilityBaselineForNewModels() {
        val current = createTempDirectory()
        val baseline = createTempDirectory()
        current.resolve("person.yaml").writeText(schema("Person"))

        val report = SchemaBuildEngine.checkCompatibility(
            currentSchemaFiles = SchemaBuildEngine.discoverSchemas(listOf(current)),
            baselineSchemaFiles = SchemaBuildEngine.discoverSchemas(listOf(baseline)),
        )

        assertTrue(report.isCompatible)
        assertEquals(emptyList(), report.reasons)
    }

    @Test
    fun rejectsEmptyCurrentSchemasDuringCompatibilityCheck() {
        val baseline = createTempDirectory()
        baseline.resolve("person.yaml").writeText(schema("Person"))

        val exception = assertFailsWith<SchemaBuildException> {
            SchemaBuildEngine.checkCompatibility(
                currentSchemaFiles = emptyList(),
                baselineSchemaFiles = SchemaBuildEngine.discoverSchemas(listOf(baseline)),
            )
        }

        assertEquals(
            "No Maryk schemas found; configure at least one .yaml, .yml, or .json schema",
            exception.message,
        )
    }

    private fun schema(name: String, required: Boolean = true) = """
        name: $name
        ? 1: value
        : !String
          required: $required
    """.trimIndent()

    private fun jsonSchema(name: String): String {
        val escapedName = buildString {
            for (character in name) {
                when (character) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    in '\u0000'..'\u001f', in '\u007f'..'\u009f' -> append("\\u${character.code.toString(16).padStart(4, '0')}")
                    else -> append(character)
                }
            }
        }
        return """
        {
          "name": "$escapedName",
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

    private fun referenceSchema(name: String, referenceName: String) = """
        name: $name
        ? 1: reference
        : !Reference
          required: false
          final: false
          unique: false
          dataModel: $referenceName
    """.trimIndent()

    private fun jsonReferenceSchema(name: String, referenceName: String) = """
        {
          "name": "$name",
          "properties": [{
            "index": 1,
            "name": "reference",
            "definition": ["Reference", {
              "required": false,
              "final": false,
              "unique": false,
              "dataModel": "$referenceName"
            }]
          }]
        }
    """.trimIndent()

    private fun LoadedSchema.referenceDefinition() =
        model["reference"]!!.definition as ReferenceDefinition<*>
}
