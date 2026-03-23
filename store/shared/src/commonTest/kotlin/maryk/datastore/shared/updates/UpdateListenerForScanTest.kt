package maryk.datastore.shared.updates

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.processors.datastore.scanRange.createScanRange
import maryk.core.properties.types.Bytes
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.changes.IndexChange
import maryk.core.query.changes.IndexDelete
import maryk.core.query.changes.IndexUpdate
import maryk.core.query.orders.ascending
import maryk.core.query.requests.IsFlowRequest
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.requests.ScanUpdatesRequest
import maryk.core.query.requests.scanUpdates
import maryk.core.query.responses.FetchByUpdateHistoryIndex
import maryk.core.query.responses.IsDataResponse
import maryk.core.query.responses.IsResponse
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.UpdatesResponse
import maryk.core.query.responses.ValuesResponse
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.core.query.responses.updates.ProcessResponse
import maryk.test.models.AnyValueMapIndexModel
import maryk.datastore.shared.IsDataStore
import maryk.lib.extensions.compare.compareTo
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
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

    @Test
    fun updateHistoryRollbackRestoresOriginalKeysWhenFetchReturnsNoMatch() = runTest {
        val existingKey = AnyValueMapIndexModel.key(ByteArray(16) { 1 })
        val changedKey = AnyValueMapIndexModel.key(ByteArray(16) { 2 })

        val request = AnyValueMapIndexModel.scanUpdates(limit = 1u)
        val listener = UpdateListenerForScan(
            request = request,
            scanRange = AnyValueMapIndexModel.createScanRange(request.where, request.startKey?.bytes, request.includeStart),
            response = UpdatesResponse(
                dataModel = AnyValueMapIndexModel,
                updates = listOf(OrderedKeysUpdate(listOf(existingKey), version = 1uL)),
                dataFetchType = FetchByUpdateHistoryIndex()
            )
        )

        listener.process(
            Update.Change(
                dataModel = AnyValueMapIndexModel,
                key = changedKey,
                version = 2uL,
                changes = emptyList()
            ),
            object : IsDataStore {
                override val dataModelsById = emptyMap<UInt, IsRootDataModel>()
                override val dataModelIdsByString = emptyMap<String, UInt>()
                override val keepAllVersions = false
                override val keepUpdateHistoryIndex = true
                override val supportsFuzzyQualifierFiltering = false
                override val supportsSubReferenceFiltering = false

                @Suppress("UNCHECKED_CAST")
                override suspend fun <DM : IsRootDataModel, RQ : IsStoreRequest<DM, RP>, RP : IsResponse> execute(
                    request: RQ
                ): RP = ValuesResponse(
                    dataModel = AnyValueMapIndexModel,
                    values = emptyList()
                ) as RP

                override suspend fun <DM : IsRootDataModel, RQ : IsFlowRequest<DM, RP>, RP : IsDataResponse<DM>> executeFlow(
                    request: RQ
                ): Flow<IsUpdateResponse<DM>> = error("unused")

                override suspend fun <DM : IsRootDataModel> processUpdate(
                    updateResponse: UpdateResponse<DM>
                ): ProcessResponse<DM> = error("unused")

                override suspend fun close() = Unit

                override suspend fun closeAllListeners() = Unit
            }
        )

        assertEquals(listOf(existingKey), listener.matchingKeys.value)
    }

    @Test
    fun updateHistoryRefillPreservesFromVersionAndMaxVersions() = runTest {
        val existingKey = AnyValueMapIndexModel.key(ByteArray(16) { 1 })

        val request = AnyValueMapIndexModel.scanUpdates(
            fromVersion = 44uL,
            maxVersions = 5u,
            limit = 1u
        )
        val listener = UpdateListenerForScan(
            request = request,
            scanRange = AnyValueMapIndexModel.createScanRange(request.where, request.startKey?.bytes, request.includeStart),
            response = UpdatesResponse(
                dataModel = AnyValueMapIndexModel,
                updates = listOf(OrderedKeysUpdate(listOf(existingKey), version = 50uL)),
                dataFetchType = FetchByUpdateHistoryIndex()
            )
        )

        var refillRequest: ScanUpdatesRequest<AnyValueMapIndexModel>? = null

        listener.process(
            Update.Deletion(
                dataModel = AnyValueMapIndexModel,
                key = existingKey,
                version = 51uL,
                isHardDelete = true
            ),
            object : IsDataStore {
                override val dataModelsById = emptyMap<UInt, IsRootDataModel>()
                override val dataModelIdsByString = emptyMap<String, UInt>()
                override val keepAllVersions = true
                override val keepUpdateHistoryIndex = true
                override val supportsFuzzyQualifierFiltering = false
                override val supportsSubReferenceFiltering = false

                @Suppress("UNCHECKED_CAST")
                override suspend fun <DM : IsRootDataModel, RQ : IsStoreRequest<DM, RP>, RP : IsResponse> execute(
                    request: RQ
                ): RP {
                    refillRequest = assertIs<ScanUpdatesRequest<AnyValueMapIndexModel>>(request)
                    return UpdatesResponse(
                        dataModel = AnyValueMapIndexModel,
                        updates = listOf(OrderedKeysUpdate(emptyList(), version = 51uL)),
                        dataFetchType = FetchByUpdateHistoryIndex()
                    ) as RP
                }

                override suspend fun <DM : IsRootDataModel, RQ : IsFlowRequest<DM, RP>, RP : IsDataResponse<DM>> executeFlow(
                    request: RQ
                ): Flow<IsUpdateResponse<DM>> = error("unused")

                override suspend fun <DM : IsRootDataModel> processUpdate(
                    updateResponse: UpdateResponse<DM>
                ): ProcessResponse<DM> = error("unused")

                override suspend fun close() = Unit

                override suspend fun closeAllListeners() = Unit
            }
        )

        assertEquals(44uL, refillRequest?.fromVersion)
        assertEquals(5u, refillRequest?.maxVersions)
    }
}
