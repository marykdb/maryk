package maryk.core.properties.definitions.wrapper

import kotlin.test.assertEquals

internal fun comparePropertyDefinitionWrapper(
    converted: IsDefinitionWrapper<out Any, out Any, *, Any>,
    original: IsDefinitionWrapper<out Any, out Any, *, Any>
) {
    assertEquals(original.index, converted.index)
    assertEquals(original.name, converted.name)
    assertEquals(original.definition, converted.definition)
}
