package io.maryk.app.data

import kotlinx.coroutines.runBlocking
import maryk.core.models.RootDataModel
import maryk.core.models.asValues
import maryk.core.models.key
import maryk.core.extensions.bytes.toVarBytes
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.string
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.RequestContext
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.pairs.with
import maryk.core.protobuf.WriteCache
import maryk.core.query.requests.add
import maryk.datastore.memory.InMemoryDataStore
import java.nio.file.Files
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DataImportProtoScopeTest {
    @Test
    fun detectsAndImportsUnframedVersionedProtoExport() = runBlocking {
        val source = InMemoryDataStore.open(
            keepAllVersions = true,
            dataModelsById = mapOf(1u to ProtoScopeModel),
        )
        val destination = InMemoryDataStore.open(
            keepAllVersions = true,
            dataModelsById = mapOf(1u to ProtoScopeModel),
        )
        val folder = Files.createTempDirectory("maryk-import-unframed-versioned-export-")
        try {
            val values = ProtoScopeModel.create {
                id with 7u
                number with 42u
            }
            val key = ProtoScopeModel.key(values)
            source.execute(ProtoScopeModel.add(values))

            exportRowDataToFolder(
                dataStore = source,
                model = ProtoScopeModel,
                key = key,
                keyText = "7",
                format = DataExportFormat.PROTO,
                folder = folder.toString(),
                includeVersionHistory = true,
            )

            val path = folder.resolve("${ProtoScopeModel.Meta.name}.7.versions.proto")
            val requestContext = buildRequestContext(ProtoScopeModel)
            val scope = detectImportScopeFromPath(path.toString(), DataExportFormat.PROTO)

            assertEquals(10, Files.readAllBytes(path).first().toInt())
            assertEquals(DataImportScope.SINGLE, scope)
            assertTrue(detectVersionedImport(path.toString(), DataExportFormat.PROTO, requestContext))
            assertEquals(
                ImportResult(imported = 1, failed = 0),
                importVersionedDataFromFile(
                    dataStore = destination,
                    model = ProtoScopeModel,
                    format = DataExportFormat.PROTO,
                    scope = scope,
                    path = path.toString(),
                ),
            )
        } finally {
            source.close()
            destination.close()
            folder.toFile().deleteRecursively()
        }
    }

    @Test
    fun importsAmbiguousUnframedProtoRecordAsSingleScope() = runBlocking {
        val destination = InMemoryDataStore.open(dataModelsById = mapOf(1u to AmbiguousProtoScopeModel))
        val ambiguousRecord = byteArrayOf(
            40, 0,
            10, 4, 0, 0, 0, 7,
            18, 27, 8, 7, 16, 42, 26, 21,
            97, 97, 97, 97, 97, 97, 97, 97, 97, 97, 97, 97, 97, 97, 97, 97, 97, 97, 97, 97, 97,
            24, 1, 32, 1,
        )
        val path = Files.createTempFile("maryk-import-ambiguous-unframed-", ".proto")
        try {
            Files.write(path, ambiguousRecord)
            assertEquals(ambiguousRecord.size - 1, ambiguousRecord.first().toInt())

            val scope = detectImportScopeFromPath(path.toString(), DataExportFormat.PROTO)

            assertEquals(DataImportScope.SINGLE, scope)
            assertEquals(
                ImportResult(imported = 1, failed = 0),
                importDataFromFile(
                    dataStore = destination,
                    model = AmbiguousProtoScopeModel,
                    format = DataExportFormat.PROTO,
                    scope = scope,
                    path = path.toString(),
                ),
            )
        } finally {
            destination.close()
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun importOneRecordFramedProtoExport() = runBlocking {
        val source = InMemoryDataStore.open(dataModelsById = mapOf(1u to ProtoScopeModel))
        val destination = InMemoryDataStore.open(dataModelsById = mapOf(1u to ProtoScopeModel))
        val folder = Files.createTempDirectory("maryk-import-one-framed-export-")
        try {
            source.execute(
                ProtoScopeModel.add(
                    ProtoScopeModel.create {
                        id with 7u
                        number with 42u
                    }
                )
            )

            exportModelDataToFolder(
                dataStore = source,
                model = ProtoScopeModel,
                format = DataExportFormat.PROTO,
                folder = folder.toString(),
            )

            val path = folder.resolve("${ProtoScopeModel.Meta.name}.data.proto")
            val scope = detectImportScopeFromPath(path.toString(), DataExportFormat.PROTO)
            val result = importDataFromFile(
                dataStore = destination,
                model = ProtoScopeModel,
                format = DataExportFormat.PROTO,
                scope = scope,
                path = path.toString(),
            )

            assertEquals(DataImportScope.SINGLE, scope)
            assertEquals(ImportResult(imported = 1, failed = 0), result)
        } finally {
            source.close()
            destination.close()
            folder.toFile().deleteRecursively()
        }
    }

    @Test
    fun importOneRecordFramedVersionedProtoExport() = runBlocking {
        val source = InMemoryDataStore.open(
            keepAllVersions = true,
            dataModelsById = mapOf(1u to ProtoScopeModel),
        )
        val destination = InMemoryDataStore.open(
            keepAllVersions = true,
            dataModelsById = mapOf(1u to ProtoScopeModel),
        )
        val folder = Files.createTempDirectory("maryk-import-one-framed-versioned-export-")
        try {
            source.execute(
                ProtoScopeModel.add(
                    ProtoScopeModel.create {
                        id with 7u
                        number with 42u
                    }
                )
            )

            exportModelDataToFolder(
                dataStore = source,
                model = ProtoScopeModel,
                format = DataExportFormat.PROTO,
                folder = folder.toString(),
                includeVersionHistory = true,
            )

            val path = folder.resolve("${ProtoScopeModel.Meta.name}.data.versions.proto")
            val requestContext = buildRequestContext(ProtoScopeModel)
            val scope = detectImportScopeFromPath(path.toString(), DataExportFormat.PROTO)
            assertEquals(DataImportScope.SINGLE, scope)
            assertEquals(
                true,
                detectVersionedImport(path.toString(), DataExportFormat.PROTO, requestContext),
            )
            val result = importVersionedDataFromFile(
                dataStore = destination,
                model = ProtoScopeModel,
                format = DataExportFormat.PROTO,
                scope = scope,
                path = path.toString(),
            )

            assertEquals(ImportResult(imported = 1, failed = 0), result)
        } finally {
            source.close()
            destination.close()
            folder.toFile().deleteRecursively()
        }
    }

    @Test
    fun detectSingleProtoScope() {
        val values = ProtoScopeModel.create {
            id with 7u
            number with 42u
        }
        val record = ValuesWithMetaData(
            key = ProtoScopeModel.key(values),
            values = values,
            firstVersion = 1uL,
            lastVersion = 1uL,
            isDeleted = false,
        )
        val requestContext = buildRequestContext(ProtoScopeModel)
        val bytes = serializeValuesWithMetaDataProto(record, requestContext)

        val path = Files.createTempFile("maryk-import-single-", ".proto")
        try {
            Files.write(path, bytes)
            assertEquals(
                DataImportScope.SINGLE,
                detectImportScopeFromPath(path.toString(), DataExportFormat.PROTO),
            )
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun detectMultiProtoScope() {
        val values = ProtoScopeModel.create {
            id with 7u
            number with 42u
        }
        val record = ValuesWithMetaData(
            key = ProtoScopeModel.key(values),
            values = values,
            firstVersion = 1uL,
            lastVersion = 1uL,
            isDeleted = false,
        )
        val requestContext = buildRequestContext(ProtoScopeModel)
        val single = serializeValuesWithMetaDataProto(record, requestContext)
        val framed = single.size.toVarBytes() + single + single.size.toVarBytes() + single

        val path = Files.createTempFile("maryk-import-multi-", ".proto")
        try {
            Files.write(path, framed)
            assertEquals(
                DataImportScope.MULTIPLE,
                detectImportScopeFromPath(path.toString(), DataExportFormat.PROTO),
            )
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun detectOneFramedProtoRecordAsSingleScope() {
        val values = ProtoScopeModel.create {
            id with 7u
            number with 42u
        }
        val record = ValuesWithMetaData(
            key = ProtoScopeModel.key(values),
            values = values,
            firstVersion = 1uL,
            lastVersion = 1uL,
            isDeleted = false,
        )
        val requestContext = buildRequestContext(ProtoScopeModel)
        val recordBytes = serializeValuesWithMetaDataProto(record, requestContext)
        val framed = recordBytes.size.toVarBytes() + recordBytes

        val path = Files.createTempFile("maryk-import-one-framed-", ".proto")
        try {
            Files.write(path, framed)
            assertEquals(
                DataImportScope.SINGLE,
                detectImportScopeFromPath(path.toString(), DataExportFormat.PROTO),
            )
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun malformedHugeProtoLengthFallsBackToSingleScope() {
        val bytes = byteArrayOf(-1, -1, -1, -1, 7)
        val path = Files.createTempFile("maryk-import-huge-length-", ".proto")
        try {
            Files.write(path, bytes)
            assertEquals(
                DataImportScope.SINGLE,
                detectImportScopeFromPath(path.toString(), DataExportFormat.PROTO),
            )
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun truncatedProtoFrameFallsBackToSingleScope() {
        val bytes = byteArrayOf(10, 1, 2)
        val path = Files.createTempFile("maryk-import-truncated-", ".proto")
        try {
            Files.write(path, bytes)
            assertEquals(
                DataImportScope.SINGLE,
                detectImportScopeFromPath(path.toString(), DataExportFormat.PROTO),
            )
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun protoPayloadReaderRejectsTrailingBytes() {
        assertFailsWith<IllegalArgumentException> {
            readProtoPayload(
                bytes = byteArrayOf(1, 2),
                start = 0,
                length = 2,
                label = "test",
            ) { reader ->
                reader()
            }
        }
    }

    @Test
    fun protoPayloadReaderRejectsOverread() {
        assertFailsWith<IllegalArgumentException> {
            readProtoPayload(
                bytes = byteArrayOf(1),
                start = 0,
                length = 1,
                label = "test",
            ) { reader ->
                reader()
                reader()
            }
        }
    }

    @Test
    fun protoPayloadReaderRethrowsCancellation() {
        assertFailsWith<CancellationException> {
            readProtoPayload(
                bytes = byteArrayOf(1),
                start = 0,
                length = 1,
                label = "test",
            ) {
                throw CancellationException("cancelled")
            }
        }
    }
}

private fun serializeValuesWithMetaDataProto(
    record: ValuesWithMetaData<ProtoScopeModel>,
    requestContext: RequestContext,
): ByteArray {
    val values = ValuesWithMetaData.asValues(record, requestContext)
    val cache = WriteCache()
    val length = ValuesWithMetaData.Serializer.calculateProtoBufLength(values, cache, requestContext)
    val bytes = ByteArray(length)
    var index = 0
    ValuesWithMetaData.Serializer.writeProtoBuf(values, cache, { byte ->
        bytes[index++] = byte
    }, requestContext)
    return bytes
}

private object ProtoScopeModel : RootDataModel<ProtoScopeModel>(
    keyDefinition = {
        ProtoScopeModel.run { id.ref() }
    },
) {
    val id by number(index = 1u, type = UInt32, final = true)
    val number by number(index = 2u, type = UInt32)
}

private object AmbiguousProtoScopeModel : RootDataModel<AmbiguousProtoScopeModel>(
    keyDefinition = {
        AmbiguousProtoScopeModel.run { id.ref() }
    },
) {
    val id by number(index = 1u, type = UInt32, final = true)
    val number by number(index = 2u, type = UInt32)
    val label by string(index = 3u)
}
