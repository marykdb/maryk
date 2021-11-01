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
import maryk.test.models.TestMarykModel.Properties.dateTime
import maryk.test.models.TestMarykModel.Properties.double
import maryk.test.models.TestMarykModel.Properties.enum
import maryk.test.models.TestMarykModel.Properties.int
import kotlin.test.Test
import kotlin.test.expect

class OptimizeTableScanKtTest {
    private val tableScan = TableScan()

    @Test
    fun optimizeTableScanNoMatch() {
        expect(tableScan) {
            TestMarykModel.optimizeTableScan(tableScan, listOf())
        }
    }

    @Test
    fun optimizeTableScanInt() {
        expect(
            IndexScan(
                int.ref(),
                ASC
            )
        ) {
            TestMarykModel.optimizeTableScan(
                tableScan,
                listOf(
                    enum.ref() with V3,
                    int.ref() with 245
                )
            )
        }
    }

    @Test
    fun optimizeTableScanDoubleReversed() {
        expect(
            IndexScan(Reversed(double.ref()), ASC)
        ) {
            TestMarykModel.optimizeTableScan(
                tableScan,
                listOf(
                    double.ref() with 2.5
                )
            )
        }
    }

    @Test
    fun optimizeTableScanMultiple() {
        expect(
            IndexScan(
                Multiple(
                    Reversed(dateTime.ref()),
                    enum.ref(),
                    int.ref()
                ),
                ASC
            )
        ) {
            TestMarykModel.optimizeTableScan(
                tableScan,
                listOf(
                    dateTime.ref() with LocalDateTime(2018, 3, 3, 0, 0),
                    enum.ref() with V3,
                    int.ref() with 245
                )
            )
        }
    }
}
