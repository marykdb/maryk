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
import maryk.core.query.pairs.with
import maryk.lib.time.DateTime
import maryk.test.models.Option.V1
import maryk.test.models.TestMarykModel
import maryk.test.models.TestMarykModel.Properties.bool
import maryk.test.models.TestMarykModel.Properties.dateTime
import maryk.test.models.TestMarykModel.Properties.double
import maryk.test.models.TestMarykModel.Properties.enum
import maryk.test.models.TestMarykModel.Properties.int
import maryk.test.models.TestMarykModel.Properties.uint
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
        TestMarykModel.orderToScanType(ascending, emptyList()) shouldBe TableScan(direction = ASC)
        TestMarykModel.orderToScanType(descending, emptyList()) shouldBe TableScan(direction = DESC)

        TestMarykModel.orderToScanType(Orders(ascending), emptyList()) shouldBe TableScan(direction = ASC)
    }

    @Test
    fun intOrderToIndexScan() {
        TestMarykModel.orderToScanType(
            int.ref().ascending(),
            emptyList()
        ) shouldBe IndexScan(
            int.ref(),
            direction = ASC
        )

        TestMarykModel.orderToScanType(
            int.ref().descending(),
            emptyList()
        ) shouldBe IndexScan(
            int.ref(),
            direction = DESC
        )

        TestMarykModel.orderToScanType(
            Orders(int.ref().ascending()),
            emptyList()
        ) shouldBe IndexScan(
            int.ref(),
            direction = ASC
        )

        TestMarykModel.orderToScanType(
            Orders(int.ref().ascending(), ascending),
            emptyList()
        ) shouldBe IndexScan(
            int.ref(),
            direction = ASC
        )
    }

    @Test
    fun doubleOrderToIndexScan() {
        TestMarykModel.orderToScanType(
            double.ref().ascending(),
            emptyList()
        ) shouldBe IndexScan(
            Reversed(double.ref()),
            direction = DESC
        )

        TestMarykModel.orderToScanType(
            double.ref().descending(),
            emptyList()
        ) shouldBe IndexScan(
            Reversed(double.ref()),
            direction = ASC
        )
    }

    @Test
    fun dateTimeOrderToIndexScan() {
        TestMarykModel.orderToScanType(
            dateTime.ref().ascending(),
            emptyList()
        ) shouldBe IndexScan(
            multipleIndex,
            direction = DESC
        )

        TestMarykModel.orderToScanType(
            dateTime.ref().descending(),
            emptyList()
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
            ),
            emptyList()
        ) shouldBe IndexScan(
            multipleIndex,
            direction = ASC
        )

        TestMarykModel.orderToScanType(
            Orders(
                dateTime.ref().ascending(),
                enum.ref().descending()
            ),
            emptyList()
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
            ),
            emptyList()
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
            ),
            emptyList()
        ) shouldBe IndexScan(
            multipleIndex,
            direction = ASC
        )

        TestMarykModel.orderToScanType(
            Orders(
                dateTime.ref().ascending(),
                enum.ref().descending(),
                int.ref().descending()
            ),
            emptyList()
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
            ),
            emptyList()
        ) shouldBe IndexScan(
            multipleIndex,
            direction = DESC
        )
    }

    @Test
    fun dateTimeEnumIntOrderToIndexScanWithEqualPairs() {
        TestMarykModel.orderToScanType(
            Orders(
                dateTime.ref().descending(),
                int.ref().ascending()
            ),
            listOf(
                enum.ref() with V1
            )
        ) shouldBe IndexScan(
            multipleIndex,
            direction = ASC
        )

        TestMarykModel.orderToScanType(
            Orders(
                dateTime.ref().descending(),
                enum.ref().ascending(),
                ascending
            ),
            listOf(
                int.ref() with 4
            )
        ) shouldBe IndexScan(
            multipleIndex,
            direction = ASC
        )

        TestMarykModel.orderToScanType(
            Orders(
                enum.ref().descending(),
                int.ref().descending()
            ),
            listOf(
                dateTime.ref() with DateTime(2018, 5, 4)
            )
        ) shouldBe IndexScan(
            multipleIndex,
            direction = DESC
        )

        TestMarykModel.orderToScanType(
            Orders(
                enum.ref().ascending(),
                int.ref().ascending(),
                ascending
            ),
            listOf(
                dateTime.ref() with DateTime(2018, 5, 4)
            )
        ) shouldBe IndexScan(
            multipleIndex,
            direction = ASC
        )
    }

    @Test
    fun defaultOrdersToIndexScanWithEqualPairs() {
        TestMarykModel.orderToScanType(
            Orders(
                uint.ref().ascending(),
                bool.ref().ascending(),
                enum.ref().ascending()
            ),
            emptyList()
        ) shouldBe TableScan(
            direction = ASC
        )

        TestMarykModel.orderToScanType(
            Orders(
                uint.ref().descending()
            ),
            emptyList()
        ) shouldBe TableScan(
            direction = DESC
        )

        TestMarykModel.orderToScanType(
            Orders(
                uint.ref().ascending(),
                enum.ref().ascending()
            ),
            listOf(
                bool.ref() with true
            )
        ) shouldBe TableScan(
            direction = ASC
        )
    }

    @Test
    fun orderNoIndexFound() {
        shouldThrow<RequestException> {
            TestMarykModel.orderToScanType(
                bool.ref().ascending(),
                emptyList()
            )
        }

        shouldThrow<RequestException> {
            TestMarykModel.orderToScanType(
                Orders(
                    dateTime.ref().ascending(),
                    bool.ref().descending()
                ),
                emptyList()
            )
        }
    }

    @Test
    fun wrongTableOrder() {
        shouldThrow<RequestException> {
            TestMarykModel.orderToScanType(
                Orders(int.ref().ascending(), descending),
                emptyList()
            )
        }

        shouldThrow<RequestException> {
            TestMarykModel.orderToScanType(
                Orders(double.ref().descending(), descending),
                emptyList()
            )
        }

        shouldThrow<RequestException> {
            TestMarykModel.orderToScanType(
                Orders(
                    dateTime.ref().ascending(),
                    enum.ref().ascending()
                ),
                emptyList()
            )
        }

        shouldThrow<RequestException> {
            TestMarykModel.orderToScanType(
                Orders(
                    dateTime.ref().ascending(),
                    enum.ref().descending(),
                    int.ref().ascending()
                ),
                emptyList()
            )
        }

        shouldThrow<RequestException> {
            TestMarykModel.orderToScanType(
                Orders(
                    dateTime.ref().ascending(),
                    enum.ref().descending(),
                    int.ref().descending(),
                    ascending
                ),
                emptyList()
            )
        }

        shouldThrow<RequestException> {
            TestMarykModel.orderToScanType(
                Orders(
                    enum.ref().descending(),
                    int.ref().descending(),
                    ascending
                ),
                listOf(
                    dateTime.ref() with DateTime(2018, 5, 4)
                )
            )
        }
    }
}
