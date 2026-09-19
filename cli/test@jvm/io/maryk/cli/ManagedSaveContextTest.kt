package io.maryk.cli

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ManagedSaveContextTest {
    @Test
    fun preservesFiveArgumentSaveJvmSignature() {
        SaveContext::class.java.getMethod(
            "save",
            String::class.java,
            SaveFormat::class.java,
            Boolean::class.javaPrimitiveType,
            String::class.java,
            Boolean::class.javaPrimitiveType,
        )
    }

    @Test
    fun dataAndMetaSavePublishesManagedRevisionByDefault() {
        val directory = Files.createTempDirectory("maryk-cli-managed-")
        try {
            val result = context().save(directory.toString(), SaveFormat.YAML, includeMeta = true)

            assertTrue(result.contains("via ${directory}/.maryk-export/current"))
            assertTrue(Files.exists(directory.resolve(".maryk-export/current")))
            assertFalse(Files.exists(directory.resolve("record.yaml")))
            assertFalse(Files.exists(directory.resolve("record.meta.yaml")))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun legacyDirectKeepsDataAndMetaAtSelectedDirectory() {
        val directory = Files.createTempDirectory("maryk-cli-legacy-")
        try {
            context().save(
                directory = directory.toString(),
                format = SaveFormat.YAML,
                includeMeta = true,
                packageName = null,
                noDeps = false,
                legacyDirect = true,
            )

            assertTrue(Files.exists(directory.resolve("record.yaml")))
            assertTrue(Files.exists(directory.resolve("record.meta.yaml")))
            assertFalse(Files.exists(directory.resolve(".maryk-export/current")))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun kotlinSavePublishesCompleteManagedRevisionByDefault() {
        val directory = Files.createTempDirectory("maryk-cli-kotlin-managed-")
        try {
            val result = context(
                kotlinGenerator = { KotlinSaveResult(mapOf("One.kt" to "one", "Two.kt" to "two")) },
            ).save(directory.toString(), SaveFormat.KOTLIN, includeMeta = false, packageName = "test")

            assertTrue(result.contains("via ${directory}/.maryk-export/current"))
            val revision = Files.readString(directory.resolve(".maryk-export/current")).trim()
            assertTrue(Files.exists(directory.resolve(".maryk-export/revisions/$revision/One.kt")))
            assertTrue(Files.exists(directory.resolve(".maryk-export/revisions/$revision/Two.kt")))
            assertFalse(Files.exists(directory.resolve("One.kt")))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun kotlinLegacyDirectKeepsFilesAtSelectedDirectory() {
        val directory = Files.createTempDirectory("maryk-cli-kotlin-legacy-")
        try {
            context(
                kotlinGenerator = { KotlinSaveResult(mapOf("One.kt" to "one", "Two.kt" to "two")) },
            ).save(
                directory = directory.toString(),
                format = SaveFormat.KOTLIN,
                includeMeta = false,
                packageName = "test",
                noDeps = false,
                legacyDirect = true,
            )

            assertTrue(Files.exists(directory.resolve("One.kt")))
            assertTrue(Files.exists(directory.resolve("Two.kt")))
            assertFalse(Files.exists(directory.resolve(".maryk-export/current")))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private fun context(
        kotlinGenerator: ((String) -> KotlinSaveResult)? = null,
    ) = SaveContext(
        key = "record",
        dataYaml = "value: data\n",
        dataJson = "{\"value\":\"data\"}",
        dataProto = byteArrayOf(1),
        metaYaml = "key: record\n",
        metaJson = "{\"key\":\"record\"}",
        metaProto = byteArrayOf(2),
        kotlinGenerator = kotlinGenerator,
    )
}
