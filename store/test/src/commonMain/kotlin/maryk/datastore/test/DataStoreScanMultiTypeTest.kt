package maryk.datastore.test

import kotlinx.datetime.LocalDateTime
import maryk.core.models.graph
import maryk.core.properties.types.Key
import maryk.core.properties.types.invoke
import maryk.core.query.filters.Equals
import maryk.core.query.orders.Order.Companion.descending
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.delete
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.datastore.shared.IsDataStore
import maryk.test.models.LengthMeasurement
import maryk.test.models.Measurement
import maryk.test.models.MeasurementType
import maryk.test.models.WeightMeasurement
import kotlin.test.expect

class DataStoreScanMultiTypeTest(
    val dataStore: IsDataStore
) : IsDataStoreTest {
    private val keys = mutableListOf<Key<Measurement>>()
    private var lowestVersion = ULong.MAX_VALUE

    override val allTests = mapOf(
        "executeSimpleScanRequest" to ::executeSimpleScanRequest,
        "executeSimpleScanRequestReverseOrder" to ::executeSimpleScanRequestReverseOrder,
        "executeScanRequestWithLimit" to ::executeScanRequestWithLimit,
        "executeScanRequestWithSelect" to ::executeScanRequestWithSelect,
        "executeSimpleScanFilterRequest" to ::executeSimpleScanFilterRequest,
        "executeScanOnTypeIndexRequest" to ::executeScanOnTypeIndexRequest
    )

    private val measurements = arrayOf(
        Measurement.run { create(
            this.timestamp with LocalDateTime(2023, 11, 14, 11, 22, 33, 40000000),
            this.measurement with MeasurementType.Weight.invoke(WeightMeasurement.run { create(
                weightInKg with 80u,
            ) }),
        ) },
        Measurement.run { create(
            this.timestamp with LocalDateTime(2023, 11, 14, 12, 0, 0, 0),
            this.measurement with MeasurementType.Length(LengthMeasurement.run { create(
                lengthInCm with 181u,
            ) }),
        ) },
        Measurement.run { create(
            this.timestamp with LocalDateTime(2023, 11, 14, 12, 33, 22, 111000000),
            this.measurement with MeasurementType.Weight(WeightMeasurement.run { create(
                weightInKg with 78u,
            ) }),
        ) },
        Measurement.run { create(
            this.timestamp with LocalDateTime(2023, 11, 14, 13, 0, 2, 0),
            this.measurement with MeasurementType.Length(LengthMeasurement.run { create(
                lengthInCm with 180u,
            ) }),
        ) },
        Measurement.run { create(
            this.timestamp with LocalDateTime(2023, 11, 14, 14, 0, 2, 0),
            this.measurement with MeasurementType.Number(220u),
        ) },
        Measurement.run { create(
            this.timestamp with LocalDateTime(2023, 11, 14, 15, 0, 2, 0),
            this.measurement with MeasurementType.Number(231u),
        ) },
    )

    override suspend fun initData() {
        val addResponse = dataStore.execute(
            Measurement.add(*measurements)
        )
        addResponse.statuses.forEach { status ->
            val response = assertStatusIs<AddSuccess<Measurement>>(status)
            keys.add(response.key)
            if (response.version < lowestVersion) {
                // Add lowest version for scan test
                lowestVersion = response.version
            }
        }
    }

    override suspend fun resetData() {
        dataStore.execute(
            Measurement.delete(*keys.toTypedArray(), hardDelete = true)
        ).statuses.forEach {
            assertStatusIs<DeleteSuccess<*>>(it)
        }
        keys.clear()
        lowestVersion = ULong.MAX_VALUE
    }

    private suspend fun executeSimpleScanRequest() {
        val scanResponse = dataStore.execute(
            Measurement.scan(startKey = keys[2])
        )

        expect(3) { scanResponse.values.size }

        // Mind that Measurement is sorted in reverse, so it goes back in time going forward
        scanResponse.values[0].let {
            expect(measurements[2]) { it.values }
            expect(keys[2]) { it.key }
        }
        scanResponse.values[1].let {
            expect(measurements[1]) { it.values }
            expect(keys[1]) { it.key }
        }
        scanResponse.values[2].let {
            expect(measurements[0]) { it.values }
            expect(keys[0]) { it.key }
        }
    }

    private suspend fun executeSimpleScanRequestReverseOrder() {
        val scanResponse = dataStore.execute(
            Measurement.scan(startKey = keys[2], order = descending)
        )

        expect(4) { scanResponse.values.size }

        // Mind that Measurement is sorted in reverse, so it goes back in time going forward
        scanResponse.values[0].let {
            expect(measurements[2]) { it.values }
            expect(keys[2]) { it.key }
        }
        scanResponse.values[1].let {
            expect(measurements[3]) { it.values }
            expect(keys[3]) { it.key }
        }
        scanResponse.values[2].let {
            expect(measurements[4]) { it.values }
            expect(keys[4]) { it.key }
        }
        scanResponse.values[3].let {
            expect(measurements[5]) { it.values }
            expect(keys[5]) { it.key }
        }
    }

    private suspend fun executeScanOnTypeIndexRequest() {
        val scanResponse = dataStore.execute(
            Measurement.scan(
                where = Equals(
                    Measurement { measurement.simpleRefAtType(MeasurementType.Number) } with 220u.toUShort()
                )
            )
        )

        expect(1) { scanResponse.values.size }

        // Mind that Measurement is sorted in reverse, so it goes back in time going forward
        scanResponse.values[0].let {
            expect(measurements[4]) { it.values }
            expect(keys[4]) { it.key }
        }
    }

    private suspend fun executeScanRequestWithLimit() {
        val scanResponse = dataStore.execute(
            Measurement.scan(startKey = keys[2], limit = 1u)
        )

        expect(1) { scanResponse.values.size }

        // Mind that Measurement is sorted in reverse, so it goes back in time going forward
        scanResponse.values[0].let {
            expect(measurements[2]) { it.values }
            expect(keys[2]) { it.key }
        }
    }

    private suspend fun executeScanRequestWithSelect() {
        val scanResponse = dataStore.execute(
            Measurement.scan(
                startKey = keys[2],
                select = Measurement.graph {
                    listOf(measurement)
                }
            )
        )

        expect(3) { scanResponse.values.size }

        // Mind that Log is sorted in reverse, so it goes back in time going forward
        scanResponse.values[0].let {
            expect(
                Measurement.run { create(
                    measurement with MeasurementType.Weight(WeightMeasurement.run { create(
                        weightInKg with 78u,
                    ) }),
                ) }
            ) { it.values }
            expect(keys[2]) { it.key }
        }
    }

    private suspend fun executeSimpleScanFilterRequest() {
        val scanResponse = dataStore.execute(
            Measurement.scan(
                where = Equals(
                    Measurement { measurement::typeRef } with MeasurementType.Weight,
                )
            )
        )

        expect(2) { scanResponse.values.size }

        // Reverse order
        scanResponse.values[0].let {
            expect(measurements[2]) { it.values }
            expect(keys[2]) { it.key }
        }

        scanResponse.values[1].let {
            expect(measurements[0]) { it.values }
            expect(keys[0]) { it.key }
        }
    }
}
