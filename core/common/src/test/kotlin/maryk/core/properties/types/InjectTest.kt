package maryk.core.properties.types

import maryk.EmbeddedMarykModel
import maryk.TestMarykModel
import maryk.core.models.asValues
import maryk.core.models.injectable
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

    init {
        context.addToCollect("testCollection", getRequest)
        context.collectResult("testCollection", valuesResponse)
    }

    private val firstResponseValueRef = ValuesResponse { values.ref(0) { values } }

    private val inject =
        ValuesResponse.injectable("testCollection") {
            TestMarykModel.ref(firstResponseValueRef) { string }
        }

    private val injectDeep =
        ValuesResponse.injectable("testCollection") {
            TestMarykModel(firstResponseValueRef) { embeddedValues.ref { value } }
        }

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
                string with EmbeddedMarykModel.injectable(
                    "testCollection2"
                ) {
                    this { model.ref { value } }
                }
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
                filter with EmbeddedMarykModel.injectable("filter") {
                    this { model.ref { value } }
                }
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
}
