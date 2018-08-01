package maryk.core.properties.types

import maryk.EmbeddedMarykModel
import maryk.core.query.DataModelContext
import maryk.test.shouldBe
import kotlin.test.Test

class InjectTest {
    val context = DataModelContext(
        dataModels = mutableMapOf(
            EmbeddedMarykModel.name to { EmbeddedMarykModel }
        )
    )

    val valuesToCollect = EmbeddedMarykModel(
        value ="a test value",
        model = EmbeddedMarykModel(
            "embedded value"
        )
    )

    init {
        context.collectResult("testCollection", valuesToCollect)
    }

    val inject = Inject(
        "testCollection",
        EmbeddedMarykModel,
        EmbeddedMarykModel.ref { value }
    )

    val injectDeep = Inject(
        "testCollection",
        EmbeddedMarykModel,
        EmbeddedMarykModel { model.ref { value } }
    )

    @Test
    fun testResolve() {
        inject.resolve(context) shouldBe "a test value"
        injectDeep.resolve(context) shouldBe "embedded value"
    }
}
