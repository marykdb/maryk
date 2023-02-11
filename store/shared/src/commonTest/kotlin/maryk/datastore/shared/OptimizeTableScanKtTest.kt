package maryk.datastore.shared

import kotlinx.datetime.LocalDateTime
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.index.Reversed
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
            TestMarykModel.Model.optimizeTableScan(tableScan, listOf())
        }
    }

    @Test
    fun optimizeTableScanInt() {
        expect(
            IndexScan(
                TestMarykModel.int.ref(),
                ASC
            )
        ) {
            TestMarykModel.Model.optimizeTableScan(
                tableScan,
                listOf(
                    TestMarykModel.enum.ref() with V3,
                    TestMarykModel.int.ref() with 245
                )
            )
        }
    }

    @Test
    fun optimizeTableScanDoubleReversed() {
        expect(
            IndexScan(Reversed(TestMarykModel.double.ref()), ASC)
        ) {
            TestMarykModel.Model.optimizeTableScan(
                tableScan,
                listOf(
                    TestMarykModel.double.ref() with 2.5
                )
            )
        }
    }

    @Test
    fun optimizeTableScanMultiple() {
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
            TestMarykModel.Model.optimizeTableScan(
                tableScan,
                listOf(
                    TestMarykModel.dateTime.ref() with LocalDateTime(2018, 3, 3, 0, 0),
                    TestMarykModel.enum.ref() with V3,
                    TestMarykModel.int.ref() with 245
                )
            )
        }
    }
}
