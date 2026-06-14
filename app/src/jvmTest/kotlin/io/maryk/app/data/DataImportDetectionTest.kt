package io.maryk.app.data

import maryk.core.models.RootDataModel
import maryk.core.models.key
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.definitions.number
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.DefinitionsContext
import maryk.core.query.RequestContext
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.VersionedChanges
import maryk.json.JsonWriter
import maryk.yaml.YamlWriter
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DataImportDetectionTest {
    @Test
    fun detectVersionedImportReturnsTrueForVersionedJsonArray() {
        val values = VersionedImportModel.create {
            id with 7u
            number with 42u
        }
        val record = DataObjectVersionedChange(
            key = VersionedImportModel.key(values),
            changes = listOf(
                VersionedChanges(
                    version = 1uL,
                    changes = listOf(ObjectCreate),
                )
            )
        )
        val requestContext = buildRequestContext(VersionedImportModel)
        val json = buildString {
            val writer = JsonWriter(pretty = true) { append(it) }
            DataObjectVersionedChange.Serializer.writeObjectAsJson(
                record,
                writer,
                requestContext,
            )
        }
        val path = Files.createTempFile("maryk-import-versioned-", ".json")
        try {
            Files.writeString(path, "[$json]")
            assertTrue(
                detectVersionedImport(path.toString(), DataExportFormat.JSON, requestContext)
            )
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun detectVersionedImportReturnsTrueForVersionedYamlDocument() {
        val values = VersionedImportModel.create {
            id with 7u
            number with 42u
        }
        val record = DataObjectVersionedChange(
            key = VersionedImportModel.key(values),
            changes = listOf(
                VersionedChanges(
                    version = 1uL,
                    changes = listOf(ObjectCreate),
                )
            )
        )
        val requestContext = buildRequestContext(VersionedImportModel)
        val yaml = buildString {
            val writer = YamlWriter { append(it) }
            DataObjectVersionedChange.Serializer.writeObjectAsJson(
                record,
                writer,
                requestContext,
            )
        }
        val path = Files.createTempFile("maryk-import-versioned-", ".yaml")
        try {
            Files.writeString(path, yaml)
            assertTrue(
                detectVersionedImport(path.toString(), DataExportFormat.YAML, requestContext)
            )
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun detectVersionedImportReturnsFalseForPlainJsonRecords() {
        val path = Files.createTempFile("maryk-import-plain-", ".json")
        try {
            Files.writeString(
                path,
                """[{"key":"AAACKwEAAw","values":{"number":1},"firstVersion":1,"lastVersion":1,"isDeleted":false}]"""
            )
            val requestContext = RequestContext(
                DefinitionsContext(mutableMapOf(PlainImportModel.Meta.name to DataModelReference(PlainImportModel))),
                dataModel = PlainImportModel,
            )

            assertFalse(
                detectVersionedImport(path.toString(), DataExportFormat.JSON, requestContext)
            )
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun detectVersionedImportReturnsFalseForPlainYamlRecords() {
        val path = Files.createTempFile("maryk-import-plain-", ".yaml")
        try {
            Files.writeString(
                path,
                """
                ---
                key: AAACKwEAAw
                values:
                  number: 1
                firstVersion: 1
                lastVersion: 1
                isDeleted: false
                """.trimIndent()
            )
            val requestContext = RequestContext(
                DefinitionsContext(mutableMapOf(PlainImportModel.Meta.name to DataModelReference(PlainImportModel))),
                dataModel = PlainImportModel,
            )

            assertFalse(
                detectVersionedImport(path.toString(), DataExportFormat.YAML, requestContext)
            )
        } finally {
            Files.deleteIfExists(path)
        }
    }

}

private object PlainImportModel : RootDataModel<PlainImportModel>() {
    val number by number(index = 1u, type = UInt32)
}

private object VersionedImportModel : RootDataModel<VersionedImportModel>(
    keyDefinition = {
        VersionedImportModel.run { id.ref() }
    },
) {
    val id by number(index = 1u, type = UInt32, final = true)
    val number by number(index = 2u, type = UInt32)
}
