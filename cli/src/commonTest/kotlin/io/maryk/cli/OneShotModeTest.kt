package io.maryk.cli

import io.maryk.cli.commands.Command
import io.maryk.cli.commands.CommandContext
import io.maryk.cli.commands.CommandRegistry
import io.maryk.cli.commands.CommandResult
import io.maryk.cli.commands.FakeDataStore
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
    fun parseRequiresConnectWhenExecIsPresent() {
        val result = parseOneShotArgs(arrayOf("--exec", "list"))
        val error = requireNotNull(result as? OneShotParseResult.Error)
        assertEquals("`--connect` is required for one-shot mode.", error.message)
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
}
