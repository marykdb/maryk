package io.maryk.cli.commands

import io.maryk.cli.CliEnvironment
import io.maryk.cli.CliState
import io.maryk.cli.DirectoryResolution
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ServeCommandTest {
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
        assertTrue(duplicate.reason.contains("multiple times"))

        val blank = assertIs<ServeParseResult.Error>(
            parseServeOptions(
                TestServeEnvironment,
                listOf("rocksdb", "--dir", "/data", "--bearer-token="),
            )
        )
        assertTrue(blank.reason.contains("cannot be blank"))
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

        assertEquals("secret", result.input.bearerToken)
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
