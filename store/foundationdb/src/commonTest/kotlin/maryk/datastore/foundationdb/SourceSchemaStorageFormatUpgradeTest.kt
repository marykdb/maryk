package maryk.datastore.foundationdb

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.models.DataModel
import maryk.core.models.RootDataModel
import maryk.core.models.IsRootDataModel
import maryk.core.models.definitions.DataModelDefinition
import maryk.core.models.migration.StorageFormat
import maryk.core.properties.definitions.DateTimeDefinition
import maryk.core.properties.definitions.IsEmbeddedValuesDefinition
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.number
import maryk.core.properties.types.TimePrecision.NANOS
import maryk.core.properties.types.Version
import maryk.core.properties.types.numeric.Float64
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.DefinitionsConversionContext
import maryk.datastore.foundationdb.model.encodeModelDefinition
import maryk.datastore.foundationdb.model.modelDefinitionKey
import maryk.datastore.foundationdb.model.modelDependentsDefinitionKey
import maryk.datastore.foundationdb.model.readStoredModelDefinition
import maryk.datastore.foundationdb.model.readStoredModelDefinitionsFromDirectory
import maryk.datastore.foundationdb.model.sourceSchemaStorageFormatKey
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.shared.migration.StorageFormatMetadataKey
import maryk.test.models.SimpleMarykModel
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val sourceNanosDefaults = listOf(LocalDateTime(1970, 1, 1, 0, 0, 8, 1))

private object NanosDefaultDetail : DataModel<NanosDefaultDetail>() {
    val stamps by list(1u, valueDefinition = DateTimeDefinition(precision = NANOS), default = sourceNanosDefaults)
}

private object NanosDefaultSourceRecord : RootDataModel<NanosDefaultSourceRecord>(version = Version(3, 2, 1)) {
    val stamps by list(1u, valueDefinition = DateTimeDefinition(precision = NANOS), default = sourceNanosDefaults)
    val detail by embed(2u, dataModel = { NanosDefaultDetail }, required = false)
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

class SourceSchemaStorageFormatUpgradeTest {
    @Test
    fun retainedSourcePairsKeepTheirOwnRecursiveDependenciesInEitherModelOrder() = runTest {
        for (reverse in listOf(false, true)) {
            val sources: List<IsRootDataModel> = listOf(FirstRecursiveSource, SecondRecursiveSource).let { if (reverse) it.reversed() else it }
            val directory = listOf("maryk", "isolated-recursive-schemas-$reverse-${Random.nextInt()}")
            suspend fun open(id: UInt, source: IsRootDataModel) = FoundationDBDataStore.open(
                fdbClusterFilePath = "./fdb.cluster", directoryPath = directory, dataModelsById = mapOf(id to source))
            fun assertSources(models: Map<UInt, RootDataModel<*>>) {
                for (id in 1u..2u) {
                    val source = assertNotNull(models[id])
                    val expectedType = if (source.Meta.name == FirstRecursiveSource.Meta.name) Float64 else UInt32
                    val detail = assertIs<IsEmbeddedValuesDefinition<*, *>>(source[1u]?.definition).dataModel
                    val recursive = assertIs<IsEmbeddedValuesDefinition<*, *>>(detail[2u]?.definition).dataModel
                    assertEquals(expectedType, assertIs<NumberDefinition<*>>(detail[1u]?.definition).type)
                    assertEquals(expectedType, assertIs<NumberDefinition<*>>(recursive[1u]?.definition).type)
                }
            }
            var store = open(1u, sources[0])
            try {
                store.close()
                store = open(2u, sources[1])
                val prefixes = store.readStoredModelNamesById().mapValues { (id, name) ->
                    store.openTableDirs(id, name, false).modelPrefix
                }
                store.tc.run { it.clear(store.storageFormatPrefix + StorageFormatMetadataKey) }
                store.close()
                assertSources(readStoredModelDefinitionsFromDirectory("./fdb.cluster", directory))
                repeat(2) {
                    store = open(3u, SimpleMarykModel)
                    val context = DefinitionsConversionContext()
                    assertSources((1u..2u).associateWith { id ->
                        assertNotNull(readStoredModelDefinition(store.tc, prefixes.getValue(id), context))
                    })
                    store.close()
                    assertSources(readStoredModelDefinitionsFromDirectory("./fdb.cluster", directory))
                }
            } finally {
                store.close()
            }
        }
    }

    @Test
    fun rewritesRetainedSourceAndDependentDefaultsAndResumesWithTheirCurrentMarker() = runTest {
        val directory = listOf("maryk", "source-schema-upgrade-${Random.nextInt()}")
        suspend fun openSource() = FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster", directoryPath = directory,
            dataModelsById = mapOf(1u to NanosDefaultSourceRecord), keepAllVersions = true)
        suspend fun openRetained() = FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster", directoryPath = directory,
            dataModelsById = mapOf(2u to SimpleMarykModel), keepAllVersions = true)
        var store = openSource()
        try {
            fun assertDefaults(source: RootDataModel<*>) {
                assertEquals(NanosDefaultSourceRecord.Meta.version, source.Meta.version)
                assertEquals(sourceNanosDefaults, assertIs<IsListDefinition<*, *>>(source[1u]?.definition).default)
                val detail = assertIs<IsEmbeddedValuesDefinition<*, *>>(source[2u]?.definition).dataModel
                assertEquals(sourceNanosDefaults, assertIs<IsListDefinition<*, *>>(detail[1u]?.definition).default)
            }
            val sourcePrefix = store.getTableDirs(1u).modelPrefix
            val definition = encodeModelDefinition(NanosDefaultSourceRecord)
            val (legacyRoot, rootDefaults) = releasedNanosDefaults(definition.model)
            val (legacyDependents, dependentDefaults) = releasedNanosDefaults(assertNotNull(definition.dependents))
            assertTrue(rootDefaults > 0)
            assertTrue(dependentDefaults > 0)
            store.tc.run { transaction ->
                transaction.set(sourcePrefix + modelDefinitionKey, legacyRoot)
                transaction.set(sourcePrefix + modelDependentsDefinitionKey, legacyDependents)
                transaction.clear(sourcePrefix + sourceSchemaStorageFormatKey)
                transaction.clear(store.storageFormatPrefix + StorageFormatMetadataKey)
            }
            assertDefaults(assertNotNull(readStoredModelDefinition(store.tc, sourcePrefix, DefinitionsConversionContext())))
            store.close()
            assertDefaults(assertNotNull(readStoredModelDefinitionsFromDirectory("./fdb.cluster", directory)[1u]))
            store = openRetained()
            fun assertSourceDefaults() {
                val source = assertNotNull(readStoredModelDefinition(store.tc, sourcePrefix, DefinitionsConversionContext()))
                assertDefaults(source)
                store.tc.run { transaction ->
                    assertEquals(StorageFormat.CURRENT.toString(),
                        transaction.get(sourcePrefix + sourceSchemaStorageFormatKey).awaitResult()?.decodeToString())
                }
            }
            assertSourceDefaults()
            // Simulate interruption after a definition pair was rewritten, before global publication.
            store.tc.run { it.clear(store.storageFormatPrefix + StorageFormatMetadataKey) }
            store.close()
            assertDefaults(assertNotNull(readStoredModelDefinitionsFromDirectory("./fdb.cluster", directory)[1u]))
            store = openRetained()
            assertSourceDefaults()
            store.close()
            store = openSource()
            assertSourceDefaults()
        } finally {
            store.close()
        }
    }
}

/** Rewrite only the known default field; enclosing message lengths are independently repaired. */
private fun releasedNanosDefaults(bytes: ByteArray): Pair<ByteArray, Int> {
    var offset = 0
    fun readVar(): ULong? {
        var result = 0uL
        var shift = 0
        while (offset < bytes.size && shift < 64) {
            val byte = bytes[offset++].toInt() and 255
            result = result or ((byte and 127).toULong() shl shift)
            if (byte and 128 == 0) return result
            shift += 7
        }
        return null
    }
    val result = mutableListOf<Byte>()
    var replacements = 0
    while (offset < bytes.size) {
        val start = offset
        val tag = readVar() ?: return bytes to 0
        if (tag shr 3 == 0uL) return bytes to 0
        val keyEnd = offset
        when ((tag and 7uL).toInt()) {
            0 -> { readVar() ?: return bytes to 0; result.addAll(bytes.copyOfRange(start, offset).toList()) }
            1, 5 -> {
                offset += if (tag and 7uL == 1uL) 8 else 4
                if (offset > bytes.size) return bytes to 0
                result.addAll(bytes.copyOfRange(start, offset).toList())
            }
            2 -> {
                val length = readVar()?.toInt() ?: return bytes to 0
                if (length < 0 || length > bytes.size - offset) return bytes to 0
                val payload = bytes.copyOfRange(offset, offset + length)
                offset += length
                val rewritten = if (tag == 50uL && payload.contentEquals(byteArrayOf(8, 8, 16, 1))) {
                    // Released default: seconds=8, nanos=1 as a packed varint pair.
                    // Reading these bytes as a current message incorrectly yields seconds=1.
                    byteArrayOf(8, 1) to 1
                } else releasedNanosDefaults(payload)
                result.addAll(bytes.copyOfRange(start, keyEnd).toList())
                rewritten.first.size.toUInt().writeVarBytes(result::add)
                result.addAll(rewritten.first.toList())
                replacements += rewritten.second
            }
            else -> return bytes to 0
        }
    }
    return result.toByteArray() to replacements
}
