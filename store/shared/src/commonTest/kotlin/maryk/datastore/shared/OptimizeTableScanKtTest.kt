package maryk.datastore.shared

import kotlinx.datetime.LocalDateTime
import maryk.core.processors.datastore.scanRange.createScanRange
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.index.Reversed
import maryk.core.query.filters.Equals
import maryk.core.query.orders.Direction.ASC
import maryk.core.query.pairs.with
import maryk.datastore.shared.ScanType.IndexScan
import maryk.datastore.shared.ScanType.TableScan
import maryk.test.models.Option.V3
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class OptimizeTableScanKtTest {
    private val tableScan = TableScan()

    @Test
    fun optimizeTableScanNoMatch() {
        expect(tableScan) {
            val keyScanRanges = TestMarykModel.createScanRange(null, null, false)
            TestMarykModel.optimizeTableScan(tableScan, null, keyScanRanges).first
        }
    }

    @Test
    fun optimizeTableScanInt() {
        val filter = Equals(
            TestMarykModel { enum::ref} with V3,
            TestMarykModel { int::ref} with 245,
        )
        val keyScanRanges = TestMarykModel.createScanRange(
            filter = filter,
            startKey = null
        )

        expect(
            IndexScan(
                TestMarykModel.int.ref(),
                ASC
            )
        ) {
            TestMarykModel.optimizeTableScan(
                tableScan,
                filter,
                keyScanRanges
            ).first
        }
    }

    @Test
    fun optimizeTableScanDoubleReversed() {
        val filter = Equals(
            TestMarykModel { double::ref} with 2.5,
        )
        val keyScanRanges = TestMarykModel.createScanRange(
            filter = filter,
            startKey = null
        )

        expect(
            IndexScan(Reversed(TestMarykModel.double.ref()), ASC)
        ) {
            TestMarykModel.optimizeTableScan(
                tableScan,
                filter,
                keyScanRanges,
            ).first
        }
    }

    @Test
    fun optimizeTableScanMultiple() {
        val filter = Equals(
            TestMarykModel { dateTime::ref} with LocalDateTime(2018, 3, 3, 0, 0),
            TestMarykModel { enum::ref} with V3,
            TestMarykModel { int::ref} with 245,
        )
        val keyScanRanges = TestMarykModel.createScanRange(
            filter = filter,
            startKey = null
        )

        expect(
            IndexScan(
                TestMarykModel.run {
                    Multiple(
                        Reversed(dateTime.ref()),
                        enum.ref(),
                        int.ref()
                    )
                },
                ASC
            )
        ) {
            TestMarykModel.optimizeTableScan(
                tableScan,
                filter,
                keyScanRanges,
            ).first
        }
    }
}
