package maryk.core.query.responses

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.aggregations.AggregationsResponse
import maryk.core.aggregations.metric.ValueCountResponse
import maryk.core.models.asValues
import maryk.core.models.key
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.RequestContext
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.orders.Direction
import maryk.core.query.requests.createCursor
import maryk.core.query.requests.scan
import maryk.test.models.SimpleMarykModel
import maryk.test.models.SimpleMarykObject
import kotlin.test.Test
import kotlin.test.expect

class ValuesResponseTest {
    private val simpleValue = SimpleMarykModel.create {
        value with "haha1"
    }

    private val key = SimpleMarykModel.key("-1xO4zD4R5sIMcS9pXTZEA")

    private val objectsResponse = ValuesResponse(
        SimpleMarykModel,
        listOf(
            ValuesWithMetaData(
                key = key,
                values = simpleValue,
                firstVersion = 0uL,
                lastVersion = 14141uL,
                isDeleted = false
            )
        ),
        AggregationsResponse(
            "total" to ValueCountResponse(SimpleMarykObject { value::ref }, 1uL)
        ),
        dataFetchType = FetchByKey,
    )

    private val context = RequestContext(mapOf(
        SimpleMarykModel.Meta.name to DataModelReference(SimpleMarykModel)
    ))

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.objectsResponse, ValuesResponse, { this.context })
        checkProtoBufConversion(
            this.objectsResponse.copy(
                nextCursor = SimpleMarykModel.scan().createCursor(key, null),
            ),
            ValuesResponse,
            { this.context },
        )
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.objectsResponse, ValuesResponse, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            dataModel: SimpleMarykModel
            values:
            - key: -1xO4zD4R5sIMcS9pXTZEA
              values:
                value: haha1
              firstVersion: 0
              lastVersion: 14141
              isDeleted: false
            aggregations:
              total: !ValueCount
                of: value
                value: 1
            dataFetchType: !Key

            """.trimIndent()
        ) {
            checkYamlConversion(this.objectsResponse, ValuesResponse, { this.context })
        }
    }

    @Test
    fun preservesEveryDataFetchTypeAcrossResponseFormats() {
        listOf(
            FetchByTableScan(Direction.ASC, byteArrayOf(1), byteArrayOf(2)),
            FetchByIndexScan(byteArrayOf(3), Direction.DESC, byteArrayOf(4), byteArrayOf(5)),
            FetchByUpdateHistoryIndex(),
            FetchByUniqueKey(byteArrayOf(6)),
        ).forEach { dataFetchType ->
            val response = objectsResponse.copy(dataFetchType = dataFetchType)
            checkProtoBufConversion(response, ValuesResponse, { context })
            checkJsonConversion(response, ValuesResponse, { context })
            checkYamlConversion(response, ValuesResponse, { context })
        }
    }

    @Test
    fun referenceToValuesAndGetOnObject() {
        val valuesResponseRef = ValuesResponse { values.refAt(0u) { values } }
        val simpleValueRef = SimpleMarykObject(valuesResponseRef) { value::ref }

        expect("values.@0.values") { valuesResponseRef.completeName }
        expect("values.@0.values.value") { simpleValueRef.completeName }

        val objectValues = ValuesResponse.asValues(objectsResponse)

        expect("haha1") { objectValues[simpleValueRef] }
    }
}
