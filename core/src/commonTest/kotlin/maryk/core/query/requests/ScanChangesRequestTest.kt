package maryk.core.query.requests

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.exceptions.RequestException
import maryk.core.models.DataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.string
import maryk.core.properties.definitions.index.AnyOf
import maryk.core.properties.definitions.index.normalize
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.RequestContext
import maryk.core.query.filters.Equals
import maryk.core.query.filters.IsFilter
import maryk.core.query.filters.Matches
import maryk.core.query.filters.MatchesPrefix
import maryk.core.query.filters.MatchesRegEx
import maryk.core.query.filters.Not
import maryk.core.query.orders.Order.Companion.ascending
import maryk.core.query.orders.Orders
import maryk.core.query.orders.ascending
import maryk.core.query.orders.descending
import maryk.core.query.pairs.with
import maryk.test.models.SimpleMarykModel
import maryk.test.models.CaseInsensitivePerson
import maryk.test.models.TestMarykModel
import maryk.test.requests.scanChangesMaxRequest
import maryk.test.requests.scanChangesRequest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.expect

class ScanChangesRequestTest {
    @Test
    fun checkNamedSearchMutability() {
        val filters = listOf<IsFilter>(
            Matches("name" with "match"),
            MatchesPrefix("name" with "ma"),
            MatchesRegEx("name" with Regex("^ma.*$")),
        )
        for (filter in filters.flatMap { listOf(it, Not(it)) }) {
            assertFailsWith<RequestException> { CaseInsensitivePerson.scanChanges(where = filter) }
            ImmutableSearchRecord.scanChanges(where = filter)
        }
    }

    @Test
    fun checkNestedOrderMutability() {
        assertFailsWith<RequestException> {
            NestedOrderRecord.scanChanges(order = NestedOrderRecord.ref { optional { code } }.ascending())
        }
        NestedOrderRecord.scanChanges(order = NestedOrderRecord.ref { stable { code } }.ascending())
    }

    private val context = RequestContext(mapOf(
        SimpleMarykModel.Meta.name to DataModelReference(SimpleMarykModel),
        TestMarykModel.Meta.name to DataModelReference(TestMarykModel),
    ))

    @Test
    fun rejectLimitAboveMaximum() {
        assertFailsWith<RequestException> {
            TestMarykModel.scanChanges(limit = MAX_SCAN_LIMIT + 1u)
        }
    }

    @Test
    fun rejectZeroLimit() {
        assertFailsWith<RequestException> {
            TestMarykModel.scanChanges(limit = 0u)
        }
    }

    @Test
    fun checkOrders() {
        // Does not fail
        TestMarykModel.scanChanges(
            order = ascending
        )

        // Does not fail
        TestMarykModel.scanChanges(
            order = TestMarykModel.ref { uint }.ascending()
        )

        assertFailsWith<RequestException> {
            TestMarykModel.scanChanges(
                order = TestMarykModel.ref { int }.ascending()
            )
        }

        assertFailsWith<RequestException> {
            TestMarykModel.scanChanges(
                order = Orders(
                    TestMarykModel.ref { dateTime }.descending(),
                    TestMarykModel.ref { enum }.ascending()
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
                TestMarykModel.ref { uint } with 8u
            )
        )

        assertFailsWith<RequestException> {
            TestMarykModel.scanChanges(
                where = Equals(
                    TestMarykModel.ref { int } with 8
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

private object ImmutableSearchRecord : RootDataModel<ImmutableSearchRecord>(
    indexes = { listOf(AnyOf("name", ImmutableSearchRecord.name.ref()).normalize()) },
) {
    val name by string(1u, required = true, final = true)
}

private object NestedOrderLeaf : DataModel<NestedOrderLeaf>() {
    val code by string(1u, required = true, final = true)
}

private object NestedOrderRecord : RootDataModel<NestedOrderRecord>() {
    val optional by embed(1u, required = false, dataModel = { NestedOrderLeaf })
    val stable by embed(2u, required = true, final = true, dataModel = { NestedOrderLeaf })
}
