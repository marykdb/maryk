package io.maryk.cli

import io.maryk.cli.commands.Command
import io.maryk.cli.commands.CommandContext
import io.maryk.cli.commands.CommandRegistry
import io.maryk.cli.commands.CommandResult
import io.maryk.cli.commands.FakeDataStore
import io.maryk.cli.commands.GetCommand
import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.properties.types.Key
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.requests.DeleteRequest
import maryk.core.query.requests.GetRequest
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.responses.DeleteResponse
import maryk.core.query.responses.IsResponse
import maryk.core.query.responses.ValuesResponse
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.statuses.DoesNotExist
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OneShotModeTest {
    @Test
    fun parseIgnoresArgsWithoutExec() {
        val result = parseOneShotArgs(arrayOf("list"))
        assertNull(result)
    }

    @Test
    fun parseAllowsExecWithoutConnect() {
        val result = parseOneShotArgs(arrayOf("--exec", "list"))
        val options = requireNotNull(result as? OneShotParseResult.Success).options
        assertEquals(null, options.store)
        assertEquals(emptyList(), options.connectArgs)
        assertEquals("list", options.commandLine)
    }

    @Test
    fun parseCollectsConnectArgsAndExecCommand() {
        val result = parseOneShotArgs(arrayOf("--connect", "rocksdb", "--dir", "/data", "--exec", "list"))
        val options = requireNotNull(result as? OneShotParseResult.Success).options
        assertEquals("rocksdb", options.store)
        assertEquals(listOf("--dir", "/data"), options.connectArgs)
        assertEquals("list", options.commandLine)
    }

    @Test
    fun runOneShotExecutesCommandAndClosesConnection() {
        val state = CliState()
        val environment = FakeEnvironment()
        val registry = CommandRegistry(state, environment)
        val connection = FakeConnection()
        val connectCommand = CapturingConnectCommand(connection)
        val listCommand = CapturingCommand("list")

        registry.register(connectCommand)
        registry.register(listCommand)

        val exitCode = runOneShot(
            registry,
            OneShotOptions(
                store = "rocksdb",
                connectArgs = listOf("--dir", "/data"),
                commandLine = "list",
            ),
        )

        assertEquals(0, exitCode)
        assertEquals(listOf("rocksdb", "--dir", "/data"), connectCommand.receivedArgs)
        assertTrue(listCommand.called)
        assertTrue(connection.closed)
        assertNull(state.currentConnection)
    }

    @Test
    fun runOneShotRejectsInteractiveCommands() {
        val state = CliState()
        val environment = FakeEnvironment()
        val registry = CommandRegistry(state, environment)
        val connection = FakeConnection()

        registry.register(CapturingConnectCommand(connection))
        registry.register(InteractiveCommand("scan"))

        val exitCode = runOneShot(
            registry,
            OneShotOptions(
                store = "rocksdb",
                connectArgs = listOf("--dir", "/data"),
                commandLine = "scan Client",
            ),
        )

        assertEquals(1, exitCode)
        assertFalse(state.hasActiveInteraction())
        assertTrue(connection.closed)
    }

    @Test
    fun runOneShotExecutesCommandWithoutConnect() {
        val state = CliState()
        val environment = FakeEnvironment()
        val registry = CommandRegistry(state, environment)
        val serveCommand = CapturingCommand("serve")
        registry.register(serveCommand)

        val exitCode = runOneShot(
            registry,
            OneShotOptions(
                store = null,
                connectArgs = emptyList(),
                commandLine = "serve rocksdb --dir /data",
            ),
        )

        assertEquals(0, exitCode)
        assertTrue(serveCommand.called)
        assertNull(state.currentConnection)
    }

    @Test
    fun runOneShotMatchesCommandsIgnoringCase() {
        val state = CliState()
        val registry = CommandRegistry(state, FakeEnvironment())
        val listCommand = CapturingCommand("list")
        registry.register(listCommand)

        val exitCode = runOneShot(
            registry,
            OneShotOptions(
                store = null,
                connectArgs = emptyList(),
                commandLine = "LIST",
            ),
        )

        assertEquals(0, exitCode)
        assertTrue(listCommand.called)
    }

    @Test
    fun runOneShotReturnsDeleteFailureAndSuccessExitCodes() {
        assertEquals(1, runOneShotDelete { key ->
            DeleteResponse(SimpleMarykModel, listOf(DoesNotExist(key)))
        })
        assertEquals(0, runOneShotDelete {
            DeleteResponse(SimpleMarykModel, listOf(DeleteSuccess(1uL)))
        })
    }

    private class FakeEnvironment : CliEnvironment {
        override fun resolveDirectory(path: String): DirectoryResolution =
            DirectoryResolution.Success(path)
    }

    private class FakeConnection : StoreConnection {
        var closed = false
        override val type: StoreType = StoreType.ROCKS_DB
        override val dataStore = FakeDataStore()

        override fun close() {
            closed = true
        }
    }

    private class CapturingConnectCommand(
        private val connection: StoreConnection,
    ) : Command {
        val receivedArgs = mutableListOf<String>()

        override val name: String = "connect"
        override val description: String = "Connect test double."

        override fun execute(context: CommandContext, arguments: List<String>): CommandResult {
            receivedArgs.clear()
            receivedArgs.addAll(arguments)
            context.state.replaceConnection(connection)
            return CommandResult(lines = listOf("Connected"))
        }
    }

    private class CapturingCommand(
        override val name: String,
    ) : Command {
        override val description: String = "Test command."
        var called = false

        override fun execute(context: CommandContext, arguments: List<String>): CommandResult {
            called = true
            return CommandResult(lines = listOf("ok"))
        }
    }

    private class InteractiveCommand(
        override val name: String,
    ) : Command {
        override val description: String = "Interactive test command."

        override fun execute(context: CommandContext, arguments: List<String>): CommandResult {
            context.state.startInteraction(DummyInteraction())
            return CommandResult(lines = listOf("interactive"))
        }
    }

    private class DummyInteraction : CliInteraction {
        override val promptLabel: String = "dummy> "
        override val introLines: List<String> = listOf("dummy")

        override fun onInput(input: String): InteractionResult =
            InteractionResult.Complete(listOf("done"))
    }

    private fun runOneShotDelete(
        deleteResponse: (Key<SimpleMarykModel>) -> DeleteResponse<SimpleMarykModel>,
    ): Int {
        val values = SimpleMarykModel.create { value with "hello" }
        val key = SimpleMarykModel.key(values)
        val store = object : FakeDataStore(dataModelsById = mapOf(1u to SimpleMarykModel)) {
            @Suppress("UNCHECKED_CAST")
            override suspend fun <DM : IsRootDataModel, RQ : IsStoreRequest<DM, RP>, RP : IsResponse> execute(request: RQ): RP = when (request) {
                is GetRequest<*> -> ValuesResponse(
                    dataModel = SimpleMarykModel,
                    values = listOf(ValuesWithMetaData(key, values, 1uL, 1uL, false)),
                ) as RP
                is DeleteRequest<*> -> deleteResponse(key) as RP
                else -> error("Unexpected request")
            }
        }
        val state = CliState().apply {
            replaceConnection(RocksDbStoreConnection("/data/store", store))
        }
        val registry = CommandRegistry(state, FakeEnvironment()).apply {
            register(GetCommand())
        }

        return runOneShot(
            registry,
            OneShotOptions(
                store = null,
                connectArgs = emptyList(),
                commandLine = "get SimpleMarykModel $key delete",
            ),
        )
    }
}
