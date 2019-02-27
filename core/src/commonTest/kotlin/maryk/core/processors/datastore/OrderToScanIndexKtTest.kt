package maryk.core.processors.datastore

import maryk.core.processors.datastore.ScanType.IndexScan
import maryk.core.processors.datastore.ScanType.TableScan
import maryk.core.properties.definitions.index.Reversed
import maryk.core.query.orders.Direction.ASC
import maryk.core.query.orders.Direction.DESC
import maryk.core.query.orders.Order.Companion.ascending
import maryk.core.query.orders.Order.Companion.descending
import maryk.core.query.orders.ascending
import maryk.core.query.orders.descending
import maryk.test.models.TestMarykModel
import maryk.test.models.TestMarykModel.Properties.double
import maryk.test.models.TestMarykModel.Properties.int
import maryk.test.shouldBe
import kotlin.test.Test

class OrderToScanIndexKtTest {
    @Test
    fun defaultOrderToTableScan() {
        TestMarykModel.orderToScanType(ascending) shouldBe TableScan(direction = ASC)
    }

    @Test
    fun descendingOrderToDescendingTableScan() {
        TestMarykModel.orderToScanType(descending) shouldBe TableScan(direction = DESC)
    }

    @Test
    fun ascendingIntOrderToIndexScan() {
        TestMarykModel.orderToScanType(
            int.ref().ascending()
        ) shouldBe IndexScan(
            int.ref(),
            direction = ASC
        )
    }

    @Test
    fun descendingIntOrderToIndexScan() {
        TestMarykModel.orderToScanType(
            int.ref().descending()
        ) shouldBe IndexScan(
            int.ref(),
            direction = DESC
        )
    }

    @Test
    fun ascendingDoubleOrderToIndexScan() {
        TestMarykModel.orderToScanType(
            double.ref().ascending()
        ) shouldBe IndexScan(
            Reversed(double.ref()),
            direction = DESC
        )
    }

    @Test
    fun descendingDoubleOrderToIndexScan() {
        TestMarykModel.orderToScanType(
            double.ref().descending()
        ) shouldBe IndexScan(
            Reversed(double.ref()),
            direction = ASC
        )
    }
}
