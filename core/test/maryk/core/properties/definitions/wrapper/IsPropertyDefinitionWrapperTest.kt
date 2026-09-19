package maryk.core.properties.definitions.wrapper

import kotlin.test.assertEquals

internal fun comparePropertyDefinitionWrapper(
    converted: IsDefinitionWrapper<out Any, out Any, *, Any>,
    original: IsDefinitionWrapper<out Any, out Any, *, Any>
) {
    assertEquals(original.index, converted.index)
    assertEquals(original.name, converted.name)
    if (original is IsSensitiveValueDefinitionWrapper<*, *, *, *> && converted is IsSensitiveValueDefinitionWrapper<*, *, *, *>) {
        assertEquals(original.sensitive, converted.sensitive)
    }
    assertEquals(original.definition, converted.definition)
}
