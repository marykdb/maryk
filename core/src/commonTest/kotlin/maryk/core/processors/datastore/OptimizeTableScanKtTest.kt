package maryk.core.processors.datastore

import maryk.core.processors.datastore.ScanType.IndexScan
import maryk.core.processors.datastore.ScanType.TableScan
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.index.Reversed
import maryk.core.query.orders.Direction.ASC
import maryk.core.query.pairs.with
import maryk.lib.time.DateTime
import maryk.test.models.Option.V3
import maryk.test.models.TestMarykModel
import maryk.test.models.TestMarykModel.Properties.dateTime
import maryk.test.models.TestMarykModel.Properties.double
import maryk.test.models.TestMarykModel.Properties.enum
import maryk.test.models.TestMarykModel.Properties.int
import maryk.test.shouldBe
import kotlin.test.Test

class OptimizeTableScanKtTest {
    private val tableScan = TableScan()

    @Test
    fun optimizeTableScanNoMatch() {
        TestMarykModel.optimizeTableScan(tableScan, listOf()) shouldBe tableScan
    }

    @Test
    fun optimizeTableScanInt() {
        TestMarykModel.optimizeTableScan(
            tableScan,
            listOf(
                enum.ref() with V3,
                int.ref() with 245
            )
        ) shouldBe IndexScan(
            int.ref(),
            ASC
        )
    }

    @Test
    fun optimizeTableScanDoubleReversed() {
        TestMarykModel.optimizeTableScan(
            tableScan,
            listOf(
                double.ref() with 2.5
            )
        ) shouldBe IndexScan(Reversed(double.ref()), ASC)
    }

    @Test
    fun optimizeTableScanMultiple() {
        TestMarykModel.optimizeTableScan(
            tableScan,
            listOf(
                dateTime.ref() with DateTime(2018, 3, 3),
                enum.ref() with V3,
                int.ref() with 245
            )
        ) shouldBe IndexScan(
            Multiple(
                Reversed(dateTime.ref()),
                enum.ref(),
                int.ref()
            ),
            ASC
        )
    }
}
