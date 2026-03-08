package maryk.datastore.test

import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.properties.types.Key
import maryk.core.query.filters.And
import maryk.core.query.filters.Equals
import maryk.core.query.filters.Matches
import maryk.core.query.filters.MatchesPrefix
import maryk.core.query.filters.MatchesRegEx
import maryk.core.query.filters.Prefix
import maryk.core.query.filters.RegEx
import maryk.core.query.orders.Direction
import maryk.core.query.orders.Orders
import maryk.core.query.orders.ascending
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.scan
import maryk.core.query.responses.FetchByIndexScan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.datastore.shared.IsDataStore
import maryk.test.models.CaseInsensitivePerson
import kotlin.test.expect

class DataStoreScanOnNormalizeIndexTest(
    val dataStore: IsDataStore
) : IsDataStoreTest {
    private val keys = mutableListOf<Key<CaseInsensitivePerson>>()
    private var initialVersion: ULong? = null

    override val allTests = mapOf(
        "executeIndexScanRequestOnNormalizeIndex" to ::executeIndexScanRequestOnNormalizeIndex,
        "executeIndexEqualsScanRequestOnNormalizeIndex" to ::executeIndexEqualsScanRequestOnNormalizeIndex,
        "executeIndexPrefixScanRequestOnNormalizeIndex" to ::executeIndexPrefixScanRequestOnNormalizeIndex,
        "executeIndexRegexScanRequestOnNormalizeIndex" to ::executeIndexRegexScanRequestOnNormalizeIndex,
        "executeIndexOnlyNormalizesConfiguredPart" to ::executeIndexOnlyNormalizesConfiguredPart,
        "executeNamedAnyOfMatchesWithoutOrder" to ::executeNamedAnyOfMatchesWithoutOrder,
        "executeNamedAnyOfMultiTermMatchesWithoutOrder" to ::executeNamedAnyOfMultiTermMatchesWithoutOrder,
        "executeNamedAnyOfMatchesPrefixWithoutOrder" to ::executeNamedAnyOfMatchesPrefixWithoutOrder,
        "executeNamedAnyOfMultiTermMatchesPrefixWithoutOrder" to ::executeNamedAnyOfMultiTermMatchesPrefixWithoutOrder,
        "executeNamedAnyOfMultiTermMatchesPrefixWithoutOrderNoFalsePositive" to ::executeNamedAnyOfMultiTermMatchesPrefixWithoutOrderNoFalsePositive,
        "executeNamedAnyOfMultiTermMatchesPrefixWithAndWithoutOrder" to ::executeNamedAnyOfMultiTermMatchesPrefixWithAndWithoutOrder,
        "executeNamedAnyOfMultiTermMatchesWithoutOrderToVersion" to ::executeNamedAnyOfMultiTermMatchesWithoutOrderToVersion,
        "executeNamedAnyOfMultiTermMatchesPrefixWithoutOrderToVersion" to ::executeNamedAnyOfMultiTermMatchesPrefixWithoutOrderToVersion,
        "executeNamedAnyOfMultiTermMatchesPrefixWithAndWithoutOrderToVersion" to ::executeNamedAnyOfMultiTermMatchesPrefixWithAndWithoutOrderToVersion,
        "executeNamedAnyOfMultiTermMatchesPrefixWithoutOrderToVersionSkipsDeletedSiblingToken" to ::executeNamedAnyOfMultiTermMatchesPrefixWithoutOrderToVersionSkipsDeletedSiblingToken,
        "executeNamedAnyOfMatchesRegexWithoutOrder" to ::executeNamedAnyOfMatchesRegexWithoutOrder,
        "executePropertyEqualsDoesNotUseNamedAnyOfSearch" to ::executePropertyEqualsDoesNotUseNamedAnyOfSearch,
    )

    private val persons = arrayOf(
        CaseInsensitivePerson.create {
            firstName with "Jurriaan"
            surname with "Mous"
        },
        CaseInsensitivePerson.create {
            firstName with "Karel"
            surname with "Kastens"
        },
        CaseInsensitivePerson.create {
            firstName with "Ariël"
            surname with "kAstens"
        },
        CaseInsensitivePerson.create {
            firstName with "Ti"
            surname with "Tockle"
        },
        CaseInsensitivePerson.create {
            firstName with "José"
            surname with "García-López"
        },
        CaseInsensitivePerson.create {
            firstName with "Marie"
            surname with "van   der  Waals"
        },
        CaseInsensitivePerson.create {
            firstName with "Mila"
            surname with "Verhoeven"
        },
        CaseInsensitivePerson.create {
            firstName with "Mila"
            surname with "Rijnders"
        },
    )

    override suspend fun initData() {
        val addResponse = dataStore.execute(
            CaseInsensitivePerson.add(*persons)
        )
        addResponse.statuses.forEach { status ->
            assertStatusIs<AddSuccess<CaseInsensitivePerson>>(status).also {
                keys.add(it.key)
                if (initialVersion == null) {
                    initialVersion = it.version
                }
            }
        }
    }

    override suspend fun resetData() {
        dataStore.execute(
            CaseInsensitivePerson.delete(*keys.toTypedArray(), hardDelete = true)
        ).statuses.forEach {
            assertStatusIs<DeleteSuccess<*>>(it)
        }
        keys.clear()
        initialVersion = null
    }

    private suspend fun executeIndexScanRequestOnNormalizeIndex() {
        val scanResponse = dataStore.execute(
            CaseInsensitivePerson.scan(
                order = Orders(
                    CaseInsensitivePerson { surname::ref }.ascending(),
                    CaseInsensitivePerson { firstName::ref }.ascending()
                )
            )
        )

        expect(8) { scanResponse.values.size }
        expect(FetchByIndexScan(
            direction = Direction.ASC,
            index = CaseInsensitivePerson.Meta.indexes!![0].referenceStorageByteArray.bytes,
            startKey = byteArrayOf(),
            stopKey = byteArrayOf(),
        )) { scanResponse.dataFetchType }

        scanResponse.values[0].let {
            expect(persons[4]) { it.values }
            expect(keys[4]) { it.key }
        }
        scanResponse.values[1].let {
            expect(persons[2]) { it.values }
            expect(keys[2]) { it.key }
        }
        scanResponse.values[2].let {
            expect(persons[1]) { it.values }
            expect(keys[1]) { it.key }
        }
        scanResponse.values[3].let {
            expect(persons[0]) { it.values }
            expect(keys[0]) { it.key }
        }
        scanResponse.values[4].let {
            expect(persons[7]) { it.values }
            expect(keys[7]) { it.key }
        }
        scanResponse.values[5].let {
            expect(persons[3]) { it.values }
            expect(keys[3]) { it.key }
        }
        scanResponse.values[6].let {
            expect(persons[5]) { it.values }
            expect(keys[5]) { it.key }
        }
        scanResponse.values[7].let {
            expect(persons[6]) { it.values }
            expect(keys[6]) { it.key }
        }
    }

    private suspend fun executeIndexEqualsScanRequestOnNormalizeIndex() {
        val scanResponse = dataStore.execute(
            CaseInsensitivePerson.scan(
                where = Equals(
                    CaseInsensitivePerson { surname::ref } with "garcia lopez",
                ),
                order = Orders(
                    CaseInsensitivePerson { surname::ref }.ascending(),
                    CaseInsensitivePerson { firstName::ref }.ascending()
                ),
            )
        )

        expect(1) { scanResponse.values.size }
        expect(persons[4]) { scanResponse.values.first().values }
        expect(FetchByIndexScan(
            direction = Direction.ASC,
            index = CaseInsensitivePerson.Meta.indexes!![0].referenceStorageByteArray.bytes,
            startKey = "garcialopez".encodeToByteArray(),
            stopKey = "garcialope{".encodeToByteArray(),
        )) { scanResponse.dataFetchType }
    }

    private suspend fun executeIndexPrefixScanRequestOnNormalizeIndex() {
        val scanResponse = dataStore.execute(
            CaseInsensitivePerson.scan(
                where = Prefix(
                    CaseInsensitivePerson { surname::ref } with "van der",
                ),
                order = Orders(
                    CaseInsensitivePerson { surname::ref }.ascending(),
                    CaseInsensitivePerson { firstName::ref }.ascending()
                ),
            )
        )

        expect(1) { scanResponse.values.size }
        expect(persons[5]) { scanResponse.values.first().values }
        expect(FetchByIndexScan(
            direction = Direction.ASC,
            index = CaseInsensitivePerson.Meta.indexes!![0].referenceStorageByteArray.bytes,
            startKey = "vander".encodeToByteArray(),
            stopKey = "vandes".encodeToByteArray(),
        )) { scanResponse.dataFetchType }
    }

    private suspend fun executeIndexRegexScanRequestOnNormalizeIndex() {
        val scanResponse = dataStore.execute(
            CaseInsensitivePerson.scan(
                where = RegEx(
                    CaseInsensitivePerson { surname::ref } with Regex("^garcia.*$")
                ),
                order = Orders(
                    CaseInsensitivePerson { surname::ref }.ascending(),
                    CaseInsensitivePerson { firstName::ref }.ascending()
                ),
            )
        )

        expect(1) { scanResponse.values.size }
        expect(persons[4]) { scanResponse.values.first().values }
    }

    private suspend fun executeIndexOnlyNormalizesConfiguredPart() {
        val normalizedSurname = dataStore.execute(
            CaseInsensitivePerson.scan(
                where = Equals(
                    CaseInsensitivePerson { surname::ref } with "garcia lopez",
                    CaseInsensitivePerson { firstName::ref } with "José",
                ),
                order = Orders(
                    CaseInsensitivePerson { surname::ref }.ascending(),
                    CaseInsensitivePerson { firstName::ref }.ascending()
                ),
            )
        )

        expect(1) { normalizedSurname.values.size }
        expect(persons[4]) { normalizedSurname.values.first().values }

        val wrongFirstNameNormalization = dataStore.execute(
            CaseInsensitivePerson.scan(
                where = Equals(
                    CaseInsensitivePerson { surname::ref } with "garcia lopez",
                    CaseInsensitivePerson { firstName::ref } with "Jose",
                ),
                order = Orders(
                    CaseInsensitivePerson { surname::ref }.ascending(),
                    CaseInsensitivePerson { firstName::ref }.ascending()
                ),
            )
        )

        expect(0) { wrongFirstNameNormalization.values.size }
    }

    private suspend fun executeNamedAnyOfMatchesWithoutOrder() {
        val scanResponse = dataStore.execute(
            CaseInsensitivePerson.scan(
                where = Matches(
                    "name" with "garcia"
                )
            )
        )

        expect(1) { scanResponse.values.size }
        expect(persons[4]) { scanResponse.values.first().values }
        expect(FetchByIndexScan(
            direction = Direction.ASC,
            index = CaseInsensitivePerson.Meta.indexes!![1].referenceStorageByteArray.bytes,
            startKey = "garcia".encodeToByteArray(),
            stopKey = "garcib".encodeToByteArray(),
        )) { scanResponse.dataFetchType }
    }

    private suspend fun executeNamedAnyOfMultiTermMatchesWithoutOrder() {
        val scanResponse = dataStore.execute(
            CaseInsensitivePerson.scan(
                where = Matches(
                    "name" with "van der waals"
                )
            )
        )

        expect(1) { scanResponse.values.size }
        expect(persons[5]) { scanResponse.values.first().values }
        expect(FetchByIndexScan(
            direction = Direction.ASC,
            index = CaseInsensitivePerson.Meta.indexes!![1].referenceStorageByteArray.bytes,
            startKey = "van".encodeToByteArray(),
            stopKey = "vao".encodeToByteArray(),
        )) { scanResponse.dataFetchType }
    }

    private suspend fun executeNamedAnyOfMatchesPrefixWithoutOrder() {
        val scanResponse = dataStore.execute(
            CaseInsensitivePerson.scan(
                where = MatchesPrefix(
                    "name" with "gar"
                )
            )
        )

        expect(1) { scanResponse.values.size }
        expect(persons[4]) { scanResponse.values.first().values }
        expect(FetchByIndexScan(
            direction = Direction.ASC,
            index = CaseInsensitivePerson.Meta.indexes!![1].referenceStorageByteArray.bytes,
            startKey = "gar".encodeToByteArray(),
            stopKey = "gas".encodeToByteArray(),
        )) { scanResponse.dataFetchType }
    }

    private suspend fun executeNamedAnyOfMultiTermMatchesPrefixWithoutOrder() {
        val scanResponse = dataStore.execute(
            CaseInsensitivePerson.scan(
                where = MatchesPrefix(
                    "name" with "mila verh"
                )
            )
        )

        expect(1) { scanResponse.values.size }
        expect(persons[6]) { scanResponse.values.first().values }
        expect(FetchByIndexScan(
            direction = Direction.ASC,
            index = CaseInsensitivePerson.Meta.indexes!![1].referenceStorageByteArray.bytes,
            startKey = "mila".encodeToByteArray(),
            stopKey = "milb".encodeToByteArray(),
        )) { scanResponse.dataFetchType }
    }

    private suspend fun executeNamedAnyOfMultiTermMatchesPrefixWithoutOrderNoFalsePositive() {
        val scanResponse = dataStore.execute(
            CaseInsensitivePerson.scan(
                where = MatchesPrefix(
                    "name" with "mila rij verh"
                )
            )
        )

        expect(0) { scanResponse.values.size }
        expect(FetchByIndexScan(
            direction = Direction.ASC,
            index = CaseInsensitivePerson.Meta.indexes!![1].referenceStorageByteArray.bytes,
            startKey = "mila".encodeToByteArray(),
            stopKey = "milb".encodeToByteArray(),
        )) { scanResponse.dataFetchType }
    }

    private suspend fun executeNamedAnyOfMultiTermMatchesPrefixWithAndWithoutOrder() {
        val scanResponse = dataStore.execute(
            CaseInsensitivePerson.scan(
                where = And(
                    MatchesPrefix(
                        "name" with "mila verh"
                    ),
                    Equals(
                        CaseInsensitivePerson { firstName::ref } with "Mila"
                    )
                )
            )
        )

        expect(1) { scanResponse.values.size }
        expect(persons[6]) { scanResponse.values.first().values }
        expect(FetchByIndexScan(
            direction = Direction.ASC,
            index = CaseInsensitivePerson.Meta.indexes!![1].referenceStorageByteArray.bytes,
            startKey = "mila".encodeToByteArray(),
            stopKey = "milb".encodeToByteArray(),
        )) { scanResponse.dataFetchType }
    }

    private suspend fun executeNamedAnyOfMultiTermMatchesWithoutOrderToVersion() {
        if (!dataStore.keepAllVersions) return

        val version = initialVersion ?: error("Missing initial version")
        val scanResponse = dataStore.execute(
            CaseInsensitivePerson.scan(
                where = Matches(
                    "name" with "van der waals"
                ),
                toVersion = version
            )
        )

        expect(1) { scanResponse.values.size }
        expect(persons[5]) { scanResponse.values.first().values }
        expect(FetchByIndexScan(
            direction = Direction.ASC,
            index = CaseInsensitivePerson.Meta.indexes!![1].referenceStorageByteArray.bytes,
            startKey = "van".encodeToByteArray(),
            stopKey = "vao".encodeToByteArray(),
        )) { scanResponse.dataFetchType }
    }

    private suspend fun executeNamedAnyOfMultiTermMatchesPrefixWithoutOrderToVersion() {
        if (!dataStore.keepAllVersions) return

        val version = initialVersion ?: error("Missing initial version")
        val scanResponse = dataStore.execute(
            CaseInsensitivePerson.scan(
                where = MatchesPrefix(
                    "name" with "mila verh"
                ),
                toVersion = version
            )
        )

        expect(1) { scanResponse.values.size }
        expect(persons[6]) { scanResponse.values.first().values }
        expect(FetchByIndexScan(
            direction = Direction.ASC,
            index = CaseInsensitivePerson.Meta.indexes!![1].referenceStorageByteArray.bytes,
            startKey = "mila".encodeToByteArray(),
            stopKey = "milb".encodeToByteArray(),
        )) { scanResponse.dataFetchType }
    }

    private suspend fun executeNamedAnyOfMultiTermMatchesPrefixWithAndWithoutOrderToVersion() {
        if (!dataStore.keepAllVersions) return

        val version = initialVersion ?: error("Missing initial version")
        val scanResponse = dataStore.execute(
            CaseInsensitivePerson.scan(
                where = And(
                    MatchesPrefix(
                        "name" with "mila verh"
                    ),
                    Equals(
                        CaseInsensitivePerson { firstName::ref } with "Mila"
                    )
                ),
                toVersion = version
            )
        )

        expect(1) { scanResponse.values.size }
        expect(persons[6]) { scanResponse.values.first().values }
        expect(FetchByIndexScan(
            direction = Direction.ASC,
            index = CaseInsensitivePerson.Meta.indexes!![1].referenceStorageByteArray.bytes,
            startKey = "mila".encodeToByteArray(),
            stopKey = "milb".encodeToByteArray(),
        )) { scanResponse.dataFetchType }
    }

    private suspend fun executeNamedAnyOfMultiTermMatchesPrefixWithoutOrderToVersionSkipsDeletedSiblingToken() {
        if (!dataStore.keepAllVersions) return

        dataStore.execute(
            CaseInsensitivePerson.change(
                keys[6].change(
                    Change(
                        CaseInsensitivePerson { surname::ref } with "Verhoeven Vermeer"
                    )
                )
            )
        ).statuses.first().also {
            assertStatusIs<ChangeSuccess<CaseInsensitivePerson>>(it)
        }

        val latestVersion = dataStore.execute(
            CaseInsensitivePerson.change(
                keys[6].change(
                    Change(
                        CaseInsensitivePerson { surname::ref } with "Vermeer"
                    )
                )
            )
        ).statuses.first().let {
            assertStatusIs<ChangeSuccess<CaseInsensitivePerson>>(it).version
        }

        val scanResponse = dataStore.execute(
            CaseInsensitivePerson.scan(
                where = MatchesPrefix(
                    "name" with "mila ver"
                ),
                toVersion = latestVersion
            )
        )

        expect(1) { scanResponse.values.size }
        expect(
            CaseInsensitivePerson.create {
                firstName with "Mila"
                surname with "Vermeer"
            }
        ) { scanResponse.values.first().values }
        expect(keys[6]) { scanResponse.values.first().key }
        expect(FetchByIndexScan(
            direction = Direction.ASC,
            index = CaseInsensitivePerson.Meta.indexes!![1].referenceStorageByteArray.bytes,
            startKey = "mila".encodeToByteArray(),
            stopKey = "milb".encodeToByteArray(),
        )) { scanResponse.dataFetchType }
    }

    private suspend fun executeNamedAnyOfMatchesRegexWithoutOrder() {
        val scanResponse = dataStore.execute(
            CaseInsensitivePerson.scan(
                where = MatchesRegEx(
                    "name" with Regex("^gar.*$")
                )
            )
        )

        expect(1) { scanResponse.values.size }
        expect(persons[4]) { scanResponse.values.first().values }
    }

    private suspend fun executePropertyEqualsDoesNotUseNamedAnyOfSearch() {
        val scanResponse = dataStore.execute(
            CaseInsensitivePerson.scan(
                where = Equals(
                    CaseInsensitivePerson { surname::ref } with "garcia",
                ),
                allowTableScan = true
            )
        )

        expect(0) { scanResponse.values.size }
    }
}
