package maryk.datastore.test

import kotlinx.datetime.LocalDateTime
import maryk.core.exceptions.RequestException
import maryk.core.models.graph
import maryk.core.properties.types.Key
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.filters.Equals
import maryk.core.query.filters.GreaterThanEquals
import maryk.core.query.filters.LessThanEquals
import maryk.core.query.orders.Direction
import maryk.core.query.orders.ascending
import maryk.core.query.orders.descending
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.scan
import maryk.core.query.responses.FetchByIndexScan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.datastore.shared.IsDataStore
import maryk.test.models.Log
import maryk.test.models.Log.message
import maryk.test.models.Log.severity
import maryk.test.models.Severity.DEBUG
import maryk.test.models.Severity.ERROR
import maryk.test.models.Severity.INFO
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.expect

class DataStoreScanOnIndexTest(
    val dataStore: IsDataStore
) : IsDataStoreTest {
    private val keys = mutableListOf<Key<Log>>()
    private var highestCreationVersion = ULong.MIN_VALUE

    override val allTests = mapOf(
        "executeSimpleIndexScanRequest" to ::executeSimpleIndexScanRequest,
        "executeSimpleIndexScanWithStartKeyRequest" to ::executeSimpleIndexScanWithStartKeyRequest,
        "executeSimpleIndexScanRequestReverseOrder" to ::executeSimpleIndexScanRequestReverseOrder,
        "executeIndexScanRequestWithLimit" to ::executeIndexScanRequestWithLimit,
        "executeIndexScanRequestWithToVersionAscending" to ::executeIndexScanRequestWithToVersionAscending,
        "executeIndexScanRequestWithToVersionDescending" to ::executeIndexScanRequestWithToVersionDescending,
        "executeIndexScanRequestWithSelect" to ::executeIndexScanRequestWithSelect,
        "executeSimpleIndexFilterScanRequest" to ::executeSimpleIndexFilterScanRequest,
        "executeSimpleIndexFilterGreaterScanRequest" to ::executeSimpleIndexFilterGreaterScanRequest,
        "executeSimpleIndexFilterLessScanRequest" to ::executeSimpleIndexFilterLessScanRequest
    )

    private val logs = arrayOf(
        Log("Something happened", INFO, LocalDateTime(2018, 11, 14, 11, 22, 33, 40000000)),
        Log("Something else happened", DEBUG, LocalDateTime(2018, 11, 14, 12, 0, 0, 0)),
        Log("Something REALLY happened", INFO, LocalDateTime(2018, 11, 14, 12, 33, 22, 111000000)),
        Log("WRONG", ERROR, LocalDateTime(2018, 11, 14, 13, 0, 2, 0))
    )

    override suspend fun initData() {
        val addResponse = dataStore.execute(
            Log.add(*logs)
        )
        addResponse.statuses.forEach { status ->
            val response = assertStatusIs<AddSuccess<Log>>(status)
            keys.add(response.key)
            if (response.version > highestCreationVersion) {
                // Add lowest version for scan test
                highestCreationVersion = response.version
            }
        }
    }

    override suspend fun resetData() {
        dataStore.execute(
            Log.delete(*keys.toTypedArray(), hardDelete = true)
        ).statuses.forEach {
            assertStatusIs<DeleteSuccess<*>>(it)
        }
        keys.clear()
        highestCreationVersion = ULong.MIN_VALUE
    }

    private suspend fun executeSimpleIndexScanRequest() {
        val scanResponse = dataStore.execute(
            Log.scan(order = severity.ref().ascending())
        )

        expect(4) { scanResponse.values.size }
        expect(FetchByIndexScan(
            direction = Direction.ASC,
            index = byteArrayOf(10, 17),
            startKey = byteArrayOf(),
            stopKey = byteArrayOf(),
        )) { scanResponse.dataFetchType }

        // Sorted on severity
        scanResponse.values[0].let {
            expect(logs[2]) { it.values }
            expect(keys[2]) { it.key }
        }
        scanResponse.values[1].let {
            expect(logs[0]) { it.values }
            expect(keys[0]) { it.key }
        }
        scanResponse.values[2].let {
            expect(logs[1]) { it.values }
            expect(keys[1]) { it.key }
        }
        scanResponse.values[3].let {
            expect(logs[3]) { it.values }
            expect(keys[3]) { it.key }
        }
    }

    private suspend fun executeSimpleIndexScanWithStartKeyRequest() {
        val scanResponse = dataStore.execute(
            Log.scan(startKey = keys[1], order = severity.ref().ascending())
        )

        expect(2) { scanResponse.values.size }
        expect(FetchByIndexScan(
            direction = Direction.ASC,
            index = byteArrayOf(10, 17),
            startKey = byteArrayOf(0, 2, 2, *keys[1].bytes),
            stopKey = byteArrayOf(),
        )) { scanResponse.dataFetchType }

        // Sorted on severity
        scanResponse.values[0].let {
            expect(logs[1]) { it.values }
            expect(keys[1]) { it.key }
        }
        scanResponse.values[1].let {
            expect(logs[3]) { it.values }
            expect(keys[3]) { it.key }
        }
    }

    private suspend fun executeSimpleIndexScanRequestReverseOrder() {
        val scanResponse = dataStore.execute(
            Log.scan(order = severity.ref().descending())
        )

        expect(4) { scanResponse.values.size }
        expect(FetchByIndexScan(
            direction = Direction.DESC,
            index = byteArrayOf(10, 17),
            startKey = byteArrayOf(),
            stopKey = byteArrayOf(),
        )) { scanResponse.dataFetchType }


        // Sorted on severity
        scanResponse.values[0].let {
            expect(logs[3]) { it.values }
            expect(keys[3]) { it.key }
        }
        scanResponse.values[1].let {
            expect(logs[1]) { it.values }
            expect(keys[1]) { it.key }
        }
        scanResponse.values[2].let {
            expect(logs[0]) { it.values }
            expect(keys[0]) { it.key }
        }
        scanResponse.values[3].let {
            expect(logs[2]) { it.values }
            expect(keys[2]) { it.key }
        }
    }

    private suspend fun executeIndexScanRequestWithLimit() {
        val scanResponse = dataStore.execute(
            Log.scan(limit = 1u, order = severity.ref().ascending())
        )

        expect(1) { scanResponse.values.size }
        expect(FetchByIndexScan(
            direction = Direction.ASC,
            index = byteArrayOf(10, 17),
            startKey = byteArrayOf(),
            stopKey = byteArrayOf(),
        )) { scanResponse.dataFetchType }


        scanResponse.values[0].let {
            expect(logs[2]) { it.values }
            expect(keys[2]) { it.key }
        }
    }

    private suspend fun executeIndexScanRequestWithToVersionAscending() {
        dataStore.execute(
            Log.change(
                keys[0].change(
                    Change(
                        message.ref() with "new message"
                    )
                )
            )
        )

        val scan = Log.scan(toVersion = highestCreationVersion, order = severity.ref().ascending())

        if (dataStore.keepAllVersions) {
            val scanResponse = dataStore.execute(scan)

            expect(4) { scanResponse.values.size }
            expect(FetchByIndexScan(
                direction = Direction.ASC,
                index = byteArrayOf(10, 17),
                startKey = byteArrayOf(),
                stopKey = byteArrayOf(),
            )) { scanResponse.dataFetchType }


            // Find the values of object that was changed
            val value = scanResponse.values.find { it.key == keys[0] }

            assertSame(value, scanResponse.values[1])

            assertEquals("Something happened", value!!.values[message.ref()])
        } else {
            assertFailsWith<RequestException> {
                dataStore.execute(scan)
            }
        }
    }

    private suspend fun executeIndexScanRequestWithToVersionDescending() {
        dataStore.execute(
            Log.change(
                keys[0].change(
                    Change(
                        message.ref() with "new message"
                    )
                )
            )
        )

        val scan = Log.scan(toVersion = highestCreationVersion, order = severity.ref().descending())

        if (dataStore.keepAllVersions) {
            val scanResponse = dataStore.execute(scan)

            expect(4) { scanResponse.values.size }
            expect(FetchByIndexScan(
                direction = Direction.DESC,
                index = byteArrayOf(10, 17),
                startKey = byteArrayOf(),
                stopKey = byteArrayOf(),
            )) { scanResponse.dataFetchType }

            // Find the values of object that was changed
            val value = scanResponse.values.find { it.key == keys[0] }

            assertSame(value, scanResponse.values[2])

            assertEquals("Something happened", value!!.values[message.ref()])
        } else {
            assertFailsWith<RequestException> {
                dataStore.execute(scan)
            }
        }
    }

    private suspend fun executeIndexScanRequestWithSelect() {
        val scanResponse = dataStore.execute(
            Log.scan(
                select = Log.graph {
                    listOf(
                        timestamp,
                        severity
                    )
                },
                order = severity.ref().ascending()
            )
        )

        expect(4) { scanResponse.values.size }
        expect(FetchByIndexScan(
            direction = Direction.ASC,
            index = byteArrayOf(10, 17),
            startKey = byteArrayOf(),
            stopKey = byteArrayOf(),
        )) { scanResponse.dataFetchType }


        // Mind that Log is sorted in reverse, so it goes back in time going forward
        scanResponse.values[0].let {
            expect(
                Log.run {
                    create(
                        this.severity with INFO,
                        this.timestamp with LocalDateTime(2018, 11, 14, 12, 33, 22, 111000000)
                    )
                }
            ) { it.values }
            expect(keys[2]) { it.key }
        }
    }

    private suspend fun executeSimpleIndexFilterScanRequest() {
        val scanResponse = dataStore.execute(
            Log.scan(
                where = Equals(
                    severity.ref() with DEBUG
                ),
                order = severity.ref().ascending()
            )
        )

        expect(1) { scanResponse.values.size }
        expect(FetchByIndexScan(
            direction = Direction.ASC,
            index = byteArrayOf(10, 17),
            startKey = byteArrayOf(0, 2),
            stopKey = byteArrayOf(0, 3),
        )) { scanResponse.dataFetchType }

        scanResponse.values[0].let {
            expect(logs[1]) { it.values }
            expect(keys[1]) { it.key }
        }
    }

    private suspend fun executeSimpleIndexFilterGreaterScanRequest() {
        val scanResponse = dataStore.execute(
            Log.scan(
                where = GreaterThanEquals(
                    severity.ref() with DEBUG
                ),
                order = severity.ref().ascending()
            )
        )

        expect(2) { scanResponse.values.size }
        expect(FetchByIndexScan(
            direction = Direction.ASC,
            index = byteArrayOf(10, 17),
            startKey = byteArrayOf(0, 2),
            stopKey = byteArrayOf(),
        )) { scanResponse.dataFetchType }


        scanResponse.values[0].let {
            expect(logs[1]) { it.values }
            expect(keys[1]) { it.key }
        }
        scanResponse.values[1].let {
            expect(logs[3]) { it.values }
            expect(keys[3]) { it.key }
        }
    }

    private suspend fun executeSimpleIndexFilterLessScanRequest() {
        val scanResponse = dataStore.execute(
            Log.scan(
                where = LessThanEquals(
                    severity.ref() with DEBUG
                ),
                order = severity.ref().descending()
            )
        )

        expect(3) { scanResponse.values.size }
        expect(FetchByIndexScan(
            direction = Direction.DESC,
            index = byteArrayOf(10, 17),
            startKey = byteArrayOf(0, 3),
            stopKey = byteArrayOf(),
        )) { scanResponse.dataFetchType }

        scanResponse.values[0].let {
            expect(logs[1]) { it.values }
            expect(keys[1]) { it.key }
        }
        scanResponse.values[1].let {
            expect(logs[0]) { it.values }
            expect(keys[0]) { it.key }
        }
        scanResponse.values[2].let {
            expect(logs[2]) { it.values }
            expect(keys[2]) { it.key }
        }
    }
}
