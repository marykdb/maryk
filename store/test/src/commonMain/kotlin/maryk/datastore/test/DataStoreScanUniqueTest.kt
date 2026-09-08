package maryk.datastore.test

import kotlinx.datetime.LocalDate
import maryk.core.properties.types.Key
import maryk.core.properties.types.invoke
import maryk.core.query.changes.Change
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.change
import maryk.core.query.filters.Equals
import maryk.core.query.filters.Prefix
import maryk.core.query.filters.ValueIn
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.scan
import maryk.core.query.responses.FetchByUniqueKey
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.statuses.DoesNotExist
import maryk.datastore.shared.IsDataStore
import maryk.test.models.CompleteMarykModel
import maryk.test.models.MarykEnumEmbedded.E1
import maryk.test.models.MarykTypeEnum.T2
import maryk.test.models.SimpleMarykModel
import maryk.test.models.SimpleMarykTypeEnum.S1
import kotlin.test.assertTrue
import kotlin.test.expect

class DataStoreScanUniqueTest(
    val dataStore: IsDataStore
) : IsDataStoreTest {
    private val keys = mutableListOf<Key<CompleteMarykModel>>()
    private var lowestVersion = ULong.MAX_VALUE

    override val allTests = mapOf(
        "executeSimpleScanFilterRequest" to ::executeSimpleScanFilterRequest,
        "executeUniquePrefixReturnsAllMatches" to ::executeUniquePrefixReturnsAllMatches,
        "executeUniqueValueInReturnsAllMatches" to ::executeUniqueValueInReturnsAllMatches,
        "executeUniqueValueInWithAbsentFirstValue" to ::executeUniqueValueInWithAbsentFirstValue,
        "executeSimpleScanFilterWithToVersionRequest" to ::executeSimpleScanFilterWithToVersionRequest,
        "executeHistoricalUniqueDoesNotMatchPrefixCollision" to ::executeHistoricalUniqueDoesNotMatchPrefixCollision,
        "executeHistoricalUniqueCanIncludeSoftDeletedObject" to ::executeHistoricalUniqueCanIncludeSoftDeletedObject,
        "executeHistoricalUniqueCanIncludeObjectSoftDeletedByChange" to ::executeHistoricalUniqueCanIncludeObjectSoftDeletedByChange,
    )

    private val objects = arrayOf(
        CompleteMarykModel.create {
            string with "haas"
            number with 24u
            subModel with SimpleMarykModel.create {
                value with "haha"
            }
            multi with T2(22)
            booleanForKey with true
            dateForKey with LocalDate(2018, 3, 29)
            multiForKey with S1("hii")
            enumEmbedded with E1
        }
    )

    override suspend fun initData() {
        val addResponse = dataStore.execute(
            CompleteMarykModel.add(*objects)
        )
        addResponse.statuses.forEach { status ->
            val response = assertStatusIs<AddSuccess<CompleteMarykModel>>(status)
            keys.add(response.key)
            if (response.version < lowestVersion) {
                // Add lowest version for scan test
                lowestVersion = response.version
            }
        }
    }

    override suspend fun resetData() {
        dataStore.execute(
            CompleteMarykModel.delete(*keys.toTypedArray(), hardDelete = true)
        ).statuses.forEach {
            when (it) {
                is DeleteSuccess<*> -> {}
                is DoesNotExist<*> -> {}
                else -> assertStatusIs<DeleteSuccess<*>>(it)
            }
        }
        keys.clear()
        lowestVersion = ULong.MAX_VALUE
    }

    private suspend fun executeSimpleScanFilterRequest() {
        val scanResponse = dataStore.execute(
            CompleteMarykModel.scan(
                where = Equals(
                    CompleteMarykModel.string.ref() with "haas"
                )
            )
        )

        expect(1) { scanResponse.values.size }
        expect(FetchByUniqueKey(byteArrayOf(9))) { scanResponse.dataFetchType }

        scanResponse.values[0].let {
            expect(objects[0]) { it.values }
            expect(keys[0]) { it.key }
        }
    }

    private suspend fun executeSimpleScanFilterWithToVersionRequest() {
        val changeResponse = dataStore.execute(
            CompleteMarykModel.change(
                keys[0].change(
                    Change(CompleteMarykModel.string.ref() with "haas2")
                )
            )
        )

        assertStatusIs<ChangeSuccess<*>>(changeResponse.statuses[0])

        val scanResponseForLatest = dataStore.execute(
            CompleteMarykModel.scan(
                where = Equals(
                    CompleteMarykModel.string.ref() with "haas"
                )
            )
        )

        expect(0) { scanResponseForLatest.values.size }
        assertTrue { scanResponseForLatest.dataFetchType is FetchByUniqueKey }
        expect(FetchByUniqueKey(byteArrayOf(9))) { scanResponseForLatest.dataFetchType }

        // Only test if all versions are kept
        if (dataStore.keepAllVersions) {
            val scanResponseBeforeChange = dataStore.execute(
                CompleteMarykModel.scan(
                    where = Equals(
                        CompleteMarykModel.string.ref() with "haas"
                    ),
                    toVersion = lowestVersion
                )
            )

            expect(1) { scanResponseBeforeChange.values.size }

            scanResponseBeforeChange.values[0].let {
                expect(objects[0]) { it.values }
                expect(keys[0]) { it.key }
            }
        }
    }

    private suspend fun addSecondPrefixMatch() {
        val secondObject = objects[0].change(listOf(Change(
            CompleteMarykModel.string.ref() with "haas2",
            CompleteMarykModel.number.ref() with 25u,
            CompleteMarykModel.enum.ref() with null,
            CompleteMarykModel.date.ref() with null,
            CompleteMarykModel.dateTime.ref() with null,
            CompleteMarykModel.time.ref() with null,
            CompleteMarykModel.fixedBytes.ref() with null,
            CompleteMarykModel.flexBytes.ref() with null,
            CompleteMarykModel.reference.ref() with null,
            CompleteMarykModel.dateForKey.ref() with LocalDate(2018, 3, 30)
        )))
        val status = assertStatusIs<AddSuccess<CompleteMarykModel>>(
            dataStore.execute(CompleteMarykModel.add(secondObject)).statuses.single()
        )
        keys.add(status.key)
    }

    private suspend fun executeUniquePrefixReturnsAllMatches() {
        addSecondPrefixMatch()
        val response = dataStore.execute(CompleteMarykModel.scan(
            where = Prefix(CompleteMarykModel.string.ref() with "haa"),
            allowTableScan = true
        ))
        expect(keys.toSet()) { response.values.map { it.key }.toSet() }
    }

    private suspend fun executeUniqueValueInReturnsAllMatches() {
        addSecondPrefixMatch()
        val response = dataStore.execute(CompleteMarykModel.scan(
            where = ValueIn(CompleteMarykModel.string.ref() with linkedSetOf("haas", "haas2")),
            allowTableScan = true
        ))
        expect(keys.toSet()) { response.values.map { it.key }.toSet() }
    }

    private suspend fun executeUniqueValueInWithAbsentFirstValue() {
        val response = dataStore.execute(CompleteMarykModel.scan(
            where = ValueIn(CompleteMarykModel.string.ref() with linkedSetOf("absent", "haas")),
            allowTableScan = true
        ))
        expect(keys.toSet()) { response.values.map { it.key }.toSet() }
    }

    private suspend fun executeHistoricalUniqueDoesNotMatchPrefixCollision() {
        if (!dataStore.keepAllVersions) return

        val scanResponse = dataStore.execute(
            CompleteMarykModel.scan(
                where = Equals(
                    CompleteMarykModel.string.ref() with "haa"
                ),
                toVersion = lowestVersion
            )
        )

        expect(0) { scanResponse.values.size }
        assertTrue { scanResponse.dataFetchType is FetchByUniqueKey }
        expect(FetchByUniqueKey(byteArrayOf(9))) { scanResponse.dataFetchType }
    }

    private suspend fun executeHistoricalUniqueCanIncludeSoftDeletedObject() {
        if (!dataStore.keepAllVersions) return

        val deleteResponse = dataStore.execute(
            CompleteMarykModel.delete(keys[0], hardDelete = false)
        )
        val deleteVersion = assertStatusIs<DeleteSuccess<CompleteMarykModel>>(deleteResponse.statuses.single()).version

        val scanResponse = dataStore.execute(
            CompleteMarykModel.scan(
                where = Equals(
                    CompleteMarykModel.string.ref() with "haas"
                ),
                toVersion = deleteVersion,
                filterSoftDeleted = false
            )
        )

        expect(1) { scanResponse.values.size }
        assertTrue { scanResponse.values.single().isDeleted }
        expect(keys[0]) { scanResponse.values.single().key }
        expect(FetchByUniqueKey(byteArrayOf(9))) { scanResponse.dataFetchType }
    }

    private suspend fun executeHistoricalUniqueCanIncludeObjectSoftDeletedByChange() {
        if (!dataStore.keepAllVersions) return

        val changeResponse = dataStore.execute(
            CompleteMarykModel.change(
                keys[0].change(ObjectSoftDeleteChange(true))
            )
        )
        val deleteVersion = assertStatusIs<ChangeSuccess<CompleteMarykModel>>(changeResponse.statuses.single()).version

        val filteredScanResponse = dataStore.execute(
            CompleteMarykModel.scan(
                where = Equals(
                    CompleteMarykModel.string.ref() with "haas"
                ),
                toVersion = deleteVersion
            )
        )

        expect(0) { filteredScanResponse.values.size }

        val scanResponse = dataStore.execute(
            CompleteMarykModel.scan(
                where = Equals(
                    CompleteMarykModel.string.ref() with "haas"
                ),
                toVersion = deleteVersion,
                filterSoftDeleted = false
            )
        )

        expect(1) { scanResponse.values.size }
        assertTrue { scanResponse.values.single().isDeleted }
        expect(keys[0]) { scanResponse.values.single().key }
        expect(FetchByUniqueKey(byteArrayOf(9))) { scanResponse.dataFetchType }
    }
}
