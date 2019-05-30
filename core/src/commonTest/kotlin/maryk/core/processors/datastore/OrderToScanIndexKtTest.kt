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
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.expect

class OrderToScanIndexKtTest {
    private val multipleIndex = Multiple(
        Reversed(dateTime.ref()),
        enum.ref(),
        int.ref()
    )

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
                int.ref(),
                direction = ASC
            )
        ) {
            TestMarykModel.orderToScanType(
                int.ref().ascending(),
                emptyList()
            )
        }


        expect(
            IndexScan(
                int.ref(),
                direction = DESC
            )
        ) {
            TestMarykModel.orderToScanType(
                int.ref().descending(),
                emptyList()
            )
        }

        expect(
            IndexScan(
                int.ref(),
                direction = ASC
            )
        ) {
            TestMarykModel.orderToScanType(
                Orders(int.ref().ascending()),
                emptyList()
            )
        }


        expect(
            IndexScan(
                int.ref(),
                direction = ASC
            )
        ) {
            TestMarykModel.orderToScanType(
                Orders(int.ref().ascending(), ascending),
                emptyList()
            )
        }

    }

    @Test
    fun doubleOrderToIndexScan() {
        expect(
            IndexScan(
                Reversed(double.ref()),
                direction = DESC
            )
        ) {
            TestMarykModel.orderToScanType(
                double.ref().ascending(),
                emptyList()
            )
        }


        expect(
            IndexScan(
                Reversed(double.ref()),
                direction = ASC
            )
        ) {
            TestMarykModel.orderToScanType(
                double.ref().descending(),
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
                dateTime.ref().ascending(),
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
                dateTime.ref().descending(),
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
                    dateTime.ref().descending(),
                    enum.ref().ascending()
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
                    dateTime.ref().ascending(),
                    enum.ref().descending()
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
                    dateTime.ref().descending(),
                    enum.ref().ascending(),
                    int.ref().ascending()
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
                    dateTime.ref().descending(),
                    enum.ref().ascending(),
                    int.ref().ascending(),
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
                    dateTime.ref().ascending(),
                    enum.ref().descending(),
                    int.ref().descending()
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
                    dateTime.ref().ascending(),
                    enum.ref().descending(),
                    int.ref().descending(),
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
                    dateTime.ref().descending(),
                    int.ref().ascending()
                ),
                listOf(
                    enum.ref() with V1
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
                    dateTime.ref().descending(),
                    enum.ref().ascending(),
                    ascending
                ),
                listOf(
                    int.ref() with 4
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
                    enum.ref().descending(),
                    int.ref().descending()
                ),
                listOf(
                    dateTime.ref() with DateTime(2018, 5, 4)
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
                    enum.ref().ascending(),
                    int.ref().ascending(),
                    ascending
                ),
                listOf(
                    dateTime.ref() with DateTime(2018, 5, 4)
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
                    uint.ref().ascending(),
                    bool.ref().ascending(),
                    enum.ref().ascending()
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
                    uint.ref().descending()
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
                    uint.ref().ascending(),
                    enum.ref().ascending()
                ),
                listOf(
                    bool.ref() with true
                )
            )
        }
    }

    @Test
    fun orderNoIndexFound() {
        assertFailsWith<RequestException> {
            TestMarykModel.orderToScanType(
                bool.ref().ascending(),
                emptyList()
            )
        }

        assertFailsWith<RequestException> {
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
        assertFailsWith<RequestException> {
            TestMarykModel.orderToScanType(
                Orders(int.ref().ascending(), descending),
                emptyList()
            )
        }

        assertFailsWith<RequestException> {
            TestMarykModel.orderToScanType(
                Orders(double.ref().descending(), descending),
                emptyList()
            )
        }

        assertFailsWith<RequestException> {
            TestMarykModel.orderToScanType(
                Orders(
                    dateTime.ref().ascending(),
                    enum.ref().ascending()
                ),
                emptyList()
            )
        }

        assertFailsWith<RequestException> {
            TestMarykModel.orderToScanType(
                Orders(
                    dateTime.ref().ascending(),
                    enum.ref().descending(),
                    int.ref().ascending()
                ),
                emptyList()
            )
        }

        assertFailsWith<RequestException> {
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

        assertFailsWith<RequestException> {
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
