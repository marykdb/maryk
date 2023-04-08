package maryk.core.models

import maryk.checkJsonConversion
import maryk.checkProtoBufObjectValuesConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.core.query.filters.Exists
import maryk.core.query.requests.GetRequest
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.expect

private val key1 = SimpleMarykModel.key("dR9gVdRcSPw2molM1AiOng")
private val key2 = SimpleMarykModel.key("Vc4WgX/mQHYCSEoLtfLSUQ")

private val context = RequestContext(mapOf(
    SimpleMarykModel.Meta.name toUnitLambda { SimpleMarykModel }
))

class ObjectAsMapConversionTest {
    private val getRequestWithInjectable = GetRequest.run {
        create(
            from with SimpleMarykModel,
            keys with listOf(key1, key2),
            select with SimpleMarykModel.graph {
                listOf(value)
            },
            where with Exists(SimpleMarykModel { value::ref }),
            toVersion with 333uL,
            filterSoftDeleted with true,
            context = context,
        )
    }

    @Test
    fun convertToYAMLAndBack() {
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
            checkYamlConversion(
                getRequestWithInjectable,
                GetRequest,
                { context },
                checker = { a, b ->
                    assertEquals(b.toDataObject(), a.toDataObject())
                }
            )
        }
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(
            getRequestWithInjectable,
            GetRequest,
            { context },
            checker = { a, b ->
                assertEquals(b.toDataObject(), a.toDataObject())
            }
        )
    }

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufObjectValuesConversion(
            getRequestWithInjectable,
            GetRequest,
            { context },
            checker = { a, b ->
                assertEquals(b.toDataObject(), a.toDataObject())
            }
        )
    }
}
