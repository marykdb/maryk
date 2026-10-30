package maryk.datastore.rocksdb

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import maryk.core.models.DataModel
import maryk.core.models.RootDataModel
import maryk.core.models.ValueDataModel
import maryk.core.properties.definitions.DateTimeDefinition
import maryk.core.properties.definitions.IsEmbeddedValuesDefinition
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.dateTime
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.set
import maryk.core.properties.definitions.string
import maryk.core.properties.definitions.valueObject
import maryk.core.properties.types.Key
import maryk.core.properties.types.TimePrecision.NANOS
import maryk.core.properties.types.ValueDataObject
import maryk.core.properties.types.Version
import maryk.core.properties.types.numeric.Float32
import maryk.core.properties.types.numeric.Float64
import maryk.core.query.DefinitionsConversionContext
import maryk.core.query.filters.Equals
import maryk.core.query.orders.Orders
import maryk.core.query.orders.ascending
import maryk.core.query.pairs.with
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.core.query.responses.FetchByIndexScan
import maryk.core.values.ObjectValues
import maryk.core.values.Values
import maryk.datastore.rocksdb.model.readStoredModelDefinition
import maryk.datastore.rocksdb.processors.VersionedComparator
import org.rocksdb.ColumnFamilyDescriptor
import org.rocksdb.ColumnFamilyHandle
import org.rocksdb.ColumnFamilyOptions
import org.rocksdb.ComparatorOptions
import org.rocksdb.DBOptions
import org.rocksdb.RocksDB
import org.rocksdb.WriteBatch
import org.rocksdb.WriteOptions
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

val ReleasedNanosDefault = listOf(LocalDateTime(1970, 1, 1, 0, 0, 8, 1))

object ReleasedFixtureDependent : DataModel<ReleasedFixtureDependent>() {
    val stamps by list(1u, valueDefinition = DateTimeDefinition(precision = NANOS), default = ReleasedNanosDefault)
    val number by number(2u, type = Float64, default = -0.0)
}

data class ReleasedPacked(val float: Float, val double: Double) : ValueDataObject(toBytes(float, double)) {
    companion object : ValueDataModel<ReleasedPacked, Companion>(ReleasedPacked::class) {
        val float by number(1u, ReleasedPacked::float, type = Float32)
        val double by number(2u, ReleasedPacked::double, type = Float64)
        override fun invoke(values: ObjectValues<ReleasedPacked, Companion>) = ReleasedPacked(values(1u), values(2u))
    }
}

object ReleasedFixtureRoot : RootDataModel<ReleasedFixtureRoot>(
    version = Version(3, 1, 2),
    indexes = { listOf(Multiple(ReleasedFixtureRoot.label.ref(), ReleasedFixtureRoot.double.ref()), ReleasedFixtureRoot.packed.ref()) },
) {
    val label by string(1u)
    val float by number(2u, type = Float32)
    val double by number(3u, type = Float64)
    val packed by valueObject(4u, dataModel = ReleasedPacked)
    val stamp by dateTime(5u, precision = NANOS)
    val stamps by list(6u, valueDefinition = DateTimeDefinition(precision = NANOS), default = ReleasedNanosDefault)
    val child by embed(7u, dataModel = { ReleasedFixtureDependent })
    val emptyList by list(8u, valueDefinition = StringDefinition())
    val emptySet by set(9u, valueDefinition = StringDefinition())
    val emptyMap by map(10u, keyDefinition = StringDefinition(), valueDefinition = StringDefinition())
}

/** Exact native-engine 0.13.0 output, without a reverse-current fixture encoder. */
class ReleasedEngineCompatibilityTest {
    private fun resource(name: String): String = assertNotNull(javaClass.getResourceAsStream("/released-0.13.0/$name"))
        .bufferedReader().use { it.readText() }

    private data class ManifestRow(
        val row: Int,
        val key: Key<ReleasedFixtureRoot>,
        val addVersion: ULong,
        val changeVersion: ULong?,
        val floatBits: Int,
        val doubleBits: Long,
        val label: String,
        val stamp: LocalDateTime,
    ) {
        val currentLabel get() = if (row == 0) "changed\u0000label" else label
    }

    private fun manifest() = resource("manifest.tsv").lineSequence().drop(1).filter { it.isNotBlank() }.map { line ->
        val cells = line.split('\t')
        ManifestRow(cells[0].toInt(), Key(cells[1].hexToByteArray()), cells[2].toULong(), cells[3].toULongOrNull(),
            cells[4].toUInt(16).toInt(), cells[5].toULong(16).toLong(),
            cells[6].chunked(4).map { it.toInt(16).toChar() }.joinToString(""), LocalDateTime.parse(cells[7]))
    }.toList()

    private fun restoreRawReleasedRows(destination: File) {
        val lines = resource("rows.tsv").lineSequence().filter { it.isNotBlank() }.map { it.split('\t') }.toList()
        val families = lines.filter { it[0] == "family" }.map { it[1] }
            .sortedBy { if (it == "64656661756c74") 0 else 1 }
        assertEquals(11, families.size)
        assertEquals(419, lines.count { it[0] == "row" })
        RocksDB.loadLibrary()
        val familyOptions = mutableListOf<ColumnFamilyOptions>()
        val comparators = mutableListOf<VersionedComparator>()
        val handles = mutableListOf<ColumnFamilyHandle>()
        ComparatorOptions().use { comparatorOptions ->
            try {
                val descriptors = families.map { hex ->
                    val name = hex.hexToByteArray()
                    val options = ColumnFamilyOptions().also(familyOptions::add)
                    if (name.firstOrNull() in listOf(6.toByte(), 7.toByte(), 8.toByte())) {
                        val comparator = VersionedComparator(comparatorOptions, 16).also(comparators::add)
                        options.setComparator(comparator)
                    }
                    ColumnFamilyDescriptor(name, options)
                }
                DBOptions().setCreateIfMissing(true).setCreateMissingColumnFamilies(true).use { options ->
                    RocksDB.open(options, destination.absolutePath, descriptors, handles).use { database ->
                        val byFamily = families.zip(handles).toMap()
                        WriteBatch().use { batch ->
                            lines.filter { it[0] == "row" }.forEach { row ->
                                batch.put(byFamily.getValue(row[1]), row[2].hexToByteArray(), row[3].hexToByteArray())
                            }
                            WriteOptions().use { database.write(it, batch) }
                        }
                    }
                }
            } finally {
                handles.forEach { it.close() }
                familyOptions.forEach { it.close() }
                comparators.forEach { it.close() }
            }
        }
        File(destination, "MARYK_META.yml").writeText(resource("MARYK_META.yml"))
    }

    private fun verifyValue(row: ManifestRow, values: Values<ReleasedFixtureRoot>, label: String) {
        assertEquals(label, values { ReleasedFixtureRoot.label })
        assertEquals(row.floatBits, values { ReleasedFixtureRoot.float }!!.toRawBits())
        assertEquals(row.doubleBits, values { ReleasedFixtureRoot.double }!!.toRawBits())
        val packed = assertNotNull(values { ReleasedFixtureRoot.packed })
        assertEquals(row.floatBits, packed.float.toRawBits())
        assertEquals(row.doubleBits, packed.double.toRawBits())
        assertContentEquals(ReleasedPacked(Float.fromBits(row.floatBits), Double.fromBits(row.doubleBits)).toByteArray(), packed.toByteArray())
        assertEquals(row.stamp, values { ReleasedFixtureRoot.stamp })
        assertEquals(ReleasedNanosDefault, values { ReleasedFixtureRoot.stamps })
        val child = assertNotNull(values { ReleasedFixtureRoot.child })
        assertEquals(ReleasedNanosDefault, child { ReleasedFixtureDependent.stamps })
        assertEquals((-0.0).toRawBits(), child { ReleasedFixtureDependent.number }!!.toRawBits())
        assertEquals(emptyList(), values { ReleasedFixtureRoot.emptyList })
        assertEquals(emptySet(), values { ReleasedFixtureRoot.emptySet })
        assertEquals(emptyMap(), values { ReleasedFixtureRoot.emptyMap })
    }

    @Test
    fun upgradesExactReleasedEngineBytesAndReopensWithoutLosingBitsDefaultsOrHistory() = runTest {
        val directory = Files.createTempDirectory("maryk-released-engine-compatibility").toFile()
        val rows = manifest()
        try {
            restoreRawReleasedRows(directory)
            repeat(2) {
                val store = RocksDBDataStore.open(relativePath = directory.absolutePath,
                    dataModelsById = mapOf(1u to ReleasedFixtureRoot), keepAllVersions = true, keepUpdateHistoryIndex = true)
                try {
                    for (row in rows) {
                        val current = store.execute(ReleasedFixtureRoot.get(row.key)).values.single()
                        verifyValue(row, current.values, row.currentLabel)
                        assertEquals(row.addVersion, current.firstVersion)
                        assertEquals(row.changeVersion ?: row.addVersion, current.lastVersion)
                        val creation = store.execute(ReleasedFixtureRoot.get(row.key, toVersion = row.addVersion)).values.single()
                        verifyValue(row, creation.values, row.label)
                    }
                    val finalVersion = rows.maxOf { it.changeVersion ?: it.addVersion }
                    for (version in listOf(null, finalVersion)) {
                        val indexed = store.execute(ReleasedFixtureRoot.scan(order = Orders(
                            ReleasedFixtureRoot.ref { label }.ascending(), ReleasedFixtureRoot.ref { double }.ascending()), toVersion = version))
                        assertIs<FetchByIndexScan>(indexed.dataFetchType)
                        assertEquals(rows.sortedBy { it.currentLabel }.map { it.key }, indexed.values.map { it.key })
                        val packedResult = store.execute(ReleasedFixtureRoot.scan(order = ReleasedFixtureRoot.ref { packed }.ascending(), toVersion = version))
                        assertIs<FetchByIndexScan>(packedResult.dataFetchType)
                        assertEquals(rows.map { it.key }.toSet(), packedResult.values.map { it.key }.toSet())
                        val finite = store.execute(ReleasedFixtureRoot.scan(order = ReleasedFixtureRoot.ref { packed }.ascending(),
                            where = Equals(ReleasedFixtureRoot.ref { packed } with ReleasedPacked(-1f, -1.0)), toVersion = version))
                        assertEquals(listOf(rows[2].key), finite.values.map { it.key })
                    }
                    val firstIndex = store.execute(ReleasedFixtureRoot.scan(order = Orders(
                        ReleasedFixtureRoot.ref { label }.ascending(), ReleasedFixtureRoot.ref { double }.ascending()), toVersion = rows[0].addVersion))
                    assertIs<FetchByIndexScan>(firstIndex.dataFetchType)
                    assertEquals(listOf(rows[0].key), firstIndex.values.map { it.key })
                    assertEquals("", firstIndex.values.single().values { label })
                    val source = assertNotNull(readStoredModelDefinition(store.db, store.getColumnFamilies(1u).model, DefinitionsConversionContext()))
                    assertEquals(Version(3, 1, 2), source.Meta.version)
                    assertEquals(ReleasedNanosDefault, assertIs<IsListDefinition<*, *>>(source[6u]!!.definition).default)
                    val dependent = assertIs<IsEmbeddedValuesDefinition<*, *>>(source[7u]!!.definition).dataModel
                    assertEquals(ReleasedNanosDefault, assertIs<IsListDefinition<*, *>>(dependent[1u]!!.definition).default)
                    assertEquals((-0.0).toRawBits(), (assertIs<NumberDefinition<*>>(dependent[2u]!!.definition).default as Double).toRawBits())
                } finally { store.close() }
            }
        } finally { directory.deleteRecursively() }
    }
}
