package maryk.core.properties.types

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.inject.Inject
import maryk.core.models.testExtendedMarykModelObject
import maryk.core.models.testMarykModelObject
import maryk.core.properties.asValues
import maryk.core.properties.exceptions.InjectException
import maryk.core.properties.key
import maryk.core.properties.values
import maryk.core.query.DefinitionsContext
import maryk.core.query.RequestContext
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.filters.Equals
import maryk.core.query.pairs.with
import maryk.core.query.requests.GetRequest
import maryk.core.query.requests.get
import maryk.core.query.responses.ValuesResponse
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.expect

class InjectTest {
    private val definitionsContext = DefinitionsContext(
        dataModels = mutableMapOf(
            EmbeddedMarykModel.Model.name toUnitLambda { EmbeddedMarykModel }
        )
    )

    private val context = RequestContext(
        definitionsContext
    )

    private val key1 = TestMarykModel.key(testMarykModelObject)
    private val key2 = TestMarykModel.key(testExtendedMarykModelObject)

    private val getRequest = TestMarykModel.get(key1, key2)

    private val valuesResponse = ValuesResponse.asValues(
        ValuesResponse(
            TestMarykModel,
            listOf(
                ValuesWithMetaData(
                    key = key2,
                    values = testExtendedMarykModelObject,
                    firstVersion = 1234uL,
                    lastVersion = 3456uL,
                    isDeleted = false
                ),
                ValuesWithMetaData(
                    key = key1,
                    values = testMarykModelObject,
                    firstVersion = 1235uL,
                    lastVersion = 3457uL,
                    isDeleted = false
                )
            )
        )
    )

    private val injectSimple = Inject("testSimpleConvert", EmbeddedMarykModel { model { value::ref } })
    private val injectCompleteObject = Inject("testCompleteConvert")

    init {
        context.addToCollect("testCollection", getRequest)
        context.collectResult("testCollection", valuesResponse)
        context.addToCollect("testSimpleConvert", EmbeddedMarykModel)
    }

    private val firstResponseValueRef = ValuesResponse { values.refAt(0u) { values } }

    private val inject =
        Inject("testCollection", TestMarykModel(firstResponseValueRef) { string::ref })

    private val injectDeep =
        Inject("testCollection", TestMarykModel(firstResponseValueRef) { embeddedValues { value::ref } })

    private val injectFromAny =
        Inject("testCollection", ValuesResponse { values.atAny { values.refWithDM(TestMarykModel.Model) { string } } })

    @Test
    fun testGetToCollect() {
        expect(ValuesResponse) { context.getToCollectModel("testCollection")?.model }
    }

    @Test
    fun testResolve() {
        expect("hay") { inject.resolve(context) }
        expect("test") { injectDeep.resolve(context) }
    }

    @Test
    fun testResolveAny() {
        expect(listOf("hay", "haas")) { injectFromAny.resolve(context) as List<*> }
    }

    private val valuesToCollect = EmbeddedMarykModel.run { create(
        value with "a test value",
        model with EmbeddedMarykModel.run { create(
            value  with "embedded value"
        ) }
    ) }

    @Test
    fun testInjectInValues() {
        context.addToCollect("testCollection2", EmbeddedMarykModel)

        val values = TestMarykModel.values(context) {
            mapNonNulls(
                string injectWith Inject("testCollection2", EmbeddedMarykModel { this.model { value::ref } })
            )
        }

        expect(InjectException("testCollection2")) {
            assertFails {
                values { string }
            }
        }

        context.collectResult("testCollection2", valuesToCollect)

        expect("embedded value") { values { string } }
    }

    @Test
    fun testInjectInValuesWithResponse() {
        val values = TestMarykModel.values(context) {
            mapNonNulls(
                string injectWith injectDeep
            )
        }

        expect("test") { values { string } }
    }

    @Test
    fun testInjectInObject() {
        context.addToCollect("where", Equals)

        val getRequest = GetRequest.run {
            create(
                where injectWith Inject("where", EmbeddedMarykModel { this.model { value::ref } }),
                context = context,
            )
        }

        expect(InjectException("where")) {
            assertFailsWith {
                getRequest { where }
            }
        }

        val equals = Equals(
            EmbeddedMarykModel { value::ref } with "hoi"
        )

        context.collectResult("where", Equals.asValues(equals))

        expect(equals) { getRequest { where } }
    }

    @Test
    fun convertSimpleToJSONAndBack() {
        checkJsonConversion(injectSimple, Inject, { this.context })
    }

    @Test
    fun convertSimpleToProtoBufAndBack() {
        checkProtoBufConversion(injectSimple, Inject, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            testCollection: values.@0.values.string

            """.trimIndent()
        ) {
            checkYamlConversion(this.inject, Inject, { this.context })
        }
    }

    @Test
    fun convertCompleteObjectToYAMLAndBack() {
        expect(
            """
            testCompleteConvert
            """.trimIndent()
        ) {
            checkYamlConversion(injectCompleteObject, Inject, { this.context })
        }
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.inject, Inject, { this.context })
    }

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.inject, Inject, { this.context })
    }

    @Test
    fun convertAnyToYAMLAndBack() {
        expect(
            """
            testCollection: values.*.values.string

            """.trimIndent()
        ) {
            checkYamlConversion(this.injectFromAny, Inject, { this.context })
        }
    }

    @Test
    fun convertAnyToJSONAndBack() {
        expect(
            """
            {
              "testCollection": "values.*.values.string"
            }
            """.trimIndent()
        ) {
            checkJsonConversion(this.injectFromAny, Inject, { this.context })
        }
    }

    @Test
    fun convertAnyToProtoBufAndBack() {
        checkProtoBufConversion(this.injectFromAny, Inject, { this.context })
    }
}
