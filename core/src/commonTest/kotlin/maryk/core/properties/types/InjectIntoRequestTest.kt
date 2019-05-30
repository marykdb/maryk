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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.expect

private val context = RequestContext(mapOf(
    SimpleMarykModel.name toUnitLambda { SimpleMarykModel },
    ReferencesModel.name toUnitLambda { ReferencesModel }
)).apply {
    addToCollect("keysToInject", ValuesResponse)
    addToCollect("referencedKeys", ValuesResponse)
}

class InjectIntoRequestTest {
    private val getRequestWithInjectable = GetRequest.values(context) {
        mapNonNulls(
            dataModel with SimpleMarykModel,
            keys injectWith Inject("keysToInject", GetRequest { keys::ref }),
            where with Exists(SimpleMarykModel { value::ref }),
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
        val requestRef = ValuesResponse { values.atAny { values.refWithDM(ReferencesModel) { references } } }

        val getRequest = GetRequest.values(context) {
            mapNonNulls(
                keys injectWith Inject("referencedKeys", requestRef)
            )
        }

        expect(InjectException("referencedKeys")) {
            assertFails {
                getRequest { keys }
            }
        }

        val expectedKeys = listOf(
            SimpleMarykModel.key(SimpleMarykModel("v1")),
            SimpleMarykModel.key(SimpleMarykModel("v2")),
            SimpleMarykModel.key(SimpleMarykModel("v3"))
        )

        val row1 = ReferencesModel(
            references = expectedKeys.subList(0, 2)
        )

        val row2 = ReferencesModel(
            references = expectedKeys.subList(2, 3)
        )

        val response = ValuesResponse(
            dataModel = ReferencesModel,
            values = listOf(
                ValuesWithMetaData(
                    key = ReferencesModel.key(row1),
                    values = row1,
                    isDeleted = false,
                    firstVersion = 0uL,
                    lastVersion = 3uL
                ),
                ValuesWithMetaData(
                    key = ReferencesModel.key(row2),
                    values = row2,
                    isDeleted = false,
                    firstVersion = 1uL,
                    lastVersion = 4uL
                )
            )
        )

        context.collectResult("referencedKeys", ValuesResponse.asValues(response))

        context.dataModel = ReferencesModel

        expect(expectedKeys) { getRequest { keys } }
    }

    private fun checker(
        converted: ObjectValues<GetRequest<*, *>, GetRequest.Properties>,
        original: ObjectValues<GetRequest<*, *>, GetRequest.Properties>
    ) {
        when (val originalKeys = converted.original { keys } as Any?) {
            null -> error("Keys should not be null")
            is ObjectValues<*, *> ->
                assertEquals<Any?>(originalKeys.toDataObject(), original.original { keys })
            else ->
                expect(original.original { keys }) { originalKeys }
        }
        assertEquals(original { where }, converted { where })
    }

    @Test
    fun convertToYAMLAndBack() {
        context.addToCollect("keysToInject", GetRequest)

        expect(
            """
            from: SimpleMarykModel
            keys: !:Inject
              keysToInject: keys
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
                checker = ::checker
            )
        }
    }

    @Test
    fun convertToJSONAndBack() {
        context.addToCollect("keysToInject", GetRequest)

        expect(
            """
            {"from":"SimpleMarykModel","?keys":{"keysToInject":"keys"},"select":["value"],"where":["Exists","value"],"toVersion":"333","filterSoftDeleted":true}
            """.trimIndent()
        ) {
            checkJsonConversion(
                getRequestWithInjectable,
                GetRequest,
                { context },
                checker = ::checker
            )
        }
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
