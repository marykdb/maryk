package maryk.core.properties.definitions.wrapper

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.definitions.StringDefinition
import maryk.core.protobuf.WriteCache
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

fun comparePropertyDefinitionWrapper(
    converted: IsDefinitionWrapper<*, *, *, *>,
    original: IsDefinitionWrapper<*, *, *, *>
) {
    assertEquals(original.index, converted.index)
    assertEquals(original.name, converted.name)
    // Make sure JS tests correct
    assertTrue("${converted.name} should match with original ${original.name}. $converted to $original") {
        original.definition == converted.definition
    }
}

class PropertyDefinitionWrapperTest {
    private val def = FlexBytesDefinitionWrapper(
        index = 1u,
        name = "wrapper",
        definition = StringDefinition(),
        getter = { _: Any -> null }
    )

    @Test
    fun convertDefinitionToProtoBufAndBack() {
        checkProtoBufConversion(this.def, IsDefinitionWrapper.Model, null, ::comparePropertyDefinitionWrapper)
    }

    @Test
    fun convertDefinitionToJSONAndBack() {
        checkJsonConversion(this.def, IsDefinitionWrapper.Model, null, ::comparePropertyDefinitionWrapper)
    }

    @Test
    fun rejectsIndexesOutsideTransportTagRange() {
        val invalid = FlexBytesDefinitionWrapper(
            index = Short.MAX_VALUE.toUInt() + 1u,
            name = "invalid",
            definition = StringDefinition(),
            getter = { _: Any -> null }
        )

        assertFailsWith<IllegalArgumentException> {
            invalid.calculateTransportByteLengthWithKey("value", WriteCache())
        }
    }
}
