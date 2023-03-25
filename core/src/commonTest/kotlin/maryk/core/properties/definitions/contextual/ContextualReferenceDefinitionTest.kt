package maryk.core.properties.definitions.contextual

import maryk.checkProtoBufConversion
import maryk.core.extensions.bytes.MAX_BYTE
import maryk.core.extensions.bytes.ZERO_BYTE
import maryk.core.extensions.toUnitLambda
import maryk.core.properties.IsRootModel
import maryk.core.properties.types.Key
import maryk.core.query.RequestContext
import maryk.test.ByteCollector
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class ContextualReferenceDefinitionTest {
    private val refsToTest = arrayOf<Key<TestMarykModel>>(
        Key(ByteArray(7) { ZERO_BYTE }),
        Key(ByteArray(7) { MAX_BYTE }),
        Key(ByteArray(7) { if (it % 2 == 1) 0b1000_1000.toByte() else MAX_BYTE })
    )

    private val def = ContextualReferenceDefinition<RequestContext>(
        contextualResolver = { it!!.dataModel as IsRootModel }
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.Model.name toUnitLambda { TestMarykModel },
            EmbeddedMarykModel.Model.name toUnitLambda { EmbeddedMarykModel }
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
        for (key in refsToTest) {
            val b = def.asString(key, this.context)
            expect(key) { def.fromString(b, this.context) }
        }
    }
}
