package maryk.core.properties.definitions.contextual

import maryk.SubMarykObject
import maryk.TestMarykObject
import maryk.checkProtoBufConversion
import maryk.core.extensions.bytes.MAX_BYTE
import maryk.core.extensions.bytes.ZERO_BYTE
import maryk.core.objects.RootDataModel
import maryk.core.properties.ByteCollector
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.types.Key
import maryk.core.query.DataModelPropertyContext
import maryk.test.shouldBe
import kotlin.test.Test

class ContextualReferenceDefinitionTest {
    private val refsToTest = arrayOf<Key<TestMarykObject>>(
        Key(ByteArray(9, { ZERO_BYTE })),
        Key(ByteArray(9, { MAX_BYTE })),
        Key(ByteArray(9, { if (it % 2 == 1) 0b1000_1000.toByte() else MAX_BYTE }))
    )

    private val def = ContextualReferenceDefinition<DataModelPropertyContext>(
        contextualResolver = { it!!.dataModel!!.key }
    )

    @Suppress("UNCHECKED_CAST")
    private val context = DataModelPropertyContext(
        mapOf(
            TestMarykObject.name to TestMarykObject,
            SubMarykObject.name to SubMarykObject
        ),
        dataModel = TestMarykObject as RootDataModel<Any, PropertyDefinitions<Any>>
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
