package io.maryk.cli.commands

import io.maryk.cli.CliEnvironment
import io.maryk.cli.CliState
import io.maryk.cli.DirectoryResolution
import io.maryk.cli.RocksDbStoreConnection
import maryk.file.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ServeCommandTest {
    @Test
    fun reportsServerStartFailureAndClosesStore() {
        val store = FakeDataStore()
        val command = ServeCommand(
            rocksDbConnector = RocksDbConnector {
                ConnectCommand.RocksDbConnectionOutcome.Success(RocksDbStoreConnection("/data", store))
            },
            serverStarter = { _, _, _, _, _ -> throw IllegalStateException("bind boom") },
        )
        val state = CliState()

        val result = command.execute(
            CommandContext(CommandRegistry(state, TestServeEnvironment), state, TestServeEnvironment),
            listOf("rocksdb", "--dir", "/data"),
        )

        assertTrue(result.isError)
        assertEquals(listOf("Serve failed: bind boom"), result.lines)
        assertTrue(store.closed)
    }

    @Test
    fun reportsStoreCloseFailureAfterServerStops() {
        val store = object : FakeDataStore() {
            override suspend fun close() {
                throw IllegalStateException("close boom")
            }
        }
        val command = ServeCommand(
            rocksDbConnector = RocksDbConnector {
                ConnectCommand.RocksDbConnectionOutcome.Success(RocksDbStoreConnection("/data", store))
            },
            serverStarter = { _, _, _, _, _ -> },
        )
        val state = CliState()

        val result = command.execute(
            CommandContext(CommandRegistry(state, TestServeEnvironment), state, TestServeEnvironment),
            listOf("rocksdb", "--dir", "/data"),
        )

        assertTrue(result.isError)
        assertTrue(result.lines.any { it.contains("close boom") })
        assertTrue(store.listenersClosed)
    }

    @Test
    fun rejectsUnauthenticatedPublicBindBeforeOpeningStore() {
        var connectorCalled = false
        val command = ServeCommand(
            rocksDbConnector = RocksDbConnector {
                connectorCalled = true
                error("Connector should not be called for invalid server configuration")
            }
        )
        val state = CliState()
        val registry = CommandRegistry(state, TestServeEnvironment)
        val result = command.execute(
            CommandContext(registry, state, TestServeEnvironment),
            listOf("rocksdb", "--dir", "/data", "--host", "0.0.0.0"),
        )

        assertTrue(result.isError)
        assertTrue(result.lines.first().contains("Serve configuration error"))
        assertTrue(result.lines.first().contains("non-loopback"))
        assertFalse(connectorCalled)
    }

    @Test
    fun rejectsBearerProtectedPublicBindBeforeOpeningStore() {
        var connectorCalled = false
        val command = ServeCommand(
            rocksDbConnector = RocksDbConnector {
                connectorCalled = true
                error("Connector should not be called for invalid server configuration")
            }
        )
        val state = CliState()
        val registry = CommandRegistry(state, TestServeEnvironment)
        val result = command.execute(
            CommandContext(registry, state, TestServeEnvironment),
            listOf("rocksdb", "--dir", "/data", "--host", "0.0.0.0", "--bearer-token", "secret"),
        )

        assertTrue(result.isError)
        assertTrue(result.lines.first().contains("plaintext"))
        assertFalse(connectorCalled)
    }

    @Test
    fun parsesBearerTokenAndInsecureOptIn() {
        val result = assertIs<ServeParseResult.Success>(
            parseServeOptions(
                TestServeEnvironment,
                listOf(
                    "rocksdb",
                    "--dir",
                    "/data",
                    "--host",
                    "0.0.0.0",
                    "--bearer-token",
                    "secret",
                    "--allow-insecure-remote-binding",
                ),
            )
        )

        assertEquals("secret", result.options.bearerToken)
        assertTrue(result.options.allowInsecureRemoteBinding)
    }

    @Test
    fun rejectsDuplicateOrBlankBearerToken() {
        val duplicate = assertIs<ServeParseResult.Error>(
            parseServeOptions(
                TestServeEnvironment,
                listOf("rocksdb", "--dir", "/data", "--bearer-token=a", "--bearer-token=b"),
            )
        )
        assertTrue(duplicate.reason.contains("only one bearer token source"))

        val blank = assertIs<ServeParseResult.Error>(
            parseServeOptions(
                TestServeEnvironment,
                listOf("rocksdb", "--dir", "/data", "--bearer-token="),
            )
        )
        assertTrue(blank.reason.contains("cannot be blank"))
    }

    @Test
    fun loadsBearerTokenFromFileWithoutPuttingTheSecretInArguments() {
        val path = "build/tmp/serve-command-token.txt"
        File.writeText(path, "secret-from-file\n")

        val result = assertIs<ServeParseResult.Success>(
            parseServeOptions(
                TestServeEnvironment,
                listOf("rocksdb", "--dir", "/data", "--bearer-token-file", path),
            )
        )

        assertEquals("secret-from-file", result.options.bearerToken)
    }

    @Test
    fun rejectsOversizedBearerTokenFile() {
        val path = "build/tmp/serve-command-token-oversized.txt"
        File.writeText(path, "s".repeat(16 * 1024 + 1))

        val result = assertIs<ServeParseResult.Error>(
            parseServeOptions(
                TestServeEnvironment,
                listOf("rocksdb", "--dir", "/data", "--bearer-token-file", path),
            )
        )

        assertTrue(result.reason.contains("max size"))
    }

    @Test
    fun rejectsOversizedServeConfiguration() {
        val path = "build/tmp/serve-command-config-oversized.txt"
        File.writeText(path, "#".repeat(1024 * 1024 + 1))

        val result = assertIs<ServeParseResult.Error>(
            parseServeOptions(TestServeEnvironment, listOf("--config", path))
        )

        assertTrue(result.reason.contains("max size"))
    }

    @Test
    fun rejectsMoreThanOneBearerTokenSource() {
        val result = assertIs<ServeParseResult.Error>(
            parseServeOptions(
                TestServeEnvironment,
                listOf("rocksdb", "--dir", "/data", "--bearer-token", "literal", "--bearer-token-env", "MARYK_TOKEN"),
            )
        )

        assertTrue(result.reason.contains("only one bearer token source"))
    }

    @Test
    fun reportsMissingBearerTokenEnvironmentVariable() {
        val result = assertIs<ServeParseResult.Error>(
            parseServeOptions(
                TestServeEnvironment,
                listOf("rocksdb", "--dir", "/data", "--bearer-token-env", "MARYK_TEST_MISSING_TOKEN"),
            )
        )

        assertTrue(result.reason.contains("MARYK_TEST_MISSING_TOKEN"))
        assertTrue(result.reason.contains("not set"))
    }

    @Test
    fun parsesSecuritySettingsFromConfig() {
        val result = assertIs<ConfigParseResult.Success>(
            parseServeConfig(
                """
                store = rocksdb
                directory = /data
                bearer-token = secret
                allow-insecure-remote-binding = true
                """.trimIndent()
            )
        )

        assertEquals(BearerTokenSource.Literal("secret"), result.input.bearerTokenSource)
        assertEquals(true, result.input.allowInsecureRemoteBinding)
    }

    @Test
    fun rejectsDuplicateConfigAliasesAndInvalidBoolean() {
        val duplicate = assertIs<ConfigParseResult.Error>(
            parseServeConfig("bearer-token = one\nbearertoken = two")
        )
        assertTrue(duplicate.reason.contains("Duplicate"))

        val invalidBoolean = assertIs<ConfigParseResult.Error>(
            parseServeConfig("allow-insecure-remote-binding = sometimes")
        )
        assertTrue(invalidBoolean.reason.contains("Invalid boolean"))
    }
}

private object TestServeEnvironment : CliEnvironment {
    override fun resolveDirectory(path: String): DirectoryResolution = DirectoryResolution.Success(path)
}
