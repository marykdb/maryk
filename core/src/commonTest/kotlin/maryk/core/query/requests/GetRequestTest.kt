package maryk.core.query.requests

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.core.query.filters.Exists
import maryk.core.yaml.MarykYamlReaders
import maryk.test.models.SimpleMarykModel
import maryk.test.requests.getMaxRequest
import maryk.test.requests.getRequest
import kotlin.test.Test
import kotlin.test.expect

class GetRequestTest {
    private val context = RequestContext(mapOf(
        SimpleMarykModel.name toUnitLambda { SimpleMarykModel }
    ))

    @Test
    fun createAsMap() {
        expect(getRequest) {
            GetRequest.values(context) {
                mapNonNulls(
                    dataModel with SimpleMarykModel,
                    keys with listOf(getRequest.keys[0], getRequest.keys[1])
                )
            }.toDataObject()
        }
    }

    @Test
    fun createAsMaxMap() {
        expect(getMaxRequest) {
            GetRequest.values(context) {
                mapNonNulls(
                    dataModel with SimpleMarykModel,
                    keys with listOf(getMaxRequest.keys[0], getMaxRequest.keys[1]),
                    where with Exists(SimpleMarykModel { value::ref }),
                    toVersion with 333uL,
                    filterSoftDeleted with true,
                    select with SimpleMarykModel.graph {
                        listOf(value)
                    }
                )
            }.toDataObject()
        }
    }

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(getRequest, GetRequest, { this.context })
        checkProtoBufConversion(getMaxRequest, GetRequest, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(getRequest, GetRequest, { this.context })
        checkJsonConversion(getMaxRequest, GetRequest, { this.context })
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
            checkYamlConversion(getRequest, GetRequest, { this.context })
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

            """.trimIndent()
        ) {
            checkYamlConversion(getMaxRequest, GetRequest, { this.context })
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

        val reader = MarykYamlReaders {
            simpleYaml[index++].also {
                if (it == '\u0000') {
                    throw Throwable("0 char encountered")
                }
            }
        }


        GetRequest.readJson(reader, this.context)
            .toDataObject()
            .apply {
                expect(SimpleMarykModel) { dataModel }
                expect(null) { where }
            }
    }
}
