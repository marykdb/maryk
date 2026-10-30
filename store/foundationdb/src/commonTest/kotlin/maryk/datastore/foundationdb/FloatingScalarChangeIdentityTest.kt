package maryk.datastore.foundationdb

import kotlinx.coroutines.test.runTest
import maryk.core.extensions.bytes.toByteArray
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.number
import maryk.core.properties.types.numeric.Float32
import maryk.core.properties.types.numeric.Float64
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.filters.Equals
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.ValidationFail
import maryk.datastore.shared.TypeIndicator
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.packKey
import kotlin.random.Random

private object RawScalarRecord : RootDataModel<RawScalarRecord>() {
    val double by number(1u, type = Float64, unique = true, required = false)
    val float by number(2u, type = Float32, unique = true, required = false)
}

class FloatingScalarChangeIdentityTest {
    @Test
    fun rawNanChangesMoveUniqueOwnershipAndSurviveHistoryAndReopen() = runTest {
        val doubles = listOf(0x7ff8000000000001L, 0x7ff8000000000002L, 0x7ff8000000000003L).map(Double::fromBits)
        val floats = listOf(0x7fc00001, 0x7fc00002, 0x7fc00003).map(Float::fromBits)
        fun values(index: Int) = RawScalarRecord.create { double with doubles[index]; float with floats[index] }
        fun change(index: Int) = Change(RawScalarRecord.ref { double } with doubles[index], RawScalarRecord.ref { float } with floats[index])
        for (history in listOf(false, true)) {
            val directory = listOf("maryk", "floating-scalar-change-$history-${Random.nextInt()}")
            suspend fun open() = FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster", directoryPath = directory,
                dataModelsById = mapOf(1u to RawScalarRecord), keepAllVersions = history)
            var store = open()
            try {
                val added = assertIs<AddSuccess<RawScalarRecord>>(store.execute(RawScalarRecord.add(values(0))).statuses.single())
                val other = assertIs<AddSuccess<RawScalarRecord>>(store.execute(RawScalarRecord.add(values(1))).statuses.single())
                val changed = assertIs<ChangeSuccess<RawScalarRecord>>(store.execute(RawScalarRecord.change(added.key.change(change(2)))).statuses.single())
                val noOp = assertIs<ChangeSuccess<RawScalarRecord>>(store.execute(RawScalarRecord.change(added.key.change(change(2)))).statuses.single())
                assertEquals(changed.version, noOp.version)
                assertIs<ValidationFail<RawScalarRecord>>(store.execute(RawScalarRecord.change(added.key.change(change(1)))).statuses.single())
                store.close()
                store = open()
                val current = store.execute(RawScalarRecord.get(added.key)).values.single()
                assertEquals(doubles[2].toRawBits(), current.values { double }!!.toRawBits())
                assertEquals(floats[2].toRawBits(), current.values { float }!!.toRawBits())
                assertEquals(changed.version, current.lastVersion)
                assertEquals(emptyList(), store.execute(RawScalarRecord.scan(where = Equals(RawScalarRecord.ref { double } with doubles[0]))).values)
                assertEquals(emptyList(), store.execute(RawScalarRecord.scan(where = Equals(RawScalarRecord.ref { float } with floats[0]))).values)
                assertEquals(listOf(added.key), store.execute(RawScalarRecord.scan(where = Equals(RawScalarRecord.ref { double } with doubles[2]))).values.map { it.key })
                assertEquals(listOf(added.key), store.execute(RawScalarRecord.scan(where = Equals(RawScalarRecord.ref { float } with floats[2]))).values.map { it.key })
                assertEquals(listOf(other.key), store.execute(RawScalarRecord.scan(where = Equals(RawScalarRecord.ref { double } with doubles[1]))).values.map { it.key })
                assertEquals(listOf(other.key), store.execute(RawScalarRecord.scan(where = Equals(RawScalarRecord.ref { float } with floats[1]))).values.map { it.key })
                val doubleReference = RawScalarRecord.ref { double }.toStorageByteArray()
                val floatReference = RawScalarRecord.ref { float }.toStorageByteArray()
                val oldDouble = RawScalarRecord.double.definition.toStorageBytes(doubles[0], TypeIndicator.NoTypeIndicator.byte)
                val oldFloat = RawScalarRecord.float.definition.toStorageBytes(floats[0], TypeIndicator.NoTypeIndicator.byte)
                val newDouble = RawScalarRecord.double.definition.toStorageBytes(doubles[2], TypeIndicator.NoTypeIndicator.byte)
                val newFloat = RawScalarRecord.float.definition.toStorageBytes(floats[2], TypeIndicator.NoTypeIndicator.byte)
                val dirs = store.getTableDirs(RawScalarRecord)
                store.runTransaction { tr ->
                    assertContentEquals(changed.version.toByteArray() + newDouble, tr.get(packKey(dirs.tablePrefix, added.key.bytes, doubleReference)).awaitResult())
                    assertContentEquals(changed.version.toByteArray() + newFloat, tr.get(packKey(dirs.tablePrefix, added.key.bytes, floatReference)).awaitResult())
                    assertNull(tr.get(packKey(dirs.uniquePrefix, doubleReference, oldDouble)).awaitResult())
                    assertNull(tr.get(packKey(dirs.uniquePrefix, floatReference, oldFloat)).awaitResult())
                    assertContentEquals(changed.version.toByteArray() + added.key.bytes, tr.get(packKey(dirs.uniquePrefix, doubleReference, newDouble)).awaitResult())
                    assertContentEquals(changed.version.toByteArray() + added.key.bytes, tr.get(packKey(dirs.uniquePrefix, floatReference, newFloat)).awaitResult())
                }
                assertIs<AddSuccess<RawScalarRecord>>(store.execute(RawScalarRecord.add(values(0))).statuses.single())
                assertIs<ValidationFail<RawScalarRecord>>(store.execute(RawScalarRecord.add(values(2))).statuses.single())
                if (history) {
                    val original = store.execute(RawScalarRecord.get(added.key, toVersion = added.version)).values.single()
                    assertEquals(doubles[0].toRawBits(), original.values { double }!!.toRawBits())
                    assertEquals(floats[0].toRawBits(), original.values { float }!!.toRawBits())
                    assertEquals(listOf(added.key), store.execute(RawScalarRecord.scan(where = Equals(RawScalarRecord.ref { double } with doubles[0]), toVersion = added.version)).values.map { it.key })
                    assertEquals(listOf(added.key), store.execute(RawScalarRecord.scan(where = Equals(RawScalarRecord.ref { float } with floats[0]), toVersion = added.version)).values.map { it.key })
                }
            } finally {
                store.close()

            }
        }
    }
}
