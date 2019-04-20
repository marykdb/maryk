package maryk.core.properties.types

import maryk.checkJsonConversion
import maryk.checkProtoBufObjectValuesConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.inject.Inject
import maryk.core.models.asValues
import maryk.core.models.key
import maryk.core.properties.exceptions.InjectException
import maryk.core.query.RequestContext
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.filters.Exists
import maryk.core.query.requests.GetRequest
import maryk.core.query.requests.RequestType
import maryk.core.query.requests.Requests
import maryk.core.query.responses.ValuesResponse
import maryk.core.values.ObjectValues
import maryk.test.models.ReferencesModel
import maryk.test.models.SimpleMarykModel
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

private val context = RequestContext(mapOf(
    SimpleMarykModel.name toUnitLambda { SimpleMarykModel },
    ReferencesModel.name toUnitLambda { ReferencesModel }
)).apply {
    addToCollect("keysToInject", ValuesResponse)
    addToCollect("referencedKeys", ValuesResponse)
}

class InjectInRequestTest {
    private val getRequestWithInjectable = GetRequest.values(context) {
        mapNonNulls(
            dataModel with SimpleMarykModel,
            keys injectWith Inject("keysToInject", GetRequest.ref { keys }),
            where with Exists(SimpleMarykModel.ref { value }),
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

    @Test
    fun testInjectInValuesGetRequest() {
        val requestRef = ValuesResponse { values.at(0u) { values.ref(ReferencesModel) { references } } }

        val getRequest = GetRequest.values(context) {
            mapNonNulls(
                keys injectWith Inject("referencedKeys", requestRef)
            )
        }

        shouldThrow<InjectException> {
            getRequest { keys }
        } shouldBe InjectException("referencedKeys")

        val expectedKeys = listOf(
            SimpleMarykModel.key(SimpleMarykModel("v1")),
            SimpleMarykModel.key(SimpleMarykModel("v2"))
        )

        val row = ReferencesModel(
            references = expectedKeys
        )

        val response = ValuesResponse(
            dataModel = ReferencesModel,
            values = listOf(
                ValuesWithMetaData(
                    key = ReferencesModel.key(row),
                    values = row,
                    isDeleted = false,
                    firstVersion = 0uL,
                    lastVersion = 3uL
                )
            )
        )

        context.collectResult("referencedKeys", ValuesResponse.asValues(response))

        context.dataModel = ReferencesModel

        getRequest { keys } shouldBe expectedKeys
    }

    private fun checker(
        converted: ObjectValues<GetRequest<*, *>, GetRequest.Properties>,
        original: ObjectValues<GetRequest<*, *>, GetRequest.Properties>
    ) {
        when (val originalKeys = converted.original { keys } as Any?) {
            null -> error("Keys should not be null")
            is ObjectValues<*, *> ->
                originalKeys.toDataObject() shouldBe original.original { keys }
            else -> originalKeys shouldBe original.original { keys }
        }
        converted { where } shouldBe original { where }
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
        where: !Exists value
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
        {"from":"SimpleMarykModel","?keys":{"keysToInject":"keys"},"select":["value"],"where":["Exists","value"],"toVersion":"333","filterSoftDeleted":true}
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
