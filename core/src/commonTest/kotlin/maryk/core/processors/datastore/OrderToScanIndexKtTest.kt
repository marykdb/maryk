package maryk.core.processors.datastore

import maryk.core.exceptions.RequestException
import maryk.core.processors.datastore.ScanType.IndexScan
import maryk.core.processors.datastore.ScanType.TableScan
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.index.Reversed
import maryk.core.query.orders.Direction.ASC
import maryk.core.query.orders.Direction.DESC
import maryk.core.query.orders.Order.Companion.ascending
import maryk.core.query.orders.Order.Companion.descending
import maryk.core.query.orders.Orders
import maryk.core.query.orders.ascending
import maryk.core.query.orders.descending
import maryk.test.models.TestMarykModel
import maryk.test.models.TestMarykModel.Properties.bool
import maryk.test.models.TestMarykModel.Properties.dateTime
import maryk.test.models.TestMarykModel.Properties.double
import maryk.test.models.TestMarykModel.Properties.enum
import maryk.test.models.TestMarykModel.Properties.int
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

class OrderToScanIndexKtTest {
    val multipleIndex = Multiple(
        Reversed(dateTime.ref()),
        enum.ref(),
        int.ref()
    )

    @Test
    fun defaultOrderToTableScan() {
        TestMarykModel.orderToScanType(ascending) shouldBe TableScan(direction = ASC)
        TestMarykModel.orderToScanType(descending) shouldBe TableScan(direction = DESC)

        TestMarykModel.orderToScanType(Orders(ascending)) shouldBe TableScan(direction = ASC)
    }

    @Test
    fun intOrderToIndexScan() {
        TestMarykModel.orderToScanType(
            int.ref().ascending()
        ) shouldBe IndexScan(
            int.ref(),
            direction = ASC
        )

        TestMarykModel.orderToScanType(
            int.ref().descending()
        ) shouldBe IndexScan(
            int.ref(),
            direction = DESC
        )

        TestMarykModel.orderToScanType(
            Orders(int.ref().ascending())
        ) shouldBe IndexScan(
            int.ref(),
            direction = ASC
        )

        TestMarykModel.orderToScanType(
            Orders(int.ref().ascending(), ascending)
        ) shouldBe IndexScan(
            int.ref(),
            direction = ASC
        )
    }

    @Test
    fun doubleOrderToIndexScan() {
        TestMarykModel.orderToScanType(
            double.ref().ascending()
        ) shouldBe IndexScan(
            Reversed(double.ref()),
            direction = DESC
        )

        TestMarykModel.orderToScanType(
            double.ref().descending()
        ) shouldBe IndexScan(
            Reversed(double.ref()),
            direction = ASC
        )
    }

    @Test
    fun dateTimeOrderToIndexScan() {
        TestMarykModel.orderToScanType(
            dateTime.ref().ascending()
        ) shouldBe IndexScan(
            multipleIndex,
            direction = DESC
        )

        TestMarykModel.orderToScanType(
            dateTime.ref().descending()
        ) shouldBe IndexScan(
            multipleIndex,
            direction = ASC
        )
    }

    @Test
    fun dateTimeEnumOrderToIndexScan() {
        TestMarykModel.orderToScanType(
            Orders(
                dateTime.ref().descending(),
                enum.ref().ascending()
            )
        ) shouldBe IndexScan(
            multipleIndex,
            direction = ASC
        )

        TestMarykModel.orderToScanType(
            Orders(
                dateTime.ref().ascending(),
                enum.ref().descending()
            )
        ) shouldBe IndexScan(
            multipleIndex,
            direction = DESC
        )
    }

    @Test
    fun dateTimeEnumIntOrderToIndexScan() {
        TestMarykModel.orderToScanType(
            Orders(
                dateTime.ref().descending(),
                enum.ref().ascending(),
                int.ref().ascending()
            )
        ) shouldBe IndexScan(
            multipleIndex,
            direction = ASC
        )

        TestMarykModel.orderToScanType(
            Orders(
                dateTime.ref().descending(),
                enum.ref().ascending(),
                int.ref().ascending(),
                ascending
            )
        ) shouldBe IndexScan(
            multipleIndex,
            direction = ASC
        )

        TestMarykModel.orderToScanType(
            Orders(
                dateTime.ref().ascending(),
                enum.ref().descending(),
                int.ref().descending()
            )
        ) shouldBe IndexScan(
            multipleIndex,
            direction = DESC
        )

        TestMarykModel.orderToScanType(
            Orders(
                dateTime.ref().ascending(),
                enum.ref().descending(),
                int.ref().descending(),
                descending
            )
        ) shouldBe IndexScan(
            multipleIndex,
            direction = DESC
        )
    }

    @Test
    fun orderNoIndexFound() {
        shouldThrow<RequestException> {
            TestMarykModel.orderToScanType(
                bool.ref().ascending()
            )
        }

        shouldThrow<RequestException> {
            TestMarykModel.orderToScanType(
                Orders(
                    dateTime.ref().ascending(),
                    bool.ref().descending()
                )
            )
        }
    }

    @Test
    fun wrongTableOrder() {
        shouldThrow<RequestException> {
            TestMarykModel.orderToScanType(
                Orders(int.ref().ascending(), descending)
            )
        }

        shouldThrow<RequestException> {
            TestMarykModel.orderToScanType(
                Orders(double.ref().descending(), descending)
            )
        }

        shouldThrow<RequestException> {
            TestMarykModel.orderToScanType(
                Orders(
                    dateTime.ref().ascending(),
                    enum.ref().ascending()
                )
            )
        }

        shouldThrow<RequestException> {
            TestMarykModel.orderToScanType(
                Orders(
                    dateTime.ref().ascending(),
                    enum.ref().descending(),
                    int.ref().ascending()
                )
            )
        }

        shouldThrow<RequestException> {
            TestMarykModel.orderToScanType(
                Orders(
                    dateTime.ref().ascending(),
                    enum.ref().descending(),
                    int.ref().descending(),
                    ascending
                )
            )
        }
    }
}
