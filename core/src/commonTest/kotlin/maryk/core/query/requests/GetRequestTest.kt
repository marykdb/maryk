package maryk.core.query.requests

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.aggregations.Aggregations
import maryk.core.aggregations.metric.ValueCount
import maryk.core.extensions.toUnitLambda
import maryk.core.properties.graph
import maryk.core.query.RequestContext
import maryk.core.query.filters.Exists
import maryk.core.yaml.MarykYamlReader
import maryk.test.models.SimpleMarykModel
import maryk.test.requests.getMaxRequest
import maryk.test.requests.getRequest
import kotlin.test.Test
import kotlin.test.expect

class GetRequestTest {
    private val context = RequestContext(mapOf(
        SimpleMarykModel.Model.name toUnitLambda { SimpleMarykModel.Model }
    ))

    @Test
    fun createAsMap() {
        expect(getRequest) {
            GetRequest.Model.values(context) {
                mapNonNulls(
                    from with SimpleMarykModel,
                    keys with listOf(getRequest.keys[0], getRequest.keys[1])
                )
            }.toDataObject()
        }
    }

    @Test
    fun createAsMaxMap() {
        expect(getMaxRequest) {
            GetRequest.Model.values(context) {
                mapNonNulls(
                    from with SimpleMarykModel,
                    keys with listOf(getMaxRequest.keys[0], getMaxRequest.keys[1]),
                    where with Exists(SimpleMarykModel { value::ref }),
                    toVersion with 333uL,
                    filterSoftDeleted with true,
                    select with SimpleMarykModel.graph {
                        listOf(value)
                    },
                    aggregations with Aggregations(
                        namedAggregations = mapOf(
                            "totalValues" to ValueCount(SimpleMarykModel { value::ref })
                        )
                    )
                )
            }.toDataObject()
        }
    }

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(getRequest, GetRequest.Model, { this.context })
        checkProtoBufConversion(getMaxRequest, GetRequest.Model, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(getRequest, GetRequest.Model, { this.context })
        checkJsonConversion(getMaxRequest, GetRequest.Model, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            from: SimpleMarykModel
            keys: [dR9gVdRcSPw2molM1AiOng, Vc4WgX/mQHYCSEoLtfLSUQ]
            filterSoftDeleted: true

            """.trimIndent()
        ) {
            checkYamlConversion(getRequest, GetRequest.Model, { this.context })
        }

        expect(
            """
            from: SimpleMarykModel
            keys: [dR9gVdRcSPw2molM1AiOng, Vc4WgX/mQHYCSEoLtfLSUQ]
            select:
            - value
            where: !Exists value
            toVersion: 333
            filterSoftDeleted: true
            aggregations:
              totalValues: !ValueCount
                of: value

            """.trimIndent()
        ) {
            checkYamlConversion(getMaxRequest, GetRequest.Model, { this.context })
        }
    }

    @Test
    fun convertBasicDefinitionFromYAML() {
        val simpleYaml = """
        from: SimpleMarykModel
        keys: [dR9gVdRcSPw2molM1AiOng, Vc4WgX/mQHYCSEoLtfLSUQ]
        where:
        select:
        - value

        """.trimIndent()

        var index = 0

        val reader = MarykYamlReader {
            simpleYaml[index++].also {
                if (it == '\u0000') {
                    throw Throwable("0 char encountered")
                }
            }
        }

        GetRequest.Model.readJson(reader, this.context)
            .toDataObject()
            .apply {
                expect(SimpleMarykModel) { dataModel }
                expect(null) { where }
            }
    }
}
