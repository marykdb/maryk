package io.maryk.cli.commands

import io.maryk.cli.CliEnvironment
import io.maryk.cli.CliState
import io.maryk.cli.DirectoryResolution
import io.maryk.cli.RocksDbStoreConnection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DisconnectCommandTest {
    @Test
    fun disconnectsAndClosesStore() {
        val dataStore = FakeDataStore()
        val state = CliState().apply {
            replaceConnection(RocksDbStoreConnection("/data/store", dataStore))
        }

        val result = DisconnectCommand().execute(
            CommandContext(CommandRegistry(state, TestEnvironment), state, TestEnvironment),
            emptyList(),
        )

        assertFalse(result.isError)
        assertTrue(result.lines.first().contains("Disconnected"))
        assertTrue(dataStore.closed)
        assertTrue(dataStore.listenersClosed)
        assertEquals(null, state.currentConnection)
    }

    @Test
    fun errorsWhenNoConnection() {
        val state = CliState()
        val result = DisconnectCommand().execute(
            CommandContext(CommandRegistry(state, TestEnvironment), state, TestEnvironment),
            emptyList(),
        )

        assertTrue(result.isError)
        assertTrue(result.lines.first().contains("No active"))
    }
}

private object TestEnvironment : CliEnvironment {
    override fun resolveDirectory(path: String): DirectoryResolution = DirectoryResolution.Success(path)
}
