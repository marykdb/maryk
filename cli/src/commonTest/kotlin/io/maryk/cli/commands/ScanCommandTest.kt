package io.maryk.cli.commands

import io.maryk.cli.CliEnvironment
import io.maryk.cli.CliState
import io.maryk.cli.DirectoryResolution
import io.maryk.cli.RocksDbStoreConnection
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertTrue

class ScanCommandTest {
    private val environment = object : CliEnvironment {
        override fun resolveDirectory(path: String): DirectoryResolution = DirectoryResolution.Success(path)
    }

    @Test
    fun errorsWhenNotConnected() {
        val state = CliState()

        val result = ScanCommand().execute(
            CommandContext(CommandRegistry(state, environment), state, environment),
            listOf("SimpleMarykModel"),
        )

        assertTrue(result.isError)
        assertTrue(result.lines.first().contains("Not connected"))
    }

    @Test
    fun showsUsageWhenArgumentsMissing() {
        val state = connectedState()

        val result = ScanCommand().execute(
            CommandContext(CommandRegistry(state, environment), state, environment),
            emptyList(),
        )

        assertTrue(result.isError)
        assertTrue(result.lines.first().startsWith("Usage: scan"))
    }

    @Test
    fun errorsOnUnknownOption() {
        val state = connectedState()

        val result = ScanCommand().execute(
            CommandContext(CommandRegistry(state, environment), state, environment),
            listOf("SimpleMarykModel", "--nope"),
        )

        assertTrue(result.isError)
        assertTrue(result.lines.single().contains("Unknown option"))
    }

    @Test
    fun errorsWhenLimitIsZero() {
        val state = connectedState()

        val result = ScanCommand().execute(
            CommandContext(CommandRegistry(state, environment), state, environment),
            listOf("SimpleMarykModel", "--limit", "0"),
        )

        assertTrue(result.isError)
        assertTrue(result.lines.single().contains("`--limit` must be greater than 0."))
    }

    @Test
    fun errorsWhenMaxCharsTooLow() {
        val state = connectedState()

        val result = ScanCommand().execute(
            CommandContext(CommandRegistry(state, environment), state, environment),
            listOf("SimpleMarykModel", "--max-chars", "10"),
        )

        assertTrue(result.isError)
        assertTrue(result.lines.single().contains("`--max-chars` must be at least 20."))
    }

    private fun connectedState(): CliState {
        val store = FakeDataStore(
            dataModelsById = mapOf(1u to SimpleMarykModel),
        )
        return CliState().apply {
            replaceConnection(RocksDbStoreConnection("/data/store", store))
        }
    }
}
