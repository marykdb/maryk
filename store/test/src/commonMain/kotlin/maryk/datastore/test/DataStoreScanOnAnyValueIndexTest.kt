package maryk.datastore.test

import maryk.core.properties.types.Key
import maryk.core.query.changes.Change
import maryk.core.query.changes.SetChange
import maryk.core.query.changes.change
import maryk.core.query.filters.Equals
import maryk.core.query.orders.ascending
import maryk.core.query.orders.descending
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.scan
import maryk.core.query.responses.FetchByIndexScan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.datastore.shared.IsDataStore
import maryk.test.models.AnyValueIncMapIndexModel
import maryk.test.models.AnyValueMapIndexModel
import maryk.test.models.AnyValueSetIndexModel
import kotlin.test.assertIs
import kotlin.test.expect

class DataStoreScanOnAnyValueIndexTest(
    val dataStore: IsDataStore
) : IsDataStoreTest {
    private val mapKeys = mutableListOf<Key<AnyValueMapIndexModel>>()
    private val incMapKeys = mutableListOf<Key<AnyValueIncMapIndexModel>>()
    private val setKeys = mutableListOf<Key<AnyValueSetIndexModel>>()

    override val allTests = mapOf(
        "executeScanOnMapRefToAnyIndexRequest" to ::executeScanOnMapRefToAnyIndexRequest,
        "executeScanOnIncMapRefToAnyIndexRequest" to ::executeScanOnIncMapRefToAnyIndexRequest,
        "executeScanOnSetRefToAnyIndexRequest" to ::executeScanOnSetRefToAnyIndexRequest,
        "executeFilterOnMapRefToAnyIndexRequest" to ::executeFilterOnMapRefToAnyIndexRequest,
        "executeFilterOnIncMapRefToAnyIndexRequest" to ::executeFilterOnIncMapRefToAnyIndexRequest,
        "executeFilterOnSetRefToAnyIndexRequest" to ::executeFilterOnSetRefToAnyIndexRequest,
        "executeChangeUpdatesMapRefToAnyIndexRequest" to ::executeChangeUpdatesMapRefToAnyIndexRequest,
        "executeChangeUpdatesIncMapRefToAnyIndexRequest" to ::executeChangeUpdatesIncMapRefToAnyIndexRequest,
        "executeChangeUpdatesSetRefToAnyIndexRequest" to ::executeChangeUpdatesSetRefToAnyIndexRequest,
    )

    private val mapObjects = arrayOf(
        AnyValueMapIndexModel.create {
            name with "a"
            mapValues with mapOf("k1" to "m3")
        },
        AnyValueMapIndexModel.create {
            name with "b"
            mapValues with mapOf("k2" to "m1")
        },
        AnyValueMapIndexModel.create {
            name with "c"
            mapValues with mapOf("k3" to "m2")
        },
    )

    private val incMapObjects = arrayOf(
        AnyValueIncMapIndexModel.create {
            name with "a"
            incMapValues with mapOf(2u to "i2")
        },
        AnyValueIncMapIndexModel.create {
            name with "b"
            incMapValues with mapOf(3u to "i3")
        },
        AnyValueIncMapIndexModel.create {
            name with "c"
            incMapValues with mapOf(1u to "i1")
        },
    )

    private val setObjects = arrayOf(
        AnyValueSetIndexModel.create {
            name with "a"
            setValues with setOf("s3")
        },
        AnyValueSetIndexModel.create {
            name with "b"
            setValues with setOf("s1")
        },
        AnyValueSetIndexModel.create {
            name with "c"
            setValues with setOf("s2")
        },
    )

    override suspend fun initData() {
        dataStore.execute(AnyValueMapIndexModel.add(*mapObjects)).statuses.forEach { status ->
            mapKeys.add(assertStatusIs<AddSuccess<AnyValueMapIndexModel>>(status).key)
        }
        dataStore.execute(AnyValueIncMapIndexModel.add(*incMapObjects)).statuses.forEach { status ->
            incMapKeys.add(assertStatusIs<AddSuccess<AnyValueIncMapIndexModel>>(status).key)
        }
        dataStore.execute(AnyValueSetIndexModel.add(*setObjects)).statuses.forEach { status ->
            setKeys.add(assertStatusIs<AddSuccess<AnyValueSetIndexModel>>(status).key)
        }
    }

    override suspend fun resetData() {
        dataStore.execute(
            AnyValueMapIndexModel.delete(*mapKeys.toTypedArray(), hardDelete = true)
        ).statuses.forEach {
            assertStatusIs<DeleteSuccess<*>>(it)
        }
        dataStore.execute(
            AnyValueIncMapIndexModel.delete(*incMapKeys.toTypedArray(), hardDelete = true)
        ).statuses.forEach {
            assertStatusIs<DeleteSuccess<*>>(it)
        }
        dataStore.execute(
            AnyValueSetIndexModel.delete(*setKeys.toTypedArray(), hardDelete = true)
        ).statuses.forEach {
            assertStatusIs<DeleteSuccess<*>>(it)
        }
        mapKeys.clear()
        incMapKeys.clear()
        setKeys.clear()
    }

    private suspend fun executeScanOnMapRefToAnyIndexRequest() {
        val scanResponse = dataStore.execute(
            AnyValueMapIndexModel.scan(
                order = AnyValueMapIndexModel { mapValues.refToAnyKey() }.ascending()
            )
        )

        assertIs<FetchByIndexScan>(scanResponse.dataFetchType)
        expect(listOf("a", "b", "c")) {
            scanResponse.values.map { it.values { name } }.distinct()
        }
    }

    private suspend fun executeScanOnIncMapRefToAnyIndexRequest() {
        val scanResponse = dataStore.execute(
            AnyValueIncMapIndexModel.scan(
                order = AnyValueIncMapIndexModel { incMapValues.refToAnyKey() }.descending()
            )
        )

        assertIs<FetchByIndexScan>(scanResponse.dataFetchType)
        expect(listOf("c", "a", "b")) {
            scanResponse.values.map { it.values { name } }.distinct()
        }
    }

    private suspend fun executeScanOnSetRefToAnyIndexRequest() {
        val scanResponse = dataStore.execute(
            AnyValueSetIndexModel.scan(
                order = AnyValueSetIndexModel { setValues.refToAny() }.ascending()
            )
        )

        assertIs<FetchByIndexScan>(scanResponse.dataFetchType)
        expect(listOf("b", "c", "a")) {
            scanResponse.values.map { it.values { name } }.distinct()
        }
    }

    private suspend fun executeFilterOnMapRefToAnyIndexRequest() {
        val scanResponse = dataStore.execute(
            AnyValueMapIndexModel.scan(
                where = Equals(
                    AnyValueMapIndexModel { mapValues.refToAnyKey() } with "k3"
                )
            )
        )

        assertIs<FetchByIndexScan>(scanResponse.dataFetchType)
        expect(listOf("c")) {
            scanResponse.values.map { it.values { name } }.distinct()
        }
    }

    private suspend fun executeFilterOnIncMapRefToAnyIndexRequest() {
        val scanResponse = dataStore.execute(
            AnyValueIncMapIndexModel.scan(
                where = Equals(
                    AnyValueIncMapIndexModel { incMapValues.refToAnyKey() } with 2u
                )
            )
        )

        assertIs<FetchByIndexScan>(scanResponse.dataFetchType)
        expect(listOf("a")) {
            scanResponse.values.map { it.values { name } }.distinct()
        }
    }

    private suspend fun executeFilterOnSetRefToAnyIndexRequest() {
        val scanResponse = dataStore.execute(
            AnyValueSetIndexModel.scan(
                where = Equals(
                    AnyValueSetIndexModel { setValues.refToAny() } with "s2"
                )
            )
        )

        assertIs<FetchByIndexScan>(scanResponse.dataFetchType)
        expect(listOf("c")) {
            scanResponse.values.map { it.values { name } }.distinct()
        }
    }

    private suspend fun executeChangeUpdatesMapRefToAnyIndexRequest() {
        val changeResponse = dataStore.execute(
            AnyValueMapIndexModel.change(
                mapKeys[0].change(
                    Change(
                        AnyValueMapIndexModel { mapValues refAt "k0" } with "m3"
                    )
                )
            )
        )
        changeResponse.statuses.forEach {
            assertStatusIs<ChangeSuccess<AnyValueMapIndexModel>>(it)
        }

        val scanResponse = dataStore.execute(
            AnyValueMapIndexModel.scan(
                where = Equals(
                    AnyValueMapIndexModel { mapValues.refToAnyKey() } with "k0"
                )
            )
        )

        assertIs<FetchByIndexScan>(scanResponse.dataFetchType)
        expect(listOf("a")) {
            scanResponse.values.map { it.values { name } }.distinct()
        }
    }

    private suspend fun executeChangeUpdatesIncMapRefToAnyIndexRequest() {
        val changeResponse = dataStore.execute(
            AnyValueIncMapIndexModel.change(
                incMapKeys[0].change(
                    Change(
                        AnyValueIncMapIndexModel { incMapValues refAt 4u } with "i2"
                    )
                )
            )
        )
        changeResponse.statuses.forEach {
            assertStatusIs<ChangeSuccess<AnyValueIncMapIndexModel>>(it)
        }

        val scanResponse = dataStore.execute(
            AnyValueIncMapIndexModel.scan(
                where = Equals(
                    AnyValueIncMapIndexModel { incMapValues.refToAnyKey() } with 4u
                )
            )
        )

        assertIs<FetchByIndexScan>(scanResponse.dataFetchType)
        expect(listOf("a")) {
            scanResponse.values.map { it.values { name } }.distinct()
        }
    }

    private suspend fun executeChangeUpdatesSetRefToAnyIndexRequest() {
        val changeResponse = dataStore.execute(
            AnyValueSetIndexModel.change(
                setKeys[0].change(
                    SetChange(
                        AnyValueSetIndexModel { setValues::ref }.change(
                            addValues = setOf("s0")
                        )
                    )
                )
            )
        )
        changeResponse.statuses.forEach {
            assertStatusIs<ChangeSuccess<AnyValueSetIndexModel>>(it)
        }

        val scanResponse = dataStore.execute(
            AnyValueSetIndexModel.scan(
                where = Equals(
                    AnyValueSetIndexModel { setValues.refToAny() } with "s0"
                )
            )
        )

        assertIs<FetchByIndexScan>(scanResponse.dataFetchType)
        expect(listOf("a")) {
            scanResponse.values.map { it.values { name } }.distinct()
        }
    }

}
