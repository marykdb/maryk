package maryk.core.properties.types

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.inject.Inject
import maryk.core.models.asValues
import maryk.core.models.key
import maryk.core.models.testExtendedMarykModelObject
import maryk.core.models.testMarykModelObject
import maryk.core.properties.exceptions.InjectException
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
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

class InjectTest {
    private val definitionsContext = DefinitionsContext(
        dataModels = mutableMapOf(
            EmbeddedMarykModel.name toUnitLambda { EmbeddedMarykModel }
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

    private val injectSimple = Inject("testSimpleConvert", EmbeddedMarykModel { model.ref { value } })
    private val injectCompleteObject = Inject("testCompleteConvert")

    init {
        context.addToCollect("testCollection", getRequest)
        context.collectResult("testCollection", valuesResponse)
        context.addToCollect("testSimpleConvert", EmbeddedMarykModel)
    }

    private val firstResponseValueRef = ValuesResponse { values.refAt(0u) { values } }

    private val inject =
        Inject("testCollection", TestMarykModel.ref(firstResponseValueRef) { string })

    private val injectDeep =
        Inject("testCollection", TestMarykModel(firstResponseValueRef) { embeddedValues.ref { value } })

    private val injectFromAny =
        Inject("testCollection", ValuesResponse { values.atAny { values.ref(TestMarykModel) { string } } })

    @Test
    fun testGetToCollect() {
        context.getToCollectModel("testCollection")?.model shouldBe ValuesResponse
    }

    @Test
    fun testResolve() {
        inject.resolve(context) shouldBe "hay"
        injectDeep.resolve(context) shouldBe "test"
    }

    @Test
    fun testResolveAny() {
        injectFromAny.resolve(context) shouldBe listOf("hay", "haas")
    }

    private val valuesToCollect = EmbeddedMarykModel(
        value = "a test value",
        model = EmbeddedMarykModel(
            "embedded value"
        )
    )

    @Test
    fun testInjectInValues() {
        context.addToCollect("testCollection2", EmbeddedMarykModel)

        val values = TestMarykModel.values(context) {
            mapNonNulls(
                string injectWith Inject("testCollection2", EmbeddedMarykModel { model.ref { value } })
            )
        }

        shouldThrow<InjectException> {
            values { string }
        } shouldBe InjectException("testCollection2")

        context.collectResult("testCollection2", valuesToCollect)

        values { string } shouldBe "embedded value"
    }

    @Test
    fun testInjectInValuesWithResponse() {
        val values = TestMarykModel.values(context) {
            mapNonNulls(
                string injectWith injectDeep
            )
        }

        values { string } shouldBe "test"
    }

    @Test
    fun testInjectInObject() {
        context.addToCollect("where", Equals)

        val getRequest = GetRequest.values(context) {
            mapNonNulls(
                where injectWith Inject("where", EmbeddedMarykModel { model.ref { value } })
            )
        }

        shouldThrow<InjectException> {
            getRequest { where }
        } shouldBe InjectException("where")

        val equals = Equals(
            EmbeddedMarykModel.ref { value } with "hoi"
        )

        context.collectResult("where", Equals.asValues(equals))

        getRequest { where } shouldBe equals
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
        testCollection: values.@0.values.string

        """.trimIndent()
    }

    @Test
    fun convertCompleteObjectToYAMLAndBack() {
        checkYamlConversion(injectCompleteObject, Inject, { this.context }) shouldBe """
        testCompleteConvert
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

    @Test
    fun convertAnyToYAMLAndBack() {
        checkYamlConversion(this.injectFromAny, Inject, { this.context }) shouldBe """
        testCollection: values.*.values.string

        """.trimIndent()
    }

    @Test
    fun convertAnyToJSONAndBack() {
        checkJsonConversion(this.injectFromAny, Inject, { this.context }) shouldBe """
        {
        	"testCollection": "values.*.values.string"
        }
        """.trimIndent()
    }

    @Test
    fun convertAnyToProtoBufAndBack() {
        checkProtoBufConversion(this.injectFromAny, Inject, { this.context })
    }
}
