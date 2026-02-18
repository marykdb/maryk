package maryk.datastore.shared.updates

import kotlinx.coroutines.test.runTest
import maryk.core.models.key
import maryk.core.processors.datastore.scanRange.createScanRange
import maryk.core.properties.types.Bytes
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.changes.IndexChange
import maryk.core.query.changes.IndexDelete
import maryk.core.query.changes.IndexUpdate
import maryk.core.query.orders.ascending
import maryk.core.query.requests.scanUpdates
import maryk.core.query.responses.ValuesResponse
import maryk.test.models.AnyValueMapIndexModel
import maryk.lib.extensions.compare.compareTo
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UpdateListenerForScanTest {
    @Test
    fun changeOrderHandlesMultipleUpdatesForSameIndex() = runTest {
        val key = AnyValueMapIndexModel.key(ByteArray(16) { 1 })

        val oldValues = AnyValueMapIndexModel.create {
            name with "a"
            mapValues with mapOf("k2" to "v2")
        }
        val newValues = AnyValueMapIndexModel.create {
            name with "a"
            mapValues with mapOf("k0" to "v0", "k8" to "v8")
        }

        val index = AnyValueMapIndexModel.Meta.indexes!!.first()
        val oldIndexKeys = index.toStorageByteArraysForIndex(oldValues, key.bytes)
        val newIndexKeys = index.toStorageByteArraysForIndex(newValues, key.bytes)
        val oldKeyK2 = oldIndexKeys.single()
        val newKeyK0 = newIndexKeys.minWithOrNull { a, b -> a compareTo b }!!
        val newKeyK8 = newIndexKeys.maxWithOrNull { a, b -> a compareTo b }!!

        val request = AnyValueMapIndexModel.scanUpdates(
            order = AnyValueMapIndexModel { mapValues.refToAnyKey() }.ascending(),
            limit = 1u
        )
        val response = ValuesResponse(
            dataModel = AnyValueMapIndexModel,
            values = listOf(
                ValuesWithMetaData(
                    key = key,
                    values = oldValues,
                    firstVersion = 1uL,
                    lastVersion = 1uL,
                    isDeleted = false
                )
            )
        )
        val listener = UpdateListenerForScan(
            request = request,
            scanRange = AnyValueMapIndexModel.createScanRange(request.where, request.startKey?.bytes, request.includeStart),
            response = response
        )

        var handledIndex: Int? = null
        var orderChanged = false

        listener.changeOrder(
            Update.Change(
                dataModel = AnyValueMapIndexModel,
                key = key,
                version = 2uL,
                changes = listOf(
                    IndexChange(
                        listOf(
                            IndexDelete(index.referenceStorageByteArray, Bytes(oldKeyK2)),
                            IndexUpdate(index.referenceStorageByteArray, Bytes(newKeyK8), null),
                            IndexUpdate(index.referenceStorageByteArray, Bytes(newKeyK0), null)
                        )
                    )
                )
            )
        ) { newIndex, changed ->
            handledIndex = newIndex
            orderChanged = changed
        }

        assertEquals(0, handledIndex)
        assertTrue(orderChanged)
        assertEquals(listOf(key), listener.matchingKeys.value)
        assertContentEquals(newKeyK0, listener.sortedValues?.value?.single())
    }
}
