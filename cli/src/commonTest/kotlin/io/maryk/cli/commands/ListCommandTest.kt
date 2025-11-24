package io.maryk.cli.commands

import io.maryk.cli.CliEnvironment
import io.maryk.cli.CliState
import io.maryk.cli.DirectoryResolution
import io.maryk.cli.RocksDbStoreConnection
import maryk.test.models.SimpleMarykModel
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ListCommandTest {
    private val environment = object : CliEnvironment {
        override fun resolveDirectory(path: String): DirectoryResolution = DirectoryResolution.Success(path)
    }

    @Test
    fun errorsWhenNotConnected() {
        val state = CliState()
        val result = ListCommand().execute(
            CommandContext(CommandRegistry(state, environment), state, environment),
            emptyList(),
        )

        assertTrue(result.isError)
        assertTrue(result.lines.first().contains("Not connected"))
    }

    @Test
    fun listsModelsFromStore() {
        val store = FakeDataStore(
            dataModelsById = mapOf(
                2u to TestMarykModel,
                1u to SimpleMarykModel,
            ),
        )
        val state = CliState().apply {
            replaceConnection(RocksDbStoreConnection("/data/store", store))
        }

        val result = ListCommand().execute(
            CommandContext(CommandRegistry(state, environment), state, environment),
            emptyList(),
        )

        assertEquals(
            listOf(
                "Models:",
                "1 - SimpleMarykModel",
                "2 - TestMarykModel",
            ),
            result.lines,
        )
    }

    @Test
    fun alignsIdsWithPadding() {
        val store = FakeDataStore(
            dataModelsById = mapOf(
                105u to SimpleMarykModel,
                9u to TestMarykModel,
            ),
        )
        val state = CliState().apply {
            replaceConnection(RocksDbStoreConnection("/data/store", store))
        }

        val result = ListCommand().execute(
            CommandContext(CommandRegistry(state, environment), state, environment),
            emptyList(),
        )

        assertEquals(
            listOf(
                "Models:",
                "  9 - TestMarykModel",
                "105 - SimpleMarykModel",
            ),
            result.lines,
        )
    }

    @Test
    fun reportsEmptyStore() {
        val store = FakeDataStore()
        val state = CliState().apply {
            replaceConnection(RocksDbStoreConnection("/data/store", store))
        }

        val result = ListCommand().execute(
            CommandContext(CommandRegistry(state, environment), state, environment),
            emptyList(),
        )

        assertEquals(listOf("No models found in the connected store."), result.lines)
        assertFalse(result.isError)
    }
}
