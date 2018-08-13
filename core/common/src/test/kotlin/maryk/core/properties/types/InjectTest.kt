package maryk.core.properties.types

import maryk.EmbeddedMarykModel
import maryk.TestMarykModel
import maryk.core.models.asValues
import maryk.core.models.injectable
import maryk.core.properties.exceptions.InjectException
import maryk.core.query.DefinitionsContext
import maryk.core.query.RequestContext
import maryk.core.query.filters.Equals
import maryk.core.query.pairs.with
import maryk.core.query.requests.GetRequest
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

    private val valuesToCollect = EmbeddedMarykModel(
        value ="a test value",
        model = EmbeddedMarykModel(
            "embedded value"
        )
    )

    init {
        context.addToCollect("testCollection", EmbeddedMarykModel)
        context.collectResult("testCollection", valuesToCollect)
    }

    private val inject =
        EmbeddedMarykModel.injectable("testCollection") {
            ref { value }
        }

    private val injectDeep =
        EmbeddedMarykModel.injectable("testCollection") {
            this { model.ref { value } }
        }

    @Test
    fun testResolve() {
        inject.resolve(context) shouldBe "a test value"
        injectDeep.resolve(context) shouldBe "embedded value"
    }

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
