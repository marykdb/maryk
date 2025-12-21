package io.maryk.cli.commands

import io.maryk.cli.CliEnvironment
import io.maryk.cli.CliState
import io.maryk.cli.DirectoryResolution
import io.maryk.cli.InteractionResult
import io.maryk.cli.RocksDbStoreConnection
import maryk.core.definitions.Definitions
import maryk.core.definitions.MarykPrimitive
import maryk.core.definitions.PrimitiveType
import maryk.core.models.RootDataModel
import maryk.core.query.DefinitionsConversionContext
import maryk.test.models.SimpleMarykModel
import maryk.test.models.TestMarykModel
import maryk.yaml.YamlReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ModelCommandTest {
    private fun buildContext(
        state: CliState,
        environment: CliEnvironment,
        registry: CommandRegistry = CommandRegistry(state, environment),
    ): CommandContext = CommandContext(registry, state, environment)

    @Test
    fun errorsWhenNotConnected() {
        val state = CliState()
        val command = ModelCommand()

        val result = command.execute(
            buildContext(state, FakeEnvironment { it }),
            emptyList(),
        )

        assertTrue(result.isError)
        assertTrue(result.lines.first().contains("Not connected"))
    }

    @Test
    fun showsHelpWithoutConnection() {
        val state = CliState()
        val command = ModelCommand()

        val result = command.execute(buildContext(state, FakeEnvironment { it }), listOf("--help"))

        assertFalse(result.isError)
        assertEquals("Usage:", result.lines.first())
    }

    @Test
    fun showsYamlWhenModelIsNamed() {
        val store = FakeDataStore(
            dataModelsById = mapOf(1u to SimpleMarykModel),
        )
        val state = CliState().apply {
            replaceConnection(RocksDbStoreConnection("/data/store", store))
        }

        val command = ModelCommand()
        val context = buildContext(state, FakeEnvironment { it })
        val result = command.execute(context, listOf(SimpleMarykModel.Meta.name))

        assertFalse(result.isError)
        assertTrue(result.lines.any { it.startsWith("${SimpleMarykModel.Meta.name}:") })
        assertContainsDefinition(result.lines, SimpleMarykModel.Meta.name, PrimitiveType.RootModel)
    }

    @Test
    fun showsYamlWhenModelIdIsProvided() {
        val store = FakeDataStore(
            dataModelsById = mapOf(7u to TestMarykModel),
        )
        val state = CliState().apply {
            replaceConnection(RocksDbStoreConnection("/data/store", store))
        }

        val command = ModelCommand()
        val context = buildContext(state, FakeEnvironment { it })
        val result = command.execute(context, listOf("7"))

        assertFalse(result.isError)
        assertTrue(result.lines.any { it.startsWith("${TestMarykModel.Meta.name}:") })
        assertContainsDefinition(result.lines, TestMarykModel.Meta.name, PrimitiveType.RootModel)

        val dependencies = mutableListOf<MarykPrimitive>()
        TestMarykModel.getAllDependencies(dependencies)
        assertTrue(dependencies.isNotEmpty(), "Expected TestMarykModel to have at least one dependency for this test.")
        val dependencyName = dependencies.first().Meta.name
        assertTrue(
            result.lines.any { it.startsWith("$dependencyName:") },
            "Expected dependency `$dependencyName` to be included as YAML key.",
        )
    }

    @Test
    fun startsSelectorWhenNoArgumentsProvided() {
        val store = FakeDataStore(
            dataModelsById = mapOf(
                1u to SimpleMarykModel,
                2u to TestMarykModel,
            ),
        )
        val state = CliState().apply {
            replaceConnection(RocksDbStoreConnection("/data/store", store))
        }
        val command = ModelCommand()
        val context = buildContext(state, FakeEnvironment { it })

        val result = command.execute(context, emptyList())

        assertFalse(result.isError)
        assertNotNull(state.currentInteraction)
        assertTrue(result.lines.first().contains("Select a model"))

        val interaction = state.currentInteraction!!
        val outcome = interaction.onInput(TestMarykModel.Meta.name)
        val complete = assertIs<InteractionResult.Complete>(outcome)
        assertTrue(complete.lines.any { it.startsWith("${TestMarykModel.Meta.name}:") })
        assertContainsDefinition(complete.lines, TestMarykModel.Meta.name, PrimitiveType.RootModel)
    }

    @Test
    fun reportsAmbiguousPrefix() {
        val store = FakeDataStore(
            dataModelsById = mapOf(
                1u to AlphaModel,
                2u to AlphaOtherModel,
            ),
        )
        val state = CliState().apply {
            replaceConnection(RocksDbStoreConnection("/data/store", store))
        }
        val command = ModelCommand()

        val result = command.execute(buildContext(state, FakeEnvironment { it }), listOf("Alpha"))

        assertTrue(result.isError)
        assertTrue(result.lines.first().contains("ambiguous"))
        assertTrue(result.lines.any { it.contains(AlphaModel.Meta.name) })
        assertTrue(result.lines.any { it.contains(AlphaOtherModel.Meta.name) })
    }

    @Test
    fun reportsUnknownModelName() {
        val store = FakeDataStore(dataModelsById = mapOf(1u to SimpleMarykModel))
        val state = CliState().apply {
            replaceConnection(RocksDbStoreConnection("/data/store", store))
        }

        val command = ModelCommand()
        val context = buildContext(state, FakeEnvironment { it })
        val result = command.execute(context, listOf("Missing"))

        assertTrue(result.isError)
        assertTrue(result.lines.first().contains("not found"))
    }

    @Test
    fun reportsEmptyStore() {
        val store = FakeDataStore()
        val state = CliState().apply {
            replaceConnection(RocksDbStoreConnection("/data/store", store))
        }

        val command = ModelCommand()
        val context = buildContext(state, FakeEnvironment { it })
        val result = command.execute(context, emptyList())

        assertFalse(result.isError)
        assertEquals(listOf("No models found in the connected store."), result.lines)
    }

    private fun assertContainsDefinition(lines: List<String>, name: String, primitiveType: PrimitiveType) {
        val yaml = lines.joinToString(separator = "\n", postfix = "\n")
        val context = DefinitionsConversionContext()
        val reader = createYamlReader(yaml)
        val values = Definitions.Serializer.readJson(reader, context)
        val definitions = Definitions(values)

        assertTrue(
            definitions.definitions.any { it.Meta.name == name && it.Meta.primitiveType == primitiveType },
            "Expected definition for $primitiveType `$name` in YAML output, got: ${definitions.definitions.map { it.Meta.name }}",
        )
    }

    private fun createYamlReader(yaml: String) = run {
        var index = 0
        val defaultTag = "tag:maryk.io,2018:"
        YamlReader(
            defaultTag = defaultTag,
            tagMap = mapOf(defaultTag to emptyMap()),
            allowUnknownTags = true,
        ) {
            yaml.getOrNull(index)?.also { index++ } ?: throw Throwable("0 char encountered")
        }
    }

    private object AlphaModel : RootDataModel<AlphaModel>()
    private object AlphaOtherModel : RootDataModel<AlphaOtherModel>()
}

private class FakeEnvironment(
    private val resolver: (String) -> String,
) : CliEnvironment {
    override fun resolveDirectory(path: String) = DirectoryResolution.Success(resolver(path))
}
