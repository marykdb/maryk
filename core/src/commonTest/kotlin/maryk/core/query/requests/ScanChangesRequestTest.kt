package maryk.core.query.requests

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.exceptions.RequestException
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.core.query.filters.Equals
import maryk.core.query.orders.Order.Companion.ascending
import maryk.core.query.orders.Orders
import maryk.core.query.orders.ascending
import maryk.core.query.orders.descending
import maryk.core.query.pairs.with
import maryk.test.models.SimpleMarykModel
import maryk.test.models.TestMarykModel
import maryk.test.requests.scanChangesMaxRequest
import maryk.test.requests.scanChangesRequest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.expect

class ScanChangesRequestTest {
    private val context = RequestContext(mapOf(
        SimpleMarykModel.Model.name toUnitLambda { SimpleMarykModel },
        TestMarykModel.Model.name toUnitLambda { TestMarykModel }
    ))

    @Test
    fun checkOrders() {
        // Does not fail
        TestMarykModel.scanChanges(
            order = ascending
        )

        // Does not fail
        TestMarykModel.scanChanges(
            order = TestMarykModel { uint::ref }.ascending()
        )

        assertFailsWith<RequestException> {
            TestMarykModel.scanChanges(
                order = TestMarykModel { int::ref }.ascending()
            )
        }

        assertFailsWith<RequestException> {
            TestMarykModel.scanChanges(
                order = Orders(
                    TestMarykModel { dateTime::ref }.descending(),
                    TestMarykModel { enum::ref }.ascending()
                )
            )
        }
    }

    @Test
    fun checkWhere() {
        // Does not fail
        TestMarykModel.scanChanges()

        // Does not fail
        TestMarykModel.scanChanges(
            where = Equals(
                TestMarykModel { uint::ref } with 8u
            )
        )

        assertFailsWith<RequestException> {
            TestMarykModel.scanChanges(
                where = Equals(
                    TestMarykModel { int::ref } with 8
                )
            )
        }
    }

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(scanChangesRequest, ScanChangesRequest, { this.context })
        checkProtoBufConversion(scanChangesMaxRequest, ScanChangesRequest, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(scanChangesRequest, ScanChangesRequest, { this.context })
        checkJsonConversion(scanChangesMaxRequest, ScanChangesRequest, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            from: SimpleMarykModel
            filterSoftDeleted: true
            limit: 100
            includeStart: true
            fromVersion: 0
            maxVersions: 1

            """.trimIndent()
        ) {
            checkYamlConversion(scanChangesRequest, ScanChangesRequest, { this.context })
        }

        expect(
            """
            from: TestMarykModel
            startKey: AAACKwEAAg
            select:
            - uint
            where: !Exists uint
            toVersion: 2345
            filterSoftDeleted: true
            order: !Desc uint
            limit: 300
            includeStart: false
            fromVersion: 1234
            maxVersions: 10

            """.trimIndent()
        ) {
            checkYamlConversion(scanChangesMaxRequest, ScanChangesRequest, { this.context })
        }
    }
}
