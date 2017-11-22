package maryk.core.properties.definitions.contextual

import maryk.SubMarykObject
import maryk.TestMarykObject
import maryk.checkProtoBufConversion
import maryk.core.extensions.bytes.MAXBYTE
import maryk.core.extensions.bytes.ZEROBYTE
import maryk.core.objects.RootDataModel
import maryk.core.properties.ByteCollectorWithLengthCacher
import maryk.core.properties.types.Key
import maryk.core.query.properties.DataModelPropertyContext
import maryk.test.shouldBe
import kotlin.test.Test

class ContextualReferenceDefinitionTest {
    private val refsToTest = arrayOf<Key<TestMarykObject>>(
            Key(ByteArray(9, { ZEROBYTE })),
            Key(ByteArray(9, { MAXBYTE })),
            Key(ByteArray(9, { if (it % 2 == 1) 0b1000_1000.toByte() else MAXBYTE }))
    )

    private val def = ContextualReferenceDefinition<DataModelPropertyContext>(
            index = 14,
            name = "test",
            contextualResolver = { it!!.dataModel!!.key }
    )

    @Suppress("UNCHECKED_CAST")
    private val context = DataModelPropertyContext(
            mapOf(
                    TestMarykObject.name to TestMarykObject,
                    SubMarykObject.name to SubMarykObject
            ),
            dataModel = TestMarykObject as RootDataModel<Any>
    )

    @Test
    fun testTransportConversion() {
        val bc = ByteCollectorWithLengthCacher()
        refsToTest.forEach { value ->
            checkProtoBufConversion(bc, value, this.def, this.context)
        }
    }

    @Test
    fun convertString() {
        refsToTest.forEach {
            val b = def.asString(it, this.context)
            def.fromString(b, this.context) shouldBe it
        }
    }
}