package maryk.core.properties.types

import maryk.checkJsonConversion
import maryk.checkProtoBufObjectValuesConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.inject.Inject
import maryk.core.query.RequestContext
import maryk.core.query.descending
import maryk.core.query.filters.Exists
import maryk.core.query.requests.GetRequest
import maryk.core.query.requests.RequestType
import maryk.core.query.requests.Requests
import maryk.core.values.ObjectValues
import maryk.test.models.SimpleMarykModel
import maryk.test.shouldBe
import kotlin.test.Test

private val context = RequestContext(mapOf(
    SimpleMarykModel.name toUnitLambda { SimpleMarykModel }
)).apply {
    addToCollect("keysToInject", GetRequest)
}

class InjectInRequestTest {
    private val getRequestWithInjectable = GetRequest.values(context) {
        mapNonNulls(
            dataModel with SimpleMarykModel,
            keys injectWith Inject("keysToInject", GetRequest.ref { keys }),
            filter with Exists(SimpleMarykModel.ref { value }),
            order with SimpleMarykModel.ref { value }.descending(),
            toVersion with 333uL,
            filterSoftDeleted with true,
            select with SimpleMarykModel.graph {
                listOf(value)
            }
        )
    }

    private val requests = Requests.values {
        mapNonNulls(
            requests withSerializable listOf(TypedValue(RequestType.Get, getRequestWithInjectable))
        )
    }

    private fun checker(converted: ObjectValues<GetRequest<*, *>, GetRequest.Properties>, original: ObjectValues<GetRequest<*, *>, GetRequest.Properties>) {
        val originalKeys = converted.original { keys } as Any?

        when (originalKeys) {
            null -> error("Keys should not be null")
            is ObjectValues<*, *> -> {
                originalKeys.toDataObject() shouldBe original.original { keys }
            }
            else -> {
                originalKeys shouldBe original.original { keys }
            }
        }
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
        from: SimpleMarykModel
        keys: !:Inject
          keysToInject: keys
        select:
        - value
        filter: !Exists value
        order: !Desc value
        toVersion: 333
        filterSoftDeleted: true

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
        {"from":"SimpleMarykModel","?keys":{"keysToInject":"keys"},"select":["value"],"filter":["Exists","value"],"order":{"propertyReference":"value","direction":"DESC"},"toVersion":"333","filterSoftDeleted":true}
        """.trimIndent()
    }

    @Test
    fun convertToProtoBufAndBack() {
        context.addToCollect("keysToInject", GetRequest)

        checkProtoBufObjectValuesConversion(
            requests,
            Requests,
            { context },
            checker = { converted, original ->
                @Suppress("UNCHECKED_CAST")
                checker(
                    converted.original { requests }!![0].value as ObjectValues<GetRequest<*, *>, GetRequest.Properties>,
                    original.original { requests }!![0].value as ObjectValues<GetRequest<*, *>, GetRequest.Properties>
                )
            }
        )
    }
}
