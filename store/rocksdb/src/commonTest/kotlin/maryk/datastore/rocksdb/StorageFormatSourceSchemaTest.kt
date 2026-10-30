package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.models.DataModel
import maryk.core.models.RootDataModel
import maryk.core.models.IsRootDataModel
import maryk.core.models.definitions.DataModelDefinition
import maryk.core.properties.definitions.DateTimeDefinition
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.number
import maryk.core.properties.types.TimePrecision.NANOS
import maryk.core.properties.types.Version
import maryk.core.properties.types.numeric.Float64
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.DefinitionsConversionContext
import maryk.createTestDBFolder
import maryk.datastore.rocksdb.model.modelDefinitionKey
import maryk.datastore.rocksdb.model.modelDependentsDefinitionKey
import maryk.datastore.rocksdb.model.modelStorageFormatKey
import maryk.datastore.rocksdb.model.modelVersionKey
import maryk.datastore.rocksdb.model.readStoredModelDefinition
import maryk.datastore.rocksdb.model.readStoredModelDefinitionsFromPath
import maryk.datastore.rocksdb.model.rewriteStoredModelDefinition
import maryk.datastore.shared.migration.StorageFormatMetadataKey
import maryk.deleteFolder
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

private val SourceNanosDefault = listOf(LocalDateTime(1970, 1, 1, 0, 0, 8, 1))

private object NanosSchemaDependent : DataModel<NanosSchemaDependent>() {
    val stamps by list(1u, valueDefinition = DateTimeDefinition(precision = NANOS), default = SourceNanosDefault)
}

private object NanosSchemaSource : RootDataModel<NanosSchemaSource>(version = Version(3, 1, 2)) {
    val stamps by list(1u, valueDefinition = DateTimeDefinition(precision = NANOS), default = SourceNanosDefault)
    val child by embed(2u, dataModel = { NanosSchemaDependent })
}

private object FirstRecursiveDetail : DataModel<FirstRecursiveDetail>({ DataModelDefinition("RetainedRecursiveDetail") }) {
    val value by number(1u, type = Float64)
    val next by embed(2u, dataModel = { FirstRecursiveDetail }, required = false)
}

private object SecondRecursiveDetail : DataModel<SecondRecursiveDetail>({ DataModelDefinition("RetainedRecursiveDetail") }) {
    val value by number(1u, type = UInt32)
    val next by embed(2u, dataModel = { SecondRecursiveDetail }, required = false)
}

private object FirstRecursiveSource : RootDataModel<FirstRecursiveSource>() {
    val detail by embed(1u, dataModel = { FirstRecursiveDetail })
}

private object SecondRecursiveSource : RootDataModel<SecondRecursiveSource>() {
    val detail by embed(1u, dataModel = { SecondRecursiveDetail })
}

class StorageFormatSourceSchemaTest {
    @Test
    fun retainedSourcePairsKeepTheirOwnRecursiveDependencyDefinitions() = runTest {
        for (reverse in listOf(false, true)) {
            val sources: List<IsRootDataModel> = listOf(FirstRecursiveSource, SecondRecursiveSource).let { if (reverse) it.reversed() else it }
            val folder = createTestDBFolder("isolated-recursive-source-schemas-$reverse")
            var store = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(1u to sources[0]))
            fun assertSources(models: Map<UInt, RootDataModel<*>>) {
                for (id in 1u..2u) {
                    val source = assertNotNull(models[id])
                    val expectedType = if (source.Meta.name == FirstRecursiveSource.Meta.name) Float64 else UInt32
                    val detail = (source[1u]!!.definition as EmbeddedValuesDefinition<*>).dataModel
                    val recursive = (detail[2u]!!.definition as EmbeddedValuesDefinition<*>).dataModel
                    assertEquals(expectedType, (detail[1u]!!.definition as NumberDefinition<*>).type)
                    assertEquals(expectedType, (recursive[1u]!!.definition as NumberDefinition<*>).type)
                }
            }
            try {
                store.close()
                store = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(2u to sources[1]))
                store.db.delete(store.transactionMetadataHandle, StorageFormatMetadataKey)
                store.close()
                assertSources(readStoredModelDefinitionsFromPath(folder))
                repeat(2) {
                    store = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(3u to SimpleMarykModel))
                    val context = DefinitionsConversionContext()
                    assertSources((1u..2u).associateWith { id ->
                        val handle = store.storageUpgradeHandles.first { it.getName().contentEquals(TableType.Model.getName(id)) }
                        assertNotNull(readStoredModelDefinition(store.db, handle, context))
                    })
                    store.close()
                    assertSources(readStoredModelDefinitionsFromPath(folder))
                }
            } finally {
                store.close()
                deleteFolder(folder)
            }
        }
    }

    @Test
    fun rewritesLegacySourceSchemaDefaultsAndResumesAfterPairPublication() = runTest {
        for (publishPairBeforeReopen in listOf(false, true)) {
            val folder = createTestDBFolder("legacy-source-schema-$publishPairBeforeReopen")
            var store = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(1u to NanosSchemaSource))
            try {
                val family = store.getColumnFamilies(1u).model
                val modelBytes = assertNotNull(store.db.get(family, modelDefinitionKey))
                val dependentBytes = assertNotNull(store.db.get(family, modelDependentsDefinitionKey))
                val versionBytes = assertNotNull(store.db.get(family, modelVersionKey))
                val legacyModel = legacyNanosDefaultBytes(modelBytes)
                val legacyDependents = legacyNanosDefaultBytes(dependentBytes)
                assertFalse(modelBytes.contentEquals(legacyModel))
                assertFalse(dependentBytes.contentEquals(legacyDependents))
                store.db.put(family, modelDefinitionKey, legacyModel)
                store.db.put(family, modelDependentsDefinitionKey, legacyDependents)
                store.db.delete(family, modelStorageFormatKey)
                store.db.delete(store.transactionMetadataHandle, StorageFormatMetadataKey)
                val source = assertNotNull(readStoredModelDefinition(store.db, family, DefinitionsConversionContext()))
                assertSourceDefaults(source)
                if (publishPairBeforeReopen) {
                    // An interruption after this atomic pair publication still leaves the global stamp legacy.
                    rewriteStoredModelDefinition(store.db, family, source)
                    assertSourceDefaults(assertNotNull(readStoredModelDefinition(store.db, family, DefinitionsConversionContext())))
                }
                store.close()
                // The conversion must rewrite retained source definitions, without publishing configured replacements.
                repeat(2) {
                    store = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(2u to SimpleMarykModel))
                    val sourceHandle = store.storageUpgradeHandles.first { it.getName().contentEquals(TableType.Model.getName(1u)) }
                    assertSourceDefaults(assertNotNull(readStoredModelDefinition(store.db, sourceHandle, DefinitionsConversionContext())))
                    assertContentEquals(versionBytes, store.db.get(sourceHandle, modelVersionKey))
                    assertContentEquals(modelBytes, store.db.get(sourceHandle, modelDefinitionKey))
                    assertContentEquals(dependentBytes, store.db.get(sourceHandle, modelDependentsDefinitionKey))
                    store.close()
                }
            } finally {
                store.close()
                deleteFolder(folder)
            }
        }
    }

    private fun assertSourceDefaults(source: RootDataModel<*>) {
        assertEquals(NanosSchemaSource.Meta.version, source.Meta.version)
        assertEquals(SourceNanosDefault, (source[1u]!!.definition as ListDefinition<*, *>).default)
        val dependent = (source[2u]!!.definition as EmbeddedValuesDefinition<*>).dataModel
        assertEquals(SourceNanosDefault, (dependent[1u]!!.definition as ListDefinition<*, *>).default)
    }

    /** Replace the framed default message with the released packed seconds/nanos pair, repairing enclosing lengths. */
    private fun legacyNanosDefaultBytes(message: ByteArray): ByteArray {
        if (message.contentEquals(byteArrayOf(8, 8, 16, 1))) return byteArrayOf(8, 1)
        var offset = 0
        fun readVarInt(): Int {
            var result = 0
            var shift = 0
            do {
                require(offset < message.size && shift < 32)
                val byte = message[offset++].toInt() and 255
                result = result or ((byte and 127) shl shift)
                shift += 7
            } while (byte and 128 != 0)
            return result
        }
        return try {
            buildList {
                while (offset < message.size) {
                    val fieldStart = offset
                    val tag = readVarInt()
                    require(tag ushr 3 > 0)
                    if (tag and 7 == 2) {
                        val tagEnd = offset
                        val size = readVarInt()
                        require(size >= 0 && size <= message.size - offset)
                        val converted = legacyNanosDefaultBytes(message.copyOfRange(offset, offset + size))
                        message.copyOfRange(fieldStart, tagEnd).forEach(::add)
                        converted.size.writeVarBytes(::add)
                        converted.forEach(::add)
                        offset += size
                    } else {
                        when (tag and 7) {
                            0 -> readVarInt()
                            1 -> offset += 8
                            5 -> offset += 4
                            else -> error("Not a protobuf message")
                        }
                        require(offset <= message.size)
                        message.copyOfRange(fieldStart, offset).forEach(::add)
                    }
                }
            }.toByteArray()
        } catch (_: IllegalArgumentException) {
            message
        } catch (_: IllegalStateException) {
            message
        }
    }
}
