package io.maryk.app.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import maryk.file.File

class StoreConfigTest {
    @Test
    fun storeRepositoryRoundTripsSshFields() {
        val path = "build/test-store-config-roundtrip.txt"
        val repository = StoreRepository(path)
        val store = StoreDefinition(
            id = "remote-1",
            name = "Remote",
            type = StoreKind.REMOTE,
            directory = "http://127.0.0.1:9000",
            sshHost = "ssh.example",
            sshUser = "mary",
            sshPort = 2200,
            sshLocalPort = 3100,
            sshIdentityFile = "/tmp/id_ed25519",
        )

        try {
            repository.save(listOf(store))

            assertEquals(listOf(store), repository.load())
        } finally {
            File.delete(path)
        }
    }

    @Test
    fun storeRepositoryKeepsBlankSshPortsAsNull() {
        val path = "build/test-store-config-blank-port.txt"
        val repository = StoreRepository(path)
        val line = listOf(
            "remote-blank",
            "Remote",
            "REMOTE",
            "http://127.0.0.1:9000",
            "",
            "ssh.example",
            "mary",
            "",
            "",
            "",
        ).joinToString("\t")

        try {
            File.writeText(path, line)

            val store = repository.load().single()
            assertEquals(null, store.sshPort)
            assertEquals(null, store.sshLocalPort)
        } finally {
            File.delete(path)
        }
    }

    @Test
    fun storeRepositoryRejectsInvalidPersistedSshPorts() {
        val path = "build/test-store-config-invalid-port.txt"
        val repository = StoreRepository(path)
        val line = listOf(
            "remote-invalid",
            "Remote",
            "REMOTE",
            "http://127.0.0.1:9000",
            "",
            "ssh.example",
            "mary",
            "not-a-port",
            "",
            "",
        ).joinToString("\t")

        try {
            File.writeText(path, line)

            assertTrue(repository.load().isEmpty())
        } finally {
            File.delete(path)
        }
    }

    @Test
    fun storeRepositoryPreservesBearerTokenFileReference() {
        val path = "build/test-store-config-token-file.txt"
        val tokenPath = "/run/secrets/maryk-token"
        val line = listOf(
            "remote-token",
            "Remote",
            "REMOTE",
            "https://store.example.test",
            "",
            "",
            "",
            "",
            "",
            "",
            tokenPath,
        ).joinToString("\t")

        try {
            File.writeText(path, line)
            val repository = StoreRepository(path)

            repository.save(repository.load())

            assertTrue(File.readText(path).orEmpty().contains(tokenPath))
        } finally {
            File.delete(path)
        }
    }

    @Test
    fun storeRepositoryRejectsOversizedConfiguration() {
        val path = "build/test-store-config-oversized.txt"
        try {
            File.writeText(path, "#".repeat(1024 * 1024 + 1))

            val error = assertFailsWith<IllegalArgumentException> {
                StoreRepository(path).load()
            }

            assertTrue(error.message.orEmpty().contains("max size"))
        } finally {
            File.delete(path)
        }
    }

    @Test
    fun bearerTokenFileReadTrimsLineEnding() {
        val path = "build/test-store-bearer-token.txt"
        try {
            File.writeText(path, "secret-token\n")

            assertEquals("secret-token", readBearerToken(path))
        } finally {
            File.delete(path)
        }
    }

    @Test
    fun oversizedBearerTokenFileIsRejected() {
        val path = "build/test-store-bearer-token-oversized.txt"
        try {
            File.writeText(path, "s".repeat(16 * 1024 + 1))

            val error = assertFailsWith<IllegalArgumentException> {
                readBearerToken(path)
            }

            assertTrue(error.message.orEmpty().contains("max size"))
        } finally {
            File.delete(path)
        }
    }
}
