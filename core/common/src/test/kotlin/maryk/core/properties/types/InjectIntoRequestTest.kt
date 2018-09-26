package maryk.core.properties.types

import maryk.SimpleMarykModel
import maryk.checkJsonConversion
import maryk.checkYamlConversion
import maryk.core.objects.ObjectValues
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.types.numeric.toUInt64
import maryk.core.query.RequestContext
import maryk.core.query.descending
import maryk.core.query.filters.Exists
import maryk.core.query.requests.GetRequest
import maryk.test.shouldBe
import kotlin.test.Test

private val context = RequestContext(mapOf(
    SimpleMarykModel.name to { SimpleMarykModel }
))

class InjectInRequestTest {
    private val getRequestWithInjectable = GetRequest.map(context) {
        mapNonNulls(
            dataModel with SimpleMarykModel,
            keys injectWith Inject("keysToInject", GetRequest.ref { keys }),
            filter with Exists(SimpleMarykModel.ref { value }),
            order with SimpleMarykModel.ref { value }.descending(),
            toVersion with 333L.toUInt64(),
            filterSoftDeleted with true,
            select with SimpleMarykModel.props {
                RootPropRefGraph<SimpleMarykModel>(
                    value
                )
            }
        )
    }

    fun checker(converted: ObjectValues<GetRequest<*>, GetRequest.Properties>, original: ObjectValues<GetRequest<*>, GetRequest.Properties>) {
        (converted.original { keys } as ObjectValues<*, *>).toDataObject() shouldBe original.original { keys }
        converted { filter } shouldBe original { filter }
    }

    @Test
    fun convertToYAMLAndBack() {
        context.addToCollect("keysToInject", GetRequest)

        checkYamlConversion(
            getRequestWithInjectable,
            GetRequest,
            { context },
            checker = ::checker
        ) shouldBe """
        dataModel: SimpleMarykModel
        keys: !:Inject
          keysToInject: keys
        filter: !Exists value
        order: !Desc value
        toVersion: 333
        filterSoftDeleted: true
        select:
        - value

        """.trimIndent()
    }

    @Test
    fun convertToJSONAndBack() {
        context.addToCollect("keysToInject", GetRequest)

        checkJsonConversion(
            getRequestWithInjectable,
            GetRequest,
            { context },
            checker = ::checker
        ) shouldBe """
        {"dataModel":"SimpleMarykModel","?keys":{"keysToInject":"keys"},"filter":["Exists","value"],"order":{"propertyReference":"value","direction":"DESC"},"toVersion":"333","filterSoftDeleted":true,"select":["value"]}
        """.trimIndent()
    }
}
