package io.maryk.cli.commands

import io.maryk.cli.CliEnvironment
import io.maryk.cli.CliState
import io.maryk.cli.DirectoryResolution
import io.maryk.cli.InteractionResult
import io.maryk.cli.RocksDbStoreConnection
import maryk.core.definitions.MarykPrimitive
import maryk.core.models.RootDataModel
import maryk.test.models.SimpleMarykModel
import maryk.test.models.TestMarykModel
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
        assertContainsModelLine(result.lines, SimpleMarykModel.Meta.name)
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
        assertContainsModelLine(result.lines, TestMarykModel.Meta.name)
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
        assertTrue(result.lines.isEmpty())

        val interaction = state.currentInteraction!!
        val outcome = interaction.onInput(TestMarykModel.Meta.name)
        val complete = assertIs<InteractionResult.Complete>(outcome)
        assertContainsModelLine(complete.lines, TestMarykModel.Meta.name)
    }

    @Test
    fun includesDependenciesWhenRequested() {
        val store = FakeDataStore(
            dataModelsById = mapOf(7u to TestMarykModel),
        )
        val state = CliState().apply {
            replaceConnection(RocksDbStoreConnection("/data/store", store))
        }

        val command = ModelCommand()
        val context = buildContext(state, FakeEnvironment { it })
        val result = command.execute(context, listOf("--with-deps", "7"))

        assertFalse(result.isError)
        assertContainsModelLine(result.lines, TestMarykModel.Meta.name)

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

    private fun assertContainsModelLine(lines: List<String>, name: String) {
        assertTrue(
            lines.any { it.startsWith("$name:") },
            "Expected model `$name` definition header in output.",
        )
    }

    private object AlphaModel : RootDataModel<AlphaModel>()
    private object AlphaOtherModel : RootDataModel<AlphaOtherModel>()
}

private class FakeEnvironment(
    private val resolver: (String) -> String,
) : CliEnvironment {
    override fun resolveDirectory(path: String) = DirectoryResolution.Success(resolver(path))
}
