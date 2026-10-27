package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.EnumDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.map
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IndexedEnumImpl
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ValidationFail
import maryk.createTestDBFolder
import maryk.deleteFolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private sealed class BoundedEnum(index: UInt) : IndexedEnumImpl<BoundedEnum>(index) {
    object Low : BoundedEnum(1u)
    object Upper : BoundedEnum(65535u)
    class Unknown(index: UInt) : BoundedEnum(index)

    companion object : IndexedEnumDefinition<BoundedEnum>(BoundedEnum::class, { listOf(Low, Upper) })
}

private object BoundedEnumMap : RootDataModel<BoundedEnumMap>() {
    val items by map(1u, keyDefinition = EnumDefinition(enum = BoundedEnum), valueDefinition = StringDefinition())
}

class EnumStorageBoundsTest {
    @Test
    fun invalidEnumMapMutationsAreRejectedAndValidBoundaryValuesSurviveReopen() = runTest {
        val folder = createTestDBFolder("enum-storage-bounds")
        val original = mapOf(BoundedEnum.Low to "first", BoundedEnum.Upper to "last")
        val invalid = mapOf(BoundedEnum.Low to "first", BoundedEnum.Unknown(65537u) to "overwrite")
        try {
            val writer = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(1u to BoundedEnumMap))
            val key = try {
                val key = assertIs<AddSuccess<BoundedEnumMap>>(writer.execute(BoundedEnumMap.add(
                    BoundedEnumMap.create { items with original }
                )).statuses.single()).key
                assertIs<ValidationFail<*>>(writer.execute(BoundedEnumMap.add(
                    BoundedEnumMap.create { items with invalid }
                )).statuses.single())
                assertIs<ValidationFail<*>>(writer.execute(BoundedEnumMap.change(
                    key.change(Change(BoundedEnumMap.ref { items } with invalid))
                )).statuses.single())
                assertEquals(original, writer.execute(BoundedEnumMap.get(key)).values.single().values { items })
                key
            } finally {
                writer.close()
            }
            val reader = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(1u to BoundedEnumMap))
            try {
                assertEquals(original, reader.execute(BoundedEnumMap.get(key)).values.single().values { items })
                assertEquals(1, reader.execute(BoundedEnumMap.scan(allowTableScan = true)).values.size)
            } finally {
                reader.close()
            }
        } finally {
            deleteFolder(folder)
        }
    }
}
