package maryk.datastore.indexeddb

import kotlinx.coroutines.test.runTest
import maryk.core.models.RootDataModel
import maryk.core.models.key
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.reference
import maryk.core.properties.types.numeric.UInt32
import maryk.core.protobuf.WriteCache
import maryk.core.query.DefinitionsConversionContext
import maryk.core.query.requests.add
import maryk.core.query.requests.get
import maryk.core.query.responses.statuses.AddSuccess
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private object SubsetReferenceTarget : RootDataModel<SubsetReferenceTarget>(
    keyDefinition = { SubsetReferenceTarget.id.ref() },
) {
    val id by number(index = 1u, type = UInt32, final = true)
}

private object SubsetReferenceOwner : RootDataModel<SubsetReferenceOwner>() {
    val target by reference(index = 1u, dataModel = { SubsetReferenceTarget })
}

class SourceReferenceWidthTest {
    @Test
    fun subsetOpeningStillLoadsUnconfiguredPersistedReferenceTargets() = runTest {
        installIndexedDbForTests()
        val database = "maryk-source-reference-subset-${Random.nextInt()}"
        var store = IndexedDbDataStore.open(database, mapOf(1u to SubsetReferenceOwner, 2u to SubsetReferenceTarget))
        try {
            val original = SubsetReferenceOwner.create {
                target with SubsetReferenceTarget.key(byteArrayOf(1, 2, 3, 4))
            }
            val added = assertIs<AddSuccess<SubsetReferenceOwner>>(store.execute(SubsetReferenceOwner.add(original)).statuses.single())

            // Older source metadata names its reference target without a width.
            val context = DefinitionsConversionContext()
            val cache = WriteCache()
            val length = RootDataModel.Model.Serializer.calculateObjectProtoBufLength(SubsetReferenceOwner, cache, context)
            val bytes = ByteArray(length)
            var offset = 0
            RootDataModel.Model.Serializer.writeObjectProtoBuf(SubsetReferenceOwner, cache, { bytes[offset++] = it }, context)
            store.byteStore.put("meta", byteArrayOf(3, 0, 0, 0, 1), bytes)
            store.close()

            repeat(2) {
                store = IndexedDbDataStore.open(database, mapOf(1u to SubsetReferenceOwner))
                val row = store.execute(SubsetReferenceOwner.get(added.key)).values.single()
                assertEquals(original, row.values)
                assertEquals(added.version, row.lastVersion)
                store.close()
            }
        } finally { store.close() }
    }
}
