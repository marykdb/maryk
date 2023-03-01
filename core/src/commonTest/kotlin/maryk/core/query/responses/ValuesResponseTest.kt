package maryk.core.query.responses

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.aggregations.AggregationsResponse
import maryk.core.aggregations.metric.ValueCountResponse
import maryk.core.extensions.toUnitLambda
import maryk.core.models.asValues
import maryk.core.query.RequestContext
import maryk.core.query.ValuesWithMetaData
import maryk.test.models.SimpleMarykModel
import maryk.test.models.SimpleMarykObject
import kotlin.test.Test
import kotlin.test.expect

class ValuesResponseTest {
    private val simpleValue = SimpleMarykModel.run { create(value with "haha1") }

    private val key = SimpleMarykModel.key("+1xO4zD4R5sIMcS9pXTZEA")

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
        )
    )

    private val context = RequestContext(mapOf(
        SimpleMarykModel.Model.name toUnitLambda { SimpleMarykModel.Model }
    ))

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.objectsResponse, ValuesResponse.Model, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.objectsResponse, ValuesResponse.Model, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            dataModel: SimpleMarykModel
            values:
            - key: +1xO4zD4R5sIMcS9pXTZEA
              values:
                value: haha1
              firstVersion: 0
              lastVersion: 14141
              isDeleted: false
            aggregations:
              total: !ValueCount
                of: value
                value: 1

            """.trimIndent()
        ) {
            checkYamlConversion(this.objectsResponse, ValuesResponse.Model, { this.context })
        }
    }

    @Test
    fun referenceToValuesAndGetOnObject() {
        val valuesResponseRef = ValuesResponse { values.refAt(0u) { values } }
        val simpleValueRef = SimpleMarykObject(valuesResponseRef) { value::ref }

        expect("values.@0.values") { valuesResponseRef.completeName }
        expect("values.@0.values.value") { simpleValueRef.completeName }

        val objectValues = ValuesResponse.Model.asValues(objectsResponse)

        expect("haha1") { objectValues[simpleValueRef] }
    }
}
