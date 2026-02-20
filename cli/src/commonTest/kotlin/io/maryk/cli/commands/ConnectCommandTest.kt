package io.maryk.cli.commands

import io.maryk.cli.CliEnvironment
import io.maryk.cli.CliState
import io.maryk.cli.DirectoryResolution
import io.maryk.cli.FoundationDbStoreConnection
import io.maryk.cli.InteractionResult
import io.maryk.cli.RocksDbStoreConnection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ConnectCommandTest {
    private fun buildContext(
        state: CliState,
        environment: CliEnvironment,
        registry: CommandRegistry = CommandRegistry(state, environment),
    ): CommandContext = CommandContext(registry, state, environment)

    @Test
    fun startsInteractiveFlowWhenNoArgumentsProvided() {
        val dataStore = FakeDataStore()
        val connector = FakeRocksDbConnector { path ->
            ConnectCommand.RocksDbConnectionOutcome.Success(
                RocksDbStoreConnection(path, dataStore),
            )
        }
        val command = ConnectCommand(connector, FakeFoundationDbConnector())
        val state = CliState()
        val environment = FakeEnvironment { DirectoryResolution.Success("/data/store") }

        val context = buildContext(state, environment)
        val result = command.execute(context, emptyList())

        assertFalse(result.isError)
        assertTrue(result.lines.first().contains("Interactive connect setup"))
        assertTrue(state.hasActiveInteraction())

        val selectionInteraction = state.currentInteraction!!
        val selectionOutcome = selectionInteraction.onInput("1")
        assertTrue(selectionOutcome is InteractionResult.Continue)
        val continueOutcome = selectionOutcome
        assertTrue(continueOutcome.lines.first().contains("Selected RocksDB"))

        state.replaceInteraction(continueOutcome.next, continueOutcome.showIntro)

        val directoryOutcome = continueOutcome.next.onInput("/data/store")
        assertTrue(directoryOutcome is InteractionResult.Complete)
        val completeLines = directoryOutcome.lines
        assertEquals(
            listOf(
                "Store type: RocksDB",
                "Directory: /data/store",
                "Status: connected",
            ),
            completeLines,
        )
        assertEquals(
            RocksDbStoreConnection("/data/store", dataStore),
            state.currentConnection,
        )
        assertSame(dataStore, state.currentConnection?.dataStore)
        assertEquals(listOf("/data/store"), environment.receivedPaths)
    }

    @Test
    fun requiresDirectoryForRocksDb() {
        val command = ConnectCommand(FakeRocksDbConnector(), FakeFoundationDbConnector())
        val state = CliState()
        val environment = FakeEnvironment { DirectoryResolution.Success("/data/store") }

        val result = command.execute(buildContext(state, environment), listOf("rocksdb"))

        assertTrue(result.isError)
        assertEquals("Store type: RocksDB", result.lines[0])
        assertTrue(result.lines[1].contains("Directory is required"))
    }

    @Test
    fun connectsToRocksDbAndStoresState() {
        val dataStore = FakeDataStore()
        val connector = FakeRocksDbConnector { path ->
            ConnectCommand.RocksDbConnectionOutcome.Success(
                RocksDbStoreConnection(path, dataStore),
            )
        }
        val command = ConnectCommand(connector, FakeFoundationDbConnector())
        val state = CliState()
        val environment = FakeEnvironment { DirectoryResolution.Success("/normalized/store") }

        val context = buildContext(state, environment)
        val result = command.execute(context, listOf("rocksdb", "--dir", "/my-store"))

        assertFalse(result.isError)
        assertEquals(
            RocksDbStoreConnection("/normalized/store", dataStore),
            state.currentConnection,
        )
        assertSame(dataStore, state.currentConnection?.dataStore)
        assertEquals("Status: connected", result.lines.last())
        assertEquals(listOf("/my-store"), environment.receivedPaths)
    }

    @Test
    fun blocksConnectWhenAlreadyConnected() {
        val dataStore = FakeDataStore()
        val connector = FakeRocksDbConnector { path ->
            ConnectCommand.RocksDbConnectionOutcome.Success(
                RocksDbStoreConnection(path, dataStore),
            )
        }
        val command = ConnectCommand(connector, FakeFoundationDbConnector())
        val state = CliState()
        val environment = FakeEnvironment { DirectoryResolution.Success("/store") }
        val context = buildContext(state, environment)

        state.replaceConnection(RocksDbStoreConnection("/store", dataStore))

        val result = command.execute(context, listOf("rocksdb", "--dir", "/other"))

        assertTrue(result.isError)
        assertTrue(result.lines.any { it.contains("disconnect") })
        assertEquals(listOf(), connector.receivedPaths)
    }

    @Test
    fun handlesFoundationDbPlaceholder() {
        val state = CliState()
        val environment = FakeEnvironment { DirectoryResolution.Success("/store") }

        val foundationConnector = FakeFoundationDbConnector { options ->
            ConnectCommand.FoundationDbConnectionOutcome.Success(
                FoundationDbStoreConnection(options.directoryPath, options.clusterFile, FakeDataStore()),
            )
        }

        val result = ConnectCommand(FakeRocksDbConnector(), foundationConnector)
            .execute(buildContext(state, environment), listOf("foundationdb", "--dir", "maryk/test"))

        assertFalse(result.isError)
        assertTrue(foundationConnector.called)
        assertEquals(listOf( "maryk", "test"), (state.currentConnection as FoundationDbStoreConnection).directoryPath)
    }

    @Test
    fun interactiveFlowCanBeCancelled() {
        val command = ConnectCommand(FakeRocksDbConnector(), FakeFoundationDbConnector())
        val state = CliState()
        val environment = FakeEnvironment { DirectoryResolution.Success("/store") }

        val context = buildContext(state, environment)
        command.execute(context, emptyList())

        val selectionInteraction = state.currentInteraction!!
        val cancelOutcome = selectionInteraction.onInput("cancel")

        assertTrue(cancelOutcome is InteractionResult.Complete)
        assertTrue(cancelOutcome.lines.first().contains("cancelled"))
        assertNull(state.currentConnection)
    }

    private class FakeEnvironment(
        private val resolver: (String) -> DirectoryResolution,
    ) : CliEnvironment {
        val receivedPaths = mutableListOf<String>()

        override fun resolveDirectory(path: String): DirectoryResolution {
            receivedPaths += path
            return resolver(path)
        }
    }

    private class FakeRocksDbConnector(
        private val factory: (String) -> ConnectCommand.RocksDbConnectionOutcome = { path ->
            ConnectCommand.RocksDbConnectionOutcome.Success(
                RocksDbStoreConnection(path, FakeDataStore()),
            )
        },
    ) : RocksDbConnector {
        val receivedPaths = mutableListOf<String>()

        override fun connect(path: String): ConnectCommand.RocksDbConnectionOutcome {
            receivedPaths += path
            return factory(path)
        }
    }

    private class FakeFoundationDbConnector(
        private val factory: (ConnectCommand.FoundationOptions) -> ConnectCommand.FoundationDbConnectionOutcome = {
            ConnectCommand.FoundationDbConnectionOutcome.Success(
                FoundationDbStoreConnection(it.directoryPath, it.clusterFile, FakeDataStore()),
            )
        },
    ) : FoundationDbConnector {
        var called = false
        override fun connect(options: ConnectCommand.FoundationOptions): ConnectCommand.FoundationDbConnectionOutcome {
            called = true
            return factory(options)
        }
    }
}
