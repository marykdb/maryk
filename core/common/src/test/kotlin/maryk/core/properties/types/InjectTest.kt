package maryk.core.properties.types

import maryk.EmbeddedMarykModel
import maryk.TestMarykModel
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.models.asValues
import maryk.core.models.testExtendedMarykModelObject
import maryk.core.models.testMarykModelObject
import maryk.core.properties.exceptions.InjectException
import maryk.core.properties.types.numeric.toUInt64
import maryk.core.query.DefinitionsContext
import maryk.core.query.RequestContext
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.filters.Equals
import maryk.core.query.pairs.with
import maryk.core.query.requests.GetRequest
import maryk.core.query.requests.get
import maryk.core.query.responses.ValuesResponse
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

class InjectTest {
    private val definitionsContext = DefinitionsContext(
        dataModels = mutableMapOf(
            EmbeddedMarykModel.name to { EmbeddedMarykModel }
        )
    )

    private val context = RequestContext(
        definitionsContext
    )

    val key = TestMarykModel.key(testMarykModelObject)

    private val getRequest = TestMarykModel.get(key)

    private val valuesResponse = ValuesResponse.asValues(
        ValuesResponse(
            TestMarykModel,
            listOf(
                ValuesWithMetaData(
                    key = key,
                    values = testExtendedMarykModelObject,
                    firstVersion = 1234L.toUInt64(),
                    lastVersion = 3456L.toUInt64(),
                    isDeleted = false
                )
            )
        )
    )

    private val injectSimple = Inject("testSimpleConvert", EmbeddedMarykModel { model.ref { value } })

    init {
        context.addToCollect("testCollection", getRequest)
        context.collectResult("testCollection", valuesResponse)
        context.addToCollect("testSimpleConvert", EmbeddedMarykModel)
    }

    private val firstResponseValueRef = ValuesResponse { values.ref(0) { values } }

    private val inject =
        Inject("testCollection", TestMarykModel.ref(firstResponseValueRef) { string })

    private val injectDeep =
        Inject("testCollection", TestMarykModel(firstResponseValueRef) { embeddedValues.ref { value } })

    @Test
    fun testGetToCollect() {
        context.getToCollectModel("testCollection")?.model shouldBe ValuesResponse
    }

    @Test
    fun testResolve() {
        inject.resolve(context) shouldBe "hay"
        injectDeep.resolve(context) shouldBe "test"
    }

    private val valuesToCollect = EmbeddedMarykModel(
        value ="a test value",
        model = EmbeddedMarykModel(
            "embedded value"
        )
    )

    @Test
    fun testInjectInValues() {
        context.addToCollect("testCollection2", EmbeddedMarykModel)

        val values = TestMarykModel.map(context) {
            mapNonNulls(
                string with Inject("testCollection2", EmbeddedMarykModel { model.ref { value } })
            )
        }

        shouldThrow<InjectException> {
            values { string }
        } shouldBe InjectException("testCollection2")

        context.collectResult("testCollection2", valuesToCollect)

        values { string } shouldBe "embedded value"
    }

    @Test
    fun testInjectInObject() {
        context.addToCollect("filter", Equals)

        val getRequest = GetRequest.map(context) {
            mapNonNulls(
                filter with Inject("filter", EmbeddedMarykModel { model.ref { value } })
            )
        }

        shouldThrow<InjectException> {
            getRequest { filter }
        } shouldBe InjectException("filter")

        val equals = Equals(
            EmbeddedMarykModel.ref { value } with "hoi"
        )

        context.collectResult("filter", Equals.asValues(equals))

        getRequest { filter } shouldBe equals
    }

    @Test
    fun convertSimpleToYAMLAndBack() {
        checkYamlConversion(injectSimple, Inject, { this.context }) shouldBe """
        collectionName: testSimpleConvert
        propertyReference: model.value

        """.trimIndent()
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
        checkYamlConversion(this.inject, Inject, { this.context }) shouldBe """
        collectionName: testCollection
        propertyReference: values.@0.values.string

        """.trimIndent()
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.inject, Inject, { this.context })
    }

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.inject, Inject, { this.context })
    }
}
