package maryk.core.properties.definitions.contextual

import maryk.EmbeddedMarykModel
import maryk.TestMarykModel
import maryk.checkProtoBufConversion
import maryk.core.extensions.bytes.MAX_BYTE
import maryk.core.extensions.bytes.ZERO_BYTE
import maryk.core.extensions.toUnitLambda
import maryk.core.models.IsRootDataModel
import maryk.core.properties.types.Key
import maryk.core.query.RequestContext
import maryk.test.ByteCollector
import maryk.test.shouldBe
import kotlin.test.Test

class ContextualReferenceDefinitionTest {
    private val refsToTest = arrayOf<Key<TestMarykModel>>(
        Key(ByteArray(9) { ZERO_BYTE }),
        Key(ByteArray(9) { MAX_BYTE }),
        Key(ByteArray(9) { if (it % 2 == 1) 0b1000_1000.toByte() else MAX_BYTE })
    )

    private val def = ContextualReferenceDefinition<RequestContext>(
        contextualResolver = { it!!.dataModel!! as IsRootDataModel<*> }
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.name toUnitLambda { TestMarykModel },
            EmbeddedMarykModel.name toUnitLambda { EmbeddedMarykModel }
        ),
        dataModel = TestMarykModel
    )

    @Test
    fun testTransportConversion() {
        val bc = ByteCollector()
        for (value in refsToTest) {
            checkProtoBufConversion(bc, value, this.def, this.context)
        }
    }

    @Test
    fun convertString() {
        for (it in refsToTest) {
            val b = def.asString(it, this.context)
            def.fromString(b, this.context) shouldBe it
        }
    }
}
