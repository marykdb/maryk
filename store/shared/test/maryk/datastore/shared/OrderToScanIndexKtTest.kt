package maryk.datastore.shared

import kotlinx.datetime.LocalDateTime
import maryk.core.exceptions.RequestException
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
import maryk.datastore.shared.ScanType.IndexScan
import maryk.datastore.shared.ScanType.TableScan
import maryk.test.models.Option.V1
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.expect

class OrderToScanIndexKtTest {
    private val multipleIndex = TestMarykModel.run {
        Multiple(
            Reversed(dateTime.ref()),
            enum.ref(),
            int.ref()
        )
    }

    @Test
    fun defaultOrderToTableScan() {
        expect(TableScan(direction = ASC)) { TestMarykModel.orderToScanType(ascending, emptyList()) }
        expect(TableScan(direction = DESC)) { TestMarykModel.orderToScanType(descending, emptyList()) }

        expect(TableScan(direction = ASC)) { TestMarykModel.orderToScanType(Orders(ascending), emptyList()) }
    }

    @Test
    fun intOrderToIndexScan() {
        expect(
            IndexScan(
                TestMarykModel.int.ref(),
                direction = ASC
            )
        ) {
            TestMarykModel.orderToScanType(
                TestMarykModel.int.ref().ascending(),
                emptyList()
            )
        }


        expect(
            IndexScan(
                TestMarykModel.int.ref(),
                direction = DESC
            )
        ) {
            TestMarykModel.orderToScanType(
                TestMarykModel.int.ref().descending(),
                emptyList()
            )
        }

        expect(
            IndexScan(
                TestMarykModel.int.ref(),
                direction = ASC
            )
        ) {
            TestMarykModel.orderToScanType(
                Orders(TestMarykModel.int.ref().ascending()),
                emptyList()
            )
        }


        expect(
            IndexScan(
                TestMarykModel.int.ref(),
                direction = ASC
            )
        ) {
            TestMarykModel.orderToScanType(
                Orders(TestMarykModel.int.ref().ascending(), ascending),
                emptyList()
            )
        }

    }

    @Test
    fun doubleOrderToIndexScan() {
        expect(
            IndexScan(
                Reversed(TestMarykModel.double.ref()),
                direction = DESC
            )
        ) {
            TestMarykModel.orderToScanType(
                TestMarykModel.double.ref().ascending(),
                emptyList()
            )
        }


        expect(
            IndexScan(
                Reversed(TestMarykModel.double.ref()),
                direction = ASC
            )
        ) {
            TestMarykModel.orderToScanType(
                TestMarykModel.double.ref().descending(),
                emptyList()
            )
        }
    }

    @Test
    fun dateTimeOrderToIndexScan() {
        expect(
            IndexScan(
                multipleIndex,
                direction = DESC
            )
        ) {

            TestMarykModel.orderToScanType(
                TestMarykModel.dateTime.ref().ascending(),
                emptyList()
            )
        }

        expect(
            IndexScan(
                multipleIndex,
                direction = ASC
            )
        ) {
            TestMarykModel.orderToScanType(
                TestMarykModel.dateTime.ref().descending(),
                emptyList()
            )
        }
    }

    @Test
    fun dateTimeEnumOrderToIndexScan() {
        expect(
            IndexScan(
                multipleIndex,
                direction = ASC
            )
        ) {
            TestMarykModel.orderToScanType(
                Orders(
                    TestMarykModel.dateTime.ref().descending(),
                    TestMarykModel.enum.ref().ascending()
                ),
                emptyList()
            )
        }

        expect(
            IndexScan(
                multipleIndex,
                direction = DESC
            )
        ) {
            TestMarykModel.orderToScanType(
                Orders(
                    TestMarykModel.dateTime.ref().ascending(),
                    TestMarykModel.enum.ref().descending()
                ),
                emptyList()
            )
        }
    }

    @Test
    fun dateTimeEnumIntOrderToIndexScan() {
        expect(
            IndexScan(
                multipleIndex,
                direction = ASC
            )
        ) {
            TestMarykModel.orderToScanType(
                Orders(
                    TestMarykModel.dateTime.ref().descending(),
                    TestMarykModel.enum.ref().ascending(),
                    TestMarykModel.int.ref().ascending()
                ),
                emptyList()
            )
        }

        expect(
            IndexScan(
                multipleIndex,
                direction = ASC
            )
        ) {
            TestMarykModel.orderToScanType(
                Orders(
                    TestMarykModel.dateTime.ref().descending(),
                    TestMarykModel.enum.ref().ascending(),
                    TestMarykModel.int.ref().ascending(),
                    ascending
                ),
                emptyList()
            )
        }


        expect(
            IndexScan(
                multipleIndex,
                direction = DESC
            )
        ) {
            TestMarykModel.orderToScanType(
                Orders(
                    TestMarykModel.dateTime.ref().ascending(),
                    TestMarykModel.enum.ref().descending(),
                    TestMarykModel.int.ref().descending()
                ),
                emptyList()
            )
        }

        expect(
            IndexScan(
                multipleIndex,
                direction = DESC
            )
        ) {
            TestMarykModel.orderToScanType(
                Orders(
                    TestMarykModel.dateTime.ref().ascending(),
                    TestMarykModel.enum.ref().descending(),
                    TestMarykModel.int.ref().descending(),
                    descending
                ),
                emptyList()
            )
        }
    }

    @Test
    fun dateTimeEnumIntOrderToIndexScanWithEqualPairs() {
        expect(
            IndexScan(
                multipleIndex,
                direction = ASC
            )
        ) {
            TestMarykModel.orderToScanType(
                Orders(
                    TestMarykModel.dateTime.ref().descending(),
                    TestMarykModel.int.ref().ascending()
                ),
                listOf(
                    TestMarykModel.enum.ref() with V1
                )
            )
        }


        expect(
            IndexScan(
                multipleIndex,
                direction = ASC
            )
        ) {
            TestMarykModel.orderToScanType(
                Orders(
                    TestMarykModel.dateTime.ref().descending(),
                    TestMarykModel.enum.ref().ascending(),
                    ascending
                ),
                listOf(
                    TestMarykModel.int.ref() with 4
                )
            )
        }


        expect(
            IndexScan(
                multipleIndex,
                direction = DESC
            )
        ) {
            TestMarykModel.orderToScanType(
                Orders(
                    TestMarykModel.enum.ref().descending(),
                    TestMarykModel.int.ref().descending()
                ),
                listOf(
                    TestMarykModel.dateTime.ref() with LocalDateTime(2018, 5, 4, 0, 0)
                )
            )
        }


        expect(
            IndexScan(
                multipleIndex,
                direction = ASC
            )
        ) {
            TestMarykModel.orderToScanType(
                Orders(
                    TestMarykModel.enum.ref().ascending(),
                    TestMarykModel.int.ref().ascending(),
                    ascending
                ),
                listOf(
                    TestMarykModel.dateTime.ref() with LocalDateTime(2018, 5, 4, 0, 0)
                )
            )
        }

    }

    @Test
    fun defaultOrdersToIndexScanWithEqualPairs() {
        expect(
            TableScan(
                direction = ASC
            )
        ) {
            TestMarykModel.orderToScanType(
                Orders(
                    TestMarykModel.uint.ref().ascending(),
                    TestMarykModel.bool.ref().ascending(),
                    TestMarykModel.enum.ref().ascending()
                ),
                emptyList()
            )
        }

        expect(
            TableScan(
                direction = DESC
            )
        ) {
            TestMarykModel.orderToScanType(
                Orders(
                    TestMarykModel.uint.ref().descending()
                ),
                emptyList()
            )
        }

        expect(
            TableScan(
                direction = ASC
            )
        ) {
            TestMarykModel.orderToScanType(
                Orders(
                    TestMarykModel.uint.ref().ascending(),
                    TestMarykModel.enum.ref().ascending()
                ),
                listOf(
                    TestMarykModel.bool.ref() with true
                )
            )
        }
    }

    @Test
    fun orderNoIndexFound() {
        assertFailsWith<RequestException> {
            TestMarykModel.orderToScanType(
                TestMarykModel.bool.ref().ascending(),
                emptyList()
            )
        }

        assertFailsWith<RequestException> {
            TestMarykModel.orderToScanType(
                Orders(
                    TestMarykModel.dateTime.ref().ascending(),
                    TestMarykModel.bool.ref().descending()
                ),
                emptyList()
            )
        }
    }

    @Test
    fun wrongTableOrder() {
        assertFailsWith<RequestException> {
            TestMarykModel.orderToScanType(
                Orders(TestMarykModel.int.ref().ascending(), descending),
                emptyList()
            )
        }

        assertFailsWith<RequestException> {
            TestMarykModel.orderToScanType(
                Orders(TestMarykModel.double.ref().descending(), descending),
                emptyList()
            )
        }

        assertFailsWith<RequestException> {
            TestMarykModel.orderToScanType(
                Orders(
                    TestMarykModel.dateTime.ref().ascending(),
                    TestMarykModel.enum.ref().ascending()
                ),
                emptyList()
            )
        }

        assertFailsWith<RequestException> {
            TestMarykModel.orderToScanType(
                Orders(
                    TestMarykModel.dateTime.ref().ascending(),
                    TestMarykModel.enum.ref().descending(),
                    TestMarykModel.int.ref().ascending()
                ),
                emptyList()
            )
        }

        assertFailsWith<RequestException> {
            TestMarykModel.orderToScanType(
                Orders(
                    TestMarykModel.dateTime.ref().ascending(),
                    TestMarykModel.enum.ref().descending(),
                    TestMarykModel.int.ref().descending(),
                    ascending
                ),
                emptyList()
            )
        }

        assertFailsWith<RequestException> {
            TestMarykModel.orderToScanType(
                Orders(
                    TestMarykModel.enum.ref().descending(),
                    TestMarykModel.int.ref().descending(),
                    ascending
                ),
                listOf(
                    TestMarykModel.dateTime.ref() with LocalDateTime(2018, 5, 4, 0, 0)
                )
            )
        }
    }
}
