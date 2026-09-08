package maryk.core.properties.definitions.wrapper

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.createFixedBytesWrapper
import maryk.core.properties.definitions.createFlexBytesWrapper
import maryk.core.properties.definitions.string
import maryk.core.protobuf.WriteCache
import maryk.core.query.DefinitionsConversionContext
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
    if (original is IsSensitiveValueDefinitionWrapper<*, *, *, *> && converted is IsSensitiveValueDefinitionWrapper<*, *, *, *>) {
        assertEquals(original.sensitive, converted.sensitive)
    }
    // Serialized definitions rebuild their model and enum instances, so compare schema semantics.
    val originalDefinition = original.definition
    val convertedDefinition = converted.definition
    val incompatibilities = mutableListOf<String>()
    val compatibleBothWays = originalDefinition.compatibleWith(
        convertedDefinition,
        mutableListOf(),
        incompatibilities::add,
    ) && convertedDefinition.compatibleWith(
        originalDefinition,
        mutableListOf(),
        incompatibilities::add,
    )
    assertTrue(
        compatibleBothWays,
        "${converted.name} should match with original ${original.name}: ${incompatibilities.joinToString()}",
    )
}

class PropertyDefinitionWrapperTest {
    private val def = FlexBytesDefinitionWrapper(
        index = 1u,
        name = "wrapper",
        definition = StringDefinition(),
        sensitive = true,
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
    fun convertDefinitionToYamlAndBack() {
        val yaml = checkYamlConversion(
            SensitiveWrapperModel,
            RootDataModel.Model,
            { DefinitionsConversionContext() },
            checker = { converted, _ ->
                val secret = converted["secret"] as IsSensitiveValueDefinitionWrapper<*, *, *, *>
                assertEquals(true, secret.sensitive)
            },
        )

        assertTrue(yaml.contains("? 1: [secret, true]"))
    }

    @Test
    fun legacyFlexWrapperCreatorSignatureRemainsCallable() {
        val legacyCreator: (UInt, String, Set<String>?, IsPropertyDefinition<out Any>) -> Any = createFlexBytesWrapper

        val wrapper = legacyCreator(1u, "wrapper", null, StringDefinition())
            as IsSensitiveValueDefinitionWrapper<*, *, *, *>

        assertEquals(false, wrapper.sensitive)
    }

    @Test
    fun legacyFixedBytesWrapperCreatorSignatureRemainsCallable() {
        val legacyCreator: (UInt, String, Set<String>?, IsPropertyDefinition<out Any>) -> Any = createFixedBytesWrapper

        val wrapper = legacyCreator(1u, "wrapper", null, BooleanDefinition())
            as IsSensitiveValueDefinitionWrapper<*, *, *, *>

        assertEquals(false, wrapper.sensitive)
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

private object SensitiveWrapperModel : RootDataModel<SensitiveWrapperModel>() {
    val secret by string(
        index = 1u,
        sensitive = true,
    )
}
